package com.zerotoship.z2term.proot

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class Z2ActionScriptTest {
    @Test fun savePreservesMultilineTextAndRunPropagatesFailure() {
        val dir = Files.createTempDirectory("action-cli").toFile()
        try {
            dir.resolve("z2api").apply {
                writeText("#!/bin/sh\ncase \"\$3\" in\n start) echo run-123 ;;\n wait) echo finished; exit 7 ;;\n *) printf '%s\\n' \"\$@\" ;;\nesac\n")
                check(setExecutable(true))
            }
            val script = dir.resolve("z2-action").apply { writeText(z2ActionScript("ja")) }
            val source = dir.resolve("with spaces.actions").apply { writeText("version=1\ncommand printf '%s' \"a=b\"\n") }
            fun run(vararg args: String): Pair<Int, String> {
                val p = ProcessBuilder(listOf("sh", script.path) + args).redirectErrorStream(true).apply {
                    environment()["PATH"] = dir.path + ":" + System.getenv("PATH")
                }.start()
                val output = p.inputStream.bufferedReader().readText()
                return p.waitFor() to output
            }
            assertEquals(0 to "1\naction\nsave\nsample\n${source.readText()}\n", run("save", "sample", source.path))
            assertEquals(7 to "finished\n", run("run", "sample"))
            assertNotEquals(0, run("save", "sample", dir.resolve("missing").path).first)
            source.writeText("x".repeat(65537))
            assertNotEquals(0, run("save", "sample", source.path).first)
        } finally { dir.deleteRecursively() }
    }
}
