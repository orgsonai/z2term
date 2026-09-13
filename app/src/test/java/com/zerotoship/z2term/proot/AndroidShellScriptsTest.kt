package com.zerotoship.z2term.proot

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class AndroidShellScriptsTest {
    @Test fun generatedScriptsParseInEveryLanguageAndLeaveLinuxPathsIntact() {
        val failures = mutableListOf<String>()
        for (lang in listOf("ja", "en", "zh-CN", "zh-TW", "es", "ko")) {
            val dir = Files.createTempDirectory("android cli '").toFile()
            try {
                val linux = z2ApiScripts(lang)
                val scripts = AndroidShellScripts.create(lang, dir.path, "${dir.path}/bridge")
                for ((name, body) in scripts + linux.mapKeys { "linux-${it.key}" }) {
                    val file = File(dir, name).apply { writeText(body) }
                    val process = ProcessBuilder("sh", "-n", file.path).redirectErrorStream(true).start()
                    val out = process.inputStream.bufferedReader().readText()
                    if (process.waitFor() != 0) failures += "$lang/$name: $out"
                }
                assertEquals(linux, z2ApiScripts(lang))
                assertTrue(linux.getValue("z2api").contains("DIR=/storage/app/z2api"))
                assertTrue(linux.getValue("z2-notify").contains("exec /usr/local/bin/z2api"))
            } finally { dir.deleteRecursively() }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun commandsPreserveArgumentsAndWriteRulesToSharedHome() {
        val dir = Files.createTempDirectory("android cli '").toFile()
        try {
            val bin = File(dir, "bin").apply { mkdirs() }
            val bridge = File(dir, "bridge")
            val home = File(dir, "shared_home").apply { mkdirs() }
            AndroidShellScripts.create("en", bin.path, bridge.path).forEach { (name, body) ->
                // Host shell stands in for Android's interpreter; native entry is tested separately.
                File(bin, name).apply {
                    writeText(body.replace("#!/system/bin/sh", "#!/bin/sh"))
                    setExecutable(true)
                }
            }
            fun run(vararg args: String): String {
                val executable = if (args[0].startsWith("z2")) File(bin, args[0]).path else args[0]
                val p = ProcessBuilder(listOf(executable) + args.drop(1)).apply {
                    environment()["HOME"] = home.path
                    environment()["PATH"] = "${bin.path}:${environment()["PATH"]}"
                    environment()["Z2_EDGE_RUN"] = "edge-token"
                }.redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                assertEquals(out, 0, p.waitFor())
                return out
            }
            val body = "日本語\nquoted ' \" \$(exit 7)"
            run("sh", "-c", "z2-notify -n test \"\$1\"", "test", body)
            val request = File(bridge, "req").listFiles()!!.single().readLines()
            fun decode(line: String) = String(Base64.getDecoder().decode(line.substring(2)), Charsets.UTF_8)
            assertEquals("CMD notify", request.first())
            assertEquals(body, decode(request.first { it.startsWith("A ") }))
            assertEquals("edge-token", decode(request.single { it.startsWith("E ") }))

            val id = run("z2-when", "charge:start", "name=charge notice", "run", "z2-notify charged").trim()
            val rule = File(home, ".z2term/when/$id.rule")
            assertTrue(rule.readText().contains("run=z2-notify charged\n"))
            assertTrue(run("z2-when", "list").contains("charge notice"))
            run("z2-when", "off", id)
            assertTrue(rule.readText().contains("enabled=0"))
            run("z2-when", "remove", id)
            assertFalse(rule.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun linuxOnlyCommandsFailBeforeTryingToInstallAnything() {
        val dir = Files.createTempDirectory("android-cli").toFile()
        try {
            val scripts = AndroidShellScripts.create("en", dir.path, "${dir.path}/bridge")
            for (name in listOf("z2-audio", "z2-img")) {
                val file = File(dir, name).apply { writeText(scripts.getValue(name)) }
                val p = ProcessBuilder("sh", file.path, "install").redirectErrorStream(true).start()
                assertTrue(p.inputStream.bufferedReader().readText().contains("install a Linux OS"))
                assertEquals(1, p.waitFor())
            }
        } finally { dir.deleteRecursively() }
    }
}
