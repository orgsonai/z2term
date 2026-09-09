package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DECSTBM でスクロール領域を狭めた状態で改行(LF)スクロールしても、領域外の固定行
 * (TUI の下部ステータス/コマンド行 = 行番号やルーラ表示) が一緒に押し上げられない
 * ことを保証する回帰テスト。
 *
 * 元バグ: lineFeed が領域を無視して全画面 scrollUp を呼んでいたため、固定行が毎回 1 行
 * ずつ上にずれ、毎行に行番号が焼き付いて見えた。
 */
class ScrollRegionLineFeedTest {

    private val ESC = "\u001B"

    private fun emu(rows: Int, cols: Int) =
        TerminalEmulator(output = {}, initialRows = rows, initialColumns = cols)

    private fun feed(e: TerminalEmulator, s: String) =
        e.processBytes(s.toByteArray(Charsets.US_ASCII))

    /** 下 1 行を固定 (DECSTBM 1;rows-1) して領域内で改行スクロールしても固定行は不動。 */
    @Test
    fun lineFeedInRegion_keepsBottomFixedLine() {
        val rows = 5
        val e = emu(rows, 10)

        // 最下行(row5=index4)に固定ステータスを書く: ESC[5;1H で移動して "STATUS"。
        feed(e, "$ESC[5;1HSTATUS")
        // 下 1 行を除いた領域 (1..4 行目 = index0..3) をスクロール領域に設定。
        feed(e, "$ESC[1;4r")
        // 領域先頭へ。DECSTBM 後は左上(0,0)へ移動する実装。
        feed(e, "$ESC[1;1H")

        // 領域の高さ(4)を超える行数を流し込み、領域内スクロールを何度も起こす。
        // 末尾改行で余計に 1 行スクロールしないよう、行間だけ CRLF を入れる。
        for (i in 0..9) {
            if (i > 0) feed(e, "\r\n")
            feed(e, "L$i")
        }

        // 固定行(最下行)は一切動かず "STATUS" のまま。
        assertEquals("STATUS", e.buffer.getScreenRow(rows - 1).toText().trimEnd())

        // 領域内 (index0..3) には最後に書いた行が残る。最終書き込み行 index3 は L9。
        assertEquals("L9", e.buffer.getScreenRow(3).toText().trimEnd())

        // 上端から押し出された本文を保存し、固定行は履歴へ混ぜない。
        assertEquals(6, e.buffer.scrollbackSize)
        assertEquals((0..5).map { "L$it" }, (0 until 6).map { e.buffer.getRow(it).toText().trimEnd() })
    }

    /** 領域未設定(全画面)の通常改行は従来どおり最上行を scrollback へ押し出す。 */
    @Test
    fun lineFeedFullScreen_pushesToScrollback() {
        val rows = 3
        val e = emu(rows, 10)
        // 3 行画面を溢れさせる: 5 行書けば最上 2 行分が scrollback へ。
        for (i in 0..4) {
            if (i > 0) feed(e, "\r\n")
            feed(e, "L$i")
        }
        assertEquals(2, e.buffer.scrollbackSize)
    }

    @Test
    fun explicitScrollUpPreservesHistoryAndFixedFooter() {
        val e = emu(5, 10)
        for (i in 1..5) feed(e, "$ESC[${i};1HL$i")
        feed(e, "$ESC[1;4r$ESC[2S")
        assertEquals(2, e.buffer.scrollbackSize)
        assertEquals("L1", e.buffer.getRow(0).toText().trimEnd())
        assertEquals("L2", e.buffer.getRow(1).toText().trimEnd())
        assertEquals("L3", e.buffer.getScreenRow(0).toText().trimEnd())
        assertEquals("L5", e.buffer.getScreenRow(4).toText().trimEnd())
        // Repainting the visible region must not overwrite rows already in history.
        feed(e, "$ESC[1;1HREPAINT")
        assertEquals("L1", e.buffer.getRow(0).toText().trimEnd())
    }

    @Test
    fun explicitFullScreenScrollPreservesHistory() {
        val e = emu(3, 10)
        feed(e, "FIRST$ESC[S")
        assertEquals(1, e.buffer.scrollbackSize)
        assertEquals("FIRST", e.buffer.getRow(0).toText().trimEnd())
    }

    @Test
    fun interiorRegionDoesNotAddHistoryOrMoveFixedRows() {
        val e = emu(5, 10)
        for (i in 1..5) feed(e, "$ESC[${i};1HL$i")
        feed(e, "$ESC[2;4r$ESC[S$ESC[4;1H\n")
        assertEquals(0, e.buffer.scrollbackSize)
        assertEquals("L1", e.buffer.getScreenRow(0).toText().trimEnd())
        assertEquals("L5", e.buffer.getScreenRow(4).toText().trimEnd())
    }

    @Test
    fun deleteLineAtTopIsDeletionNotHistory() {
        val e = emu(3, 10)
        feed(e, "REMOVE$ESC[2;1HKEEP$ESC[1;1H$ESC[M")
        assertEquals(0, e.buffer.scrollbackSize)
        assertEquals("KEEP", e.buffer.getScreenRow(0).toText().trimEnd())
    }

    @Test
    fun alternateRegionNeverContaminatesPrimaryHistory() {
        val e = emu(3, 10)
        feed(e, "PRIMARY$ESC[S$ESC[?1049hALT$ESC[1;2r$ESC[S$ESC[2;1H\n$ESC[?1049l")
        assertEquals(1, e.buffer.scrollbackSize)
        assertEquals("PRIMARY", e.buffer.getRow(0).toText().trimEnd())
    }

    @Test
    fun indexInTopRegionKeepsHistory() {
        val e = emu(3, 10)
        feed(e, "FIRST$ESC[1;2r$ESC[2;1H${ESC}D")
        assertEquals(1, e.buffer.scrollbackSize)
        assertEquals("FIRST", e.buffer.getRow(0).toText().trimEnd())
    }

    @Test
    fun historyCapacityIsRespectedIncludingZero() {
        val buffer = TerminalBuffer(3, 10, scrollbackCapacity = 2)
        repeat(4) { i ->
            buffer.getScreenRow(0).setChar(0, ('A'.code + i).toChar(), SgrAttribute.DEFAULT, SgrAttribute.DEFAULT)
            buffer.scrollUpRegion(0, 1, saveToScrollback = true)
        }
        assertEquals(2, buffer.scrollbackSize)
        assertEquals("C", buffer.getRow(0).toText().trimEnd())
        assertEquals("D", buffer.getRow(1).toText().trimEnd())
        buffer.scrollbackCapacity = 0
        buffer.scrollUp()
        buffer.scrollUpRegion(0, 1, saveToScrollback = true)
        assertEquals(0, buffer.scrollbackSize)
    }

    @Test
    fun lineFeedBelowRegionDoesNotScrollItsContents() {
        val e = emu(5, 10)
        feed(e, "TOP$ESC[1;3r$ESC[5;1HFOOTER\n")
        assertEquals(0, e.buffer.scrollbackSize)
        assertEquals("TOP", e.buffer.getScreenRow(0).toText().trimEnd())
        assertEquals("FOOTER", e.buffer.getScreenRow(4).toText().trimEnd())
        assertEquals(4, e.cursorRow)
    }
}
