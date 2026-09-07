package com.zerotoship.z2term.channel

import android.util.Log
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import com.zerotoship.z2term.net.HostAddress
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * SOCKS プロキシ (`ssh -D`)。**繋いだ先の網から外へ出るための口**を端末側に開く。
 *
 * `-L` との違いは**宛先を先に決めないこと**。使う側が 1 接続ごとに相手を指定するので、
 * ブラウザや `curl --socks5-hostname` の設定 1 つで、相手の数だけ転送を書かずに済む。
 *
 * ⚠ **JSch は SOCKS を持っていない** (`setPortForwardingL` / `R` だけ)。待ち受けと SOCKS5 の
 * やりとりはここで書き、繋ぐところだけ `direct-tcpip` チャネルに渡す。
 *
 * ⚠ **名前解決は向こう側でやる** — ドメイン名は解決せずにそのままチャネルへ渡す
 * (`curl` でいう `--socks5-hostname`)。こちらで引くと、繋いだ先からしか引けない名前が
 * 使えないうえ、どこへ行こうとしているかが端末側の DNS に漏れる。
 */
class SocksProxy private constructor(
    private val session: Session,
    private val server: ServerSocket,
) : AutoCloseable {

    @Volatile private var closed = false

    /** 実際に割り当てられた待ち受けポート (0 を指定したときは OS が選ぶ)。 */
    val localPort: Int get() = server.localPort

    init {
        Thread({ acceptLoop() }, "socks-$localPort").apply { isDaemon = true }.start()
    }

    private fun acceptLoop() {
        while (!closed) {
            val socket = try {
                server.accept()
            } catch (e: IOException) {
                break // close() で閉じたか、もう待ち受けられない
            }
            Thread({ serve(socket) }, "socks-conn-$localPort").apply { isDaemon = true }.start()
        }
    }

    private fun serve(socket: Socket) {
        var channel: ChannelDirectTCPIP? = null
        try {
            socket.tcpNoDelay = true
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            if (!readGreeting(input)) {
                output.write(byteArrayOf(SOCKS5.toByte(), METHOD_NONE.toByte()))
                output.flush()
                return
            }
            output.write(byteArrayOf(SOCKS5.toByte(), METHOD_NO_AUTH.toByte()))
            output.flush()

            val target = try {
                readConnect(input)
            } catch (e: SocksFailure) {
                output.write(socksReply(e.code))
                output.flush()
                return
            }

            val ch = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel = ch
            ch.setHost(target.host)
            ch.setPort(target.port)
            // ⚠ **繋ぐ前に取る。** JSch はここで渡したパイプを connect() のときに繋ぎ込む。
            val fromRemote = ch.inputStream
            val toRemote = ch.outputStream
            try {
                ch.connect(CONNECT_TIMEOUT_MS)
            } catch (e: Exception) {
                // ⛔ 開けなかったことを「開けた」と返さない。ここを楽観的に返すと、繋がらない
                // 相手への接続が**クライアント側では成功に見えて**、その後で黙って切れる。
                Log.w(TAG, "socks connect failed (${HostAddress.hostPort(target.host, target.port)}): ${e.message}")
                output.write(socksReply(REP_HOST_UNREACHABLE))
                output.flush()
                return
            }

            output.write(socksReply(REP_OK))
            output.flush()

            // 下り (相手 → 使う側) は別スレッド、上り (使う側 → 相手) はこのスレッドで回す。
            val down = Thread({
                runCatching { pump(fromRemote, output) }
                runCatching { socket.close() }
            }, "socks-down-$localPort").apply { isDaemon = true }
            down.start()
            runCatching { pump(input, toRemote) }
            down.join(DRAIN_MS)
        } catch (e: Exception) {
            Log.w(TAG, "socks session ended: ${e.message}")
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { socket.close() }
        }
    }

    /** ⚠ 待ち受けを閉じるだけ。**開いている接続は SSH セッションを畳むときに一緒に落ちる。** */
    override fun close() {
        closed = true
        runCatching { server.close() }
    }

    companion object {
        private const val TAG = "SocksProxy"
        private const val BACKLOG = 32
        private const val CONNECT_TIMEOUT_MS = 15_000

        /** 上りが終わったあと、下りが流し終わるのを待つ上限。 */
        private const val DRAIN_MS = 3_000L

        /**
         * 待ち受けを開く。⚠ **ポートが塞がっていれば例外**が出る (張れなかった転送として扱う)。
         */
        fun start(session: Session, bindAddress: String, port: Int): SocksProxy {
            val bind = InetAddress.getByName(HostAddress.normalize(bindAddress))
            return SocksProxy(session, ServerSocket(port, BACKLOG, bind))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// SOCKS5 (RFC 1928) のやりとり。⚠ **ここだけは純粋なバイト列の読み書き**にして、テストから
// 直接呼べるようにしてある (実際に繋がないと確かめられない形にすると、誰も確かめない)。
// ------------------------------------------------------------------------------------------------

internal const val SOCKS5 = 0x05
internal const val METHOD_NO_AUTH = 0x00
internal const val METHOD_NONE = 0xFF
internal const val CMD_CONNECT = 0x01
internal const val ATYP_IPV4 = 0x01
internal const val ATYP_DOMAIN = 0x03
internal const val ATYP_IPV6 = 0x04
internal const val REP_OK = 0x00
internal const val REP_CMD_NOT_SUPPORTED = 0x07
internal const val REP_ATYP_NOT_SUPPORTED = 0x08
internal const val REP_HOST_UNREACHABLE = 0x04

/** 1 接続ぶんの宛先。 */
internal data class SocksTarget(val host: String, val port: Int)

/** 返す側の失敗。[code] はそのまま SOCKS5 の応答コードになる。 */
internal class SocksFailure(val code: Int, message: String) : IOException(message)

/**
 * 最初の挨拶を読む。認証なしで進めてよければ true。
 *
 * ⚠ **SOCKS4 は受けない** — 版が 5 でなければ false を返す。挨拶の形自体が違うので、
 * 4 のつもりで送られたものを 5 として読み進めると、後ろのバイトの意味が丸ごとずれる。
 */
internal fun readGreeting(input: InputStream): Boolean {
    if (input.readByte() != SOCKS5) return false
    val count = input.readByte()
    val methods = ByteArray(count)
    input.readFully(methods)
    return methods.any { (it.toInt() and 0xFF) == METHOD_NO_AUTH }
}

/**
 * CONNECT 要求を読み、宛先を返す。
 *
 * ⚠ **ドメイン名は解決しない**。名前のまま返して向こう側で引かせる (クラスの説明を参照)。
 */
internal fun readConnect(input: InputStream): SocksTarget {
    if (input.readByte() != SOCKS5) throw SocksFailure(REP_CMD_NOT_SUPPORTED, "not SOCKS5")
    val command = input.readByte()
    input.readByte() // RSV
    val type = input.readByte()
    if (command != CMD_CONNECT) {
        // BIND / UDP ASSOCIATE は受けない。⚠ **宛先を読み飛ばしてから**断る — 読み残すと
        // 次の読み手が要求の途中から読み始めることになる。
        skipAddress(input, type)
        input.readByte(); input.readByte()
        throw SocksFailure(REP_CMD_NOT_SUPPORTED, "command $command")
    }
    val host = when (type) {
        ATYP_IPV4 -> ByteArray(4).also { input.readFully(it) }.joinToString(".") { (it.toInt() and 0xFF).toString() }
        ATYP_DOMAIN -> {
            val length = input.readByte()
            val name = ByteArray(length)
            input.readFully(name)
            String(name, Charsets.US_ASCII)
        }
        ATYP_IPV6 -> InetAddress.getByAddress(ByteArray(16).also { input.readFully(it) }).hostAddress.orEmpty()
        else -> throw SocksFailure(REP_ATYP_NOT_SUPPORTED, "address type $type")
    }
    val port = (input.readByte() shl 8) or input.readByte()
    return SocksTarget(host, port)
}

/**
 * 応答 10 バイト。⚠ **BND.ADDR / BND.PORT は 0 で返す** — 使う側がここを見て繋ぎ直すのは
 * BIND のときだけで、CONNECT では読まれない。
 */
internal fun socksReply(code: Int): ByteArray = byteArrayOf(
    SOCKS5.toByte(), code.toByte(), 0, ATYP_IPV4.toByte(), 0, 0, 0, 0, 0, 0
)

private fun skipAddress(input: InputStream, type: Int) {
    when (type) {
        ATYP_IPV4 -> input.readFully(ByteArray(4))
        ATYP_DOMAIN -> input.readFully(ByteArray(input.readByte()))
        ATYP_IPV6 -> input.readFully(ByteArray(16))
    }
}

/** 1 バイト読む。⚠ 途中で切れたら例外にする (0 として読み進めると意味が変わる)。 */
private fun InputStream.readByte(): Int {
    val b = read()
    if (b < 0) throw IOException("socks: stream ended")
    return b
}

private fun InputStream.readFully(buffer: ByteArray) {
    var done = 0
    while (done < buffer.size) {
        val n = read(buffer, done, buffer.size - done)
        if (n < 0) throw IOException("socks: stream ended")
        done += n
    }
}

private fun pump(from: InputStream, to: OutputStream) {
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val n = from.read(buffer)
        if (n < 0) break
        to.write(buffer, 0, n)
        to.flush()
    }
}
