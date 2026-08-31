package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 通信量の上限 (0.8.388) の判定。**止めてよい相手かどうか**と**いつから数え直すか**を固定する。
 *
 * ⚠ ここを間違えると、**家の中の機器へ繋げなくなる**か、**上限を過ぎても止まらない**。
 * どちらも利用者からは「壊れている」としか見えない。
 */
class NetGuardTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun at(y: Int, mon: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(y, mon - 1, d, h, min, 0)
        }.timeInMillis

    // --- 家の中かどうか ---

    @Test fun privateIpv4RangesAreLocal() {
        assertTrue(NetGuard.isPrivateLiteral("192.168.10.20"))
        assertTrue(NetGuard.isPrivateLiteral("10.0.0.1"))
        assertTrue(NetGuard.isPrivateLiteral("172.16.0.1"))
        assertTrue(NetGuard.isPrivateLiteral("172.31.255.254"))
        assertTrue(NetGuard.isPrivateLiteral("127.0.0.1"))
        // 169.254.x は端末どうしを直結したときのアドレス (ここも通信料はかからない)。
        assertTrue(NetGuard.isPrivateLiteral("169.254.1.1"))
    }

    @Test fun publicIpv4IsNotLocal() {
        assertFalse(NetGuard.isPrivateLiteral("8.8.8.8"))
        // ⚠ 172 でも 16-31 の外は外部。ここを雑に見ると外への接続が素通りする。
        assertFalse(NetGuard.isPrivateLiteral("172.15.0.1"))
        assertFalse(NetGuard.isPrivateLiteral("172.32.0.1"))
        assertFalse(NetGuard.isPrivateLiteral("192.169.0.1"))
    }

    @Test fun ipv6LocalForms() {
        assertTrue(NetGuard.isPrivateLiteral("::1"))
        assertTrue(NetGuard.isPrivateLiteral("[fe80::1]"))
        assertTrue(NetGuard.isPrivateLiteral("fd00::1234"))
        assertFalse(NetGuard.isPrivateLiteral("2001:4860:4860::8888"))
    }

    @Test fun localNames() {
        assertTrue(NetGuard.isLocalName("localhost"))
        assertTrue(NetGuard.isLocalName("nas.local"))
        assertTrue(NetGuard.isLocalName("pi.lan"))
        // ドットを含まない一語の名前は LAN の相手 (外の名前にはならない)。
        assertTrue(NetGuard.isLocalName("myserver"))
        // IPv6 はドットを含まなくても「一語の LAN 名」ではない。
        assertFalse(NetGuard.isLocalName("2001:4860:4860::8888"))
        assertFalse(NetGuard.isLocalName("[2001:4860:4860::8888]"))
        assertFalse(NetGuard.isLocalName("example.com"))
        assertFalse(NetGuard.isLocalName("github.com"))
    }

    // --- いつから数え直すか ---

    @Test fun periodStart_isThisMonthWhenTheDayHasPassed() {
        // 締め日 1 日。8/23 にいるなら 8/1 0:00 から数えている。
        assertEquals(at(2026, 8, 1, 0, 0), NetGuard.periodStart(at(2026, 8, 23, 12, 0), 1, utc))
    }

    @Test fun periodStart_isLastMonthWhenTheDayHasNotComeYet() {
        // 締め日 20 日。8/5 にいるなら、まだ 7/20 からの期間の途中。
        assertEquals(at(2026, 7, 20, 0, 0), NetGuard.periodStart(at(2026, 8, 5, 12, 0), 20, utc))
    }

    @Test fun periodStart_clampsTheDayTo28() {
        // 31 日は無い月があるので 28 に丸める (丸めなければ 2 月だけ区切りが飛ぶ)。
        assertEquals(at(2026, 2, 28, 0, 0), NetGuard.periodStart(at(2026, 3, 1, 12, 0), 31, utc))
    }

    // --- 見せ方 ---

    @Test fun formatBytes_switchesToGbAtAThousandAndTwentyFour() {
        assertEquals("340 MB", NetGuard.formatBytes(340L * 1024 * 1024))
        assertEquals("1.0 GB", NetGuard.formatBytes(1024L * 1024 * 1024))
        assertEquals("2.9 GB", NetGuard.formatBytes(3000L * 1024 * 1024))
    }

    @Test fun stepIndex_picksTheNearestStep() {
        // 保存済みの値がちょうど段の上に無くても、つまみは近い段に着く。
        assertEquals(NetGuard.LIMIT_STEPS_MB.indexOf(3_000), NetGuard.stepIndexOf(3_000))
        assertEquals(NetGuard.LIMIT_STEPS_MB.indexOf(1_000), NetGuard.stepIndexOf(1_100))
        assertEquals(NetGuard.LIMIT_STEPS_MB.indexOf(100), NetGuard.stepIndexOf(1))
        assertEquals(NetGuard.LIMIT_STEPS_MB.indexOf(50_000), NetGuard.stepIndexOf(99_999))
    }

    // --- 止めるかどうか ---

    @Test fun blocking_isOffWhileOnWifiIfWifiIsExempt() {
        val st = NetGuard.Status(
            enabled = true, measurable = true,
            usedBytes = 4L * 1024 * 1024 * 1024, limitBytes = 3L * 1024 * 1024 * 1024,
            periodStart = 0, onWifi = true, wifiExempt = true
        )
        assertTrue(st.over)
        // ⚠ 超えていても、モバイルを使っていないなら止める理由がない。
        assertFalse(st.blocking)
    }

    @Test fun blocking_isOnOverMobile() {
        val st = NetGuard.Status(
            enabled = true, measurable = true,
            usedBytes = 4L * 1024 * 1024 * 1024, limitBytes = 3L * 1024 * 1024 * 1024,
            periodStart = 0, onWifi = false, wifiExempt = true
        )
        assertTrue(st.blocking)
    }

    @Test fun blocking_neverHappensWhenUsageCannotBeRead() {
        // 測れない端末で止めると、直しようのない締め出しになる。
        val st = NetGuard.Status(
            enabled = true, measurable = false,
            usedBytes = 0, limitBytes = 3L * 1024 * 1024 * 1024,
            periodStart = 0, onWifi = false, wifiExempt = true
        )
        assertFalse(st.over)
        assertFalse(st.blocking)
    }
}
