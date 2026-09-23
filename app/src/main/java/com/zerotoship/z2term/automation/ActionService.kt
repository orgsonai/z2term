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
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.action_macro_title),
            NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val stop = PendingIntent.getService(this, 7811,
            Intent(this, ActionService::class.java).setAction(STOP)
                .setData("z2term-action:${runId.orEmpty()}".toUri())
                .putExtra("run_id", runId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setZ2SmallIcon(this)
            .setContentTitle(getString(R.string.action_macro_title))
            .setContentText(getString(R.string.action_macro_running, name))
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .addAction(0, getString(R.string.action_macro_stop), stop).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7811, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7811, notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            intent.getStringExtra("run_id")?.let { runCatching { ActionRuntime.stop(it) } }
            if (!ActionRuntime.running) stopSelf()
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
        unregisterReceiver(screenOff)
        ActionRuntime.serviceDestroyed(this)
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "z2term_actions_v1"
        private const val STOP = "com.zerotoship.z2term.ACTION_MACRO_STOP"
    }
}
