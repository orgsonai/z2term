package com.zerotoship.z2term.channel

import com.jcraft.jsch.Channel
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * 踏み台 1 段ぶんの通り道 (`ssh -J` 相当)。
 *
 * ⭐ **手前のセッションの中に `direct-tcpip` を 1 本開き、その中で次の SSH を話す。**
 * OpenSSH の `ProxyJump` と同じ形で、**端末側に待ち受けポートを開かない**。
 *
 * ⚠ **`-L` で中継しない**という判断が要点。ローカル転送を挟む書き方だと JSch から見た
 * 相手が `127.0.0.1:<毎回変わるポート>` になり、
 *  1. **known_hosts が 127.0.0.1 で記録される** — 別の踏み台の先も同じ名前になり、鍵の
 *     すり替わりを見分けられなくなる (この画面はホスト鍵を必ず確認させる作りなので致命的)
 *  2. 端末上の他アプリからもその待ち受けポートへ入れてしまう
 * の 2 つを同時に踏む。[Proxy] なら [Session] のホスト名は本来の宛先のまま残る。
 *
 * ⚠ **JSch は `getSocket()` が null でも動く** (実測: `connect` の `setSoTimeout` も
 * `setTimeout` も null を見て飛ばす)。ただし**ソケットが無い＝読み取りタイムアウトが効かない**
 * ので、このセッション自身の keepalive は鳴らない。⇒ 常駐の生存確認は**ソケットを持つ
 * 1 段目**に載せる ([SshLink.enableKeepAlive])。
 */
internal class JumpProxy(private val via: Session) : Proxy {

    private var channel: Channel? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val forwarder = via.getStreamForwarder(host, port)
        // ⚠ **繋ぐ前に受け取る。** getInputStream() がこのチャネルの受け皿を作る呼び出しで、
        //    接続後に呼ぶと最初のバイトを取りこぼす。
        val incoming = forwarder.inputStream
        val outgoing = forwarder.outputStream
        forwarder.connect(if (timeout > 0) timeout else SshSessionFactory.CONNECT_TIMEOUT_MS)
        channel = forwarder
        input = incoming
        output = outgoing
    }

    override fun getInputStream(): InputStream? = input

    override fun getOutputStream(): OutputStream? = output

    /** ⚠ 生のソケットは無い (通り道は手前のセッションの中のチャネル)。 */
    override fun getSocket(): Socket? = null

    override fun close() {
        runCatching { channel?.disconnect() }
        channel = null
        input = null
        output = null
    }
}
