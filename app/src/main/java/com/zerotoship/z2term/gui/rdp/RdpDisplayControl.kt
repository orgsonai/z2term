package com.zerotoship.z2term.gui.rdp

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * [MS-RDPEDISP] の `Microsoft::Windows::RDS::DisplayControl` dynamic virtual channel。
 *
 * 端末を回したり分割の幅を変えたりしたときに、**相手のデスクトップをこちらの枠に合わせて
 * 作り直させる**。RDP は接続のたびにこちら専用のセッションを作らせるので、大きさを決めるのは
 * クライアント側 (→ `RdpTarget` の KDoc)。
 *
 * ⚠ **サーバーの CAPS を受け取るまで Monitor Layout を送らない** ([MS-RDPEDISP] 1.3.1 の順番)。
 * チャネルが開いた直後は「何面まで・どれだけの面積まで許すか」を知らないので、先に投げても
 * 相手が受け付けられる保証がない。⇒ **caps 前の要求は最後の 1 つだけ保留し、caps が来た時点で送る**
 * (回転が続けて起きても、最後に落ち着いた大きさだけが要求される)。
 *
 * ⛔ **[send] をロックの中から呼ばない。** 送信先の `RdpDynamicChannel` も自分の錠を持っていて、
 * 受信スレッドは「DVC の錠 → ここの錠」の順で入ってくる。ここで逆順に取ると、回転と画面更新が
 * 同時に起きたときに噛み合って両方止まる。⇒ **錠の中では組み立てるだけにして、送信は外で行う。**
 */
internal class RdpDisplayControl(private val send: (ByteArray) -> Unit) {
    private data class Size(val width: Int, val height: Int)

    /** CAPS を受け取ったか。受け取るまでは送信しない。 */
    private var ready = false
    private var maxMonitors = 1
    /** 1 面あたりの画素数の上限 (MaxMonitorAreaFactorA × B)。0 = 上限を知らされていない。 */
    private var maxArea = 0L
    private var pending: Size? = null
    private var sent: Size? = null

    /** サーバー → クライアントの PDU。今のところ意味があるのは CAPS だけ。 */
    fun accept(message: ByteArray) {
        val layout = synchronized(this) { readPdus(message) }
        layout?.let(send)
    }

    /**
     * デスクトップを [width] x [height] へ作り直すよう要求する。
     *
     * 端末の枠はどんな値でも来るので、**相手が拒む値をそのまま投げない**: 偶数へ丸め
     * ([MS-RDPEDISP] 2.2.2.2.1 は幅が偶数であることを求める)、200〜8192 に収め、面積の上限を
     * 超えるなら送らない。⭐ 同じ大きさの再送もしない — 1 回ごとにセッションが作り直され、
     * 画面がいったん消えて戻ってくるため。
     */
    fun requestSize(width: Int, height: Int) {
        val layout = synchronized(this) { prepare(Size(normalize(width), normalize(height))) }
        layout?.let(send)
    }

    /** チャネルが閉じたとき。次に開いたら CAPS からやり直す。 */
    fun reset() {
        synchronized(this) {
            ready = false
            pending = null
            sent = null
        }
    }

    /** 錠の中で呼ぶ。送るべき Monitor Layout があれば返す。 */
    private fun readPdus(message: ByteArray): ByteArray? {
        var offset = 0
        var layout: ByteArray? = null
        while (offset + HEADER_SIZE <= message.size) {
            val type = le32(message, offset)
            val length = le32(message, offset + 4)
            if (length < HEADER_SIZE || offset + length > message.size) {
                throw IOException("invalid DisplayControl PDU length: $length")
            }
            if (type == TYPE_CAPS) layout = caps(message, offset + HEADER_SIZE, offset + length)
            // ⛔ **知らない PDU で例外を投げない。** 長さで区切られているので読み飛ばせる
            //    (RDPGFX と同じ作法)。投げるとチャネルごと落ちて resize が二度とできなくなる。
            offset += length
        }
        return layout
    }

    private fun caps(message: ByteArray, start: Int, end: Int): ByteArray? {
        if (end - start < CAPS_BODY_SIZE) throw IOException("truncated DisplayControl caps")
        maxMonitors = le32(message, start)
        val factorA = le32(message, start + 4).toLong() and 0xFFFFFFFFL
        val factorB = le32(message, start + 8).toLong() and 0xFFFFFFFFL
        maxArea = factorA * factorB
        ready = true
        Log.i(TAG, "DisplayControl: caps monitors=$maxMonitors area=$maxArea")
        val held = pending ?: return null
        pending = null
        return prepare(held)
    }

    /** 錠の中で呼ぶ。送るなら Monitor Layout、送らないなら null。 */
    private fun prepare(size: Size): ByteArray? {
        if (maxArea > 0 && size.width.toLong() * size.height > maxArea) {
            Log.i(TAG, "DisplayControl: ${size.width}x${size.height} exceeds the area limit $maxArea; not sent")
            return null
        }
        if (!ready) {
            pending = size
            return null
        }
        if (size == sent) return null
        sent = size
        Log.i(TAG, "DisplayControl: requesting ${size.width}x${size.height}")
        return monitorLayout(size.width, size.height)
    }

    private fun monitorLayout(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream(HEADER_SIZE + 8 + MONITOR_LAYOUT_SIZE)
        fun le32(value: Int) = repeat(4) { out.write((value ushr (it * 8)) and 0xFF) }
        le32(TYPE_MONITOR_LAYOUT)
        le32(HEADER_SIZE + 8 + MONITOR_LAYOUT_SIZE)
        le32(MONITOR_LAYOUT_SIZE)
        le32(1) // NumMonitors。GUI タブは 1 枚の画面として使うので 1 面だけ要求する。
        le32(MONITOR_PRIMARY)
        le32(0) // Left
        le32(0) // Top
        le32(width)
        le32(height)
        // PhysicalWidth / PhysicalHeight は 0 = 「知らせない」。⚠ 中途半端な実寸を入れると相手が
        // それを元に DPI を決めてしまう。枠に合わせて拡大するのはこちらの表示側の仕事。
        le32(0)
        le32(0)
        le32(ORIENTATION_LANDSCAPE)
        le32(SCALE_FACTOR_100)
        le32(SCALE_FACTOR_100)
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "RdpDisplayControl"

        const val CHANNEL_NAME = "Microsoft::Windows::RDS::DisplayControl"

        private const val HEADER_SIZE = 8
        private const val CAPS_BODY_SIZE = 12
        private const val TYPE_MONITOR_LAYOUT = 0x02
        private const val TYPE_CAPS = 0x05
        private const val MONITOR_LAYOUT_SIZE = 40
        private const val MONITOR_PRIMARY = 0x01
        private const val ORIENTATION_LANDSCAPE = 0
        private const val SCALE_FACTOR_100 = 100
        private const val MIN_SIDE = 200
        private const val MAX_SIDE = 8192

        /** [MS-RDPEDISP] 2.2.2.2.1 が許す範囲へ収める (幅の偶数条件は高さにも合わせておく)。 */
        private fun normalize(px: Int): Int = (px and 1.inv()).coerceIn(MIN_SIDE, MAX_SIDE)

        private fun le32(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
