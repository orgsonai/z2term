package com.zerotoship.z2term.ui.terminal.input

import com.zerotoship.z2term.core.TerminalSelection
import com.zerotoship.z2term.emulator.TerminalBuffer
import java.text.BreakIterator
import java.util.Locale

/**
 * 端末バッファ上の 1 点から「単語」の範囲を取り出す (ダブルタップ選択)。
 *
 * ⭐ **端末の単語は文章の単語ではない。** 掴みたいのはたいてい**パス・ホスト名・
 * ハッシュ・オプション**で、`/usr/local/bin/z2attach` や `192.168.10.20` や
 * `~/.bashrc` は**まとめて 1 つ**でないと使えない。そこで英数字に加えて `@-./_~` を
 * 単語の一部として扱う (xterm 系の端末が既定にしている集合と同じ)。
 * ⚠ `:` は入れない。`file.txt:42:` のような「位置つきの出力」から**ファイル名だけ**を
 * 取りたいことの方が多く、入れると必ず行番号までついてくる。
 *
 * 日本語には空白の切れ目が無いので、単純に伸ばすと**その行の日本語が丸ごと**選ばれる。
 * 漢字・かなを掴んだときだけ [BreakIterator] に切れ目を決めさせる。
 *
 * 折り返し (wrapped) は跨ぐ。端末の幅が狭いほど 1 つのパスが 2〜3 行に割れるので、
 * 跨がないと**この機能が一番効いてほしい場面で効かない**。
 */
object WordFinder {

    /** 英数字以外で「単語の一部」として扱う文字。 */
    private const val WORD_SYMBOLS = "@-./_~"

    /** 折り返しを辿る上限 (前後それぞれ)。長大な 1 論理行で無駄に走らないための歯止め。 */
    private const val MAX_WRAP_ROWS = 16

    private enum class CharClass { WORD, SPACE, OTHER }

    /**
     * (absRow, col) にかかる単語の範囲。空白を掴んだとき / 範囲外なら null
     * (何も選ばない = ダブルタップが空振りする)。
     */
    fun wordAt(buffer: TerminalBuffer, absRow: Int, col: Int): TerminalSelection? {
        if (absRow !in 0 until buffer.totalRows) return null
        val line = buildLogicalLine(buffer, absRow, col) ?: return null
        val text = line.text
        val tap = line.tapIndex
        if (tap !in text.indices) return null

        val tapped = text[tap]
        if (classOf(tapped) == CharClass.SPACE) return null

        val range = if (isCjk(tapped)) cjkRange(text, tap) else sameClassRange(text, tap)
        val from = range.first
        val to = range.second
        if (to <= from) return null

        val startRow = line.rows[from]
        val startCol = line.cols[from]
        val endRow = line.rows[to - 1]
        var endCol = line.cols[to - 1]
        // ⚠ 全角文字で終わるときは右半分のセルまで含める。含めないと**帯が文字の途中で
        //   切れて**見え、掴み直したくなる (取れる文字列は変わらない)。
        val lastRow = buffer.getRow(endRow)
        if (endCol + 1 < lastRow.columns && lastRow.getCell(endCol + 1).wideCont) endCol++

        return TerminalSelection.of(startRow, startCol, endRow, endCol)
    }

    // --- 切り出し -------------------------------------------------------------

    /** [tap] と同じ種類の文字が続く範囲 [from, to)。 */
    private fun sameClassRange(text: String, tap: Int): Pair<Int, Int> {
        val cls = classOf(text[tap])
        var from = tap
        while (from > 0 && classOf(text[from - 1]) == cls) from--
        var to = tap + 1
        while (to < text.length && classOf(text[to]) == cls) to++
        return from to to
    }

    /**
     * 漢字・かなの切れ目。⚠ **範囲を求めてから種類で絞り直さない** — [BreakIterator] が
     * 返す区切りをそのまま使う (「食べた」を「食べ」「た」に割るのはこちらの都合ではない)。
     */
    private fun cjkRange(text: String, tap: Int): Pair<Int, Int> {
        val bi = BreakIterator.getWordInstance(Locale.JAPANESE)
        bi.setText(text)
        var from = if (bi.isBoundary(tap)) tap else bi.preceding(tap)
        if (from == BreakIterator.DONE) from = 0
        var to = bi.following(tap)
        if (to == BreakIterator.DONE) to = text.length
        // 念のため: 区切りが取れなかった (from >= to) ときは 1 文字だけ返す。
        return if (to > from) from to to else tap to (tap + 1)
    }

    private fun classOf(c: Char): CharClass = when {
        c == ' ' || c == '\t' || c == '　' -> CharClass.SPACE
        Character.isLetterOrDigit(c) || c in WORD_SYMBOLS -> CharClass.WORD
        else -> CharClass.OTHER
    }

    /** 漢字・ひらがな・カタカナか (空白で切れない文字)。 */
    private fun isCjk(c: Char): Boolean {
        val b = Character.UnicodeBlock.of(c) ?: return false
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            b == Character.UnicodeBlock.HIRAGANA ||
            b == Character.UnicodeBlock.KATAKANA ||
            b == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
    }

    // --- 論理行の組み立て ------------------------------------------------------

    /**
     * 論理行のテキストと、1 文字ごとの出どころ (absRow, col)。
     * ⚠ 全角の右半分 (wideCont) はテキストに現れないので、添字と列は 1 対 1 にならない。
     * だから列を計算し直さず**覚えておく**。
     */
    private class Line(val text: String, val tapIndex: Int, val rows: IntArray, val cols: IntArray)

    private fun buildLogicalLine(buffer: TerminalBuffer, absRow: Int, tapCol: Int): Line? {
        var start = absRow
        while (start > 0 &&
            absRow - start < MAX_WRAP_ROWS &&
            buffer.getRow(start - 1).wrapped
        ) start--
        var end = absRow
        while (end < buffer.totalRows - 1 &&
            end - absRow < MAX_WRAP_ROWS &&
            buffer.getRow(end).wrapped
        ) end++

        val sb = StringBuilder()
        val rows = ArrayList<Int>()
        val cols = ArrayList<Int>()
        var tapIndex = -1
        for (r in start..end) {
            val row = buffer.getRow(r)
            for (c in 0 until row.columns) {
                val cell = row.getCell(c)
                if (cell.wideCont) continue
                if (r == absRow && c == tapCol) tapIndex = sb.length
                sb.append(cell.char)
                rows.add(r)
                cols.add(c)
            }
        }
        // タップ位置が本文に現れない (全角の右半分を突いた) ときは、その左のセルを掴む。
        if (tapIndex < 0 && tapCol > 0) {
            for (i in rows.indices.reversed()) {
                if (rows[i] == absRow && cols[i] < tapCol) { tapIndex = i; break }
            }
        }
        if (tapIndex < 0) return null
        return Line(sb.toString(), tapIndex, rows.toIntArray(), cols.toIntArray())
    }
}
