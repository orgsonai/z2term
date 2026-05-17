package com.zerotoship.z2term.emulator

/**
 * ターミナルバッファ。
 *
 * 構造:
 *   - スクロールバック領域 (リングバッファ、Primary スクリーンと連動)
 *   - Primary スクリーン (通常表示、rows × columns)
 *   - Alternate スクリーン (vim/htop 等の全画面アプリ用、rows × columns)
 *
 * スクロールバックは Primary がアクティブな間のみ更新される。
 * Alternate がアクティブな間にスクロールしても履歴には残らない。
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
            while (scrollback.size > field) scrollback.removeFirst()
        }
    var rows: Int = initialRows
        private set
    var columns: Int = initialColumns
        private set

    /** Primary スクリーン */
    private var primary: Array<TerminalRow> = Array(initialRows) { TerminalRow(initialColumns) }

    /** Alternate スクリーン */
    private var alternate: Array<TerminalRow> = Array(initialRows) { TerminalRow(initialColumns) }

    /** 現在アクティブなスクリーン */
    private var screen: Array<TerminalRow> = primary

    /** Primary がアクティブか */
    var primaryActive: Boolean = true
        private set

    /** スクロールバック (リングバッファ) — Primary のみ更新 */
    private val scrollback = ArrayDeque<TerminalRow>(scrollbackCapacity)

    /** スクロールバック行数 */
    val scrollbackSize: Int get() = if (primaryActive) scrollback.size else 0

    /** 全行数 (スクロールバック + スクリーン) */
    val totalRows: Int get() = scrollbackSize + rows

    /**
     * 行を取得。
     * index 0 がスクロールバック最古行、totalRows-1 がスクリーン最下行。
     * Alternate アクティブ時はスクロールバックは存在しない。
     */
    fun getRow(index: Int): TerminalRow {
        return if (primaryActive && index < scrollback.size) {
            scrollback[index]
        } else {
            screen[index - scrollbackSize]
        }
    }

    /** スクリーン上の行を取得 (0 = 最上行) */
    fun getScreenRow(row: Int): TerminalRow {
        require(row in 0 until rows) { "row=$row out of range [0,$rows)" }
        return screen[row]
    }

    /**
     * Alternate スクリーンに切替。
     * @param clear true なら alternate を消去してから使う (DECSET 1049/1047 相当)
     */
    fun switchToAlternate(clear: Boolean, fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        if (!primaryActive) return
        if (clear) {
            for (row in alternate) row.clear(fg = fg, bg = bg)
        }
        screen = alternate
        primaryActive = false
        markAllDirty()
    }

    /** Primary スクリーンに復帰 */
    fun switchToPrimary() {
        if (primaryActive) return
        screen = primary
        primaryActive = true
        markAllDirty()
    }

    /**
     * 1 行スクロールアップ。
     * Primary 時はスクリーン最上行をスクロールバックに移し、最下行に空行を追加。
     * Alternate 時は履歴に残さず捨てる。
     */
    fun scrollUp(fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        val firstRow = screen[0]
        if (primaryActive) {
            if (scrollback.size >= scrollbackCapacity) {
                scrollback.removeFirst()
            }
            scrollback.addLast(firstRow)
        }

        for (i in 0 until rows - 1) {
            screen[i] = screen[i + 1]
        }
        screen[rows - 1] = TerminalRow(columns).apply { clear(fg = fg, bg = bg) }
    }

    /** 1 行スクロールダウン (逆方向) — スクリーン最下行を捨て最上行に空行 */
    fun scrollDown(fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        for (i in rows - 1 downTo 1) {
            screen[i] = screen[i - 1]
        }
        screen[0] = TerminalRow(columns).apply { clear(fg = fg, bg = bg) }
    }

    /** 指定範囲 [top, bottom] でスクロールアップ — スクロールバックには入らない */
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

    /** アクティブスクリーン全消去 */
    fun clearScreen(fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        for (row in screen) row.clear(fg = fg, bg = bg)
    }

    /** リサイズ (両スクリーン + スクロールバックを揃える) */
    fun resize(newRows: Int, newColumns: Int) {
        if (newRows == rows && newColumns == columns) return

        if (newColumns != columns) {
            for (row in primary) row.resize(newColumns)
            for (row in alternate) row.resize(newColumns)
            for (row in scrollback) row.resize(newColumns)
        }

        primary = resizeScreen(primary, newRows, newColumns, pushToScrollback = primaryActive)
        alternate = resizeScreen(alternate, newRows, newColumns, pushToScrollback = false)
        screen = if (primaryActive) primary else alternate

        rows = newRows
        columns = newColumns
        markAllDirty()
    }

    private fun resizeScreen(
        old: Array<TerminalRow>,
        newRows: Int,
        newColumns: Int,
        pushToScrollback: Boolean
    ): Array<TerminalRow> {
        if (newRows == old.size) return old
        return if (newRows > old.size) {
            Array(newRows) { i ->
                if (i < old.size) old[i] else TerminalRow(newColumns)
            }
        } else {
            val removed = old.size - newRows
            if (pushToScrollback) {
                for (i in 0 until removed) {
                    if (scrollback.size >= scrollbackCapacity) scrollback.removeFirst()
                    scrollback.addLast(old[i])
                }
            }
            Array(newRows) { i -> old[i + removed] }
        }
    }

    /** 全行をダーティに */
    fun markAllDirty() {
        for (row in screen) row.dirty = true
    }

    /** 全行のテキストを文字列で取得 (コピー用) */
    fun getAllText(includeScrollback: Boolean = true): String {
        val sb = StringBuilder()
        if (includeScrollback && primaryActive) {
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

    /** 指定範囲 (行・列、両端含む) のテキストを取得 */
    fun getRangeText(startRow: Int, startCol: Int, endRow: Int, endCol: Int): String {
        if (startRow > endRow || startRow !in 0 until totalRows) return ""
        val sb = StringBuilder()
        for (r in startRow..endRow.coerceAtMost(totalRows - 1)) {
            val row = getRow(r)
            val from = if (r == startRow) startCol else 0
            val to = if (r == endRow) endCol + 1 else row.columns
            for (c in from.coerceAtLeast(0) until to.coerceAtMost(row.columns)) {
                sb.append(row.getCell(c).char)
            }
            if (r < endRow) sb.append('\n')
        }
        return sb.toString()
    }

    /** スクロールバッククリア */
    fun clearScrollback() {
        scrollback.clear()
    }
}
