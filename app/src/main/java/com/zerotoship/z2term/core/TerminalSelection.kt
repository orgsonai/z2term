package com.zerotoship.z2term.core

/**
 * Renderer が計測した 1 セルあたりの寸法と canvas 領域。
 * InputView がピクセル → セル変換に使うため StateFlow で公開する。
 * topAbsRow は scrollOffset / cursorRow / scrollback サイズに依存するため
 * 利用側で都度計算する。
 */
data class CellMetrics(
    val cellW: Float = 0f,
    val lineHeight: Float = 0f,
    val canvasRows: Int = 0,
    val canvasCols: Int = 0
)

/**
 * ターミナル上のテキスト選択範囲。
 * (startAbsRow, startCol) と (endAbsRow, endCol) は常に
 * 「読み順で start ≤ end」になるよう正規化されている。
 * - anchor: 選択開始点 (long-press 位置)
 * - head: 選択末端 (現在のドラッグ位置)
 *
 * 描画と「コピー」テキスト抽出に使う絶対座標 (scrollback + screen) で保持。
 */
data class TerminalSelection(
    val startAbsRow: Int,
    val startCol: Int,
    val endAbsRow: Int,
    val endCol: Int
) {
    fun contains(absRow: Int): Boolean = absRow in startAbsRow..endAbsRow

    /** abs 行内で選択がカバーする列範囲 [from, to) を返す。 */
    fun colRangeFor(absRow: Int, maxCols: Int): Pair<Int, Int> {
        if (!contains(absRow)) return 0 to 0
        val from = if (absRow == startAbsRow) startCol else 0
        val to = if (absRow == endAbsRow) endCol + 1 else maxCols
        return from.coerceIn(0, maxCols) to to.coerceIn(0, maxCols)
    }

    companion object {
        fun of(r1: Int, c1: Int, r2: Int, c2: Int): TerminalSelection {
            val swap = r1 > r2 || (r1 == r2 && c1 > c2)
            return if (swap) TerminalSelection(r2, c2, r1, c1)
            else TerminalSelection(r1, c1, r2, c2)
        }
    }
}
