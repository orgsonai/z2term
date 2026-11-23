package com.zerotoship.z2term.channel

import android.util.Log
import com.zerotoship.z2term.net.HostAddress
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue

/**
 * ネットワーク越しのシリアル (1.0.209)。相手の UART を TCP へ素通しにしている中継器
 * (Wi-Fi のマイコン、ser2net の raw の口など) へつなぐ。届いたバイトも送るバイトも手を加えない。
 *
 * USB ([SerialChannel]) と同じく**タブの寿命と接続の寿命を分ける**。切れてもタブと画面の文字は残り、
 * [RETRY_MS] ごとにつなぎ直す (中継器の再起動や Wi-Fi の瞬断で、タブを開き直させないため)。
 * 速さ・パリティなどは中継器の側で決まるので、ここでは持たない。
 *
 * 状態の変化は 1 本のスレッド ([loop]) だけで起こすので、知らせ ([onEvent]) も順に届く。
 */
class TcpSerialChannel(
    private val host: String,
    private val port: Int,
    private val onEvent: (Event) -> Unit,
) : SerialLink {

    sealed class Event {
        data class Connecting(val target: String) : Event()
        data class Connected(val target: String) : Event()
        object Lost : Event()
        data class Failed(val message: String) : Event()
    }

    override val transcript = SerialTranscript()

    @Volatile private var socket: Socket? = null
    @Volatile private var closed = false

    override val connected: Boolean get() = socket != null
    override val deviceLabel: String? get() = if (socket != null) target else null

    private val target = HostAddress.hostPort(host, port)
    private val incoming = LinkedBlockingQueue<ByteArray>()

    override val reader: InputStream = object : InputStream() {
        private var chunk: ByteArray = ByteArray(0)
        private var pos = 0

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= chunk.size) {
                val next = incoming.take()
                if (next === EOF) {
                    incoming.offer(EOF)
                    return -1
                }
                chunk = next
                pos = 0
            }
            val n = minOf(len, chunk.size - pos)
            System.arraycopy(chunk, pos, b, off, n)
            pos += n
            return n
        }
    }

    override val writer: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            // ⚠ 切れている間に打った分は捨てる (USB と同じ。つなぎ直したときに古い入力を流さない)。
            val s = socket ?: return
            try {
                s.getOutputStream().write(b, off, len)
            } catch (e: IOException) {
                Log.w(TAG, "write failed: ${e.message}")
            }
        }

        override fun flush() {
            runCatching { socket?.getOutputStream()?.flush() }
        }
    }

    override val isAlive: Boolean get() = !closed
    override val exitCode: Int? get() = if (closed) 0 else null

    /** 素通しの TCP には画面の大きさを伝える仕組みが無い (相手側で `stty` 等を使う)。 */
    override fun resize(rows: Int, cols: Int) = Unit

    private val loop = Thread({ run() }, "serial-tcp").apply { isDaemon = true }

    fun start() = loop.start()

    override fun close() {
        if (closed) return
        closed = true
        runCatching { socket?.close() }
        loop.interrupt()
        incoming.offer(EOF)
    }

    private fun run() {
        var lastFailure: String? = null
        while (!closed) {
            if (lastFailure == null) emit(Event.Connecting(target))
            val s = Socket()
            // つながった後の失敗 (相手からの RST など) は「切れた」。「つなげない」はつなぐ前の失敗だけ。
            var opened = false
            try {
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket = s
                opened = true
                lastFailure = null
                emit(Event.Connected(target))
                val input = s.getInputStream()
                val buf = ByteArray(4096)
                while (!closed) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) {
                        val data = buf.copyOf(n)
                        transcript.append(data)
                        incoming.offer(data)
                    }
                }
                socket = null
                runCatching { s.close() }
                if (!closed) emit(Event.Lost)
            } catch (e: IOException) {
                socket = null
                runCatching { s.close() }
                if (closed) break
                if (opened) {
                    emit(Event.Lost)
                } else {
                    // 同じ失敗を 3 秒ごとに並べない (中継器の電源が切れている間、画面が埋まる)。
                    val message = e.message ?: e.javaClass.simpleName
                    if (message != lastFailure) emit(Event.Failed(message))
                    lastFailure = message
                }
            }
            try {
                Thread.sleep(RETRY_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun emit(event: Event) {
        runCatching { onEvent(event) }
    }

    companion object {
        private const val TAG = "TcpSerialChannel"
        private const val CONNECT_TIMEOUT_MS = 5_000
        const val RETRY_MS = 3_000L
        private val EOF = ByteArray(0)
    }
}
