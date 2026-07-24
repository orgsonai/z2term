package com.zerotoship.z2term.service

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
}
