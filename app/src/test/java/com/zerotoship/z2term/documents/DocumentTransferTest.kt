package com.zerotoship.z2term.documents

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class DocumentTransferTest {
    @Test fun preservesBinaryDataAcrossBufferBoundaries() {
        val bytes = ByteArray(150001) { (it % 256).toByte() }
        val out = ByteArrayOutputStream()
        assertEquals(bytes.size.toLong(), DocumentTransfer.copy(bytes.inputStream(), out))
        assertArrayEquals(bytes, out.toByteArray())
    }
    @Test fun cancellationStopsBeforeTheNextWrite() {
        val out = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) {
            DocumentTransfer.copy(ByteArray(150001).inputStream(), out) { out.size() == 0 }
        }
        assertEquals(65536, out.size())
    }
    @Test fun exactLimitSucceedsButOversizeStreamNeverWritesBeyondLimit() {
        fun source(size: Long) = object : InputStream() {
            var remaining = size
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining == 0L) return -1
                val n = minOf(remaining, len.toLong()).toInt(); remaining -= n; return n
            }
        }
        var count = 0L
        val sink = object : OutputStream() {
            override fun write(b: Int) { count++ }
            override fun write(b: ByteArray, off: Int, len: Int) { count += len }
        }
        assertEquals(DocumentTransfer.MAX_BYTES, DocumentTransfer.copy(source(DocumentTransfer.MAX_BYTES), sink))
        count = 0
        assertThrows(IllegalArgumentException::class.java) {
            DocumentTransfer.copy(source(DocumentTransfer.MAX_BYTES + 1), sink)
        }
        assertEquals(DocumentTransfer.MAX_BYTES, count)
    }
}
