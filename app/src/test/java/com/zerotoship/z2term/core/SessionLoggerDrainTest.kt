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
}
