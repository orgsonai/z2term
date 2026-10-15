package com.zerotoship.z2term.proot

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ViewerCliTest {
    @Test fun routingAndControlsAreTransportedAndOversizeHtmlIsRejected() {
        val dir = Files.createTempDirectory("viewer-cli").toFile()
        try {
            val staging = File(dir, "stage").apply { mkdirs() }
            val calls = File(dir, "calls")
            val api = File(dir, "api").apply {
                writeText("""#!/bin/sh
printf '%s\n' "${'$'}@" > '${calls.path}'
rm -f '${dir.path}'/received-page.*
for f in '${staging.path}/'"${'$'}3"/page.*; do cp "${'$'}f" '${dir.path}/received-'"${'$'}{f##*/}"; done
if [ -f '${staging.path}/'"${'$'}3"/controls.json ]; then cp '${staging.path}/'"${'$'}3"/controls.json '${dir.path}/received.json'; fi
""")
                setExecutable(true)
            }
            val script = File(dir, "view").apply {
                writeText(z2ApiScripts().getValue("z2-view").replace("DIR=/storage/app/z2api", "DIR='${staging.path}'")
                    .replace("/usr/local/bin/z2api", "'${api.path}'"))
            }
            val html = File(dir, "page.html").apply { writeText("<p>日本語</p>") }
            val controls = File(dir, "controls.json").apply { writeText("{\"handler\":\"example.sh\"}") }
            fun feed(input: String?, vararg args: String): Int {
                val p = ProcessBuilder(listOf("sh", script.path) + args).apply {
                    environment()["Z2_VIEW_TARGET"] = "tools:page"
                    environment()["Z2_VIEW_SESSION"] = "session-token"
                }.redirectErrorStream(true).start()
                p.outputStream.use { if (input != null) it.write(input.toByteArray()) }
                p.inputStream.bufferedReader().readText()
                return p.waitFor()
            }
            fun run(vararg args: String) = feed(null, *args)
            val received = File(dir, "received-page.html")
            val receivedText = File(dir, "received-page.txt")
            assertEquals(0, run("--controls", controls.path, html.path, "Title"))
            // z2api 1 view STAGE TITLE TARGET SESSION PINCH TEXT-SIZE HIDE
            assertEquals(listOf("tools:page", "session-token", "", "", ""), calls.readLines().drop(4))
            assertEquals(controls.readText(), File(dir, "received.json").readText())
            assertEquals(html.readText(), received.readText())
            assertEquals(0, run("--edge", "other:page", html.path))
            assertEquals(listOf("other:page", ""), calls.readLines().drop(4).take(2))
            // Pinch and text size for the full screen travel as the last two arguments; bad values never reach the app.
            assertEquals(0, run("--pinch", "page", "--text-size", "120", html.path))
            assertEquals(listOf("page", "120", ""), calls.readLines().drop(6))
            // The full screen shows Refresh and Find unless told not to.
            assertEquals(0, run("--no-find", "--no-refresh", html.path))
            assertEquals("find,refresh", calls.readLines().last())
            calls.delete()
            for (bad in listOf(listOf("--pinch", "zoom"), listOf("--pinch"), listOf("--text-size", "49"),
                listOf("--text-size", "301"), listOf("--text-size", "1e2"), listOf("--text-size"))) {
                assertNotEquals(0, run(*(bad + html.path).toTypedArray()))
                assertFalse(calls.exists())
            }
            assertTrue(staging.listFiles()!!.isEmpty())
            calls.delete()
            html.writeBytes(ByteArray(4 * 1024 * 1024 + 1) { 65 })
            assertNotEquals(0, run(html.path))
            assertFalse(calls.exists())
            assertTrue(staging.listFiles()!!.isEmpty())
            html.writeBytes(ByteArray(4 * 1024 * 1024) { 65 })
            assertEquals(0, run(html.path))
            assertEquals(html.length(), received.length())

            // Standard input: plain text goes over as text, a page as a page; files decide by name, then content.
            assertEquals(0, feed("total 0\n<not a tag at the start>\n"))
            assertEquals("total 0\n<not a tag at the start>\n", receivedText.readText()); assertFalse(received.exists())
            assertEquals(0, feed("  <p>page</p>", "-", "Title"))
            assertTrue(received.exists()); assertFalse(receivedText.exists())
            assertEquals(0, feed("<p>shown as text</p>", "--text", "-"))
            assertTrue(receivedText.exists())
            val log = File(dir, "notes.log").apply { writeText("plain words") }
            assertEquals(0, run(log.path)); assertTrue(receivedText.exists())
            assertEquals(0, run("--html", log.path)); assertTrue(received.exists())
            assertNotEquals(0, run("-x"))
            assertTrue(staging.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
