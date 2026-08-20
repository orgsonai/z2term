package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `z2-when` の絞り込み ([WhenGuard]) の判定。日跨ぎ・否定・数値比較・書き間違いの扱いなど、
 * **間違えると「ルールが永久に動かない」に直結する**ところを押さえる。
 */
class WhenGuardTest {

    private val state = mapOf(
        "screen" to "off",
        "locked" to "true",
        "charging" to "false",
        "wifi" to "true",
        "ssid" to "Home",
        "level" to "45",
        "ringer" to "silent",
        "temp" to "31.5",
    )

    // --- if_any= (0.8.372) ---

    @Test
    fun `if_any は空なら絞らない`() {
        // ⚠ ここを false にすると、if_any= を書いていない今までのルールが全部止まる。
        assertTrue(WhenGuard.anyConditionMet("", state))
        assertTrue(WhenGuard.anyConditionMet("  ,  ", state))
    }

    @Test
    fun `if_any はどれか 1 つ成り立てば通る`() {
        // charging は false、wifi は true → どれかなので通る。
        assertTrue(WhenGuard.anyConditionMet("charging,wifi", state))
        assertTrue(WhenGuard.anyConditionMet("ssid=Home,charging", state))
        // 全部外れたときだけ落ちる。
        assertFalse(WhenGuard.anyConditionMet("charging,screen", state))
        assertFalse(WhenGuard.anyConditionMet("ssid=Office", state))
    }

    @Test
    fun `if_any でも否定と数値比較が同じに効く`() {
        assertTrue(WhenGuard.anyConditionMet("!charging", state))
        assertTrue(WhenGuard.anyConditionMet("level<50,charging", state))
        assertFalse(WhenGuard.anyConditionMet("level>50,!wifi", state))
    }

    @Test
    fun `if と if_any は同じ語彙で検査される`() {
        // 打ち間違いは書いた瞬間に弾く。どちらの欄でも同じ判定を使う。
        assertNotNull(WhenGuard.conditionError("wifi,nosuchkey", field = "if_any"))
        assertNull(WhenGuard.conditionError("wifi,charging", field = "if_any"))
    }

    // --- if= ---

    @Test
    fun `空の条件は常に通る`() {
        assertTrue(WhenGuard.conditionsMet("", state))
        assertTrue(WhenGuard.conditionsMet("  ,  ", state))
    }

    @Test
    fun `真偽の条件`() {
        assertTrue(WhenGuard.conditionsMet("wifi", state))
        assertFalse(WhenGuard.conditionsMet("charging", state))
        // screen は on/off で返るので、裸の screen は off を偽として読む。
        assertFalse(WhenGuard.conditionsMet("screen", state))
        assertTrue(WhenGuard.conditionsMet("!screen", state))
    }

    @Test
    fun `カンマは AND`() {
        assertTrue(WhenGuard.conditionsMet("wifi,!screen", state))
        assertFalse(WhenGuard.conditionsMet("wifi,charging", state))
    }

    @Test
    fun `一致と数値比較`() {
        assertTrue(WhenGuard.conditionsMet("ssid=Home", state))
        assertTrue(WhenGuard.conditionsMet("ssid=home", state))   // 大小文字は区別しない
        assertFalse(WhenGuard.conditionsMet("ssid=Cafe", state))
        assertTrue(WhenGuard.conditionsMet("level<50", state))
        assertFalse(WhenGuard.conditionsMet("level>50", state))
        assertTrue(WhenGuard.conditionsMet("temp>30", state))     // 小数も比較できる
        assertTrue(WhenGuard.conditionsMet("!level>50", state))
    }

    @Test
    fun `知らないキーと取れなかった値は不成立`() {
        assertFalse(WhenGuard.conditionsMet("bogus", state))
        assertFalse(WhenGuard.conditionsMet("bogus=1", state))
        // 値が空 (SSID が権限不足で取れない等) のときに = が通ってしまわないこと。
        assertFalse(WhenGuard.conditionsMet("ssid=Home", state + ("ssid" to "")))
    }

    @Test
    fun `書式の検査`() {
        assertNull(WhenGuard.conditionError(""))
        assertNull(WhenGuard.conditionError("wifi,!screen,level<30"))
        assertNotNull(WhenGuard.conditionError("wifii"))
        assertNotNull(WhenGuard.conditionError("wifi,batery<30"))
    }

    // --- between= ---

    @Test
    fun `時間帯は開始を含み終了を含まない`() {
        assertTrue(WhenGuard.inWindow("09:00-17:00", 9 * 60))
        assertTrue(WhenGuard.inWindow("09:00-17:00", 16 * 60 + 59))
        assertFalse(WhenGuard.inWindow("09:00-17:00", 17 * 60))
        assertFalse(WhenGuard.inWindow("09:00-17:00", 8 * 60 + 59))
    }

    @Test
    fun `日を跨ぐ時間帯`() {
        assertTrue(WhenGuard.inWindow("22:00-07:00", 23 * 60))
        assertTrue(WhenGuard.inWindow("22:00-07:00", 0))
        assertTrue(WhenGuard.inWindow("22:00-07:00", 6 * 60 + 59))
        assertFalse(WhenGuard.inWindow("22:00-07:00", 7 * 60))
        assertFalse(WhenGuard.inWindow("22:00-07:00", 12 * 60))
    }

    @Test
    fun `壊れた時間帯は絞らない`() {
        // 書き間違いでルールが永久に動かなくなる方が困る、という判断。
        assertTrue(WhenGuard.inWindow("あさ-よる", 12 * 60))
        assertTrue(WhenGuard.inWindow("25:00-30:00", 12 * 60))
        assertTrue(WhenGuard.inWindow("09:00", 12 * 60))
        assertTrue(WhenGuard.inWindow("09:00-09:00", 3 * 60))
    }

    // --- days= ---

    @Test
    fun `曜日名と範囲`() {
        // 0 = 日曜 … 6 = 土曜
        assertTrue(WhenGuard.dayAllowed("mon-fri", 1))
        assertTrue(WhenGuard.dayAllowed("mon-fri", 5))
        assertFalse(WhenGuard.dayAllowed("mon-fri", 6))
        assertFalse(WhenGuard.dayAllowed("mon-fri", 0))
        assertTrue(WhenGuard.dayAllowed("sat,sun", 0))
        assertTrue(WhenGuard.dayAllowed("Monday", 1))       // 前方一致なので長い綴りも通る
    }

    @Test
    fun `数字は cron と同じで 0 と 7 が日曜`() {
        assertTrue(WhenGuard.dayAllowed("1-5", 1))
        assertFalse(WhenGuard.dayAllowed("1-5", 0))
        assertTrue(WhenGuard.dayAllowed("0", 0))
        assertTrue(WhenGuard.dayAllowed("7", 0))
        // 範囲外だけを書いた = 1 つも読めない。壊れた指定として絞らない (永久に動かないルールを
        // 作らない)。読める曜日が 1 つでもあれば、そちらだけで絞る (下の 1-5,9)。
        assertTrue(WhenGuard.dayAllowed("8", 1))
        assertTrue(WhenGuard.dayAllowed("1-5,9", 1))
        assertFalse(WhenGuard.dayAllowed("1-5,9", 0))
    }

    @Test
    fun `週を跨ぐ範囲と壊れた指定`() {
        assertTrue(WhenGuard.dayAllowed("fri-mon", 6))
        assertTrue(WhenGuard.dayAllowed("fri-mon", 1))
        assertFalse(WhenGuard.dayAllowed("fri-mon", 3))
        assertTrue(WhenGuard.dayAllowed("", 3))
        assertTrue(WhenGuard.dayAllowed("へいじつ", 3))     // 読めなければ絞らない
    }

    // --- cooldown= ---

    @Test
    fun `クールダウンの単位`() {
        assertEquals(30_000L, WhenGuard.cooldownMs("30s"))
        assertEquals(600_000L, WhenGuard.cooldownMs("10m"))
        assertEquals(7_200_000L, WhenGuard.cooldownMs("2h"))
        assertEquals(300_000L, WhenGuard.cooldownMs("5"))   // 単位を省くと分
    }

    @Test
    fun `読めないクールダウンは抑制しない`() {
        assertEquals(0L, WhenGuard.cooldownMs(""))
        assertEquals(0L, WhenGuard.cooldownMs("あとで"))
        assertEquals(0L, WhenGuard.cooldownMs("0m"))
        assertEquals(0L, WhenGuard.cooldownMs("-5m"))
    }

    @Test
    fun `time every と違って 1 分に切り上げない`() {
        // 振ったときの連打を数秒だけ抑えたい、という使い方があるため。
        assertEquals(3_000L, WhenGuard.cooldownMs("3s"))
    }
}
