package com.zerotoship.z2term.emulator

import android.util.Log

/**
 * VT100 / xterm 互換のターミナルエミュレータ。
 *
 * バイトストリームを処理し、TerminalBuffer に反映する。
 *
 * 対応シーケンス:
 *   - 制御文字 (CR, LF, BS, BEL, HT, etc.)
 *   - CSI シーケンス (ESC [ ...)
 *   - OSC シーケンス (ESC ] ... BEL/ST)
 *   - SGR (色・装飾)
 *   - DEC モード (DECSET/DECRST)
 *   - キャラクタセット指定 (ESC ( )
 *
 * 状態機械はシンプルな switch で実装。
 * 完全な VT510 互換ではないが、bash/zsh/vim/htop/tmux/ncurses の実用範囲をカバーする。
 *
 * 参考: termux/terminal-emulator (Apache 2.0), DEC VT220 manual, xterm ctlseqs
 */
class TerminalEmulator(
    private val output: (ByteArray) -> Unit,
    initialRows: Int = 24,
    initialColumns: Int = 80
) {
    val buffer = TerminalBuffer(initialRows, initialColumns)
    val colors = TerminalColors()

    // --- カーソル状態 ---
    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    var cursorVisible = true
        private set

    // --- 現在の SGR 属性 ---
    private var currentFg = SgrAttribute.DEFAULT
    private var currentBg = SgrAttribute.DEFAULT
    private var currentFlags = 0

    // --- スクロール領域 (top, bottom) inclusive ---
    private var scrollTop = 0
    private var scrollBottom = initialRows - 1

    // --- モードフラグ ---
    private var autoWrap = true
    private var originMode = false  // DECOM
    private var insertMode = false  // IRM
    private var applicationCursorKeys = false  // DECCKM

    // --- 状態機械 ---
    private enum class State {
        GROUND,
        ESCAPE,
        CSI,
        OSC,
        CHARSET
    }
    private var state = State.GROUND

    // --- UTF-8 デコーダ (Ground 状態の非 ASCII バイト用) ---
    private val utf8 = Utf8Decoder()

    // --- CSI パラメータバッファ ---
    private val csiParams = mutableListOf<Int>()
    private var csiCurrentParam = -1   // -1 = まだ何も入力されていない
    private var csiPrefix: Char = 0.toChar()    // '?' や '>' などのプレフィックス
    private var csiIntermediate: Char = 0.toChar()

    // --- OSC バッファ ---
    private val oscBuffer = StringBuilder()

    // --- カーソル保存 ---
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var savedFg = SgrAttribute.DEFAULT
    private var savedBg = SgrAttribute.DEFAULT
    private var savedFlags = 0

    /**
     * 入力バイト列を処理する。
     */
    fun processBytes(bytes: ByteArray, length: Int = bytes.size) {
        var i = 0
        while (i < length) {
            val b = bytes[i].toInt() and 0xFF
            processByte(b)
            i++
        }
    }

    private fun processByte(b: Int) {
        when (state) {
            State.GROUND -> processGround(b)
            State.ESCAPE -> processEscape(b)
            State.CSI -> processCsi(b)
            State.OSC -> processOsc(b)
            State.CHARSET -> {
                // ESC ( B など、キャラクタセット指定 (無視)
                state = State.GROUND
            }
        }
    }

    private fun processGround(b: Int) {
        // ASCII 範囲: デコーダ状態を破棄して既存処理 (制御コードや ESC を優先)
        if (b < 0x80) {
            utf8.reset()
            when (b) {
                0x07 -> { /* BEL */ }
                0x08 -> { if (cursorCol > 0) cursorCol-- }
                0x09 -> {
                    cursorCol = ((cursorCol / 8) + 1) * 8
                    if (cursorCol >= buffer.columns) cursorCol = buffer.columns - 1
                }
                0x0A, 0x0B, 0x0C -> lineFeed()
                0x0D -> cursorCol = 0
                0x0E, 0x0F -> { /* SO/SI: 無視 */ }
                0x1B -> state = State.ESCAPE
                in 0x20..0x7E -> putChar(b.toChar())
                else -> { /* その他制御文字は無視 */ }
            }
        } else {
            // 0x80-0xFF: UTF-8 デコーダに供給。完成したらコードポイントを put
            val cp = utf8.feed(b) ?: return
            putCodepoint(cp)
        }
    }

    private fun putCodepoint(cp: Int) {
        if (cp <= 0xFFFF) {
            putChar(cp.toChar())
        } else {
            // BMP 範囲外 → サロゲートペアで 2 セル消費
            val highSurrogate = ((cp - 0x10000) shr 10) + 0xD800
            val lowSurrogate = ((cp - 0x10000) and 0x3FF) + 0xDC00
            putChar(highSurrogate.toChar())
            putChar(lowSurrogate.toChar())
        }
    }

    private fun processEscape(b: Int) {
        when (val c = b.toChar()) {
            '[' -> {
                state = State.CSI
                csiParams.clear()
                csiCurrentParam = -1
                csiPrefix = 0.toChar()
                csiIntermediate = 0.toChar()
            }
            ']' -> {
                state = State.OSC
                oscBuffer.clear()
            }
            '(', ')', '*', '+' -> {
                state = State.CHARSET
            }
            '7' -> {
                // DECSC: カーソル保存
                saveCursor()
                state = State.GROUND
            }
            '8' -> {
                // DECRC: カーソル復元
                restoreCursor()
                state = State.GROUND
            }
            'D' -> {
                // IND: index (下方向、必要ならスクロール)
                lineFeed()
                state = State.GROUND
            }
            'E' -> {
                // NEL: next line
                cursorCol = 0
                lineFeed()
                state = State.GROUND
            }
            'M' -> {
                // RI: reverse index (上方向、必要ならスクロール)
                if (cursorRow > scrollTop) {
                    cursorRow--
                } else {
                    buffer.scrollDownRegion(scrollTop, scrollBottom, currentFg, currentBg)
                }
                state = State.GROUND
            }
            'c' -> {
                // RIS: full reset
                fullReset()
                state = State.GROUND
            }
            '=' -> { /* DECKPAM: keypad app mode (無視) */ state = State.GROUND }
            '>' -> { /* DECKPNM: keypad numeric mode (無視) */ state = State.GROUND }
            else -> {
                Log.d(TAG, "Unhandled ESC $c (0x${b.toString(16)})")
                state = State.GROUND
            }
        }
    }

    private fun processCsi(b: Int) {
        val c = b.toChar()
        when {
            c in '0'..'9' -> {
                if (csiCurrentParam < 0) csiCurrentParam = 0
                csiCurrentParam = csiCurrentParam * 10 + (b - 0x30)
                if (csiCurrentParam > 9999) csiCurrentParam = 9999
            }
            c == ';' -> {
                csiParams.add(if (csiCurrentParam < 0) 0 else csiCurrentParam)
                csiCurrentParam = -1
            }
            c == ':' -> {
                // SGR の : 区切り (RGB color 用)
                csiParams.add(if (csiCurrentParam < 0) 0 else csiCurrentParam)
                csiCurrentParam = -1
            }
            c == '?' || c == '>' || c == '!' || c == '<' -> {
                csiPrefix = c
            }
            c in '\u0020'..'\u002F' -> {
                csiIntermediate = c
            }
            c in '\u0040'..'\u007E' -> {
                // 終端文字
                if (csiCurrentParam >= 0) csiParams.add(csiCurrentParam)
                dispatchCsi(c)
                state = State.GROUND
            }
            else -> {
                // 想定外、リセット
                state = State.GROUND
            }
        }
    }

    private fun processOsc(b: Int) {
        when (b) {
            0x07 -> {
                // BEL: OSC 終端
                dispatchOsc()
                state = State.GROUND
            }
            0x1B -> {
                // ESC: 次が \ なら ST (string terminator)
                // 簡易実装: ESC で常に終了とみなす
                dispatchOsc()
                state = State.GROUND
            }
            else -> {
                if (oscBuffer.length < 1024) {
                    oscBuffer.append(b.toChar())
                }
            }
        }
    }

    private fun getCsiParam(index: Int, default: Int): Int {
        if (index >= csiParams.size) return default
        val v = csiParams[index]
        return if (v == 0) default else v
    }

    private fun getCsiParamRaw(index: Int): Int {
        if (index >= csiParams.size) return 0
        return csiParams[index]
    }

    private fun dispatchCsi(finalChar: Char) {
        when (csiPrefix) {
            '?' -> dispatchCsiQuestion(finalChar)
            else -> dispatchCsiStandard(finalChar)
        }
    }

    private fun dispatchCsiStandard(finalChar: Char) {
        when (finalChar) {
            'A' -> {
                // CUU: cursor up
                val n = getCsiParam(0, 1)
                cursorRow = (cursorRow - n).coerceAtLeast(scrollTop)
            }
            'B' -> {
                // CUD: cursor down
                val n = getCsiParam(0, 1)
                cursorRow = (cursorRow + n).coerceAtMost(scrollBottom)
            }
            'C' -> {
                // CUF: cursor forward
                val n = getCsiParam(0, 1)
                cursorCol = (cursorCol + n).coerceAtMost(buffer.columns - 1)
            }
            'D' -> {
                // CUB: cursor back
                val n = getCsiParam(0, 1)
                cursorCol = (cursorCol - n).coerceAtLeast(0)
            }
            'E' -> {
                // CNL: cursor next line
                val n = getCsiParam(0, 1)
                cursorRow = (cursorRow + n).coerceAtMost(scrollBottom)
                cursorCol = 0
            }
            'F' -> {
                // CPL: cursor previous line
                val n = getCsiParam(0, 1)
                cursorRow = (cursorRow - n).coerceAtLeast(scrollTop)
                cursorCol = 0
            }
            'G' -> {
                // CHA: cursor horizontal absolute
                val col = getCsiParam(0, 1) - 1
                cursorCol = col.coerceIn(0, buffer.columns - 1)
            }
            'H', 'f' -> {
                // CUP / HVP: cursor position
                val row = getCsiParam(0, 1) - 1
                val col = getCsiParam(1, 1) - 1
                cursorRow = if (originMode) {
                    (row + scrollTop).coerceIn(scrollTop, scrollBottom)
                } else {
                    row.coerceIn(0, buffer.rows - 1)
                }
                cursorCol = col.coerceIn(0, buffer.columns - 1)
            }
            'J' -> {
                // ED: erase in display
                when (getCsiParamRaw(0)) {
                    0 -> {
                        // カーソルから画面末尾まで
                        eraseInLine(0)
                        for (r in cursorRow + 1 until buffer.rows) {
                            buffer.getScreenRow(r).clear(fg = currentFg, bg = currentBg)
                        }
                    }
                    1 -> {
                        // 画面頭からカーソルまで
                        for (r in 0 until cursorRow) {
                            buffer.getScreenRow(r).clear(fg = currentFg, bg = currentBg)
                        }
                        eraseInLine(1)
                    }
                    2, 3 -> {
                        // 全画面 (3 はスクロールバックも)
                        buffer.clearScreen(currentFg, currentBg)
                        if (getCsiParamRaw(0) == 3) {
                            buffer.clearScrollback()
                        }
                    }
                }
            }
            'K' -> {
                // EL: erase in line
                eraseInLine(getCsiParamRaw(0))
            }
            'L' -> {
                // IL: insert line
                val n = getCsiParam(0, 1)
                if (cursorRow in scrollTop..scrollBottom) {
                    repeat(n.coerceAtMost(scrollBottom - cursorRow + 1)) {
                        buffer.scrollDownRegion(cursorRow, scrollBottom, currentFg, currentBg)
                    }
                }
            }
            'M' -> {
                // DL: delete line
                val n = getCsiParam(0, 1)
                if (cursorRow in scrollTop..scrollBottom) {
                    repeat(n.coerceAtMost(scrollBottom - cursorRow + 1)) {
                        buffer.scrollUpRegion(cursorRow, scrollBottom, currentFg, currentBg)
                    }
                }
            }
            'P' -> {
                // DCH: delete characters
                val n = getCsiParam(0, 1)
                buffer.getScreenRow(cursorRow).deleteChars(cursorCol, n, currentFg, currentBg)
            }
            '@' -> {
                // ICH: insert characters
                val n = getCsiParam(0, 1)
                buffer.getScreenRow(cursorRow).insertChars(cursorCol, n, currentFg, currentBg)
            }
            'S' -> {
                // SU: scroll up
                val n = getCsiParam(0, 1)
                repeat(n) { buffer.scrollUpRegion(scrollTop, scrollBottom, currentFg, currentBg) }
            }
            'T' -> {
                // SD: scroll down
                val n = getCsiParam(0, 1)
                repeat(n) { buffer.scrollDownRegion(scrollTop, scrollBottom, currentFg, currentBg) }
            }
            'X' -> {
                // ECH: erase characters
                val n = getCsiParam(0, 1)
                val row = buffer.getScreenRow(cursorRow)
                for (i in cursorCol until (cursorCol + n).coerceAtMost(buffer.columns)) {
                    row.setChar(i, ' ', currentFg, currentBg)
                }
            }
            'd' -> {
                // VPA: vertical position absolute
                val row = getCsiParam(0, 1) - 1
                cursorRow = row.coerceIn(0, buffer.rows - 1)
            }
            'h' -> {
                // SM: set mode
                applyModes(csiParams, set = true)
            }
            'l' -> {
                // RM: reset mode
                applyModes(csiParams, set = false)
            }
            'm' -> {
                // SGR
                applySgr()
            }
            'n' -> {
                // DSR: device status report
                if (getCsiParamRaw(0) == 6) {
                    // CPR: cursor position report
                    val report = "\u001b[${cursorRow + 1};${cursorCol + 1}R"
                    output(report.toByteArray())
                }
            }
            'r' -> {
                // DECSTBM: set top/bottom margins (scroll region)
                val top = (getCsiParam(0, 1) - 1).coerceIn(0, buffer.rows - 1)
                val bottom = (getCsiParam(1, buffer.rows) - 1).coerceIn(0, buffer.rows - 1)
                if (top < bottom) {
                    scrollTop = top
                    scrollBottom = bottom
                    cursorRow = if (originMode) scrollTop else 0
                    cursorCol = 0
                }
            }
            's' -> {
                // SCOSC: save cursor
                saveCursor()
            }
            'u' -> {
                // SCORC: restore cursor
                restoreCursor()
            }
            't' -> {
                // window manipulation (主に無視)
            }
            else -> {
                Log.d(TAG, "Unhandled CSI ${csiParams} $finalChar")
            }
        }
    }

    private fun dispatchCsiQuestion(finalChar: Char) {
        // DEC private modes
        when (finalChar) {
            'h' -> applyDecModes(csiParams, set = true)
            'l' -> applyDecModes(csiParams, set = false)
            else -> Log.d(TAG, "Unhandled CSI ? ${csiParams} $finalChar")
        }
    }

    private fun applyModes(params: List<Int>, set: Boolean) {
        for (p in params) {
            when (p) {
                4 -> insertMode = set  // IRM
                20 -> { /* LNM: 改行モード (無視) */ }
                else -> {}
            }
        }
    }

    private fun applyDecModes(params: List<Int>, set: Boolean) {
        for (p in params) {
            when (p) {
                1 -> applicationCursorKeys = set  // DECCKM
                6 -> {
                    // DECOM: origin mode
                    originMode = set
                    cursorRow = if (originMode) scrollTop else 0
                    cursorCol = 0
                }
                7 -> autoWrap = set  // DECAWM
                25 -> cursorVisible = set  // DECTCEM
                1049, 47, 1047 -> {
                    // 代替スクリーン (簡易: 無視。完全実装は M3 以降)
                }
                2004 -> {
                    // bracketed paste (無視)
                }
                else -> {}
            }
        }
    }

    private fun applySgr() {
        if (csiParams.isEmpty()) {
            // CSI m == CSI 0 m: reset
            currentFg = SgrAttribute.DEFAULT
            currentBg = SgrAttribute.DEFAULT
            currentFlags = 0
            return
        }
        var i = 0
        while (i < csiParams.size) {
            val p = csiParams[i]
            when (p) {
                0 -> {
                    currentFg = SgrAttribute.DEFAULT
                    currentBg = SgrAttribute.DEFAULT
                    currentFlags = 0
                }
                1 -> currentFlags = currentFlags or SgrAttribute.FLAG_BOLD
                3 -> currentFlags = currentFlags or SgrAttribute.FLAG_ITALIC
                4 -> currentFlags = currentFlags or SgrAttribute.FLAG_UNDERLINE
                5 -> currentFlags = currentFlags or SgrAttribute.FLAG_BLINK
                7 -> currentFlags = currentFlags or SgrAttribute.FLAG_INVERSE
                9 -> currentFlags = currentFlags or SgrAttribute.FLAG_STRIKE
                22 -> currentFlags = currentFlags and SgrAttribute.FLAG_BOLD.inv()
                23 -> currentFlags = currentFlags and SgrAttribute.FLAG_ITALIC.inv()
                24 -> currentFlags = currentFlags and SgrAttribute.FLAG_UNDERLINE.inv()
                25 -> currentFlags = currentFlags and SgrAttribute.FLAG_BLINK.inv()
                27 -> currentFlags = currentFlags and SgrAttribute.FLAG_INVERSE.inv()
                29 -> currentFlags = currentFlags and SgrAttribute.FLAG_STRIKE.inv()
                in 30..37 -> currentFg = SgrAttribute.makeIndexed(p - 30)
                38 -> {
                    // 拡張色指定
                    val mode = csiParams.getOrNull(i + 1) ?: 0
                    if (mode == 5) {
                        // 256色
                        val idx = csiParams.getOrNull(i + 2) ?: 0
                        currentFg = SgrAttribute.makeIndexed(idx.coerceIn(0, 255))
                        i += 2
                    } else if (mode == 2) {
                        // RGB
                        val r = csiParams.getOrNull(i + 2) ?: 0
                        val g = csiParams.getOrNull(i + 3) ?: 0
                        val b = csiParams.getOrNull(i + 4) ?: 0
                        currentFg = SgrAttribute.makeRgb(r, g, b)
                        i += 4
                    }
                }
                39 -> currentFg = SgrAttribute.DEFAULT
                in 40..47 -> currentBg = SgrAttribute.makeIndexed(p - 40)
                48 -> {
                    val mode = csiParams.getOrNull(i + 1) ?: 0
                    if (mode == 5) {
                        val idx = csiParams.getOrNull(i + 2) ?: 0
                        currentBg = SgrAttribute.makeIndexed(idx.coerceIn(0, 255))
                        i += 2
                    } else if (mode == 2) {
                        val r = csiParams.getOrNull(i + 2) ?: 0
                        val g = csiParams.getOrNull(i + 3) ?: 0
                        val b = csiParams.getOrNull(i + 4) ?: 0
                        currentBg = SgrAttribute.makeRgb(r, g, b)
                        i += 4
                    }
                }
                49 -> currentBg = SgrAttribute.DEFAULT
                in 90..97 -> currentFg = SgrAttribute.makeIndexed(p - 90 + 8)  // bright fg
                in 100..107 -> currentBg = SgrAttribute.makeIndexed(p - 100 + 8) // bright bg
                else -> {}
            }
            i++
        }
    }

    private fun dispatchOsc() {
        // OSC コードを解釈 (タイトル設定など)
        val s = oscBuffer.toString()
        val sep = s.indexOf(';')
        if (sep < 0) return
        val code = s.substring(0, sep).toIntOrNull() ?: return
        val arg = s.substring(sep + 1)
        when (code) {
            0, 1, 2 -> {
                // ウィンドウタイトル設定 (UI 側に通知できるようにフックは作るが現状は無視)
                // titleListener?.invoke(arg)
            }
            else -> {}
        }
    }

    private fun eraseInLine(mode: Int) {
        val row = buffer.getScreenRow(cursorRow)
        when (mode) {
            0 -> row.clear(from = cursorCol, fg = currentFg, bg = currentBg)
            1 -> row.clear(to = cursorCol + 1, fg = currentFg, bg = currentBg)
            2 -> row.clear(fg = currentFg, bg = currentBg)
        }
    }

    /** 文字を現在位置に書き込み、カーソル前進 */
    private fun putChar(c: Char) {
        if (cursorCol >= buffer.columns) {
            if (autoWrap) {
                cursorCol = 0
                lineFeed()
                buffer.getScreenRow(cursorRow).wrapped = true
            } else {
                cursorCol = buffer.columns - 1
            }
        }

        // 合成フラグを fg にエンコード
        val fgWithFlags = currentFg or currentFlags
        val bgWithFlags = currentBg

        if (insertMode) {
            buffer.getScreenRow(cursorRow).insertChars(cursorCol, 1, fgWithFlags, bgWithFlags)
        }
        buffer.getScreenRow(cursorRow).setChar(cursorCol, c, fgWithFlags, bgWithFlags)
        cursorCol++
    }

    /** 改行 (必要ならスクロール) */
    private fun lineFeed() {
        if (cursorRow >= scrollBottom) {
            buffer.scrollUp(currentFg, currentBg)
            cursorRow = scrollBottom
        } else {
            cursorRow++
        }
    }

    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
        savedFg = currentFg
        savedBg = currentBg
        savedFlags = currentFlags
    }

    private fun restoreCursor() {
        cursorRow = savedCursorRow
        cursorCol = savedCursorCol
        currentFg = savedFg
        currentBg = savedBg
        currentFlags = savedFlags
    }

    private fun fullReset() {
        currentFg = SgrAttribute.DEFAULT
        currentBg = SgrAttribute.DEFAULT
        currentFlags = 0
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = buffer.rows - 1
        autoWrap = true
        originMode = false
        insertMode = false
        cursorVisible = true
        buffer.clearScreen()
        buffer.clearScrollback()
    }

    fun resize(rows: Int, columns: Int) {
        buffer.resize(rows, columns)
        scrollTop = scrollTop.coerceAtMost(rows - 1)
        scrollBottom = (if (scrollBottom == 0) rows - 1 else scrollBottom).coerceAtMost(rows - 1)
        if (scrollBottom <= scrollTop) {
            scrollTop = 0
            scrollBottom = rows - 1
        }
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, columns - 1)
    }

    /** カーソルキー押下時のバイト列を取得 */
    fun cursorKeyBytes(key: CursorKey): ByteArray {
        val esc = if (applicationCursorKeys) 'O' else '['
        return when (key) {
            CursorKey.UP -> byteArrayOf(0x1B, esc.code.toByte(), 'A'.code.toByte())
            CursorKey.DOWN -> byteArrayOf(0x1B, esc.code.toByte(), 'B'.code.toByte())
            CursorKey.RIGHT -> byteArrayOf(0x1B, esc.code.toByte(), 'C'.code.toByte())
            CursorKey.LEFT -> byteArrayOf(0x1B, esc.code.toByte(), 'D'.code.toByte())
        }
    }

    enum class CursorKey { UP, DOWN, LEFT, RIGHT }

    companion object {
        private const val TAG = "TerminalEmulator"
    }
}
