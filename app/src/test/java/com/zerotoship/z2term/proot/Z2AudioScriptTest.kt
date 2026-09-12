package com.zerotoship.z2term.proot

import com.zerotoship.z2term.settings.AppLanguages
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class Z2AudioScriptTest {
    private fun fixture(block: (File, File) -> Unit) {
        val dir = Files.createTempDirectory("audio-cli").toFile()
        try {
            val script = dir.resolve("z2-audio").apply { writeText(z2AudioScript("ja")) }
            fun stub(name: String, body: String) = dir.resolve(name).apply {
                writeText("#!/bin/sh\n$body\n")
                check(setExecutable(true))
            }
            stub("z2api", """
                printf '%s\n' "${'$'}3" >> "${'$'}AUDIO_TEST_DIR/api.log"
                case "${'$'}3" in
                  open) echo 'abcd-1234 45678' ;;
                  ready) echo ready ;;
                  close) echo closed ;;
                esac
            """.trimIndent())
            stub("pactl", "test -f \"${'$'}AUDIO_TEST_DIR/pulse-alive\"")
            stub("pulseaudio", """
                echo "${'$'}PULSE_RUNTIME_PATH" > "${'$'}AUDIO_TEST_DIR/runtime"
                if [ "${'$'}{AUDIO_TEST_FAIL:-0}" = 1 ]; then echo startup-failed >&2; exit 1; fi
                touch "${'$'}AUDIO_TEST_DIR/pulse-alive"
                trap 'rm -f "${'$'}AUDIO_TEST_DIR/pulse-alive"; exit 0' TERM INT HUP
                while :; do sleep 0.1; done
            """.trimIndent())
            stub("child", """
                printf '%s\n' "${'$'}@" > "${'$'}AUDIO_TEST_DIR/args"
                printf '%s\n' "${'$'}PULSE_SERVER" "${'$'}XDG_RUNTIME_DIR" > "${'$'}AUDIO_TEST_DIR/env"
                cat > "${'$'}AUDIO_TEST_DIR/stdin"
                exit 7
            """.trimIndent())
            block(dir, script)
        } finally { dir.deleteRecursively() }
    }

    private fun run(dir: File, script: File, args: List<String>, fail: Boolean = false): Pair<Int, String> {
        val log = dir.resolve("output")
        val process = ProcessBuilder(listOf("sh", script.path) + args)
            .redirectErrorStream(true).redirectOutput(log).apply {
                environment()["PATH"] = dir.path + ":" + System.getenv("PATH")
                environment()["Z2_SESSION_ID"] = "test-session"
                environment()["Z2_DISTRO_ID"] = "test-os"
                environment()["XDG_RUNTIME_DIR"] = "/tmp/existing-gui-runtime"
                environment()["AUDIO_TEST_DIR"] = dir.path
                environment()["AUDIO_TEST_FAIL"] = if (fail) "1" else "0"
            }.start()
        process.outputStream.use { it.write("input text\n".toByteArray()) }
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            fail("audio wrapper timed out: ${log.readText()}")
        }
        return process.exitValue() to log.readText()
    }

    @Test fun helpAndAllTranslationsParseWithoutStartingAudio() {
        fixture { dir, script ->
            for (lang in AppLanguages.CODES) {
                script.writeText(z2AudioScript(lang))
                val syntax = ProcessBuilder("sh", "-n", script.path).start()
                assertEquals(lang, 0, syntax.waitFor())
            }
            for (args in listOf(emptyList(), listOf("-h"), listOf("--help"), listOf("help"))) {
                assertEquals(0, run(dir, script, args).first)
            }
            assertFalse(dir.resolve("api.log").exists())
        }
    }

    @Test fun preservesArgumentsStdinExitCodeAndReleasesOnlyItsOutput() {
        fixture { dir, script ->
            val args = listOf("a b", "", "x;y", "${'$'}(touch never)")
            val result = run(dir, script, listOf("run", "child") + args)
            assertEquals(result.second, 7, result.first)
            assertEquals(args.joinToString("\n", postfix = "\n"), dir.resolve("args").readText())
            assertEquals("input text\n", dir.resolve("stdin").readText())
            val runtime = dir.resolve("runtime").readText().trim()
            assertEquals("unix:$runtime/native\n/tmp/existing-gui-runtime\n", dir.resolve("env").readText())
            assertFalse(File(runtime).exists())
            assertFalse(dir.resolve("pulse-alive").exists())
            assertEquals("open", dir.resolve("api.log").readLines().first())
            assertEquals("close", dir.resolve("api.log").readLines().last())
        }
    }

    @Test fun startupFailureDoesNotRunCommandAndReleasesLease() {
        fixture { dir, script ->
            val result = run(dir, script, listOf("run", "child"), fail = true)
            assertEquals(1, result.first)
            assertTrue(result.second, result.second.contains("startup-failed"))
            assertFalse(dir.resolve("args").exists())
            assertEquals("close", dir.resolve("api.log").readLines().last())
            assertFalse(File(dir.resolve("runtime").readText().trim()).exists())
        }
    }
}
