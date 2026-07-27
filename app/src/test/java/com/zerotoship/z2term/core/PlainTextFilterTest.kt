package com.zerotoship.z2term.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 端末ログ (ツールバー ⚪) のプレーンテキスト変換 ([PlainTextFilter]) の回帰テスト。
 *
 * ここが壊れると「ログは取れているが読めない / 何千行にも膨れる」という形で出るため、
 * 実際に踏みやすいパターン (進捗表示・CRLF・色付きプロンプト・分割された UTF-8) を固定する。
 */
class PlainTextFilterTest {

    private val esc = ""
    private val bel = ""

    private fun filter(vararg chunks: String): String {
        val f = PlainTextFilter()
        val sb = StringBuilder()
        for (c in chunks) sb.append(String(f.filter(c.toByteArray(Charsets.UTF_8)), Charsets.UTF_8))
        sb.append(String(f.drain(), Charsets.UTF_8))
        return sb.toString()
    }

    @Test
    fun `plain lines pass through`() {
        assertEquals("hello\nworld\n", filter("hello\nworld\n"))
    }

    @Test
    fun `CRLF is an ordinary newline`() {
        // \r は行頭に戻るだけなので、行の内容は消えない。
        assertEquals("hello\nworld\n", filter("hello\r\nworld\r\n"))
    }

    @Test
    fun `progress rewrites collapse into the final state`() {
        // 進捗表示が 1 行に畳まれる (別行にすると数千行に膨れるため)。
        assertEquals("100%\n", filter("50%\r75%\r100%\n"))
    }

    @Test
    fun `carriage return overwrites only what it covers`() {
        // 端末と同じで、上書きしなかった残りはそのまま見える。
        assertEquals("XYcdef\n", filter("abcdef\rXY\n"))
    }

    @Test
    fun `SGR color sequences are dropped`() {
        assertEquals("red\n", filter("${esc}[31mred${esc}[0m\n"))
    }

    @Test
    fun `CSI with parameters is dropped`() {
        assertEquals("ab\n", filter("a${esc}[1;2;3Hb\n"))
    }

    @Test
    fun `OSC title with BEL terminator is dropped`() {
        assertEquals("prompt\n", filter("${esc}]0;my title${bel}prompt\n"))
    }

    @Test
    fun `OSC with ST terminator is dropped`() {
        assertEquals("prompt\n", filter("${esc}]0;my title$esc\\prompt\n"))
    }

    @Test
    fun `charset designation consumes its argument`() {
        assertEquals("ab\n", filter("a$esc(Bb\n"))
    }

    @Test
    fun `two byte escape ends immediately`() {
        assertEquals("ab\n", filter("a${esc}cb\n"))
    }

    @Test
    fun `escape split across chunks still absorbs the sequence`() {
        // PTY の partial read でシーケンスが割れても、状態を持ち越して読み捨てる。
        assertEquals("red\n", filter("$esc", "[31m", "red\n"))
    }

    @Test
    fun `backspace steps back without deleting`() {
        // 端末と同じで BS は位置を戻すだけ。上書きされなければ内容は残る。
        assertEquals("abc\n", filter("abc\b\n"))
    }

    @Test
    fun `backspace then space erases the character`() {
        // シェルが実際に使う「BS + space + BS」= 1 文字消す動き。
        assertEquals("ab \n", filter("abc\b \b\n"))
    }

    @Test
    fun `utf8 split across chunks is not corrupted`() {
        // 「あ」= E3 81 82 を 2 つの塊に割って渡す (PTY の partial read を再現)。
        val f = PlainTextFilter()
        val first = f.filter(byteArrayOf(0xE3.toByte(), 0x81.toByte()))
        val second = f.filter(byteArrayOf(0x82.toByte(), '\n'.code.toByte()))
        assertEquals("あ\n", String(first, Charsets.UTF_8) + String(second, Charsets.UTF_8))
    }

    @Test
    fun `carriage return overwrite keeps multibyte characters intact`() {
        // 上書きをバイト位置で行うと日本語が割れる。コードポイント単位であることの確認。
        assertEquals("Xいうえ\n", filter("あいうえ\rX\n"))
    }

    @Test
    fun `drain flushes a line that never ended`() {
        // 改行が来ないまま停止しても、最後の行を落とさない。
        assertEquals("tail\n", filter("tail"))
    }

    @Test
    fun `tab is kept and meaningless control bytes are dropped`() {
        assertEquals("a\tb\n", filter("a\tb$bel\n"))
    }
}
