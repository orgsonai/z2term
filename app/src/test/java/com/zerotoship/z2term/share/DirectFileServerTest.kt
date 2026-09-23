package com.zerotoship.z2term.share

import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectFileServerTest {
    @get:Rule val files = TemporaryFolder()
    private val origin = "http://share.example:8080"

    private fun file(value: String = "abcdef"): File = files.newFile().apply { writeText(value) }
    private fun server(snapshot: File = file(), clock: () -> Long = System::nanoTime) =
        DirectFileServer(snapshot, "<report & \"notes\">.txt", origin, 0, false, 60_000, nanoTime = clock)

    private data class Reply(val code: Int, val headers: Map<String, String>, val body: String)
    private fun request(server: DirectFileServer, target: String, method: String = "GET",
                        headers: Map<String, String> = emptyMap()): Reply =
        Socket(InetAddress.getByName("127.0.0.1"), server.localPort).use { socket ->
            socket.soTimeout = 3000
            val wire = buildString {
                append(method + " " + target + " HTTP/1.1\r\nHost: share.example:8080\r\n")
                headers.forEach { (key, value) -> append(key + ": " + value + "\r\n") }
                append("\r\n")
            }
            socket.getOutputStream().write(wire.toByteArray(Charsets.US_ASCII))
            val received = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val head = received.substringBefore("\r\n\r\n").split("\r\n")
            Reply(head.first().split(' ')[1].toInt(), head.drop(1).associate {
                it.substringBefore(':').lowercase() to it.substringAfter(':').trim()
            }, received.substringAfter("\r\n\r\n"))
        }

    private fun consent(server: DirectFileServer): String {
        val accepted = request(server, server.path + "accept", "POST",
            mapOf("Origin" to origin, "Sec-Fetch-Site" to "same-origin", "Content-Length" to "0"))
        assertEquals(303, accepted.code)
        assertEquals(server.path + "file", accepted.headers["location"])
        val cookie = accepted.headers.getValue("set-cookie")
        assertTrue(cookie.contains("HttpOnly"))
        assertTrue(cookie.contains("SameSite=Strict"))
        return cookie.substringBefore(';')
    }

    @Test fun relayHealthDoesNotCountAsAVisitOrDownloadAndRequiresTheToken() {
        val visits = AtomicLong()
        val bytes = AtomicLong()
        DirectFileServer(file(), "report.txt", null, 0, false, 60_000,
            onVisit = { visits.incrementAndGet() }, onBytes = { bytes.addAndGet(it) },
            bindAddress = InetAddress.getByName("127.0.0.1")).use { server ->
            val health = request(server, server.path + "health")
            assertEquals(200, health.code)
            assertEquals(server.token, health.body)
            assertEquals(0L, visits.get())
            assertEquals(0L, bytes.get())
            assertEquals(404, request(server, "/health").code)
            assertEquals(405, request(server, server.path + "health", "POST").code)
        }
    }

    @Test fun relayConsentUsesPublicHttpsOriginAndExpiryStartsAfterConnecting() {
        val clock = AtomicLong(0)
        DirectFileServer(file(), "report.txt", null, 0, false, 60_000, nanoTime = clock::get,
            bindAddress = InetAddress.getByName("127.0.0.1")).use { server ->
            val localOrigin = server.url.substringBefore(server.path)
            clock.set(50_000_000_000L)
            server.publishViaRelay("https://share.example")
            assertEquals("https://share.example" + server.path, server.url)
            assertTrue(runCatching { server.publishViaRelay("https://share.example") }.isFailure)
            assertEquals(403, request(server, server.path + "accept", "POST", mapOf("Origin" to localOrigin)).code)
            val accepted = request(server, server.path + "accept", "POST", mapOf("Origin" to "https://share.example"))
            assertEquals(303, accepted.code)
            assertTrue(accepted.headers.getValue("set-cookie").contains("; Secure"))
            clock.set(70_000_000_000L)
            assertEquals(200, request(server, server.path + "file", headers = mapOf(
                "Cookie" to accepted.headers.getValue("set-cookie").substringBefore(';'))).code)
            clock.set(111_000_000_000L)
            assertEquals(410, request(server, server.path).code)
        }
    }

    @Test fun confirmationPageEscapesNamesAndNeverContainsFileBytes() {
        server().use { server ->
            val page = request(server, server.path, headers = mapOf("Accept-Language" to "ja"))
            assertEquals(200, page.code)
            assertTrue(page.body.contains("&lt;report &amp; &quot;notes&quot;&gt;.txt"))
            assertTrue(page.body.contains("受信して保存"))
            assertFalse(page.body.contains("abcdef"))
            assertEquals("nosniff", page.headers["x-content-type-options"])
            assertEquals("same-origin", page.headers["referrer-policy"])
            assertTrue(page.headers.getValue("content-security-policy").contains("frame-ancestors 'none'"))
            assertEquals(403, request(server, server.path + "file").code)
            assertEquals(405, request(server, server.path + "accept").code)
        }
    }

    @Test fun explicitAcceptanceEnablesAttachmentAndRangeDownload() {
        server().use { server ->
            val cookie = consent(server)
            val full = request(server, server.path + "file", headers = mapOf("Cookie" to cookie))
            assertEquals(200, full.code)
            assertEquals("abcdef", full.body)
            assertTrue(full.headers.getValue("content-disposition").startsWith("attachment;"))
            val part = request(server, server.path + "file",
                headers = mapOf("Cookie" to cookie, "Range" to "bytes=2-4"))
            assertEquals(206, part.code)
            assertEquals("cde", part.body)
            assertEquals("bytes 2-4/6", part.headers["content-range"])
            val head = request(server, server.path + "file", "HEAD", mapOf("Cookie" to cookie))
            assertEquals("6", head.headers["content-length"])
            assertEquals("", head.body)
        }
    }

    @Test fun wrongTokenTraversalAndCrossOriginConsentCannotReadFiles() {
        server().use { server ->
            for (path in listOf("/", "/share/wrong/", server.path + "../", server.path + "%2e%2e/")) {
                assertEquals(path, 404, request(server, path).code)
            }
            assertEquals(403, request(server, server.path + "accept", "POST",
                mapOf("Origin" to "https://attacker.example")).code)
            assertEquals(403, request(server, server.path + "accept", "POST",
                mapOf("Origin" to "null", "Sec-Fetch-Site" to "same-origin")).code)
            assertEquals(403, request(server, server.path + "accept", "POST",
                mapOf("Sec-Fetch-Site" to "cross-site")).code)
            assertEquals(400, request(server, server.path + "accept", "POST",
                mapOf("Transfer-Encoding" to "chunked")).code)
            assertEquals(403, request(server, server.path + "file",
                headers = mapOf("Cookie" to "receive=wrong")).code)
        }
    }

    @Test fun emptyFileAndInvalidRangesAreHandledWithoutLeakingData() {
        server(file("")).use { server ->
            val cookie = consent(server)
            val full = request(server, server.path + "file", headers = mapOf("Cookie" to cookie))
            assertEquals(200, full.code)
            assertEquals("0", full.headers["content-length"])
            assertEquals("", full.body)
            val range = request(server, server.path + "file", headers = mapOf("Cookie" to cookie, "Range" to "bytes=0-"))
            assertEquals(416, range.code)
            assertEquals("bytes */0", range.headers["content-range"])
        }
    }

    @Test fun expiredAndClosedSharesCannotBeUsedAndNewSharesHaveNewTokens() {
        val clock = AtomicLong(0)
        val first = server(clock = clock::get)
        val token = first.token
        try {
            val cookie = consent(first)
            clock.set(60_000_000_000L)
            assertEquals(410, request(first, first.path + "file", headers = mapOf("Cookie" to cookie)).code)
        } finally { first.close() }
        // Asked of the share itself: probing the old port number can meet another listener that
        // the system has since given the same number to, which made this test fail at random.
        assertFalse(first.listening)
        server().use { assertNotEquals(token, it.token) }
    }

    @Test fun loopbackOriginUsesTheBoundPortForQrAndBrowserConsent() {
        val loopback = InetAddress.getByName("127.0.0.1")
        DirectFileServer(file(), "report.txt", null, 0, false, 60_000, bindAddress = loopback).use { server ->
            val actualOrigin = "http://127.0.0.1:" + server.localPort
            assertTrue(server.localPort > 0)
            assertEquals(actualOrigin + server.path, server.url)
            assertEquals(200, request(server, server.path).code)
            assertEquals(403, request(server, server.path + "accept", "POST", mapOf("Origin" to origin)).code)
            val accepted = request(server, server.path + "accept", "POST",
                mapOf("Origin" to actualOrigin, "Sec-Fetch-Site" to "same-origin"))
            assertEquals(303, accepted.code)
            val cookie = accepted.headers.getValue("set-cookie").substringBefore(';')
            assertEquals("abcdef", request(server, server.path + "file", headers = mapOf("Cookie" to cookie)).body)
        }
    }

    @Test fun stoppingClosesOpenConnectionsAndLeavesOtherServersRunning() {
        server().use { other ->
            val shared = DirectFileServer(file(), "report.txt", null, 0, false, 60_000,
                bindAddress = InetAddress.getByName("127.0.0.1"))
            val port = shared.localPort
            try {
                Socket("127.0.0.1", port).use { pending ->
                    pending.soTimeout = 3000
                    // A client stalled partway through its headers must not keep a stopped share alive.
                    pending.getOutputStream().write(("GET " + shared.path + " HTTP/1.1\r\n").toByteArray())
                    shared.close()
                    shared.close() // Repeated Stop/expiry cleanup is harmless.
                    try { assertEquals(-1, pending.getInputStream().read()) }
                    catch (_: java.net.SocketException) { /* Reset also means the connection is closed. */ }
                }
                assertTrue(runCatching { Socket("127.0.0.1", port).close() }.isFailure)
                assertEquals(200, request(other, other.path).code)
            } finally { shared.close() }
        }
    }

    private fun folderServer(): DirectFileServer {
        val content = DirectShareContent(listOf(
            DirectShareContent.Item(0, "共有", null, null),
            DirectShareContent.Item(1, "<資料>", 0, null),
            DirectShareContent.Item(2, "same.txt", 1, file("nested-data")),
            DirectShareContent.Item(3, "空", 0, null),
            DirectShareContent.Item(4, "same.txt", 0, file("root-data")),
        ))
        return DirectFileServer(files.newFolder(), content.root.name, origin, 0, false, 60_000, folderContent = content)
    }

    @Test fun foldersCanBeBrowsedWithoutSendingFileContents() {
        folderServer().use { server ->
            val root = request(server, server.path, headers = mapOf("Accept-Language" to "ja"))
            assertEquals(200, root.code)
            assertTrue(root.body.contains("&lt;資料&gt;"))
            assertTrue(root.body.contains(server.path + "folder/1/"))
            assertTrue(root.body.contains(server.path + "item/4/"))
            assertFalse(root.body.contains("root-data"))
            assertFalse(root.body.contains("nested-data"))
            assertFalse(root.body.contains("method=\"post\""))
            val nested = request(server, server.path + "folder/1/")
            assertTrue(nested.body.contains(server.path + "folder/0/"))
            assertTrue(nested.body.contains(server.path + "item/2/"))
            val empty = request(server, server.path + "folder/3/", headers = mapOf("Accept-Language" to "ja"))
            assertTrue(empty.body.contains("このフォルダは空です"))
            val confirmation = request(server, server.path + "item/2/")
            assertTrue(confirmation.body.contains("Receive this file?"))
            assertTrue(confirmation.body.contains(server.path + "item/2/accept"))
            assertFalse(confirmation.body.contains("nested-data"))
        }
    }

    @Test fun eachFolderFileNeedsItsOwnConsentAndSupportsRangeDownload() {
        folderServer().use { server ->
            val nested = server.path + "item/2/"
            val other = server.path + "item/4/"
            assertEquals(403, request(server, nested + "file").code)
            val accepted = request(server, nested + "accept", "POST", mapOf("Origin" to origin))
            assertEquals(303, accepted.code)
            assertEquals(nested + "file", accepted.headers["location"])
            val cookie = accepted.headers.getValue("set-cookie")
            assertTrue(cookie.contains("Path=" + nested + ";"))
            val headers = mapOf("Cookie" to cookie.substringBefore(';'))
            assertEquals("nested-data", request(server, nested + "file", headers = headers).body)
            assertEquals(403, request(server, other + "file", headers = headers).code)
            val range = request(server, nested + "file", headers = headers + ("Range" to "bytes=0-5"))
            assertEquals(206, range.code)
            assertEquals("nested", range.body)
            val head = request(server, nested + "file", "HEAD", headers)
            assertEquals(200, head.code)
            assertEquals("", head.body)
            assertEquals(403, request(server, other + "accept", "POST",
                mapOf("Origin" to "https://attacker.example")).code)
        }
    }

    @Test fun folderRoutesCannotEscapeTheSelectionOrReadDirectoriesAsFiles() {
        folderServer().use { server ->
            for (suffix in listOf("../", "%2e%2e/", "file", "accept", "folder/1/file", "folder/1/accept",
                "item/1/", "folder/2/", "item/9999/file", "item/-1/", "item/02/", "item/2/../../file",
                "item/2/%66ile", "folder/0/../../../")) {
                assertEquals(suffix, 404, request(server, server.path + suffix).code)
            }
            assertEquals(404, request(server, "/").code)
            assertEquals(404, request(server, "/share/wrong/folder/0/").code)
        }
    }

    @Test fun suffixClampingOverflowAndMultipleRanges() {
        assertEquals(4L..5L, DirectFileServer.parseRange("bytes=-2", 6))
        assertEquals(0L..5L, DirectFileServer.parseRange("bytes=-100", 6))
        assertEquals(2L..5L, DirectFileServer.parseRange("bytes=2-999", 6))
        for (value in listOf("bytes=-0", "bytes=6-", "bytes=4-2", "bytes=0-1,3-4", "bytes=999999999999999999999-")) {
            assertTrue(value, runCatching { DirectFileServer.parseRange(value, 6) }.isFailure)
        }
    }
}
