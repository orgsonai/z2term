package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SgrUnderlineSubparamTest {
    private val ESC = "\u001b"   // ⚠ 生の ESC を書かない (目に見えず、抜けてもテストが黙って空振りする)

    private fun emu() = TerminalEmulator(output = {}, initialRows = 3, initialColumns = 20)
    private fun feed(e: TerminalEmulator, s: String) = e.processBytes(s.toByteArray(Charsets.UTF_8))
    private fun flagsAt(e: TerminalEmulator, row: Int, col: Int): Int =
        e.buffer.getScreenRow(row).getCell(col).fgAttr

    private fun underline(f: Int) = (f and SgrAttribute.FLAG_UNDERLINE) != 0
    private fun italic(f: Int) = (f and SgrAttribute.FLAG_ITALIC) != 0
    private fun bold(f: Int) = (f and SgrAttribute.FLAG_BOLD) != 0
    private fun blink(f: Int) = (f and SgrAttribute.FLAG_BLINK) != 0

    @Test
    fun curlyUnderline_doesNotSetItalic() {
        val e = emu()
        feed(e, "$ESC[4:3mX")
        val f = flagsAt(e, 0, 0)
        assertTrue("4:3 は underline", underline(f))
        assertFalse("4:3 で italic を立ててはいけない", italic(f))
    }

    @Test
    fun dashedUnderline_doesNotSetBlink() {
        val e = emu()
        feed(e, "$ESC[4:5mX")
        val f = flagsAt(e, 0, 0)
        assertTrue(underline(f))
        assertFalse("4:5 で blink を立ててはいけない", blink(f))
    }

    @Test
    fun underlineOff_viaColonZero_onlyClearsUnderline_notColor() {
        val e = emu()
        // 赤文字 + undercurl → 4:0 で下線だけ消える (色は残る)
        feed(e, "$ESC[31m$ESC[4:3mA$ESC[4:0mB")
        val fa = flagsAt(e, 0, 0)
        val fb = flagsAt(e, 0, 1)
        assertTrue(underline(fa))
        assertFalse("4:0 で下線が消えること", underline(fb))
        assertEquals("4:0 は色をリセットしない", SgrAttribute.makeIndexed(1), fb and (SgrAttribute.FORMAT_MASK or SgrAttribute.COLOR_MASK))
    }

    @Test
    fun singleUnderline_colon_doesNotSetBold() {
        val e = emu()
        feed(e, "$ESC[4:1mX")
        val f = flagsAt(e, 0, 0)
        assertTrue(underline(f))
        assertFalse("4:1 で bold を立ててはいけない", bold(f))
    }
}

class SgrUnderlineAltScreenExitTest {
    private val ESC = "\u001b"   // ⚠ 生の ESC を書かない (目に見えず、抜けてもテストが黙って空振りする)
    private fun emu() = TerminalEmulator(output = {}, initialRows = 5, initialColumns = 20)
    private fun feed(e: TerminalEmulator, s: String) = e.processBytes(s.toByteArray(Charsets.UTF_8))
    private fun flagsAt(e: TerminalEmulator, row: Int, col: Int): Int =
        e.buffer.getScreenRow(row).getCell(col).fgAttr
    private fun underline(f: Int) = (f and SgrAttribute.FLAG_UNDERLINE) != 0

    // styled underline を使う alt screen TUI を抜けたあと、 通常テキストへ下線が残らないこと。
    @org.junit.Test
    fun afterAltScreenExit_normalTextHasNoUnderline() {
        val e = emu()
        feed(e, "p")                       // primary 上の通常文字 (SGR 無し)
        feed(e, "$ESC[?1049h")             // alt screen へ
        feed(e, "$ESC[4:3mUNDERCURL")      // 波線下線を描いたまま reset せず
        feed(e, "$ESC[?1049l")             // alt screen を抜けて復帰
        feed(e, "$ESC[24m")                // 下線オフ (shell が出すことがある)
        feed(e, "X")                       // 復帰後の通常文字
        org.junit.Assert.assertFalse("復帰後の通常テキストに下線が残ってはいけない", underline(flagsAt(e, 0, 1)))
    }
}

/**
 * Alt screen を抜けるときに、**文字の状態 (SGR / OSC 8 リンク) を持ち越さない**こと。
 *
 * 実機の報告 (0.8.354): **primary で描き続ける対話型 CLI** (alt screen を使わず履歴を
 * scrollback に残す作り) の中から、その CLI の機能で**全画面エディタを起こして戻ると、
 * 以降の出力が全部下線**になった。⚠ **エディタを直に起動して終了しても起きない** —
 * CLI が装飾を出している最中に Alt へ入るのが条件だった。
 *
 * ⚠ xterm の DECRST 1049 は DECRC 相当で「Alt に入る直前の SGR」を**復元する**が、
 * 本実装は**復元せず既定へ倒す** ([TerminalEmulator.resetTextStateOnPrimaryReturn])。
 */
class AltScreenExitTextStateTest {
    private val ESC = "\u001b"   // ⚠ 生の ESC を書かない (目に見えず、抜けてもテストが黙って空振りする)
    private fun emu() = TerminalEmulator(output = {}, initialRows = 5, initialColumns = 20)
    private fun feed(e: TerminalEmulator, s: String) = e.processBytes(s.toByteArray(Charsets.UTF_8))
    private fun cell(e: TerminalEmulator, row: Int, col: Int) = e.buffer.getScreenRow(row).getCell(col)
    private fun underline(f: Int) = (f and SgrAttribute.FLAG_UNDERLINE) != 0

    /** 症状そのもの: Alt に入る直前の下線を、抜けたときに持ち帰らないこと。 */
    @Test
    fun underlineBeforeAlt_isNotRestoredOnExit() {
        val e = emu()
        feed(e, "$ESC[4m")             // 装飾を出している最中に
        feed(e, "$ESC[?1049h")         // Alt へ (ここで下線が退避されていた)
        feed(e, "$ESC[0m")             // Alt の中では消えている
        feed(e, "$ESC[?1049l")         // Primary へ戻る
        feed(e, "X")
        assertFalse("Alt に入る前の下線を持ち帰ってはいけない", underline(cell(e, 0, 0).fgAttr))
    }

    /** 下線だけ直すと同じ報告をもう一度受けるので、色も持ち帰らないことを固定する。 */
    @Test
    fun colorBeforeAlt_isNotRestoredOnExit() {
        val e = emu()
        feed(e, "$ESC[31m$ESC[?1049h$ESC[0m$ESC[?1049l")
        feed(e, "X")
        assertEquals("色も持ち帰らない", SgrAttribute.DEFAULT, cell(e, 0, 0).fgAttr)
    }

    /** OSC 8 は SGR ではないので `\e[0m` では消えない。Alt を跨いで残ると直す手が無くなる。 */
    @Test
    fun osc8Link_isClearedOnAltScreenExit() {
        val e = emu()
        feed(e, "$ESC]8;;https://example.com$ESC\\")  // リンクを開いたまま
        feed(e, "$ESC[?1049h$ESC[?1049l")
        feed(e, "X")
        assertNull("Alt を跨いでリンクを持ち越さない", cell(e, 0, 0).link)
    }

    /**
     * ⚠ **ST (`ESC \`) は 2 バイト。`\` まで消費すること。**
     *
     * ESC を見た時点で OSC を終端して GROUND へ戻していた頃は、続く `\` が**通常の文字として
     * 画面に出て**いた (OSC 8 でリンクを張る CLI を動かすと画面に `\` が散り、しかもその
     * セルにはリンクが付く)。[osc8Link_isClearedOnAltScreenExit] が落ちていた真因もこれで、
     * リンクの持ち越しではなく **(0,0) に `\` が居座っていた**。
     */
    @Test
    fun osc8StringTerminatorIsFullyConsumed() {
        val e = emu()
        feed(e, "$ESC]8;;https://example.com$ESC\\")
        feed(e, "X")
        assertEquals("ST の `\\` が画面に出ている", 'X', cell(e, 0, 0).char)
        assertEquals("X はリンクの中にある", "https://example.com", cell(e, 0, 0).link)
    }

    /** BEL 終端でも同じこと。こちらは 1 バイトなので元から漏れない。 */
    @Test
    fun osc8BelTerminatorAlsoWorks() {
        val e = emu()
        feed(e, "$ESC]8;;https://example.com\u0007")
        feed(e, "X")
        assertEquals('X', cell(e, 0, 0).char)
        assertEquals("https://example.com", cell(e, 0, 0).link)
    }

    /**
     * 終端不正 (`ESC` の次が `\` 以外) は xterm 流儀でその場で打ち切り、続くバイトを
     * ESCAPE として読み直す。⚠ ここを「捨てる」にすると、後続のシーケンスが 1 つ消える。
     */
    @Test
    fun osc8BrokenTerminatorFallsBackToEscape() {
        val e = emu()
        // OSC の途中で ESC [ が来る = ST ではない。OSC は打ち切り、続く CSI が効く。
        feed(e, "$ESC]8;;https://example.com$ESC[2;3H")
        feed(e, "X")
        assertEquals("打ち切り後の CSI が効いていない", 'X', cell(e, 1, 2).char)
    }

    /** `reset` (RIS) でも消せること — ここが抜けていると利用者に直す手が無い。 */
    @Test
    fun osc8Link_isClearedByFullReset() {
        val e = emu()
        feed(e, "$ESC]8;;https://example.com$ESC\\")
        feed(e, "${ESC}c")                            // RIS
        feed(e, "X")
        assertNull("RIS でリンクが消えること", cell(e, 0, 0).link)
    }

    /** 位置は今までどおり戻すこと (SGR を戻さないことと混ぜない)。 */
    @Test
    fun cursorPosition_isStillRestored() {
        val e = emu()
        feed(e, "$ESC[3;5H")           // 3 行 5 列 (1-origin)
        feed(e, "$ESC[?1049h")
        feed(e, "$ESC[1;1H")
        feed(e, "$ESC[?1049l")
        assertEquals("行は戻る", 2, e.cursorRow)
        assertEquals("列は戻る", 4, e.cursorCol)
    }

    /** 1047 / 47 でも同じ約束にする (入口ごとに違う後始末をしない)。 */
    @Test
    fun decrst1047And47_alsoResetTextState() {
        for (mode in listOf("1047", "47")) {
            val e = emu()
            feed(e, "$ESC[4m$ESC[?${mode}h$ESC[?${mode}l")
            feed(e, "X")
            assertFalse("DECRST $mode でも既定へ戻す", underline(cell(e, 0, 0).fgAttr))
        }
    }
}
