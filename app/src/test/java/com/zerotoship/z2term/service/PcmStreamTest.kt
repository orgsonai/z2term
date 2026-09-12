package com.zerotoship.z2term.service

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class PcmStreamTest {
    @Test fun fragmentedReadsAndPartialWritesPreserveStereoFrames() {
        val source = ByteArray(403) { it.toByte() }
        val input = object : ByteArrayInputStream(source) {
            var reads = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                super.read(bytes, offset, minOf(length, ++reads % 7 + 1))
        }
        val output = ByteArrayOutputStream()
        streamPcmFrames(input, 17, { true }) { bytes, offset, count ->
            assertEquals(0, count % 4)
            val written = minOf(4, count)
            output.write(bytes, offset, written)
            written
        }
        assertArrayEquals(source.copyOf(400), output.toByteArray())
    }

    @Test(expected = IOException::class)
    fun failedOutputEndsStreamInsteadOfDiscardingAudio() {
        streamPcmFrames(ByteArrayInputStream(ByteArray(16)), 16, { true }) { _, _, _ -> 0 }
    }

    @Test fun cancellationStopsPartialWriteLoop() {
        var running = true
        var writes = 0
        streamPcmFrames(ByteArrayInputStream(ByteArray(100)), 16, { running }) { _, _, _ ->
            writes++
            running = false
            4
        }
        assertEquals(1, writes)
    }
}
