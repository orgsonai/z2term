package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * [MS-RDPECLIP] のテキスト専用実装。ファイル転送やHTML形式は宣言せず、
 * CF_UNICODETEXT だけを Android と共有する。
 */
internal class RdpCliprdr(
    private val sendMessage: (ByteArray) -> Unit,
    private val onRemoteText: (String) -> Unit,
) {
    private var serverReady = false
    private var localText: String? = null
    private val reassembler = RdpChannelReassembler("CLIPRDR", MAX_MESSAGE_BYTES) { handle(it) }

    @Synchronized
    fun start() {
        if (serverReady) return
        serverReady = true
        send(capabilities())
        send(formatList())
    }

    @Synchronized
    fun announceLocalText(text: String) {
        localText = text.take(MAX_TEXT_CHARS)
        if (serverReady) send(formatList())
    }

    @Synchronized
    fun acceptChannelChunk(payload: ByteArray) = reassembler.accept(payload)

    private fun handle(message: ByteArray) {
        if (message.size < CLIP_HEADER_SIZE) throw IOException("truncated CLIPRDR message")
        val type = le16(message, 0)
        val flags = le16(message, 2)
        val length = le32(message, 4)
        if (length < 0 || length != message.size - CLIP_HEADER_SIZE) {
            throw IOException("invalid CLIPRDR message length")
        }
        val body = message.copyOfRange(CLIP_HEADER_SIZE, message.size)
        when (type) {
            CB_MONITOR_READY -> {
                serverReady = true
                send(capabilities())
                send(formatList())
            }
            CB_CLIP_CAPS -> Unit
            CB_FORMAT_LIST -> {
                send(message(CB_FORMAT_LIST_RESPONSE, CB_RESPONSE_OK, byteArrayOf()))
                if (CF_UNICODETEXT in formatIds(body)) {
                    send(message(CB_FORMAT_DATA_REQUEST, 0, le32Bytes(CF_UNICODETEXT)))
                }
            }
            CB_FORMAT_DATA_REQUEST -> {
                val requested = body.takeIf { it.size >= 4 }?.let { le32(it, 0) }
                val text = localText
                if (requested == CF_UNICODETEXT && text != null) {
                    val encoded = (text + "\u0000").toByteArray(Charsets.UTF_16LE)
                    send(message(CB_FORMAT_DATA_RESPONSE, CB_RESPONSE_OK, encoded))
                } else {
                    send(message(CB_FORMAT_DATA_RESPONSE, CB_RESPONSE_FAIL, byteArrayOf()))
                }
            }
            CB_FORMAT_DATA_RESPONSE -> {
                if (flags and CB_RESPONSE_OK == 0 || body.isEmpty()) return
                val evenLength = body.size - (body.size % 2)
                val text = body.copyOf(evenLength).toString(Charsets.UTF_16LE).trimEnd('\u0000')
                if (text.isNotEmpty()) onRemoteText(text.take(MAX_TEXT_CHARS))
            }
        }
    }

    private fun capabilities(): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write(le16Bytes(1))
            write(le16Bytes(0))
            write(le16Bytes(CB_CAPSTYPE_GENERAL))
            write(le16Bytes(12))
            write(le32Bytes(CB_CAPS_VERSION_2))
            write(le32Bytes(CB_USE_LONG_FORMAT_NAMES))
        }.toByteArray()
        return message(CB_CLIP_CAPS, 0, body)
    }

    private fun formatList(): ByteArray {
        val body = if (localText == null) {
            byteArrayOf()
        } else {
            le32Bytes(CF_UNICODETEXT) + byteArrayOf(0, 0)
        }
        return message(CB_FORMAT_LIST, 0, body)
    }

    private fun send(message: ByteArray) = sendMessage(message)

    private fun message(type: Int, flags: Int, body: ByteArray): ByteArray =
        le16Bytes(type) + le16Bytes(flags) + le32Bytes(body.size) + body

    private fun formatIds(body: ByteArray): Set<Int> {
        if (body.isEmpty()) return emptySet()
        if (body.size % 36 == 0) {
            return (body.indices step 36).filter { it + 4 <= body.size }.map { le32(body, it) }.toSet()
        }
        val ids = mutableSetOf<Int>()
        var offset = 0
        while (offset + 4 <= body.size) {
            ids += le32(body, offset)
            offset += 4
            while (offset + 1 < body.size) {
                val end = body[offset] == 0.toByte() && body[offset + 1] == 0.toByte()
                offset += 2
                if (end) break
            }
        }
        return ids
    }

    companion object {
        private const val CLIP_HEADER_SIZE = 8

        private const val CB_MONITOR_READY = 0x0001
        private const val CB_FORMAT_LIST = 0x0002
        private const val CB_FORMAT_LIST_RESPONSE = 0x0003
        private const val CB_FORMAT_DATA_REQUEST = 0x0004
        private const val CB_FORMAT_DATA_RESPONSE = 0x0005
        private const val CB_CLIP_CAPS = 0x0007
        private const val CB_RESPONSE_OK = 0x0001
        private const val CB_RESPONSE_FAIL = 0x0002

        private const val CB_CAPSTYPE_GENERAL = 0x0001
        private const val CB_CAPS_VERSION_2 = 0x00000002
        private const val CB_USE_LONG_FORMAT_NAMES = 0x00000002
        private const val CF_UNICODETEXT = 13
        private const val MAX_MESSAGE_BYTES = 2 * 1024 * 1024
        private const val MAX_TEXT_CHARS = 256 * 1024

        private fun le16(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

        private fun le32(data: ByteArray, offset: Int): Int =
            le16(data, offset) or (le16(data, offset + 2) shl 16)

        private fun le16Bytes(value: Int) =
            byteArrayOf(value.toByte(), (value ushr 8).toByte())

        private fun le32Bytes(value: Int) =
            byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())
    }
}
