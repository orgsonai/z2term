package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 端末に出る文言の言語選択 ([CliText]) と、**訳が無いときの落とし先**の回帰テスト。
 *
 * ⭐ **主目的は [generatedScriptsFallBackToEnglishForAnUnknownLanguage]**。
 * 0.8.421 までは `val ja = lang != "en"` と書かれていて、**知らない言語を渡すと日本語が出た**。
 * 言語を増やしたとき、訳の無い文言だけ日本語に化けるのは端末を見るまで気付けないので、
 * 「訳が無ければ英語」を生成物のレベルで縛っておく。
 */
class CliTextTest {

    @Test
    fun picksTheRequestedLanguage() {
        assertEquals("hello", CliText("en")(en = "hello", ja = "こんにちは"))
        assertEquals("こんにちは", CliText("ja")(en = "hello", ja = "こんにちは"))
    }

    /** 変わり値の無い言語は英語。⛔ 日本語へ倒さない。 */
    @Test
    fun languageWithoutAVariantFallsBackToEnglish() {
        assertEquals("hello", CliText("zh-CN")(en = "hello", ja = "こんにちは"))
        assertEquals("hello", CliText("de")(en = "hello", ja = "こんにちは"))
        assertEquals("hello", CliText("")(en = "hello", ja = "こんにちは"))
    }

    /** ⭐ 3 言語目を足す口。挙げた言語はそのまま出る（この口が死んでいないことの確認）。 */
    @Test
    fun aThirdLanguageCanBeAdded() {
        assertEquals(
            "用法: z2-toast <消息>",
            CliText("zh-CN")(
                en = "usage: z2-toast <message>",
                ja = "usage: z2-toast <メッセージ>",
                "zh-CN" to "用法: z2-toast <消息>",
            )
        )
    }

    @Test
    fun linesFollowTheSameRule() {
        val en = listOf("a", "b")
        val ja = listOf("あ", "い")
        assertEquals(ja, CliText("ja").lines(en = en, ja = ja))
        assertEquals(en, CliText("zh-CN").lines(en = en, ja = ja))
        assertEquals(
            listOf("甲"),
            CliText("zh-CN").lines(en = en, ja = ja, "zh-CN" to listOf("甲"))
        )
    }

    /** 文字列でないもの ([CliText.of]) も同じ規則。 */
    @Test
    fun nonTextValuesFollowTheSameRule() {
        assertEquals(8, CliText("ja").of(en = 7, ja = 8))
        assertEquals(7, CliText("zh-CN").of(en = 7, ja = 8))
    }

    /**
     * ⭐ **生成物での裏取り。** 訳を持たない言語で組み立てたスクリプトは
     * **英語版と 1 バイトも違わない**こと、そして日本語版とは違うこと。
     *
     * ⚠ 端末に出る文言の持ち主を増やしたら、ここに 1 行足すこと —
     * その 1 本だけ「英語でなければ日本語」で書かれている、が起こりうる。
     */
    @Test
    fun generatedScriptsFallBackToEnglishForAnUnknownLanguage() {
        val unknown = "zh-CN"
        for ((name, build) in listOf<Pair<String, (String) -> String>>(
            "z2doctor" to ::z2doctorScript,
            "z2scan" to ::z2scanScript,
            "z2adb" to ::z2adbScript,
            "z2help" to ::z2helpScript,
            "z2run" to ::z2runScript,
            "z2-macro" to ::z2MacroScript,
            "pacman-keyring" to ::pacmanKeyringScript,
        )) {
            assertEquals("$name: 訳が無い言語なのに英語版と違う", build("en"), build(unknown))
            // 日英が本当に別物であることも押さえる (取り違えの検出になる)
            assertNotEquals("$name: 日本語版と英語版が同じ", build("ja"), build("en"))
        }

        assertEquals(z2ApiScripts("en"), z2ApiScripts(unknown))
        assertNotEquals(z2ApiScripts("ja"), z2ApiScripts("en"))
        assertEquals(z2MacroSamples("en"), z2MacroSamples(unknown))
        assertNotEquals(z2MacroSamples("ja"), z2MacroSamples("en"))
        assertEquals(GuiScriptStrings.en(), GuiScriptStrings.forLang(unknown))
        assertEquals(SshdScriptStrings.en(), SshdScriptStrings.forLang(unknown))
    }
}
