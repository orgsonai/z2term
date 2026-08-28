package com.zerotoship.z2term.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 対応言語の名簿 ([AppLanguages]) の回帰テスト。
 *
 * ここが崩れると、言語を増やしたときに**設定画面に出ない / 端末の言語から選ばれない**という
 * 形で静かに壊れる（アプリは動くので気付けない）。
 */
class AppLanguagesTest {

    /** 名簿は空でなく、英語 ([AppLanguages.FALLBACK]) を必ず含む。 */
    @Test
    fun fallbackIsInTheRoster() {
        assertTrue("名簿が空", AppLanguages.ALL.isNotEmpty())
        assertTrue(
            "落とし先の ${AppLanguages.FALLBACK} が名簿に無い",
            AppLanguages.FALLBACK in AppLanguages.CODES
        )
    }

    /** コードの重複は「設定画面に同じ選択肢が 2 つ並ぶ」になる。 */
    @Test
    fun codesAreUnique() {
        assertEquals(AppLanguages.CODES.size, AppLanguages.CODES.toSet().size)
    }

    /**
     * ⚠ 言語名は**その言語で**書く。英語表記に揃えると、その言語しか読めない利用者が
     * 自分の言語を見つけられない。ここでは「空でない」ことだけを縛る。
     */
    @Test
    fun everyEntryHasANativeName() {
        for (e in AppLanguages.ALL) {
            assertTrue("${e.code} の表示名が空", e.nativeName.isNotBlank())
        }
    }

    /**
     * ⭐ **落とし先の言語は必ず「完訳」でなければならない。**
     * 訳の無い文言は [AppLanguages.FALLBACK] で出るので、その言語自体に穴があると
     * どこへも落ちられない（英語が欠けた文言は空文字や別言語になる）。
     * 実際の埋まり具合は [com.zerotoship.z2term.proot.CliTranslationCheckTest] が数える。
     */
    @Test
    fun theFallbackLanguageIsMarkedComplete() {
        val fallback = AppLanguages.ALL.first { it.code == AppLanguages.FALLBACK }
        assertTrue(
            "落とし先の ${AppLanguages.FALLBACK} に cliComplete が付いていない",
            fallback.cliComplete
        )
    }

    @Test
    fun resolveKeepsKnownAndDropsUnknown() {
        assertEquals("ja", AppLanguages.resolve("ja"))
        assertEquals("en", AppLanguages.resolve("en"))
        assertEquals("zh-CN", AppLanguages.resolve("zh-CN"))
        // ⛔ 名簿に無い言語は**英語**。日本語へ倒さない。
        // ⚠ ここに「そのうち載せる言語」を書かないこと (載せた日にこのテストが嘘になる)。
        assertEquals(AppLanguages.FALLBACK, AppLanguages.resolve("zz"))
        assertEquals(AppLanguages.FALLBACK, AppLanguages.resolve(""))
    }

    /** 端末ロケールは地域つきで来る (`ja-JP` / `en_US`)。言語部分で拾えること。 */
    @Test
    fun deviceLocaleFallsBackToTheLanguagePart() {
        assertEquals("ja", AppLanguages.matchDeviceLocale("ja-JP"))
        assertEquals("ja", AppLanguages.matchDeviceLocale("ja_JP"))
        assertEquals("en", AppLanguages.matchDeviceLocale("en-US"))
        assertEquals(AppLanguages.FALLBACK, AppLanguages.matchDeviceLocale("de-DE"))
    }

    /**
     * ⭐ **簡体字と繁体字の取り違えを、載せる前に縛っておく。**
     *
     * Android は端末の設定次第で `zh-CN` とだけ言うことも `zh-Hans-CN` と書き方つきで
     * 言うこともある。⚠ **中身が別物なので、繁体字の端末に簡体字を出してはいけない**
     * （読めない字が並ぶ。「近い言語だから」で流してよいものではない）。
     */
    @Test
    fun chineseIsMatchedByItsScriptNotJustItsRegion() {
        val roster = listOf("en", "ja", "zh-CN", "zh-TW")
        assertEquals("zh-CN", AppLanguages.matchIn("zh-CN", roster))
        assertEquals("zh-TW", AppLanguages.matchIn("zh-TW", roster))
        assertEquals("zh-CN", AppLanguages.matchIn("zh-Hans-CN", roster))
        assertEquals("zh-TW", AppLanguages.matchIn("zh-Hant-TW", roster))
        // 香港・マカオは繁体字。国コードは名簿に無いが、書き方で拾える。
        assertEquals("zh-TW", AppLanguages.matchIn("zh-Hant-HK", roster))
        assertEquals("zh-CN", AppLanguages.matchIn("zh-Hans-SG", roster))
        // 書き方も地域も無い素の `zh` は名簿の並び順で決まる (先に置いた方)。
        assertEquals("zh-CN", AppLanguages.matchIn("zh", roster))
        // 繁体字だけを載せている段階では、簡体字の端末もそちらへ行く (英語よりは近い)。
        assertEquals("zh-TW", AppLanguages.matchIn("zh-Hans-CN", listOf("en", "zh-TW")))
    }

    /** 地域つきのスペイン語 (`es-MX` / `es-419`) は `es` へ。 */
    @Test
    fun spanishRegionsCollapseToTheBaseLanguage() {
        val roster = listOf("en", "ja", "es")
        assertEquals("es", AppLanguages.matchIn("es-ES", roster))
        assertEquals("es", AppLanguages.matchIn("es-MX", roster))
        assertEquals("es", AppLanguages.matchIn("es-419", roster))
    }

    /** 名簿に無ければ英語。⛔ 日本語へ倒さない。 */
    @Test
    fun anythingElseGoesToEnglish() {
        val roster = listOf("en", "ja", "zh-CN")
        assertEquals("en", AppLanguages.matchIn("ko-KR", roster))
        assertEquals("en", AppLanguages.matchIn("", roster))
    }
}
