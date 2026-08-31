package com.zerotoship.z2term.gui.rdp

import java.io.IOException

/** Classic slow-path Bitmap Update の矩形を ARGB framebuffer へ展開する純 Kotlin 実装。 */
internal object RdpBitmap {
    private const val BITMAP_COMPRESSION = 0x0001
    private const val NO_BITMAP_COMPRESSION_HDR = 0x0400
    private const val MAX_RECTANGLES = 4096
    private const val MAX_BITMAP_PIXELS = 16 * 1024 * 1024

    data class DirtyRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top

        fun union(other: DirtyRect) = DirtyRect(
            minOf(left, other.left),
            minOf(top, other.top),
            maxOf(right, other.right),
            maxOf(bottom, other.bottom),
        )
    }

    private data class Patch(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val width: Int,
        val height: Int,
        val pixels: IntArray,
    )

    /** TS_UPDATE_BITMAP_DATA を完全に検証してから framebuffer へまとめて反映する。 */
    fun applyUpdate(
        data: ByteArray,
        framebuffer: IntArray,
        screenWidth: Int,
        screenHeight: Int,
    ): DirtyRect? {
        requireFramebuffer(framebuffer, screenWidth, screenHeight)
        val c = Cursor(data)
        if (c.le16() != 1) throw IOException("RDP update is not a Bitmap Update")
        val count = c.le16()
        if (count > MAX_RECTANGLES) throw IOException("too many RDP bitmap rectangles: $count")
        val patches = ArrayList<Patch>(count)
        var remainingPixelBudget = MAX_BITMAP_PIXELS
        repeat(count) {
            val patch = readPatch(c, remainingPixelBudget)
            remainingPixelBudget -= patch.pixels.size
            patches += patch
        }
        c.end()

        var dirty: DirtyRect? = null
        for (patch in patches) {
            val x0 = patch.left.coerceIn(0, screenWidth)
            val y0 = patch.top.coerceIn(0, screenHeight)
            val x1 = (patch.right + 1).coerceIn(0, screenWidth)
            val y1 = (patch.bottom + 1).coerceIn(0, screenHeight)
            if (x1 <= x0 || y1 <= y0) continue
            for (y in y0 until y1) {
                val src = (y - patch.top) * patch.width + (x0 - patch.left)
                val dst = y * screenWidth + x0
                patch.pixels.copyInto(framebuffer, dst, src, src + (x1 - x0))
            }
            val rect = DirtyRect(x0, y0, x1, y1)
            dirty = dirty?.union(rect) ?: rect
        }
        return dirty
    }

    /** テストと wire decoder で共有する Interleaved RLE 展開。返値は通常の上→下順。 */
    internal fun decodeInterleaved(
        data: ByteArray,
        width: Int,
        height: Int,
        bitsPerPixel: Int,
    ): IntArray {
        val pixelCount = checkedPixelCount(width, height)
        val bytesPerPixel = bytesPerPixel(bitsPerPixel)
        val raw = IntArray(pixelCount)
        val c = Cursor(data)
        var out = 0
        var foreground = white(bitsPerPixel)
        var insertForeground = false

        while (c.remaining > 0) {
            val firstLine = out < width
            if (!firstLine && out == width) insertForeground = false
            val header = c.u8()
            val code = when {
                header >= 0xF0 -> header
                header >= 0xC0 -> header ushr 4
                else -> header ushr 5
            }
            val run = runLength(c, header, code)

            fun previous(position: Int): Int {
                if (position < width) return 0
                return raw[position - width]
            }

            fun write(value: Int) {
                if (out >= pixelCount) throw IOException("RDP RLE run exceeds bitmap bounds")
                raw[out++] = value
            }

            when (code) {
                0x00, 0xF0 -> {
                    var remaining = run
                    if (insertForeground) {
                        if (remaining <= 0) throw IOException("invalid RDP RLE background run")
                        write(if (firstLine) foreground else previous(out) xor foreground)
                        remaining--
                    }
                    repeat(remaining) { write(if (firstLine) 0 else previous(out)) }
                    insertForeground = true
                }

                0x01, 0xF1, 0x0C, 0xF6 -> {
                    insertForeground = false
                    if (code == 0x0C || code == 0xF6) foreground = c.pixel(bytesPerPixel)
                    repeat(run) { write(if (firstLine) foreground else previous(out) xor foreground) }
                }

                0x0E, 0xF8 -> {
                    insertForeground = false
                    val a = c.pixel(bytesPerPixel)
                    val b = c.pixel(bytesPerPixel)
                    if (run > (pixelCount - out) / 2) throw IOException("RDP RLE dither run exceeds bitmap bounds")
                    repeat(run) { write(a); write(b) }
                }

                0x03, 0xF3 -> {
                    insertForeground = false
                    val color = c.pixel(bytesPerPixel)
                    repeat(run) { write(color) }
                }

                0x02, 0xF2, 0x0D, 0xF7 -> {
                    insertForeground = false
                    if (code == 0x0D || code == 0xF7) foreground = c.pixel(bytesPerPixel)
                    var remaining = run
                    while (remaining > 0) {
                        val mask = c.u8()
                        val bits = minOf(8, remaining)
                        repeat(bits) { bit ->
                            val base = if (firstLine) 0 else previous(out)
                            write(if (mask and (1 shl bit) != 0) base xor foreground else base)
                        }
                        remaining -= bits
                    }
                }

                0x04, 0xF4 -> {
                    insertForeground = false
                    repeat(run) { write(c.pixel(bytesPerPixel)) }
                }

                0xF9, 0xFA -> {
                    insertForeground = false
                    val mask = if (code == 0xF9) 0x03 else 0x05
                    repeat(8) { bit ->
                        val base = if (firstLine) 0 else previous(out)
                        write(if (mask and (1 shl bit) != 0) base xor foreground else base)
                    }
                }

                0xFD -> { insertForeground = false; write(white(bitsPerPixel)) }
                0xFE -> { insertForeground = false; write(0) }
                else -> throw IOException("unsupported RDP RLE order: 0x${code.toString(16)}")
            }
        }
        if (out != pixelCount) throw IOException("RDP RLE bitmap is truncated: $out/$pixelCount pixels")

        return toTopDownArgb(raw, width, height, bitsPerPixel)
    }

    private fun readPatch(c: Cursor, remainingPixelBudget: Int): Patch {
        val left = c.le16()
        val top = c.le16()
        val right = c.le16()
        val bottom = c.le16()
        val width = c.le16()
        val height = c.le16()
        val bitsPerPixel = c.le16()
        val flags = c.le16()
        val bitmapLength = c.le16()

        if (right < left || bottom < top) throw IOException("invalid RDP bitmap rectangle coordinates")
        val destWidth = right - left + 1
        val destHeight = bottom - top + 1
        if (destWidth > width || destHeight > height) {
            throw IOException("RDP bitmap source is smaller than its destination rectangle")
        }
        val pixelCount = checkedPixelCount(width, height)
        if (pixelCount > remainingPixelBudget) {
            throw IOException("RDP bitmap update expands beyond the pixel limit")
        }
        val bytesPerPixel = bytesPerPixel(bitsPerPixel)
        if (flags and (BITMAP_COMPRESSION or NO_BITMAP_COMPRESSION_HDR).inv() != 0) {
            throw IOException("unsupported RDP bitmap flags: 0x${flags.toString(16)}")
        }
        if (flags and NO_BITMAP_COMPRESSION_HDR != 0 && flags and BITMAP_COMPRESSION == 0) {
            throw IOException("RDP bitmap compression header flag without compression")
        }

        val pixels = if (flags and BITMAP_COMPRESSION != 0) {
            val stream = if (flags and NO_BITMAP_COMPRESSION_HDR != 0) {
                c.bytes(bitmapLength)
            } else {
                val firstRow = c.le16()
                val mainBody = c.le16()
                c.le16() // cbScanWidth is advisory and differs between older servers.
                val uncompressed = c.le16()
                // MS-RDPBCGR counts the 8-byte header in bitmapLength. FreeRDP's long-standing
                // writer counts only cbCompMainBodySize, so accept both unambiguous conventions.
                if (firstRow != 0 || (bitmapLength != mainBody && bitmapLength != mainBody + 8)) {
                    throw IOException("invalid RDP bitmap compression header")
                }
                val expected = width.toLong() * height.toLong() * bytesPerPixel
                if (uncompressed == 0 || uncompressed.toLong() > expected + height.toLong() * 3L) {
                    throw IOException("invalid RDP bitmap uncompressed size")
                }
                c.bytes(mainBody)
            }
            decodeInterleaved(stream, width, height, bitsPerPixel)
        } else {
            val rowBytes = width.toLong() * bytesPerPixel
            val stride = (rowBytes + 3L) and -4L
            val expected = stride * height.toLong()
            if (expected > Int.MAX_VALUE || bitmapLength.toLong() != expected) {
                throw IOException("invalid uncompressed RDP bitmap length")
            }
            val bitmap = c.sub(bitmapLength)
            val result = IntArray(pixelCount)
            for (sourceRow in 0 until height) {
                val targetRow = height - 1 - sourceRow
                val base = targetRow * width
                repeat(width) { x -> result[base + x] = argb(bitmap.pixel(bytesPerPixel), bitsPerPixel) }
                bitmap.skip((stride - rowBytes).toInt())
            }
            bitmap.end()
            result
        }
        return Patch(left, top, right, bottom, width, height, pixels)
    }

    private fun runLength(c: Cursor, header: Int, code: Int): Int {
        val run = when (code) {
            0xF9, 0xFA, 0xFD, 0xFE -> if (code == 0xF9 || code == 0xFA) 8 else 1
            in 0xF0..0xF8 -> c.le16()
            0x02 -> (header and 0x1F).let { if (it == 0) c.u8() + 1 else it * 8 }
            0x0D -> (header and 0x0F).let { if (it == 0) c.u8() + 1 else it * 8 }
            in 0x00..0x04 -> (header and 0x1F).let { if (it == 0) c.u8() + 32 else it }
            0x0C, 0x0E -> (header and 0x0F).let { if (it == 0) c.u8() + 16 else it }
            else -> 0
        }
        if (run <= 0) throw IOException("invalid RDP RLE run length")
        return run
    }

    private fun toTopDownArgb(raw: IntArray, width: Int, height: Int, bitsPerPixel: Int): IntArray {
        for (row in 0 until height / 2) {
            val bottom = row * width
            val top = (height - 1 - row) * width
            repeat(width) { x ->
                val bottomPixel = argb(raw[bottom + x], bitsPerPixel)
                raw[bottom + x] = argb(raw[top + x], bitsPerPixel)
                raw[top + x] = bottomPixel
            }
        }
        if (height and 1 != 0) {
            val middle = height / 2 * width
            repeat(width) { x -> raw[middle + x] = argb(raw[middle + x], bitsPerPixel) }
        }
        return raw
    }

    private fun argb(pixel: Int, bitsPerPixel: Int): Int = when (bitsPerPixel) {
        15 -> {
            val r5 = pixel ushr 10 and 0x1F
            val g5 = pixel ushr 5 and 0x1F
            val b5 = pixel and 0x1F
            0xFF000000.toInt() or (expand5(r5) shl 16) or (expand5(g5) shl 8) or expand5(b5)
        }
        16 -> {
            val r5 = pixel ushr 11 and 0x1F
            val g6 = pixel ushr 5 and 0x3F
            val b5 = pixel and 0x1F
            0xFF000000.toInt() or (expand5(r5) shl 16) or (expand6(g6) shl 8) or expand5(b5)
        }
        24 -> 0xFF000000.toInt() or pixel
        else -> throw IOException("unsupported RDP bitmap depth: $bitsPerPixel")
    }

    private fun expand5(value: Int) = (value shl 3) or (value ushr 2)
    private fun expand6(value: Int) = (value shl 2) or (value ushr 4)
    private fun white(bitsPerPixel: Int) = when (bitsPerPixel) {
        15 -> 0x7FFF
        16 -> 0xFFFF
        24 -> 0xFFFFFF
        else -> throw IOException("unsupported RDP bitmap depth: $bitsPerPixel")
    }

    private fun bytesPerPixel(bitsPerPixel: Int) = when (bitsPerPixel) {
        15, 16 -> 2
        24 -> 3
        else -> throw IOException("unsupported RDP bitmap depth: $bitsPerPixel")
    }

    private fun checkedPixelCount(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) throw IOException("invalid RDP bitmap dimensions")
        val count = width.toLong() * height.toLong()
        if (count > MAX_BITMAP_PIXELS) throw IOException("RDP bitmap is too large: ${width}x$height")
        return count.toInt()
    }

    private fun requireFramebuffer(framebuffer: IntArray, width: Int, height: Int) {
        val count = checkedPixelCount(width, height)
        if (framebuffer.size != count) throw IOException("RDP framebuffer size does not match dimensions")
    }

    private class Cursor(private val data: ByteArray) {
        private var offset = 0
        val remaining get() = data.size - offset

        fun u8(): Int {
            if (remaining < 1) throw IOException("truncated RDP bitmap data")
            return data[offset++].toInt() and 0xFF
        }

        fun le16() = u8() or (u8() shl 8)

        fun pixel(bytes: Int): Int {
            var value = 0
            repeat(bytes) { value = value or (u8() shl (it * 8)) }
            return value
        }

        fun bytes(length: Int): ByteArray {
            if (length < 0 || remaining < length) throw IOException("truncated RDP bitmap data")
            return data.copyOfRange(offset, offset + length).also { offset += length }
        }

        fun sub(length: Int) = Cursor(bytes(length))
        fun skip(length: Int) { bytes(length) }

        fun end() {
            if (remaining != 0) throw IOException("trailing RDP bitmap data")
        }
    }
}
