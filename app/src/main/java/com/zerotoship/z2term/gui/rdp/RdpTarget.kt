package com.zerotoship.z2term.gui.rdp

import com.zerotoship.z2term.gui.RemoteDesktopClient
import com.zerotoship.z2term.gui.RemoteTarget
import com.zerotoship.z2term.net.HostAddress
import java.security.cert.X509Certificate

/**
 * リモート RDP の接続先 1 件。[RemoteTarget] の RDP 版で、[VncTarget] と同じ立ち位置。
 *
 * ⚠ **画面の大きさを決めるのはこちら側**。RFB は「もう立っている画面へ後から覗きに行く」ので
 * サーバーが決めた解像度をそのまま受け取るが、RDP は接続のたびに**新しいセッションを作らせる**
 * ので、クライアントが希望の大きさを出さないと相手の既定 (1024x768 等) になる。ここには
 * [fitDesktopSize] で端末の画面から決めた値が入る。
 *
 * @property trustKey 証明書の指紋を覚えるときの相手の名前。SSH 転送を通すと [host] は
 *   `127.0.0.1` + 毎回変わるポートになってしまうので、**本来の宛先**をここに入れる。
 */
data class RdpTarget(
    override val host: String,
    override val port: Int = DEFAULT_PORT,
    val user: String = "",
    val password: String = "",
    val domain: String = "",
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
    val name: String = "",
    val trustKey: String = HostAddress.hostPort(host, port),
    /** TLS 証明書を受け入れるか。既定は「受け入れない」= 呼び出し側が必ず決める。 */
    internal val certificateVerifier: (X509Certificate) -> Boolean = { false },
    /** SSH 一時転送など、この RDP タブと同じ寿命を持つ通信経路。 */
    internal val transportCloser: (() -> Unit)? = null,
) : RemoteTarget {
    private val transportClosed = java.util.concurrent.atomic.AtomicBoolean(false)

    override val label: String get() = name.ifBlank { HostAddress.hostPort(host, port) }

    override fun createClient(): RemoteDesktopClient = RdpClient(
        host = host,
        port = port,
        credentials = CredSspNtlm.Credentials(
            user = user,
            domain = domain,
            password = password,
        ),
        settings = RdpMcs.ClientSettings(width = width, height = height),
        certificateVerifier = certificateVerifier,
    )

    override fun closeTransport() {
        if (transportClosed.compareAndSet(false, true)) transportCloser?.invoke()
    }

    companion object {
        /** RDP の既定ポート。 */
        const val DEFAULT_PORT = 3389
        const val DEFAULT_WIDTH = 1024
        const val DEFAULT_HEIGHT = 768

        /** 要求できる画面の範囲。上限は [RdpMcs.ClientSettings] の検査と揃えてある。 */
        private const val MIN_SIDE = 640
        private const val MAX_SIDE = 4096

        /**
         * 端末の画面 (px) から、RDP へ要求するデスクトップの大きさを決める。
         *
         * - **横長にする**: GUI タブは横画面で使うので、長辺を幅にした方が縦横の入れ替えが要らない。
         * - **4 の倍数へ丸める**: bitmap update の矩形は 4 の倍数で来ることが多く、端に半端な
         *   1〜3 px が残ると毎回そこだけ描き換わらない帯になる。
         * - 640x480 未満・4096 超は丸めずに範囲へ収める (相手が拒否する大きさを出さない)。
         */
        fun fitDesktopSize(widthPx: Int, heightPx: Int): Pair<Int, Int> {
            val long = maxOf(widthPx, heightPx)
            val short = minOf(widthPx, heightPx)
            return round(long) to round(short)
        }

        private fun round(px: Int): Int = (px / 4 * 4).coerceIn(MIN_SIDE, MAX_SIDE)
    }
}
