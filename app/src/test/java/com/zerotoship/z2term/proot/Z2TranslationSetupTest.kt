package com.zerotoship.z2term.proot

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Z2TranslationSetupTest {
    private fun hint(release: String?, fallback: String? = null, lang: String = "ja"): String {
        val root = Files.createTempDirectory("translation-setup").toFile()
        try {
            val primary = File(root, "os-release")
            val secondary = File(root, "fallback-release")
            release?.let { primary.writeText(it) }
            fallback?.let { secondary.writeText(it) }
            val marker = File(root, "executed")
            for (command in listOf("pacman", "apk", "apt-get")) {
                File(root, command).apply {
                    writeText("#!/bin/sh\nprintf called > executed\nexit 99\n")
                    setExecutable(true)
                }
            }
            val script = File(root, "setup.sh").apply {
                writeText("#!/bin/sh\n" + z2TranslationSetup(lang) + "\ntranslation_setup \"\$@\"\n")
            }
            val process = ProcessBuilder("/bin/sh", script.path, primary.path, secondary.path)
                .directory(root).apply { environment()["PATH"] = root.path }
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.waitFor())
            assertFalse("Setup must only print commands; metadata must stay data", marker.exists())
            return output
        } finally { root.deleteRecursively() }
    }

    @Test fun supportedDistributionsPrintTheirOwnInstallCommand() {
        val expected = mapOf(
            "arch" to "pacman -S --needed translate-shell",
            "archarm" to "pacman -S --needed translate-shell",
            "alpine" to "apk update && apk add translate-shell",
            "ubuntu" to "apt-get update && apt-get install translate-shell",
            "debian" to "apt-get update && apt-get install translate-shell",
            "kali" to "apt-get update && apt-get install translate-shell",
        )
        for ((id, command) in expected) {
            val output = hint("ID=\"$id\"\n")
            assertTrue(output, output.contains(command))
            assertEquals(output, 1, output.lines().count { it.contains("translate-shell") })
            assertEquals(output, id == "alpine", output.contains("community"))
            assertEquals(output, id == "ubuntu", output.contains("universe"))
        }
    }

    @Test fun relatedFamilyAndFallbackReleaseAreSupported() {
        assertTrue(hint("ID=custom\nID_LIKE='ubuntu debian'\n").contains("apt-get update"))
        assertTrue(hint("ID=custom\nID_LIKE=\"arch linux\"\n").contains("pacman -S"))
        assertTrue(hint("ID=alpine\nID_LIKE=debian\n").contains("apk update"))
        assertTrue(hint(null, "ID='arch'").contains("pacman -S"))
        assertTrue(hint("ID=ubuntu\n", "ID=arch\n").contains("apt-get update"))
    }

    @Test fun unknownOrUnavailableMetadataShowsCommandListWithoutExecutingIt() {
        for (release in listOf(null, "", "ID=unknown\n", "ID_LIKE=notarch\n",
            "ID=\"\$(printf injected > executed)\"\nprintf injected > executed\n")) {
            val output = hint(release)
            assertTrue(output.contains("判別できません"))
            assertTrue(output.contains("pacman -S"))
            assertTrue(output.contains("apk update"))
            assertTrue(output.contains("apt-get update"))
        }
    }

    @Test fun everyLanguageIncludesTheInstallCommand() {
        for (lang in listOf("ja", "en", "zh-CN", "zh-TW", "es", "ko")) {
            assertTrue(hint("ID=arch\n", lang = lang).contains("pacman -S --needed translate-shell"))
        }
    }
}
