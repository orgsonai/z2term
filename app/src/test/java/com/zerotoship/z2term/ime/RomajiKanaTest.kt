package com.zerotoship.z2term.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ローマ字 → かな ([RomajiKana])。
 *
 * ⚠ **打鍵の途中を持つ状態機械**なので、1 文字ずつ流したときの積み上がりで確かめる。
 * ここがずれると「打った字が消える」「変な字が入る」という形でしか現れず、実機では
 * 原因が見えない。
 */
class RomajiKanaTest {

    /** 文字列を 1 文字ずつ流し込み、確定したかなを繋いだものと、宙に浮いたローマ字を返す。 */
    private fun type(input: String): Pair<String, String> {
        val kana = StringBuilder()
        var pending = ""
        input.forEach { ch ->
            val result = RomajiKana.feed(pending, ch)
            kana.append(result.kana)
            pending = result.pending
        }
        return kana.toString() to pending
    }

    private fun kanaOf(input: String): String {
        val (kana, pending) = type(input)
        return kana + RomajiKana.flush(pending)
    }

    @Test
    fun plainSyllables() {
        assertEquals("あいうえお", kanaOf("aiueo"))
        assertEquals("かきくけこ", kanaOf("kakikukeko"))
        assertEquals("ぱぴぷぺぽ", kanaOf("papipupepo"))
    }

    @Test
    fun bothRomanizationsAreAccepted() {
        // ⚠ ヘボン式と訓令式のどちらで打つかは人による。片方しか受けないと「打てない」になる。
        assertEquals("し", kanaOf("shi"))
        assertEquals("し", kanaOf("si"))
        assertEquals("つ", kanaOf("tsu"))
        assertEquals("つ", kanaOf("tu"))
        assertEquals("ふ", kanaOf("fu"))
        assertEquals("ふ", kanaOf("hu"))
        assertEquals("じ", kanaOf("ji"))
        assertEquals("じ", kanaOf("zi"))
    }

    @Test
    fun palatalizedSyllables() {
        assertEquals("きょう", kanaOf("kyou"))
        assertEquals("しゃしん", kanaOf("shashin"))
        assertEquals("じゅう", kanaOf("juu"))
        assertEquals("ちゃ", kanaOf("cha"))
    }

    @Test
    fun theDoubledConsonantBecomesASmallTsu() {
        assertEquals("がっこう", kanaOf("gakkou"))
        assertEquals("いっしょ", kanaOf("issho"))
        assertEquals("まって", kanaOf("matte"))
    }

    @Test
    fun theSyllabicNIsNotEatenByTheNextSyllable() {
        // ⚠ "n" は "na" にも育つので、次の打鍵まで確定させない。
        assertEquals("な", kanaOf("na"))
        assertEquals("ん", kanaOf("nn"))
        assertEquals("こんにちは", kanaOf("konnnichiha"))
        assertEquals("かんじ", kanaOf("kanji"))
        assertEquals("ほん", kanaOf("hon"))
        // アポストロフィでも切れる (「ん」+ 母音を打ち分けたいとき)。
        assertEquals("んあ", kanaOf("n'a"))
    }

    @Test
    fun smallKanaAndSymbols() {
        assertEquals("ぁぃぅぇぉ", kanaOf("xaxixuxexo"))
        assertEquals("っ", kanaOf("xtu"))
        assertEquals("ゃゅょ", kanaOf("lyalyulyo"))
        assertEquals("ー", kanaOf("-"))
        assertEquals("、", kanaOf(","))
        assertEquals("。", kanaOf("."))
    }

    @Test
    fun anUnknownKeyIsNotSwallowed() {
        // ⛔ 溜まっていたローマ字を黙って捨てると、打った字が消えて「打鍵が飛んだ」に見える。
        assertEquals("k1", kanaOf("k1"))
        assertEquals("あ9", kanaOf("a9"))
    }

    @Test
    fun aHalfTypedSyllableWaits() {
        // 途中の "ky" は宙に浮いたまま (次の母音を待つ)。
        assertEquals("" to "ky", type("ky"))
        // 打ち終わりに flush すると、そのままの形で落とす (消さない)。
        assertEquals("ky", RomajiKana.flush("ky"))
        assertEquals("ん", RomajiKana.flush("n"))
        assertEquals("か", RomajiKana.flush("ka"))
    }
}
