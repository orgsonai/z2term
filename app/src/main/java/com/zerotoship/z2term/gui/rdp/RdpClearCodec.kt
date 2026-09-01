package com.zerotoship.z2term.gui.rdp

import java.io.IOException

/** ClearCodec decoder ([MS-RDPEGFX] 2.2.4.1)。内部色は Android と同じ ARGB。 */
internal class RdpClearCodec {
    private var sequence = 0
    private val glyphs = mutableMapOf<Int, IntArray>()
    private val verticalBars = mutableMapOf<Int, IntArray>()
    private val shortVerticalBars = mutableMapOf<Int, IntArray>()
    private var verticalBarCursor = 0
    private var shortVerticalBarCursor = 0

    fun reset() {
        sequence = 0
        glyphs.clear()
        verticalBars.clear()
        shortVerticalBars.clear()
        verticalBarCursor = 0
        shortVerticalBarCursor = 0
    }

    fun deleteSurface(@Suppress("UNUSED_PARAMETER") surfaceId: Int) {
        // ClearCodec の glyph/vbar cache は surface ではなく connection 単位。
    }

    fun decode(
        @Suppress("UNUSED_PARAMETER") surfaceId: Int,
        encoded: ByteArray,
        destination: IntArray,
        destinationWidth: Int,
        destinationHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (width <= 0 || height <= 0 || x < 0 || y < 0 ||
            x + width > destinationWidth || y + height > destinationHeight
        ) throw IOException("invalid ClearCodec destination")
        val cursor = Cursor(encoded)
        val glyphFlags = cursor.u8()
        val packetSequence = cursor.u8()
        if (sequence == 0 && packetSequence != 0) sequence = packetSequence
        if (packetSequence != sequence) {
            throw IOException("unexpected ClearCodec sequence: $packetSequence != $sequence")
        }
        sequence = (packetSequence + 1) and 0xFF
        if (glyphFlags and FLAG_CACHE_RESET != 0) {
            verticalBarCursor = 0
            shortVerticalBarCursor = 0
        }

        var glyphIndex: Int? = null
        var target = destination
        var targetWidth = destinationWidth
        var targetHeight = destinationHeight
        var targetX = x
        var targetY = y
        if (glyphFlags and FLAG_GLYPH_HIT != 0 && glyphFlags and FLAG_GLYPH_INDEX == 0) {
            throw IOException("invalid ClearCodec glyph flags")
        }
        if (glyphFlags and FLAG_GLYPH_INDEX != 0) {
            glyphIndex = cursor.le16()
            if (glyphIndex !in 0 until GLYPH_CACHE_SIZE) throw IOException("invalid ClearCodec glyph index")
            if (glyphFlags and FLAG_GLYPH_HIT != 0) {
                val glyph = glyphs[glyphIndex] ?: throw IOException("missing ClearCodec glyph $glyphIndex")
                if (glyph.size < width * height) throw IOException("ClearCodec glyph is too small")
                copyRect(glyph, width, 0, 0, destination, destinationWidth, x, y, width, height)
                if (cursor.remaining == 0) return
            } else {
                target = IntArray(width * height)
                targetWidth = width
                targetHeight = height
                targetX = 0
                targetY = 0
            }
        }

        if (cursor.remaining < 12) throw IOException("truncated ClearCodec composition header")
        val residualLength = cursor.le32()
        val bandsLength = cursor.le32()
        val subcodecsLength = cursor.le32()
        decodeResidual(cursor.sub(residualLength), target, targetWidth, targetHeight,
            targetX, targetY, width, height)
        decodeBands(cursor.sub(bandsLength), target, targetWidth, targetHeight,
            targetX, targetY, width, height)
        decodeSubcodecs(cursor.sub(subcodecsLength), target, targetWidth, targetHeight,
            targetX, targetY, width, height)
        cursor.requireEnd()

        if (glyphIndex != null && glyphFlags and FLAG_GLYPH_HIT == 0) {
            glyphs[glyphIndex] = target
            copyRect(target, width, 0, 0, destination, destinationWidth, x, y, width, height)
        }
    }

    private fun decodeResidual(
        cursor: Cursor,
        destination: IntArray,
        destinationWidth: Int,
        destinationHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (cursor.remaining == 0) return
        val pixels = IntArray(width * height)
        var index = 0
        while (cursor.remaining > 0) {
            val color = cursor.bgr()
            val count = cursor.runLength()
            if (count < 0 || index + count > pixels.size) throw IOException("invalid ClearCodec residual run")
            java.util.Arrays.fill(pixels, index, index + count, color)
            index += count
        }
        if (index != pixels.size) throw IOException("incomplete ClearCodec residual bitmap")
        copyRect(pixels, width, 0, 0, destination, destinationWidth, x, y, width, height)
    }

    private fun decodeBands(
        cursor: Cursor,
        destination: IntArray,
        destinationWidth: Int,
        destinationHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        while (cursor.remaining > 0) {
            val xStart = cursor.le16()
            val xEnd = cursor.le16()
            val yStart = cursor.le16()
            val yEnd = cursor.le16()
            val background = cursor.bgr()
            if (xEnd < xStart || yEnd < yStart) throw IOException("invalid ClearCodec band")
            val barCount = xEnd - xStart + 1
            val barHeight = yEnd - yStart + 1
            if (barHeight > MAX_VBAR_HEIGHT || xStart + barCount > width || yStart + barHeight > height) {
                throw IOException("ClearCodec band exceeds destination")
            }
            repeat(barCount) { column ->
                val header = cursor.le16()
                val bar: IntArray = when {
                    header and 0xC000 == 0x4000 -> {
                        val short = shortVerticalBars[header and 0x3FFF]
                            ?: throw IOException("missing ClearCodec short vbar")
                        val yOn = cursor.u8()
                        buildBar(barHeight, yOn, short, background)
                    }
                    header and 0xC000 == 0x0000 -> {
                        val yOn = header and 0xFF
                        val yOff = (header ushr 8) and 0x3F
                        if (yOff < yOn || yOff - yOn > MAX_VBAR_HEIGHT) {
                            throw IOException("invalid ClearCodec short vbar span")
                        }
                        val short = IntArray(yOff - yOn) { cursor.bgr() }
                        shortVerticalBars[shortVerticalBarCursor] = short
                        shortVerticalBarCursor = (shortVerticalBarCursor + 1) % SHORT_VBAR_CACHE_SIZE
                        buildBar(barHeight, yOn, short, background)
                    }
                    header and 0x8000 != 0 -> {
                        val index = header and 0x7FFF
                        verticalBars[index]?.let { existing ->
                            if (existing.size == barHeight) existing else existing.copyOf(barHeight)
                        } ?: IntArray(barHeight)
                    }
                    else -> throw IOException("invalid ClearCodec vbar header")
                }
                if (header and 0x8000 == 0) {
                    verticalBars[verticalBarCursor] = bar
                    verticalBarCursor = (verticalBarCursor + 1) % VBAR_CACHE_SIZE
                }
                for (row in bar.indices) {
                    val dx = x + xStart + column
                    val dy = y + yStart + row
                    if (dx !in 0 until destinationWidth || dy !in 0 until destinationHeight) {
                        throw IOException("ClearCodec vbar exceeds surface")
                    }
                    destination[dy * destinationWidth + dx] = bar[row]
                }
            }
        }
    }

    private fun decodeSubcodecs(
        cursor: Cursor,
        destination: IntArray,
        destinationWidth: Int,
        destinationHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        while (cursor.remaining > 0) {
            val xStart = cursor.le16()
            val yStart = cursor.le16()
            val subWidth = cursor.le16()
            val subHeight = cursor.le16()
            val byteCount = cursor.le32()
            val codec = cursor.u8()
            if (subWidth <= 0 || subHeight <= 0 || xStart + subWidth > width || yStart + subHeight > height) {
                throw IOException("ClearCodec subcodec rectangle exceeds destination")
            }
            val data = cursor.sub(byteCount)
            when (codec) {
                SUBCODEC_UNCOMPRESSED -> {
                    if (data.remaining != subWidth * subHeight * 3) {
                        throw IOException("invalid ClearCodec uncompressed subcodec length")
                    }
                    for (row in 0 until subHeight) for (column in 0 until subWidth) {
                        destination[(y + yStart + row) * destinationWidth + x + xStart + column] = data.bgr()
                    }
                }
                SUBCODEC_RLEX -> decodeRlex(data, destination, destinationWidth, destinationHeight,
                    x + xStart, y + yStart, subWidth, subHeight)
                SUBCODEC_NSCODEC -> throw IOException("NSCodec inside ClearCodec is not supported")
                else -> throw IOException("unknown ClearCodec subcodec: $codec")
            }
            data.requireEnd()
        }
    }

    private fun decodeRlex(
        cursor: Cursor,
        destination: IntArray,
        destinationWidth: Int,
        destinationHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (x + width > destinationWidth || y + height > destinationHeight) {
            throw IOException("ClearCodec RLEX exceeds destination")
        }
        val paletteCount = cursor.u8()
        if (paletteCount !in 1..127) throw IOException("invalid ClearCodec RLEX palette")
        val palette = IntArray(paletteCount) { cursor.bgr() }
        val numberBits = floorLog2(paletteCount - 1) + 1
        val lowMask = (1 shl numberBits) - 1
        val highMask = (1 shl (8 - numberBits)) - 1
        var pixel = 0
        val pixelCount = width * height
        while (cursor.remaining > 0) {
            val packed = cursor.u8()
            val run = cursor.runLength()
            val suiteDepth = (packed ushr numberBits) and highMask
            val stopIndex = packed and lowMask
            val startIndex = stopIndex - suiteDepth
            if (startIndex !in palette.indices || stopIndex !in palette.indices || pixel + run + suiteDepth + 1 > pixelCount) {
                throw IOException("invalid ClearCodec RLEX suite")
            }
            repeat(run) { putLinear(destination, destinationWidth, x, y, width, pixel++, palette[startIndex]) }
            for (index in startIndex..stopIndex) {
                putLinear(destination, destinationWidth, x, y, width, pixel++, palette[index])
            }
        }
        if (pixel != pixelCount) throw IOException("incomplete ClearCodec RLEX bitmap")
    }

    private fun buildBar(height: Int, yOn: Int, short: IntArray, background: Int): IntArray {
        if (yOn > height) throw IOException("invalid ClearCodec vbar offset")
        return IntArray(height) { row ->
            val shortIndex = row - yOn
            if (shortIndex in short.indices) short[shortIndex] else background
        }
    }

    private fun putLinear(
        destination: IntArray,
        destinationWidth: Int,
        x: Int,
        y: Int,
        width: Int,
        index: Int,
        color: Int,
    ) {
        destination[(y + index / width) * destinationWidth + x + index % width] = color
    }

    private fun copyRect(
        source: IntArray,
        sourceWidth: Int,
        sourceX: Int,
        sourceY: Int,
        destination: IntArray,
        destinationWidth: Int,
        destinationX: Int,
        destinationY: Int,
        width: Int,
        height: Int,
    ) {
        repeat(height) { row ->
            source.copyInto(destination, (destinationY + row) * destinationWidth + destinationX,
                (sourceY + row) * sourceWidth + sourceX, (sourceY + row) * sourceWidth + sourceX + width)
        }
    }

    private class Cursor(private val data: ByteArray) {
        private var offset = 0
        val remaining: Int get() = data.size - offset
        fun u8(): Int {
            if (remaining < 1) throw IOException("truncated ClearCodec data")
            return data[offset++].toInt() and 0xFF
        }
        fun le16(): Int = u8() or (u8() shl 8)
        fun le32(): Int = u8() or (u8() shl 8) or (u8() shl 16) or (u8() shl 24)
        fun bgr(): Int {
            val b = u8()
            val g = u8()
            val r = u8()
            return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        fun runLength(): Int {
            val first = u8()
            if (first < 0xFF) return first
            val second = le16()
            return if (second < 0xFFFF) second else le32().also {
                if (it < 0) throw IOException("ClearCodec run is too large")
            }
        }
        fun sub(length: Int): Cursor {
            if (length < 0 || remaining < length) throw IOException("truncated ClearCodec section")
            return Cursor(data.copyOfRange(offset, offset + length)).also { offset += length }
        }
        fun requireEnd() { if (remaining != 0) throw IOException("trailing ClearCodec data: $remaining bytes") }
    }

    companion object {
        private const val FLAG_GLYPH_INDEX = 0x01
        private const val FLAG_GLYPH_HIT = 0x02
        private const val FLAG_CACHE_RESET = 0x04
        private const val SUBCODEC_UNCOMPRESSED = 0
        private const val SUBCODEC_NSCODEC = 1
        private const val SUBCODEC_RLEX = 2
        private const val GLYPH_CACHE_SIZE = 4000
        private const val VBAR_CACHE_SIZE = 32768
        private const val SHORT_VBAR_CACHE_SIZE = 16384
        private const val MAX_VBAR_HEIGHT = 52

        private fun floorLog2(value: Int): Int = if (value <= 0) 0 else 31 - Integer.numberOfLeadingZeros(value)
    }
}
