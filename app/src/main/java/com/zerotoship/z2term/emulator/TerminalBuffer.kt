package com.zerotoship.z2term.emulator

/**
 * ターミナルバッファ。
 *
 * 構造:
 *   - スクロールバック領域 (リングバッファ)
 *   - スクリーン領域 (rows × columns)
 *
 * スクロールバックはリングバッファで実装。
 * スクリーンが下に流れる際、スクリーン最上行をスクロールバックの末尾に追加。
 */
class TerminalBuffer(
    initialRows: Int,
    initialColumns: Int,
    scrollbackCapacity: Int = 5000
) {
    /** スクロールバック上限行数 (実行時変更可) */
    var scrollbackCapacity: Int = scrollbackCapacity
        set(value) {
            field = value.coerceAtLeast(0)
            // 既に超過していたら詰める
            while (scrollback.size > field) scrollback.removeFirst()
        }
    var rows: Int = initialRows
        private set
    var columns: Int = initialColumns
        private set

    /** スクリーン本体 (rows × columns) */
    private var screen: Array<TerminalRow> = Array(initialRows) { TerminalRow(initialColumns) }

    /** スクロールバック (リングバッファ) */
    private val scrollback = ArrayDeque<TerminalRow>(scrollbackCapacity)

    /** スクロールバック行数 */
    val scrollbackSize: Int get() = scrollback.size

    /** 全行数 (スクロールバック + スクリーン) */
    val totalRows: Int get() = scrollback.size + rows

    /**
     * 行を取得。
     * index 0 がスクロールバック最古行、totalRows-1 がスクリーン最下行。
     */
    fun getRow(index: Int): TerminalRow {
        return if (index < scrollback.size) {
            scrollback[index]
        } else {
            screen[index - scrollback.size]
        }
    }

    /** スクリーン上の行を取得 (0 = 最上行) */
    fun getScreenRow(row: Int): TerminalRow {
        require(row in 0 until rows) { "row=$row out of range [0,$rows)" }
        return screen[row]
    }

    /**
     * 1 行スクロールアップ。
     * スクリーン最上行をスクロールバックに移し、最下行に空行を追加。
     */
    fun scrollUp(fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        val firstRow = screen[0]
        // スクロールバックに追加
        if (scrollback.size >= scrollbackCapacity) {
            scrollback.removeFirst()
        }
        scrollback.addLast(firstRow)

        // 残りを上にシフト
        for (i in 0 until rows - 1) {
            screen[i] = screen[i + 1]
        }
        // 最下行は新規 (使い回さない方が安全)
        screen[rows - 1] = TerminalRow(columns).apply {
            clear(fg = fg, bg = bg)
        }
    }

    /**
     * 1 行スクロールダウン (逆方向)。
     * スクリーン最下行を捨て、最上行に空行を追加。
     */
    fun scrollDown(fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        for (i in rows - 1 downTo 1) {
            screen[i] = screen[i - 1]
        }
        screen[0] = TerminalRow(columns).apply { clear(fg = fg, bg = bg) }
    }

    /**
     * 指定範囲 [top, bottom] でスクロールアップ (内部スクロール)。
     * top 行は捨てられ、bottom 行が空になる。
     * スクロールバックには入らない。
     */
    fun scrollUpRegion(top: Int, bottom: Int, fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        if (top < 0 || bottom >= rows || top >= bottom) return
        for (i in top until bottom) {
            screen[i] = screen[i + 1]
        }
        screen[bottom] = TerminalRow(columns).apply { clear(fg = fg, bg = bg) }
    }

    /** 指定範囲でスクロールダウン (逆方向) */
    fun scrollDownRegion(top: Int, bottom: Int, fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        if (top < 0 || bottom >= rows || top >= bottom) return
        for (i in bottom downTo top + 1) {
            screen[i] = screen[i - 1]
        }
        screen[top] = TerminalRow(columns).apply { clear(fg = fg, bg = bg) }
    }

    /** スクリーン全消去 */
    fun clearScreen(fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        for (row in screen) row.clear(fg = fg, bg = bg)
    }

    /** リサイズ (簡易版: 既存内容を保持しつつサイズ変更) */
    fun resize(newRows: Int, newColumns: Int) {
        if (newRows == rows && newColumns == columns) return

        // 列幅変更: 全行を再構成
        if (newColumns != columns) {
            for (row in screen) row.resize(newColumns)
            for (row in scrollback) row.resize(newColumns)
        }

        // 行数変更: 増減
        if (newRows > rows) {
            // 行を増やす: スクロールバックから戻すか、新規空行
            val newScreen = Array(newRows) { i ->
                if (i < rows) {
                    screen[i]
                } else {
                    TerminalRow(newColumns)
                }
            }
            screen = newScreen
        } else if (newRows < rows) {
            // 行を減らす: 上部をスクロールバックへ
            val removed = rows - newRows
            for (i in 0 until removed) {
                if (scrollback.size >= scrollbackCapacity) {
                    scrollback.removeFirst()
                }
                scrollback.addLast(screen[i])
            }
            screen = Array(newRows) { i -> screen[i + removed] }
        }

        rows = newRows
        columns = newColumns
        markAllDirty()
    }

    /** 全行をダーティに */
    fun markAllDirty() {
        for (row in screen) row.dirty = true
    }

    /** 全行のテキストを文字列で取得 (コピー用) */
    fun getAllText(includeScrollback: Boolean = true): String {
        val sb = StringBuilder()
        if (includeScrollback) {
            for (row in scrollback) {
                sb.append(row.toText())
                if (!row.wrapped) sb.append('\n')
            }
        }
        for (i in 0 until rows) {
            sb.append(screen[i].toText())
            if (i < rows - 1) sb.append('\n')
        }
        return sb.toString()
    }

    /** スクロールバッククリア */
    fun clearScrollback() {
        scrollback.clear()
    }
}
