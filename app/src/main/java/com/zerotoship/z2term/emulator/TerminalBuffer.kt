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

    /**
     * リサイズ (両スクリーン + スクロールバックを揃える)。
     *
     * 縮小時はカーソル行 [cursorRow] を必ず保持するため、まず「カーソル行より下の
     * 空行」を捨て、足りなければ「カーソル行より上の行」を scrollback に押し出す。
     *
     * 拡大時は逆に、縮小で scrollback に押し出した行を可能な範囲で画面上部へ戻す
     * (ピンチで一度小さくして戻したとき、上へスクロールアウトした内容を復帰させる)。
     *
     * @param cursorRow 縮小時にこの行を残すヒント (≥0)。範囲外なら 0 扱い。
     * @return カーソル行の補正量。縮小で scrollback に push した行数は正、拡大で
     *         scrollback から pull した行数は負で返す。emulator はこれを引いて
     *         画面上のカーソル位置を保つ。変化なしは 0。
     */
    fun resize(newRows: Int, newColumns: Int, cursorRow: Int = 0): Int {
        if (newRows == rows && newColumns == columns) return 0

        if (newColumns != columns) {
            for (row in primary) row.resize(newColumns)
            for (row in alternate) row.resize(newColumns)
            for (row in scrollback) row.resize(newColumns)
        }

        val pushed = resizePrimaryWithCursor(newRows, newColumns, cursorRow)
        alternate = resizeScreenSimple(alternate, newRows, newColumns)
        screen = if (primaryActive) primary else alternate

        rows = newRows
        columns = newColumns
        markAllDirty()
        return pushed
    }

    /**
     * Primary スクリーンを縮小・拡大する。
     * 縮小時はカーソル行を残し、まず下方の空行を、足りなければ上方を scrollback に出す。
     * 拡大時はまず scrollback の末尾 (新しい履歴) を画面上部へ戻し、足りない分だけ
     * 下部に空行を足す。
     * 戻り値はカーソル補正量 (縮小 push は正、拡大 pull は負、unchanged は 0)。
     */
    private fun resizePrimaryWithCursor(
        newRows: Int,
        newColumns: Int,
        cursorRowHint: Int
    ): Int {
        val old = primary
        if (newRows == old.size) return 0
        if (newRows > old.size) {
            // 拡大: 縮小で scrollback へ押し出した行を、可能な範囲で上部へ戻す。
            val grow = newRows - old.size
            val pulled = if (primaryActive) minOf(grow, scrollback.size) else 0
            primary = Array(newRows) { i ->
                when {
                    i < pulled -> scrollback[scrollback.size - pulled + i]
                    i - pulled < old.size -> old[i - pulled]
                    else -> TerminalRow(newColumns)
                }
            }
            repeat(pulled) { scrollback.removeLast() }
            // 上に pulled 行戻したぶんカーソルは下へずれる → 負の補正量で表現。
            return -pulled
        }
        val removed = old.size - newRows
        val cursor = cursorRowHint.coerceIn(0, old.size - 1)
        // カーソル行より下に何行あるか
        val belowCursor = (old.size - 1 - cursor).coerceAtLeast(0)
        val bottomDrop = minOf(removed, belowCursor)
        val topDrop = removed - bottomDrop
        if (primaryActive && topDrop > 0) {
            for (i in 0 until topDrop) {
                if (scrollback.size >= scrollbackCapacity) scrollback.removeFirst()
                scrollback.addLast(old[i])
            }
        }
        primary = Array(newRows) { i -> old[i + topDrop] }
        return if (primaryActive) topDrop else 0
    }

    /**
     * Alternate スクリーン用 resize (scrollback には影響しない)。
     *
     * 拡大時は下に空行を追加。
     * 縮小時は **下行 (カーソル付近) を残して上行を捨てる**。vim/htop 等は
     * SIGWINCH 受信後すぐ全再描画するが、その redraw が到着するまでの数フレームで
     * カーソル付近が消えると「画面下半分が無くなる」崩れに見える。下を残すことで
     * 再描画到着までの見た目の崩れを最小限にする (旧実装は上を残して下を捨てていた)。
     */
    private fun resizeScreenSimple(
        old: Array<TerminalRow>,
        newRows: Int,
        newColumns: Int
    ): Array<TerminalRow> {
        if (newRows == old.size) return old
        return if (newRows > old.size) {
            Array(newRows) { i ->
                if (i < old.size) old[i] else TerminalRow(newColumns)
            }
        } else {
            val drop = old.size - newRows
            Array(newRows) { i -> old[i + drop] }
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

    /** 指定範囲 (行・列、両端含む) のテキストを取得 (wide-cont セルはスキップ) */
    fun getRangeText(startRow: Int, startCol: Int, endRow: Int, endCol: Int): String {
        if (startRow > endRow || startRow !in 0 until totalRows) return ""
        val sb = StringBuilder()
        val lastRow = endRow.coerceAtMost(totalRows - 1)
        for (r in startRow..lastRow) {
            val row = getRow(r)
            val from = if (r == startRow) startCol else 0
            val to = if (r == endRow) endCol + 1 else row.columns
            val lineStart = sb.length
            for (c in from.coerceAtLeast(0) until to.coerceAtMost(row.columns)) {
                val cell = row.getCell(c)
                // wide-cont セルは通常スキップするが、絵文字/CJK 拡張は右セルに低サロゲートを
                // 持つため、それは出力する (スキップすると孤立サロゲートで文字が壊れる)。
                if (!cell.wideCont || cell.char.isLowSurrogate()) sb.append(cell.char)
            }
            // ソフト折り返し行 (row.wrapped) は視覚的に次行へ続く 1 論理行なので、改行を入れず
            // そのまま連結する。ハード行は端末がセルを空白で右端まで埋めるため、行末スペースを
            // 削ってから改行を付ける (これをしないとコピー結果が空白だらけで改行が埋もれる)。
            if (!row.wrapped) {
                while (sb.length > lineStart && sb[sb.length - 1] == ' ') sb.deleteCharAt(sb.length - 1)
                if (r < lastRow) sb.append('\n')
            }
        }
        return sb.toString()
    }

    /** スクロールバッククリア */
    fun clearScrollback() {
        scrollback.clear()
    }

    /**
     * Primary / Alternate / scrollback すべての行から画像参照を外す。
     * Kitty graphics の `a=d` 系のうち、id 引数なしの「全削除」相当に使う。
     */
    fun clearAllImages() {
        for (row in primary) row.image = null
        for (row in alternate) row.image = null
        for (row in scrollback) row.image = null
    }
}
