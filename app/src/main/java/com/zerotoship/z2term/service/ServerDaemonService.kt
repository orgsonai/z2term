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
import com.zerotoship.z2term.icon.setZ2SmallIcon
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.widget.StatusWidgetProvider
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
                // 常駐トンネル (A2) も同じ枠にぶら下がっているので一緒に畳む。
                TunnelManager.stopAll()
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
                    // 常駐トンネル (A2) を張る。サーバーが 1 つも無くてもトンネルだけで常駐する。
                    runCatching { TunnelManager.reload(this) }
                    if (!started && !TunnelManager.isRunning) {
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

    private fun buildNotification(): Notification =
        buildNotification(ServerDaemonManager.readStatus(this).count { it.state == "running" })

    /**
     * 稼働数を渡して通知を組む。[startNotificationRefresher] は数えた結果を持っているので、
     * ここへ渡して**状態ファイルの読み直しを 1 周期 1 回**に抑える (以前は数えるためと通知を
     * 組むために毎周期 2 回読んでいた)。
     */
    private fun buildNotification(running: Int): Notification {
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
            .setContentText(resources.getQuantityString(R.plurals.server_notification_text, running, running))
            .setZ2SmallIcon(this)
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
     *
     * ⚠ **常駐中ずっと回り続けるループ**なので、周期と 1 周期あたりの仕事量がそのまま電池に効く
     * (0.8.268)。0.8.267 までは 3 秒ごとに status を 2 回読んで通知を出し直していた ＝ 1 日
     * 約 29,000 回。しかも WakeLock を握っているので端末が Doze へ入れず、その都度 CPU が起きる。
     * いまは **稼働数が変わったときだけ**描き直し、落ち着いたら周期も広げる。
     */
    private fun startNotificationRefresher() {
        stopNotificationRefresher()
        val t = Thread {
            val nm = getSystemService(NotificationManager::class.java)
            var lastRunning = -1
            var elapsed = 0L
            try {
                while (ServerDaemonManager.isRunning && !Thread.currentThread().isInterrupted) {
                    val running = runCatching {
                        ServerDaemonManager.readStatus(this).count { it.state == "running" }
                    }.getOrDefault(-1)
                    // 同じ数のまま通知を出し直しても見た目は 1 ドットも変わらないので出さない。
                    // ホーム画面ウィジェットも同じ理由で変化時だけ描き直す。
                    if (running != lastRunning) {
                        lastRunning = running
                        runCatching { nm.notify(NOTIFICATION_ID, buildNotification(running)) }
                        StatusWidgetProvider.refresh(this)
                    }
                    // 起動直後は supervisor が status を書くまでラグがあるので短く見る。
                    // 落ち着いた後は「クラッシュして再起動した」に気付けば十分なので広げる。
                    val interval = if (elapsed < SETTLE_MILLIS) SETTLE_POLL_MILLIS else STEADY_POLL_MILLIS
                    Thread.sleep(interval)
                    elapsed += interval
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
        TunnelManager.stopAll()
        // 常駐が終わった＝ホーム画面ウィジェットの「常駐 N」が古くなるので描き直す。
        StatusWidgetProvider.refresh(this)
    }

    companion object {
        private const val TAG = "ServerDaemonService"
        // v2: IMPORTANCE_MIN へ変更。チャンネルの重要度は作成後に下げられないため、確実に反映させる
        // よう新 ID にする (旧 z2term_servers チャンネルは未使用のまま残るだけ)。
        private const val CHANNEL_ID = "z2term_servers_v2"
        private const val NOTIFICATION_ID = 1002
        private const val MAX_WAKELOCK_MILLIS = 8L * 60 * 60 * 1000

        /** 起動直後、status の書き込みラグに追従するため短い周期で見る時間。 */
        private const val SETTLE_MILLIS = 60_000L
        /** その間の周期 (従来と同じ)。 */
        private const val SETTLE_POLL_MILLIS = 3_000L
        /** 落ち着いた後の周期。常駐中ずっと回るので、ここを詰めると電池に効く。 */
        private const val STEADY_POLL_MILLIS = 30_000L
        const val ACTION_START = "com.zerotoship.z2term.SERVERS_START"
        const val ACTION_STOP = "com.zerotoship.z2term.SERVERS_STOP"

        fun start(context: Context) {
            val intent = Intent(context, ServerDaemonService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ServerDaemonService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
