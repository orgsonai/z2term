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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop servers action")
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
                        // 稼働数を反映した通知へ更新。
                        runCatching {
                            val nm = getSystemService(NotificationManager::class.java)
                            nm.notify(NOTIFICATION_ID, buildNotification())
                        }
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
        acquireLocks()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.server_channel_name),
                        NotificationManager.IMPORTANCE_LOW
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
        releaseLocks()
    }

    companion object {
        private const val TAG = "ServerDaemonService"
        private const val CHANNEL_ID = "z2term_servers"
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
