package com.zerotoship.z2term.proot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GuestShellResolverTest {

    @Test
    fun alpineAbsoluteBusyboxSymlinkIsExecutable() {
        val rootfs = Files.createTempDirectory("z2-alpine-shell").toFile()
        try {
            val bin = rootfs.resolve("bin").apply { mkdirs() }
            bin.resolve("busybox").apply {
                writeText("busybox")
                setExecutable(true)
            }
            Files.createSymbolicLink(bin.resolve("ash").toPath(), java.nio.file.Path.of("/bin/busybox"))

            assertTrue(guestExecutableExists(rootfs, "/bin/ash"))
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun danglingShellSymlinkIsRejected() {
        val rootfs = Files.createTempDirectory("z2-dangling-shell").toFile()
        try {
            val bin = rootfs.resolve("bin").apply { mkdirs() }
            Files.createSymbolicLink(bin.resolve("zsh").toPath(), java.nio.file.Path.of("/bin/missing-zsh"))

            assertFalse(guestExecutableExists(rootfs, "/bin/zsh"))
        } finally {
            rootfs.deleteRecursively()
        }
    }
}
