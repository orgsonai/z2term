package com.zerotoship.z2term.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * ネットワーク越しのシリアル (1.0.209): 中継器 (ここではループバックのサーバー) との素通しと、つなぎ直し。
 * ⚠ 保存の形 ([SerialConfig.toJson]) はここでは確かめられない (単体試験の org.json は Android の空の版で、値を返さない)。
 */
class TcpSerialChannelTest {

    @Test
    fun passesBytesBothWaysUnchanged() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val events = CopyOnWriteArrayList<TcpSerialChannel.Event>()
            val ch = TcpSerialChannel("127.0.0.1", server.localPort) { events += it }
            ch.start()
            try {
                server.soTimeout = 5_000
                val peer = server.accept()
                waitUntil { ch.connected }
                // 0xFF・NUL・ESC も手を加えずに通す (telnet の取り決めは使わない)。
                val fromDevice = byteArrayOf(0x41, 0xFF.toByte(), 0x00, 0x1B, 0x0A)
                peer.getOutputStream().apply { write(fromDevice); flush() }
                val got = ByteArray(fromDevice.size)
                var n = 0
                while (n < got.size) n += ch.reader.read(got, n, got.size - n)
                assertTrue(fromDevice.contentEquals(got))
                assertTrue(fromDevice.contentEquals(ch.transcript.tail(100)))

                ch.writer.write(byteArrayOf(0x03, 0x0D))
                ch.writer.flush()
                val echoed = ByteArray(2)
                peer.getInputStream().read(echoed)
                assertTrue(byteArrayOf(0x03, 0x0D).contentEquals(echoed))
                assertEquals("127.0.0.1:${server.localPort}", ch.deviceLabel)
                peer.close()
            } finally {
                ch.close()
            }
        }
    }

    @Test
    fun reconnectsAfterTheRelayDropsTheLine() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val events = CopyOnWriteArrayList<TcpSerialChannel.Event>()
            val ch = TcpSerialChannel("127.0.0.1", server.localPort) { events += it }
            ch.start()
            try {
                server.soTimeout = 10_000
                server.accept().close()
                waitUntil { events.any { it == TcpSerialChannel.Event.Lost } }
                assertFalse(ch.connected)
                assertTrue(ch.isAlive)
                // RETRY_MS 後にもう一度つないでくる。
                server.accept().use { waitUntil { ch.connected } }
                assertEquals(2, events.count { it is TcpSerialChannel.Event.Connected })
            } finally {
                ch.close()
            }
        }
    }

    @Test
    fun aResetAfterConnectingIsReportedAsLostNotFailed() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val events = CopyOnWriteArrayList<TcpSerialChannel.Event>()
            val ch = TcpSerialChannel("127.0.0.1", server.localPort) { events += it }
            ch.start()
            try {
                server.soTimeout = 5_000
                // 中継器の再起動と同じ形: つながった後に RST で切られる。
                server.accept().apply { setSoLinger(true, 0) }.close()
                waitUntil { events.any { it == TcpSerialChannel.Event.Lost } }
                assertFalse(events.any { it is TcpSerialChannel.Event.Failed })
            } finally {
                ch.close()
            }
        }
    }

    private fun waitUntil(timeoutMs: Long = 8_000, check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!check()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out")
            Thread.sleep(20)
        }
    }
}
