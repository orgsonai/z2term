package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [WhenTriggerMatch] の Wi‑Fi トリガー判定を具体例で検証する (A6 `z2-when` stage2)。 */
class WhenTriggerMatchTest {

    private fun m(spec: String, connected: Boolean, ssid: String) =
        WhenTriggerMatch.wifi(spec, connected, ssid)

    @Test fun connect_firesOnlyOnConnect() {
        assertTrue(m("connect", connected = true, ssid = "home"))
        assertFalse(m("connect", connected = false, ssid = ""))
    }

    @Test fun disconnect_firesOnlyOnDisconnect() {
        assertTrue(m("disconnect", connected = false, ssid = ""))
        assertFalse(m("disconnect", connected = true, ssid = "home"))
    }

    @Test fun ssid_matchesOnConnectCaseInsensitive() {
        assertTrue(m("ssid=home", connected = true, ssid = "home"))
        assertTrue(m("ssid=Home", connected = true, ssid = "home")) // 大小文字は区別しない
        assertFalse(m("ssid=home", connected = true, ssid = "office"))
    }

    @Test fun ssid_neverFiresOnDisconnect() {
        assertFalse(m("ssid=home", connected = false, ssid = ""))
    }

    @Test fun ssid_emptyEitherSide_doesNotFire() {
        // 位置情報権限が無く SSID が取れない場合は一致し得ない (誤発火より取りこぼしを選ぶ)。
        assertFalse(m("ssid=home", connected = true, ssid = ""))
        // 空 SSID 指定は常に不一致。
        assertFalse(m("ssid=", connected = true, ssid = "home"))
    }

    @Test fun unknownSpec_doesNotFire() {
        assertFalse(m("whatever", connected = true, ssid = "home"))
        assertFalse(m("", connected = true, ssid = "home"))
    }

    // --- sms ---

    private fun sms(spec: String, from: String, body: String) =
        WhenTriggerMatch.sms(spec, from, body)

    @Test fun sms_any_alwaysFires() {
        assertTrue(sms("any", from = "+81", body = "hi"))
        assertTrue(sms("any", from = "", body = ""))
    }

    @Test fun sms_from_substringCaseInsensitive() {
        assertTrue(sms("from=bank", from = "MyBank", body = "..."))
        assertTrue(sms("from=1234", from = "+81901234", body = "..."))
        assertFalse(sms("from=bank", from = "Shop", body = "..."))
        assertFalse(sms("from=", from = "MyBank", body = "...")) // 空指定は不一致
    }

    @Test fun sms_contains_substringCaseInsensitive() {
        assertTrue(sms("contains=code", from = "x", body = "Your CODE is 1234"))
        assertFalse(sms("contains=code", from = "x", body = "no match here"))
    }

    @Test fun sms_otp_firesOnlyWhenCodePresent() {
        assertTrue(sms("otp", from = "x", body = "Your code is 483920"))
        assertFalse(sms("otp", from = "x", body = "Welcome! No code in this one."))
    }

    @Test fun extractOtp_findsFourToEightDigits() {
        assertEquals("483920", WhenTriggerMatch.extractOtp("Your code is 483920. Do not share."))
        assertEquals("12345", WhenTriggerMatch.extractOtp("G-12345 is your code")) // G- の後ろも拾う
        assertEquals("8842", WhenTriggerMatch.extractOtp("Verification: 8842"))
    }

    @Test fun extractOtp_ignoresLongDigitRuns() {
        // 9 桁以上 (電話番号・注文番号) は OTP として拾わない。
        assertEquals("", WhenTriggerMatch.extractOtp("Order #1234567890 has shipped"))
        assertEquals("", WhenTriggerMatch.extractOtp("Call +8190123456789 now"))
        // 3 桁以下も拾わない。
        assertEquals("", WhenTriggerMatch.extractOtp("Room 12 is ready"))
        // コードが無ければ空。
        assertEquals("", WhenTriggerMatch.extractOtp("Thanks for signing up!"))
    }
}
