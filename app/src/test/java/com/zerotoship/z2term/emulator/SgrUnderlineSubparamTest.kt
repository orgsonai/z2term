package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SgrUnderlineSubparamTest {
    private val ESC = ""

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
    private val ESC = ""
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
