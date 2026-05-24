package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ピンチ(列幅・行数の拡大/縮小)往復で画面外へ追い出した内容が戻ることを保証する
 * 回帰テスト。元バグ: 縮小→拡大で右端の文字 / 上部の行が消えていた (commit 4cce382)。
 *
 * 純ロジック (emulator パッケージ) なので JVM 上で実機なしに確認できる。
 */
class PinchResizeRegressionTest {

    /** 列縮小で行末の実内容セルを捨てず、広げ直すと右端の文字が復活する。 */
    @Test
    fun columnRoundTrip_restoresTrailingChars() {
        val row = TerminalRow(20)
        // 0..14 に文字、15..19 は空白 → 実内容幅 15。
        for (c in 0..14) row.setChar(c, 'X', SgrAttribute.DEFAULT, SgrAttribute.DEFAULT)
        assertEquals("XXXXXXXXXXXXXXX", row.toText())

        // 縮小: 余白(15..19)だけ捨て、実内容は残る (keep = max(15,10) = 15)。
        row.resize(10)
        assertEquals("XXXXXXXXXXXXXXX", row.toText())

        // 拡大し直すと右端の文字 (col 14) が復活する。
        row.resize(20)
        assertEquals('X', row.getCell(14).char)
        assertEquals("XXXXXXXXXXXXXXX", row.toText())
    }

    /** 行縮小で上部へ押し出した行が、拡大し直すと画面上部へ戻る。 */
    @Test
    fun rowRoundTrip_restoresScrolledOutLines() {
        val buf = TerminalBuffer(initialRows = 5, initialColumns = 10)
        // 各行に行番号マーカーを置く ("0".."4")。
        for (r in 0..4) {
            buf.getScreenRow(r).setChar(0, '0' + r, SgrAttribute.DEFAULT, SgrAttribute.DEFAULT)
        }

        // 縮小 (cursorRow=4): 上 2 行 "0","1" を scrollback へ push。補正量は +2。
        val pushed = buf.resize(newRows = 3, newColumns = 10, cursorRow = 4)
        assertEquals(2, pushed)
        assertEquals(2, buf.scrollbackSize)
        assertEquals("2", buf.getScreenRow(0).toText())

        // 拡大し直すと scrollback の 2 行が上部へ戻り、補正量は -2。
        val pulled = buf.resize(newRows = 5, newColumns = 10, cursorRow = 0)
        assertEquals(-2, pulled)
        assertEquals(0, buf.scrollbackSize)
        assertEquals("0", buf.getScreenRow(0).toText())
        assertEquals("4", buf.getScreenRow(4).toText())
    }
}
