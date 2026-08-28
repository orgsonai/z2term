package com.zerotoship.z2term.ui.terminal.input

import com.zerotoship.z2term.emulator.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ダブルタップ選択 ([WordFinder]) の範囲。
 *
 * ⭐ 期待値は「掴んだ結果として取れる文字列」で書く。列番号で書くと、全角や折り返しの
 * 扱いを変えたときに**テストの方を直すことになり**、何を守っているのか分からなくなる。
 */
class WordFinderTest {

    private fun emu(cols: Int, text: String): TerminalEmulator {
        val e = TerminalEmulator(output = {}, initialRows = 24, initialColumns = cols)
        e.processBytes(text.toByteArray(Charsets.UTF_8))
        return e
    }

    /** (row, col) を掴んだときに選ばれる文字列。選ばれなければ null。 */
    private fun pick(e: TerminalEmulator, row: Int, col: Int): String? {
        val sel = WordFinder.wordAt(e.buffer, row, col) ?: return null
        return e.buffer.getRangeText(sel.startAbsRow, sel.startCol, sel.endAbsRow, sel.endCol)
    }

    @Test
    fun pathIsOneWord() {
        val e = emu(80, "cat /usr/local/bin/z2attach")
        // "/usr/local/bin/z2attach" の途中 ("local" の l) を掴む。
        assertEquals("/usr/local/bin/z2attach", pick(e, 0, 9))
    }

    @Test
    fun ipAddressIsOneWord() {
        val e = emu(80, "ssh root@192.168.10.20")
        // "@" も "." も単語の一部なので、ユーザー名込みで 1 つになる。
        assertEquals("root@192.168.10.20", pick(e, 0, 12))
    }

    @Test
    fun homeRelativePathIsOneWord() {
        val e = emu(80, "vi ~/.bashrc")
        assertEquals("~/.bashrc", pick(e, 0, 5))
    }

    @Test
    fun colonSplitsFileFromLineNumber() {
        // ⚠ ここが `:` を単語に入れない理由。grep 出力からファイル名だけを取りたい。
        val e = emu(80, "src/main.kt:42:error")
        assertEquals("src/main.kt", pick(e, 0, 4))
    }

    @Test
    fun blankPicksNothing() {
        val e = emu(80, "ls -la")
        assertNull(pick(e, 0, 30))
    }

    @Test
    fun symbolRunIsOneWord() {
        val e = emu(80, "a ==> b")
        assertEquals("==>", pick(e, 0, 3))
    }

    @Test
    fun wrappedPathCrossesTheFold() {
        // 幅 20 で折り返す長いパス。折り返しを跨いで 1 つとして取れること。
        val cols = 20
        val path = "/usr/local/share/doc/z2term/handbook.txt"
        val e = emu(cols, "less $path")
        // 1 行目の末尾寄り (折り返し前) を掴む。
        assertEquals(path, pick(e, 0, 10))
        // 折り返した先を掴んでも同じ範囲になる。
        assertEquals(path, pick(e, 1, 3))
    }

    @Test
    fun japaneseIsNotSelectedWholeLine() {
        val e = emu(80, "設定を保存しました")
        val picked = pick(e, 0, 0)
        // 行まるごと (9 文字) にはならないこと。⚠ 区切り位置そのものは
        // BreakIterator (端末ごとの ICU) 任せなので、長さで縛らない。
        assertEquals(true, picked != null && picked.length < 9)
    }

    @Test
    fun wideCharRightHalfPicksTheCharacter() {
        // 全角の右半分セルを突いても、その文字を含む範囲が返ること (null にしない)。
        val e = emu(80, "あい")
        assertEquals(true, pick(e, 0, 1) != null)
    }
}
