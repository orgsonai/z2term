package com.zerotoship.z2term.gui.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RdpTargetTest {
    @Test
    fun desktopSizeIsLandscapeAndRoundedToFour() {
        // 縦持ちの端末でも横長で要求する (GUI タブは横で使う)。1080 は 4 の倍数、2337 は丸める。
        assertEquals(2336 to 1080, RdpTarget.fitDesktopSize(1080, 2337))
        assertEquals(2336 to 1080, RdpTarget.fitDesktopSize(2337, 1080))
    }

    @Test
    fun desktopSizeStaysInsideTheRangeTheServerAccepts() {
        // 極端に小さい / 大きい画面でも、相手が拒否する大きさは出さない。
        assertEquals(640 to 640, RdpTarget.fitDesktopSize(320, 200))
        assertEquals(4096 to 4096, RdpTarget.fitDesktopSize(8000, 6000))
    }

    @Test
    fun labelFallsBackToTheEndpoint() {
        val named = RdpTarget(host = "10.0.0.5", user = "user", name = "office")
        val unnamed = RdpTarget(host = "10.0.0.5", user = "user")

        assertEquals("office", named.label)
        assertEquals("10.0.0.5:3389", unnamed.label)
    }

    @Test
    fun trustKeyIsIndependentOfTheForwardedLocalEndpoint() {
        // SSH 転送中の接続先は 127.0.0.1:<毎回変わるポート>。証明書を覚える名前がそれに
        // 引きずられると、次の接続で必ず「初めての相手」になってしまう。
        val forwarded = RdpTarget(
            host = "127.0.0.1",
            port = 41234,
            user = "user",
            trustKey = "desktop.internal:3389",
        )

        assertEquals("desktop.internal:3389", forwarded.trustKey)
        assertNotEquals(forwarded.label, forwarded.trustKey)
    }

    @Test
    fun defaultTrustKeyIsTheDirectEndpoint() {
        assertEquals("10.0.0.5:3389", RdpTarget(host = "10.0.0.5", user = "user").trustKey)
        assertEquals(
            "[fd00::20]:3390",
            RdpTarget(host = "fd00::20", port = 3390, user = "user").trustKey,
        )
    }

    @Test
    fun transportIsClosedOnlyOnce() {
        var closes = 0
        val target = RdpTarget(host = "10.0.0.5", user = "user", transportCloser = { closes++ })

        target.closeTransport()
        target.closeTransport()

        assertEquals(1, closes)
    }
}
