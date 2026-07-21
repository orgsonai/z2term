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
    /**
     * SGR 58 で設定される underline color。
     * 本実装ではセル描画には使わず、 Kitty graphics の Unicode placeholder セルへの
     * image id 上位 8bit (32bit 拡張) のみで参照する。
     * 形式は [currentFg]/[currentBg] と同じ ([SgrAttribute.makeRgb] 等)。
     */
    private var currentUnderlineColor = SgrAttribute.DEFAULT

    // --- スクロール領域 (top, bottom) inclusive ---
    private var scrollTop = 0
    private var scrollBottom = initialRows - 1

    // --- モードフラグ ---
    private var autoWrap = true
    private var originMode = false  // DECOM
    private var insertMode = false  // IRM
    private var applicationCursorKeys = false  // DECCKM

    /**
     * Bracketed paste (DECSET 2004)。ON のときペーストは `ESC[200~ … ESC[201~` で囲んで
     * 送るべき (UI 側 [com.zerotoship.z2term.core.TerminalSession.pasteFromClipboard] が参照)。
     * これにより bash/zsh の readline や vim が「貼り付け」と認識し、各行の即時実行や
     * 自動インデントの連鎖を防ぐ。
     */
    var bracketedPasteMode: Boolean = false
        private set

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
        CHARSET,
        /**
         * DCS (`ESC P`) / APC (`ESC _`) / PM (`ESC ^`) / SOS (`ESC X`) の
         * 「文字列系」シーケンスを ST (`ESC \`) または BEL まで吸収する状態。
         *
         * 本実装はこれら 4 種をすべて **黙って破棄** する (描画しない / 応答しない)。
         * これだけで以下の症状を止められる:
         *  - APC 開始の `ESC _` を吸収できず Kitty graphics の `G;…\e\\` 本文や
         *    base64 payload が画面に文字として漏れる
         *  - DCS 内に含まれる `ESC [ … M` 等を CSI として誤解釈し、終端不一致で
         *    続く本文がそのまま画面へ流れる (= mouse SGR 漏れの正体の 1 つ)
         *  - 同じく DCS/APC 本文内の `\r` (0x0D) が GROUND 状態の CR として処理
         *    されて、TUI 描画中に cursor が突然行頭に飛ぶ
         */
        STRING
    }
    private var state = State.GROUND
    /** STRING 状態で直前に ESC を受けたか (次バイトが `\` なら ST=終端、それ以外なら継続 or 異常終了)。 */
    private var stringEscSeen = false
    /**
     * 進行中の文字列系シーケンスが Kitty graphics (APC `_`) かどうか。
     * 他 (DCS/PM/SOS) は本文を破棄するだけだが、APC はパーサに本文を流す。
     */
    private var stringIsKittyApc = false
    /** Kitty graphics protocol パーサ。 lazy 初期化 (使わない端末ではアロケート無し)。 */
    private val kittyParser: KittyGraphicsParser by lazy { KittyGraphicsParser() }

    /**
     * Renderer から渡される「現在の 1 セル幅 / 行高 (px)」のヒント。
     * Kitty graphics の `c=N`/`r=N` 指定が無いとき、画像ピクセル数からセル数を
     * 自動算出するのに使う。 未設定 (0) のときは `KittyGraphicsParser` 内で
     * 1 セルにフォールバックする。
     */
    private var cellWidthPxHint: Float = 0f
    private var lineHeightPxHint: Float = 0f

    /**
     * Renderer 側で 1 セルの幅/高を計算し終えたあと呼んで、画像の自動セル数算出に
     * 反映させる。 値が変わらないときは何もしない (再描画起動を抑える)。
     */
    fun setCellMetricsHint(cellWidthPx: Float, lineHeightPx: Float) {
        if (cellWidthPx > 0f) cellWidthPxHint = cellWidthPx
        if (lineHeightPx > 0f) lineHeightPxHint = lineHeightPx
    }

    /**
     * Kitty graphics `t=f`/`t=t`/`t=s` の payload 取得元 (ゲスト→ホスト変換 + 実 I/O) を
     * 設定する。 null (既定) のときは file/temp/shm をすべて破棄し、 `a=q` も
     * ENOTSUPPORTED を返す (opt-in)。 セッション側で AppSettings の opt-in が ON のとき
     * のみ実体を注入する。
     */
    fun setKittyExternalTransfer(source: KittyGraphicsParser.ExternalTransferSource?) {
        kittyParser.externalTransferSource = source
    }

    // --- UTF-8 デコーダ (Ground 状態の非 ASCII バイト用) ---
    private val utf8 = Utf8Decoder()

    // --- CSI パラメータバッファ ---
    private val csiParams = mutableListOf<Int>()
    // csiParamIsSub[k] = true のとき csiParams[k] は直前パラメータの `:` サブパラメータ
    // (例: SGR `4:3` の `3`)。`;` 区切りと区別して SGR の style 指定を正しく解釈するため。
    private val csiParamIsSub = mutableListOf<Boolean>()
    private var csiPendingSub = false  // 次に確定するパラメータが `:` サブパラメータか
    private var csiCurrentParam = -1   // -1 = まだ何も入力されていない
    private var csiPrefix: Char = 0.toChar()    // '?' や '>' などのプレフィックス
    private var csiIntermediate: Char = 0.toChar()

    // --- OSC バッファ ---
    // 生バイトで貯めて終端時に UTF-8 デコードする (b.toChar() だと日本語タイトル/パスが
    // Latin-1 扱いになり文字化けするため)。ASCII(OSC 4/7/8/52 等)はそのまま通る。
    private val oscBuffer = java.io.ByteArrayOutputStream()

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
            State.STRING -> processString(b)
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
        // Kitty Unicode placeholder: `U+10EEEE`。 1 セル幅の専用ルートで書く。
        if (cp == KittyPlaceholder.CODEPOINT) {
            putKittyPlaceholder()
            return
        }
        // Kitty placeholder の直後の最大 3 個の combining diacritic は row/col/placementIdLow
        // を表すメタ。 直前 placeholder セルの ref を更新するだけでカーソルは進めない。
        val diIdx = KittyPlaceholder.diacriticIndex(cp)
        if (diIdx >= 0 && placeholderStage in 0..2 && lastPlaceholderRow >= 0) {
            applyPlaceholderDiacritic(diIdx)
            return
        }
        // それ以外の通常文字書込みでは placeholder の diacritic 受付状態を必ず解除する
        // (直前 placeholder セルにこれ以上 diacritic が来ない確定)。
        placeholderStage = -1
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

    // --- Kitty Unicode placeholder の状態 ---
    /** 直前に書いた placeholder セルの screen row (なければ -1)。 */
    private var lastPlaceholderRow: Int = -1
    /** 直前に書いた placeholder セルの screen col (なければ -1)。 */
    private var lastPlaceholderCol: Int = -1
    /**
     * 直前 placeholder セルに次に来る diacritic が更新するフィールド:
     *  -1 = 受付終了、 0 = srcRow 待ち、 1 = srcCol 待ち、 2 = placementIdLow 待ち、
     *  3 = 全部埋まったので以降はもう受け付けない。
     */
    private var placeholderStage: Int = -1

    /**
     * `U+10EEEE` (Kitty Unicode placeholder) を現在カーソル位置に 1 セル幅で書く。
     * セルの fg truecolor (`\e[38:2:R:G:B`) から image id 下位 24bit を抽出し、
     * SGR 58 (underline color) の R 値を上位 8bit として OR して image id 32bit を組み立てる
     * (Kitty 仕様: id が 24bit を越える場合に上位 8bit を underline color に詰める)。
     * truecolor 未指定なら imageId=0 で「未割当 placeholder」。
     * 直後に来る diacritic で srcRow/srcCol/placementIdLow を順に上書きする。
     */
    private fun putKittyPlaceholder() {
        // 1 セルしか使わないが、autoWrap は通常文字と同じ流儀で行う。
        if (cursorCol >= buffer.columns) {
            if (autoWrap) {
                buffer.getScreenRow(cursorRow).wrapped = true
                cursorCol = 0
                lineFeed()
            } else {
                cursorCol = buffer.columns - 1
            }
        }
        val fg = currentFg or currentFlags
        val id24 = if (SgrAttribute.isRgb(currentFg)) {
            (SgrAttribute.getR(currentFg) shl 16) or
                (SgrAttribute.getG(currentFg) shl 8) or
                SgrAttribute.getB(currentFg)
        } else 0
        val id32high = if (SgrAttribute.isRgb(currentUnderlineColor)) {
            SgrAttribute.getR(currentUnderlineColor) and 0xFF
        } else 0
        val imageId = (id32high shl 24) or id24
        val row = buffer.getScreenRow(cursorRow)
        if (insertMode) row.insertChars(cursorCol, 1, fg, currentBg)
        row.setPlaceholder(
            col = cursorCol,
            ref = PlaceholderRef(imageId = imageId),
            fg = fg,
            bg = currentBg,
            link = currentLink
        )
        lastPlaceholderRow = cursorRow
        lastPlaceholderCol = cursorCol
        placeholderStage = 0
        cursorCol++
    }

    /** 直前 placeholder セルの [PlaceholderRef] の現在 stage に [idx] を入れて stage を進める。 */
    private fun applyPlaceholderDiacritic(idx: Int) {
        val r = lastPlaceholderRow
        val c = lastPlaceholderCol
        if (r !in 0 until buffer.rows || c !in 0 until buffer.columns) {
            placeholderStage = -1
            return
        }
        val cell = buffer.getScreenRow(r).getCell(c)
        val ref = cell.placeholder ?: run { placeholderStage = -1; return }
        cell.placeholder = when (placeholderStage) {
            0 -> ref.copy(srcRow = idx)
            1 -> ref.copy(srcCol = idx)
            2 -> ref.copy(placementIdLow = idx and 0xFF)
            else -> ref
        }
        placeholderStage++
        buffer.getScreenRow(r).dirty = true
    }

    private fun putWideChar(c: Char) {
        placeholderStage = -1
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
        placeholderStage = -1
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
                // 折り返し「元」の行に印を付ける (消費側 UrlFinder/getAllText の規約に合わせる)。
                val row = buffer.getScreenRow(cursorRow)
                row.wrapped = true
                // wide 文字 (全角/絵文字) が右端の残りセルに収まらず折り返す場合、その余りセルは
                // 表示上の埋め草でしかない。素の空白のままだと wrapped 行はコピー時にトリムされない
                // ため、折り返し境界に余白 1 文字が紛れ込み「2 行に渡るワンライン」が 1 行で
                // コピーできなくなる。コピー/描画で飛ばす wideCont 印を付けて埋め草を無害化する。
                for (c in cursorCol until buffer.columns) {
                    row.setChar(c, ' ', currentFg or currentFlags, currentBg, wideCont = true, link = currentLink)
                }
                cursorCol = 0
                lineFeed()
            }
        }
    }

    private fun processEscape(b: Int) {
        when (val c = b.toChar()) {
            '[' -> {
                state = State.CSI
                csiParams.clear()
                csiParamIsSub.clear()
                csiPendingSub = false
                csiCurrentParam = -1
                csiPrefix = 0.toChar()
                csiIntermediate = 0.toChar()
            }
            ']' -> {
                state = State.OSC
                oscBuffer.reset()
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
            'P', '_', 'X', '^' -> {
                // DCS / APC / SOS / PM。本文の取り扱いはシーケンス種で分岐:
                //  - `_` (APC) は Kitty graphics 用の本文をパーサへ流す
                //  - 他は本文を破棄
                // いずれも ST (ESC \) または BEL まで「文字列」として吸収する。
                state = State.STRING
                stringEscSeen = false
                stringIsKittyApc = (c == '_')
                if (stringIsKittyApc) kittyParser.reset()
            }
            '\\' -> {
                // 単独 ST (孤立した ESC \)。STRING 状態外なので無視。
                state = State.GROUND
            }
            else -> {
                Log.d(TAG, "Unhandled ESC $c (0x${b.toString(16)})")
                state = State.GROUND
            }
        }
    }

    /**
     * DCS / APC / PM / SOS の本文を「読み捨てる」。終端は BEL (0x07) または
     * ST (`ESC \` = 0x1B 0x5C) のいずれか。終端不正 (ESC + 非 `\`) は xterm 流儀で
     * その時点で打ち切り、続くバイトは ESCAPE 状態として再解釈する。
     */
    private fun processString(b: Int) {
        if (stringEscSeen) {
            stringEscSeen = false
            if (b == 0x5C) {
                // ST 完成: 正常終端
                finalizeStringSequence()
                state = State.GROUND
            } else {
                // ESC のあとに `\` 以外: 文字列は打ち切り、続くバイトを ESCAPE として処理
                finalizeStringSequence()
                state = State.ESCAPE
                processEscape(b)
            }
            return
        }
        when (b) {
            0x07 -> {
                finalizeStringSequence()
                state = State.GROUND
            }
            0x1B -> stringEscSeen = true   // 次が `\` なら ST
            else -> {
                // APC は Kitty graphics 用にパーサへ流す。 他は破棄。
                if (stringIsKittyApc) kittyParser.feedByte(b)
            }
        }
    }

    /**
     * STRING 状態を終端する際に呼ぶ。 Kitty graphics ならパーサに完成を要求し、
     * 画像が返ってきたら現在のカーソル行に commit する。
     */
    private fun finalizeStringSequence() {
        if (!stringIsKittyApc) return
        val result = kittyParser.finishSequence(cellWidthPxHint, lineHeightPxHint)
        stringIsKittyApc = false
        when (result) {
            is KittyGraphicsParser.Result.Transmit -> {
                // imageId != 0 ならキャッシュへ登録 (a=T も a=t も)。
                buffer.cacheImage(result.imageId, result.bitmap)
                if (result.unicodePlaceholder) {
                    // U=1: cursor 位置に描かず virtual placement だけ登録 (Unicode
                    // placeholder セルで後から位置決め)。 imageId=0 のときは登録しない
                    // (placeholder 側から逆引きできないため)。
                    if (result.imageId != 0) {
                        buffer.registerVirtualPlacement(
                            result.imageId,
                            VirtualPlacementSpec(
                                widthCells = result.widthCells,
                                heightCells = result.heightCells,
                                zIndex = result.zIndex,
                                placementId = result.placementId,
                                bitmap = result.bitmap
                            )
                        )
                    }
                } else if (result.display) {
                    commitKittyPlacement(
                        bitmap = result.bitmap,
                        widthCells = result.widthCells,
                        heightCells = result.heightCells,
                        imageId = result.imageId,
                        placementId = result.placementId,
                        zIndex = result.zIndex
                    )
                }
            }
            is KittyGraphicsParser.Result.Put -> {
                val cached = buffer.getCachedImage(result.imageId) ?: return
                val w = result.cellsWidth
                    ?: estimateCellsFromPixels(cached.width.toFloat(), cellWidthPxHint)
                val h = result.cellsHeight
                    ?: estimateCellsFromPixels(cached.height.toFloat(), lineHeightPxHint)
                commitKittyPlacement(
                    bitmap = cached,
                    widthCells = w,
                    heightCells = h,
                    imageId = result.imageId,
                    placementId = result.placementId,
                    zIndex = result.zIndex
                )
            }
            is KittyGraphicsParser.Result.VirtualPut -> {
                val cached = buffer.getCachedImage(result.imageId) ?: return
                val w = (result.cellsWidth
                    ?: estimateCellsFromPixels(cached.width.toFloat(), cellWidthPxHint))
                    .coerceAtLeast(1)
                val h = (result.cellsHeight
                    ?: estimateCellsFromPixels(cached.height.toFloat(), lineHeightPxHint))
                    .coerceAtLeast(1)
                buffer.registerVirtualPlacement(
                    result.imageId,
                    VirtualPlacementSpec(
                        widthCells = w,
                        heightCells = h,
                        zIndex = result.zIndex,
                        placementId = result.placementId,
                        bitmap = cached
                    )
                )
            }
            is KittyGraphicsParser.Result.DeleteAll -> buffer.clearAllImages()
            is KittyGraphicsParser.Result.DeleteImage -> buffer.deleteImageById(result.imageId)
            is KittyGraphicsParser.Result.DeletePlacement ->
                buffer.deletePlacement(result.imageId, result.placementId)
            is KittyGraphicsParser.Result.Query -> sendKittyQueryResponse(result)
            is KittyGraphicsParser.Result.Frame -> {
                // 段階 7: animation frame は蓄積するだけ (描画は frame 0 を継続表示)。
                // 再生は段階 8 で対応する。
                buffer.addAnimationFrame(
                    imageId = result.imageId,
                    frame = AnimationFrame(
                        bitmap = result.bitmap,
                        delayMs = result.delayMs,
                        composeMode = result.composeMode,
                        xOffset = result.xOffset,
                        yOffset = result.yOffset
                    )
                )
            }
            is KittyGraphicsParser.Result.Continue -> {
                /* 次のチャンク APC を待つ。 stringIsKittyApc は次の `_` で再 ON */
            }
            is KittyGraphicsParser.Result.Discard -> { /* 無視 */ }
        }
    }

    /**
     * `a=q` の応答を `output` 経由で TUI に返す。
     *
     * Kitty の quiet level:
     *  - `q=0` (既定): 成功・エラー両方を返す
     *  - `q=1`: エラーのみ
     *  - `q=2`: 一切返さない
     *
     * 応答形式: `ESC _ G i=<imageId> ; <message> ESC \`
     * (image id 0 のときは `i=` 部分を省略するのが Kitty 仕様)。
     */
    private fun sendKittyQueryResponse(q: KittyGraphicsParser.Result.Query) {
        val shouldEmit = when {
            q.quietLevel >= 2 -> false
            q.quietLevel == 1 -> !q.ok  // エラーのみ
            else -> true                // q=0: 全部
        }
        if (!shouldEmit) return
        val header = if (q.imageId != 0) "i=${q.imageId}" else ""
        val body = "${q.message}"
        // ESC _ G <header> ; <body> ESC \
        val payload = buildString {
            append("_G")
            append(header)
            append(';')
            append(body)
            append("\\")
        }
        output(payload.toByteArray(Charsets.US_ASCII))
    }

    /** ピクセル数 / 1 セル数 を整数セルへ。 セル数ヒントが未設定なら 1 セル扱い。 */
    private fun estimateCellsFromPixels(pixels: Float, perCell: Float): Int {
        if (perCell <= 0f) return 1
        return (pixels / perCell + 0.5f).toInt().coerceAtLeast(1)
    }

    /**
     * Kitty graphics の画像を現在のカーソル位置に配置する。
     *
     * 設計上の合意点:
     *  - **anchor 行 = 現在のカーソル行** に `image` を持たせる (top-left)。
     *    複数行にまたがっても anchor 行 1 つにだけ image を持たせ、Renderer は
     *    anchor 行を描く回で `widthCells × heightCells` の矩形を一括描画する。
     *  - **カーソルは画像の幅 (cells) ぶん右へ進める**。 高さ方向は xterm/kitty 流儀の
     *    "C=0" 同等 (= カーソル行を動かさない)。 改行が必要なら後段の TUI が `\n` を
     *    送る。 これで「画像の右に文字列を続ける」「`\n` を挟んで次の画像を下に置く」が
     *    自然に書ける。
     *  - 画像領域が右端を超える場合、最初の anchor は **そのまま** とし、Renderer 側で
     *    描画矩形を画面右端でクリップする (cell 占有は cols 余ぶん減らす)。
     *  - 画像内のセルは空白 (`' '`) で埋めて文字描画と被らないようにする。
     */
    /**
     * Kitty graphics の placement (`a=T` の display 部 / `a=p`) を現在のカーソル位置に
     * 1 つ追加する。
     *
     * 設計上の合意点:
     *  - **anchor 行 = 現在のカーソル行**。 anchor 行内の [`TerminalRow.images`] に
     *    [TerminalImage] を 1 つ追加する。 同じ行にすでに別 placement があっても並列保持。
     *  - **カーソルは画像の幅セルぶん右へ進める** (改行は TUI が `\n` で送る前提)。
     *  - 画像領域は空白セルで先に潰してから images に追加する (`setChar` が image 領域への
     *    書込みで placement を消す副作用に commit と同タイミングで巻き込まれないようにする)。
     *  - 同 row 内に既に同じ `imageId`/`placementId` の placement があれば置換する
     *    (Kitty 仕様で同一 (image id, placement id) は同位置の上書きが期待される)。
     */
    private fun commitKittyPlacement(
        bitmap: android.graphics.Bitmap,
        widthCells: Int,
        heightCells: Int,
        imageId: Int,
        placementId: Int,
        zIndex: Int = 0
    ) {
        val row = buffer.getScreenRow(cursorRow)
        val anchorCol = cursorCol.coerceIn(0, buffer.columns - 1)
        val w = widthCells.coerceAtMost(buffer.columns - anchorCol).coerceAtLeast(1)
        val h = heightCells.coerceAtLeast(1)
        // 1) 占有セルを空白埋め (setChar は image 領域へのヒットで images から除外する)。
        for (c in anchorCol until (anchorCol + w).coerceAtMost(buffer.columns)) {
            row.setChar(c, ' ', currentFg, currentBg, wideCont = false, link = currentLink)
        }
        // 2) 同 row に同じ (imageId, placementId) があれば置換 (両方 0 のときも 1 つだけ保つ)。
        row.images.removeAll { it.imageId == imageId && it.placementId == placementId }
        row.images.add(
            TerminalImage(
                col = anchorCol,
                widthCells = w,
                heightCells = h,
                bitmap = bitmap,
                imageId = imageId,
                placementId = placementId,
                zIndex = zIndex
            )
        )
        row.dirty = true
        cursorCol = (anchorCol + w).coerceAtMost(buffer.columns - 1)
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
                csiParamIsSub.add(csiPendingSub)
                csiCurrentParam = -1
                csiPendingSub = false
            }
            c == ':' -> {
                // SGR の : サブパラメータ区切り (underline style `4:3` / RGB color など)。
                // 次に確定するパラメータをサブパラメータとして印付ける。
                csiParams.add(if (csiCurrentParam < 0) 0 else csiCurrentParam)
                csiParamIsSub.add(csiPendingSub)
                csiCurrentParam = -1
                csiPendingSub = true
            }
            c == '?' || c == '>' || c == '!' || c == '<' -> {
                csiPrefix = c
            }
            c in '\u0020'..'\u002F' -> {
                csiIntermediate = c
            }
            c in '\u0040'..'\u007E' -> {
                // 終端文字
                if (csiCurrentParam >= 0) {
                    csiParams.add(csiCurrentParam)
                    csiParamIsSub.add(csiPendingSub)
                }
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
                if (oscBuffer.size() < 1024) {
                    oscBuffer.write(b)
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
                            // Alt → Primary に戻るタイミングでマウスレポートを OFF に強制。
                            // 一部 TUI が DECRST 1049 だけ送って DECRST 1000/1006 を送り忘れる
                            // ため、primary シェルでスワイプすると stale な mouseEnabled で
                            // `\e[<...M` が PTY に流れ readline が壊れる症状を防ぐ。
                            mouseProtocol = MouseProtocol.OFF
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
                            mouseProtocol = MouseProtocol.OFF
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
                            mouseProtocol = MouseProtocol.OFF
                        }
                    }
                }
                2004 -> {
                    // bracketed paste mode。ペースト送出時に 200~/201~ で囲むか決める。
                    bracketedPasteMode = set
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
                    currentUnderlineColor = SgrAttribute.DEFAULT
                }
                1 -> currentFlags = currentFlags or SgrAttribute.FLAG_BOLD
                3 -> currentFlags = currentFlags or SgrAttribute.FLAG_ITALIC
                4 -> {
                    // `4` 単体は単線下線。`4:n` (style 付き) は n==0 で下線オフ、
                    // それ以外 (1=単線/2=二重/3=波線/4=点線/5=破線) は下線オン。
                    // サブパラメータを別 SGR として誤解釈しないよう必ず読み飛ばす。
                    if (i + 1 < csiParams.size && csiParamIsSub[i + 1]) {
                        val style = csiParams[i + 1]
                        currentFlags = if (style == 0) {
                            currentFlags and SgrAttribute.FLAG_UNDERLINE.inv()
                        } else {
                            currentFlags or SgrAttribute.FLAG_UNDERLINE
                        }
                        while (i + 1 < csiParams.size && csiParamIsSub[i + 1]) i++
                    } else {
                        currentFlags = currentFlags or SgrAttribute.FLAG_UNDERLINE
                    }
                }
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
                58 -> {
                    // Underline color (SGR 58:2:R:G:B / 58:5:idx)。 本実装は描画はせず、
                    // Kitty graphics の Unicode placeholder セルの image id 上位 8bit 抽出にのみ使う。
                    val mode = csiParams.getOrNull(i + 1) ?: 0
                    if (mode == 5) {
                        val idx = csiParams.getOrNull(i + 2) ?: 0
                        currentUnderlineColor = SgrAttribute.makeIndexed(idx.coerceIn(0, 255))
                        i += 2
                    } else if (mode == 2) {
                        val r = csiParams.getOrNull(i + 2) ?: 0
                        val g = csiParams.getOrNull(i + 3) ?: 0
                        val b = csiParams.getOrNull(i + 4) ?: 0
                        currentUnderlineColor = SgrAttribute.makeRgb(r, g, b)
                        i += 4
                    }
                }
                59 -> currentUnderlineColor = SgrAttribute.DEFAULT
                in 90..97 -> currentFg = SgrAttribute.makeIndexed(p - 90 + 8)  // bright fg
                in 100..107 -> currentBg = SgrAttribute.makeIndexed(p - 100 + 8) // bright bg
                else -> {}
            }
            i++
        }
    }

    private fun dispatchOsc() {
        val s = String(oscBuffer.toByteArray(), Charsets.UTF_8)
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
            // ロケールは ROOT 固定。既定ロケール任せだと数字が非 ASCII 字形になる環境があり、
            // これは端末へ書き出す制御応答なので壊れる (US_ASCII で送る前提)。
            val reply = String.format(
                java.util.Locale.ROOT,
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
        // 通常文字が書かれた時点で直前 placeholder セルの diacritic 受付は終了する
        // (ASCII は putCodepoint を経由しないので、 個別にリセット)。
        placeholderStage = -1
        if (cursorCol >= buffer.columns) {
            if (autoWrap) {
                // 折り返し「元」の行に印を付ける (消費側 UrlFinder/getAllText の規約に合わせる)。
                buffer.getScreenRow(cursorRow).wrapped = true
                cursorCol = 0
                lineFeed()
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
            // スクロール領域が画面全体 (DECSTBM 未設定 = scrollTop 0 かつ scrollBottom 最下行)
            // のときだけ、最上行を scrollback に送る通常スクロール。
            // vim 等は下のステータス/コマンド行 (行番号・ルーラ表示) を固定するため DECSTBM で
            // 領域を画面途中までに狭める。その場合は領域内だけをスクロールし、領域外の固定行は
            // 動かさず scrollback にも送らない。これを怠ると固定行が一緒に押し上げられ、毎行に
            // 行番号が焼き付いて見える (報告された不具合)。
            if (scrollTop == 0 && scrollBottom == buffer.rows - 1) {
                buffer.scrollUp(currentFg, currentBg)
            } else {
                buffer.scrollUpRegion(scrollTop, scrollBottom, currentFg, currentBg)
            }
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
        bracketedPasteMode = false
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
