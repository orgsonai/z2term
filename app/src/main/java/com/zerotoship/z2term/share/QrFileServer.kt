package com.zerotoship.z2term.share

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Serves one private snapshot on loopback; request paths never become filesystem paths. */
internal class QrFileServer(
    private val file: File,
    private val filename: String,
    val token: String,
    private val downloadLabel: String,
    private val onBytes: (Long) -> Unit = {},
) : Closeable {
    init { require(file.isFile && token.matches(Regex("[a-f0-9]{64}"))) }
    private val running = AtomicBoolean(true)
    private val listener = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4),
        { task -> Thread(task, "qr-share-http").apply { isDaemon = true } })
    val port: Int get() = listener.localPort
    val path: String = "/$token/"
    val size: Long = file.length()

    init {
        Thread({
            while (running.get()) {
                val socket = try { listener.accept() } catch (_: Exception) { break }
                synchronized(this) {
                    if (!running.get()) socket.close()
                    else {
                        sockets.add(socket)
                        try {
                            workers.execute {
                                try { socket.use { serve(it) } } catch (_: Exception) { /* Client cancelled. */ }
                                finally { sockets.remove(socket) }
                            }
                        } catch (_: Exception) { sockets.remove(socket); socket.close() }
                    }
                }
            }
        }, "qr-share-accept").apply { isDaemon = true; start() }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 10_000
        val input = BufferedInputStream(socket.getInputStream())
        val request = readLine(input)?.split(' ') ?: return
        if (request.size != 3 || request[2] !in listOf("HTTP/1.0", "HTTP/1.1")) return
        val headers = LinkedHashMap<String, String>()
        var count = 0
        var total = 0
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            total += line.length
            if (++count > 40 || total > 16_384) return
            val colon = line.indexOf(':')
            if (colon <= 0) return
            val key = line.substring(0, colon).lowercase(java.util.Locale.ROOT)
            if (headers.put(key, line.substring(colon + 1).trim()) != null) return
        }
        val method = request[0]
        if (method != "GET" && method != "HEAD") {
            respond(socket, 405, "Method Not Allowed", 0, mapOf("Allow" to "GET, HEAD")); return
        }
        if (!running.get()) return
        when (request[1]) {
            path + "health" -> {
                val bytes = token.toByteArray(Charsets.US_ASCII)
                respond(socket, 200, "OK", bytes.size.toLong(), mapOf("Content-Type" to "text/plain"))
                if (method == "GET") socket.getOutputStream().write(bytes)
            }
            path -> {
                val html = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>z2term</title><style>body{font:18px system-ui;max-width:36em;margin:3em auto;padding:0 1em;overflow-wrap:anywhere}h1{font-size:1.25em}a{display:inline-block;padding:.7em 1em;border:1px solid;color:inherit}p{line-height:1.6}</style></head><body><p>z2term</p><h1>${escape(filename)}</h1><p>$size bytes</p><a href="file" download>${escape(downloadLabel)}</a></body></html>"""
                val bytes = html.toByteArray(Charsets.UTF_8)
                respond(socket, 200, "OK", bytes.size.toLong(), mapOf("Content-Type" to "text/html; charset=utf-8"))
                if (method == "GET") socket.getOutputStream().write(bytes)
            }
            path + "file" -> {
                val range = try { parseRange(headers["range"], size) } catch (_: IllegalArgumentException) {
                    respond(socket, 416, "Range Not Satisfiable", 0, mapOf("Content-Range" to "bytes */$size")); return
                }
                val partial = headers.containsKey("range")
                val start = range?.first ?: 0
                val length = range?.let { it.last - it.first + 1 } ?: size
                val encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
                val extra = linkedMapOf("Content-Type" to "application/octet-stream",
                    "Content-Disposition" to "attachment; filename=\"download\"; filename*=UTF-8''$encoded",
                    "Accept-Ranges" to "bytes")
                if (partial) extra["Content-Range"] = "bytes $start-${start + length - 1}/$size"
                respond(socket, if (partial) 206 else 200, if (partial) "Partial Content" else "OK", length, extra)
                if (method == "HEAD") return
                RandomAccessFile(file, "r").use { source ->
                    source.seek(start)
                    val output = socket.getOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var remaining = length
                    while (remaining > 0 && running.get()) {
                        val n = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        remaining -= n
                        onBytes(n.toLong())
                    }
                }
            }
            else -> respond(socket, 404, "Not Found", 0)
        }
    }

    private fun respond(socket: Socket, code: Int, reason: String, length: Long, extra: Map<String, String> = emptyMap()) {
        val header = buildString {
            append("HTTP/1.1 $code $reason\r\nContent-Length: $length\r\nConnection: close\r\n")
            append("Cache-Control: no-store, private, max-age=0\r\nPragma: no-cache\r\n")
            append("X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n")
            append("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\n")
            extra.forEach { (key, value) -> append("$key: $value\r\n") }
            append("\r\n")
        }
        socket.getOutputStream().write(header.toByteArray(Charsets.US_ASCII))
    }

    @Synchronized override fun close() {
        if (!running.getAndSet(false)) return
        runCatching { listener.close() }
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }

    companion object {
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
