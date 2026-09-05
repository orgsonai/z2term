package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Z2BwrapCompatScriptTest {

    @Test
    fun `generated wrapper parses as posix sh`() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() } ?: return
        val dir = Files.createTempDirectory("z2bwrap-syntax-").toFile()
        try {
            val wrapper = File(dir, "bwrap").apply { writeText(z2BwrapCompatScript()) }
            val process = ProcessBuilder(sh, "-n", wrapper.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals("sh -n failed:\n$output", 0, process.waitFor())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `glycin loader runs directly with requested cwd env and arguments`() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() } ?: return
        val dir = Files.createTempDirectory("z2bwrap-run-").toFile()
        try {
            val wrapper = File(dir, "bwrap").apply { writeText(z2BwrapCompatScript()) }
            val loader = File(dir, "glycin-test").apply {
                writeText(
                    """
                    |#!/bin/sh
                    |printf 'cwd=%s\n' "${'$'}PWD"
                    |printf 'home=%s\n' "${'$'}HOME"
                    |printf 'args=%s\n' "${'$'}*"
                    """.trimMargin()
                )
                setExecutable(true)
            }
            val process = ProcessBuilder(
                sh, wrapper.absolutePath,
                "--unshare-all", "--die-with-parent",
                "--chdir", dir.absolutePath,
                "--ro-bind", "/usr", "/usr",
                "--dev", "/dev", "--tmpfs", "/tmp",
                "--clearenv", "--setenv", "HOME", "/tmp/glycin-home",
                "--seccomp", "20",
                loader.absolutePath, "--dbus-fd", "19"
            ).apply {
                environment()["Z2ROOT_ENGINE"] = "1"
                redirectErrorStream(true)
            }.start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals("wrapper failed:\n$output", 0, process.waitFor())
            assertTrue(output.contains("cwd=${dir.absolutePath}"))
            assertTrue(output.contains("home=/tmp/glycin-home"))
            assertTrue(output.contains("args=--dbus-fd 19"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `non glycin commands stay delegated to real bubblewrap`() {
        val script = z2BwrapCompatScript()
        assertTrue(script.contains("exec \"${'$'}REAL_BWRAP\" \"${'$'}@\""))
        assertTrue(script.contains("*/glycin-*|glycin-*"))
    }
}
