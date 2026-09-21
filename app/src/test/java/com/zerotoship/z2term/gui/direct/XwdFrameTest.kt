package com.zerotoship.z2term.gui.direct

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class XwdFrameTest {
    private fun header(order: Int = 0): ByteArray {
        val h = ByteBuffer.allocate(100).order(ByteOrder.BIG_ENDIAN)
        val words = mapOf(0 to 104, 1 to 7, 2 to 2, 3 to 24, 4 to 640, 5 to 480,
            7 to order, 11 to 32, 12 to 2560, 13 to 4, 14 to 0xFF0000, 15 to 0xFF00, 16 to 0xFF, 19 to 256)
        words.forEach { (index, value) -> h.putInt(index * 4, value) }
        return h.array()
    }
    @Test fun headerIsAlwaysNetworkOrderButPixelsHaveTheirOwnOrder() {
        for (order in 0..1) {
            val frame = XwdFrame.parse(header(order), 104L + 256 * 12 + 640 * 480 * 4)
            assertEquals(640, frame.width); assertEquals(480, frame.height)
            assertEquals(3176L, frame.offset)
            assertEquals(if (order == 0) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN, frame.order)
        }
    }
    @Test fun rejectsTruncationDimensionsStrideAndOverflow() {
        assertThrows(IllegalArgumentException::class.java) { XwdFrame.parse(header(), 100) }
        for ((word, value) in listOf(0 to Int.MAX_VALUE, 4 to 0, 5 to 4097, 12 to 1, 14 to 255, 19 to Int.MAX_VALUE)) {
            val bad = header().also { ByteBuffer.wrap(it).putInt(word * 4, value) }
            assertThrows(IllegalArgumentException::class.java) { XwdFrame.parse(bad, Long.MAX_VALUE) }
        }
    }
}
