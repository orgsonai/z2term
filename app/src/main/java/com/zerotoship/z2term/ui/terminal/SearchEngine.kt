package com.zerotoship.z2term.ui.terminal

import com.zerotoship.z2term.emulator.TerminalBuffer

/**
 * スクロールバック検索のヒット 1 件。
 *
 * 列は **セル列** (描画グリッド) で表す。全角 (CJK) は 2 セル幅なので、
 * 文字列インデックスではなくセル列で持つことで [TerminalRenderer] のハイライトと
 * 正確に一致させる。
 *
 * @param absRow 絶対行 (0 = scrollback 最古、totalRows-1 = 画面最下行)
 * @param colStart ヒット開始セル列 (含む)
 * @param colEnd   ヒット終了セル列 (含まない)
 */
data class SearchMatch(
    val absRow: Int,
    val colStart: Int,
    val colEnd: Int
)

/**
 * 端末バッファ (scrollback + 画面) からテキストを検索する。
 *
 * 設計:
 * - 1 行ごとに「可視文字列」と「各文字の開始セル列」を組み立て、`indexOf` ループで
 *   全出現を集める。`wideCont` (全角の右半分) はスキップし、左セルが本体の 1 文字を持つ。
 * - 大文字小文字無視は **1 文字単位の lowercase** で行い、文字数 (= セル列マップとの対応) を
 *   崩さないようにする (`String.lowercase()` は一部 Unicode で長さが変わるため使わない)。
 * - マッチはスナップショット。呼び出し側 (検索バー) が、開いた時点 / 入力確定時に再計算する。
 *   実行中コマンドで scrollback が伸びると absRow がずれる追従は v2。
 */
object SearchEngine {

    fun search(buffer: TerminalBuffer, query: String, ignoreCase: Boolean = true): List<SearchMatch> {
        if (query.isEmpty()) return emptyList()
        val needle = if (ignoreCase) query.map { it.lowercaseChar() }.joinToString("") else query

        val out = ArrayList<SearchMatch>()
        val total = buffer.totalRows
        for (i in 0 until total) {
            // 検索は Main で走り emulator スレッドが同時に書き込む (= 選択コピーと同じ前提)。
            // resize 等でバッファ構造が入れ替わる瞬間に getRow/getCell が範囲外になり得るので、
            // 行単位で握り潰して次行へ進む (1 行落としても次回入力で再計算される)。
            val row = runCatching { buffer.getRow(i) }.getOrNull() ?: continue
            val cols = row.columns
            val sb = StringBuilder(cols)
            // starts[k] = k 番目の可視文字の開始セル列。末尾に番兵 (最終文字の次のセル列) を足す。
            val starts = ArrayList<Int>(cols + 1)
            val scanned = runCatching {
                var c = 0
                while (c < cols) {
                    val cell = row.getCell(c)
                    if (cell.wideCont) { c++; continue }
                    val isWide = c + 1 < cols && row.getCell(c + 1).wideCont
                    val ch = cell.char
                    sb.append(if (ignoreCase) ch.lowercaseChar() else ch)
                    starts.add(c)
                    c += if (isWide) 2 else 1
                }
                c
            }.getOrNull() ?: continue
            if (sb.isEmpty()) continue
            starts.add(scanned) // 番兵 = 行内容の末尾セル列

            val hay = sb.toString()
            var from = 0
            while (true) {
                val idx = hay.indexOf(needle, from)
                if (idx < 0) break
                val endChar = idx + needle.length
                val colStart = starts[idx]
                val colEnd = starts[endChar.coerceAtMost(starts.size - 1)]
                out.add(SearchMatch(i, colStart, colEnd))
                from = idx + 1
            }
        }
        return out
    }
}
