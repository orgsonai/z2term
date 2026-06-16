package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DECSTBM でスクロール領域を狭めた状態で改行(LF)スクロールしても、領域外の固定行
 * (vim 等の下部ステータス/コマンド行 = 行番号やルーラ表示) が一緒に押し上げられない
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

        // 領域内スクロールは scrollback に押し出さない (固定行ありの領域では履歴に残さない)。
        assertEquals(0, e.buffer.scrollbackSize)
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
}
