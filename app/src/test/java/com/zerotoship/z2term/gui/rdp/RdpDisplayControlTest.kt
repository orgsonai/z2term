package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpDisplayControlTest {
    @Test
    fun sizeRequestedBeforeCapsIsHeldAndSentWhenCapsArrive() {
        val sent = mutableListOf<ByteArray>()
        val display = RdpDisplayControl(sent::add)

        display.requestSize(2400, 1080)
        assertTrue("CAPS の前に送ってはいけない", sent.isEmpty())

        display.accept(caps(maxMonitors = 1, factorA = 8192, factorB = 8192))

        val layout = sent.single()
        assertEquals(TYPE_MONITOR_LAYOUT, le32(layout, 0))
        assertEquals(layout.size, le32(layout, 4))
        assertEquals(56, layout.size) // header 8 + MonitorLayoutSize/NumMonitors 8 + monitor 40
        assertEquals(40, le32(layout, 8))
        assertEquals(1, le32(layout, 12))
        assertEquals(1, le32(layout, 16)) // Flags = PRIMARY
        assertEquals(0, le32(layout, 20)) // Left
        assertEquals(0, le32(layout, 24)) // Top
        assertEquals(2400, le32(layout, 28))
        assertEquals(1080, le32(layout, 32))
        assertEquals(0, le32(layout, 36)) // PhysicalWidth は知らせない
        assertEquals(0, le32(layout, 40)) // PhysicalHeight
        assertEquals(0, le32(layout, 44)) // Orientation
        assertEquals(100, le32(layout, 48)) // DesktopScaleFactor
        assertEquals(100, le32(layout, 52)) // DeviceScaleFactor
    }

    /** ⭐ 保留するのは最後の 1 つだけ。回転が続けて起きても、落ち着いた大きさだけを要求する。 */
    @Test
    fun onlyTheLastHeldSizeIsSent() {
        val sent = mutableListOf<ByteArray>()
        val display = RdpDisplayControl(sent::add)

        display.requestSize(1280, 720)
        display.requestSize(1920, 1080)
        display.accept(caps())

        assertEquals(1, sent.size)
        assertEquals(1920, le32(sent.single(), 28))
        assertEquals(1080, le32(sent.single(), 32))
    }

    @Test
    fun sameSizeIsNotRequestedTwice() {
        val sent = mutableListOf<ByteArray>()
        val display = RdpDisplayControl(sent::add)
        display.accept(caps())

        display.requestSize(1920, 1080)
        display.requestSize(1920, 1080)
        assertEquals(1, sent.size)

        display.requestSize(1600, 900)
        assertEquals(2, sent.size)
    }

    /** [MS-RDPEDISP] 2.2.2.2.1 は幅が偶数であることを求め、200〜8192 の外を許さない。 */
    @Test
    fun sizeIsClampedToWhatThePeerAccepts() {
        val sent = mutableListOf<ByteArray>()
        val display = RdpDisplayControl(sent::add)
        display.accept(caps())

        display.requestSize(1367, 769)
        assertEquals(1366, le32(sent.last(), 28))
        assertEquals(768, le32(sent.last(), 32))

        display.requestSize(10, 100000)
        assertEquals(200, le32(sent.last(), 28))
        assertEquals(8192, le32(sent.last(), 32))
    }

    @Test
    fun areaBeyondTheAdvertisedLimitIsNotSent() {
        val sent = mutableListOf<ByteArray>()
        val display = RdpDisplayControl(sent::add)
        display.accept(caps(factorA = 1024, factorB = 768))

        display.requestSize(3840, 2160)
        assertTrue("面積の上限を超える要求は送らない", sent.isEmpty())

        display.requestSize(1024, 768)
        assertEquals(1, sent.size)
    }

    /** ⛔ 知らない PDU でチャネルを落とさない (落とすと resize が二度とできなくなる)。 */
    @Test
    fun unknownPduIsSkippedAndCapsAfterItStillCounts() {
        val sent = mutableListOf<ByteArray>()
        val display = RdpDisplayControl(sent::add)
        display.requestSize(1920, 1080)

        val message = ByteArrayOutputStream().apply {
            write(pdu(type = 0x0F, body = ByteArray(4)))
            write(caps())
        }.toByteArray()
        display.accept(message)

        assertEquals(1920, le32(sent.single(), 28))
    }

    @Test
    fun truncatedPduLengthIsRejected() {
        val display = RdpDisplayControl { }
        val broken = ByteArrayOutputStream().apply {
            write(le32Bytes(TYPE_CAPS))
            write(le32Bytes(64)) // 実際の長さより大きい
            write(ByteArray(12))
        }.toByteArray()

        assertThrows(IOException::class.java) { display.accept(broken) }
    }

    /** チャネルが閉じたら CAPS からやり直す。閉じた後の要求は次の CAPS まで保留される。 */
    @Test
    fun resetRequiresCapsAgain() {
        val sent = mutableListOf<ByteArray>()
        val display = RdpDisplayControl(sent::add)
        display.accept(caps())
        display.requestSize(1920, 1080)
        assertEquals(1, sent.size)

        display.reset()
        display.requestSize(1280, 720)
        assertEquals("CAPS 前なので保留する", 1, sent.size)

        display.accept(caps())
        assertEquals(2, sent.size)
        assertEquals(1280, le32(sent.last(), 28))
    }

    /** 中身の無いメッセージで例外も送信も起こさない (送ったらラムダが落とす)。 */
    @Test
    fun emptyMessageIsIgnored() {
        val display = RdpDisplayControl { throw AssertionError("送信してはいけない") }
        display.accept(ByteArray(0))
    }

    private fun caps(maxMonitors: Int = 1, factorA: Int = 8192, factorB: Int = 8192): ByteArray =
        pdu(TYPE_CAPS, ByteArrayOutputStream().apply {
            write(le32Bytes(maxMonitors))
            write(le32Bytes(factorA))
            write(le32Bytes(factorB))
        }.toByteArray())

    private fun pdu(type: Int, body: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(le32Bytes(type))
        write(le32Bytes(8 + body.size))
        write(body)
    }.toByteArray()

    private fun le32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun le32Bytes(value: Int) = ByteArray(4) { (value ushr (it * 8)).toByte() }

    private companion object {
        const val TYPE_MONITOR_LAYOUT = 0x02
        const val TYPE_CAPS = 0x05
    }
}
