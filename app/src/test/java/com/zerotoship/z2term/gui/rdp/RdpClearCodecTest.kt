package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RdpClearCodecTest {
    @Test
    fun residualRunFillsDestinationRectangle() {
        val encoded = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0)) // flags, sequence
            write(le32(4)); write(le32(0)); write(le32(0))
            write(byteArrayOf(0x33, 0x22, 0x11, 2)) // BGR + run
        }.toByteArray()
        val pixels = IntArray(6)

        RdpClearCodec().decode(0, encoded, pixels, 3, 2, 1, 1, 2, 1)

        assertArrayEquals(
            intArrayOf(0, 0, 0, 0, 0xFF112233.toInt(), 0xFF112233.toInt()),
            pixels,
        )
    }

    private fun le32(value: Int) = ByteArray(4) { (value ushr (it * 8)).toByte() }
}
