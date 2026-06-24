package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DCS (`ESC P`) / APC (`ESC _`) / PM (`ESC ^`) / SOS (`ESC X`) を ST (`ESC \`) または
 * BEL まで吸収して破棄することを保証する回帰テスト。
 *
 * 元の症状:
 *  - 画像転送プロトコル等が送る `ESC _ G...\e\\` の `_` を吸収できず、続く本文や
 *    base64 payload が画面に文字として書かれていた。
 *  - DCS 本文中に含まれる `ESC [ ... M` のような並びを CSI として誤解釈し、途中
 *    終端不一致で続きが画面へ流出していた (mouse SGR 漏れの正体の 1 つ)。
 *  - DCS/APC 本文に混じる `\r` (0x0D) が GROUND の CR として処理され、TUI 描画
 *    中に cursor が突然行頭に飛んでいた。
 *
 * 期待動作:
 *  - 文字列系シーケンスの開始から終端 (BEL/ST) までの全バイトを破棄する
 *  - 終端後は GROUND 状態に戻り、後続の通常テキストはそのまま描画される
 *  - 異常終端 (ESC + 非 `\`) はその時点で文字列扱いを打ち切り、続くバイトを
 *    ESCAPE 状態として再解釈する (xterm 流儀)
 */
class StringStateAbsorbTest {

    private val ESC = ""
    private val BEL = ""
    private val ST = "$ESC\\"

    private fun emu(rows: Int = 3, cols: Int = 40) =
        TerminalEmulator(output = {}, initialRows = rows, initialColumns = cols)

    private fun feed(e: TerminalEmulator, s: String) =
        e.processBytes(s.toByteArray(Charsets.UTF_8))

    private fun rowText(e: TerminalEmulator, row: Int): String =
        e.buffer.getScreenRow(row).toText().trimEnd()

    @Test
    fun apcWithPngLikePayload_isFullyDiscarded_andTextSurvives() {
        val e = emu()
        // 画像プロトコル想定: APC + key=value 並び + ; + base64 payload + ST。
        // 本文には CR、CSI 風の並び、ASCII 数字記号を意図的に混入。
        val payload = "G,a=T,f=100,s=12,v=8;iVBORw0KGgoAAAANSUhEUgAAAAwAAAAI\r$ESC[<35;1;1M"
        feed(e, "$ESC" + "_" + payload + ST)
        // 画像本文が一切残らないこと
        assertEquals("", rowText(e, 0))
        // 直後の通常テキストはそのまま描画される
        feed(e, "hello")
        assertEquals("hello", rowText(e, 0))
    }

    @Test
    fun dcsWithCsiLookalike_isAbsorbed_noStrayGlyphs() {
        val e = emu()
        // DCS 本文に CSI 風の並びが混ざるパターン (DECRQSS 応答のような構造)。
        feed(e, "${ESC}P1\$r0;1m${ESC}\\")
        feed(e, "ok")
        assertEquals("ok", rowText(e, 0))
    }

    @Test
    fun pmAndSos_areAbsorbed_withBelTerminator() {
        val e = emu()
        feed(e, "$ESC^private message body$BEL")
        feed(e, "${ESC}Xsos body$BEL")
        feed(e, "after")
        assertEquals("after", rowText(e, 0))
    }

    @Test
    fun stringBody_doesNotMoveCursor_onEmbeddedCrOrLf() {
        val e = emu()
        feed(e, "abc")
        // cursorCol が 3 の状態で APC 本文に CR/LF が混ざっても cursor は動かない。
        feed(e, "$ESC" + "_anything\r\n more" + ST)
        feed(e, "X")
        // "abcX" になっていれば CR/LF が GROUND 状態に漏れていない証拠。
        assertEquals("abcX", rowText(e, 0))
    }

    @Test
    fun malformedTerminator_escThenNonBackslash_recoversCleanly() {
        val e = emu()
        // APC 本文中の `ESC [ 1 m` は ST ではないので xterm 流儀で打ち切り、
        // 続く `[ 1 m` は CSI として解釈される (描画には何も書かない SGR)。
        feed(e, "$ESC" + "_payload" + "${ESC}[1mAFTER")
        // SGR の効果は文字には現れないため、表示は "AFTER" だけ残る。
        assertEquals("AFTER", rowText(e, 0))
    }
}
