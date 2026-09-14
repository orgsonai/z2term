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
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.net.Inet6Address
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ServerSocketFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

/** Explicitly started sharing only; never restarts or publishes files after process death. */
internal class DirectShareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val expire = Runnable { DirectShareManager.stop(this) }
    private var currentId = ""
    private var started = false
    @Volatile private var server: DirectFileServer? = null
    @Volatile private var source: InputStream? = null
    @Volatile private var snapshot: File? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkWatcher: DirectShareNetwork? = null
    private val queryCancellation = CancellationSignal()
    private val certificateGrant = CompletableDeferred<Unit>()

    override fun onCreate() {
        super.onCreate()
        // Own the reservation even if Stop arrives before the first start command.
        currentId = DirectShareManager.state.value.id
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { DirectShareManager.stop(this); stopSelf(); return START_NOT_STICKY }
        val id = intent?.getStringExtra("id").orEmpty()
        if (intent?.action == CERTIFICATE_GRANT) {
            if (started && id == currentId && DirectShareManager.state.value.active) certificateGrant.complete(Unit)
            else if (!started) stopSelf(startId)
            return START_NOT_STICKY
        }
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
            try {
                wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "z2term:direct-share")
                    .apply { acquire(65 * 60_000L) }
                handler.postDelayed(expire, 65 * 60_000L)
                val minutes = intent.getIntExtra("minutes", 0)
                require(minutes in setOf(5, 15, 60))
                val automatic = intent.getBooleanExtra("automatic", false)
                val folder = intent.getBooleanExtra("folder", false)
                val config = if (automatic) null else DirectShareConfig.parse(
                    intent.getStringExtra("origin").orEmpty(), intent.getIntExtra("port", 0), minutes)
                require(automatic || config?.tls == true || intent.getBooleanExtra("allowHttp", false))
                if (folder && config?.tls == true) withTimeout(10_000) { certificateGrant.await() }
                val watcher = DirectShareNetwork(getSystemService(ConnectivityManager::class.java), handler) {
                    DirectShareManager.stop(this@DirectShareService, DirectShareManager.Problem.NETWORK_CHANGED)
                }.also { networkWatcher = it }
                val bindAddress = watcher.prepare(automatic)
                val ipv6 = config?.ipv6 ?: (bindAddress is Inet6Address)
                val uri = Uri.parse(intent.getStringExtra("file"))
                require(uri.scheme == "content")
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
                        val factory = if (config?.tls == true) tlsFactory(Uri.parse(intent.getStringExtra("certificate")), intent.getStringExtra("password").orEmpty())
                            else ServerSocketFactory.getDefault()
                        ensureActive()
                        DirectFileServer(content.root.file ?: cache, content.root.name, config?.origin, config?.port ?: 0, ipv6, minutes * 60_000L, factory,
                            onVisit = { DirectShareManager.update(id) { it.copy(visited = true) } },
                            onBytes = { bytes -> DirectShareManager.update(id) { it.copy(sent = it.sent + bytes) } },
                            bindAddress = bindAddress, folderContent = content.takeIf { folder })
                            .also { server = it; if (!isActive) it.close() } to content.root.name
                    } finally {
                        if (!isActive) { cache.deleteRecursively(); cache.parentFile?.delete() }
                    }
                }
                ensureActive()
                watcher.checkCurrent()
                DirectShareManager.update(id) { it.copy(preparing = false, name = name, url = ready.url, ipv6 = ipv6,
                    size = ready.size, fileCount = ready.fileCount, expires = System.currentTimeMillis() + minutes * 60_000L) }
                handler.removeCallbacks(expire)
                handler.postDelayed(expire, minutes * 60_000L)
            } catch (_: TimeoutCancellationException) {
                DirectShareManager.update(id) { it.copy(active = false, stopping = true, preparing = false, failed = true, url = "") }
                stopSelf()
            } catch (_: CancellationException) {
                // Explicit stop or service timeout.
            } catch (e: DirectShareNetwork.Unavailable) {
                DirectShareManager.stop(this@DirectShareService, e.problem)
                stopSelf()
            } catch (_: Exception) {
                DirectShareManager.update(id) { it.copy(active = false, stopping = true, preparing = false, failed = true, url = "") }
                stopSelf()
            } finally {
                intent.removeExtra("password")
            }
        }
        return START_NOT_STICKY
    }

    private fun tlsFactory(uri: Uri, password: String): ServerSocketFactory {
        require(uri.scheme == "content")
        val input = requireNotNull(contentResolver.openInputStream(uri))
        source = input
        val bytes = try { input.use { it.readBytesLimited(2 * 1024 * 1024) } } finally { source = null }
        val chars = password.toCharArray()
        try {
            val store = KeyStore.getInstance("PKCS12")
            bytes.inputStream().use { store.load(it, chars) }
            val aliases = java.util.Collections.list(store.aliases()).filter { store.isKeyEntry(it) }
            require(aliases.size == 1)
            (store.getCertificate(aliases.single()) as X509Certificate).checkValidity()
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keys.init(store, chars)
            val context = SSLContext.getInstance("TLS")
            context.init(keys.keyManagers, null, null)
            val delegate = context.serverSocketFactory
            return object : ServerSocketFactory() {
                override fun createServerSocket(port: Int, backlog: Int, address: java.net.InetAddress): java.net.ServerSocket =
                    (delegate.createServerSocket(port, backlog, address) as SSLServerSocket).apply {
                        enabledProtocols = supportedProtocols.filter { it in setOf("TLSv1.2", "TLSv1.3") }.toTypedArray()
                    }
                override fun createServerSocket(port: Int) = createServerSocket(port, 8, java.net.InetAddress.getByName("0.0.0.0"))
                override fun createServerSocket(port: Int, backlog: Int) = createServerSocket(port, backlog, java.net.InetAddress.getByName("0.0.0.0"))
            }
        } finally { chars.fill('\u0000'); bytes.fill(0) }
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            scope.ensureActive()
            val n = read(buffer)
            if (n < 0) break
            require(output.size() + n <= limit)
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }

    override fun onTimeout(startId: Int, fgsType: Int) { DirectShareManager.stop(this); stopSelf() }
    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        networkWatcher?.close()
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
        const val CERTIFICATE_GRANT = "com.zerotoship.z2term.DIRECT_SHARE_CERTIFICATE_GRANT"
        private const val CHANNEL = "z2term_direct_share"
        private val PROCESS_ID = java.util.UUID.randomUUID().toString()
    }
}
