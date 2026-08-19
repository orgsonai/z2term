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
import com.zerotoship.z2term.icon.setZ2SmallIcon
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Terminal フォアグラウンドサービス。
 *
 * - Activity が破棄された後も [SessionManager] が保持するセッションを生かす。
 * - 通知から STOP アクションを発火すると、セッションを終了して自身を停止。
 *
 * ## WifiLock を持たない理由 (0.8.268)
 *
 * 0.8.143〜0.8.267 はここでも `WIFI_MODE_FULL_HIGH_PERF` の WifiLock を握っていた。これは
 * **Wi-Fi 無線の省電力を完全に止める**指定で、画面消灯中も無線がフルパワーのままになり、
 * 電池と発熱に直接効く。
 *
 * 無線を起こしたままにする必要があるのは「外から着信を受ける」= 常駐サーバー
 * ([ServerDaemonService]) の仕事で、そちらが同じ WifiLock を持っている。このサービスの役目は
 * **対話セッションのプロセスを生かすこと**だけで、そのために無線は要らない。両方が握ると
 * 同じロックが二重になるだけなので、ここでは持たない。
 *
 * ⚠ 逆に言うと、外部からの到達性を保つには常駐サーバー側を動かす必要がある (🔒 だけでは
 * 保たれない)。
 *
 * ## 省電力モードに従う (0.8.269)
 *
 * WakeLock も [AppSettings.serversLowPower] が ON なら握らない。0.8.268 まではこのサービスだけ
 * 設定を見ておらず、常駐サーバーを省電力モードにしても**こちらが握り続けるので設定が効き切らなかった**
 * (常駐サーバーが動いている間は 2 つのサービスが同じ WakeLock を 1 本ずつ持つ)。「電池を採る」と
 * 決めた人に対して片方だけ握り続けるのは約束を破っているので、フラグは 1 つで共有する。
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
        // 省電力モードのときは WakeLock を握らず Doze を許す (0.8.269)。
        // 0.8.268 まではこのサービスだけ設定を見ておらず、常駐サーバーを省電力モードにしても
        // こちらが握り続けるので **設定が効き切らなかった**。「電池を採る」と決めた人に対して、
        // 片方だけ握り続けるのは約束を破っている。
        if (!lowPowerNow()) acquireWakeLock() else releaseWakeLock()
    }

    /**
     * 省電力モードか (常駐サーバーと共通の [AppSettings.serversLowPower])。
     *
     * 設定は「常駐している間ずっと CPU を起こしておくか」の 1 つの意思表示で、サービスごとに
     * 分ける意味が無いのでフラグも 1 つで共有する。⚠ ここは `onStartCommand` からしか読まないので、
     * 設定を変えたら [start] を呼び直して再判定させること (`ServersSheet` がそうしている)。
     */
    private fun lowPowerNow(): Boolean = runCatching {
        runBlocking { AppSettings(applicationContext).flow.first().serversLowPower }
    }.getOrDefault(false)

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

        // ⚠ 常駐の理由が 🔒 なのか attach なのか読めないと、通知を見ても止め方が分からない。
        val attached = AttachHold.attached
        val text = if (attached > 0) {
            resources.getQuantityString(R.plurals.service_notification_attached, attached, attached)
        } else {
            getString(R.string.service_notification_text)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(text)
            .setZ2SmallIcon(this)
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
            context.startForegroundService(intent)
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
