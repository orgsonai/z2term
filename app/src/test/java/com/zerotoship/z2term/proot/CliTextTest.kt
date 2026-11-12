package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    /** 英語・日本語以外の訳の代わり (テスト用)。 */
    private val fake = object : CliLanguage {
        override val code = "xx"
        override val texts = mapOf("toast.usage" to "用法: z2-toast <消息>")
        override val lineSets = mapOf("macro.head" to listOf("甲"))
        override val values = mapOf<String, Any>("macro.cols" to 9, "macro.wrongType" to "9")
    }

    @Test
    fun picksTheRequestedLanguage() {
        assertEquals("hello", CliText("en")("k", en = "hello", ja = "こんにちは"))
        assertEquals("こんにちは", CliText("ja")("k", en = "hello", ja = "こんにちは"))
    }

    /** 訳の無い言語は英語。⛔ 日本語へ倒さない。 */
    @Test
    fun languageWithoutAVariantFallsBackToEnglish() {
        assertEquals("hello", CliText("zz")("k", en = "hello", ja = "こんにちは"))
        assertEquals("hello", CliText("")("k", en = "hello", ja = "こんにちは"))
        // 言語ファイルはあるが、その名前の訳が無い
        assertEquals("hello", CliText("xx", fake)("nope", en = "hello", ja = "こんにちは"))
    }

    /** ⭐ 3 言語目の口。言語ファイルにある訳はその名前で出る（この口が死んでいないことの確認）。 */
    @Test
    fun aThirdLanguageComesFromItsLanguageFile() {
        assertEquals(
            "用法: z2-toast <消息>",
            CliText("xx", fake)("toast.usage", en = "usage: z2-toast <message>", ja = "usage: z2-toast <メッセージ>")
        )
    }

    @Test
    fun linesFollowTheSameRule() {
        val en = listOf("a", "b")
        val ja = listOf("あ", "い")
        assertEquals(ja, CliText("ja").lines("macro.head", en = en, ja = ja))
        assertEquals(en, CliText("zz").lines("macro.head", en = en, ja = ja))
        assertEquals(listOf("甲"), CliText("xx", fake).lines("macro.head", en = en, ja = ja))
    }

    /** 文字列でないもの ([CliText.of]) も同じ規則。型が違う値は英語へ落ちる。 */
    @Test
    fun nonTextValuesFollowTheSameRule() {
        assertEquals(8, CliText("ja").of("macro.cols", en = 7, ja = 8))
        assertEquals(7, CliText("zz").of("macro.cols", en = 7, ja = 8))
        assertEquals(9, CliText("xx", fake).of("macro.cols", en = 7, ja = 8))
        assertEquals(7, CliText("xx", fake).of("macro.wrongType", en = 7, ja = 8))
    }

    /**
     * ⭐ **言語ファイルが実際に使われていること。** 1.0.84 で訳を `proot/lang/` へ移したので、
     * 一覧 ([CliLanguages]) への登録漏れや、[Z2ApiMsg.of] を通さずに作った箇所があると、
     * その言語だけ黙って英語に戻る。生成物が英語版と違うことで押さえる。
     */
    @Test
    fun everyLanguageFileIsUsed() {
        for (lang in CliLanguages.all.map { it.code }) {
            assertNotNull("$lang: 言語ファイルが一覧に無い", CliLanguages.of(lang))
            assertNotEquals("$lang: z2doctor が英語のまま", z2doctorScript("en"), z2doctorScript(lang))
            assertNotEquals("$lang: z2-* が英語のまま", z2ApiScripts("en"), z2ApiScripts(lang))
            assertNotEquals("$lang: サンプルマクロが英語のまま", z2MacroSamples("en"), z2MacroSamples(lang))
            assertNotEquals(
                "$lang: Z2ApiMsg が上書きされていない",
                Z2ApiMsg.of("en", "$").usbHelp,
                Z2ApiMsg.of(lang, "$").usbHelp
            )
            assertNotEquals("$lang: GUI の組が英語のまま", GuiScriptStrings.en(), GuiScriptStrings.forLang(lang))
            assertNotEquals("$lang: sshd の組が英語のまま", SshdScriptStrings.en(), SshdScriptStrings.forLang(lang))
        }
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
        // ⛔ **名簿に載る予定のある言語をここに書かないこと。** 0.8.424 まで "zh-CN" だったが、
        // 簡体字を実際に足した時点で「訳の無い言語」ではなくなり、このテストの前提が崩れた。
        // `zz` は ISO 639-1 で割り当てられない領域なので、名簿に載ることがない。
        val unknown = "zz"
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
