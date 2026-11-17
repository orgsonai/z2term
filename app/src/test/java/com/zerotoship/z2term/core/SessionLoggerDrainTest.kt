package com.zerotoship.z2term.core

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SessionLoggerDrainTest {
    @Test fun closeDrainsQueuedBulkOutputWithoutDroppingTheTail() {
        val file = Files.createTempFile("z2term-log-drain", ".txt").toFile()
        val chunk = "0123456789abcdef".repeat(512).toByteArray()
        val count = 1024
        try {
            val logger = SessionLogger(file, append = false, raw = true, mask = false)
            repeat(count) { logger.append(chunk) }
            logger.close()
            val expected = chunk.size.toLong() * count
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (file.length() < expected && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals(expected, file.length())
            file.inputStream().use { input ->
                val read = ByteArray(chunk.size)
                repeat(count) {
                    assertEquals(chunk.size, input.read(read))
                    assertArrayEquals(chunk, read)
                }
                assertEquals(-1, input.read())
            }
        } finally { file.delete() }
    }

    // 1.0.154: 何も書かないまま止めたら、ファイルを作らない。
    @Test fun nothingWrittenLeavesNoFile() {
        val dir = Files.createTempDirectory("z2term-log-empty").toFile()
        val file = java.io.File(dir, "empty.txt")
        try {
            val logger = SessionLogger(file, append = false, raw = false, mask = true, timestamp = true)
            // 画面制御だけの出力は、プレーンテキストにすると何も残らない。
            logger.append("\u001b[2J\u001b[H".toByteArray())
            logger.close()
            Thread.sleep(300)
            assertFalse(file.exists())
            assertEquals(0L, logger.bytesWritten)
        } finally { dir.deleteRecursively() }
    }

    @Test fun fileIsCreatedOnFirstWriteAndAvoidsANameTakenInTheMeantime() {
        val dir = Files.createTempDirectory("z2term-log-lazy").toFile()
        val file = java.io.File(dir, "log.txt")
        try {
            val logger = SessionLogger(file, append = false, raw = true, mask = false)
            assertFalse(file.exists())
            // 始めてから最初に書くまでの間に、よそで同じ名前ができた。
            file.writeText("other")
            logger.append("hello\n".toByteArray())
            logger.close()
            val moved = java.io.File(dir, "log-2.txt")
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (moved.length() < 6 && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals("other", file.readText())
            assertEquals("hello\n", moved.readText())
            assertEquals(moved, logger.file)
        } finally { dir.deleteRecursively() }
    }
}
