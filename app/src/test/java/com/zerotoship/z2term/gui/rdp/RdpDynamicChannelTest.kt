package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpDynamicChannelTest {
    @Test
    fun capabilityAndGraphicsCreateProduceResponsesAndMinimalCaps() {
        val sent = mutableListOf<ByteArray>()
        lateinit var dynamic: RdpDynamicChannel
        val graphics = RdpGfx(send = { dynamic.sendGraphics(it) }, onFrame = { _, _, _, _ -> })
        dynamic = RdpDynamicChannel(sent::add, graphics)

        dynamic.acceptStaticChunk(staticChunk(hex("50 00 03 00 01 00 02 00 03 00 04 00")))
        assertArrayEquals(hex("50 00 03 00"), sent.removeAt(0))

        val create = byteArrayOf(0x18, 7) +
            "Microsoft::Windows::RDS::Graphics".toByteArray() + byteArrayOf(0)
        dynamic.acceptStaticChunk(staticChunk(create))

        assertArrayEquals(hex("10 07 00 00 00 00"), sent[0])
        assertEquals(0x30, sent[1][0].toInt() and 0xFF)
        assertEquals(7, sent[1][1].toInt() and 0xFF)
        val caps = sent[1].copyOfRange(2, sent[1].size)
        assertEquals(0x12, le16(caps, 0))
        assertEquals(22, le32(caps, 4))
        assertEquals(0x00080004, le32(caps, 10))
        assertEquals(1, le32(caps, 18))
    }

    @Test
    fun unknownDynamicChannelIsRejectedWithoutOpeningGraphics() {
        val sent = mutableListOf<ByteArray>()
        lateinit var dynamic: RdpDynamicChannel
        dynamic = RdpDynamicChannel(sent::add, RdpGfx({ dynamic.sendGraphics(it) }) { _, _, _, _ -> })
        val create = byteArrayOf(0x10, 4) + "unknown".toByteArray() + byteArrayOf(0)

        dynamic.acceptStaticChunk(staticChunk(create))

        assertEquals(1, sent.size)
        assertEquals(0xC0000225.toInt(), le32(sent.single(), 2))
    }

    @Test
    fun staticChannelChunksAreReassembledBeforeParsing() {
        val sent = mutableListOf<ByteArray>()
        lateinit var dynamic: RdpDynamicChannel
        dynamic = RdpDynamicChannel(sent::add, RdpGfx({ dynamic.sendGraphics(it) }) { _, _, _, _ -> })
        val message = hex("50 00 01 00")

        dynamic.acceptStaticChunk(staticChunk(message.copyOfRange(0, 2), message.size, first = true, last = false))
        assertTrue(sent.isEmpty())
        dynamic.acceptStaticChunk(staticChunk(message.copyOfRange(2, 4), message.size, first = false, last = true))

        assertArrayEquals(hex("50 00 01 00"), sent.single())
    }

    private fun staticChunk(
        body: ByteArray,
        total: Int = body.size,
        first: Boolean = true,
        last: Boolean = true,
    ): ByteArray = ByteArrayOutputStream().apply {
        write(le32Bytes(total))
        write(le32Bytes((if (first) 1 else 0) or (if (last) 2 else 0)))
        write(body)
    }.toByteArray()

    private fun le16(data: ByteArray, offset: Int) =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    private fun le32(data: ByteArray, offset: Int) =
        le16(data, offset) or (le16(data, offset + 2) shl 16)
    private fun le32Bytes(value: Int) = ByteArray(4) { (value ushr (it * 8)).toByte() }
    private fun hex(value: String) = value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
