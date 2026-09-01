package com.zerotoship.z2term.gui.rdp

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * [MS-RDPEDYC] の `drdynvc` static virtual channel。
 *
 * Windows が作る Dynamic Virtual Channel のうち Graphics だけを受け入れ、それ以外には
 * STATUS_NOT_FOUND を返す。static channel の分割と DVC 自身の DATA_FIRST/DATA 分割は別階層なので、
 * それぞれ独立して復元する。
 */
internal class RdpDynamicChannel(
    private val sendStatic: (ByteArray) -> Unit,
    private val graphics: RdpGfx,
) {
    private var staticLength = 0
    private var staticFragments = ByteArrayOutputStream()
    private var version = 3
    private var graphicsChannelId: Int? = null
    private val dynamicFragments = mutableMapOf<Int, Fragment>()

    @Synchronized
    fun acceptStaticChunk(payload: ByteArray) {
        if (payload.size < CHANNEL_HEADER_SIZE) throw IOException("truncated drdynvc channel header")
        val totalLength = le32(payload, 0)
        val flags = le32(payload, 4)
        if (totalLength !in 0..MAX_STATIC_MESSAGE_BYTES) {
            throw IOException("invalid drdynvc static message length: $totalLength")
        }
        if (flags and CHANNEL_FLAG_FIRST != 0) {
            staticLength = totalLength
            staticFragments = ByteArrayOutputStream(totalLength)
        }
        if (staticLength == 0 && totalLength != 0) {
            throw IOException("drdynvc static continuation without first chunk")
        }
        staticFragments.write(payload, CHANNEL_HEADER_SIZE, payload.size - CHANNEL_HEADER_SIZE)
        if (staticFragments.size() > staticLength) throw IOException("drdynvc static message exceeds length")
        if (flags and CHANNEL_FLAG_LAST != 0) {
            if (staticFragments.size() != staticLength) throw IOException("incomplete drdynvc static message")
            val message = staticFragments.toByteArray()
            staticLength = 0
            staticFragments.reset()
            if (message.isNotEmpty()) acceptOrder(message)
        }
    }

    private fun acceptOrder(message: ByteArray) {
        val cursor = Cursor(message)
        val header = cursor.u8()
        val command = header ushr 4
        val sp = (header ushr 2) and 0x03
        val cbChannelId = header and 0x03
        when (command) {
            CMD_CAPABILITY -> capability(cursor)
            CMD_CREATE -> create(cursor, cbChannelId)
            CMD_DATA_FIRST -> dataFirst(cursor, cbChannelId, sp)
            CMD_DATA -> data(cursor, cbChannelId)
            CMD_CLOSE -> close(cursor, cbChannelId)
            CMD_DATA_FIRST_COMPRESSED, CMD_DATA_COMPRESSED ->
                throw IOException("compressed drdynvc data is not supported")
            else -> throw IOException("unsupported drdynvc command: 0x${command.toString(16)}")
        }
    }

    private fun capability(cursor: Cursor) {
        cursor.u8() // pad
        version = cursor.le16()
        if (version == 2 || version == 3) {
            repeat(4) { cursor.le16() } // PriorityCharge0..3
        }
        // ⚠ 余剰バイトを拒否しない。相手が将来の field を足しても capability 交換で止めない。
        Log.i(TAG, "drdynvc: capability version=$version")
        // CAPABILITY_RESPONSE: Cmd + pad + negotiated version。FreeRDP と同じ version を返す。
        sendStatic(byteArrayOf(0x50, 0, version.toByte(), (version ushr 8).toByte()))
    }

    private fun create(cursor: Cursor, cbChannelId: Int) {
        val channelId = cursor.variableUInt(cbChannelId)
        val nameBytes = cursor.bytesUntilZero()
        cursor.requireEnd()
        val name = nameBytes.toString(StandardCharsets.US_ASCII)
        val accepted = name == GRAPHICS_CHANNEL_NAME
        Log.i(TAG, "drdynvc: create id=$channelId name='$name' accepted=$accepted")
        val response = Writer().apply {
            u8((CMD_CREATE shl 4) or cbChannelId)
            variableUInt(channelId, cbChannelId)
            le32(if (accepted) 0 else STATUS_NOT_FOUND)
        }.array()
        sendStatic(response)
        if (accepted) {
            graphicsChannelId = channelId
            dynamicFragments.remove(channelId)
            sendDynamic(channelId, graphics.capabilitiesAdvertise())
            Log.i(TAG, "drdynvc: graphics capabilities sent")
        }
    }

    private fun dataFirst(cursor: Cursor, cbChannelId: Int, cbLength: Int) {
        val channelId = cursor.variableUInt(cbChannelId)
        val totalLength = cursor.variableUInt(cbLength)
        if (totalLength !in 1..MAX_DYNAMIC_MESSAGE_BYTES) {
            throw IOException("invalid dynamic channel message length: $totalLength")
        }
        val fragment = Fragment(totalLength, ByteArrayOutputStream(totalLength))
        fragment.data.write(cursor.remainingBytes())
        if (fragment.data.size() > totalLength) throw IOException("dynamic channel data exceeds length")
        dynamicFragments[channelId] = fragment
        finishDynamicIfReady(channelId, fragment)
    }

    private fun data(cursor: Cursor, cbChannelId: Int) {
        val channelId = cursor.variableUInt(cbChannelId)
        val body = cursor.remainingBytes()
        val fragment = dynamicFragments[channelId]
        if (fragment == null) {
            deliver(channelId, body)
            return
        }
        fragment.data.write(body)
        if (fragment.data.size() > fragment.length) throw IOException("dynamic channel data exceeds length")
        finishDynamicIfReady(channelId, fragment)
    }

    private fun finishDynamicIfReady(channelId: Int, fragment: Fragment) {
        if (fragment.data.size() != fragment.length) return
        dynamicFragments.remove(channelId)
        deliver(channelId, fragment.data.toByteArray())
    }

    private fun deliver(channelId: Int, body: ByteArray) {
        if (channelId == graphicsChannelId) graphics.accept(body)
        // Windows は未登録 DVC にも DATA を送ることがある。CREATE で拒否済みなので無視する。
    }

    private fun close(cursor: Cursor, cbChannelId: Int) {
        val channelId = cursor.variableUInt(cbChannelId)
        cursor.requireEnd()
        dynamicFragments.remove(channelId)
        if (channelId == graphicsChannelId) {
            graphicsChannelId = null
            graphics.reset()
        }
        sendStatic(Writer().apply {
            u8((CMD_CLOSE shl 4) or cbChannelId)
            variableUInt(channelId, cbChannelId)
        }.array())
    }

    /** Graphics の client-to-server PDU を DVC orderへ包む。 */
    @Synchronized
    fun sendGraphics(message: ByteArray) {
        graphicsChannelId?.let { sendDynamic(it, message) }
    }

    private fun sendDynamic(channelId: Int, message: ByteArray) {
        require(message.isNotEmpty())
        val cbChannelId = encodedWidth(channelId)
        val shortHeader = 1 + widthBytes(cbChannelId)
        if (message.size <= DVC_CHUNK_BYTES - shortHeader) {
            sendStatic(Writer().apply {
                u8((CMD_DATA shl 4) or cbChannelId)
                variableUInt(channelId, cbChannelId)
                bytes(message)
            }.array())
            return
        }

        val cbLength = encodedWidth(message.size)
        var offset = 0
        val first = Writer().apply {
            u8((CMD_DATA_FIRST shl 4) or (cbLength shl 2) or cbChannelId)
            variableUInt(channelId, cbChannelId)
            variableUInt(message.size, cbLength)
            val count = minOf(DVC_CHUNK_BYTES - size, message.size)
            bytes(message.copyOfRange(0, count))
            offset = count
        }.array()
        sendStatic(first)
        while (offset < message.size) {
            val count = minOf(DVC_CHUNK_BYTES - shortHeader, message.size - offset)
            sendStatic(Writer().apply {
                u8((CMD_DATA shl 4) or cbChannelId)
                variableUInt(channelId, cbChannelId)
                bytes(message.copyOfRange(offset, offset + count))
            }.array())
            offset += count
        }
    }

    private data class Fragment(val length: Int, val data: ByteArrayOutputStream)

    private class Cursor(private val data: ByteArray) {
        private var offset = 0
        private val remaining: Int get() = data.size - offset
        fun u8(): Int {
            if (remaining < 1) throw IOException("truncated drdynvc PDU")
            return data[offset++].toInt() and 0xFF
        }
        fun le16(): Int = u8() or (u8() shl 8)
        fun variableUInt(width: Int): Int = when (width) {
            0 -> u8()
            1 -> le16()
            else -> u8() or (u8() shl 8) or (u8() shl 16) or (u8() shl 24)
        }
        fun bytesUntilZero(): ByteArray {
            val start = offset
            while (remaining > 0 && data[offset] != 0.toByte()) offset++
            if (remaining == 0) throw IOException("unterminated dynamic channel name")
            return data.copyOfRange(start, offset).also { offset++ }
        }
        fun remainingBytes(): ByteArray = data.copyOfRange(offset, data.size).also { offset = data.size }
        fun requireEnd() { if (remaining != 0) throw IOException("trailing drdynvc data: $remaining bytes") }
    }

    private class Writer {
        private val out = ByteArrayOutputStream()
        val size: Int get() = out.size()
        fun u8(value: Int) = out.write(value and 0xFF)
        fun le16(value: Int) { u8(value); u8(value ushr 8) }
        fun le32(value: Int) { repeat(4) { u8(value ushr (it * 8)) } }
        fun bytes(value: ByteArray) = out.write(value)
        fun variableUInt(value: Int, width: Int) = when (width) {
            0 -> u8(value)
            1 -> le16(value)
            else -> le32(value)
        }
        fun array(): ByteArray = out.toByteArray()
    }

    companion object {
        private const val TAG = "RdpDynamicChannel"
        private const val GRAPHICS_CHANNEL_NAME = "Microsoft::Windows::RDS::Graphics"
        private const val CHANNEL_HEADER_SIZE = 8
        private const val CHANNEL_FLAG_FIRST = 0x00000001
        private const val CHANNEL_FLAG_LAST = 0x00000002
        private const val CMD_CREATE = 0x01
        private const val CMD_DATA_FIRST = 0x02
        private const val CMD_DATA = 0x03
        private const val CMD_CLOSE = 0x04
        private const val CMD_CAPABILITY = 0x05
        private const val CMD_DATA_FIRST_COMPRESSED = 0x06
        private const val CMD_DATA_COMPRESSED = 0x07
        private const val STATUS_NOT_FOUND = 0xC0000225.toInt()
        private const val DVC_CHUNK_BYTES = 1600
        private const val MAX_STATIC_MESSAGE_BYTES = 64 * 1024
        private const val MAX_DYNAMIC_MESSAGE_BYTES = 32 * 1024 * 1024

        private fun le32(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)

        private fun encodedWidth(value: Int): Int = when (value) {
            in 0..0xFF -> 0
            in 0..0xFFFF -> 1
            else -> 2
        }
        private fun widthBytes(width: Int): Int = when (width) { 0 -> 1; 1 -> 2; else -> 4 }
    }
}
