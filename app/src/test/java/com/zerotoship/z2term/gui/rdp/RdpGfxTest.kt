package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RdpGfxTest {
    @Test
    fun uncompressedSurfaceIsMappedToOutputAndFrameIsAcknowledged() {
        val sent = mutableListOf<ByteArray>()
        var frame: IntArray? = null
        var frameWidth = 0
        var frameHeight = 0
        val gfx = RdpGfx(sent::add) { width, height, pixels, _ ->
            frameWidth = width
            frameHeight = height
            frame = pixels.copyOf()
        }
        val resetBody = bytes {
            le32(2); le32(1); le32(1)
            le32(0); le32(0); le32(1); le32(0); le32(1)
            zero(300)
        }
        val packets = bytes {
            pdu(0x000E, resetBody)
            pdu(0x0009, bytes { le16(0); le16(2); le16(1); u8(0x21) })
            pdu(0x0017, bytes { le16(0); le16(0); le32(0); le32(0); le32(2); le32(1) })
            pdu(0x000B, bytes { le32(123); le32(7) })
            pdu(0x0001, bytes {
                le16(0); le16(0); u8(0x21)
                le16(0); le16(0); le16(2); le16(1)
                le32(8)
                // BGRA: opaque blue, opaque red.
                u8(0xFF); u8(0); u8(0); u8(0xFF)
                u8(0); u8(0); u8(0xFF); u8(0xFF)
            })
            pdu(0x000C, bytes { le32(7) })
        }

        gfx.accept(byteArrayOf(0xE0.toByte(), 0x04) + packets)

        assertEquals(2, frameWidth)
        assertEquals(1, frameHeight)
        assertNotNull(frame)
        assertArrayEquals(intArrayOf(0xFF0000FF.toInt(), 0xFFFF0000.toInt()), frame)
        assertEquals(1, sent.size)
        assertEquals(0x000D, le16(sent.single(), 0))
        assertEquals(7, le32(sent.single(), 12))
        assertEquals(1, le32(sent.single(), 16))
    }

    @Test
    fun cachedRectangleIsBlittedBackAndUnknownCommandsDoNotStopTheStream() {
        val sent = mutableListOf<ByteArray>()
        var frame: IntArray? = null
        val gfx = RdpGfx(sent::add) { _, _, pixels, _ -> frame = pixels.copyOf() }
        val resetBody = bytes {
            le32(2); le32(2); le32(1)
            le32(0); le32(0); le32(2); le32(0); le32(1)
            zero(300)
        }

        gfx.accept(byteArrayOf(0xE0.toByte(), 0x04) + bytes {
            pdu(0x000E, resetBody)
            pdu(0x0009, bytes { le16(0); le16(2); le16(2); u8(0x20) })
            pdu(0x000F, bytes { le16(0); le16(0); le32(0); le32(0) })
            // 左上だけ赤く塗り、その 1 画素を cache slot 5 に取り置く。
            pdu(0x0004, bytes {
                le16(0); u8(0); u8(0); u8(0xFF); u8(0xFF); le16(1)
                le16(0); le16(0); le16(1); le16(1)
            })
            pdu(0x0006, bytes {
                le16(0); le32(0); le32(0); le16(5)
                le16(0); le16(0); le16(1); le16(1)
            })
            // 未実装の command (MAP_SURFACE_TO_WINDOW) が挟まっても止まらない。
            pdu(0x0015, bytes { le16(0); le32(0); le32(0) })
            // cache slot 5 を右下へ貼る。
            pdu(0x0007, bytes { le16(5); le16(0); le16(1); le16(1); le16(1) })
            pdu(0x000B, bytes { le32(0); le32(1) })
            pdu(0x000C, bytes { le32(1) })
        })

        val red = 0xFFFF0000.toInt()
        val black = 0xFF000000.toInt()
        assertArrayEquals(intArrayOf(red, black, black, red), frame)
        assertEquals(1, sent.size)
    }

    @Test
    fun unimplementedCodecLeavesTheRestOfTheFrameIntact() {
        var frame: IntArray? = null
        val gfx = RdpGfx({}) { _, _, pixels, _ -> frame = pixels.copyOf() }
        val resetBody = bytes {
            le32(2); le32(1); le32(1)
            le32(0); le32(0); le32(2); le32(0); le32(1)
            zero(300)
        }

        gfx.accept(byteArrayOf(0xE0.toByte(), 0x04) + bytes {
            pdu(0x000E, resetBody)
            pdu(0x0009, bytes { le16(0); le16(2); le16(1); u8(0x20) })
            pdu(0x000F, bytes { le16(0); le16(0); le32(0); le32(0) })
            pdu(0x0004, bytes {
                le16(0); u8(0); u8(0xFF); u8(0); u8(0xFF); le16(1)
                le16(0); le16(0); le16(2); le16(1)
            })
            // Progressive の WIRE_TO_SURFACE_2。decoder は無いので数えて捨てる。
            pdu(0x0002, bytes { le16(0); le16(0x0009); le32(1); u8(0x20); zero(4) })
            pdu(0x000C, bytes { le32(1) })
        })

        val green = 0xFF00FF00.toInt()
        assertArrayEquals(intArrayOf(green, green), frame)
    }

    @Test
    fun capabilitiesAdvertiseOnlyRdp8ThinClient() {
        val caps = RdpGfx({}, { _, _, _, _ -> }).capabilitiesAdvertise()

        assertEquals(0x0012, le16(caps, 0))
        assertEquals(22, le32(caps, 4))
        assertEquals(1, le16(caps, 8))
        assertEquals(0x00080004, le32(caps, 10))
        assertEquals(1, le32(caps, 18))
    }

    private fun bytes(block: Writer.() -> Unit): ByteArray = Writer().apply(block).array()
    private class Writer {
        private val out = ByteArrayOutputStream()
        fun u8(value: Int) = out.write(value and 0xFF)
        fun le16(value: Int) { u8(value); u8(value ushr 8) }
        fun le32(value: Int) { repeat(4) { u8(value ushr (it * 8)) } }
        fun zero(count: Int) = out.write(ByteArray(count))
        fun pdu(command: Int, body: ByteArray) {
            le16(command); le16(0); le32(body.size + 8); out.write(body)
        }
        fun array() = out.toByteArray()
    }
    private fun le16(data: ByteArray, offset: Int) =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    private fun le32(data: ByteArray, offset: Int) = le16(data, offset) or (le16(data, offset + 2) shl 16)
}
