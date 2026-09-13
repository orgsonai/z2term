package com.zerotoship.z2term.share

import com.zerotoship.z2term.proot.AndroidShellScripts
import com.zerotoship.z2term.proot.z2ApiScripts
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class QrShareScriptTest {
    @Test fun qrOptionsPreserveQuotedPathsAndExistingTextSharing() {
        val dir = Files.createTempDirectory("qr-cli ' ").toFile()
        try {
            val recorder = File(dir, "api").apply { writeText("#!/bin/sh\nprintf '%s\\0' \"\$@\"\n"); setExecutable(true) }
            val script = File(dir, "share").apply { writeText(z2ApiScripts("en").getValue("z2-share")
                .replace("/usr/local/bin/z2api", AndroidShellScripts.quote(recorder.path))) }
            val filename = "日本語 '\" \$(touch unexpected).pdf"
            File(dir, filename).writeText("sample")
            fun call(vararg args: String): Pair<Int, List<String>> {
                val p = ProcessBuilder(listOf("sh", script.path) + args).directory(dir).apply {
                    environment()["HOME"] = dir.path; environment()["PWD"] = dir.path
                    environment()["Z2_DISTRO_ID"] = ""
                }.start()
                val output = p.inputStream.readBytes().toString(Charsets.UTF_8)
                p.errorStream.readBytes()
                return p.waitFor() to output.split('\u0000').dropLast(1)
            }
            assertEquals(0 to listOf("1", "qr-share", "start", "${dir.path}/$filename", dir.path, ""), call("--qr", filename))
            assertFalse(File(dir, "unexpected").exists())
            assertEquals(0 to listOf("1", "qr-share", "show"), call("--qr"))
            assertEquals(0 to listOf("1", "qr-share", "status"), call("--qr-status"))
            assertEquals(0 to listOf("1", "qr-share", "stop"), call("--qr-stop"))
            assertEquals(0 to listOf("0", "share", "hello --qr ' literal"), call("hello", "--qr", "' literal"))
            assertEquals(0 to listOf("0", "share", "help"), call("help"))
            assertEquals(0 to listOf("0", "share", "--qr"), call("--", "--qr"))
            for (args in listOf(arrayOf("--qr", "missing"), arrayOf("--qr", "."), arrayOf("--qr", filename, "extra"), arrayOf("--qr-stop", "extra"))) {
                assertEquals(1, call(*args).first)
            }
        } finally { dir.deleteRecursively() }
    }
}
