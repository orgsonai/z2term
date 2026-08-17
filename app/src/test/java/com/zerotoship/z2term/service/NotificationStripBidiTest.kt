package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 通知から双方向制御文字を落とす ([NotificationLogService.stripBidi]) の検証。
 *
 * 実機で電話番号が `U+202A` + 番号 + `U+202C` の形で届き、同梱マクロ `unknown-call.sh` の
 * 「電話番号に使う文字を消して何も残らないか」判定が**名前と誤読**して、着信を 1 件も
 * 拾えていなかった (0.8.356 で修正)。`z2-when fired` には `run` と残るため、
 * 「動いているのに何も起きない」という読みにくい壊れ方をしていた。
 */
class NotificationStripBidiTest {

    private fun strip(s: String) = NotificationLogService.stripBidi(s)

    /** 実機に届いていた形そのもの (2026-08-17 の着信)。 */
    @Test
    fun phoneNumberWrappedInBidiBecomesPlain() {
        assertEquals("0120-355-565", strip("\u202A0120-355-565\u202C"))
    }

    /** マクロ側の判定 (`tr -d '0-9+() -'` で何も残らない) が通るところまで見る。 */
    @Test
    fun strippedNumberPassesTheMacroTest() {
        val rest = strip("\u202A0120-355-565\u202C").filterNot { it.isDigit() || it in "+() -" }
        assertEquals("", rest)
    }

    /** LRM/RLM/ALM・埋め込みと上書き・分離の 3 組すべて。 */
    @Test
    fun everyBidiControlIsRemoved() {
        val all = "\u200E\u200F\u061C\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069"
        assertEquals("", strip(all))
        assertEquals("名前", strip("\u2066名\u2069前"))
    }

    /** 制御文字が無ければ何も作らない (通知が来るたびに通る道なので、無駄な複製を避ける)。 */
    @Test
    fun plainTextIsReturnedAsIs() {
        val s = "三井住友カード"
        assertSame(s, strip(s))
    }

    /** BMP 外の絵文字はサロゲートペアで来る。Char 単位で落としても壊れないこと。 */
    @Test
    fun surrogatePairsSurvive() {
        assertEquals("こんにちは\uD83D\uDE03", strip("こんにちは\uD83D\uDE03"))
        assertEquals("\uD83D\uDE03", strip("\u202A\uD83D\uDE03\u202C"))
    }
}
