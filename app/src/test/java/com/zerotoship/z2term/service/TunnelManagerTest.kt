package com.zerotoship.z2term.service

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
}
