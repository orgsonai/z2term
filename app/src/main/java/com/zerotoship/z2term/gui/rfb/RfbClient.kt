package com.zerotoship.z2term.gui.rfb

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * RFB (VNC) 3.8 クライアント。M8-2 段階は **表示のみ**（None 認証 + Raw/CopyRect デコード）。
 *
 * 127.0.0.1:5901 の Xvnc (z2gui が起動) に接続し、フレームバッファを ARGB_8888 [Bitmap] に描く。
 * 受信ループ [run] は呼び出し側 (GuiSession) が IO コルーチンで回す。再描画は [redraw] StateFlow
 * で Compose に伝える（端末の redrawTick と同じ方式）。
 *
 * - PixelFormat は 32bpp little-endian truecolor を要求し、各ピクセルを 0xAARRGGBB へ直変換。
 * - 入力 (ポインタ/キー) は M8-3。ここでは送らない。
 * - ZRLE/Tight は M8-4。ここでは Raw + CopyRect のみ（loopback なので帯域は問題にならない）。
 */
class RfbClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 5901,
) {
    /** ServerInit で受け取る画面サイズ。connect 後に有効。 */
    var width = 0
        private set
    var height = 0
        private set
    var desktopName = ""
        private set

    /** 描画対象 Bitmap。connect 成功後に確保。setPixels と描画は [frameLock] で直列化する。 */
    @Volatile
    var frame: Bitmap? = null
        private set
    val frameLock = Any()

    private val _redraw = MutableStateFlow(0)
    /** フレーム更新の度にインクリメント。Compose 側はこれを collect して再描画する。 */
    val redraw: StateFlow<Int> = _redraw.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    @Volatile
    private var closed = false

    /** width*height の ARGB バッファ。rect デコードはここに書き、まとめて Bitmap へ流す。 */
    private var pixels: IntArray = IntArray(0)

    /**
     * 同期接続 + RFB 3.8 ハンドシェイク。**IO スレッドで呼ぶこと**。失敗時は例外を投げる。
     */
    fun connect(timeoutMs: Int = 8000) {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), timeoutMs)
        socket = s
        val inp = DataInputStream(BufferedInputStream(s.getInputStream(), 1 shl 16))
        val out = DataOutputStream(BufferedOutputStream(s.getOutputStream(), 1 shl 16))
        input = inp
        output = out

        handshake(inp, out)

        pixels = IntArray(width * height)
        frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        _connected.value = true
        Log.i(TAG, "RFB connected: ${width}x$height '$desktopName'")

        // 最初は全画面 (non-incremental) を要求
        sendFramebufferUpdateRequest(out, incremental = false)
    }

    private fun handshake(inp: DataInputStream, out: DataOutputStream) {
        // 1. ProtocolVersion (サーバ→) を読み、3.8 を要求 (→サーバ)
        val server = ByteArray(12).also { inp.readFully(it) }
        Log.d(TAG, "server ProtocolVersion=${String(server, Charsets.US_ASCII).trim()}")
        out.write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
        out.flush()

        // 2. Security types (3.7+: U8 個数 + リスト)
        val count = inp.readUnsignedByte()
        if (count == 0) throw IOException("VNC security 失敗: ${readReason(inp)}")
        val types = ByteArray(count).also { inp.readFully(it) }
        if (types.none { it.toInt() == SEC_NONE }) {
            throw IOException("VNC: None(1) 認証が無い (types=${types.joinToString { it.toInt().toString() }})")
        }
        out.writeByte(SEC_NONE)
        out.flush()

        // 3. SecurityResult (3.8 は None でも必ず来る)
        val secResult = inp.readInt()
        if (secResult != 0) throw IOException("VNC SecurityResult=$secResult: ${readReason(inp)}")

        // 4. ClientInit (shared=1: 他クライアントを切らない)
        out.writeByte(1)
        out.flush()

        // 5. ServerInit: 幅/高さ/PixelFormat(16)/名前
        width = inp.readUnsignedShort()
        height = inp.readUnsignedShort()
        inp.readFully(ByteArray(16)) // server pixel format は SetPixelFormat で上書きするので捨てる
        val nameLen = inp.readInt()
        desktopName = ByteArray(nameLen.coerceAtLeast(0)).also { inp.readFully(it) }.toString(Charsets.UTF_8)

        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            throw IOException("不正な画面サイズ: ${width}x$height")
        }

        // 6. SetPixelFormat: 32bpp little-endian truecolor (ARGB_8888 互換)
        setPixelFormat(out)
        // 7. SetEncodings: Raw + CopyRect
        setEncodings(out, intArrayOf(ENC_RAW, ENC_COPYRECT))
    }

    private fun readReason(inp: DataInputStream): String {
        val len = inp.readInt().coerceIn(0, 4096)
        return ByteArray(len).also { inp.readFully(it) }.toString(Charsets.UTF_8)
    }

    private fun setPixelFormat(out: DataOutputStream) {
        out.writeByte(MSG_SET_PIXEL_FORMAT)
        out.write(byteArrayOf(0, 0, 0)) // padding
        // PIXEL_FORMAT (16 bytes)
        out.writeByte(32)   // bits-per-pixel
        out.writeByte(24)   // depth
        out.writeByte(0)    // big-endian-flag = 0 (little)
        out.writeByte(1)    // true-color-flag = 1
        out.writeShort(255) // red-max
        out.writeShort(255) // green-max
        out.writeShort(255) // blue-max
        out.writeByte(16)   // red-shift
        out.writeByte(8)    // green-shift
        out.writeByte(0)    // blue-shift
        out.write(byteArrayOf(0, 0, 0)) // padding
        out.flush()
    }

    private fun setEncodings(out: DataOutputStream, encs: IntArray) {
        out.writeByte(MSG_SET_ENCODINGS)
        out.writeByte(0) // padding
        out.writeShort(encs.size)
        for (e in encs) out.writeInt(e)
        out.flush()
    }

    private fun sendFramebufferUpdateRequest(out: DataOutputStream, incremental: Boolean) {
        out.writeByte(MSG_FB_UPDATE_REQUEST)
        out.writeByte(if (incremental) 1 else 0)
        out.writeShort(0)      // x
        out.writeShort(0)      // y
        out.writeShort(width)
        out.writeShort(height)
        out.flush()
    }

    /**
     * サーバ→クライアントのメッセージを処理し続ける。**IO スレッドで呼ぶ**。
     * [close] されるか接続が切れると返る。
     */
    fun run() {
        val inp = input ?: return
        val out = output ?: return
        try {
            while (!closed) {
                val type = inp.read()
                if (type < 0) break // EOF
                when (type) {
                    SRV_FB_UPDATE -> handleFramebufferUpdate(inp, out)
                    SRV_SET_COLOUR_MAP -> handleSetColourMap(inp)
                    SRV_BELL -> { /* 本体なし */ }
                    SRV_CUT_TEXT -> handleServerCutText(inp)
                    else -> throw IOException("未知のサーバメッセージ type=$type")
                }
            }
        } catch (e: Exception) {
            if (!closed) Log.w(TAG, "RFB 受信ループ終了", e)
        } finally {
            _connected.value = false
        }
    }

    private fun handleFramebufferUpdate(inp: DataInputStream, out: DataOutputStream) {
        inp.readByte() // padding
        val numRects = inp.readUnsignedShort()
        for (r in 0 until numRects) {
            val x = inp.readUnsignedShort()
            val y = inp.readUnsignedShort()
            val w = inp.readUnsignedShort()
            val h = inp.readUnsignedShort()
            when (val enc = inp.readInt()) {
                ENC_RAW -> readRaw(inp, x, y, w, h)
                ENC_COPYRECT -> readCopyRect(inp, x, y, w, h)
                else -> throw IOException("未対応エンコーディング=$enc (rect $x,$y ${w}x$h)")
            }
        }
        pushFrame()
        // 次の更新を要求 (incremental)
        sendFramebufferUpdateRequest(out, incremental = true)
    }

    private fun readRaw(inp: DataInputStream, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val rowBytes = ByteArray(w * 4)
        for (row in 0 until h) {
            inp.readFully(rowBytes)
            val py = y + row
            if (py < 0 || py >= height) continue
            val base = py * width
            var bi = 0
            for (col in 0 until w) {
                val px = x + col
                if (px in 0 until width) {
                    val b = rowBytes[bi].toInt() and 0xFF       // little-endian: byte0 = blue (shift 0)
                    val g = rowBytes[bi + 1].toInt() and 0xFF   // byte1 = green (shift 8)
                    val rr = rowBytes[bi + 2].toInt() and 0xFF  // byte2 = red (shift 16)
                    // byte3 = 未使用 (X)
                    pixels[base + px] = ALPHA or (rr shl 16) or (g shl 8) or b
                }
                bi += 4
            }
        }
    }

    private fun readCopyRect(inp: DataInputStream, x: Int, y: Int, w: Int, h: Int) {
        val srcX = inp.readUnsignedShort()
        val srcY = inp.readUnsignedShort()
        if (w <= 0 || h <= 0) return
        // 上下の重なりに備えて、コピー方向を選ぶ (System.arraycopy は同一行内の左右重なりは memmove 安全)。
        if (srcY < y) {
            for (row in h - 1 downTo 0) copyRow(srcX, srcY + row, x, y + row, w)
        } else {
            for (row in 0 until h) copyRow(srcX, srcY + row, x, y + row, w)
        }
    }

    private fun copyRow(sx: Int, sy: Int, dx: Int, dy: Int, w: Int) {
        if (sy !in 0 until height || dy !in 0 until height) return
        if (sx < 0 || dx < 0 || sx + w > width || dx + w > width) return
        System.arraycopy(pixels, sy * width + sx, pixels, dy * width + dx, w)
    }

    private fun pushFrame() {
        val bmp = frame ?: return
        synchronized(frameLock) {
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        _redraw.value = _redraw.value + 1
    }

    private fun handleSetColourMap(inp: DataInputStream) {
        inp.readByte() // padding
        inp.readUnsignedShort() // first-colour
        val n = inp.readUnsignedShort()
        skipFully(inp, n.toLong() * 6)
    }

    private fun handleServerCutText(inp: DataInputStream) {
        inp.readByte(); inp.readByte(); inp.readByte() // padding
        val len = inp.readInt().toLong()
        skipFully(inp, len)
    }

    private fun skipFully(inp: DataInputStream, n: Long) {
        var remaining = n
        while (remaining > 0) {
            val skipped = inp.skip(remaining)
            if (skipped <= 0) {
                if (inp.read() < 0) break // EOF
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    fun close() {
        closed = true
        _connected.value = false
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
    }

    companion object {
        private const val TAG = "RfbClient"
        private const val ALPHA = 0xFF shl 24

        private const val SEC_NONE = 1

        // client→server message types
        private const val MSG_SET_PIXEL_FORMAT = 0
        private const val MSG_SET_ENCODINGS = 2
        private const val MSG_FB_UPDATE_REQUEST = 3

        // server→client message types
        private const val SRV_FB_UPDATE = 0
        private const val SRV_SET_COLOUR_MAP = 1
        private const val SRV_BELL = 2
        private const val SRV_CUT_TEXT = 3

        // encodings
        private const val ENC_RAW = 0
        private const val ENC_COPYRECT = 1
    }
}
