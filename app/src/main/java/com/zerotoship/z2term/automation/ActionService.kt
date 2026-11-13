package com.zerotoship.z2term.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zerotoship.z2term.R
import com.zerotoship.z2term.icon.setZ2SmallIcon
import androidx.core.net.toUri

/** Foreground only during a requested macro. Interrupted runs never resume automatically. */
class ActionService : Service() {
    private var runId: String? = null
    private var runName = ""
    private var progressText = ""
    private var shownProgressAt = 0L
    private var pendingProgress: Runnable? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { runId?.let { ActionRuntime.stop(it, "Screen turned off") } }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        showRun("")
    }
    internal fun showRun(name: String) {
        runName = name
        val manager = getSystemService(NotificationManager::class.java)
        // ⚠ 1.0.94 までの「低」の重要度では、通知欄の「サイレント」の段にたたまれて出て、
        // 開かないと「停止」ボタンが見えなかった (利用者の指摘)。音もバイブも無しのまま「既定」の
        // 重要度にして、ふつうの段に出す。重要度は作ったあとで変えられないので、チャネルを作り直す。
        manager.deleteNotificationChannel(LEGACY_CHANNEL)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.action_macro_title),
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            setShowBadge(false); setSound(null, null); enableVibration(false)
        })
        fun control(action: String, code: Int) = PendingIntent.getService(this, code,
            Intent(this, ActionService::class.java).setAction(action)
                .setData("z2term-action:${runId.orEmpty()}".toUri())
                .putExtra("run_id", runId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = control(STOP, 7811)
        // Paused: Resume and Stop. Waiting for the screen after Resume: Stop only. Running: Pause and Stop.
        val paused = ActionRuntime.paused
        val resuming = ActionRuntime.resuming
        val state = getString(when {
            resuming -> R.string.action_macro_resuming
            paused -> R.string.action_macro_paused
            else -> R.string.action_macro_running
        }, name)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setZ2SmallIcon(this)
            .setContentTitle(getString(R.string.action_macro_title))
            .setContentText(state + if (progressText.isNotEmpty()) " · $progressText" else "")
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
        if (runId != null && !resuming) builder.addAction(0, getString(if (paused) R.string.action_macro_resume else R.string.action_macro_pause),
            control(if (paused) RESUME else PAUSE, 7812))
        val notification = builder.addAction(0, getString(R.string.action_macro_stop), stop).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7811, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7811, notification)
    }
    /**
     * 進み具合を通知の本文に足す。繰り返しの速いマクロでは手順ごとに通知を出し直すと Android に
     * 間引かれるので、1 秒に 1 回までにまとめる (最後の値は必ず出す)。
     */
    internal fun showProgress(text: String) {
        progressText = text
        if (pendingProgress != null) return
        val wait = shownProgressAt + PROGRESS_INTERVAL_MS - android.os.SystemClock.uptimeMillis()
        val task = Runnable {
            pendingProgress = null
            shownProgressAt = android.os.SystemClock.uptimeMillis()
            if (runId != null) runCatching { showRun(runName) }
        }
        if (wait <= 0) task.run() else { pendingProgress = task; main.postDelayed(task, wait) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            intent.getStringExtra("run_id")?.let { runCatching { ActionRuntime.stop(it) } }
            if (!ActionRuntime.running) stopSelf()
        } else if (intent?.action == PAUSE || intent?.action == RESUME) {
            intent.getStringExtra("run_id")?.let { id ->
                runCatching { if (intent.action == PAUSE) ActionRuntime.pause(id) else ActionRuntime.resume(id) }
            }
        } else {
            runId = intent?.getStringExtra("run_id")
            try {
                if (!ActionRuntime.ready(runId, this)) stopSelf(startId)
            } catch (e: Exception) {
                runId?.let { runCatching { ActionRuntime.stop(it, e.message ?: "Cannot start execution") } }
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        pendingProgress?.let(main::removeCallbacks); pendingProgress = null
        unregisterReceiver(screenOff)
        ActionRuntime.serviceDestroyed(this)
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "z2term_actions_v2"
        private const val LEGACY_CHANNEL = "z2term_actions_v1"
        private const val PROGRESS_INTERVAL_MS = 1000L
        private const val STOP = "com.zerotoship.z2term.ACTION_MACRO_STOP"
        private const val PAUSE = "com.zerotoship.z2term.ACTION_MACRO_PAUSE"
        private const val RESUME = "com.zerotoship.z2term.ACTION_MACRO_RESUME"
    }
}
