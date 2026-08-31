package com.zerotoship.z2term.gui.rfb

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.createBitmap
import com.zerotoship.z2term.gui.RemoteDesktopClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import com.zerotoship.z2term.net.HostAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * RFB (VNC) 3.8 クライアント。表示 (M8-2) + 入力 (M8-3) に対応（None 認証 + Raw/CopyRect デコード）。
 *
 * 既定では 127.0.0.1:5901 の Xvnc (z2gui が起動) に、[host]/[port] を変えれば**リモートの VNC
 * サーバ**に接続し (A1)、フレームバッファを ARGB_8888 [Bitmap] に描く。
 * 受信ループ [run] は呼び出し側 (GuiSession) が IO コルーチンで回す。再描画は [redraw] StateFlow
 * で Compose に伝える（端末の redrawTick と同じ方式）。
 *
 * - 認証は **None (1)** と **VNC 認証 (2)** に対応 ([VncAuth])。VeNCrypt/TLS は未対応。
 *   ProtocolVersion は 3.8 / 3.7 / 3.3 に合わせられる (リモートには古いサーバが居るため)。
 * - PixelFormat は 32bpp little-endian truecolor を要求し、各ピクセルを 0xAARRGGBB へ直変換。
 * - 入力 (M8-3): [sendPointerEvent] (type 5) / [sendKeyEvent] (type 4)。送信は [writeLock] で直列化し、
 *   受信ループ側の `FramebufferUpdateRequest` 送出と混線しないようにする。
 * - エンコーディング: Raw / CopyRect / **ZRLE** (M8-4 で追加。タイル+zlib)。Tight は未対応。
 *   描画は更新矩形の外接範囲だけ `setPixels` する (全画面転送を避ける)。
 */
class RfbClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 5901,
    password: String = "",
) : RemoteDesktopClient {
    /**
     * VNC 認証 (security type 2) のパスワード。空ならパスワードを要求しないサーバ専用
     * (ローカルの Xvnc はこちら)。入れ直して [connect] をやり直せるよう var にしてある。
     */
    @Volatile
    var password: String = password

    /** ServerInit で受け取る画面サイズ。connect 後に有効。ExtendedDesktopSize で動的に変わる。 */
    @Volatile
    override var width = 0
        private set
    @Volatile
    override var height = 0
        private set
    override var desktopName = ""
        private set

    /** 描画対象 Bitmap。connect 成功後に確保。setPixels と描画は [frameLock] で直列化する。 */
    @Volatile
    override var frame: Bitmap? = null
        private set
    override val frameLock = Any()

    private val _redraw = MutableStateFlow(0)
    /** フレーム更新の度にインクリメント。Compose 側はこれを collect して再描画する。 */
    override val redraw: StateFlow<Int> = _redraw.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /**
     * サーバ (xterm 等) が選択/コピーしたテキストを受け取るコールバック (M8-6 T6)。
     * 受信ループ (IO スレッド) から呼ばれる。Android クリップボードへ反映するのは呼び出し側 ([GuiSession])。
     */
    override var onRemoteClipboardText: ((String) -> Unit)? = null

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    /** [output] への書き込みを直列化する。受信ループの FB 要求と入力送信が混ざらないように。 */
    private val writeLock = Any()

    /** 入力送信専用の単一スレッド。main でのソケット書き込み(StrictMode 違反)を避け、順序も保つ。 */
    private val sender = Executors.newSingleThreadExecutor { r ->
        Thread(r, "rfb-sender").apply { isDaemon = true }
    }

    @Volatile
    private var closed = false

    /** width*height の ARGB バッファ。rect デコードはここに書き、まとめて Bitmap へ流す。 */
    private var pixels: IntArray = IntArray(0)

    /** ExtendedDesktopSize で得た 1 番目のスクリーン ID。SetDesktopSize 要求で再利用する。 */
    @Volatile private var screenId = 0
    @Volatile private var hasScreenId = false
    /** 解像度変更直後の 1 回だけ全画面 (non-incremental) を要求するためのフラグ。 */
    @Volatile private var pendingFullRequest = false

    /**
     * 起動直後の **要求 / 受信の噛み合い**を数えるための診断カウンタ (0.8.344)。
     *
     * RFB は「クライアントが要求 → サーバは変化があるまで保留 → 変化したら 1 回返す」の
     * 往復で回る。どこかで往復が途切れると**画面が固まったまま何のエラーも出ない**ので、
     * ログが無いと「要求が出ていない」のか「更新が来ていない」のかを区別できない
     * (実機で「GUI を開いても、画面をタップするまで端末の窓が出ない」ときに区別できなかった)。
     * 出すのは接続直後の [TRACE_LIMIT] 件だけ。定常状態では 1 行も出さない。
     */
    private var reqSeq = 0
    private var updSeq = 0

    /**
     * 同期接続 + RFB 3.8 ハンドシェイク。**IO スレッドで呼ぶこと**。失敗時は例外を投げる。
     */
    override fun connect(timeoutMs: Int) {
        // 繋ぎ直し (切断された / パスワードを入れ直した) に備え、前回の残骸を先に畳む。
        // ZRLE の zlib ストリームは接続ごとに最初からなので、[inflater] も巻き戻す
        // (使い回すと前の接続の続きとして展開してしまい、画面が壊れる)。
        runCatching { socket?.close() }
        inflater.reset()
        val s = Socket()
        s.tcpNoDelay = true
        try {
            s.connect(InetSocketAddress(HostAddress.normalize(host), port), timeoutMs)
        } catch (e: Exception) {
            runCatching { s.close() }  // 未接続のソケットを積み残さない
            throw e
        }
        socket = s
        val inp = DataInputStream(BufferedInputStream(s.getInputStream(), 1 shl 16))
        val out = DataOutputStream(BufferedOutputStream(s.getOutputStream(), 1 shl 16))
        input = inp
        output = out

        try {
            handshake(inp, out)
        } catch (e: Exception) {
            // 認証失敗などで途中終了したソケットを掴んだままにしない。[closed] は立てないので、
            // パスワードを入れ直して同じインスタンスで繋ぎ直せる。
            runCatching { s.close() }
            socket = null
            input = null
            output = null
            throw e
        }

        pixels = IntArray(width * height)
        frame = createBitmap(width, height)
        _connected.value = true
        Log.i(TAG, "RFB connected: ${width}x$height '$desktopName'")

        // 最初は全画面 (non-incremental) を要求
        sendFramebufferUpdateRequest(out, incremental = false)
    }

    private fun handshake(inp: DataInputStream, out: DataOutputStream) {
        // 1. ProtocolVersion (サーバ→) を読み、こちらの上限 (3.8) との低い方に合わせる。
        //    リモートには 3.3 しか話さないサーバ (組込み・古い実装) が居るので、そこまで落とせる。
        val server = ByteArray(12).also { inp.readFully(it) }
        val serverVersion = String(server, Charsets.US_ASCII).trim()
        val minor = negotiateVersion(serverVersion)
        Log.d(TAG, "server ProtocolVersion=$serverVersion (using 3.$minor)")
        out.write("RFB 003.00$minor\n".toByteArray(Charsets.US_ASCII))
        out.flush()

        // 2. Security type の決定。3.7+ は「サーバが並べた中から選ぶ」、3.3 は「サーバが 1 つ指定」。
        val secType = if (minor >= 7) negotiateSecurity(inp, out) else readSecurity33(inp)
        if (secType == VncAuth.SEC_VNC_AUTH) answerVncAuth(inp, out)

        // 3. SecurityResult。3.8 は必ず来る / 3.3・3.7 は VNC 認証のときだけ来る
        //    (None では何も来ずに ClientInit へ進むので、読みに行くと次のメッセージを食う)。
        if (minor >= 8 || secType == VncAuth.SEC_VNC_AUTH) readSecurityResult(inp, minor, secType)

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
            throw IOException("invalid screen size: ${width}x$height")
        }

        // 6. SetPixelFormat: 32bpp little-endian truecolor (ARGB_8888 互換)
        setPixelFormat(out)
        // 7. SetEncodings: 優先順 ZRLE → CopyRect → Raw。ZRLE はタイル+zlib で
        //    更新矩形が小さくなり、差分 setPixels と相性が良い。Raw は常に保険で残す。
        //    擬似エンコーディング ExtendedDesktopSize を併せて通知し、サーバ側の解像度変更
        //    (回転時の再ネゴ) と、クライアントからの SetDesktopSize 要求を有効にする。
        //    ⭐ カーソル擬似エンコーディング (Cursor / XCursor) も通知する。これを送らない限り
        //    サーバは**ポインタを framebuffer に焼き込む**ので、こちらが描く仮想カーソルと
        //    合わせて 2 個見えてしまう (0.8.431)。通知したサーバは形状だけを別の矩形で送り、
        //    画面には焼き込まなくなる。
        setEncodings(
            out,
            intArrayOf(ENC_ZRLE, ENC_COPYRECT, ENC_RAW, ENC_EXT_DESKTOP_SIZE, ENC_CURSOR, ENC_X_CURSOR),
        )
    }

    /**
     * ProtocolVersion 文字列 ("RFB 003.008") から、こちらが使う minor 版数を決める。
     *
     * 返すのは 8 / 7 / 3 の 3 通りだけ。Apple の "RFB 003.889" のような独自版は 3.8 として扱う
     * (他のクライアントも同じ)。**VNC ではない相手に繋いだ場合もここで分かる** — ポートを
     * 間違えて sshd に繋ぐと "SSH-2.0-Open" が読めるので、そのまま理由に出す。
     */
    private fun negotiateVersion(version: String): Int {
        // 版数は行全体で一致させる (末尾 $ を生文字列に置くと Kotlin の文字列テンプレートと
        // 紛らわしいので matchEntire を使う)。
        val m = Regex("""RFB (\d{3})\.(\d{3})""").matchEntire(version)
            ?: throw IOException("not a VNC server (got \"" + version.take(24) + "\")")
        if (m.groupValues[1].toInt() != 3) throw IOException("unsupported RFB version: $version")
        val minor = m.groupValues[2].toInt()
        return when {
            minor >= 8 -> 8
            minor == 7 -> 7
            else -> 3
        }
    }

    /** 3.7+ の security 交渉: サーバが並べた型から 1 つ選んで返事する。 */
    private fun negotiateSecurity(inp: DataInputStream, out: DataOutputStream): Int {
        val count = inp.readUnsignedByte()
        if (count == 0) throw IOException("VNC security negotiation failed: ${readReason(inp)}")
        val types = ByteArray(count).also { inp.readFully(it) }
        val chosen = VncAuth.pickSecurityType(types.map { it.toInt() and 0xFF }, password.isNotEmpty())
        out.writeByte(chosen)
        out.flush()
        return chosen
    }

    /** 3.3 の security: サーバが U32 で 1 つ指定してくる (こちらに選択権が無い)。 */
    private fun readSecurity33(inp: DataInputStream): Int {
        val type = inp.readInt()
        if (type == VncAuth.SEC_INVALID) throw IOException("VNC connection refused: ${readReason(inp)}")
        // 「パスワードを持っているか」等の判断は 3.7+ と同じ規則に乗せる。
        return VncAuth.pickSecurityType(listOf(type), password.isNotEmpty())
    }

    /** VNC 認証 (2): 16 バイトのチャレンジを DES で暗号化して返す。 */
    private fun answerVncAuth(inp: DataInputStream, out: DataOutputStream) {
        val challenge = ByteArray(VncAuth.CHALLENGE_BYTES).also { inp.readFully(it) }
        out.write(VncAuth.challengeResponse(password, challenge))
        out.flush()
    }

    /** SecurityResult (0 = OK)。失敗理由の文字列が付くのは 3.8 だけ。 */
    private fun readSecurityResult(inp: DataInputStream, minor: Int, secType: Int) {
        val result = inp.readInt()
        if (result == 0) return
        val reason = if (minor >= 8) runCatching { readReason(inp) }.getOrDefault("") else ""
        val tail = if (reason.isEmpty()) "" else ": $reason"
        if (secType == VncAuth.SEC_VNC_AUTH) throw RfbAuthFailedException("VNC authentication failed$tail")
        throw IOException("VNC SecurityResult=$result$tail")
    }

    private fun readReason(inp: DataInputStream): String {
        val len = inp.readInt().coerceIn(0, 4096)
        return ByteArray(len).also { inp.readFully(it) }.toString(Charsets.UTF_8)
    }

    private fun setPixelFormat(out: DataOutputStream) = synchronized(writeLock) {
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

    private fun setEncodings(out: DataOutputStream, encs: IntArray) = synchronized(writeLock) {
        out.writeByte(MSG_SET_ENCODINGS)
        out.writeByte(0) // padding
        out.writeShort(encs.size)
        for (e in encs) out.writeInt(e)
        out.flush()
    }

    private fun sendFramebufferUpdateRequest(out: DataOutputStream, incremental: Boolean) = synchronized(writeLock) {
        out.writeByte(MSG_FB_UPDATE_REQUEST)
        out.writeByte(if (incremental) 1 else 0)
        out.writeShort(0)      // x
        out.writeShort(0)      // y
        out.writeShort(width)
        out.writeShort(height)
        out.flush()
        if (reqSeq < TRACE_LIMIT) {
            reqSeq++
            Log.d(TAG, "req#$reqSeq incremental=$incremental ${width}x$height")
        }
    }

    /**
     * ポインタイベント (RFB type 5) を送る。**UI スレッドから呼んでよい**。
     * 実際のソケット書き込みは [sender]（単一スレッド）へ退避する（main でのネットワーク禁止 + 順序保証）。
     *
     * @param buttonMask ボタン状態のビットマスク。bit0=左, bit1=中, bit2=右, bit3=ホイール上, bit4=ホイール下。
     * @param x,y フレームバッファ座標（呼び出し側で表示→FB 変換済み。範囲外はクランプ）。
     */
    override fun sendPointerEvent(buttonMask: Int, x: Int, y: Int) {
        if (closed) return
        val cx = x.coerceIn(0, (width - 1).coerceAtLeast(0))
        val cy = y.coerceIn(0, (height - 1).coerceAtLeast(0))
        submitWrite {
            val out = output ?: return@submitWrite
            synchronized(writeLock) {
                out.writeByte(MSG_POINTER_EVENT)
                out.writeByte(buttonMask and 0xFF)
                out.writeShort(cx)
                out.writeShort(cy)
                out.flush()
            }
        }
    }

    /**
     * キーイベント (RFB type 4) を送る。**UI スレッドから呼んでよい**（書き込みは [sender] に退避）。
     * keysym は X11 の値（[com.zerotoship.z2term.gui.GuiKeyMapper] で変換）。
     */
    override fun sendKeyEvent(keysym: Int, down: Boolean) {
        if (closed || keysym == 0) return
        submitWrite {
            val out = output ?: return@submitWrite
            synchronized(writeLock) {
                out.writeByte(MSG_KEY_EVENT)
                out.writeByte(if (down) 1 else 0)
                out.writeShort(0) // padding
                out.writeInt(keysym)
                out.flush()
            }
        }
    }

    /** 入力送信を [sender] スレッドで実行する。IOException は接続断とみなして握り潰す。 */
    private fun submitWrite(block: () -> Unit) {
        if (closed) return
        try {
            sender.execute {
                if (closed) return@execute
                try {
                    block()
                } catch (e: IOException) {
                    if (!closed) Log.w(TAG, "input send failed", e)
                }
            }
        } catch (_: RejectedExecutionException) {
            // close 後。無視。
        }
    }

    /** keysym を down→up でまとめて送る（タップ入力や文字確定で使う）。 */
    override fun tapKey(keysym: Int) {
        sendKeyEvent(keysym, down = true)
        sendKeyEvent(keysym, down = false)
    }

    /**
     * サーバ→クライアントのメッセージを処理し続ける。**IO スレッドで呼ぶ**。
     * [close] されるか接続が切れると返る。
     */
    override fun run() {
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
                    else -> throw IOException("unknown server message type=$type")
                }
            }
        } catch (e: Exception) {
            if (!closed) Log.w(TAG, "RFB receive loop ended", e)
        } finally {
            _connected.value = false
        }
    }

    private fun handleFramebufferUpdate(inp: DataInputStream, out: DataOutputStream) {
        inp.readByte() // padding
        val numRects = inp.readUnsignedShort()
        // 更新された矩形の外接 (バウンディング) 範囲。ここだけ Bitmap へ転送する
        // (毎フレーム全画面 setPixels を避ける = 体感の軽量化)。
        var dirty = false
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = 0; var maxY = 0
        for (r in 0 until numRects) {
            val x = inp.readUnsignedShort()
            val y = inp.readUnsignedShort()
            val w = inp.readUnsignedShort()
            val h = inp.readUnsignedShort()
            val enc = inp.readInt()
            when (enc) {
                ENC_RAW -> readRaw(inp, x, y, w, h)
                ENC_COPYRECT -> readCopyRect(inp, x, y, w, h)
                ENC_ZRLE -> {
                    readZrle(inp, x, y, w, h)
                    if (!zrleSeen) { zrleSeen = true; Log.i(TAG, "ZRLE decoding active") }
                }
                ENC_EXT_DESKTOP_SIZE -> handleExtendedDesktopSize(inp, x, w, h)
                ENC_CURSOR -> skipRichCursor(inp, w, h)
                ENC_X_CURSOR -> skipXCursor(inp, w, h)
                else -> throw IOException("unsupported encoding=$enc (rect $x,$y ${w}x$h)")
            }
            // 擬似エンコーディング (解像度変更・カーソル形状) は描画矩形ではないので
            // バウンディング対象外。
            if (!isPseudoEncoding(enc)) {
                val rx0 = x.coerceIn(0, width); val ry0 = y.coerceIn(0, height)
                val rx1 = (x + w).coerceIn(0, width); val ry1 = (y + h).coerceIn(0, height)
                if (rx1 > rx0 && ry1 > ry0) {
                    dirty = true
                    if (rx0 < minX) minX = rx0
                    if (ry0 < minY) minY = ry0
                    if (rx1 > maxX) maxX = rx1
                    if (ry1 > maxY) maxY = ry1
                }
            }
        }
        if (updSeq < TRACE_LIMIT) {
            updSeq++
            Log.d(TAG, "upd#$updSeq rects=$numRects dirty=$dirty " +
                "bbox=($minX,$minY)-($maxX,$maxY) fb=${width}x$height")
        }
        if (dirty) pushFrame(minX, minY, maxX - minX, maxY - minY)
        // 解像度が変わった直後は全画面 (non-incremental) を要求し直す。それ以外は差分。
        val full = pendingFullRequest
        pendingFullRequest = false
        sendFramebufferUpdateRequest(out, incremental = !full)
    }

    /** 描画矩形ではない擬似エンコーディングか (バウンディング計算から外す)。 */
    private fun isPseudoEncoding(enc: Int): Boolean =
        enc == ENC_EXT_DESKTOP_SIZE || enc == ENC_CURSOR || enc == ENC_X_CURSOR

    /**
     * Cursor (RichCursor) 擬似矩形 (-239) を読み捨てる。
     *
     * 中身はピクセル (w*h*4 byte。[setPixelFormat] で 32bpp に固定してあるので 4) と
     * 1bit 透過マスク (`((w+7)/8)*h` byte)。**形は使わない** — こちらは画面倍率に依らず
     * 同じ大きさで見える自前の矢印を描くため (縮小表示だと相手の 24px カーソルは指で
     * 狙えない大きさになる)。それでも要求しておくのは、要求しないとサーバが
     * framebuffer にポインタを焼き込んでカーソルが 2 個に見えるから (0.8.431)。
     * ⚠ **バイト数はきっちり読み飛ばす。** 1 バイトでもずれるとストリームが壊れる。
     */
    private fun skipRichCursor(inp: DataInputStream, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return // w*h==0 は「カーソル無し」。本体は付いてこない
        skipFully(inp, w.toLong() * h.toLong() * 4L + ((w + 7) / 8).toLong() * h.toLong())
    }

    /** XCursor 擬似矩形 (-240) を読み捨てる。前景/背景色 6 byte + 1bit ビットマップ 2 枚 (source/mask)。 */
    private fun skipXCursor(inp: DataInputStream, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        skipFully(inp, 6L + ((w + 7) / 8).toLong() * h.toLong() * 2L)
    }

    /**
     * ExtendedDesktopSize 擬似矩形 (-308) を処理する。矩形ヘッダの幅/高さが新しい
     * デスクトップ解像度、x-position が変更理由 (0=サーバ/他クライアント, 1=本クライアントの
     * SetDesktopSize 応答)。本体は number-of-screens + 各スクリーン定義 (16byte)。
     * 1 番目のスクリーン ID を控え、こちらからの [requestDesktopSize] で再利用する。
     */
    private fun handleExtendedDesktopSize(inp: DataInputStream, reason: Int, newW: Int, newH: Int) {
        val numScreens = inp.readUnsignedByte()
        inp.readByte(); inp.readByte(); inp.readByte() // padding
        for (i in 0 until numScreens) {
            val id = inp.readInt()
            inp.readUnsignedShort() // x
            inp.readUnsignedShort() // y
            inp.readUnsignedShort() // width
            inp.readUnsignedShort() // height
            inp.readInt()           // flags
            if (i == 0) { screenId = id; hasScreenId = true }
        }
        if (newW in 1..8192 && newH in 1..8192 && (newW != width || newH != height)) {
            resizeFramebuffer(newW, newH)
            Log.i(TAG, "ExtendedDesktopSize: ${newW}x$newH (reason=$reason, screens=$numScreens)")
        }
    }

    /** フレームバッファ (pixels/Bitmap) を作り直し、次回の全画面要求を予約する。受信ループから呼ぶ。 */
    private fun resizeFramebuffer(newW: Int, newH: Int) {
        synchronized(frameLock) {
            width = newW
            height = newH
            pixels = IntArray(newW * newH)
            frame = createBitmap(newW, newH)
        }
        pendingFullRequest = true
        _redraw.value = _redraw.value + 1
    }

    /**
     * クライアントから解像度変更を要求する (SetDesktopSize, type 251)。**UI スレッドから呼んでよい**。
     * 1 スクリーン構成 (0,0 起点) で要求する。サーバが対応していれば ExtendedDesktopSize 矩形で応答が返る。
     */
    override fun requestDesktopSize(width: Int, height: Int) {
        if (closed || width !in 1..8192 || height !in 1..8192) return
        if (width == this.width && height == this.height) return
        val id = if (hasScreenId) screenId else 1
        submitWrite {
            val out = output ?: return@submitWrite
            synchronized(writeLock) {
                out.writeByte(MSG_SET_DESKTOP_SIZE)
                out.writeByte(0)            // padding
                out.writeShort(width)
                out.writeShort(height)
                out.writeByte(1)            // number-of-screens
                out.writeByte(0)            // padding
                // screen[0]
                out.writeInt(id)
                out.writeShort(0)           // x
                out.writeShort(0)           // y
                out.writeShort(width)
                out.writeShort(height)
                out.writeInt(0)             // flags
                out.flush()
            }
        }
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

    // ---- ZRLE デコード -----------------------------------------------------
    // RFB ZRLE: rect は U32 長 + zlib 圧縮データ。zlib ストリームは**セッション全体で
    // 連続**するので [inflater] は使い回す (rect 毎に reset しない)。展開後は 64x64
    // タイル列 (左→右, 上→下)。各タイルは subencoding バイトで raw/solid/palette/RLE を切替。
    // ピクセルは CPIXEL=3byte (32bpp/depth24 で最上位バイト未使用 → B,G,R の 3byte)。
    private val inflater = java.util.zip.Inflater()
    private val zInflateBuf = ByteArray(1 shl 16)
    private var zdata: ByteArray = ByteArray(0)
    private var zpos = 0
    @Volatile private var zrleSeen = false

    private fun zU8(): Int = zdata[zpos++].toInt() and 0xFF
    private fun zCpixel(): Int {
        val b = zdata[zpos].toInt() and 0xFF
        val g = zdata[zpos + 1].toInt() and 0xFF
        val r = zdata[zpos + 2].toInt() and 0xFF
        zpos += 3
        return ALPHA or (r shl 16) or (g shl 8) or b
    }

    private fun readZrle(inp: DataInputStream, x: Int, y: Int, w: Int, h: Int) {
        val len = inp.readInt()
        if (len < 0) throw IOException("ZRLE invalid length=$len")
        val comp = ByteArray(len)
        inp.readFully(comp)
        if (w <= 0 || h <= 0) return
        inflater.setInput(comp)
        val out = java.io.ByteArrayOutputStream(len * 4)
        try {
            while (!inflater.needsInput()) {
                val n = inflater.inflate(zInflateBuf)
                if (n > 0) out.write(zInflateBuf, 0, n) else break
            }
        } catch (e: java.util.zip.DataFormatException) {
            throw IOException("ZRLE decompression failed", e)
        }
        zdata = out.toByteArray()
        zpos = 0
        decodeZrleTiles(x, y, w, h)
    }

    private fun decodeZrleTiles(rx: Int, ry: Int, rw: Int, rh: Int) {
        var ty = 0
        while (ty < rh) {
            val th = minOf(64, rh - ty)
            var tx = 0
            while (tx < rw) {
                val tw = minOf(64, rw - tx)
                val sub = zU8()
                when {
                    sub == 0 -> // raw
                        for (j in 0 until th) for (i in 0 until tw)
                            zSet(rx + tx + i, ry + ty + j, zCpixel())
                    sub == 1 -> { // solid
                        val p = zCpixel()
                        for (j in 0 until th) for (i in 0 until tw) zSet(rx + tx + i, ry + ty + j, p)
                    }
                    sub in 2..16 -> { // packed palette
                        val palette = IntArray(sub) { zCpixel() }
                        val bpp = if (sub == 2) 1 else if (sub <= 4) 2 else 4
                        val mask = (1 shl bpp) - 1
                        for (j in 0 until th) {
                            var bitPos = 0
                            var cur = 0
                            for (i in 0 until tw) {
                                if (bitPos == 0) { cur = zU8(); bitPos = 8 }
                                bitPos -= bpp
                                zSet(rx + tx + i, ry + ty + j, palette[(cur ushr bitPos) and mask])
                            }
                            // 行は byte 境界へパディング (端数ビットは破棄 = 次行は新 byte)
                        }
                    }
                    sub == 128 -> { // plain RLE
                        val total = tw * th
                        var count = 0
                        while (count < total) {
                            val p = zCpixel()
                            var run = 1
                            var b: Int
                            do { b = zU8(); run += b } while (b == 255)
                            zFillRun(rx + tx, ry + ty, tw, count, run, p)
                            count += run
                        }
                    }
                    sub in 130..255 -> { // palette RLE (palette size = sub-128)
                        val palette = IntArray(sub - 128) { zCpixel() }
                        val total = tw * th
                        var count = 0
                        while (count < total) {
                            val idx = zU8()
                            var run = 1
                            val p: Int
                            if (idx and 0x80 != 0) {
                                p = palette[idx and 0x7F]
                                var b: Int
                                do { b = zU8(); run += b } while (b == 255)
                            } else {
                                p = palette[idx]
                            }
                            zFillRun(rx + tx, ry + ty, tw, count, run, p)
                            count += run
                        }
                    }
                    else -> throw IOException("ZRLE unsupported subencoding=$sub")
                }
                tx += 64
            }
            ty += 64
        }
    }

    /** タイル内 linear 位置 [start] から [run] 個を行優先で [color] 埋め。tileX0/tileY0 はタイル左上の FB 座標。 */
    private fun zFillRun(tileX0: Int, tileY0: Int, tw: Int, start: Int, run: Int, color: Int) {
        for (k in 0 until run) {
            val pos = start + k
            zSet(tileX0 + (pos % tw), tileY0 + (pos / tw), color)
        }
    }

    private fun zSet(px: Int, py: Int, color: Int) {
        if (px in 0 until width && py in 0 until height) pixels[py * width + px] = color
    }
    // ---- /ZRLE ------------------------------------------------------------

    private fun pushFrame(rx: Int, ry: Int, rw: Int, rh: Int) {
        if (rw <= 0 || rh <= 0) return
        val bmp = frame ?: return
        synchronized(frameLock) {
            // pixels の (rx,ry) 起点・stride=width の部分矩形だけを Bitmap へ転送。
            bmp.setPixels(pixels, ry * width + rx, width, rx, ry, rw, rh)
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
        val len = inp.readInt().toLong() and 0xFFFFFFFFL // CARD32 (符号なし)
        if (len == 0L) return
        // ペースト爆弾対策に上限を設け、超過分は読み捨てる。RFB の cut-text は Latin-1。
        val cap = minOf(len, MAX_CUT_TEXT.toLong()).toInt()
        val body = ByteArray(cap)
        inp.readFully(body)
        if (len > cap) skipFully(inp, len - cap)
        runCatching { onRemoteClipboardText?.invoke(String(body, Charsets.ISO_8859_1)) }
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

    override fun close() {
        closed = true
        _connected.value = false
        runCatching { sender.shutdownNow() }
        runCatching { inflater.end() }
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
    }

    companion object {
        private const val TAG = "RfbClient"
        private const val ALPHA = 0xFF shl 24

        /** 起動直後の要求 / 受信を何件までログに出すか (0.8.344・定常状態では出さない)。 */
        private const val TRACE_LIMIT = 40

        /** ServerCutText の取り込み上限 (byte)。これを超える分は読み捨てる。 */
        private const val MAX_CUT_TEXT = 256 * 1024

        // client→server message types
        private const val MSG_SET_PIXEL_FORMAT = 0
        private const val MSG_SET_ENCODINGS = 2
        private const val MSG_FB_UPDATE_REQUEST = 3
        private const val MSG_KEY_EVENT = 4
        private const val MSG_POINTER_EVENT = 5
        private const val MSG_SET_DESKTOP_SIZE = 251

        // server→client message types
        private const val SRV_FB_UPDATE = 0
        private const val SRV_SET_COLOUR_MAP = 1
        private const val SRV_BELL = 2
        private const val SRV_CUT_TEXT = 3

        // encodings
        private const val ENC_RAW = 0
        private const val ENC_COPYRECT = 1
        private const val ENC_ZRLE = 16
        // pseudo-encoding: 解像度変更 (TigerVNC/RFB ExtendedDesktopSize)
        private const val ENC_EXT_DESKTOP_SIZE = -308
        // pseudo-encoding: カーソル形状。要求するとサーバは framebuffer へ焼き込まなくなる
        private const val ENC_CURSOR = -239
        private const val ENC_X_CURSOR = -240
    }
}
