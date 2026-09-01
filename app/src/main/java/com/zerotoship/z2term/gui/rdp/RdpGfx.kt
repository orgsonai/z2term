package com.zerotoship.z2term.gui.rdp

import android.graphics.Rect
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * [MS-RDPEGFX] の最小 Graphics Pipeline client。
 *
 * RDP 8 thin-client capability だけを広告し、surface lifecycle、frame acknowledgement、
 * solid fill、surface copy、surface cache、32bpp uncompressed update を受ける。
 * ClearCodec は別 decoder へ委譲する。
 *
 * ⛔ **未対応の command / codec で例外を投げない。** RDPGFX の PDU は長さで区切られているので
 * 読み飛ばせる。投げると受信ループごと落ちて**描ける部分まで消える**。何が来たかは 1 度だけ
 * ログに残し、次にどの decoder を書くかを推測ではなく実測で決める。
 */
internal class RdpGfx(
    private val send: (ByteArray) -> Unit,
    private val onFrame: (width: Int, height: Int, pixels: IntArray, dirty: Rect) -> Unit,
) {
    private data class Surface(
        val width: Int,
        val height: Int,
        val format: Int,
        val pixels: IntArray = IntArray(width * height),
    )

    private data class Mapping(
        val x: Int,
        val y: Int,
        val targetWidth: Int?,
        val targetHeight: Int?,
    )

    private val surfaces = mutableMapOf<Int, Surface>()
    private val mappings = mutableMapOf<Int, Mapping>()
    private var outputWidth = 0
    private var outputHeight = 0
    private var output = IntArray(0)
    private var totalFrames = 0
    private var currentFrameId: Int? = null
    private val bulk = Rdp8Bulk()
    private val clear = RdpClearCodec()
    private val cache = mutableMapOf<Int, Surface>()
    private var cachedPixels = 0
    /** どの codec / command が実際に来たかを 1 度だけ残す。decoder を足す順番はこれで決める。 */
    private val reportedCodecs = mutableSetOf<Int>()
    private val reportedCommands = mutableSetOf<Int>()
    private val codecCounts = mutableMapOf<Int, Int>()

    fun capabilitiesAdvertise(): ByteArray = Writer().apply {
        header(CMD_CAPS_ADVERTISE, HEADER_SIZE + 2 + 12)
        le16(1)
        le32(CAPVERSION_8)
        le32(4)
        // THINCLIENT implies a small cache and keeps the peer on the RDP 8 codec path.
        le32(CAPS_FLAG_THINCLIENT)
    }.array()

    @Synchronized
    fun accept(message: ByteArray) {
        val decoded = bulk.decodeSegmented(message)
        val cursor = Cursor(decoded)
        while (cursor.remaining > 0) {
            if (cursor.remaining < HEADER_SIZE) throw IOException("truncated RDPGFX header")
            val command = cursor.le16()
            val flags = cursor.le16()
            val length = cursor.le32()
            if (flags != 0) Log.d(TAG, "RDPGFX: command=0x${command.toString(16)} flags=0x${flags.toString(16)}")
            if (length < HEADER_SIZE || length - HEADER_SIZE > cursor.remaining) {
                throw IOException("invalid RDPGFX PDU length: $length")
            }
            val body = cursor.sub(length - HEADER_SIZE)
            when (command) {
                CMD_CAPS_CONFIRM -> capsConfirm(body)
                CMD_RESET_GRAPHICS -> resetGraphics(body)
                CMD_CREATE_SURFACE -> createSurface(body)
                CMD_DELETE_SURFACE -> deleteSurface(body)
                CMD_MAP_SURFACE_TO_OUTPUT -> mapSurface(body, scaled = false)
                CMD_MAP_SURFACE_TO_SCALED_OUTPUT -> mapSurface(body, scaled = true)
                CMD_START_FRAME -> startFrame(body)
                CMD_END_FRAME -> endFrame(body)
                CMD_WIRE_TO_SURFACE_1 -> wireToSurface1(body)
                CMD_SOLID_FILL -> solidFill(body)
                CMD_SURFACE_TO_SURFACE -> surfaceToSurface(body)
                CMD_SURFACE_TO_CACHE -> surfaceToCache(body)
                CMD_CACHE_TO_SURFACE -> cacheToSurface(body)
                CMD_EVICT_CACHE_ENTRY -> evictCacheEntry(body)
                CMD_WIRE_TO_SURFACE_2 -> wireToSurface2(body)
                CMD_DELETE_ENCODING_CONTEXT -> body.skipRemaining()
                else -> {
                    // ⛔ **未対応の command で例外を投げない。** PDU は長さで区切られているので読み飛ばせる。
                    // 投げると最初の 1 つで受信ループごと落ち、描ける部分まで消える。何が来たかは
                    // 1 度だけ残し、decoder を足す順番を実測で決める材料にする。
                    if (reportedCommands.add(command)) {
                        Log.w(TAG, "RDPGFX: command 0x${command.toString(16)} is not implemented; skipped")
                    }
                    body.skipRemaining()
                }
            }
            if (body.remaining != 0) {
                // 解析漏れ。⚠ ここで投げると画面が全部止まるので、command を残して次へ進む。
                if (reportedCommands.add(TRAILING_MARK or command)) {
                    Log.w(TAG, "RDPGFX: command 0x${command.toString(16)} left ${body.remaining} trailing bytes")
                }
            }
        }
    }

    @Synchronized
    fun reset() {
        surfaces.clear()
        mappings.clear()
        outputWidth = 0
        outputHeight = 0
        output = IntArray(0)
        totalFrames = 0
        currentFrameId = null
        bulk.reset()
        clear.reset()
        cache.clear()
        cachedPixels = 0
        reportedCodecs.clear()
        reportedCommands.clear()
        codecCounts.clear()
    }

    private fun capsConfirm(body: Cursor) {
        val version = body.le32()
        val dataLength = body.le32()
        if (dataLength < 4 || dataLength > body.remaining) throw IOException("invalid RDPGFX caps length")
        val flags = body.le32()
        if (dataLength > 4) body.skip(dataLength - 4)
        Log.i(TAG, "RDPGFX: caps confirmed version=0x${version.toString(16)} flags=0x${flags.toString(16)}")
    }

    private fun resetGraphics(body: Cursor) {
        val width = body.le32()
        val height = body.le32()
        val monitors = body.le32()
        if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION || monitors !in 1..16) {
            throw IOException("invalid RDPGFX reset: ${width}x$height monitors=$monitors")
        }
        repeat(monitors) { repeat(5) { body.le32() } }
        // RESET_GRAPHICS is padded so that the whole PDU is exactly 340 bytes.
        body.skipRemaining()
        outputWidth = width
        outputHeight = height
        output = IntArray(checkedPixelCount(width, height))
        mappings.clear()
        clear.reset()
        Log.i(TAG, "RDPGFX: reset ${width}x$height")
    }

    private fun createSurface(body: Cursor) {
        val id = body.le16()
        val width = body.le16()
        val height = body.le16()
        val format = body.u8()
        if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION || format !in SUPPORTED_PIXEL_FORMATS) {
            throw IOException("invalid RDPGFX surface $id: ${width}x$height format=0x${format.toString(16)}")
        }
        // ⚠ 0 のままだと alpha 0 = 透明。まだ描かれていない領域が抜けて見えるので不透明な黒で始める。
        surfaces[id] = Surface(width, height, format,
            IntArray(checkedPixelCount(width, height)) { OPAQUE_BLACK })
        mappings.remove(id)
        clear.deleteSurface(id)
        Log.i(TAG, "RDPGFX: surface $id ${width}x$height format=0x${format.toString(16)}")
    }

    private fun deleteSurface(body: Cursor) {
        val id = body.le16()
        surfaces.remove(id)
        mappings.remove(id)
        clear.deleteSurface(id)
    }

    private fun mapSurface(body: Cursor, scaled: Boolean) {
        val id = body.le16()
        body.le16() // reserved
        val x = body.le32()
        val y = body.le32()
        val targetWidth = if (scaled) body.le32() else null
        val targetHeight = if (scaled) body.le32() else null
        mappings[id] = Mapping(x, y, targetWidth, targetHeight)
        Log.i(TAG, "RDPGFX: surface $id mapped to ($x,$y)" +
            if (targetWidth != null) " scaled ${targetWidth}x$targetHeight" else "")
    }

    private fun startFrame(body: Cursor) {
        body.le32() // timestamp
        currentFrameId = body.le32()
    }

    private fun endFrame(body: Cursor) {
        val frameId = body.le32()
        composeOutput()
        totalFrames++
        if (totalFrames == 1) Log.i(TAG, "RDPGFX: first frame composed")
        send(frameAcknowledge(frameId, totalFrames))
        currentFrameId = null
        // 相手がどの codec を選んだかを実測する。⚠ 毎 frame は出さない (更新のたびに出るため)。
        if (totalFrames % CODEC_REPORT_FRAMES == 0) {
            Log.i(TAG, "RDPGFX: $totalFrames frames, codecs=" +
                codecCounts.entries.sortedBy { it.key }
                    .joinToString(" ") { "0x${it.key.toString(16)}=${it.value}" })
        }
    }

    private fun wireToSurface1(body: Cursor) {
        val surfaceId = body.le16()
        val codecId = body.le16()
        val pixelFormat = body.u8()
        val left = body.le16()
        val top = body.le16()
        val right = body.le16()
        val bottom = body.le16()
        val length = body.le32()
        val encoded = body.bytes(length)
        val surface = surfaces[surfaceId] ?: throw IOException("RDPGFX update for unknown surface $surfaceId")
        if (pixelFormat !in SUPPORTED_PIXEL_FORMATS) throw IOException("unsupported RDPGFX pixel format: $pixelFormat")
        if (left >= right || top >= bottom || right > surface.width || bottom > surface.height) {
            throw IOException("invalid RDPGFX destination rectangle")
        }
        val width = right - left
        val height = bottom - top
        countCodec(codecId)
        when (codecId) {
            CODEC_UNCOMPRESSED -> decodeUncompressed(encoded, surface, left, top, width, height, pixelFormat)
            CODEC_CLEAR -> clear.decode(surfaceId, encoded, surface.pixels, surface.width, surface.height, left, top, width, height)
            else -> Unit // countCodec が 1 度だけ記録している。未実装の矩形はそのまま残す。
        }
    }

    /**
     * Progressive (と将来の codec) の surface 更新。
     *
     * まだ decoder が無いので中身は捨てるが、⭐ **どの codec がどれだけ来るかは数える** —
     * 次にどの decoder を書くべきかを推測ではなく実測で決めるため。
     */
    private fun wireToSurface2(body: Cursor) {
        body.le16() // surfaceId
        val codecId = body.le16()
        body.le32() // codecContextId
        body.u8() // pixelFormat
        countCodec(codecId)
        body.skipRemaining()
    }

    private fun countCodec(codecId: Int) {
        codecCounts[codecId] = (codecCounts[codecId] ?: 0) + 1
        if (reportedCodecs.add(codecId)) {
            val known = codecId == CODEC_UNCOMPRESSED || codecId == CODEC_CLEAR
            Log.i(TAG, "RDPGFX: codec 0x${codecId.toString(16)}" + if (known) "" else " is not implemented; skipped")
        }
    }

    /** surface の矩形を cache slot へ取り置く ([MS-RDPEGFX] 2.2.2.6)。 */
    private fun surfaceToCache(body: Cursor) {
        val surfaceId = body.le16()
        body.le32() // cacheKey は再接続時の persistent cache 用。毎回作り直すので使わない。
        body.le32()
        val slot = body.le16()
        val left = body.le16()
        val top = body.le16()
        val right = body.le16()
        val bottom = body.le16()
        val surface = surfaces[surfaceId] ?: throw IOException("RDPGFX cache from unknown surface $surfaceId")
        if (left >= right || top >= bottom || right > surface.width || bottom > surface.height) {
            throw IOException("invalid RDPGFX cache rectangle")
        }
        val width = right - left
        val height = bottom - top
        val entry = Surface(width, height, surface.format, IntArray(checkedPixelCount(width, height)))
        repeat(height) { row ->
            surface.pixels.copyInto(entry.pixels, row * width,
                (top + row) * surface.width + left, (top + row) * surface.width + right)
        }
        cache.remove(slot)?.let { cachedPixels -= it.pixels.size }
        // THINCLIENT の cache は小さい。⚠ 上限を持たないと相手任せで際限なく増える。
        while (cachedPixels + entry.pixels.size > MAX_CACHE_PIXELS && cache.isNotEmpty()) {
            val oldest = cache.keys.first()
            cache.remove(oldest)?.let { cachedPixels -= it.pixels.size }
        }
        cache[slot] = entry
        cachedPixels += entry.pixels.size
    }

    /** cache slot を surface の複数箇所へ貼る ([MS-RDPEGFX] 2.2.2.7)。 */
    private fun cacheToSurface(body: Cursor) {
        val slot = body.le16()
        val surfaceId = body.le16()
        val count = body.le16()
        val surface = surfaces[surfaceId] ?: throw IOException("RDPGFX cache blit to unknown surface $surfaceId")
        val entry = cache[slot]
        repeat(count) {
            val x = body.le16()
            val y = body.le16()
            if (entry == null) return@repeat
            if (x + entry.width > surface.width || y + entry.height > surface.height) {
                throw IOException("RDPGFX cache blit exceeds surface")
            }
            repeat(entry.height) { row ->
                entry.pixels.copyInto(surface.pixels, (y + row) * surface.width + x,
                    row * entry.width, (row + 1) * entry.width)
            }
        }
        if (entry == null && reportedCommands.add(MISSING_CACHE_MARK)) {
            Log.w(TAG, "RDPGFX: cache slot $slot is empty; blit skipped")
        }
    }

    private fun evictCacheEntry(body: Cursor) {
        val slot = body.le16()
        cache.remove(slot)?.let { cachedPixels -= it.pixels.size }
    }

    private fun decodeUncompressed(
        encoded: ByteArray,
        surface: Surface,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        pixelFormat: Int,
    ) {
        val expected = checkedPixelCount(width, height) * 4
        if (encoded.size != expected) throw IOException("invalid uncompressed RDPGFX bitmap length")
        var source = 0
        for (y in 0 until height) {
            var destination = (top + y) * surface.width + left
            repeat(width) {
                val b = encoded[source++].toInt() and 0xFF
                val g = encoded[source++].toInt() and 0xFF
                val r = encoded[source++].toInt() and 0xFF
                val a = encoded[source++].toInt() and 0xFF
                surface.pixels[destination++] =
                    ((if (pixelFormat == PIXEL_FORMAT_ARGB) a else 0xFF) shl 24) or
                        (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun solidFill(body: Cursor) {
        val id = body.le16()
        val b = body.u8()
        val g = body.u8()
        val r = body.u8()
        val a = body.u8()
        val count = body.le16()
        val surface = surfaces[id] ?: throw IOException("RDPGFX fill for unknown surface $id")
        val color = ((if (surface.format == PIXEL_FORMAT_ARGB) a else 0xFF) shl 24) or
            (r shl 16) or (g shl 8) or b
        repeat(count) {
            val left = body.le16()
            val top = body.le16()
            val right = body.le16()
            val bottom = body.le16()
            fill(surface, left, top, right, bottom, color)
        }
    }

    private fun surfaceToSurface(body: Cursor) {
        val source = surfaces[body.le16()] ?: throw IOException("unknown RDPGFX source surface")
        val destination = surfaces[body.le16()] ?: throw IOException("unknown RDPGFX destination surface")
        val left = body.le16()
        val top = body.le16()
        val right = body.le16()
        val bottom = body.le16()
        val count = body.le16()
        if (left >= right || top >= bottom || right > source.width || bottom > source.height) {
            throw IOException("invalid RDPGFX surface copy rectangle")
        }
        val copyWidth = right - left
        val copyHeight = bottom - top
        val snapshot = IntArray(copyWidth * copyHeight)
        repeat(copyHeight) { y ->
            source.pixels.copyInto(snapshot, y * copyWidth, (top + y) * source.width + left,
                (top + y) * source.width + right)
        }
        repeat(count) {
            val x = body.le16()
            val y = body.le16()
            if (x + copyWidth > destination.width || y + copyHeight > destination.height) {
                throw IOException("RDPGFX surface copy exceeds destination")
            }
            repeat(copyHeight) { row ->
                snapshot.copyInto(destination.pixels, (y + row) * destination.width + x,
                    row * copyWidth, (row + 1) * copyWidth)
            }
        }
    }

    private fun composeOutput() {
        if (outputWidth <= 0 || outputHeight <= 0 || output.isEmpty()) return
        output.fill(OPAQUE_BLACK)
        for ((id, mapping) in mappings) {
            val surface = surfaces[id] ?: continue
            val targetWidth = mapping.targetWidth ?: surface.width
            val targetHeight = mapping.targetHeight ?: surface.height
            if (targetWidth <= 0 || targetHeight <= 0) continue
            for (dy in 0 until targetHeight) {
                val oy = mapping.y + dy
                if (oy !in 0 until outputHeight) continue
                val sy = (dy.toLong() * surface.height / targetHeight).toInt().coerceAtMost(surface.height - 1)
                for (dx in 0 until targetWidth) {
                    val ox = mapping.x + dx
                    if (ox !in 0 until outputWidth) continue
                    val sx = (dx.toLong() * surface.width / targetWidth).toInt().coerceAtMost(surface.width - 1)
                    output[oy * outputWidth + ox] = surface.pixels[sy * surface.width + sx]
                }
            }
        }
        onFrame(outputWidth, outputHeight, output, Rect(0, 0, outputWidth, outputHeight))
    }

    private fun fill(surface: Surface, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        if (left >= right || top >= bottom || left < 0 || top < 0 || right > surface.width || bottom > surface.height) {
            throw IOException("invalid RDPGFX fill rectangle")
        }
        for (y in top until bottom) java.util.Arrays.fill(surface.pixels, y * surface.width + left,
            y * surface.width + right, color)
    }

    private fun frameAcknowledge(frameId: Int, frames: Int): ByteArray = Writer().apply {
        header(CMD_FRAME_ACKNOWLEDGE, 20)
        le32(0) // QUEUE_DEPTH_UNAVAILABLE
        le32(frameId)
        le32(frames)
    }.array()

    private class Cursor(private val data: ByteArray) {
        private var offset = 0
        val remaining: Int get() = data.size - offset
        fun u8(): Int {
            if (remaining < 1) throw IOException("truncated RDPGFX PDU")
            return data[offset++].toInt() and 0xFF
        }
        fun le16(): Int = u8() or (u8() shl 8)
        fun le32(): Int = u8() or (u8() shl 8) or (u8() shl 16) or (u8() shl 24)
        fun bytes(length: Int): ByteArray {
            if (length < 0 || remaining < length) throw IOException("truncated RDPGFX PDU")
            return data.copyOfRange(offset, offset + length).also { offset += length }
        }
        fun sub(length: Int): Cursor = Cursor(bytes(length))
        fun skip(length: Int) { bytes(length) }
        fun skipRemaining() { offset = data.size }
        fun requireEnd() { if (remaining != 0) throw IOException("trailing RDPGFX data: $remaining bytes") }
    }

    private class Writer {
        private val out = ByteArrayOutputStream()
        fun u8(value: Int) = out.write(value and 0xFF)
        fun le16(value: Int) { u8(value); u8(value ushr 8) }
        fun le32(value: Int) { repeat(4) { u8(value ushr (it * 8)) } }
        fun header(command: Int, length: Int) { le16(command); le16(0); le32(length) }
        fun array(): ByteArray = out.toByteArray()
    }

    companion object {
        private const val TAG = "RdpGfx"
        private const val HEADER_SIZE = 8
        private const val CMD_WIRE_TO_SURFACE_1 = 0x0001
        private const val CMD_WIRE_TO_SURFACE_2 = 0x0002
        private const val CMD_DELETE_ENCODING_CONTEXT = 0x0003
        private const val CMD_SOLID_FILL = 0x0004
        private const val CMD_SURFACE_TO_SURFACE = 0x0005
        private const val CMD_SURFACE_TO_CACHE = 0x0006
        private const val CMD_CACHE_TO_SURFACE = 0x0007
        private const val CMD_EVICT_CACHE_ENTRY = 0x0008
        private const val CMD_CREATE_SURFACE = 0x0009
        private const val CMD_DELETE_SURFACE = 0x000A
        private const val CMD_START_FRAME = 0x000B
        private const val CMD_END_FRAME = 0x000C
        private const val CMD_FRAME_ACKNOWLEDGE = 0x000D
        private const val CMD_RESET_GRAPHICS = 0x000E
        private const val CMD_MAP_SURFACE_TO_OUTPUT = 0x000F
        private const val CMD_CAPS_ADVERTISE = 0x0012
        private const val CMD_CAPS_CONFIRM = 0x0013
        private const val CMD_MAP_SURFACE_TO_SCALED_OUTPUT = 0x0017
        private const val CAPVERSION_8 = 0x00080004
        private const val CAPS_FLAG_THINCLIENT = 0x00000001
        private const val CODEC_UNCOMPRESSED = 0x0000
        private const val CODEC_CLEAR = 0x0008
        private const val PIXEL_FORMAT_XRGB = 0x20
        private const val PIXEL_FORMAT_ARGB = 0x21
        private val SUPPORTED_PIXEL_FORMATS = setOf(PIXEL_FORMAT_XRGB, PIXEL_FORMAT_ARGB)
        private const val OPAQUE_BLACK = 0xFF000000.toInt()
        private const val MAX_DIMENSION = 8192
        private const val CODEC_REPORT_FRAMES = 30
        private const val MAX_CACHE_PIXELS = 4 * 1024 * 1024
        /** reportedCommands を「未実装」「余剰バイト」で使い分けるための印。command id とぶつからない。 */
        private const val TRAILING_MARK = 0x10000
        private const val MISSING_CACHE_MARK = 0x20000
        private const val MAX_PIXELS = 16 * 1024 * 1024

        private fun checkedPixelCount(width: Int, height: Int): Int {
            val count = width.toLong() * height.toLong()
            if (count !in 1..MAX_PIXELS.toLong()) throw IOException("RDPGFX surface is too large: ${width}x$height")
            return count.toInt()
        }
    }
}
