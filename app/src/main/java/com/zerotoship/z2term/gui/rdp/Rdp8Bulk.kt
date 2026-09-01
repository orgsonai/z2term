package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import java.io.IOException

/** [MS-RDPEGFX] RDP_SEGMENTED_DATA と RDP 8.0 Bulk Compression の展開器。 */
internal class Rdp8Bulk {
    private val history = ByteArray(HISTORY_SIZE)
    private var historyIndex = 0

    fun reset() {
        history.fill(0)
        historyIndex = 0
    }

    fun decodeSegmented(encoded: ByteArray): ByteArray {
        val cursor = Cursor(encoded)
        return when (val descriptor = cursor.u8()) {
            SEGMENTED_SINGLE -> decodeSegment(cursor.remainingBytes())
            SEGMENTED_MULTIPART -> {
                val count = cursor.le16()
                val expected = cursor.le32Long()
                if (count !in 1..MAX_SEGMENTS || expected !in 1..MAX_MESSAGE_BYTES.toLong()) {
                    throw IOException("invalid RDPGFX segmented data: count=$count size=$expected")
                }
                val output = ByteArrayOutputStream(expected.toInt())
                repeat(count) {
                    val size = cursor.le32Long()
                    if (size !in 1..MAX_SEGMENT_BYTES.toLong()) {
                        throw IOException("invalid RDPGFX segment length: $size")
                    }
                    output.write(decodeSegment(cursor.bytes(size.toInt())))
                    if (output.size().toLong() > expected) {
                        throw IOException("RDPGFX segmented data exceeds advertised size")
                    }
                }
                cursor.requireEnd()
                if (output.size().toLong() != expected) {
                    throw IOException("RDPGFX segmented data size mismatch: ${output.size()}/$expected")
                }
                output.toByteArray()
            }
            else -> throw IOException("invalid RDPGFX segmented descriptor: 0x${descriptor.toString(16)}")
        }
    }

    private fun decodeSegment(segment: ByteArray): ByteArray {
        if (segment.isEmpty()) throw IOException("empty RDP 8 bulk segment")
        val header = segment[0].toInt() and 0xFF
        if (header and COMPRESSION_TYPE_MASK != COMPRESSION_TYPE_RDP8) {
            throw IOException("unsupported RDP bulk compression type: 0x${(header and COMPRESSION_TYPE_MASK).toString(16)}")
        }
        val body = segment.copyOfRange(1, segment.size)
        return if (header and PACKET_COMPRESSED == 0) writeRaw(body) else decodeCompressed(body)
    }

    private fun writeRaw(data: ByteArray): ByteArray {
        if (data.size > MAX_UNCOMPRESSED_SEGMENT_BYTES) throw IOException("RDP 8 raw segment is too large")
        data.forEach(::writeHistory)
        return data
    }

    private fun decodeCompressed(data: ByteArray): ByteArray {
        if (data.isEmpty()) throw IOException("empty compressed RDP 8 segment")
        val bits = BitReader(data)
        val output = ByteArrayOutputStream()
        while (bits.remaining > 0) {
            var prefix = 0
            var haveBits = 0
            var matched = false
            for (token in TOKENS) {
                while (haveBits < token.prefixLength) {
                    prefix = (prefix shl 1) or bits.read(1)
                    haveBits++
                }
                if (prefix != token.prefixCode) continue
                matched = true
                if (!token.match) {
                    writeByte(output, token.valueBase + bits.read(token.valueBits))
                } else {
                    val distance = token.valueBase + bits.read(token.valueBits)
                    if (distance == 0) {
                        val count = bits.read(15)
                        bits.alignToByte()
                        repeat(count) { writeByte(output, bits.readRawByte()) }
                    } else {
                        val count = if (bits.read(1) == 0) {
                            3
                        } else {
                            var length = 4
                            var extra = 2
                            while (bits.read(1) == 1) {
                                if (extra >= 23) throw IOException("invalid RDP 8 match length")
                                length *= 2
                                extra++
                            }
                            length + bits.read(extra)
                        }
                        copyMatch(output, distance, count)
                    }
                }
                break
            }
            if (!matched) throw IOException("invalid RDP 8 compression token")
        }
        return output.toByteArray()
    }

    private fun copyMatch(output: ByteArrayOutputStream, distance: Int, count: Int) {
        if (distance !in 1..HISTORY_SIZE || count < 0 || output.size() + count > MAX_UNCOMPRESSED_SEGMENT_BYTES) {
            throw IOException("invalid RDP 8 match: distance=$distance count=$count")
        }
        var source = (historyIndex + HISTORY_SIZE - distance) % HISTORY_SIZE
        repeat(count) {
            val value = history[source].toInt() and 0xFF
            source = (source + 1) % HISTORY_SIZE
            writeByte(output, value)
        }
    }

    private fun writeByte(output: ByteArrayOutputStream, value: Int) {
        if (output.size() >= MAX_UNCOMPRESSED_SEGMENT_BYTES) throw IOException("RDP 8 segment expands too far")
        output.write(value)
        writeHistory(value.toByte())
    }

    private fun writeHistory(value: Byte) {
        history[historyIndex] = value
        historyIndex++
        if (historyIndex == history.size) historyIndex = 0
    }

    private data class Token(
        val prefixLength: Int,
        val prefixCode: Int,
        val valueBits: Int,
        val match: Boolean,
        val valueBase: Int,
    )

    private class BitReader(private val data: ByteArray) {
        private val end = data.lastIndex
        private var offset = 0
        private var current = 0
        private var currentCount = 0
        var remaining = 8 * end - (data[end].toInt() and 0xFF)
            private set

        init {
            if ((data[end].toInt() and 0xFF) !in 0..7 || remaining < 0) {
                throw IOException("invalid RDP 8 trailing bit count")
            }
        }

        fun read(count: Int): Int {
            if (count !in 0..24 || count > remaining) throw IOException("truncated RDP 8 bit stream")
            while (currentCount < count) {
                if (offset >= end) throw IOException("truncated RDP 8 bit stream")
                current = (current shl 8) or (data[offset++].toInt() and 0xFF)
                currentCount += 8
            }
            remaining -= count
            currentCount -= count
            val result = current ushr currentCount
            current = if (currentCount == 0) 0 else current and ((1 shl currentCount) - 1)
            return result
        }

        fun alignToByte() {
            if (currentCount > remaining) throw IOException("invalid RDP 8 bit alignment")
            remaining -= currentCount
            current = 0
            currentCount = 0
        }

        fun readRawByte(): Int {
            if (currentCount != 0 || remaining < 8 || offset >= end) {
                throw IOException("truncated RDP 8 unencoded run")
            }
            remaining -= 8
            return data[offset++].toInt() and 0xFF
        }
    }

    private class Cursor(private val data: ByteArray) {
        private var offset = 0
        private val remaining get() = data.size - offset
        fun u8(): Int {
            if (remaining < 1) throw IOException("truncated RDPGFX segmented data")
            return data[offset++].toInt() and 0xFF
        }
        fun le16(): Int = u8() or (u8() shl 8)
        fun le32Long(): Long = (u8().toLong() or (u8().toLong() shl 8) or
            (u8().toLong() shl 16) or (u8().toLong() shl 24)) and 0xFFFFFFFFL
        fun bytes(count: Int): ByteArray {
            if (count < 0 || remaining < count) throw IOException("truncated RDPGFX segmented data")
            return data.copyOfRange(offset, offset + count).also { offset += count }
        }
        fun remainingBytes(): ByteArray = bytes(remaining)
        fun requireEnd() { if (remaining != 0) throw IOException("trailing RDPGFX segmented data") }
    }

    companion object {
        private const val SEGMENTED_SINGLE = 0xE0
        private const val SEGMENTED_MULTIPART = 0xE1
        private const val COMPRESSION_TYPE_MASK = 0x0F
        private const val COMPRESSION_TYPE_RDP8 = 0x04
        private const val PACKET_COMPRESSED = 0x20
        private const val HISTORY_SIZE = 2_500_000
        private const val MAX_UNCOMPRESSED_SEGMENT_BYTES = 65_535
        private const val MAX_SEGMENT_BYTES = MAX_UNCOMPRESSED_SEGMENT_BYTES + 1_000
        private const val MAX_SEGMENTS = 65_535
        private const val MAX_MESSAGE_BYTES = 32 * 1024 * 1024

        private val TOKENS = listOf(
            Token(1, 0, 8, false, 0),
            Token(5, 17, 5, true, 0), Token(5, 18, 7, true, 32),
            Token(5, 19, 9, true, 160), Token(5, 20, 10, true, 672),
            Token(5, 21, 12, true, 1696), Token(5, 24, 0, false, 0x00),
            Token(5, 25, 0, false, 0x01), Token(6, 44, 14, true, 5792),
            Token(6, 45, 15, true, 22176), Token(6, 52, 0, false, 0x02),
            Token(6, 53, 0, false, 0x03), Token(6, 54, 0, false, 0xFF),
            Token(7, 92, 18, true, 54944), Token(7, 93, 20, true, 317088),
            Token(7, 110, 0, false, 0x04), Token(7, 111, 0, false, 0x05),
            Token(7, 112, 0, false, 0x06), Token(7, 113, 0, false, 0x07),
            Token(7, 114, 0, false, 0x08), Token(7, 115, 0, false, 0x09),
            Token(7, 116, 0, false, 0x0A), Token(7, 117, 0, false, 0x0B),
            Token(7, 118, 0, false, 0x3A), Token(7, 119, 0, false, 0x3B),
            Token(7, 120, 0, false, 0x3C), Token(7, 121, 0, false, 0x3D),
            Token(7, 122, 0, false, 0x3E), Token(7, 123, 0, false, 0x3F),
            Token(7, 124, 0, false, 0x40), Token(7, 125, 0, false, 0x80),
            Token(8, 188, 20, true, 1_365_664), Token(8, 189, 21, true, 2_414_240),
            Token(8, 252, 0, false, 0x0C), Token(8, 253, 0, false, 0x38),
            Token(8, 254, 0, false, 0x39), Token(8, 255, 0, false, 0x66),
            Token(9, 380, 22, true, 4_511_392), Token(9, 381, 23, true, 8_705_696),
            Token(9, 382, 24, true, 17_094_304),
        )
    }
}
