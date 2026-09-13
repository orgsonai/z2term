package com.zerotoship.z2term.distro

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class DistroDataDeletionTest {
    @get:Rule val temp = TemporaryFolder()
    private fun file(path: String): File = File(temp.root, path).apply {
        parentFile!!.mkdirs(); writeText("keep-or-delete")
    }
    private fun deletion(id: String = "alpine") = DistroDataDeletion(
        File(temp.root, "files"), File(temp.root, "cache"), id)

    @Test fun removesOnlyTargetOsIncludingOverlayAndItsArchives() {
        val removed = listOf(file("files/distros/alpine/etc/config"),
            file("files/home_overlay/alpine/.local/tool"), file("cache/distros/alpine-arm64-v8a.tgz"),
            file("cache/distros/alpine-x86_64.tar.gz"))
        val preserved = listOf(file("files/shared_home/work.txt"), file("files/distros/ubuntu/etc/config"),
            file("files/home_overlay/ubuntu/.local/tool"), file("cache/distros/ubuntu-arm64-v8a.tgz"),
            file("cache/distros/alpine-extra-arm64-v8a.tgz"), file("files/settings.txt"))
        deletion().delete()
        removed.forEach { assertFalse(it.path, it.exists()) }
        preserved.forEach { assertEquals(it.path, "keep-or-delete", it.readText()) }
        assertFalse(File(temp.root, "files/distros/alpine").exists())
    }

    @Test fun neverFollowsSymlinksToSharedHomeOrAnotherOs() {
        val home = file("files/shared_home/valuable.txt")
        val other = file("files/distros/ubuntu/valuable.txt")
        val root = File(temp.root, "files/distros/alpine").apply { mkdirs() }
        Files.createSymbolicLink(File(root, "root").toPath(), home.parentFile!!.toPath())
        Files.createSymbolicLink(File(root, "other").toPath(), other.parentFile!!.toPath())
        Files.createSymbolicLink(File(root, "dangling").toPath(), File(temp.root, "missing").toPath())
        deletion().delete()
        assertTrue(home.exists()); assertTrue(other.exists()); assertFalse(root.exists())
    }

    @Test fun rootSymlinkIsUnlinkedWithoutDeletingItsTarget() {
        val home = file("files/shared_home/valuable.txt")
        val root = File(temp.root, "files/distros/alpine").apply { parentFile!!.mkdirs() }
        Files.createSymbolicLink(root.toPath(), home.parentFile!!.toPath())
        deletion().delete()
        assertTrue(home.exists()); assertFalse(Files.isSymbolicLink(root.toPath()))
    }

    @Test fun redirectedParentIsRejectedBeforeDeletingAnything() {
        val external = file("outside/alpine/valuable.txt")
        val parent = File(temp.root, "files/distros").apply { parentFile!!.mkdirs() }
        Files.createSymbolicLink(parent.toPath(), external.parentFile!!.parentFile!!.toPath())
        assertThrows(IllegalStateException::class.java) { deletion().delete() }
        assertTrue(external.exists())
    }

    @Test fun rejectsMountOnRootOrDescendantIncludingEscapedPaths() {
        val root = File(temp.root, "files/distros/alpine")
        for (path in listOf(root.path, "${root.path}/root", "${root.path}/dir with space")) {
            val escaped = path.replace(" ", "\\040")
            assertThrows(DistroDataDeletion.Mounted::class.java) {
                deletion().checkUnmounted("21 1 0:1 / $escaped rw - tmpfs tmpfs rw")
            }
        }
        deletion().checkUnmounted("21 1 0:1 / ${root.path}-other rw - tmpfs tmpfs rw")
    }

    @Test fun rejectsTraversalAndAllowsRepeatedDeletion() {
        for (id in listOf("..", "../shared_home", "/tmp", "alpine/../../shared_home", "")) {
            assertThrows(IllegalArgumentException::class.java) { deletion(id) }
        }
        deletion().delete(); deletion().delete()
    }
}
