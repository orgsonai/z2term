package com.zerotoship.z2term.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.SessionManager

/**
 * Terminal フォアグラウンドサービス。
 *
 * - Activity が破棄された後も [SessionManager] が保持するセッションを生かす。
 * - 通知から STOP アクションを発火すると、セッションを終了して自身を停止。
 */
class TerminalService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop action received")
                stopSessionAndSelf()
                return START_NOT_STICKY
            }
            ACTION_DETACH -> {
                // 常駐 OFF: フォアグラウンド解除 + 通知撤去のみ。セッションは生かしたまま。
                // (プロセスが背景でいつ殺されてもよい = ユーザーが望んだ非常駐挙動)
                Log.i(TAG, "Detach action received (foreground off, sessions kept)")
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startForegroundInternal()
        }
        return START_STICKY
    }

    private fun startForegroundInternal() {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
    }

    private fun stopSessionAndSelf() {
        releaseWakeLock()
        SessionManager.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "z2term:session")
        wl.setReferenceCounted(false)
        wl.acquire(MAX_WAKELOCK_MILLIS)
        wakeLock = wl
        Log.i(TAG, "Partial WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { wl ->
            if (wl.isHeld) wl.release()
            Log.i(TAG, "Partial WakeLock released")
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.service_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.service_channel_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, TerminalService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(tapPendingIntent)
            .addAction(0, getString(R.string.service_action_stop), stopPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        Log.i(TAG, "TerminalService destroyed")
    }

    companion object {
        private const val TAG = "TerminalService"
        private const val CHANNEL_ID = "z2term_session"
        private const val NOTIFICATION_ID = 1001
        /** WakeLock の絶対上限 (8 時間)。超えると自動解放 */
        private const val MAX_WAKELOCK_MILLIS = 8L * 60 * 60 * 1000
        const val ACTION_STOP = "com.zerotoship.z2term.STOP"
        const val ACTION_DETACH = "com.zerotoship.z2term.DETACH"

        fun start(context: Context) {
            val intent = Intent(context, TerminalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 完全停止: セッションを終了して常駐解除 (通知の「停止」ボタン用) */
        fun stop(context: Context) {
            val intent = Intent(context, TerminalService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        /** 常駐解除のみ: セッションは生かしたままフォアグラウンド/通知を外す (常駐 OFF トグル用) */
        fun detach(context: Context) {
            val intent = Intent(context, TerminalService::class.java).setAction(ACTION_DETACH)
            context.startService(intent)
        }
    }
}
