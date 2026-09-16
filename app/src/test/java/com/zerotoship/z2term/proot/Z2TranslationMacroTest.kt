package com.zerotoship.z2term.proot

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Z2TranslationMacroTest {
    private fun fixture(block: (File, File) -> Unit) {
        val root = Files.createTempDirectory("translation-wrapper").toFile()
        try {
            val script = File(root, "translate.sh").apply { writeText(z2TranslationMacro("ja")) }
            block(root, script)
        } finally { root.deleteRecursively() }
    }
    private fun run(root: File, script: File, vararg args: String): Pair<Int, String> {
        val process = ProcessBuilder(listOf("/bin/sh", script.path) + args).apply {
            environment()["PATH"] = root.path
            environment()["SOURCE_LANG"] = "should-not-leak"
            environment()["TARGET_LANG"] = "should-not-leak"
        }.redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        return process.waitFor() to out
    }
    private fun translator(root: File) = File(root, "trans").apply {
        writeText("#!/bin/sh\nprintf '<%s>\\n' \"\$@\"\nprintf 'env=%s,%s\\n' \"\${SOURCE_LANG-}\" \"\${TARGET_LANG-}\"\n")
        setExecutable(true)
    }

    @Test fun missingTranslatorExplainsManualSetupWithoutDownloading() = fixture { root, script ->
        val (code, output) = run(root, script, "hello")
        assertEquals(127, code)
        assertTrue(output.contains("ユーザー自身で導入"))
        assertEquals(listOf("translate.sh"), root.listFiles()!!.map { it.name })
    }
    @Test fun textIsOneLiteralArgumentAndAutomaticSourceDoesNotInheritEnvironment() = fixture { root, script ->
        translator(root)
        val text = "--help ' \" \$(exit 3);\n改行"
        val (code, output) = run(root, script, "--", text)
        assertEquals(0, code)
        assertTrue(output.endsWith("<-t>\n<ja>\n<-->\n<$text>\nenv=,\n"))
        assertTrue(output.contains("<-no-init>"))
        assertFalse(output.contains("<-s>"))
    }
    @Test fun manualLanguagesAndBackendFailureArePreserved() = fixture { root, script ->
        val backend = translator(root)
        val (code, output) = run(root, script, "bonjour", "en", "fr")
        assertEquals(0, code)
        assertTrue(output.contains("<-t>\n<en>\n<-s>\n<fr>\n<-->\n<bonjour>"))
        backend.writeText("#!/bin/sh\nprintf 'offline' >&2\nexit 7\n")
        assertEquals(7 to "offline", run(root, script, "hello"))
    }
    @Test fun invalidLanguageAndEmptyInputNeverInvokeBackend() = fixture { root, script ->
        translator(root)
        for (args in listOf(arrayOf(""), arrayOf("file:///tmp/private"), arrayOf("https://example.com"), arrayOf("hello", "auto"), arrayOf("hello", "--help"),
            arrayOf("hello", "ja", "en;exit 0"), arrayOf("hello", "ja", "auto", "extra"))) {
            assertEquals(args.toList().toString(), 2, run(root, script, *args).first)
        }
    }
    @Test fun helpInEveryLanguageWorksWithoutDependencies() = fixture { root, script ->
        for (lang in listOf("ja", "en", "zh-CN", "zh-TW", "es", "ko")) {
            script.writeText(z2TranslationMacro(lang))
            val result = run(root, script, "--help")
            assertEquals(result.second, 0, result.first)
            assertTrue(result.second.contains("Usage:"))
        }
    }
}
