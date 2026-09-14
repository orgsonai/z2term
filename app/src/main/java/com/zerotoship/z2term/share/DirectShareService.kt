package com.zerotoship.z2term.share

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.ConnectivityManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.CancellationSignal
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.SshProfileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import javax.net.ServerSocketFactory

/** Explicitly started sharing only; never restarts or publishes files after process death. */
internal class DirectShareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val expire = Runnable { DirectShareManager.stop(this) }
    private val relayConnectTimeout = Runnable {
        DirectShareManager.stop(this, DirectShareManager.Problem.RELAY_UNAVAILABLE)
    }
    private var currentId = ""
    private var started = false
    @Volatile private var server: DirectFileServer? = null
    @Volatile private var source: InputStream? = null
    @Volatile private var snapshot: File? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkWatcher: DirectShareNetwork? = null
    private var relay: ShareRelay? = null
    private val queryCancellation = CancellationSignal()

    override fun onCreate() {
        super.onCreate()
        // Own the reservation even if Stop arrives before the first start command.
        currentId = DirectShareManager.state.value.id
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { DirectShareManager.stop(this); stopSelf(); return START_NOT_STICKY }
        val id = intent?.getStringExtra("id").orEmpty()
        if (started) return START_NOT_STICKY
        started = true
        currentId = id
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.direct_share_title), NotificationManager.IMPORTANCE_LOW))
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = PendingIntent.getActivity(this, 0, Intent(this, DirectShareActivity::class.java), pendingFlags)
            val stop = PendingIntent.getService(this, 1, Intent(this, DirectShareService::class.java).setAction(STOP), pendingFlags)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(getString(R.string.direct_share_title))
                .setContentText(getString(R.string.direct_share_notification)).setContentIntent(open)
                .setOngoing(true).addAction(0, getString(R.string.direct_share_stop), stop).build()
            ServiceCompat.startForeground(this, 1024, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (_: Exception) {
            DirectShareManager.update(id) { it.copy(active = false, stopping = true, preparing = false, failed = true) }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action != START || id.isEmpty() || !DirectShareManager.state.value.active ||
            DirectShareManager.state.value.id != id) {
            stopSelf(startId); return START_NOT_STICKY
        }
        scope.launch {
            var preparingSelection = false
            try {
                wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "z2term:direct-share")
                    .apply { acquire(65 * 60_000L) }
                handler.postDelayed(expire, 65 * 60_000L)
                val config = ShareRelayConfig.parse(intent.getStringExtra("profile").orEmpty(),
                    intent.getStringExtra("origin").orEmpty(), intent.getIntExtra("remotePort", 0),
                    intent.getIntExtra("minutes", 0))
                val folder = intent.getBooleanExtra("folder", false)
                val profile = withContext(Dispatchers.IO) {
                    SshProfileStore(this@DirectShareService).profiles.first()
                        .first { it.id == config.profileId && it.hasSsh }
                }
                val watcher = DirectShareNetwork(getSystemService(ConnectivityManager::class.java), handler) {
                    DirectShareManager.stop(this@DirectShareService, DirectShareManager.Problem.NETWORK_CHANGED)
                }.also { networkWatcher = it }
                watcher.prepare()
                val bindAddress = InetAddress.getByName("127.0.0.1")
                val uri = Uri.parse(intent.getStringExtra("file"))
                require(uri.scheme == "content")
                preparingSelection = true
                val (ready, name) = withContext(Dispatchers.IO) {
                    ensureActive()
                    val root = File(cacheDir, "direct-share").apply { mkdirs() }
                    // Delete only previous-process leftovers. A cancelled provider read from an old
                    // service in this process must never delete a newer share's snapshot.
                    root.listFiles()?.filter { it.name != PROCESS_ID }?.forEach { it.deleteRecursively() }
                    ensureActive()
                    val cache = File(root, PROCESS_ID + "/" + id).apply { mkdirs() }
                    snapshot = cache
                    try {
                        val selected = DirectShareDocuments.source(contentResolver, uri, folder, queryCancellation) { ensureActive() }
                        val content = DirectShareSnapshot.prepare(selected, cache, { ensureActive() }, { source = it })
                        ensureActive()
                        DirectFileServer(content.root.file ?: cache, content.root.name, null, 0, false,
                            config.minutes * 60_000L, ServerSocketFactory.getDefault(),
                            onVisit = { DirectShareManager.update(id) { it.copy(visited = true) } },
                            onBytes = { bytes -> DirectShareManager.update(id) { it.copy(sent = it.sent + bytes) } },
                            bindAddress = bindAddress, folderContent = content.takeIf { folder })
                            .also { server = it; if (!isActive) it.close() } to content.root.name
                    } finally {
                        if (!isActive) { cache.deleteRecursively(); cache.parentFile?.delete() }
                    }
                }
                ensureActive()
                preparingSelection = false
                watcher.checkCurrent()
                DirectShareManager.update(id) { it.copy(preparing = false, connecting = true, name = name,
                    size = ready.size, fileCount = ready.fileCount) }
                val connector = ShareRelay(this@DirectShareService, ready, profile, config).also { relay = it }
                handler.postDelayed(relayConnectTimeout, 120_000)
                val publicUrl = connector.connect()
                handler.removeCallbacks(relayConnectTimeout)
                watcher.checkCurrent()
                DirectShareManager.update(id) { it.copy(preparing = false, name = name, url = publicUrl,
                    connecting = false, size = ready.size, fileCount = ready.fileCount,
                    expires = System.currentTimeMillis() + config.minutes * 60_000L) }
                handler.removeCallbacks(expire)
                handler.postDelayed(expire, config.minutes * 60_000L)
                relay?.monitor { reachable ->
                    DirectShareManager.update(id) { it.copy(connecting = !reachable, url = if (reachable) publicUrl else "") }
                }
            } catch (_: TimeoutCancellationException) {
                DirectShareManager.update(id) { it.copy(active = false, stopping = true, preparing = false, failed = true, url = "") }
                stopSelf()
            } catch (_: CancellationException) {
                // Explicit stop or service timeout.
            } catch (e: DirectShareNetwork.Unavailable) {
                DirectShareManager.stop(this@DirectShareService, e.problem)
                stopSelf()
            } catch (e: Exception) {
                android.util.Log.w("DirectShare", "Sharing failed: ${e.javaClass.simpleName}")
                DirectShareManager.update(id) { it.copy(active = false, stopping = true, preparing = false, connecting = false, failed = true, url = "",
                    problem = if (preparingSelection) DirectShareManager.Problem.NONE else DirectShareManager.Problem.RELAY_UNAVAILABLE) }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) { DirectShareManager.stop(this); stopSelf() }
    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        networkWatcher?.close()
        relay?.close()
        server?.close()
        runCatching { queryCancellation.cancel() }
        runCatching { source?.close() }
        snapshot?.let { it.deleteRecursively(); it.parentFile?.delete() }
        wakeLock?.let { if (it.isHeld) it.release() }
        DirectShareManager.finish(currentId)
        super.onDestroy()
    }

    companion object {
        const val START = "com.zerotoship.z2term.DIRECT_SHARE_START"
        const val STOP = "com.zerotoship.z2term.DIRECT_SHARE_STOP"
        private const val CHANNEL = "z2term_direct_share"
        private val PROCESS_ID = java.util.UUID.randomUUID().toString()
    }
}
