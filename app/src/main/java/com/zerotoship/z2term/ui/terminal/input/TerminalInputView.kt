package com.zerotoship.z2term.ui.terminal.input

import android.content.Context
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.zerotoship.z2term.core.TerminalSelection
import com.zerotoship.z2term.core.TerminalSession

/**
 * ターミナル入力専用 View。
 *
 * 役割:
 *  - 物理キー / OS IME 経由の確定文字を PTY に流す
 *  - タップ / 長押し / ドラッグ / ピンチをジェスチャ検出して
 *    フォーカス・IME 表示・選択開始・スクロール・フォント拡縮に変換する
 *
 * 2 つのキーボードモード:
 *  - [imeEnabled] = false (既定): OS IME 非表示。物理キーは onKeyDown 経由。
 *  - [imeEnabled] = true: BaseInputConnection で IME の commitText / setComposingText を受ける。
 *
 * 受け取るジェスチャ:
 *  - 単タップ : フォーカス確保。IME モードなら IME 表示。選択中なら選択解除。
 *  - 長押し → ドラッグ : テキスト選択 (anchor を長押し点に固定し head を追随)
 *  - 1 本指スワイプ : スクロールバック / 最新へ移動 (lineHeight px ≒ 1 行)
 *  - ハンドル (選択端) ドラッグ : 選択範囲調整
 *  - 2 本指ピンチ : フォントサイズ変更 (8sp〜32sp)
 */
class TerminalInputView(context: Context) : View(context) {

    var session: TerminalSession? = null

    var ctrlSticky: Boolean = false

    var imeEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.restartInput(this)
            if (!value) {
                imm?.hideSoftInputFromWindow(windowToken, 0)
            }
        }

    private enum class TouchMode { NONE, SELECTING, ADJUSTING_START, ADJUSTING_END }
    private var touchMode = TouchMode.NONE
    private var selectionAnchorRow = 0
    private var selectionAnchorCol = 0
    private var initialFontSizeSp: Float = 13f
    private var initialSpan: Float = 0f
    private var lastAppliedFontSp: Float = 0f
    private var scrollAccumDy: Float = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                initialFontSizeSp = session?.settingsFlow?.value?.fontSizeSp ?: 13f
                // ピンチ開始時の指間距離を基準にする。以降は currentSpan/initialSpan の
                // 累積比でサイズを決める。旧実装は detector.scaleFactor (前イベント比) を
                // initial に掛けていたため、ほぼ 1.0 の値が揺れて「ウニョウニョ」していた。
                initialSpan = detector.currentSpan
                lastAppliedFontSp = initialFontSizeSp
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val sess = session ?: return false
                if (initialSpan <= 0f) return false
                val ratio = detector.currentSpan / initialSpan
                // 0.5sp 単位に量子化。微小変化での resize 連打 (重い) を防ぎ滑らかに。
                val raw = (initialFontSizeSp * ratio).coerceIn(8f, 32f)
                val quantized = Math.round(raw * 2f) / 2f
                if (quantized != lastAppliedFontSp) {
                    lastAppliedFontSp = quantized
                    sess.setFontSize(quantized)
                }
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                scrollAccumDy = 0f
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val sess = session ?: return
                val cell = pixelToAbsCell(e.x, e.y) ?: return
                selectionAnchorRow = cell.first
                selectionAnchorCol = cell.second
                touchMode = TouchMode.SELECTING
                sess.setSelection(
                    TerminalSelection.of(cell.first, cell.second, cell.first, cell.second)
                )
                // Haptic feedback (lightweight)
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val sess = session ?: return false
                if (scaleDetector.isInProgress) return false
                if (touchMode == TouchMode.SELECTING) {
                    val cell = pixelToAbsCell(e2.x, e2.y) ?: return false
                    sess.setSelection(
                        TerminalSelection.of(
                            selectionAnchorRow, selectionAnchorCol,
                            cell.first, cell.second
                        )
                    )
                    return true
                }
                // 通常のドラッグはターミナルをスクロール
                val m = sess.cellMetrics.value
                if (m.lineHeight <= 0f) return false
                scrollAccumDy += distanceY
                val rowDelta = (scrollAccumDy / m.lineHeight).toInt()
                if (rowDelta != 0) {
                    // distanceY > 0 (指が上に動いた = 最新へ) → scrollOffset 減少
                    // distanceY < 0 (指が下に動いた = 過去へ) → scrollOffset 増加
                    sess.scrollBy(-rowDelta)
                    scrollAccumDy -= rowDelta * m.lineHeight
                }
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val sess = session ?: return false
                // 選択中ならタップで解除 (マウスモードでも選択操作を優先)
                if (sess.selection.value != null) {
                    sess.clearSelection()
                    return true
                }
                // マウスモード有効ならボタン 1 の press+release を PTY に送る。
                // 失敗 (scrollback 表示中 / オフスクリーン) なら通常の focus/IME 経路にフォールバック。
                if (sess.emulator.mouseEnabled && sendMouseClick(e.x, e.y, sess)) {
                    return true
                }
                if (!isFocused) requestFocus()
                if (imeEnabled) requestKeyboard()
                performClick()
                return true
            }
        }
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = imeEnabled

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!imeEnabled) return null
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = (
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_FLAG_FORCE_ASCII or
                EditorInfo.IME_ACTION_NONE
            )
        return TerminalInputConnection(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val sess = session ?: return super.onKeyDown(keyCode, event)
        val bytes = AndroidKeyMapper.mapKeyEvent(event, ctrlSticky) { key ->
            sess.emulator.cursorKeyBytes(key)
        } ?: return super.onKeyDown(keyCode, event)
        sess.writeBytes(bytes)
        return true
    }

    fun requestKeyboard() {
        if (!imeEnabled) return
        if (!isFocused) requestFocus()
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        // ハンドル当たり判定は DOWN のみ
        if (action == MotionEvent.ACTION_DOWN) {
            val handle = hitTestHandle(event.x, event.y)
            if (handle != null) {
                touchMode = when (handle) {
                    Handle.START -> TouchMode.ADJUSTING_START
                    Handle.END -> TouchMode.ADJUSTING_END
                }
            }
        }

        // ADJUSTING 中は detectors を通さず直接処理
        if (touchMode == TouchMode.ADJUSTING_START || touchMode == TouchMode.ADJUSTING_END) {
            when (action) {
                MotionEvent.ACTION_MOVE -> {
                    val sess = session
                    val sel = sess?.selection?.value
                    if (sess != null && sel != null) {
                        val cell = pixelToAbsCell(event.x, event.y)
                        if (cell != null) {
                            val newSel = when (touchMode) {
                                TouchMode.ADJUSTING_START ->
                                    TerminalSelection.of(
                                        cell.first, cell.second,
                                        sel.endAbsRow, sel.endCol
                                    )
                                TouchMode.ADJUSTING_END ->
                                    TerminalSelection.of(
                                        sel.startAbsRow, sel.startCol,
                                        cell.first, cell.second
                                    )
                                else -> sel
                            }
                            sess.setSelection(newSel)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchMode = TouchMode.NONE
                }
            }
            return true
        }

        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // SELECTING はジェスチャ完了で抜ける (選択結果は維持)
            if (touchMode == TouchMode.SELECTING) {
                touchMode = TouchMode.NONE
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private enum class Handle { START, END }

    private fun hitTestHandle(x: Float, y: Float): Handle? {
        val sess = session ?: return null
        val sel = sess.selection.value ?: return null
        val m = sess.cellMetrics.value
        if (m.cellW <= 0f || m.lineHeight <= 0f) return null

        val topAbsRow = currentTopAbsRow(sess, m) ?: return null
        val radius = m.lineHeight * 0.65f
        val r2 = radius * radius

        val startCanvasRow = sel.startAbsRow - topAbsRow
        if (startCanvasRow in 0 until m.canvasRows) {
            val sx = sel.startCol * m.cellW
            val sy = (startCanvasRow + 1) * m.lineHeight
            val dx = x - sx
            val dy = y - sy
            if (dx * dx + dy * dy <= r2) return Handle.START
        }
        val endCanvasRow = sel.endAbsRow - topAbsRow
        if (endCanvasRow in 0 until m.canvasRows) {
            val ex = (sel.endCol + 1) * m.cellW
            val ey = (endCanvasRow + 1) * m.lineHeight
            val dx = x - ex
            val dy = y - ey
            if (dx * dx + dy * dy <= r2) return Handle.END
        }
        return null
    }

    /**
     * マウスクリックをエミュレータに送る。
     *
     * 動作:
     *  - タップ位置 → セル座標 (画面内 row/col)
     *  - emulator.encodeMouseEvent(button=0) で press + release のバイト列を作り PTY へ
     *
     * 制約:
     *  - scrollback 表示中 (scrollOffset > 0) でタップ位置が画面外なら送らない
     *  - 画面サイズが 0 や cellMetrics 未計測の場合も送らない
     *
     * @return 実際に PTY 送信したら true
     */
    private fun sendMouseClick(x: Float, y: Float, sess: TerminalSession): Boolean {
        val cell = pixelToAbsCell(x, y) ?: return false
        val emu = sess.emulator
        val screenRow = cell.first - emu.buffer.scrollbackSize
        if (screenRow !in 0 until emu.buffer.rows) return false
        val col = cell.second.coerceIn(0, emu.buffer.columns - 1)
        val press = emu.encodeMouseEvent(
            button = com.zerotoship.z2term.emulator.TerminalEmulator.MOUSE_BTN_LEFT,
            col0 = col, row0 = screenRow, press = true
        ) ?: return false
        sess.writeBytes(press)
        val release = emu.encodeMouseEvent(
            button = com.zerotoship.z2term.emulator.TerminalEmulator.MOUSE_BTN_LEFT,
            col0 = col, row0 = screenRow, press = false
        )
        if (release != null) sess.writeBytes(release)
        return true
    }

    private fun pixelToAbsCell(x: Float, y: Float): Pair<Int, Int>? {
        val sess = session ?: return null
        val m = sess.cellMetrics.value
        if (m.cellW <= 0f || m.lineHeight <= 0f) return null
        val topAbsRow = currentTopAbsRow(sess, m) ?: return null
        val canvasRow = (y / m.lineHeight).toInt().coerceIn(0, m.canvasRows - 1)
        val canvasCol = (x / m.cellW).toInt().coerceIn(0, m.canvasCols - 1)
        return (topAbsRow + canvasRow) to canvasCol
    }

    private fun currentTopAbsRow(sess: TerminalSession, m: com.zerotoship.z2term.core.CellMetrics): Int? {
        if (m.canvasRows <= 0) return null
        val emu = sess.emulator
        val buf = emu.buffer
        val scrollOffset = sess.scrollOffset.value
        val cursorAbsRow = buf.scrollbackSize + emu.cursorRow
        val bottomAbsRow = if (scrollOffset == 0) {
            cursorAbsRow.coerceAtLeast(buf.scrollbackSize + m.canvasRows - 1)
        } else {
            buf.scrollbackSize + buf.rows - 1 - scrollOffset
        }
        return bottomAbsRow - m.canvasRows + 1
    }
}

private class TerminalInputConnection(
    private val targetView: TerminalInputView
) : BaseInputConnection(targetView, /* fullEditor = */ true) {

    private val session: TerminalSession? get() = targetView.session

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        return super.setComposingText(text, newCursorPosition)
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        super.commitText(text, newCursorPosition)
        flushEditable()
        return true
    }

    override fun finishComposingText(): Boolean {
        super.finishComposingText()
        flushEditable()
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        super.deleteSurroundingText(beforeLength, afterLength)
        if (beforeLength > 0) {
            val sess = session
            if (sess != null) {
                val bs = ByteArray(beforeLength) { 0x7F }
                sess.writeBytes(bs)
            }
        }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val sess = session
            if (sess != null) {
                val bytes = AndroidKeyMapper.mapKeyEvent(event, targetView.ctrlSticky) { key ->
                    sess.emulator.cursorKeyBytes(key)
                }
                if (bytes != null) {
                    sess.writeBytes(bytes)
                    return true
                }
            }
        }
        return super.sendKeyEvent(event)
    }

    private fun flushEditable() {
        val editable = editable ?: return
        if (editable.isEmpty()) return
        val text = editable.toString().replace('\n', '\r')
        session?.writeBytes(text.toByteArray(Charsets.UTF_8))
        editable.clear()
    }
}
