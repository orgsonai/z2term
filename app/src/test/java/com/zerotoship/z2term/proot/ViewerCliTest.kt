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
cp '${staging.path}/'"${'$'}3"/page.html '${dir.path}/received.html'
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
            fun run(vararg args: String): Int {
                val p = ProcessBuilder(listOf("sh", script.path) + args).apply {
                    environment()["Z2_VIEW_TARGET"] = "tools:page"
                    environment()["Z2_VIEW_SESSION"] = "session-token"
                }.redirectErrorStream(true).start()
                p.inputStream.bufferedReader().readText()
                return p.waitFor()
            }
            assertEquals(0, run("--controls", controls.path, html.path, "Title"))
            assertEquals(listOf("tools:page", "session-token"), calls.readLines().takeLast(2))
            assertEquals(controls.readText(), File(dir, "received.json").readText())
            assertEquals(html.readText(), File(dir, "received.html").readText())
            assertEquals(0, run("--edge", "other:page", html.path))
            assertEquals(listOf("other:page", ""), calls.readLines().takeLast(2))
            assertTrue(staging.listFiles()!!.isEmpty())
            calls.delete()
            html.writeBytes(ByteArray(4 * 1024 * 1024 + 1) { 65 })
            assertNotEquals(0, run(html.path))
            assertFalse(calls.exists())
            assertTrue(staging.listFiles()!!.isEmpty())
            html.writeBytes(ByteArray(4 * 1024 * 1024) { 65 })
            assertEquals(0, run(html.path))
            assertEquals(html.length(), File(dir, "received.html").length())
        } finally { dir.deleteRecursively() }
    }
}
