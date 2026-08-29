package com.zerotoship.z2term.channel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 共通ファイル画面用の FTP クライアント。
 *
 * FTP は制御ポートだけを `-L` しても、PASV/EPSV のデータポートがトンネル外へ出てしまう。
 * そのため [ServiceRoute] からデータ転送ごとに追加の一時転送を張る。これにより、利用者が
 * 指定するのは制御ポート 1 つだけでよく、受動ポート範囲を手で列挙する必要がない。
 * FTPS は扱わない。SSH 経由を外した FTP は認証情報も内容も平文になる。
 */
class FtpClient private constructor(
    private val route: ServiceRoute,
    private val control: java.net.Socket,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
    startPath: String,
) : RemoteFs {
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
    private val operationMutex = Mutex()
    override val home: String = normalizePath(startPath)

    @Volatile
    override var isAlive: Boolean = true
        private set

    override suspend fun list(path: String): List<SftpEntry> = io {
        val lines = runCatching { dataLines("MLSD ${safePath(path)}") }.getOrElse {
            dataLines("LIST ${safePath(path)}")
        }
        val entries = lines.mapNotNull { parseMlsd(it) ?: parseUnixList(it) }
            .filter { it.name != "." && it.name != ".." }
            .sortedWith(compareByDescending<SftpEntry> { it.isDir }.thenBy { it.name.lowercase() })
            .toMutableList()
        if (normalizePath(path) != "/") {
            entries.add(0, SftpEntry("..", true, false, 0, 0, ""))
        }
        entries
    }

    override suspend fun download(remotePath: String, sink: OutputStream) = io {
        withDataSocket("RETR ${safePath(remotePath)}") { socket ->
            socket.getInputStream().use { it.copyTo(sink) }
        }
        Unit
    }

    override suspend fun upload(source: InputStream, remotePath: String) = io {
        withDataSocket("STOR ${safePath(remotePath)}") { socket ->
            socket.getOutputStream().use { source.copyTo(it) }
        }
        Unit
    }

    override suspend fun mkdir(path: String) = io {
        expect(command("MKD ${safePath(path)}"), 257, 250)
    }

    override suspend fun rename(from: String, to: String) = io {
        expect(command("RNFR ${safePath(from)}"), 350)
        expect(command("RNTO ${safePath(to)}"), 250)
    }

    override suspend fun rm(path: String) = io {
        expect(command("DELE ${safePath(path)}"), 250)
    }

    override suspend fun rmdir(path: String) = io {
        expect(command("RMD ${safePath(path)}"), 250)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val sendQuit = isAlive
        isAlive = false
        if (sendQuit) runCatching { command("QUIT") }
        runCatching { control.close() }
        route.close()
    }

    private fun dataLines(command: String): List<String> {
        val bytes = java.io.ByteArrayOutputStream()
        withDataSocket(command) { socket ->
            socket.getInputStream().use { it.copyTo(bytes) }
        }
        return bytes.toString(StandardCharsets.UTF_8.name()).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun <T> withDataSocket(command: String, block: (java.net.Socket) -> T): T {
        val dataPort = passivePort()
        route.openSocket(dataPort).use { routed ->
            expect(this.command(command), 125, 150)
            val result = block(routed.socket)
            // EOF/close を先にサーバへ伝えてから完了応答を読む。
            runCatching { routed.socket.shutdownOutput() }
            expect(readResponse(), 226, 250)
            return result
        }
    }

    /** EPSV を優先。古いサーバだけ PASV へフォールバックする。 */
    private fun passivePort(): Int {
        val epsv = command("EPSV")
        if (epsv.code == 229) {
            val port = Regex("""\(\|\|\|(\d+)\|\)""").find(epsv.text)
                ?.groupValues?.get(1)?.toIntOrNull()
            if (port in 1..65535) return port!!
        }
        val pasv = command("PASV")
        expect(pasv, 227)
        val parts = Regex("""\((\d+),(\d+),(\d+),(\d+),(\d+),(\d+)\)""")
            .find(pasv.text)?.groupValues?.drop(1)?.mapNotNull(String::toIntOrNull)
            ?: error("Invalid PASV response: ${pasv.text}")
        check(parts.size == 6) { "Invalid PASV response: ${pasv.text}" }
        return parts[4] * 256 + parts[5]
    }

    private fun command(value: String): Response {
        check(!value.contains('\r') && !value.contains('\n')) { "Invalid FTP command" }
        writer.write(value)
        writer.write("\r\n")
        writer.flush()
        return readResponse()
    }

    private fun readResponse(): Response {
        val first = reader.readLine() ?: error("FTP server closed the connection")
        val code = first.take(3).toIntOrNull() ?: error("Invalid FTP response: $first")
        val lines = mutableListOf(first)
        if (first.length > 3 && first[3] == '-') {
            while (true) {
                val line = reader.readLine() ?: error("FTP server closed a multiline response")
                lines += line
                if (line.startsWith("$code ")) break
            }
        }
        return Response(code, lines.joinToString("\n"))
    }

    private fun expect(response: Response, vararg codes: Int) {
        if (response.code !in codes) error(response.text)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            try {
                block()
            } catch (e: Throwable) {
                isAlive = false
                throw e
            }
        }
    }

    private data class Response(val code: Int, val text: String)

    companion object {
        suspend fun connect(route: ServiceRoute, service: RemoteService): FtpClient =
            withContext(Dispatchers.IO) {
                val socket = java.net.Socket()
                try {
                    socket.soTimeout = 60_000
                    socket.connect(java.net.InetSocketAddress(route.host, route.port), 15_000)
                    val reader = socket.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
                    val writer = BufferedWriter(
                        OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1)
                    )
                    val client = FtpClient(route, socket, reader, writer, service.path)
                    client.expect(client.readResponse(), 220)
                    val loginUser = service.user.ifBlank { "anonymous" }
                    val userReply = client.command("USER ${safe(loginUser)}")
                    when (userReply.code) {
                        230 -> Unit
                        331, 332 -> {
                            val loginPassword = service.password.ifEmpty { "anonymous@" }
                            client.expect(client.command("PASS ${safe(loginPassword)}"), 230, 202)
                        }
                        else -> error(userReply.text)
                    }
                    runCatching { client.command("OPTS UTF8 ON") }
                    client.expect(client.command("TYPE I"), 200)
                    client
                } catch (e: Throwable) {
                    runCatching { socket.close() }
                    route.close()
                    throw e
                }
            }

        private fun safe(value: String): String {
            require(!value.contains('\r') && !value.contains('\n')) { "Invalid FTP value" }
            return value
        }

        private fun safePath(path: String): String = safe(normalizePath(path))

        private fun normalizePath(path: String): String {
            val parts = RemotePath.segments(path)
            return if (parts.isEmpty()) "/" else "/" + parts.joinToString("/")
        }

        private fun parseMlsd(line: String): SftpEntry? {
            val split = line.indexOf(' ')
            if (split <= 0) return null
            val facts = line.substring(0, split).split(';')
                .mapNotNull {
                    val at = it.indexOf('=')
                    if (at <= 0) null else it.substring(0, at).lowercase() to it.substring(at + 1)
                }.toMap()
            val type = facts["type"]?.lowercase() ?: return null
            val name = line.substring(split + 1).trimStart()
            if (name.isBlank() || type == "cdir" || type == "pdir") return null
            val isDir = type == "dir"
            val modified = facts["modify"]?.let(::parseMlsdTime) ?: 0
            return SftpEntry(
                name = name,
                isDir = isDir,
                isLink = type.contains("slink"),
                size = facts["size"]?.toLongOrNull() ?: 0,
                mtimeSec = modified,
                permissions = facts["unix.mode"] ?: "",
            )
        }

        /** LIST の一般的な Unix 形式。MLSD 非対応サーバー向けの限定フォールバック。 */
        private fun parseUnixList(line: String): SftpEntry? {
            val parts = line.trim().split(Regex("\\s+"), limit = 9)
            if (parts.size < 9 || parts[0].length < 10) return null
            val name = parts[8].substringBefore(" -> ")
            return SftpEntry(
                name = name,
                isDir = parts[0].startsWith('d'),
                isLink = parts[0].startsWith('l'),
                size = parts[4].toLongOrNull() ?: 0,
                mtimeSec = 0,
                permissions = parts[0],
            )
        }

        private fun parseMlsdTime(value: String): Long = runCatching {
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value.take(14))?.time?.div(1000) ?: 0
        }.getOrDefault(0)
    }
}
