package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ソフト折り返し (autowrap) した 1 論理行を範囲コピーすると、境目で改行/空白が入らず
 * 1 行のまま取れることを保証する回帰テスト ([TerminalBuffer.getRangeText])。
 */
class WrapCopyTest {

    private fun emu(cols: Int) = TerminalEmulator(output = {}, initialRows = 24, initialColumns = cols)

    @Test
    fun softWrappedAsciiLineCopiesAsOneLine() {
        val cols = 20
        val e = emu(cols)
        // cols(20) を超える 1 行コマンド。autowrap で 2 視覚行に折り返る。
        val cmd = "echo hello-world-12345-abcdefg"  // 29 文字
        e.processBytes(cmd.toByteArray(Charsets.UTF_8))

        val buf = e.buffer
        // 折り返し元行 (row 0) は wrapped=true のはず。
        assertEquals(true, buf.getScreenRow(0).wrapped)

        // 折り返し全体を範囲コピー (row0 全体 + row1 のコマンド末尾まで)。
        val endCol = (cmd.length - cols) - 1   // row1 上の最終文字の列
        val text = buf.getRangeText(0, 0, 1, endCol)
        assertEquals(cmd, text)
    }

    @Test
    fun wideCharWrapHasNoBoundarySpace() {
        // 右端に 1 セルしか残らない位置で全角文字が来て折り返すと、余りセル (空白) が
        // 境界に混ざりやすい。wideCont 埋め草化でコピーから除かれることを保証する。
        val cols = 5
        val e = emu(cols)
        // 半角4つ + 全角2つ。col4 に余り 1 セルが出て、全角は次行へ折り返す。
        // 期待コピー: "abcdあい" (境界に空白が入らない)。
        e.processBytes("abcdあい".toByteArray(Charsets.UTF_8))
        val buf = e.buffer
        assertEquals(true, buf.getScreenRow(0).wrapped)
        val text = buf.getRangeText(0, 0, 1, 3)
        assertEquals("abcdあい", text)
    }

    @Test
    fun hardNewlineKeepsLineBreak() {
        val cols = 40
        val e = emu(cols)
        e.processBytes("line1\r\nline2".toByteArray(Charsets.UTF_8))
        val buf = e.buffer
        val text = buf.getRangeText(0, 0, 1, 4)
        assertEquals("line1\nline2", text)
    }
}
