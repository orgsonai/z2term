package com.zerotoship.z2term.share

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

class QrFileServerTest {
    private val token = "a".repeat(64)
    private val payload = ByteArray(400_000) { (it % 251).toByte() }

    private fun withServer(block: (QrFileServer, AtomicLong) -> Unit) {
        val dir = Files.createTempDirectory("qr-file-test").toFile()
        val file = File(dir, "payload").apply { writeBytes(payload) }
        val sent = AtomicLong()
        try {
            QrFileServer(file, "日本語 <script> ' name.pdf", token, "保存") { sent.addAndGet(it) }.use { block(it, sent) }
        } finally { dir.deleteRecursively() }
    }

    private data class Response(val status: Int, val headers: String, val body: ByteArray)
    private fun request(server: QrFileServer, target: String, method: String = "GET", headers: String = ""): Response {
        Socket("127.0.0.1", server.port).use { socket ->
            socket.soTimeout = 3000
            socket.getOutputStream().write("$method $target HTTP/1.1\r\nHost: localhost\r\n${headers}\r\n".toByteArray())
            val all = socket.getInputStream().readBytes()
            val separator = all.toString(Charsets.ISO_8859_1).indexOf("\r\n\r\n")
            assertTrue(separator >= 0)
            val head = all.copyOfRange(0, separator).toString(Charsets.ISO_8859_1)
            return Response(head.split(' ')[1].toInt(), head, all.copyOfRange(separator + 4, all.size))
        }
    }

    @Test fun onlyTheSelectedFileIsAvailableAndDownloadsAreRepeatable() = withServer { server, _ ->
        for (path in listOf("/", "/${"b".repeat(64)}/file", "/$token/../payload", "/$token/%2e%2e/payload", "/$token/file/else", "/z2api")) {
            val result = request(server, path)
            assertEquals(404, result.status)
            assertEquals(0, result.body.size)
        }
        repeat(2) {
            val result = request(server, server.path + "file")
            assertEquals(200, result.status)
            assertArrayEquals(payload, result.body)
            assertTrue(result.headers.contains("application/octet-stream"))
            assertTrue(result.headers.contains("filename*=UTF-8''%E6%97%A5"))
            assertTrue(result.headers.contains("Cache-Control: no-store"))
        }
    }

    @Test fun previewHeadAndHealthNeverConsumeTheFile() = withServer { server, sent ->
        val landing = request(server, server.path)
        assertEquals(200, landing.status)
        assertTrue(landing.body.toString(Charsets.UTF_8).contains("&lt;script&gt;"))
        assertFalse(landing.body.toString(Charsets.UTF_8).contains("<script>"))
        assertTrue(landing.headers.contains("frame-ancestors 'none'"))
        val health = request(server, server.path + "health")
        assertEquals(token, health.body.toString(Charsets.US_ASCII))
        val head = request(server, server.path + "file", "HEAD")
        assertEquals(200, head.status)
        assertEquals(0, head.body.size)
        assertTrue(head.headers.contains("Content-Length: ${payload.size}"))
        assertEquals(0, sent.get())
    }

    @Test fun byteRangesSupportBrowserResumeWithoutChangingTheOriginal() = withServer { server, _ ->
        for ((range, expected) in listOf("100-199" to payload.copyOfRange(100, 200),
                "399990-" to payload.takeLast(10).toByteArray(), "-10" to payload.takeLast(10).toByteArray())) {
            val result = request(server, server.path + "file", headers = "Range: bytes=$range\r\n")
            assertEquals(206, result.status)
            assertArrayEquals(expected, result.body)
            assertTrue(result.headers.contains("Content-Range: bytes "))
        }
        for (bad in listOf("0-1,3-4", "-0", "400000-", "4-2", "-", "999999999999999999999999-")) {
            val result = request(server, server.path + "file", headers = "Range: bytes=$bad\r\n")
            assertEquals(416, result.status)
            assertTrue(result.headers.contains("Content-Range: bytes */${payload.size}"))
            assertEquals(0, result.body.size)
        }
        assertArrayEquals(payload, request(server, server.path + "file").body)
    }

    @Test fun postingCannotMutateFilesAndStopClosesTheListener() = withServer { server, _ ->
        assertEquals(405, request(server, server.path + "file", "POST").status)
        assertArrayEquals(payload, request(server, server.path + "file").body)
        val port = server.port
        // Cover clients waiting for request headers, including connections queued behind workers.
        val clients = List(8) { Socket("127.0.0.1", port).apply { soTimeout = 1500 } }
        try {
            server.close()
            server.close()
            clients.forEach { client ->
                val end = runCatching { client.getInputStream().read() }
                if (end.isSuccess) assertEquals(-1, end.getOrThrow())
                else assertTrue("Waiting client must close, not time out: $end", end.exceptionOrNull() is java.net.SocketException)
            }
        } finally { clients.forEach { it.close() } }
        assertTrue(runCatching { Socket("127.0.0.1", port).use { } }.isFailure)
    }

    @Test fun emptyFilesAndOversizedSuffixRangesAreHandled() {
        assertNull(QrFileServer.parseRange(null, 0))
        assertEquals(0L..9L, QrFileServer.parseRange("bytes=-9223372036854775807", 10))
        assertTrue(runCatching { QrFileServer.parseRange("bytes=0-", 0) }.isFailure)
        val file = Files.createTempFile("qr-empty", ".txt").toFile()
        try {
            QrFileServer(file, "empty", token, "Save").use { server ->
                assertEquals(0, request(server, server.path + "file").body.size)
                assertEquals(200, request(server, server.path + "file").status)
            }
        } finally { file.delete() }
    }
}
