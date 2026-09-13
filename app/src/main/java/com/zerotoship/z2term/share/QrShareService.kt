package com.zerotoship.z2term.share

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.zerotoship.z2term.R
import com.zerotoship.z2term.icon.setZ2SmallIcon
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** A user-started, expiring transfer has its own lifetime; stopping it never stops terminals. */
internal class QrShareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transfer: Transfer? = null
    private var lock: PowerManager.WakeLock? = null
    private var activeId = ""

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { QrShareManager.stop(this); return START_NOT_STICKY }
        val id = intent?.getStringExtra("id") ?: return START_NOT_STICKY.also { stopSelf(startId) }
        val state = QrShareManager.state.value
        if (transfer != null) return START_NOT_STICKY
        if (state.id != id || !state.active || state.phase == QrShareManager.Phase.STOPPING) {
            QrShareManager.released(id)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        activeId = id
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.qr_share_title), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, QrShareActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, Intent(this, QrShareService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL).setZ2SmallIcon(this)
            .setContentTitle(getString(R.string.qr_share_title)).setContentText(state.name)
            .setOngoing(true).setSilent(true).setContentIntent(open)
            .addAction(0, getString(R.string.qr_share_stop), stop).build()
        try {
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "z2term:qr-share")
                .apply { acquire(32 * 60 * 1000L) }
            val job = Transfer(id)
            transfer = job
            scope.launch {
                try { job.run(requireNotNull(intent.data)) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    android.util.Log.w("QrShare", "Transfer failed: ${e.javaClass.simpleName}")
                    QrShareManager.update(id) { it.copy(phase = QrShareManager.Phase.ERROR, url = "",
                        error = e.message?.takeIf { msg -> msg.startsWith("z2term:") }?.removePrefix("z2term:")
                            ?: getString(R.string.qr_share_transfer_failed)) }
                } finally {
                    job.close()
                    withContext(NonCancellable + Dispatchers.Main) { stopSelf(startId) }
                }
            }
        } catch (_: Exception) {
            QrShareManager.update(id) { it.copy(phase = QrShareManager.Phase.ERROR, error = getString(R.string.qr_share_start_failed)) }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) { QrShareManager.stop(this) }

    override fun onDestroy() {
        scope.cancel()
        transfer?.close()
        QrShareManager.released(activeId)
        if (lock?.isHeld == true) lock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private inner class Transfer(private val id: String) : Closeable {
        private val resources = mutableListOf<Closeable>()
        private var closed = false
        private val dir = File(cacheDir, "qr-share/$id")
        private val sent = AtomicLong()
        private val hostname = AtomicReference<String?>()
        private var lastProbeResult = ""
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .dns(QrTunnelDns(this@QrShareService))
            .callTimeout(5, TimeUnit.SECONDS).build()

        @Synchronized private fun <T : Closeable> own(value: T): T {
            if (closed) { value.close(); throw CancellationException() }
            resources.add(value)
            return value
        }
        private fun fail(resource: Int): Nothing = throw IllegalStateException("z2term:" + getString(resource))

        @Synchronized private fun prepareSnapshotDirectory() {
            if (closed) throw CancellationException()
            // A killed process cannot run its finally block; clear its private snapshots next time.
            // Finish cleanup before close() releases ownership to a subsequent transfer.
            File(cacheDir, "qr-share").listFiles()?.filter { it.name != id && it.name.matches(Regex("[a-f0-9-]{36}")) }
                ?.forEach { removeSnapshot(it) }
            check(dir.mkdirs())
        }

        @android.annotation.SuppressLint("UsableSpace") // Use existing free space without evicting unrelated cached files.
        suspend fun run(uri: android.net.Uri) = coroutineScope {
            prepareSnapshotDirectory()
            val native = File(applicationInfo.nativeLibraryDir, "libz2tunnel.so")
            if (!native.isFile) fail(R.string.qr_share_native_missing)
            val snapshot = File(dir, "payload")
            val input: InputStream = when (uri.scheme) {
                "file" -> File(requireNotNull(uri.path)).inputStream()
                "content" -> contentResolver.openInputStream(uri) ?: fail(R.string.qr_share_file_failed)
                else -> fail(R.string.qr_share_file_failed)
            }
            own(input).use { source ->
                snapshot.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var size = 0L
                    var reported = 0L
                    while (true) {
                        ensureActive()
                        val n = source.read(buffer)
                        if (n < 0) break
                        if (size == reported && dir.usableSpace < 16 * 1024 * 1024L) fail(R.string.qr_share_space_failed)
                        out.write(buffer, 0, n)
                        size += n
                        if (size - reported >= 1024 * 1024) {
                            QrShareManager.update(id) { it.copy(size = size) }
                            reported = size
                        }
                    }
                    QrShareManager.update(id) { it.copy(size = size) }
                }
            }
            val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val state = QrShareManager.state.value
            android.util.Log.i("QrShare", "Source snapshot prepared (${snapshot.length()} bytes)")
            val server = own(QrFileServer(snapshot, state.name, token, getString(R.string.qr_share_download)) { sent.addAndGet(it) })
            QrShareManager.update(id) { it.copy(phase = QrShareManager.Phase.CONNECTING) }
            val process = ProcessBuilder(native.absolutePath, "tunnel", "--no-autoupdate", "--protocol", "http2",
                "--url", "http://127.0.0.1:${server.port}", "--metrics", "127.0.0.1:0")
                .directory(dir).redirectErrorStream(true).apply {
                    environment().apply {
                        clear(); put("HOME", dir.absolutePath); put("TMPDIR", dir.absolutePath)
                        put("PATH", "/system/bin"); put("GOMAXPROCS", "2")
                    }
                }.start()
            own(Closeable { process.destroy(); if (process.isAlive) process.destroyForcibly() })
            Thread({
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            // Never retain raw provider logs: they can contain credentials or URLs.
                            PUBLIC_URL.find(line)?.value?.let {
                                if (hostname.compareAndSet(null, it)) android.util.Log.i("QrShare", "Connector URL allocated")
                            }
                            when {
                                line.contains("x509:") -> android.util.Log.w("QrShare", "Connector certificate validation failed")
                                line.contains("no such host") -> android.util.Log.w("QrShare", "Connector DNS lookup failed")
                                line.contains("flag provided but not defined") -> android.util.Log.w("QrShare", "Connector rejected its launch arguments")
                            }
                        }
                    }
                }
            }, "qr-share-tunnel-output").apply { isDaemon = true; start() }
            val deadline = android.os.SystemClock.elapsedRealtime() + 120_000
            var url: String? = null
            while (url == null) {
                ensureActive()
                if (!process.isAlive) {
                    android.util.Log.w("QrShare", "Connector exited: ${process.exitValue()}")
                    fail(R.string.qr_share_connect_failed)
                }
                if (android.os.SystemClock.elapsedRealtime() >= deadline) fail(R.string.qr_share_connect_failed)
                val host = hostname.get()
                if (host != null && healthy(host + server.path + "health", token)) url = host + server.path
                else delay(1500)
            }
            val end = android.os.SystemClock.elapsedRealtime() + 30 * 60 * 1000
            val expires = System.currentTimeMillis() + 30 * 60 * 1000
            QrShareManager.update(id) { it.copy(phase = QrShareManager.Phase.READY, url = url, expires = expires) }
            android.util.Log.i("QrShare", "Temporary public file transfer ready")
            var nextProbe = android.os.SystemClock.elapsedRealtime() + 15_000
            while (android.os.SystemClock.elapsedRealtime() < end) {
                delay(1000)
                if (!process.isAlive) fail(R.string.qr_share_connect_failed)
                if (android.os.SystemClock.elapsedRealtime() >= nextProbe) {
                    val ready = healthy(url + "health", token)
                    QrShareManager.update(id) { it.copy(phase = if (ready) QrShareManager.Phase.READY else QrShareManager.Phase.CONNECTING,
                        url = if (ready) url else "") }
                    nextProbe = android.os.SystemClock.elapsedRealtime() + 15_000
                }
                QrShareManager.update(id) { it.copy(sent = sent.get()) }
            }
            QrShareManager.update(id) { it.copy(phase = QrShareManager.Phase.STOPPED, url = "") }
            close()
        }

        private fun healthy(url: String, token: String): Boolean = runCatching {
            client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use {
                val ready = it.code == 200 && it.peekBody(128).string() == token
                reportProbe(if (ready) "Public probe ready" else "Public probe HTTP ${it.code}; health response unavailable")
                ready
            }
        }.onFailure { reportProbe("Public probe failed: ${it.javaClass.simpleName}") }.getOrDefault(false)

        private fun reportProbe(result: String) {
            if (result != lastProbeResult) android.util.Log.i("QrShare", result)
            lastProbeResult = result
        }

        @Synchronized override fun close() {
            if (closed) return
            closed = true
            client.dispatcher.cancelAll()
            resources.asReversed().forEach { runCatching { it.close() } }
            resources.clear()
            removeSnapshot(dir)
        }
    }

    private fun removeSnapshot(directory: File) {
        runCatching {
            java.nio.file.Files.walkFileTree(directory.toPath(), object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                    java.nio.file.Files.deleteIfExists(file)
                    return java.nio.file.FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: java.nio.file.Path, exc: java.io.IOException?): java.nio.file.FileVisitResult {
                    if (exc != null) throw exc
                    java.nio.file.Files.deleteIfExists(dir)
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            })
        }
    }

    companion object {
        const val START = "qr-share-start"
        const val STOP = "qr-share-stop"
        private const val CHANNEL = "qr-file-sharing"
        private const val NOTIFICATION = 8421
        internal val PUBLIC_URL = Regex("https://[a-z0-9]+(?:-[a-z0-9]+)*\\.trycloudflare\\.com(?![a-zA-Z0-9.\\-])")
    }
}
