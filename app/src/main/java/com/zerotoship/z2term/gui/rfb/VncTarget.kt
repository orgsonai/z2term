package com.zerotoship.z2term.gui.rfb

import com.zerotoship.z2term.gui.RemoteDesktopClient
import com.zerotoship.z2term.gui.RemoteTarget
import com.zerotoship.z2term.net.HostAddress

/**
 * リモート VNC の接続先 1 件 (A1)。[RemoteTarget] の RFB 版。
 *
 * [com.zerotoship.z2term.gui.GuiSession] にこれを渡すと **proot も z2gui も起動せず**、
 * ここに書かれたホストへ RFB で繋ぎに行くだけのタブになる。保存は SSH の接続先
 * ([com.zerotoship.z2term.channel.SshProfile]) に相乗りしていて、ここはその 1 回の接続に
 * 必要な値だけを運ぶ入れ物。
 */
data class VncTarget(
    override val host: String,
    /**
     * RFB ポート。VNC の画面 `:N` は `5900+N` なので、`:1` なら 5901。
     * リモートのデスクトップ共有 (Windows / macOS / x11vnc) は `:0` = 5900 が多い。
     */
    override val port: Int = DEFAULT_PORT,
    /** VNC 認証のパスワード。空ならパスワード不要のサーバ専用。 */
    val password: String = "",
    /** タブに出す名前。空なら `host:port`。 */
    val name: String = "",
    /** SSH 一時転送など、この VNC タブと同じ寿命を持つ通信経路。 */
    internal val transportCloser: (() -> Unit)? = null,
) : RemoteTarget {
    private val transportClosed = java.util.concurrent.atomic.AtomicBoolean(false)

    /** タブ名 (名前が無ければ接続先そのもの)。 */
    override val label: String get() = name.ifBlank { HostAddress.hostPort(host, port) }

    override fun createClient(): RemoteDesktopClient =
        RfbClient(host = host, port = port, password = password)

    override fun closeTransport() {
        if (transportClosed.compareAndSet(false, true)) transportCloser?.invoke()
    }

    companion object {
        /** 既定ポート。TigerVNC の `vncserver` が最初に使う `:1` に合わせてある。 */
        const val DEFAULT_PORT = 5901
    }
}
