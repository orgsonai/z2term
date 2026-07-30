package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // --- share (0.8.266) ---

    private fun share(spec: String, kind: String, text: String, files: List<String> = emptyList()) =
        WhenTriggerMatch.share(spec, kind, text, files)

    @Test fun share_anyAndKind() {
        assertTrue(share("any", "text", "hello"))
        assertTrue(share("any", "file", "~/z2term-inbox/a.pdf", listOf("a.pdf")))
        assertTrue(share("text", "text", "hello"))
        assertFalse(share("text", "file", "~/z2term-inbox/a.pdf", listOf("a.pdf")))
        assertTrue(share("file", "file", "~/z2term-inbox/a.pdf", listOf("a.pdf")))
        assertFalse(share("file", "text", "hello"))
    }

    @Test fun share_containsIsCaseInsensitive() {
        assertTrue(share("contains=youtube", "text", "https://YouTube.com/watch?v=x"))
        assertFalse(share("contains=youtube", "text", "https://example.com/"))
        assertFalse(share("contains=", "text", "anything"))
    }

    @Test fun share_containsNeverMatchesFilePaths() {
        // ファイル共有の text は取り込み先のパス。当たると「ファイル名にたまたま含まれていた」で
        // 発火してしまい、書いた人の意図 (共有された文章の中身で絞る) とズレる。
        assertFalse(share("contains=inbox", "file", "~/z2term-inbox/report.pdf", listOf("report.pdf")))
    }

    @Test fun share_extMatchesAnyOfTheFiles() {
        assertTrue(share("ext=pdf", "file", "…", listOf("a.txt", "b.pdf")))
        assertTrue(share("ext=.pdf", "file", "…", listOf("b.pdf"))) // 先頭の . は付けても付けなくても
        assertTrue(share("ext=PDF", "file", "…", listOf("b.pdf"))) // 大小文字は区別しない
        assertFalse(share("ext=pdf", "file", "…", listOf("a.txt")))
        assertFalse(share("ext=pdf", "text", "b.pdf")) // テキスト共有には当たらない
        // 拡張子の無いファイルを ext= が拾わない (substringAfterLast の取り違え防止)。
        assertFalse(share("ext=pdf", "file", "…", listOf("README")))
    }

    @Test fun share_unknownSpec_doesNotFire() {
        assertFalse(share("", "text", "hello"))
        assertFalse(share("whatever", "text", "hello"))
    }

    // --- net (0.8.264) ---

    private fun net(spec: String, now: String, prev: String) = WhenTriggerMatch.net(spec, now, prev)

    @Test fun net_online_firesOnlyOnTheEdgeIntoOnline() {
        assertTrue(net("online", now = "wifi", prev = "none"))
        assertTrue(net("online", now = "mobile", prev = "none"))
        assertFalse(net("online", now = "none", prev = "wifi"))
    }

    @Test fun net_online_doesNotFireWhenTheLinkMerelySwitches() {
        // Wi-Fi からモバイルへ替わっても「通信できる」ことは変わらない。ここで発火すると
        // 「繋がったら送る」が移動のたびに走ってしまう (このトリガーの一番の落とし穴)。
        assertFalse(net("online", now = "mobile", prev = "wifi"))
        assertFalse(net("offline", now = "mobile", prev = "wifi"))
    }

    @Test fun net_offline_firesOnlyOnTheEdgeIntoOffline() {
        assertTrue(net("offline", now = "none", prev = "mobile"))
        assertFalse(net("offline", now = "wifi", prev = "none"))
    }

    @Test fun net_transport_firesWhenItBecomesThatLink() {
        assertTrue(net("wifi", now = "wifi", prev = "mobile"))
        assertTrue(net("wifi", now = "wifi", prev = "none"))
        assertTrue(net("mobile", now = "mobile", prev = "wifi"))
        assertTrue(net("ethernet", now = "ethernet", prev = "none"))
        // 同じ回線のままなら発火しない (呼び元も変化時しか呼ばないが、判定単体でも成り立たせる)。
        assertFalse(net("wifi", now = "wifi", prev = "wifi"))
        assertFalse(net("mobile", now = "wifi", prev = "mobile"))
    }

    @Test fun net_unknownSpec_doesNotFire() {
        assertFalse(net("vpn", now = "vpn", prev = "wifi")) // vpn は種別としては返るが spec には無い
        assertFalse(net("", now = "wifi", prev = "none"))
        assertFalse(net("whatever", now = "wifi", prev = "none"))
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

    // --- sensor ---

    @Test fun sensorType_mapsSpecToSensor() {
        assertEquals("accel", WhenTriggerMatch.sensorType("shake"))
        assertEquals("light", WhenTriggerMatch.sensorType("light>500"))
        assertEquals("light", WhenTriggerMatch.sensorType("light<10"))
        assertEquals("proximity", WhenTriggerMatch.sensorType("proximity=near"))
        assertEquals("proximity", WhenTriggerMatch.sensorType("proximity=far"))
        assertNull(WhenTriggerMatch.sensorType("nonsense"))
    }

    @Test fun lightSatisfied_thresholds() {
        assertTrue(WhenTriggerMatch.lightSatisfied("light>500", 600f))
        assertFalse(WhenTriggerMatch.lightSatisfied("light>500", 400f))
        assertTrue(WhenTriggerMatch.lightSatisfied("light<10", 5f))
        assertFalse(WhenTriggerMatch.lightSatisfied("light<10", 20f))
        assertFalse(WhenTriggerMatch.lightSatisfied("light>abc", 600f)) // 不正閾値は不成立
    }

    @Test fun proximitySatisfied_nearFar() {
        assertTrue(WhenTriggerMatch.proximitySatisfied("proximity=near", near = true))
        assertFalse(WhenTriggerMatch.proximitySatisfied("proximity=near", near = false))
        assertTrue(WhenTriggerMatch.proximitySatisfied("proximity=far", near = false))
        assertFalse(WhenTriggerMatch.proximitySatisfied("proximity=far", near = true))
    }

    // --- event:* トリガー (events.jsonl に流れる端末イベントを名前で拾う) ---

    @Test fun event_exactName() {
        assertTrue(WhenTriggerMatch.event("screen_on", "screen_on"))
        assertFalse(WhenTriggerMatch.event("screen_on", "screen_off"))
    }

    @Test fun event_prefixWildcard() {
        // ringer_normal / _vibrate / _silent をまとめて 1 ルールで拾えること。
        assertTrue(WhenTriggerMatch.event("ringer_*", "ringer_silent"))
        assertTrue(WhenTriggerMatch.event("ringer_*", "ringer_normal"))
        assertFalse(WhenTriggerMatch.event("ringer_*", "screen_on"))
    }

    @Test fun event_matchAll() {
        assertTrue(WhenTriggerMatch.event("*", "anything"))
    }

    @Test fun event_ignoresCaseAndSpace() {
        // ルールファイルは手で書けるので、大小文字と前後空白では落とさない。
        assertTrue(WhenTriggerMatch.event(" Screen_On ", "screen_on"))
    }

    @Test fun event_emptyNeverMatches() {
        // 空 spec が全一致に化けると、壊れたルールが全イベントで発火してしまう。
        assertFalse(WhenTriggerMatch.event("", "screen_on"))
        assertFalse(WhenTriggerMatch.event("screen_on", ""))
    }

    @Test fun event_prefixDoesNotMatchShorterName() {
        assertFalse(WhenTriggerMatch.event("battery_low*", "battery_"))
    }

    // --- file:new トリガー (新しいファイルが降ってきたら動く) ---

    @Test fun fileDir_parsesWithAndWithoutFilter() {
        assertEquals("/sdcard/Download", WhenTriggerMatch.fileDir("new=/sdcard/Download"))
        assertEquals("/sdcard/Download", WhenTriggerMatch.fileDir("new=/sdcard/Download,ext=zip"))
        // 末尾スラッシュの有無で別フォルダ扱いになると、同じ場所に監視が二重に張られる。
        assertEquals("/sdcard/Download", WhenTriggerMatch.fileDir("new=/sdcard/Download/"))
    }

    @Test fun fileDir_rejectsOtherSpecs() {
        assertNull(WhenTriggerMatch.fileDir("moved=/sdcard"))
        assertNull(WhenTriggerMatch.fileDir("new="))
    }

    @Test fun fileMatches_withoutFilterTakesEverything() {
        assertTrue(WhenTriggerMatch.fileMatches("new=/sdcard/Download", "a.zip"))
        assertTrue(WhenTriggerMatch.fileMatches("new=/sdcard/Download", "no-extension"))
    }

    @Test fun fileMatches_extensionFilterIsCaseInsensitive() {
        assertTrue(WhenTriggerMatch.fileMatches("new=/sdcard/Download,ext=zip", "a.ZIP"))
        assertFalse(WhenTriggerMatch.fileMatches("new=/sdcard/Download,ext=zip", "a.tar"))
    }

    // --- notify:* トリガー (SMS 以外で届く確認コードを拾うのが主目的) ---

    private fun noti(spec: String, pkg: String = "com.example.mail", app: String = "Mail",
                     title: String = "", text: String = "", category: String = "") =
        WhenTriggerMatch.notify(spec, pkg, app, title, text, category)

    @Test fun notify_any() {
        assertTrue(noti("any"))
    }

    @Test fun notify_pkgMatchesPackageOrAppName() {
        // パッケージ名は覚えていないことが多いので、表示名でも拾えるようにしてある。
        assertTrue(noti("pkg=example", pkg = "com.example.mail"))
        assertTrue(noti("pkg=mail", app = "Mail"))
        assertFalse(noti("pkg=chat", pkg = "com.example.mail", app = "Mail"))
        assertFalse(noti("pkg="))
    }

    @Test fun notify_titleAndContains() {
        assertTrue(noti("title=Bank", title = "MyBank"))
        assertFalse(noti("title=Bank", title = "Shop", text = "Bank"))
        // contains はタイトルと本文の両方を見る (どちらに入るかはアプリ次第なので)。
        assertTrue(noti("contains=code", title = "x", text = "Your CODE is 1234"))
        assertTrue(noti("contains=code", title = "Your code", text = ""))
    }

    @Test fun notify_otpLooksAtBodyThenTitle() {
        assertTrue(noti("otp", text = "Your code is 483920"))
        assertTrue(noti("otp", title = "G-12345 is your code"))
        assertFalse(noti("otp", title = "Welcome", text = "No code here"))
    }

    @Test fun notify_categoryIsExactNotPartial() {
        assertTrue(noti("category=call", category = "call"))
        assertTrue(noti("category=CALL", category = "call"))      // 大小は無視する
        assertTrue(noti("category=missed_call", category = "missed_call"))
        // ⚠ ここが要 — 部分一致にすると「着信のとき」が不在着信でも動いてしまう
        // (call は missed_call の部分文字列)。着信と不在着信を書き分けられなくなる。
        assertFalse(noti("category=call", category = "missed_call"))
        assertFalse(noti("category=call", category = ""))
        assertFalse(noti("category="))
    }

    @Test fun notify_unknownSpecNeverFires() {
        assertFalse(noti("whatever"))
        assertFalse(noti(""))
    }

    @Test fun fileMatches_skipsHiddenAndPartialFiles() {
        // 同期アプリやカメラは `.pending-xxx` で書いてから rename する。拾うと
        // 実体の無いパスでマクロが走るので、隠しファイルは常に外す。
        assertFalse(WhenTriggerMatch.fileMatches("new=/sdcard/DCIM", ".pending-1234.jpg"))
        assertFalse(WhenTriggerMatch.fileMatches("new=/sdcard/DCIM", ""))
    }
}
