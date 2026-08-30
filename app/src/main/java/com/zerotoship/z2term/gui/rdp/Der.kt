package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import java.io.IOException

/** CredSSP で使う ASN.1 DER の最小実装。TSRequest が必要とする型だけを扱う。 */
internal object Der {
    const val INTEGER = 0x02
    const val BIT_STRING = 0x03
    const val OCTET_STRING = 0x04
    const val OBJECT_IDENTIFIER = 0x06
    const val SEQUENCE = 0x30

    fun integer(value: Long): ByteArray {
        val bytes = ByteArray(8) { shift -> (value ushr ((7 - shift) * 8)).toByte() }
        var first = 0
        while (first < 7) {
            val current = bytes[first].toInt() and 0xFF
            val next = bytes[first + 1].toInt() and 0xFF
            if ((current == 0 && next and 0x80 == 0) || (current == 0xFF && next and 0x80 != 0)) {
                first++
            } else {
                break
            }
        }
        return tlv(INTEGER, bytes.copyOfRange(first, bytes.size))
    }

    fun octetString(value: ByteArray): ByteArray = tlv(OCTET_STRING, value)

    fun sequence(vararg values: ByteArray): ByteArray = tlv(SEQUENCE, concat(*values))

    /** ASN.1 の explicit context-specific constructed tag `[index]`。 */
    fun context(index: Int, value: ByteArray): ByteArray {
        require(index in 0..30) { "context tag out of range: $index" }
        return tlv(0xA0 or index, value)
    }

    fun concat(vararg values: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        values.forEach(out::write)
    }.toByteArray()

    fun tlv(tag: Int, value: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        out.write(tag)
        writeLength(out, value.size)
        out.write(value)
    }.toByteArray()

    private fun writeLength(out: ByteArrayOutputStream, length: Int) {
        require(length >= 0)
        if (length < 0x80) {
            out.write(length)
            return
        }
        var n = length
        val bytes = ByteArray(4)
        var first = bytes.size
        while (n != 0) {
            bytes[--first] = n.toByte()
            n = n ushr 8
        }
        out.write(0x80 or (bytes.size - first))
        out.write(bytes, first, bytes.size - first)
    }

    data class Value(val tag: Int, val body: ByteArray) {
        fun reader(): Reader = Reader(body)
    }

    class Reader(private val bytes: ByteArray) {
        private var offset = 0

        fun hasRemaining(): Boolean = offset < bytes.size

        fun read(expectedTag: Int? = null): Value {
            if (offset >= bytes.size) throw IOException("truncated DER tag")
            val tag = bytes[offset++].toInt() and 0xFF
            if (expectedTag != null && tag != expectedTag) {
                throw IOException("unexpected DER tag 0x${tag.toString(16)}, expected 0x${expectedTag.toString(16)}")
            }
            val length = readLength()
            if (length > bytes.size - offset) throw IOException("truncated DER value: need $length bytes")
            val body = bytes.copyOfRange(offset, offset + length)
            offset += length
            return Value(tag, body)
        }

        fun requireEnd() {
            if (hasRemaining()) throw IOException("trailing DER data: ${bytes.size - offset} bytes")
        }

        private fun readLength(): Int {
            if (offset >= bytes.size) throw IOException("truncated DER length")
            val first = bytes[offset++].toInt() and 0xFF
            if (first and 0x80 == 0) return first
            val count = first and 0x7F
            if (count == 0) throw IOException("indefinite DER length is not allowed")
            if (count > 4 || count > bytes.size - offset) throw IOException("invalid DER length")
            if ((bytes[offset].toInt() and 0xFF) == 0) throw IOException("non-minimal DER length")
            var length = 0L
            repeat(count) { length = (length shl 8) or (bytes[offset++].toLong() and 0xFF) }
            if (length < 0x80 || length > Int.MAX_VALUE) throw IOException("invalid DER length: $length")
            return length.toInt()
        }
    }

    fun decodeInteger(value: Value): Long {
        if (value.tag != INTEGER || value.body.isEmpty() || value.body.size > 8) {
            throw IOException("invalid DER integer")
        }
        var result = if (value.body[0].toInt() < 0) -1L else 0L
        value.body.forEach { result = (result shl 8) or (it.toLong() and 0xFF) }
        return result
    }
}
