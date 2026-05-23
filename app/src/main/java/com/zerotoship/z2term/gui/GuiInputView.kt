package com.zerotoship.z2term.gui

import android.content.Context
import android.text.InputType
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.zerotoship.z2term.gui.rfb.RfbClient

/**
 * GUI セッションの入力オーバーレイ View (M8-3)。
 *
 * [GuiScreen] の Compose Canvas の上に透明で重ね、タッチとキーを RFB の
 * [PointerEvent][RfbClient.sendPointerEvent] / [KeyEvent][RfbClient.sendKeyEvent] へ変換して送る。
 *
 * ポインタ（「触った位置 = 絶対座標」方式）:
 *  - 単タップ      : 左クリック (press→release)
 *  - 長押し        : 右クリック
 *  - 1 本指ドラッグ : 左ボタン押下のまま移動（ウィンドウ移動・選択など）
 *  - 2 本指上下    : ホイールスクロール
 *
 * キーボード:
 *  - OS ソフト IME の確定文字 (commitText) → 文字ごとに keysym down/up
 *  - 物理キー / 機能キー (Enter/BS/矢印/修飾) → [GuiKeyMapper] で keysym 化し down/up
 *  端末用 keyboard はバイト送出なので、ここは独立した keysym 経路（HANDOFF 3-3）。
 *
 * 表示↔FB 座標変換は [GuiScreen] の「中央フィット」計算と一致させる（同じ Box を fillMaxSize するため
 * この View と Canvas の寸法は等しい）。
 */
class GuiInputView(context: Context) : View(context) {

    var rfb: RfbClient? = null

    private var imeShown: Boolean = false

    // --- ドラッグ状態（左ボタン押下のまま移動）---
    private var dragging = false
    private var lastFx = 0
    private var lastFy = 0

    // --- 2 本指スクロール（ホイール）---
    private var twoFinger = false
    private var scrollAccumY = 0f

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 物理キーボード / キー注入が即届くようフォーカスを確保（ソフト IME は FAB で出す）。
        requestFocus()
    }

    /** 表示座標 (x,y) → FB 座標。FB 未確定・画面外は null。 */
    private fun toFb(x: Float, y: Float): Pair<Int, Int>? {
        val client = rfb ?: return null
        val fbW = client.width
        val fbH = client.height
        if (fbW <= 0 || fbH <= 0 || width <= 0 || height <= 0) return null
        val scale = minOf(width.toFloat() / fbW, height.toFloat() / fbH)
        if (scale <= 0f) return null
        val dw = fbW * scale
        val dh = fbH * scale
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f
        val fx = ((x - left) / scale).toInt().coerceIn(0, fbW - 1)
        val fy = ((y - top) / scale).toInt().coerceIn(0, fbH - 1)
        return fx to fy
    }

    private val gesture = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val (fx, fy) = toFb(e.x, e.y) ?: return false
                val c = rfb ?: return false
                c.sendPointerEvent(RfbClient.BTN_LEFT, fx, fy)  // press
                c.sendPointerEvent(0, fx, fy)                   // release
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val (fx, fy) = toFb(e.x, e.y) ?: return
                val c = rfb ?: return
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                c.sendPointerEvent(RfbClient.BTN_RIGHT, fx, fy)
                c.sendPointerEvent(0, fx, fy)
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val (fx, fy) = toFb(e2.x, e2.y) ?: return false
                val c = rfb ?: return false
                if (!dragging) {
                    dragging = true
                    c.sendPointerEvent(RfbClient.BTN_LEFT, fx, fy) // 左ボタン押下開始
                } else {
                    c.sendPointerEvent(RfbClient.BTN_LEFT, fx, fy) // 押下のまま移動
                }
                lastFx = fx
                lastFy = fy
                return true
            }
        }
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        // 2 本指: ホイールスクロール（gesture には渡さない）
        if (event.pointerCount >= 2) {
            if (dragging) { // 1→2 本指に増えたらドラッグを終う
                rfb?.sendPointerEvent(0, lastFx, lastFy)
                dragging = false
            }
            handleTwoFingerScroll(event)
            twoFinger = true
            return true
        }
        if (twoFinger) {
            // 2→1 本指へ戻った直後。全指が離れるまでクリック等を起こさない。
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                twoFinger = false
                scrollAccumY = 0f
            }
            return true
        }

        gesture.onTouchEvent(event)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (dragging) {
                rfb?.sendPointerEvent(0, lastFx, lastFy) // ドラッグ終了 = 左ボタン解放
                dragging = false
            }
        }
        return true
    }

    /** 2 本指の縦移動を貯めて、一定量ごとにホイール 1 ノッチを送る。 */
    private fun handleTwoFingerScroll(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return
        val cx = (event.getX(0) + event.getX(1)) / 2f
        val cy = (event.getY(0) + event.getY(1)) / 2f
        val (fx, fy) = toFb(cx, cy) ?: return
        val c = rfb ?: return
        // 直近フレームの中心 y との差分を貯める。
        if (scrollAccumY == 0f) { lastFy = fy } // 初回は基準合わせのみ
        val dy = fy - lastFy
        scrollAccumY += dy
        lastFy = fy
        while (scrollAccumY <= -WHEEL_STEP) {     // 指を上へ → コンテンツ下スクロール
            c.sendPointerEvent(RfbClient.BTN_WHEEL_DOWN, fx, fy)
            c.sendPointerEvent(0, fx, fy)
            scrollAccumY += WHEEL_STEP
        }
        while (scrollAccumY >= WHEEL_STEP) {      // 指を下へ → コンテンツ上スクロール
            c.sendPointerEvent(RfbClient.BTN_WHEEL_UP, fx, fy)
            c.sendPointerEvent(0, fx, fy)
            scrollAccumY -= WHEEL_STEP
        }
    }

    // ---- キーボード -------------------------------------------------------

    /** ソフトキーボードを出す / 引っ込めるをトグル（GuiScreen のボタンから呼ぶ）。 */
    fun toggleKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        if (imeShown) {
            imm?.hideSoftInputFromWindow(windowToken, 0)
            imeShown = false
        } else {
            if (!isFocused) requestFocus()
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            imeShown = true
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = (
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_ACTION_NONE
            )
        return GuiInputConnection(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // BACK 等は系統に委ねる（戻る/IME 閉じる）。
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        val keysym = GuiKeyMapper.keysymForKeyEvent(event)
        if (keysym == 0) return super.onKeyDown(keyCode, event)
        rfb?.sendKeyEvent(keysym, down = true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyUp(keyCode, event)
        val keysym = GuiKeyMapper.keysymForKeyEvent(event)
        if (keysym == 0) return super.onKeyUp(keyCode, event)
        rfb?.sendKeyEvent(keysym, down = false)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        /** ホイール 1 ノッチに必要な FB 縦移動量(px)。小さいほど敏感。 */
        const val WHEEL_STEP = 40f
    }

    /** IME の確定文字を keysym で送る InputConnection。 */
    private class GuiInputConnection(
        private val view: GuiInputView,
    ) : BaseInputConnection(view, /* fullEditor = */ false) {

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            sendAsKeysyms(text)
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            // GUI 側に変換中表示は無いので、確定 (commitText) でまとめて送る。ここでは何もしない。
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val c = view.rfb ?: return true
            repeat(beforeLength.coerceAtLeast(0)) { c.tapKey(GuiKeyMapper.XK_BackSpace) }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            // 一部 IME は Enter/Backspace 等を KeyEvent で送ってくる。
            val keysym = GuiKeyMapper.keysymForKeyEvent(event)
            if (keysym != 0) {
                view.rfb?.sendKeyEvent(keysym, down = event.action == KeyEvent.ACTION_DOWN)
                return true
            }
            return super.sendKeyEvent(event)
        }

        private fun sendAsKeysyms(text: CharSequence?) {
            val c = view.rfb ?: return
            val s = text?.toString() ?: return
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                val keysym = GuiKeyMapper.keysymForCodePoint(cp)
                if (keysym != 0) c.tapKey(keysym)
                i += Character.charCount(cp)
            }
        }
    }
}
