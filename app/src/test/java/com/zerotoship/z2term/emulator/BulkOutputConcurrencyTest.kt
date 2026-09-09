package com.zerotoship.z2term.emulator

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BulkOutputConcurrencyTest {
    @Test fun drawingAndHistoryExtractionSurviveSustainedOutputAndResize() {
        val emu = TerminalEmulator(output = {}, initialRows = 24, initialColumns = 80)
        val buffer = emu.buffer
        buffer.scrollbackCapacity = 128
        val pool = Executors.newFixedThreadPool(2)
        try {
            val writer = pool.submit {
                val chunk = ("長いログ abcdefghijklmnopqrstuvwxyz\r\n".repeat(128)).toByteArray()
                repeat(2000) {
                    emu.processBytes(chunk)
                    if (it % 100 == 0) emu.resize(if (it % 200 == 0) 40 else 24, 80)
                }
            }
            val reader = pool.submit {
                repeat(2000) {
                    synchronized(buffer) {
                        assertTrue(buffer.scrollbackSize <= 128)
                        val size = buffer.totalRows
                        for (i in (size - buffer.rows).coerceAtLeast(0) until size) {
                            val row = buffer.getRow(i)
                            for (col in 0 until row.columns) row.getCell(col)
                        }
                    }
                    buffer.getAllText()
                }
            }
            writer.get(30, TimeUnit.SECONDS)
            reader.get(30, TimeUnit.SECONDS)
            assertEquals(128, buffer.scrollbackSize)
        } finally { pool.shutdownNow() }
    }
}
