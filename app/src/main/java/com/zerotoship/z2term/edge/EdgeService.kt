package com.zerotoship.z2term.edge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import com.zerotoship.z2term.icon.setZ2SmallIcon

/** Lifetime for explicitly enabled overlay panels; no wake lock; hidden views stop polling while item shells continue. */
class EdgeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        ensureChannel(nm)
        val stop = PendingIntent.getService(this, 0, Intent(this, EdgeService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL).setZ2SmallIcon(this)
            .setContentTitle(getString(R.string.edge_title)).setContentText(getString(R.string.edge_running))
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .addAction(0, getString(R.string.edge_off), stop).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7810, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7810, notification)
        // Replace the foreground notification before removing its former channel.
        nm.deleteNotificationChannel(LEGACY_CHANNEL)
    }

    private fun ensureChannel(nm: NotificationManager) {
        nm.getNotificationChannel(CHANNEL)?.let { channel ->
            channel.name = getString(R.string.edge_title)
            nm.createNotificationChannel(channel)
            return
        }
        // Badge settings cannot be changed after registration, even by deleting and
        // recreating the same ID. Migrate existing installs to a distinct channel.
        val previous = nm.getNotificationChannel(LEGACY_CHANNEL)
        val channel = NotificationChannel(CHANNEL, getString(R.string.edge_title),
            previous?.importance ?: NotificationManager.IMPORTANCE_MIN).apply {
            if (previous != null) {
                // Preserve notification preferences, including a blocked channel.
                description = previous.description
                group = previous.group
                setSound(previous.sound, previous.audioAttributes)
                vibrationPattern = previous.vibrationPattern
                enableVibration(previous.shouldVibrate())
                lightColor = previous.lightColor
                enableLights(previous.shouldShowLights())
                lockscreenVisibility = previous.lockscreenVisibility
                setBypassDnd(previous.canBypassDnd())
            }
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            EdgeRuntime.off(this)
            return START_NOT_STICKY
        }
        if (!EdgeRuntime.store(this).enabled()) { stopSelf(); return START_NOT_STICKY }
        runCatching { EdgeRuntime.restore(this) }.onFailure { stopSelf() }
        return START_STICKY
    }

    override fun onDestroy() {
        EdgeRuntime.destroy()
        super.onDestroy()
    }

    companion object {
        const val STOP = "com.zerotoship.z2term.EDGE_STOP"
        private const val LEGACY_CHANNEL = "z2term_edge"
        private const val CHANNEL = "z2term_edge_v2"
    }
}
