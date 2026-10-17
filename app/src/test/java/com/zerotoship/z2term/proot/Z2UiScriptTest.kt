package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/** 失敗も標準出力へ JSON で出し、終了コードで成否を分けること (AI は標準出力だけを読むことがある)。 */
class Z2UiScriptTest {
    private fun run(fake: String, vararg args: String, input: String = ""): Pair<Int, String> {
        val dir = Files.createTempDirectory("ui-cli").toFile()
        try {
            dir.resolve("z2api").apply { writeText(fake); check(setExecutable(true)) }
            val script = dir.resolve("z2-ui").apply { writeText(z2UiScript("ja")) }
            val p = ProcessBuilder(listOf("sh", script.path) + args).apply {
                environment()["PATH"] = dir.path + ":" + System.getenv("PATH")
            }.start()
            p.outputStream.use { it.write(input.toByteArray()) }
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

    @Test fun mcpPassesEachLineAndAnswersForASilentApp() {
        // 1 行目は返事あり、2 行目 (通知) は返事なし、3 行目はアプリが答えない。
        val fake = "#!/bin/sh\ncase \"\$4\" in\n *'\"id\":1'*) echo '{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}' ;;\n" +
            " *initialized*) : ;;\n *) exit 1 ;;\nesac\n"
        val (rc, out) = run(fake, "mcp", input = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}\n" +
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n" +
            "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"method\":\"tools/list\"}\n")
        assertEquals(0, rc)
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n" +
            "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"error\":{\"code\":-32000,\"message\":\"z2term did not answer\"}}\n", out)
    }

    @Test fun notesCanComeFromStdin() {
        val (rc, out) = run("#!/bin/sh\nprintf '{\"args\":\"%s|%s|%s\"}\\n' \"\$3\" \"\$4\" \"\$5\"\n", "note", "org.example.app", "-",
            input = "line 1\nline 2\n")
        assertEquals(0, rc)
        assertEquals("{\"args\":\"note|org.example.app|line 1\nline 2\n\"}\n", out)
    }

    @Test fun noAnswerBecomesAFixedJson() {
        val (rc, out) = run("#!/bin/sh\necho 'z2api: timeout waiting for app' >&2\nexit 1\n", "look")
        assertEquals(1, rc)
        assertEquals("{\"ok\":false,\"error\":\"no_response\",\"message\":\"z2term did not answer\"}\n", out)
    }
}
