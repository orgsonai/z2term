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
    initialColumns: Int = 80,
    /** OSC 52 (clipboard write) のハンドラ。null なら無視。 */
    private val clipboardWriter: ((String) -> Unit)? = null,
    /** OSC 0/1/2 (window title) のハンドラ。null なら無視。 */
    private val titleSetter: ((String) -> Unit)? = null,
    /** OSC 7 (current working directory) のハンドラ。null なら無視。 */
    private val cwdSetter: ((String) -> Unit)? = null
) {

    /** OSC 8 で設定された現在のハイパーリンク URI (アクティブな間に書かれるセルに付与) */
    private var currentLink: String? = null
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

    /** EAW Ambiguous を wide 扱いするか (CJK ロケール向け) */
    var ambiguousAsWide: Boolean = false

    // --- マウスレポーティング (xterm-mouse) ---
    /** 現在のマウスプロトコル。ボタン/モーションの報告ルールを決める。 */
    var mouseProtocol: MouseProtocol = MouseProtocol.OFF
        private set

    /** マウスレポートのエンコーディング (LEGACY=X10 互換、SGR=1006、URXVT=1015) */
    var mouseEncoding: MouseEncoding = MouseEncoding.LEGACY
        private set

    /** マウスレポートが有効か (UI 側のジェスチャ振り分けに使う) */
    val mouseEnabled: Boolean get() = mouseProtocol != MouseProtocol.OFF

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

    // --- カーソル保存 (DECSC / DECRC、Primary/Alternate それぞれ用) ---
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var savedFg = SgrAttribute.DEFAULT
    private var savedBg = SgrAttribute.DEFAULT
    private var savedFlags = 0

    // --- ?1049 用: Primary 退避領域 (alt 切替前の状態を保存) ---
    private var altSavedCursorRow = 0
    private var altSavedCursorCol = 0
    private var altSavedFg = SgrAttribute.DEFAULT
    private var altSavedBg = SgrAttribute.DEFAULT
    private var altSavedFlags = 0
    private var altSavedScrollTop = 0
    private var altSavedScrollBottom = 0

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
        val wide = EastAsianWidth.isWide(cp, ambiguousAsWide)
        if (cp <= 0xFFFF) {
            if (wide) putWideChar(cp.toChar()) else putChar(cp.toChar())
        } else {
            // BMP 範囲外 (絵文字 / CJK Ext B-G 等) はサロゲートペア + 2 セル
            val high = (((cp - 0x10000) shr 10) + 0xD800).toChar()
            val low = (((cp - 0x10000) and 0x3FF) + 0xDC00).toChar()
            putSurrogatePair(high, low)
        }
    }

    private fun putWideChar(c: Char) {
        ensureRoomFor(width = 2)
        if (cursorCol >= buffer.columns - 1) {
            // 退避: 1 セルしか残っていない場合は narrow として置く
            putChar(c)
            return
        }
        val fg = currentFg or currentFlags
        val bg = currentBg
        val row = buffer.getScreenRow(cursorRow)
        if (insertMode) row.insertChars(cursorCol, 2, fg, bg)
        // 左セルに文字本体、右セルに wideCont マーカー (描画/コピー時に飛ばされる)
        row.setChar(cursorCol, c, fg, bg, wideCont = false, link = currentLink)
        row.setChar(cursorCol + 1, ' ', fg, bg, wideCont = true, link = currentLink)
        cursorCol += 2
    }

    private fun putSurrogatePair(high: Char, low: Char) {
        ensureRoomFor(width = 2)
        if (cursorCol >= buffer.columns - 1) {
            // 退避: narrow として高サロゲートだけ書く (実用上ほぼ来ない経路)
            putChar(high)
            return
        }
        val fg = currentFg or currentFlags
        val bg = currentBg
        val row = buffer.getScreenRow(cursorRow)
        if (insertMode) row.insertChars(cursorCol, 2, fg, bg)
        row.setChar(cursorCol, high, fg, bg, wideCont = false, link = currentLink)
        row.setChar(cursorCol + 1, low, fg, bg, wideCont = true, link = currentLink)
        cursorCol += 2
    }

    /** 幅 width セル分の領域を確保する (必要なら自動折り返し) */
    private fun ensureRoomFor(width: Int) {
        if (cursorCol + width > buffer.columns) {
            if (autoWrap) {
                cursorCol = 0
                lineFeed()
                buffer.getScreenRow(cursorRow).wrapped = true
            }
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
                9 -> {
                    // X10 mouse: ボタン押下のみ報告
                    mouseProtocol = if (set) MouseProtocol.X10 else MouseProtocol.OFF
                }
                25 -> cursorVisible = set  // DECTCEM
                1000 -> {
                    // Normal mouse: press + release
                    mouseProtocol = if (set) MouseProtocol.NORMAL else MouseProtocol.OFF
                }
                1002 -> {
                    // Button-event tracking: press/release + held-motion
                    mouseProtocol = if (set) MouseProtocol.BUTTON_EVENT else MouseProtocol.OFF
                }
                1003 -> {
                    // Any-event tracking: press/release + 常時 motion
                    mouseProtocol = if (set) MouseProtocol.ANY_EVENT else MouseProtocol.OFF
                }
                1005 -> {
                    // UTF-8 encoding (未対応)。SGR/URXVT に倒すアプリが多いので無視。
                }
                1006 -> {
                    // SGR encoding (xterm 1006)
                    mouseEncoding = if (set) MouseEncoding.SGR else MouseEncoding.LEGACY
                }
                1015 -> {
                    // URxvt encoding
                    mouseEncoding = if (set) MouseEncoding.URXVT else MouseEncoding.LEGACY
                }
                1049 -> {
                    // DECSET 1049: カーソル + 属性 + スクロール領域を退避 → Alt 切替 (クリア)
                    // DECRST 1049: Alt → Primary、退避していた状態を復元
                    if (set) {
                        if (buffer.primaryActive) {
                            altSavedCursorRow = cursorRow
                            altSavedCursorCol = cursorCol
                            altSavedFg = currentFg
                            altSavedBg = currentBg
                            altSavedFlags = currentFlags
                            altSavedScrollTop = scrollTop
                            altSavedScrollBottom = scrollBottom
                            buffer.switchToAlternate(clear = true, fg = currentFg, bg = currentBg)
                            cursorRow = 0
                            cursorCol = 0
                        }
                    } else {
                        if (!buffer.primaryActive) {
                            buffer.switchToPrimary()
                            cursorRow = altSavedCursorRow
                            cursorCol = altSavedCursorCol
                            currentFg = altSavedFg
                            currentBg = altSavedBg
                            currentFlags = altSavedFlags
                            scrollTop = altSavedScrollTop
                            scrollBottom = altSavedScrollBottom
                        }
                    }
                }
                1047 -> {
                    // DECSET 1047: Alt 切替 (クリア)、カーソル退避なし
                    // DECRST 1047: Primary 復帰 (Alt 内容はクリア)
                    if (set) {
                        if (buffer.primaryActive) {
                            buffer.switchToAlternate(clear = true, fg = currentFg, bg = currentBg)
                        }
                    } else {
                        if (!buffer.primaryActive) {
                            buffer.clearScreen(currentFg, currentBg)
                            buffer.switchToPrimary()
                        }
                    }
                }
                47 -> {
                    // DECSET 47: Alt 切替 (クリアなし、カーソル退避なし)
                    if (set) {
                        if (buffer.primaryActive) {
                            buffer.switchToAlternate(clear = false, fg = currentFg, bg = currentBg)
                        }
                    } else {
                        if (!buffer.primaryActive) {
                            buffer.switchToPrimary()
                        }
                    }
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
        val s = oscBuffer.toString()
        val sep = s.indexOf(';')
        if (sep < 0) return
        val code = s.substring(0, sep).toIntOrNull() ?: return
        val arg = s.substring(sep + 1)
        when (code) {
            0, 1, 2 -> titleSetter?.invoke(arg)
            4 -> handleOscPalette(arg)
            7 -> handleOscCwd(arg)
            8 -> handleOscHyperlink(arg)
            10 -> handleOscColor(code = 10, arg = arg, currentArgb = colors.defaultForeground) {
                colors.setDefaultForeground(it)
            }
            11 -> handleOscColor(code = 11, arg = arg, currentArgb = colors.defaultBackground) {
                colors.setDefaultBackground(it)
            }
            12 -> handleOscColor(code = 12, arg = arg, currentArgb = colors.cursorColor) {
                colors.setCursorColor(it)
            }
            52 -> handleOscClipboard(arg)
            else -> {}
        }
    }

    /**
     * OSC 7 ; file://host/path — current working directory 通知。
     * URI から path 部分を取り出して setter に渡す。
     */
    private fun handleOscCwd(arg: String) {
        val setter = cwdSetter ?: return
        val path = if (arg.startsWith("file://")) {
            // file://host/path から path 部分のみ
            val rest = arg.substring("file://".length)
            val slash = rest.indexOf('/')
            if (slash >= 0) {
                // URL デコード (簡易)
                try { java.net.URLDecoder.decode(rest.substring(slash), "UTF-8") }
                catch (e: Exception) { rest.substring(slash) }
            } else arg
        } else arg
        setter(path)
    }

    /**
     * OSC 8 ; params ; URI — ハイパーリンク開始 (URI 空ならリンク終了)。
     * params は id=xxx 等のセミコロン区切り。今回は URI 部分のみ使う。
     */
    private fun handleOscHyperlink(arg: String) {
        // arg = "params;URI"
        val sep = arg.indexOf(';')
        val uri = if (sep >= 0) arg.substring(sep + 1) else ""
        currentLink = uri.takeIf { it.isNotEmpty() }
    }

    /**
     * OSC 10/11/12 共通ハンドラ。
     *
     * `?` の場合は現在値を `rgb:RRRR/GGGG/BBBB` (16bit ヘックス × 3) で返信する。
     * 終端は BEL (0x07) を使う (xterm/iTerm/foot 等は ST/BEL どちらでも受ける)。
     * 通常の色指定 (rgb:.. / #RRGGBB) ならパースして setter を呼ぶ。
     */
    private fun handleOscColor(code: Int, arg: String, currentArgb: Int, setter: (Int) -> Unit) {
        val trimmed = arg.trim()
        if (trimmed == "?") {
            val r = (currentArgb shr 16) and 0xFF
            val g = (currentArgb shr 8) and 0xFF
            val b = currentArgb and 0xFF
            // 8bit → 16bit へ展開 (0xRR → 0xRRRR)。xterm 互換。
            val reply = String.format(
                "]%d;rgb:%02x%02x/%02x%02x/%02x%02x",
                code, r, r, g, g, b, b
            )
            output(reply.toByteArray(Charsets.US_ASCII))
            return
        }
        ColorSpec.parse(trimmed)?.let(setter)
    }

    /** OSC 4 ; idx ; spec[; idx; spec ...] — palette set */
    private fun handleOscPalette(arg: String) {
        val parts = arg.split(';')
        var i = 0
        while (i + 1 < parts.size) {
            val idx = parts[i].toIntOrNull()
            val color = ColorSpec.parse(parts[i + 1])
            if (idx != null && color != null) colors.setColor(idx, color)
            i += 2
        }
    }

    /**
     * OSC 52 ; selectorChars ; payload
     *   payload = "?" のとき: 問い合わせ (現状は無応答 = security デフォルト)
     *   payload = base64(text): クリップボードへ書き込み
     */
    private fun handleOscClipboard(arg: String) {
        val sep = arg.indexOf(';')
        if (sep < 0) return
        val payload = arg.substring(sep + 1)
        if (payload == "?" || payload.isEmpty()) return  // クエリは未対応
        val writer = clipboardWriter ?: return
        val decoded = try {
            android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            return
        }
        val text = String(decoded, Charsets.UTF_8)
        writer(text)
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
        buffer.getScreenRow(cursorRow).setChar(cursorCol, c, fgWithFlags, bgWithFlags, link = currentLink)
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
        utf8.reset()
        // Alt → Primary に戻して両方クリア
        if (!buffer.primaryActive) buffer.switchToPrimary()
        buffer.clearScreen()
        buffer.clearScrollback()
    }

    fun resize(rows: Int, columns: Int) {
        // buffer 側でカーソル行を残すよう下方の空行を先に捨て、足りなければ
        // 上方を scrollback に押し出す。拡大時は逆に scrollback から行を戻す。
        // その補正量 (push は正・pull は負) を cursorRow に反映し、画面上の
        // カーソル位置を保つ。
        val pushed = buffer.resize(rows, columns, cursorRow)
        // scroll region は無条件にフルリセット。TUI アプリ (vim/less/htop) は
        // SIGWINCH 受信後すぐ DECSTBM を再送するが、その隙間に届くバイトが
        // 旧 scrollBottom で流れると「同じ行に重ね書き → 文章が複数表示」
        // という崩れを起こす。再送までの不整合を消すためここで 0..rows-1 に戻す。
        scrollTop = 0
        scrollBottom = rows - 1
        cursorRow = (cursorRow - pushed).coerceIn(0, rows - 1)
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

    /**
     * xterm マウス報告プロトコル。
     *  - X10 (DECSET 9): ボタン押下のみ報告
     *  - NORMAL (1000): 押下 + 離した時の報告
     *  - BUTTON_EVENT (1002): NORMAL + 押下中の motion 報告
     *  - ANY_EVENT (1003): NORMAL + 押下していなくても常時 motion 報告
     */
    enum class MouseProtocol { OFF, X10, NORMAL, BUTTON_EVENT, ANY_EVENT }

    /**
     * マウスレポートのエンコーディング。
     *  - LEGACY: `ESC [ M Cb Cx Cy` (各 +32 オフセット、char 単位 = 7bit safe)
     *  - SGR (1006): `ESC [ < button ; col ; row M|m` (現代的、座標範囲制限なし)
     *  - URXVT (1015): `ESC [ Cb ; col ; row M` (Cb は +32)
     */
    enum class MouseEncoding { LEGACY, SGR, URXVT }

    /**
     * マウスイベントを現在の [mouseProtocol] / [mouseEncoding] でバイト列に変換して PTY に書き戻す。
     *
     * @param button 基底ボタンコード: 0=左 / 1=中 / 2=右 / 3=リリース(legacy) / 64=ホイール上 / 65=ホイール下
     * @param col0   0-based カラム
     * @param row0   0-based 行 (画面上の row、scrollback は無視)
     * @param press  true=押下イベント、false=リリースイベント (X10 では無視される)
     * @param motion true=motion イベント (BUTTON_EVENT/ANY_EVENT 以外では送られない)
     * @return       プロトコルでブロックされた場合 null。送るべきバイト列なら non-null。
     */
    fun encodeMouseEvent(
        button: Int,
        col0: Int,
        row0: Int,
        press: Boolean,
        motion: Boolean = false
    ): ByteArray? {
        val proto = mouseProtocol
        if (proto == MouseProtocol.OFF) return null
        // X10 はリリース/motion を送らない
        if (proto == MouseProtocol.X10 && (!press || motion)) return null
        // NORMAL は motion を送らない
        if (proto == MouseProtocol.NORMAL && motion) return null
        // BUTTON_EVENT は「ボタン押下中の motion」のみ。リリース後の motion は送らない (呼び出し側で判定)。
        val isWheel = button == 64 || button == 65
        val col = (col0 + 1).coerceAtLeast(1)
        val row = (row0 + 1).coerceAtLeast(1)

        return when (mouseEncoding) {
            MouseEncoding.SGR -> {
                val cb = button or (if (motion) 32 else 0)
                val terminator = if (press || isWheel) 'M' else 'm'
                "[<$cb;$col;$row$terminator".toByteArray(Charsets.US_ASCII)
            }
            MouseEncoding.URXVT -> {
                val baseCb = if (press || isWheel) button else 3
                val cb = (baseCb or (if (motion) 32 else 0)) + 32
                "[$cb;$col;${row}M".toByteArray(Charsets.US_ASCII)
            }
            MouseEncoding.LEGACY -> {
                val baseCb = if (press || isWheel) button else 3
                val cb = (baseCb or (if (motion) 32 else 0)) + 32
                // 各座標は +32 オフセット。範囲 (32..255) を超えると壊れるが xterm 仕様通り。
                val cxByte = (col + 32).coerceAtMost(255)
                val cyByte = (row + 32).coerceAtMost(255)
                byteArrayOf(
                    0x1B, '['.code.toByte(), 'M'.code.toByte(),
                    cb.coerceAtMost(255).toByte(), cxByte.toByte(), cyByte.toByte()
                )
            }
        }
    }

    companion object {
        private const val TAG = "TerminalEmulator"

        /** マウスボタンコード定数 (SGR/URxvt/Legacy 共通の基底値) */
        const val MOUSE_BTN_LEFT = 0
        const val MOUSE_BTN_MIDDLE = 1
        const val MOUSE_BTN_RIGHT = 2
        const val MOUSE_BTN_RELEASE_LEGACY = 3
        const val MOUSE_BTN_WHEEL_UP = 64
        const val MOUSE_BTN_WHEEL_DOWN = 65
    }
}
