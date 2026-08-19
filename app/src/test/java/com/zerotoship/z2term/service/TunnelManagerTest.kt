package com.zerotoship.z2term.service

import com.zerotoship.z2term.channel.PortForward
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 常駐トンネル (A2) の再接続バックオフ ([TunnelManager.backoffMs]) の検証。
 *
 * ここが効かないと、回線が落ちている間に**総当たりで再接続を撃ち続けて電池を焼く**。
 * 逆に伸びすぎると復帰が遅い。境界を具体値で押さえる。
 */
class TunnelManagerTest {

    @Test fun firstRetryUsesTheBaseDelay() {
        assertEquals(TunnelManager.BACKOFF_BASE_MS, TunnelManager.backoffMs(0))
    }

    @Test fun delayDoubles() {
        assertEquals(10_000L, TunnelManager.backoffMs(1))
        assertEquals(20_000L, TunnelManager.backoffMs(2))
        assertEquals(40_000L, TunnelManager.backoffMs(3))
    }

    @Test fun delayIsCapped() {
        assertEquals(TunnelManager.BACKOFF_MAX_MS, TunnelManager.backoffMs(10))
        // 何回失敗しても上限を超えない (オーバーフローで負にならないことも見る)。
        assertEquals(TunnelManager.BACKOFF_MAX_MS, TunnelManager.backoffMs(1000))
        assertTrue(TunnelManager.backoffMs(Int.MAX_VALUE) > 0)
    }

    @Test fun negativeIsTreatedAsFirst() {
        assertEquals(TunnelManager.BACKOFF_BASE_MS, TunnelManager.backoffMs(-1))
    }

    // ---- keepalive (0.8.367) ----------------------------------------------------------------
    // ⭐ この間隔が「スマホが LAN から消える」の根治そのもの。実測で 10 秒ごとに送ると
    //    届かない率が 37% → 1% に落ちた。**下手に広げると症状が戻る**ので値を固定で押さえる。

    @Test fun keepAliveIsTenSecondsByDefault() {
        assertEquals(10_000, TunnelManager.keepAliveMs(lowPower = false))
    }

    @Test fun lowPowerWidensTheInterval() {
        assertTrue(TunnelManager.keepAliveMs(lowPower = true) > TunnelManager.keepAliveMs(lowPower = false))
    }

    /** 切断に気付くまでが再接続の上限待ちより長いと、落ちたまま気付かない時間ができる。 */
    @Test fun dropIsNoticedWellBeforeTheBackoffCap() {
        val noticeMs = TunnelManager.KEEPALIVE_MS.toLong() * TunnelManager.KEEPALIVE_COUNT_MAX
        assertTrue(noticeMs < TunnelManager.BACKOFF_MAX_MS)
        // 1 分以内には気付くこと (これを超えるなら間隔か回数を見直す)。
        assertTrue(noticeMs <= 60_000L)
    }

    // ---- 張れなかった転送の見せ方 -------------------------------------------------------------

    private val forwardL = PortForward(localPort = 8080, remoteHost = "localhost", remotePort = 80)
    private val forwardR = PortForward(
        localPort = 65152, remoteHost = "127.0.0.1", remotePort = 65152, reverse = true
    )

    @Test fun allForwardsUpHasNoCrossMark() {
        val s = TunnelManager.detailOf(listOf(forwardL, forwardR), emptyList())
        assertEquals("${forwardL.describe()} / ${forwardR.describe()}", s)
        assertTrue(!s.contains("✗"))
    }

    /** `-R` は繋ぎ直した直後に弾かれることがある。**張れていない側だけ** ✗ が付くこと。 */
    @Test fun pendingForwardIsMarked() {
        val s = TunnelManager.detailOf(listOf(forwardL, forwardR), listOf(forwardR))
        assertEquals("${forwardL.describe()} / ✗ ${forwardR.describe()}", s)
    }

    @Test fun everyForwardCanBePending() {
        val s = TunnelManager.detailOf(listOf(forwardL), listOf(forwardL))
        assertEquals("✗ ${forwardL.describe()}", s)
    }
}
