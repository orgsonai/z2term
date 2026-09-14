package com.zerotoship.z2term.share

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.Socket
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ServerSocketFactory
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** One immutable selection; browser routes resolve only to its manifest IDs. */
internal class DirectFileServer(
    file: File,
    filename: String,
    origin: String?,
    port: Int,
    ipv6: Boolean,
    private val ttlMillis: Long,
    factory: ServerSocketFactory = ServerSocketFactory.getDefault(),
    private val onVisit: () -> Unit = {},
    private val onBytes: (Long) -> Unit = {},
    private val nanoTime: () -> Long = System::nanoTime,
    bindAddress: InetAddress? = null,
    folderContent: DirectShareContent? = null,
) : Closeable {
    val token = secret()
    val path = "/share/" + token + "/"
    private val content = folderContent ?: DirectShareContent.single(file, filename)
    val size = content.size
    val fileCount = content.fileCount
    private val consent = secret()
    @Volatile private var deadline = nanoTime() + ttlMillis * 1_000_000L
    private val running = AtomicBoolean(true)
    private val listener = run {
        require(ttlMillis in 60_000..3_600_000)
        require(origin != null || bindAddress != null)
        if (bindAddress != null) factory.createServerSocket(port, 8, bindAddress)
        else {
            // Manual DNS origins can use either stack; a literal IPv6 origin must not fall back.
            try { factory.createServerSocket(port, 8, InetAddress.getByName("::")) }
            catch (e: java.io.IOException) {
                if (ipv6) throw e
                factory.createServerSocket(port, 8, InetAddress.getByName("0.0.0.0"))
            }
        }
    }
    internal val localPort get() = listener.localPort
    // The private listener uses its actual port until the HTTPS relay is ready.
    @Volatile private var origin: String = origin ?: DirectShareAddress.httpOrigin(requireNotNull(bindAddress), localPort)
    val url get() = this.origin + path
    private var relayed = false

    /** Publish only after the external health probe succeeds; consent uses the public HTTPS origin. */
    @Synchronized fun publishViaRelay(value: String) {
        check(running.get() && !relayed && listener.inetAddress.isLoopbackAddress)
        val config = DirectShareConfig.parse(value, 8080, 15)
        require(config.tls)
        origin = config.origin
        deadline = nanoTime() + ttlMillis * 1_000_000L
        relayed = true
    }
    private val sockets = ConcurrentHashMap<Socket, Long>()
    private val timer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "direct-share-timeout").apply { isDaemon = true }
    }
    private val workers = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4),
        { task -> Thread(task, "direct-share-http").apply { isDaemon = true } })

    init {
        timer.scheduleAtFixedRate({
            if (nanoTime() >= deadline) close()
            else sockets.forEach { (socket, progress) ->
                if (nanoTime() - progress >= 30_000_000_000L) runCatching { socket.close() }
            }
        }, 5, 5, TimeUnit.SECONDS)
        Thread({
            while (running.get()) {
                val socket = try { listener.accept() } catch (_: Exception) { break }
                synchronized(this) {
                    if (!running.get()) socket.close()
                    else {
                        sockets[socket] = nanoTime()
                        try {
                            workers.execute {
                                try { socket.use { serve(it) } } catch (_: Exception) { /* Disconnected client. */ }
                                finally { sockets.remove(socket) }
                            }
                        } catch (_: Exception) { sockets.remove(socket); socket.close() }
                    }
                }
            }
        }, "direct-share-listen").apply { isDaemon = true; start() }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 10_000
        val input = BufferedInputStream(socket.getInputStream())
        val request = readLine(input)?.split(' ') ?: return
        if (request.size != 3 || request[2] !in listOf("HTTP/1.0", "HTTP/1.1")) return
        val headers = LinkedHashMap<String, String>()
        var total = 0
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            total += line.length
            if (headers.size >= 40 || total > 16_384) return
            val colon = line.indexOf(':')
            if (colon <= 0) return
            val key = line.substring(0, colon).lowercase(Locale.ROOT)
            if (headers.put(key, line.substring(colon + 1).trim()) != null) return
        }
        val method = request[0]
        if (method !in listOf("GET", "HEAD", "POST")) {
            respond(socket, 405, "Method Not Allowed", 0, mapOf("Allow" to "GET, HEAD, POST")); return
        }
        if (!running.get() || nanoTime() >= deadline) {
            respond(socket, 410, "Gone", 0); return
        }
        // Never read an unbounded body or process ambiguous transfer framing.
        if (headers.containsKey("transfer-encoding") || (headers["content-length"] ?: "0") != "0") {
            respond(socket, 400, "Bad Request", 0); return
        }
        val target = resolve(request[1])
        if (target == null) { respond(socket, 404, "Not Found", 0); return }
        when (target.action) {
            Action.HEALTH -> {
                if (method == "POST") { respond(socket, 405, "Method Not Allowed", 0); return }
                val bytes = token.toByteArray(Charsets.US_ASCII)
                respond(socket, 200, "OK", bytes.size.toLong(), mapOf("Content-Type" to "text/plain"))
                if (method == "GET") write(socket, bytes)
            }
            Action.PAGE -> {
                if (method == "POST") { respond(socket, 405, "Method Not Allowed", 0); return }
                val ja = headers["accept-language"].orEmpty().lowercase(Locale.ROOT).startsWith("ja")
                val bytes = page(target, ja).toByteArray(Charsets.UTF_8)
                respond(socket, 200, "OK", bytes.size.toLong(), mapOf("Content-Type" to "text/html; charset=utf-8"))
                if (method == "GET") { write(socket, bytes); onVisit() }
            }
            Action.ACCEPT -> {
                if (method != "POST") { respond(socket, 405, "Method Not Allowed", 0); return }
                if (headers["origin"]?.let { !DirectShareConfig.sameOrigin(it, origin) } == true ||
                    headers["sec-fetch-site"]?.let { it !in setOf("same-origin", "none") } == true) {
                    respond(socket, 403, "Forbidden", 0); return
                }
                val cookie = "receive=" + consentFor(target.item.id) + "; Path=" + target.base + "; HttpOnly; SameSite=Strict" +
                    if (origin.startsWith("https://")) "; Secure" else ""
                respond(socket, 303, "See Other", 0, mapOf("Location" to (target.base + "file"), "Set-Cookie" to cookie))
            }
            Action.FILE -> {
                if (method == "POST") { respond(socket, 405, "Method Not Allowed", 0); return }
                val expectedCookie = ("receive=" + consentFor(target.item.id)).toByteArray(Charsets.US_ASCII)
                val permitted = headers["cookie"].orEmpty().split(';').any {
                    MessageDigest.isEqual(it.trim().toByteArray(Charsets.US_ASCII), expectedCookie)
                }
                if (!permitted) { respond(socket, 403, "Forbidden", 0); return }
                val file = requireNotNull(target.item.file)
                val size = target.item.size
                val filename = target.item.name
                val range = try { parseRange(headers["range"], size) } catch (_: IllegalArgumentException) {
                    respond(socket, 416, "Range Not Satisfiable", 0, mapOf("Content-Range" to ("bytes */" + size))); return
                }
                val start = range?.first ?: 0
                val length = range?.let { it.last - it.first + 1 } ?: size
                val encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
                val extra = linkedMapOf("Content-Type" to "application/octet-stream",
                    "Content-Disposition" to ("attachment; filename=\"download\"; filename*=UTF-8''" + encoded),
                    "Accept-Ranges" to "bytes")
                if (range != null) extra["Content-Range"] = "bytes " + start + "-" + (start + length - 1) + "/" + size
                respond(socket, if (range != null) 206 else 200, if (range != null) "Partial Content" else "OK", length, extra)
                if (method == "HEAD") return
                RandomAccessFile(file, "r").use { source ->
                    source.seek(start)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = length
                    while (remaining > 0 && running.get() && nanoTime() < deadline) {
                        val n = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (n < 0) break
                        write(socket, buffer, n)
                        remaining -= n
                        onBytes(n.toLong())
                    }
                }
            }
        }
    }

    private enum class Action { PAGE, ACCEPT, FILE, HEALTH }
    private data class Target(val item: DirectShareContent.Item, val action: Action, val base: String)

    private fun resolve(request: String): Target? {
        if (request == path) return Target(content.root, Action.PAGE, path)
        if (!request.startsWith(path)) return null
        val relative = request.removePrefix(path)
        if (relative == "health") return Target(content.root, Action.HEALTH, path)
        if (!content.folder) return when (relative) {
            "accept" -> Target(content.root, Action.ACCEPT, path)
            "file" -> Target(content.root, Action.FILE, path)
            else -> null
        }
        val match = Regex("(folder|item)/([0-9]{1,5})/(accept|file)?").matchEntire(relative) ?: return null
        val id = match.groupValues[2].toIntOrNull() ?: return null
        if (id.toString() != match.groupValues[2]) return null
        val item = content.item(id) ?: return null
        if (item.directory != (match.groupValues[1] == "folder")) return null
        val action = when (match.groupValues[3]) {
            "" -> Action.PAGE
            "accept" -> Action.ACCEPT
            "file" -> Action.FILE
            else -> return null
        }
        if (item.directory && action != Action.PAGE) return null
        return Target(item, action, itemPath(item))
    }

    private fun itemPath(item: DirectShareContent.Item): String =
        path + (if (item.directory) "folder/" else "item/") + item.id + "/"

    private fun consentFor(id: Int): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(consent.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
        doFinal(id.toString().toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it) }
    }

    private fun page(target: Target, ja: Boolean): String {
        val item = target.item
        val body = buildString {
            append("<p>z2term</p>")
            val parents = ArrayList<DirectShareContent.Item>()
            var parent = item.parent
            while (parent != null) {
                val node = requireNotNull(content.item(parent))
                parents.add(node)
                parent = node.parent
            }
            if (parents.isNotEmpty()) {
                append("<nav aria-label=\"" + (if (ja) "フォルダ" else "Folders") + "\">")
                parents.asReversed().forEachIndexed { index, node ->
                    if (index > 0) append(" / ")
                    append("<a href=\"" + itemPath(node) + "\">" + escape(node.name) + "</a>")
                }
                append("</nav>")
            }
            if (item.directory) {
                append("<h1>" + escape(item.name) + "</h1><p>")
                append(if (ja) "フォルダを開くか、受信するファイルを選んでください。" else "Open a folder or select a file to receive.")
                append("</p>")
                val children = content.children(item.id)
                if (children.isEmpty()) {
                    append("<p>" + (if (ja) "このフォルダは空です。" else "This folder is empty.") + "</p>")
                } else {
                    append("<ul>")
                    children.forEach { child ->
                        append("<li><a href=\"" + itemPath(child) + "\">" + escape(child.name) +
                            (if (child.directory) "/" else "") + "</a>")
                        if (!child.directory) append(" <span>" + child.size + " bytes</span>")
                        append("</li>")
                    }
                    append("</ul>")
                }
            } else {
                append("<h1>" + (if (ja) "このファイルを受信しますか？" else "Receive this file?") +
                    "</h1><p>" + escape(item.name) + "</p><p>" + item.size + " bytes</p>")
                append("<form method=\"post\" action=\"" + target.base + "accept\"><button type=\"submit\">" +
                    (if (ja) "受信して保存" else "Receive and save") + "</button></form>")
            }
            append("<p>" + (if (ja) "保存が完了するまで送信者は共有を停止しないでください。" else
                "The sender must keep sharing until your download finishes.") + "</p>")
        }
        return "<!doctype html><html lang=\"" + (if (ja) "ja" else "en") +
            "\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>z2term</title><style>body{font:18px system-ui;max-width:36em;margin:3em auto;padding:0 1em;overflow-wrap:anywhere}" +
            "h1{font-size:1.3em}button{font:inherit;padding:.7em 1em;border:1px solid;background:transparent;color:inherit}" +
            "p{line-height:1.6}ul{list-style:none;padding:0}li{border-bottom:1px solid #aaa;padding:.6em 0}" +
            "li a{display:inline-block;padding:.2em 0}li span{font-size:.85em}nav{line-height:1.8}</style>" +
            "</head><body>" + body + "</body></html>"
    }

    private fun respond(socket: Socket, code: Int, reason: String, length: Long, extra: Map<String, String> = emptyMap()) {
        val header = buildString {
            append("HTTP/1.1 " + code + " " + reason + "\r\nContent-Length: " + length + "\r\nConnection: close\r\n")
            append("Cache-Control: no-store, private, max-age=0\r\nPragma: no-cache\r\n")
            // no-referrer makes browsers send Origin: null for HTML form POSTs, which our
            // consent check correctly rejects. Keep the origin for same-origin forms while
            // still withholding the shared URL from other origins.
            append("X-Content-Type-Options: nosniff\r\nReferrer-Policy: same-origin\r\n")
            append("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'\r\n")
            extra.forEach { (key, value) -> append(key + ": " + value + "\r\n") }
            append("\r\n")
        }
        write(socket, header.toByteArray(Charsets.US_ASCII))
    }

    private fun write(socket: Socket, bytes: ByteArray, length: Int = bytes.size) {
        socket.getOutputStream().write(bytes, 0, length)
        sockets.computeIfPresent(socket) { _, _ -> nanoTime() }
    }

    @Synchronized override fun close() {
        if (!running.getAndSet(false)) return
        runCatching { listener.close() }
        sockets.keys.forEach { runCatching { it.close() } }
        timer.shutdownNow()
        workers.shutdownNow()
    }

    companion object {
        private fun secret(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        private fun readLine(input: BufferedInputStream): String? {
            val line = StringBuilder()
            while (line.length <= 4096) {
                val b = input.read()
                if (b < 0) return null
                if (b == 10) return line.toString().removeSuffix("\r")
                if (b == 0 || b > 127) return null
                line.append(b.toChar())
            }
            return null
        }
        internal fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

        internal fun parseRange(value: String?, size: Long): LongRange? {
            if (value == null) return null
            require(size > 0 && value.matches(Regex("bytes=[0-9]*-[0-9]*")))
            val parts = value.removePrefix("bytes=").split('-')
            if (parts[0].isEmpty()) {
                val suffix = requireNotNull(parts[1].toLongOrNull())
                require(suffix > 0)
                return maxOf(0, size - minOf(suffix, size))..(size - 1)
            }
            val start = requireNotNull(parts[0].toLongOrNull())
            val end = if (parts[1].isEmpty()) size - 1 else requireNotNull(parts[1].toLongOrNull())
            require(start in 0 until size && end >= start)
            return start..minOf(end, size - 1)
        }
    }
}
