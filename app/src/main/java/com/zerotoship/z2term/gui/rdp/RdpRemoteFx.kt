package com.zerotoship.z2term.gui.rdp

import java.io.IOException

/**
 * RemoteFX (`RDPGFX_CODECID_CAVIDEO`) の decoder ([MS-RDPRFX])。
 *
 * ⭐ **実 Windows 11 が RDPGFX で選ぶのがこの codec**（0.8.477 で実測）。1 タイル 64x64 を
 * RLGR 展開 → 逆量子化 → 逆ウェーブレット → YCbCr→RGB の順に戻し、region の矩形で
 * 切り抜いて surface へ貼る。
 *
 * ⚠ 中間値は C 実装と同じ **16bit で丸める**。ここを 32bit のままにすると、桁が溢れる場面で
 * 出力が食い違う。
 */
internal class RdpRemoteFx {
    /** RLGR1 と RLGR3 は entropy 符号だけが違う。どちらかは TS_RFX_CONTEXT が指定する。 */
    private var rlgr3 = false
    private var channelWidth = 0
    private var channelHeight = 0
    private var quants = IntArray(0)
    private var numQuant = 0

    /** タイルごとに作り直さない作業領域。1 フレームで数百タイル来るので確保しっぱなしにする。 */
    private val components = Array(3) { ShortArray(TILE_PIXELS) }
    private val idwt = ShortArray(TILE_PIXELS)
    private val tile = IntArray(TILE_PIXELS)
    private val rects = ArrayList<Rect>()

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    fun reset() {
        rlgr3 = false
        channelWidth = 0
        channelHeight = 0
        quants = IntArray(0)
        numQuant = 0
        rects.clear()
    }

    /**
     * [encoded] の RFX message を [destination] の ([x],[y]) を原点として展開する。
     *
     * region の矩形もタイル座標も原点からの相対値。⚠ **surface からはみ出す分は捨てる**
     * （相手は 64 の倍数でタイルを送るので、端は必ずはみ出す）。
     */
    fun decode(
        encoded: ByteArray,
        destination: IntArray,
        destinationWidth: Int,
        destinationHeight: Int,
        x: Int,
        y: Int,
    ) {
        val cursor = Cursor(encoded, 0, encoded.size)
        while (cursor.remaining > BLOCK_HEADER_SIZE) {
            val blockType = cursor.le16()
            val blockLength = cursor.le32()
            if (blockLength < BLOCK_HEADER_SIZE || blockLength - BLOCK_HEADER_SIZE > cursor.remaining) {
                throw IOException("invalid RemoteFX block length: $blockLength")
            }
            var bodyLength = blockLength - BLOCK_HEADER_SIZE
            if (blockType in WBT_CONTEXT..WBT_EXTENSION) {
                // RFX_CODEC_CHANNELT。codecId は必ず 1、channelId は WBT_CONTEXT だけ 0xFF。
                if (bodyLength < 2) throw IOException("truncated RemoteFX codec channel header")
                val codecId = cursor.u8()
                cursor.u8()
                if (codecId != 1) throw IOException("invalid RemoteFX codecId: $codecId")
                bodyLength -= 2
            }
            val body = cursor.sub(bodyLength)
            when (blockType) {
                WBT_SYNC -> sync(body)
                WBT_CONTEXT -> context(body)
                WBT_CHANNELS -> channels(body)
                WBT_CODEC_VERSIONS, WBT_FRAME_BEGIN, WBT_FRAME_END -> Unit
                WBT_REGION -> region(body)
                WBT_EXTENSION -> tileSet(body, destination, destinationWidth, destinationHeight, x, y)
                else -> throw IOException("unknown RemoteFX block type: 0x${blockType.toString(16)}")
            }
        }
    }

    private fun sync(body: Cursor) {
        val magic = body.le32()
        if (magic != WF_MAGIC) throw IOException("invalid RemoteFX sync magic")
    }

    private fun context(body: Cursor) {
        body.u8() // ctxId
        body.le16() // tileSize は常に 64x64
        val properties = body.le16()
        rlgr3 = when ((properties ushr 9) and 0x0F) {
            CLW_ENTROPY_RLGR1 -> false
            CLW_ENTROPY_RLGR3 -> true
            else -> throw IOException("unknown RemoteFX entropy algorithm")
        }
    }

    private fun channels(body: Cursor) {
        val count = body.u8()
        if (count < 1) throw IOException("RemoteFX stream has no channel")
        body.u8() // channelId
        channelWidth = body.le16()
        channelHeight = body.le16()
        if (channelWidth < 1 || channelHeight < 1) throw IOException("invalid RemoteFX channel size")
    }

    private fun region(body: Cursor) {
        body.u8() // regionFlags
        val count = body.le16()
        rects.clear()
        if (count == 0) {
            // ⚠ numRects が 0 のときは「画面全体」を意味する ([MS-RDPRFX] 2.2.2.3.3)。
            rects += Rect(0, 0, channelWidth, channelHeight)
            return
        }
        repeat(count) { rects += Rect(body.le16(), body.le16(), body.le16(), body.le16()) }
        val regionType = body.le16()
        val tileSets = body.le16()
        if (regionType != CBT_REGION || tileSets != 1) {
            throw IOException("invalid RemoteFX region: type=$regionType tileSets=$tileSets")
        }
    }

    private fun tileSet(
        body: Cursor,
        destination: IntArray,
        destinationWidth: Int,
        destinationHeight: Int,
        originX: Int,
        originY: Int,
    ) {
        val subtype = body.le16()
        if (subtype != CBT_TILESET) throw IOException("invalid RemoteFX tile set subtype")
        body.le16() // idx
        body.le16() // properties
        numQuant = body.u8()
        body.u8() // tileSize
        if (numQuant < 1) throw IOException("RemoteFX tile set has no quantization value")
        val tiles = body.le16()
        if (tiles == 0) return // Windows は空の tile set を送ることがある
        body.le32() // tilesDataSize

        if (quants.size < numQuant * QUANT_VALUES) quants = IntArray(numQuant * QUANT_VALUES)
        for (index in 0 until numQuant) {
            // 5 バイトに 4bit ずつ 10 個。LL3, LH3, HL3, HH3, LH2, HL2, HH2, LH1, HL1, HH1 の順。
            repeat(5) {
                val packed = body.u8()
                quants[index * QUANT_VALUES + it * 2] = packed and 0x0F
                quants[index * QUANT_VALUES + it * 2 + 1] = packed ushr 4
            }
        }

        repeat(tiles) {
            if (body.remaining < TILE_HEADER_SIZE) throw IOException("truncated RemoteFX tile")
            val blockType = body.le16()
            val blockLength = body.le32()
            if (blockType != CBT_TILE) throw IOException("expected a RemoteFX tile block")
            if (blockLength < TILE_HEADER_SIZE || blockLength - BLOCK_HEADER_SIZE > body.remaining) {
                throw IOException("invalid RemoteFX tile length: $blockLength")
            }
            val tileBody = body.sub(blockLength - BLOCK_HEADER_SIZE)
            val quantY = tileBody.u8()
            val quantCb = tileBody.u8()
            val quantCr = tileBody.u8()
            if (quantY >= numQuant || quantCb >= numQuant || quantCr >= numQuant) {
                throw IOException("RemoteFX tile refers to a missing quantization value")
            }
            val tileX = tileBody.le16() * TILE_SIZE
            val tileY = tileBody.le16() * TILE_SIZE
            val yLength = tileBody.le16()
            val cbLength = tileBody.le16()
            val crLength = tileBody.le16()
            val yData = tileBody.slice(yLength)
            val cbData = tileBody.slice(cbLength)
            val crData = tileBody.slice(crLength)

            decodeComponent(yData, quantY, components[0])
            decodeComponent(cbData, quantCb, components[1])
            decodeComponent(crData, quantCr, components[2])
            toRgb()
            blit(destination, destinationWidth, destinationHeight, originX + tileX, originY + tileY,
                originX, originY)
        }
    }

    private fun decodeComponent(data: Cursor, quantIndex: Int, buffer: ShortArray) {
        rlgrDecode(data, buffer)
        // LL3 だけは隣との差分で送られてくる。
        for (index in 4033 until TILE_PIXELS) {
            buffer[index] = (buffer[index] + buffer[index - 1]).toShort()
        }
        val quant = quantIndex * QUANT_VALUES
        // ⚠ 係数は << 5 の状態のまま残す。YCbCr→RGB の側で最後に戻す。
        shiftLeft(buffer, 0, 1024, quants[quant + 8] - 1) // HL1
        shiftLeft(buffer, 1024, 1024, quants[quant + 7] - 1) // LH1
        shiftLeft(buffer, 2048, 1024, quants[quant + 9] - 1) // HH1
        shiftLeft(buffer, 3072, 256, quants[quant + 5] - 1) // HL2
        shiftLeft(buffer, 3328, 256, quants[quant + 4] - 1) // LH2
        shiftLeft(buffer, 3584, 256, quants[quant + 6] - 1) // HH2
        shiftLeft(buffer, 3840, 64, quants[quant + 2] - 1) // HL3
        shiftLeft(buffer, 3904, 64, quants[quant + 1] - 1) // LH3
        shiftLeft(buffer, 3968, 64, quants[quant + 3] - 1) // HH3
        shiftLeft(buffer, 4032, 64, quants[quant] - 1) // LL3
        inverseDwt(buffer, 3840, 8)
        inverseDwt(buffer, 3072, 16)
        inverseDwt(buffer, 0, 32)
    }

    private fun shiftLeft(buffer: ShortArray, offset: Int, count: Int, factor: Int) {
        if (factor <= 0) return
        for (index in offset until offset + count) {
            buffer[index] = (buffer[index].toInt() shl factor).toShort()
        }
    }

    /**
     * 3 段の逆ウェーブレット 1 段分。副帯は HL(0), LH(1), HH(2), LL(3) の順に並んでいる。
     */
    private fun inverseDwt(buffer: ShortArray, offset: Int, subband: Int) {
        val total = subband * 2
        val area = subband * subband
        // 横方向。L は LL と HL、H は LH と HH から作る。
        for (row in 0 until subband) {
            val ll = offset + area * 3 + row * subband
            val hl = offset + row * subband
            val lh = offset + area + row * subband
            val hh = offset + area * 2 + row * subband
            val lDst = row * total
            val hDst = area * 2 + row * total
            idwt[lDst] = (buffer[ll] - ((buffer[hl] + buffer[hl] + 1) shr 1)).toShort()
            idwt[hDst] = (buffer[lh] - ((buffer[hh] + buffer[hh] + 1) shr 1)).toShort()
            for (n in 1 until subband) {
                idwt[lDst + n * 2] =
                    (buffer[ll + n] - ((buffer[hl + n - 1] + buffer[hl + n] + 1) shr 1)).toShort()
                idwt[hDst + n * 2] =
                    (buffer[lh + n] - ((buffer[hh + n - 1] + buffer[hh + n] + 1) shr 1)).toShort()
            }
            for (n in 0 until subband - 1) {
                val at = n * 2
                idwt[lDst + at + 1] =
                    ((buffer[hl + n].toInt() shl 1) + ((idwt[lDst + at] + idwt[lDst + at + 2]) shr 1)).toShort()
                idwt[hDst + at + 1] =
                    ((buffer[hh + n].toInt() shl 1) + ((idwt[hDst + at] + idwt[hDst + at + 2]) shr 1)).toShort()
            }
            val last = (subband - 1) * 2
            idwt[lDst + last + 1] =
                ((buffer[hl + subband - 1].toInt() shl 1) + idwt[lDst + last]).toShort()
            idwt[hDst + last + 1] =
                ((buffer[hh + subband - 1].toInt() shl 1) + idwt[hDst + last]).toShort()
        }
        // 縦方向。結果は元の buffer へ戻す。
        for (column in 0 until total) {
            var l = column
            var h = column + subband * total
            var dst = offset + column
            buffer[dst] = (idwt[l] - ((idwt[h] * 2 + 1) shr 1)).toShort()
            for (n in 1 until subband) {
                l += total
                h += total
                buffer[dst + total * 2] =
                    (idwt[l] - ((idwt[h - total] + idwt[h] + 1) shr 1)).toShort()
                buffer[dst + total] =
                    ((idwt[h - total].toInt() shl 1) + ((buffer[dst] + buffer[dst + total * 2]) shr 1)).toShort()
                dst += total * 2
            }
            buffer[dst + total] = ((idwt[h].toInt() shl 1) + ((buffer[dst] * 2) shr 1)).toShort()
        }
    }

    private fun toRgb() {
        val yBuffer = components[0]
        val cbBuffer = components[1]
        val crBuffer = components[2]
        for (index in 0 until TILE_PIXELS) {
            val y = (yBuffer[index] + 4096) shl 16
            val cb = cbBuffer[index].toInt()
            val cr = crBuffer[index].toInt()
            val r = ((cr * CR_R + y) shr 16) shr 5
            val g = ((y - cb * CB_G - cr * CR_G) shr 16) shr 5
            val b = ((cb * CB_B + y) shr 16) shr 5
            tile[index] = OPAQUE or (clip(r) shl 16) or (clip(g) shl 8) or clip(b)
        }
    }

    private fun blit(
        destination: IntArray,
        destinationWidth: Int,
        destinationHeight: Int,
        tileX: Int,
        tileY: Int,
        originX: Int,
        originY: Int,
    ) {
        for (rect in rects) {
            // region の矩形は原点からの相対値。タイルとの重なりだけを貼る。
            val clipLeft = maxOf(tileX, originX + rect.x, 0)
            val clipTop = maxOf(tileY, originY + rect.y, 0)
            val clipRight = minOf(tileX + TILE_SIZE, originX + rect.x + rect.width, destinationWidth)
            val clipBottom = minOf(tileY + TILE_SIZE, originY + rect.y + rect.height, destinationHeight)
            if (clipLeft >= clipRight || clipTop >= clipBottom) continue
            for (row in clipTop until clipBottom) {
                val source = (row - tileY) * TILE_SIZE + (clipLeft - tileX)
                tile.copyInto(destination, row * destinationWidth + clipLeft, source,
                    source + (clipRight - clipLeft))
            }
        }
    }

    /**
     * RLGR1 / RLGR3 展開 ([MS-RDPRFX] 3.1.8.1.7.3)。
     *
     * `k` が 0 でない間は零の連長（RL モード）、0 なら 1 係数ずつ（GR モード）。連長と
     * Golomb-Rice の分割位置 `kp` / `krp` は、出てきた記号に応じて上下する。
     */
    private fun rlgrDecode(data: Cursor, buffer: ShortArray) {
        val bits = BitReader(data)
        var kp = 1 shl LSGR
        var k = kp shr LSGR
        var krp = 1 shl LSGR
        var kr = krp shr LSGR
        var out = 0

        while (bits.remaining > 0 && out < TILE_PIXELS) {
            if (k != 0) {
                // RL モード。1 が出るまでの 0 の個数が、連長の上位。
                val zeros = bits.countUntil(1) ?: break
                var run = 0
                repeat(zeros) {
                    run += 1 shl k
                    kp = minOf(kp + UP_GR, KPMAX)
                    k = kp shr LSGR
                }
                if (bits.remaining < k + 1) break
                run += bits.read(k)
                val negative = bits.read(1) != 0

                val ones = bits.countUntil(0) ?: break
                if (bits.remaining < kr) break
                val code = bits.read(kr) or (ones shl kr)
                if (ones == 0) {
                    krp = maxOf(krp - 2, 0)
                    kr = krp shr LSGR
                } else if (ones != 1) {
                    krp = minOf(krp + ones, KPMAX)
                    kr = krp shr LSGR
                }
                kp = maxOf(kp - DN_GR, 0)
                k = kp shr LSGR

                val magnitude = if (negative) -(code + 1) else code + 1
                val zeroCount = minOf(run, TILE_PIXELS - out)
                java.util.Arrays.fill(buffer, out, out + zeroCount, 0)
                out += zeroCount
                if (out < TILE_PIXELS) buffer[out++] = magnitude.toShort()
            } else {
                // GR モード。0 が出るまでの 1 の個数が符号の上位。
                val ones = bits.countUntil(0) ?: break
                if (bits.remaining < kr) break
                val code = bits.read(kr) or (ones shl kr)
                if (ones == 0) {
                    krp = maxOf(krp - 2, 0)
                    kr = krp shr LSGR
                } else if (ones != 1) {
                    krp = minOf(krp + ones, KPMAX)
                    kr = krp shr LSGR
                }
                if (!rlgr3) {
                    if (code == 0) {
                        kp = minOf(kp + UQ_GR, KPMAX)
                        k = kp shr LSGR
                        buffer[out++] = 0
                    } else {
                        kp = maxOf(kp - DQ_GR, 0)
                        k = kp shr LSGR
                        buffer[out++] = zigZag(code).toShort()
                    }
                } else {
                    // RLGR3 は 1 つの符号に 2 係数を詰める。
                    val width = if (code != 0) 32 - Integer.numberOfLeadingZeros(code) else 0
                    if (bits.remaining < width) break
                    val first = bits.read(width)
                    val second = code - first
                    if (first != 0 && second != 0) {
                        kp = maxOf(kp - 2 * DQ_GR, 0)
                        k = kp shr LSGR
                    } else if (first == 0 && second == 0) {
                        kp = minOf(kp + 2 * UQ_GR, KPMAX)
                        k = kp shr LSGR
                    }
                    buffer[out++] = zigZag(first).toShort()
                    if (out < TILE_PIXELS) buffer[out++] = zigZag(second).toShort()
                }
            }
        }
        java.util.Arrays.fill(buffer, out, TILE_PIXELS, 0)
    }

    /** 符号を最下位ビットに畳んだ表現を元に戻す。 */
    private fun zigZag(code: Int): Int = if (code and 1 != 0) -((code + 1) shr 1) else code shr 1

    private class BitReader(data: Cursor) {
        private val bytes = data.data
        private val start = data.start
        private val end = data.end
        private var position = start * 8

        val remaining: Int get() = end * 8 - position

        fun read(count: Int): Int {
            var value = 0
            repeat(count) { value = (value shl 1) or bit() }
            return value
        }

        /** [stop] が出るまでの反対のビット数。stop 自体も読み進める。尽きたら null。 */
        fun countUntil(stop: Int): Int? {
            var count = 0
            while (true) {
                if (remaining < 1) return null
                if (bit() == stop) return count
                count++
            }
        }

        private fun bit(): Int {
            val index = position ushr 3
            if (index >= end) return 0
            val value = (bytes[index].toInt() ushr (7 - (position and 7))) and 1
            position++
            return value
        }
    }

    private class Cursor(val data: ByteArray, var start: Int, val end: Int) {
        val remaining: Int get() = end - start
        fun u8(): Int {
            if (remaining < 1) throw IOException("truncated RemoteFX data")
            return data[start++].toInt() and 0xFF
        }
        fun le16(): Int = u8() or (u8() shl 8)
        fun le32(): Int = u8() or (u8() shl 8) or (u8() shl 16) or (u8() shl 24)
        fun sub(length: Int): Cursor {
            if (length < 0 || remaining < length) throw IOException("truncated RemoteFX block")
            return Cursor(data, start, start + length).also { start += length }
        }
        /** 中身を読まずに範囲だけ切り出す（tile の Y/Cb/Cr データ用）。 */
        fun slice(length: Int): Cursor = sub(length)
    }

    private companion object {
        const val BLOCK_HEADER_SIZE = 6
        const val TILE_HEADER_SIZE = 6 + 13
        const val TILE_SIZE = 64
        const val TILE_PIXELS = TILE_SIZE * TILE_SIZE
        const val QUANT_VALUES = 10
        const val WF_MAGIC = 0xCACCACCA.toInt()
        const val WBT_SYNC = 0xCCC0
        const val WBT_CODEC_VERSIONS = 0xCCC1
        const val WBT_CHANNELS = 0xCCC2
        const val WBT_CONTEXT = 0xCCC3
        const val WBT_FRAME_BEGIN = 0xCCC4
        const val WBT_FRAME_END = 0xCCC5
        const val WBT_REGION = 0xCCC6
        const val WBT_EXTENSION = 0xCCC7
        const val CBT_REGION = 0xCAC1
        const val CBT_TILESET = 0xCAC2
        const val CBT_TILE = 0xCAC3
        const val CLW_ENTROPY_RLGR1 = 0x01
        const val CLW_ENTROPY_RLGR3 = 0x04

        // RLGR の parameter 更新量 ([MS-RDPRFX] 3.1.8.1.7.1)。
        const val KPMAX = 80
        const val LSGR = 3
        const val UP_GR = 4
        const val DN_GR = 6
        const val UQ_GR = 3
        const val DQ_GR = 3

        // YCbCr→RGB の係数を 2^16 倍した整数。1.402525 / 0.714401 / 0.343730 / 1.769905。
        const val CR_R = 91916
        const val CR_G = 46819
        const val CB_G = 22527
        const val CB_B = 115992
        const val OPAQUE = 0xFF000000.toInt()

        fun clip(value: Int): Int = if (value < 0) 0 else if (value > 255) 255 else value
    }
}
