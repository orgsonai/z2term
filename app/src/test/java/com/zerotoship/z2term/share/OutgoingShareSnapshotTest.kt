package com.zerotoship.z2term.share

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OutgoingShareSnapshotTest {
    @Test fun snapshotsDoNotExposeSourcesOrOverwriteEqualNames() {
        val root = Files.createTempDirectory("outgoing-share").toFile()
        try {
            val stage = File(root, "share-abc").apply { mkdir() }
            File(stage, "0").writeText("first")
            File(stage, "1").writeText("second")
            val cache = File(root, "cache").apply { mkdir() }
            val files = OutgoingShareSnapshot.prepare(root, cache, "share-abc", listOf("/a/写真 ' 1.png", "/b/写真 ' 1.png"))
            stage.deleteRecursively()
            assertEquals(listOf("first", "second"), files.map { it.readText() })
            assertEquals(listOf("写真 ' 1.png", "写真 ' 1.png"), files.map { it.name })
            assertNotEquals(files[0], files[1])
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsEscapesAndRollsBackFailedCopies() {
        val root = Files.createTempDirectory("outgoing-share").toFile()
        try {
            val cache = File(root, "cache").apply { mkdir() }
            val stage = File(root, "share-abc").apply { mkdir() }
            File(stage, "0").writeText("123456")
            assertTrue(runCatching { OutgoingShareSnapshot.prepare(root, cache, "../share-abc", listOf("x")) }.isFailure)
            assertTrue(runCatching { OutgoingShareSnapshot.prepare(root, cache, "share-abc", listOf("x"), 5) }.isFailure)
            assertTrue(cache.listFiles()!!.isEmpty())
            File(stage, "0").delete()
            val outside = File(root, "private").apply { writeText("secret") }
            Files.createSymbolicLink(File(stage, "0").toPath(), outside.toPath())
            assertTrue(runCatching { OutgoingShareSnapshot.prepare(root, cache, "share-abc", listOf("x")) }.isFailure)
            assertTrue(cache.listFiles()!!.isEmpty())
            assertEquals("secret", outside.readText())
        } finally { root.deleteRecursively() }
    }
}
