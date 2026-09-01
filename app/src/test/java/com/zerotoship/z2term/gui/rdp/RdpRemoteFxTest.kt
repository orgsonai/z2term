package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class RdpRemoteFxTest {
    @Test
    fun emptyComponentsDecodeToMidGray() {
        val pixels = IntArray(70 * 70)

        RdpRemoteFx().decode(
            message(tileData = ByteArray(0), rect = intArrayOf(0, 0, 70, 70)),
            pixels, 70, 70, 0, 0,
        )

        // Y=Cb=Cr=0 は (0 + 4096) >> 5 = 128 の無彩色。
        assertEquals(0xFF808080.toInt(), pixels[0])
        assertEquals(0xFF808080.toInt(), pixels[63 * 70 + 63])
        // タイルは 64x64 なので、その外は触らない。
        assertEquals(0, pixels[64])
        assertEquals(0, pixels[64 * 70])
    }

    @Test
    fun runLengthCodedDcFillsTheTileAndIsClippedToTheRegion() {
        val pixels = IntArray(70 * 70)

        // region を 10x10 に絞る。タイルは 64x64 だが、はみ出す分は貼らない。
        RdpRemoteFx().decode(
            message(tileData = dcTile(), rect = intArrayOf(0, 0, 10, 10)),
            pixels, 70, 70, 0, 0,
        )

        // LL3 = 72、量子化 (6-1) で << 5 されて Y=2304。(2304 + 4096) >> 5 = 200。
        assertEquals(0xFFC8C8C8.toInt(), pixels[0])
        assertEquals(0xFFC8C8C8.toInt(), pixels[9 * 70 + 9])
        assertEquals(0, pixels[10])
        assertEquals(0, pixels[10 * 70])
    }

    @Test
    fun tileIsPlacedAtTheDestinationOrigin() {
        val pixels = IntArray(200 * 100)

        RdpRemoteFx().decode(
            message(tileData = ByteArray(0), rect = intArrayOf(0, 0, 64, 64)),
            pixels, 200, 100, 100, 20,
        )

        assertEquals(0xFF808080.toInt(), pixels[20 * 200 + 100])
        assertEquals(0xFF808080.toInt(), pixels[83 * 200 + 163])
        assertEquals(0, pixels[19 * 200 + 100])
        assertEquals(0, pixels[20 * 200 + 99])
    }

    /**
     * 「零を 4032 個並べてから 72」を RLGR1 で書いた 1 記号。
     *
     * 連長は先頭の 0 の個数で 2 の冪を積み上げ (終端は 1)、余りを k ビットで送る。ここでは
     * 0 を 19 個で 3068、余り 964 を 10 ビットで足して 4032。値は Golomb-Rice の 1 が 35 個
     * (終端は 0) + 余り 1 で 71、これに 1 を足した 72 になる。
     */
    private fun dcTile(): ByteArray = BitWriter().apply {
        repeat(19) { zero() }
        one()
        bits(964, 10)
        zero() // 符号 (正)
        repeat(35) { one() }
        zero()
        one() // kr = 1 の余り
    }.array()

    private fun message(tileData: ByteArray, rect: IntArray): ByteArray {
        val sync = block(0xCCC0, bytes { le32(0xCACCACCA.toInt()); le16(0x0100) })
        val context = codecBlock(0xCCC3, 0xFF, bytes { u8(0); le16(0x40); le16(0x0200) })
        val channels = block(0xCCC2, bytes { u8(1); u8(0); le16(64); le16(64) })
        val region = codecBlock(0xCCC6, 0x00, bytes {
            u8(1); le16(1)
            le16(rect[0]); le16(rect[1]); le16(rect[2]); le16(rect[3])
            le16(0xCAC1); le16(1)
        })
        val tile = bytes {
            le16(0xCAC3); le32(6 + 13 + tileData.size)
            u8(0); u8(0); u8(0) // quantIdx Y / Cb / Cr
            le16(0); le16(0) // xIdx / yIdx
            le16(tileData.size); le16(0); le16(0) // YLen / CbLen / CrLen
            raw(tileData)
        }
        val tileSet = codecBlock(0xCCC7, 0x00, bytes {
            le16(0xCAC2); le16(0); le16(0)
            u8(1) // numQuant
            u8(0x40) // tileSize
            le16(1) // numTiles
            le32(tile.size)
            repeat(5) { u8(0x66) } // 10 個すべて 6
            raw(tile)
        })
        val frameBegin = codecBlock(0xCCC4, 0x00, bytes { le32(0); le16(1) })
        val frameEnd = codecBlock(0xCCC5, 0x00, ByteArray(0))
        return sync + context + channels + frameBegin + region + tileSet + frameEnd
    }

    private fun block(type: Int, body: ByteArray): ByteArray =
        bytes { le16(type); le32(body.size + 6); raw(body) }

    private fun codecBlock(type: Int, channelId: Int, body: ByteArray): ByteArray =
        bytes { le16(type); le32(body.size + 8); u8(1); u8(channelId); raw(body) }

    private fun bytes(block: Writer.() -> Unit): ByteArray = Writer().apply(block).array()

    private class Writer {
        private val out = ByteArrayOutputStream()
        fun u8(value: Int) = out.write(value and 0xFF)
        fun le16(value: Int) { u8(value); u8(value ushr 8) }
        fun le32(value: Int) { repeat(4) { u8(value ushr (it * 8)) } }
        fun raw(value: ByteArray) = out.write(value)
        fun array(): ByteArray = out.toByteArray()
    }

    private class BitWriter {
        private val out = ByteArrayOutputStream()
        private var current = 0
        private var filled = 0
        fun one() = bit(1)
        fun zero() = bit(0)
        fun bits(value: Int, count: Int) {
            for (index in count - 1 downTo 0) bit((value ushr index) and 1)
        }
        private fun bit(value: Int) {
            current = (current shl 1) or value
            filled++
            if (filled == 8) {
                out.write(current)
                current = 0
                filled = 0
            }
        }
        fun array(): ByteArray {
            if (filled > 0) out.write(current shl (8 - filled))
            return out.toByteArray()
        }
    }
}
