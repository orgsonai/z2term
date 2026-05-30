package com.zerotoship.z2term.ui.terminal.input

import com.zerotoship.z2term.emulator.TerminalBuffer

/**
 * 端末バッファ上のタップ位置から URL / OSC8 ハイパーリンクを取り出す。
 *
 * 優先順位:
 *  1. セルが OSC8 ハイパーリンク (`cell.link`) を持てばそれ (明示リンク)。
 *  2. 折り返し (wrapped) を含む論理行のテキストを組み立て、タップ位置を含む
 *     http(s)/ftp URL を正規表現で抽出。
 */
object UrlFinder {

    // http(s)/ftp スキーム URL。末尾の句読点は [trimTrailing] で除く。
    private val URL_REGEX = Regex("(?:https?|ftp)://[^\\s]+", RegexOption.IGNORE_CASE)

    // 行末などで URL に紛れがちな末尾文字 (閉じ括弧・句読点・引用符)。
    private const val TRAILING = ".,;:!?)]}>\"'"

    /** (absRow, col) のセルにかかる URL を返す。無ければ null。 */
    fun urlAt(buffer: TerminalBuffer, absRow: Int, col: Int): String? {
        if (absRow !in 0 until buffer.totalRows) return null
        val row = buffer.getRow(absRow)
        if (col !in 0 until row.columns) return null

        // 1) OSC8 ハイパーリンク (明示リンク) を最優先。
        val explicit = row.getCell(col).link
        if (!explicit.isNullOrEmpty()) return explicit

        // 2) 論理行のテキスト中で、タップ位置を含む URL を探す。
        val (text, offset) = buildLogicalLine(buffer, absRow, col) ?: return null
        for (m in URL_REGEX.findAll(text)) {
            if (offset in m.range) return trimTrailing(m.value)
        }
        return null
    }

    /**
     * absRow の可視セル (0..maxCols) のうち、URL / OSC8 リンクに属する列を true にした
     * BooleanArray を返す。リンクが無ければ null。下線描画用。
     *
     * urlAt と同じ規則 (OSC8 link 優先 + 折り返し論理行の http/ftp URL) で、
     * 行ごとに「どの列がリンクか」をまとめて求める。
     */
    fun linkedColumns(buffer: TerminalBuffer, absRow: Int, maxCols: Int): BooleanArray? {
        if (absRow !in 0 until buffer.totalRows) return null
        val row = buffer.getRow(absRow)
        val cols = minOf(maxCols, row.columns)
        if (cols <= 0) return null
        var any = false
        val marks = BooleanArray(cols)

        // 1) OSC8 明示リンクのセル。
        for (c in 0 until cols) {
            if (!row.getCell(c).link.isNullOrEmpty()) { marks[c] = true; any = true }
        }

        // 2) プレーン URL: absRow を含む論理行を組み立て、URL に重なる absRow の列をマーク。
        var start = absRow
        while (start > 0 && buffer.getRow(start - 1).wrapped) start--
        var end = absRow
        while (end < buffer.totalRows - 1 && buffer.getRow(end).wrapped) end++

        val sb = StringBuilder()
        for (r in start..end) {
            val rr = buffer.getRow(r)
            for (cc in 0 until rr.columns) {
                val cell = rr.getCell(cc)
                if (cell.wideCont) continue
                sb.append(cell.char)
            }
        }
        // URL が無い行 (大多数) は正規表現を回さず早期に抜ける。
        if (sb.indexOf("://") >= 0) {
            val text = sb.toString()
            val ranges = URL_REGEX.findAll(text)
                .map { it.range.first until (it.range.first + trimTrailing(it.value).length) }
                .toList()
            if (ranges.isNotEmpty()) {
                // absRow 先頭セルの論理行インデックス。
                var li = 0
                for (r in start until absRow) {
                    val rr = buffer.getRow(r)
                    for (cc in 0 until rr.columns) if (!rr.getCell(cc).wideCont) li++
                }
                for (cc in 0 until row.columns) {
                    val cell = row.getCell(cc)
                    if (cell.wideCont) continue
                    if (cc < cols && ranges.any { li in it }) { marks[cc] = true; any = true }
                    li++
                }
            }
        }
        return if (any) marks else null
    }

    private fun trimTrailing(url: String): String {
        var end = url.length
        while (end > 0 && url[end - 1] in TRAILING) end--
        return url.substring(0, end)
    }

    /**
     * absRow を含む論理行 (wrapped で連結された一連の行) のテキストと、
     * (absRow, tapCol) がそのテキスト内の何文字目かを返す。タップ位置が
     * wide-cont セル等で本文に現れない場合は null。
     */
    private fun buildLogicalLine(
        buffer: TerminalBuffer,
        absRow: Int,
        tapCol: Int
    ): Pair<String, Int>? {
        // 論理行の先頭 (前の行が wrapped でなくなるまで遡る)。
        var start = absRow
        while (start > 0 && buffer.getRow(start - 1).wrapped) start--
        // 論理行の末尾 (wrapped が続く限り進む)。
        var end = absRow
        while (end < buffer.totalRows - 1 && buffer.getRow(end).wrapped) end++

        val sb = StringBuilder()
        var tapOffset = -1
        for (r in start..end) {
            val row = buffer.getRow(r)
            for (c in 0 until row.columns) {
                val cell = row.getCell(c)
                if (cell.wideCont) continue
                if (r == absRow && c == tapCol) tapOffset = sb.length
                sb.append(cell.char)
            }
        }
        if (tapOffset < 0) return null
        return sb.toString() to tapOffset
    }
}
