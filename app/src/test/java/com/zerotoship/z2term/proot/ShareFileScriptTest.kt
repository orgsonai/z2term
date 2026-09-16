package com.zerotoship.z2term.proot

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ShareFileScriptTest {
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun run(args: List<String>, apiExit: Int = 0, verify: (File, Int) -> Unit) {
        val dir = Files.createTempDirectory("share-script").toFile()
        try {
            val bridge = File(dir, "bridge").apply { mkdir() }
            val api = File(dir, "z2api").apply {
                writeText("#!/bin/sh\nprintf '%s\\0' \"\$@\" > ${quote(File(dir, "args").path)}\n" +
                    "if [ \"\$2\" = share-files ]; then cat ${quote(bridge.path)}/\"\$3\"/0 > ${quote(File(dir, "data").path)}; fi\nexit $apiExit\n")
                setExecutable(true)
            }
            File(dir, "a ' ; date.png").writeText("image bytes")
            File(dir, "second.pdf").writeText("pdf bytes")
            val script = File(dir, "share.sh").apply {
                writeText(z2ApiScripts().getValue("z2-share")
                    .replace("DIR=/storage/app/z2api", "DIR=${quote(bridge.path)}")
                    .replace("/usr/local/bin/z2api", quote(api.path)))
            }
            val process = ProcessBuilder(listOf("sh", script.path) + args).directory(dir).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
            verify(dir, process.waitFor())
            assertTrue("Staging copies must be removed", bridge.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun filesAndNamesReachTheAppWithoutBecomingShellCode() {
        run(listOf("--file", "a ' ; date.png", "second.pdf")) { dir, code ->
            assertEquals(0, code)
            val args = File(dir, "args").readText().split('\u0000').dropLast(1)
            assertEquals(listOf("1", "share-files"), args.take(2))
            assertEquals(listOf("a ' ; date.png", "second.pdf"), args.drop(3))
            assertEquals("image bytes", File(dir, "data").readText())
        }
    }

    @Test fun failuresAreReportedAndStagingIsCleaned() {
        run(listOf("--file", "missing.png")) { dir, code ->
            assertNotEquals(0, code); assertFalse(File(dir, "args").exists())
        }
        run(listOf("--file", "second.pdf"), apiExit = 9) { _, code -> assertEquals(9, code) }
    }

    @Test fun textModeAndExplicitOptionEscapeArePreserved() {
        run(listOf("hello", "world")) { dir, code ->
            assertEquals(0, code)
            assertEquals(listOf("0", "share", "hello world", ""), File(dir, "args").readText().split('\u0000'))
        }
        run(listOf("--", "--file", "hello")) { dir, code ->
            assertEquals(0, code)
            assertEquals(listOf("0", "share", "--file hello", ""), File(dir, "args").readText().split('\u0000'))
        }
    }
}
