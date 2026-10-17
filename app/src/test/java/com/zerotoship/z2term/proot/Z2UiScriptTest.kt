package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/** 失敗も標準出力へ JSON で出し、終了コードで成否を分けること (AI は標準出力だけを読むことがある)。 */
class Z2UiScriptTest {
    private fun run(fake: String, vararg args: String): Pair<Int, String> {
        val dir = Files.createTempDirectory("ui-cli").toFile()
        try {
            dir.resolve("z2api").apply { writeText(fake); check(setExecutable(true)) }
            val script = dir.resolve("z2-ui").apply { writeText(z2UiScript("ja")) }
            val p = ProcessBuilder(listOf("sh", script.path) + args).apply {
                environment()["PATH"] = dir.path + ":" + System.getenv("PATH")
            }.start()
            val out = p.inputStream.bufferedReader().readText()
            return p.waitFor() to out
        } finally { dir.deleteRecursively() }
    }

    @Test fun argumentsReachTheAppUnchanged() {
        val (rc, out) = run("#!/bin/sh\nprintf '{\"args\":\"%s\"}\\n' \"\$*\"\n", "type", "#2", "a b")
        assertEquals(0, rc)
        assertEquals("{\"args\":\"1 ui type #2 a b\"}\n", out)
    }

    @Test fun failuresAreJsonOnStdout() {
        val (rc, out) = run("#!/bin/sh\necho '{\"ok\":false,\"error\":\"stale\",\"message\":\"x\"}' >&2\nexit 1\n", "tap", "#1")
        assertEquals(1, rc)
        assertEquals("{\"ok\":false,\"error\":\"stale\",\"message\":\"x\"}\n", out)
    }

    @Test fun noAnswerBecomesAFixedJson() {
        val (rc, out) = run("#!/bin/sh\necho 'z2api: timeout waiting for app' >&2\nexit 1\n", "look")
        assertEquals(1, rc)
        assertEquals("{\"ok\":false,\"error\":\"no_response\",\"message\":\"z2term did not answer\"}\n", out)
    }
}
