package com.zerotoship.z2term.channel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * シリアルの受信の控え ([SerialTranscript])。
 *
 * ⚠ 一番怖いのは、送る前に届いていた分が「返事」に混ざること、または返事の頭が欠けること。
 * AI はそれを機器の答えとして読む。
 */
class SerialTranscriptTest {

    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8)

    @Test
    fun sinceReturnsOnlyWhatArrivedAfterTheMark() {
        val t = SerialTranscript(16)
        t.append(bytes("old>"))
        val mark = t.total
        t.append(bytes("ls\r\n"))
        t.append(bytes("a b\r\n"))
        val slice = t.since(mark)
        assertEquals("ls\r\na b\r\n", slice.bytes.toString(Charsets.UTF_8))
        assertFalse(slice.lost)
    }

    @Test
    fun nothingAfterTheMarkIsEmpty() {
        val t = SerialTranscript(16)
        t.append(bytes("abc"))
        assertEquals(0, t.since(t.total).bytes.size)
    }

    @Test
    fun wrapsAroundAndKeepsTheNewest() {
        val t = SerialTranscript(8)
        t.append(bytes("12345"))
        t.append(bytes("6789AB"))
        assertEquals(11L, t.total)
        assertArrayEquals(bytes("456789AB"), t.tail(100))
        assertArrayEquals(bytes("AB"), t.tail(2))
    }

    @Test
    fun aReplyLongerThanTheRingIsMarkedLost() {
        val t = SerialTranscript(4)
        val mark = t.total
        t.append(bytes("abcdefg"))
        val slice = t.since(mark)
        assertEquals("defg", slice.bytes.toString(Charsets.UTF_8))
        assertTrue(slice.lost)
    }
}
