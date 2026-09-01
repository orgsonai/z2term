package com.zerotoship.z2term.gui.rdp

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class Rdp8BulkTest {
    @Test
    fun decodesSingleRawSegment() {
        assertArrayEquals(
            "gfx".toByteArray(),
            Rdp8Bulk().decodeSegmented(byteArrayOf(0xE0.toByte(), 0x04, 'g'.code.toByte(), 'f'.code.toByte(), 'x'.code.toByte())),
        )
    }

    @Test
    fun decodesMultipartRawSegments() {
        val encoded = byteArrayOf(
            0xE1.toByte(), 2, 0, 3, 0, 0, 0,
            3, 0, 0, 0, 0x04, 'a'.code.toByte(), 'b'.code.toByte(),
            2, 0, 0, 0, 0x04, 'c'.code.toByte(),
        )
        assertArrayEquals("abc".toByteArray(), Rdp8Bulk().decodeSegmented(encoded))
    }

    @Test
    fun decodesCompressedLiteral() {
        // Huffman literal: prefix 0 followed by 0x41, then seven padding bits.
        val encoded = byteArrayOf(0xE0.toByte(), 0x24, 0x20, 0x80.toByte(), 7)
        assertArrayEquals(byteArrayOf(0x41), Rdp8Bulk().decodeSegmented(encoded))
    }
}
