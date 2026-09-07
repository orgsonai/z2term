package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * コマンド単位の頭出し (`OSC 133 ; A` の印) の検証。
 *
 * ⚠ **印は行そのものに付ける**ので、スクロールしても付いて回らなければ意味がない
 * (絶対行番号で覚えると、スクロールバックが溢れた瞬間に全部ずれる)。そこを具体例で固定する。
 */
class PromptMarkTest {

    private val ESC = "\u001B"

    /** ⚠ OSC は BEL (か ST) で終わる。終端を送らないと解釈されないまま溜まる。 */
    private val BEL = "\u0007"

    private fun emu(rows: Int = 5, cols: Int = 20) =
        TerminalEmulator(output = {}, initialRows = rows, initialColumns = cols)

    private fun feed(e: TerminalEmulator, s: String) =
        e.processBytes(s.toByteArray(Charsets.UTF_8))

    /** プロンプトを 1 つ出してコマンドを打つ (シェルが出すのと同じ形)。 */
    private fun prompt(e: TerminalEmulator, command: String) {
        feed(e, "$ESC]133;A$BEL$ $command\r\n")
    }

    @Test
    fun theCursorRowIsMarked() {
        val e = emu()
        feed(e, "$ESC]133;A$BEL")
        assertTrue(e.buffer.getRow(0).promptMark)
        assertFalse(e.buffer.getRow(1).promptMark)
    }

    @Test
    fun otherShellIntegrationLettersDoNothing() {
        // B / C / D は今は使わない。⚠ 知らない字で例外を投げると受信ループごと落ちる。
        val e = emu()
        feed(e, "$ESC]133;B$BEL$ESC]133;C$BEL$ESC]133;D;0$BEL")
        assertFalse(e.buffer.getRow(0).promptMark)
    }

    @Test
    fun theMarkTravelsIntoScrollback() {
        // 5 行の画面で 8 個のコマンドを打つ = 最初の方はスクロールバックへ押し出される。
        val e = emu(rows = 5)
        repeat(8) { prompt(e, "echo $it") }
        val marked = (0 until e.buffer.totalRows).filter { e.buffer.getRow(it).promptMark }
        assertEquals(8, marked.size)
        // ⚠ 押し出された行はスクロールバックの側に居る。
        assertTrue("印がスクロールバックへ移っていない", marked.first() < e.buffer.scrollbackSize)
    }

    @Test
    fun theAlternateScreenIsNotMarked() {
        // 全画面を描く TUI が出した OSC 133 を拾うと、履歴に残らない画面に印が付く。
        val e = emu()
        feed(e, "$ESC[?1049h")
        feed(e, "$ESC]133;A$BEL")
        feed(e, "$ESC[?1049l")
        assertEquals(0, (0 until e.buffer.totalRows).count { e.buffer.getRow(it).promptMark })
    }

    @Test
    fun clearingTheWholeRowDropsTheMark() {
        // 使い回された行に前の印が残ると、何も無い行が「コマンドの頭」として拾われる。
        val e = emu()
        feed(e, "$ESC]133;A${BEL}hello")
        assertTrue(e.buffer.getRow(0).promptMark)
        feed(e, "$ESC[2K")
        assertFalse(e.buffer.getRow(0).promptMark)
    }

    @Test
    fun searchingUpAndDownFindsTheNeighbours() {
        val e = emu(rows = 5)
        repeat(4) { prompt(e, "echo $it") }
        val marked = (0 until e.buffer.totalRows).filter { e.buffer.getRow(it).promptMark }
        assertEquals(4, marked.size)

        assertEquals(marked[1], e.buffer.prevPromptRow(marked[2]))
        assertEquals(marked[3], e.buffer.nextPromptRow(marked[2]))
        // 端では null (⚠ 黙って一番上/下へ飛ばさない)。
        assertNull(e.buffer.prevPromptRow(marked[0]))
        assertNull(e.buffer.nextPromptRow(marked[3]))
    }

    @Test
    fun theSearchStartsStrictlyBeyondTheGivenRow() {
        // 自分自身の行は返さない (返すと ∧ を押しても動かない)。
        val e = emu()
        feed(e, "$ESC]133;A$BEL")
        assertNull(e.buffer.prevPromptRow(0))
        assertNull(e.buffer.nextPromptRow(0))
    }

    @Test
    fun aCommandRangeStartsAtItsOwnPromptRow() {
        val e = emu(rows = 5)
        repeat(3) { prompt(e, "echo $it") }
        val marked = (0 until e.buffer.totalRows).filter { e.buffer.getRow(it).promptMark }
        // ⭐ 印の行そのものを渡したら、その行が頭 (1 つ前のコマンドを返さない)。
        assertEquals(marked[1], e.buffer.commandRangeAt(marked[1])?.first)
        // 次の頭の 1 つ手前まで。
        assertEquals(marked[2] - 1, e.buffer.commandRangeAt(marked[1])?.last)
    }

    @Test
    fun aRowInTheOutputBelongsToTheCommandAbove() {
        val e = emu(rows = 8)
        feed(e, "$ESC]133;A$BEL$ ls\r\n")
        feed(e, "a\r\nb\r\n")
        val head = (0 until e.buffer.totalRows).first { e.buffer.getRow(it).promptMark }
        assertEquals(head, e.buffer.commandRangeAt(head + 2)?.first)
    }

    @Test
    fun theLastCommandRunsToTheEnd() {
        // 最後のコマンドには「次の頭」が無い。⚠ そこで打ち切ると出力が半分しか選べない。
        val e = emu(rows = 8)
        repeat(2) { prompt(e, "echo $it") }
        val last = (0 until e.buffer.totalRows).last { e.buffer.getRow(it).promptMark }
        assertEquals(e.buffer.totalRows - 1, e.buffer.commandRangeAt(last)?.last)
    }

    @Test
    fun withoutAnyMarkThereIsNoRange() {
        // 仕掛けの入っていないシェル (SSH の先など) では印が 1 つも無い。
        val e = emu()
        feed(e, "hello\r\n")
        assertNull(e.buffer.commandRangeAt(0))
    }
}
