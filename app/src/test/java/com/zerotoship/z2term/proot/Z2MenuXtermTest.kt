package com.zerotoship.z2term.proot

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class Z2MenuXtermTest {

    @Test
    fun xtermUsesXftInsteadOfAlpineCoreFont() {
        val dir = Files.createTempDirectory("z2menu-xterm").toFile()
        try {
            val home = dir.resolve("home")
            val apps = home.resolve(".local/share/applications").apply { mkdirs() }
            apps.resolve("xterm.desktop").writeText(
                """
                [Desktop Entry]
                Type=Application
                Name=XTerm
                Exec=xterm
                Terminal=false
                Categories=System;
                """.trimIndent() + "\n"
            )
            val bin = dir.resolve("bin").apply { mkdirs() }
            bin.resolve("xterm").apply {
                writeText("#!/bin/sh\nexit 0\n")
                setExecutable(true)
            }
            val script = dir.resolve("z2menu").apply {
                writeText(z2menuScript("en"))
                setExecutable(true)
            }

            val process = ProcessBuilder("sh", script.absolutePath, "list")
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = home.absolutePath
                    environment()["PATH"] = "${bin.absolutePath}:/usr/bin:/bin"
                }
                .start()
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(process.waitFor() == 0)
            assertTrue(output.contains("\txterm -fa monospace -fs 11\t"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
