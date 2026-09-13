package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EdgeCommandScriptTest {
    @Test fun outputStatusInputAndSelectionUseTheLaunchedHome() {
        val home = Files.createTempDirectory("edge home '\"").toFile()
        try {
            val dir = File(home, ".z2term/edge/.runtime").apply { mkdirs() }
            val input = "first\n日本語 '\" \$(exit 9)\n"
            val value = "selected '\" \$(exit 8)"
            val script = EdgeCommandScript.create("test", "cat; printf '%s' \"\$1\"; printf 'problem' >&2; exit 7", input, value)
            val p = ProcessBuilder("sh", "-c", script).apply { environment()["HOME"] = home.path }
                .redirectErrorStream(true).start()
            val output = p.inputStream.bufferedReader().readText()
            assertEquals(output, 0, p.waitFor())
            assertEquals(input + value, File(dir, "test.out").readText())
            assertEquals("problem", File(dir, "test.err").readText())
            assertEquals("7", File(dir, "test.status").readText())
        } finally { home.deleteRecursively() }
    }
}
