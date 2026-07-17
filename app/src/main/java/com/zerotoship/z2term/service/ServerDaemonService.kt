package com.zerotoship.z2term.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 常駐サーバー専用フォアグラウンドサービス。対話セッションの [TerminalService] とは独立して動き、
 * アプリ (Activity) が無くても・端末起動直後 ([BootReceiver]) でもサーバーを常駐させる。
 *
 * - [ACTION_START]: [ServerDaemonManager.start] で supervisor を起動し前面化。対象サーバーが
 *   無ければ即 stopSelf。
 * - [ACTION_STOP]: [ServerDaemonManager.stop] で全サーバー停止 → 前面解除 → stopSelf。
 * - 画面消灯中でも LAN から到達できるよう WakeLock + WifiLock を保持 (sshd 等と同じ理由)。
 */
class ServerDaemonService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    @Volatile private var refresher: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop servers action")
                stopNotificationRefresher()
                ServerDaemonManager.stop()
                releaseLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // startForeground は startForegroundService から 5 秒以内に呼ぶ必要があるため、
                // 先に前面化してから重い起動処理 (DataStore 読取 + エンジン起動) を別スレッドで行う。
                startForegroundInternal()
                Thread {
                    val started = ServerDaemonManager.start(this)
                    if (!started) {
                        Log.i(TAG, "No servers to run; stopping service")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        // 省電力モードでなければ WakeLock/WifiLock を握って画面消灯中も LAN 到達性を保つ。
                        // 省電力 ON のときは握らず Doze を許す (電池優先・着信は遅延/取りこぼしうる)。
                        val lowPower = runCatching {
                            runBlocking { AppSettings(this@ServerDaemonService).flow.first().serversLowPower }
                        }.getOrDefault(false)
                        if (!lowPower) acquireLocks()
                        // supervisor が status を書くまでラグがある。1回きりの通知だと稼働数が 0 のまま
                        // 固まるため、状態が反映されるまで定期的に通知を更新する (再起動/クラッシュも追従)。
                        startNotificationRefresher()
                    }
                }.apply { isDaemon = true; name = "server-daemon-start"; start() }
            }
        }
        return START_STICKY
    }

    private fun startForegroundInternal() {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // ロック取得は起動処理側 (省電力モード判定後) で行う。
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    // IMPORTANCE_MIN: フォアグラウンドサービスの通知は OS 仕様で必須 (完全には消せない) だが、
                    // MIN にするとステータスバーのアイコンは出ず、通知シェード最下部に静かに畳まれる。
                    // = 「サーバー常駐のみのときはステータスバーに出さない」に一番近い形。
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.server_channel_name),
                        NotificationManager.IMPORTANCE_MIN
                    ).apply {
                        description = getString(R.string.server_channel_desc)
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val running = ServerDaemonManager.readStatus(this).count { it.state == "running" }
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(this, ServerDaemonService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.server_notification_title))
            .setContentText(getString(R.string.server_notification_text, running))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(tapPending)
            .addAction(0, getString(R.string.server_action_stop), stopPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * 稼働状態が通知に反映されるまで (および稼働中ずっと) 定期的に通知を更新する。
     * supervisor の status 書き込みラグや、サーバーのクラッシュ/自動再起動にも追従させる。
     */
    private fun startNotificationRefresher() {
        stopNotificationRefresher()
        val t = Thread {
            val nm = getSystemService(NotificationManager::class.java)
            try {
                while (ServerDaemonManager.isRunning && !Thread.currentThread().isInterrupted) {
                    runCatching { nm.notify(NOTIFICATION_ID, buildNotification()) }
                    Thread.sleep(3000)
                }
            } catch (_: InterruptedException) {
                // stop で割り込まれた = 正常終了。
            }
        }.apply { isDaemon = true; name = "server-notif-refresh" }
        refresher = t
        t.start()
    }

    private fun stopNotificationRefresher() {
        refresher?.interrupt()
        refresher = null
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "z2term:servers").apply {
                setReferenceCounted(false)
                acquire(MAX_WAKELOCK_MILLIS)
            }
        }
        if (wifiLock?.isHeld != true) {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "z2term:servers-wifi")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNotificationRefresher()
        releaseLocks()
    }

    companion object {
        private const val TAG = "ServerDaemonService"
        // v2: IMPORTANCE_MIN へ変更。チャンネルの重要度は作成後に下げられないため、確実に反映させる
        // よう新 ID にする (旧 z2term_servers チャンネルは未使用のまま残るだけ)。
        private const val CHANNEL_ID = "z2term_servers_v2"
        private const val NOTIFICATION_ID = 1002
        private const val MAX_WAKELOCK_MILLIS = 8L * 60 * 60 * 1000
        const val ACTION_START = "com.zerotoship.z2term.SERVERS_START"
        const val ACTION_STOP = "com.zerotoship.z2term.SERVERS_STOP"

        fun start(context: Context) {
            val intent = Intent(context, ServerDaemonService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ServerDaemonService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
