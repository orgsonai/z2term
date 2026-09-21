package com.zerotoship.z2term.share

import com.zerotoship.z2term.edge.EdgeCommandScript
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ProcessTextCommandTest {
    @Test fun selectedTextIsLiteralAndEmptyOutputAndTrailingNewlinesArePreserved() {
        val home = Files.createTempDirectory("process-text").toFile()
        try {
            val dir = File(home, ".z2term/edge/.runtime").apply { mkdirs() }
            fun run(command: String, input: String): String {
                File(dir, "selection.in").writeText(input)
                val script = EdgeCommandScript.create("selection", command, null, null, inputFile = true)
                assertTrue(script.length < 1024)
                val p = ProcessBuilder("sh", "-c", script).directory(home).apply { environment()["HOME"] = home.path }.start()
                assertEquals(0, p.waitFor())
                assertEquals("0", File(dir, "selection.status").readText())
                return File(dir, "selection.out").readText()
            }
            val input = "日本語 '$ \" `touch injected` $(touch injected)\n\n"
            assertEquals(input, run("cat", input))
            assertFalse(File(home, "injected").exists())
            val limit = "'".repeat(65536)
            assertEquals(limit, run("cat", limit))
            assertEquals("", run("cat >/dev/null", input))
            assertEquals("ABC\n\n", run("tr '[:lower:]' '[:upper:]'", "abc\n\n"))
        } finally { home.deleteRecursively() }
    }
}
