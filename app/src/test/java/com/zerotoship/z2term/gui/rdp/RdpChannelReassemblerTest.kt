package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpChannelReassemblerTest {
    @Test
    fun splitMessageIsDeliveredOnceComplete() {
        val got = mutableListOf<ByteArray>()
        val reassembler = RdpChannelReassembler("TEST", 1024, got::add)

        reassembler.accept(chunk(byteArrayOf(1, 2), total = 4, first = true, last = false))
        assertTrue(got.isEmpty())
        reassembler.accept(chunk(byteArrayOf(3, 4), total = 4, first = false, last = true))

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), got.single())
    }

    /** ⚠ 中身の無い通は捨てる (相手が区切りとして送ることがある)。 */
    @Test
    fun emptyMessageIsNotDelivered() {
        val got = mutableListOf<ByteArray>()
        val reassembler = RdpChannelReassembler("TEST", 1024, got::add)

        reassembler.accept(chunk(ByteArray(0), total = 0))

        assertTrue(got.isEmpty())
    }

    @Test
    fun lengthBeyondTheLimitIsRejected() {
        val reassembler = RdpChannelReassembler("TEST", 8) { }

        assertThrows(IOException::class.java) {
            reassembler.accept(chunk(byteArrayOf(1), total = 64, first = true, last = false))
        }
    }

    @Test
    fun continuationWithoutFirstChunkIsRejected() {
        val reassembler = RdpChannelReassembler("TEST", 1024) { }

        assertThrows(IOException::class.java) {
            reassembler.accept(chunk(byteArrayOf(1, 2), total = 4, first = false, last = false))
        }
    }

    @Test
    fun moreDataThanDeclaredIsRejected() {
        val reassembler = RdpChannelReassembler("TEST", 1024) { }

        assertThrows(IOException::class.java) {
            reassembler.accept(chunk(byteArrayOf(1, 2, 3), total = 2, first = true, last = true))
        }
    }

    /** 途中まで届いた通を捨てて、次の通を最初から組み立て直せる。 */
    @Test
    fun resetDropsAHalfReceivedMessage() {
        val got = mutableListOf<ByteArray>()
        val reassembler = RdpChannelReassembler("TEST", 1024, got::add)

        reassembler.accept(chunk(byteArrayOf(9), total = 2, first = true, last = false))
        reassembler.reset()
        reassembler.accept(chunk(byteArrayOf(7, 7), total = 2, first = true, last = true))

        assertEquals(1, got.size)
        assertArrayEquals(byteArrayOf(7, 7), got.single())
    }

    private fun chunk(
        body: ByteArray,
        total: Int = body.size,
        first: Boolean = true,
        last: Boolean = true,
    ): ByteArray = ByteArrayOutputStream().apply {
        write(ByteArray(4) { (total ushr (it * 8)).toByte() })
        val flags = (if (first) 1 else 0) or (if (last) 2 else 0)
        write(ByteArray(4) { (flags ushr (it * 8)).toByte() })
        write(body)
    }.toByteArray()
}
