package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `z2-listen -w` の判定 ([ListenWords.match]) を固定する。
 *
 * 外すと「言ったのに動かない」になり、声で操作する意味が無くなる。
 */
class ListenWordsTest {

    private val words = listOf("改行", "送信", "コントロールC", "次の曲")

    /** 1 位が同じ読みの別の漢字でも、2 位以下に待つ言葉があれば当てる (実機で「開業」になった)。 */
    @Test
    fun looksPastTheTopCandidate() {
        assertEquals("改行", ListenWords.match(words, listOf("開業", "改行", "かいぎょう")))
    }

    /** 丸ごと一致は候補の順に見る。含まれているだけの言葉より優先する。 */
    @Test
    fun prefersWholeMatchOverContained() {
        assertEquals("送信", ListenWords.match(words, listOf("次の曲を送信", "送信")))
    }

    /** 全角/半角・大文字/小文字・空白・句読点の違いでは外さない。 */
    @Test
    fun ignoresWidthCaseSpacesAndPunctuation() {
        assertEquals("コントロールC", ListenWords.match(words, listOf("コントロール ｃ。")))
    }

    /** 文の中に含まれていれば当てる。複数含まれていれば長い方。 */
    @Test
    fun findsLongestContainedWord() {
        assertEquals("次の曲", ListenWords.match(words, listOf("次の曲お願い")))
        assertEquals("次の曲", ListenWords.match(listOf("曲", "次の曲"), listOf("次の曲にして")))
    }

    @Test
    fun returnsNullWhenNothingMatches() {
        assertNull(ListenWords.match(words, listOf("開業", "かいぎょう")))
        assertNull(ListenWords.match(listOf(" ", ""), listOf("改行")))
    }
}
