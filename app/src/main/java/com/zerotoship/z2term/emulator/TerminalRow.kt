package com.zerotoship.z2term.emulator

/**
 * ターミナルの 1 行を表す。
 * 内部的にはセル配列で管理。
 */
class TerminalRow(initialColumns: Int) {
    private var cells: Array<TerminalCell> = Array(initialColumns) { TerminalCell() }

    /** ダーティフラグ (再描画必要) */
    var dirty: Boolean = true

    /** 折り返し行か (この行の末尾が次行に継続している) */
    var wrapped: Boolean = false

    val columns: Int get() = cells.size

    /** 列幅変更時の再構成 */
    fun resize(newColumns: Int) {
        if (newColumns == cells.size) return
        val newCells = Array(newColumns) { i ->
            if (i < cells.size) cells[i] else TerminalCell()
        }
        cells = newCells
        dirty = true
    }

    fun getCell(col: Int): TerminalCell {
        require(col in cells.indices) { "col=$col out of range [0,${cells.size})" }
        return cells[col]
    }

    fun setChar(col: Int, ch: Char, fg: Int, bg: Int, wideCont: Boolean = false) {
        if (col !in cells.indices) return
        cells[col].char = ch
        cells[col].fgAttr = fg
        cells[col].bgAttr = bg
        cells[col].wideCont = wideCont
        dirty = true
    }

    /** [from, to) を消去 */
    fun clear(from: Int = 0, to: Int = cells.size, fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        val start = from.coerceAtLeast(0)
        val end = to.coerceAtMost(cells.size)
        for (i in start until end) {
            cells[i].setClearedWith(fg, bg)
        }
        dirty = true
    }

    /** col 位置に文字を挿入 (右側を右にシフト) */
    fun insertChars(col: Int, count: Int, fg: Int, bg: Int) {
        if (col !in cells.indices || count <= 0) return
        val shiftCount = (cells.size - col - count).coerceAtLeast(0)
        // 右にシフト
        for (i in cells.size - 1 downTo col + count) {
            if (i - count >= col) {
                cells[i].copyFrom(cells[i - count])
            }
        }
        // 空白で埋める
        for (i in col until (col + count).coerceAtMost(cells.size)) {
            cells[i].setClearedWith(fg, bg)
        }
        dirty = true
    }

    /** col 位置から count 文字削除 (左側にシフト) */
    fun deleteChars(col: Int, count: Int, fg: Int, bg: Int) {
        if (col !in cells.indices || count <= 0) return
        // 左にシフト
        for (i in col until cells.size - count) {
            cells[i].copyFrom(cells[i + count])
        }
        // 末尾を空白で
        for (i in (cells.size - count).coerceAtLeast(col) until cells.size) {
            cells[i].setClearedWith(fg, bg)
        }
        dirty = true
    }

    /**
     * 行の表示用文字列を返す (デバッグ・コピー用)。
     * 末尾の空白は除去し、wide-cont セルは出力しない (左セルが本体の文字を持つ)。
     */
    fun toText(): String {
        var end = cells.size - 1
        while (end >= 0 && cells[end].char == ' ' && !cells[end].wideCont) end--
        if (end < 0) return ""
        return buildString(end + 1) {
            for (i in 0..end) {
                val c = cells[i]
                if (!c.wideCont) append(c.char)
            }
        }
    }

    fun copyFrom(other: TerminalRow) {
        if (cells.size != other.cells.size) {
            cells = Array(other.cells.size) { TerminalCell() }
        }
        for (i in cells.indices) {
            cells[i].copyFrom(other.cells[i])
        }
        wrapped = other.wrapped
        dirty = true
    }
}
