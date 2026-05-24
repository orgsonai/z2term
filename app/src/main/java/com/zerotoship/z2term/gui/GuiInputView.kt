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
 * ポインタ（トラックパッド式の「相対移動」。仮想カーソルを保持し触った位置へは飛ばない）:
 *  - 1 本指移動      : カーソルを相対移動（リモート側 X カーソルが動く）
 *  - 単タップ        : 現在位置で左クリック
 *  - 長押し          : 現在位置で右クリック
 *  - ダブルタップ＋移動 : 左押下を保持したまま移動（ウィンドウ移動・選択。離すと解放）
 *  - ピンチ          : ズーム（[GuiViewport] を更新、[GuiScreen] と共有）
 *  - 2 本指移動      : ズーム中はパン / 等倍時はホイールスクロール
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

    /** ズーム/パンの表示変換 (GuiScreen と共有)。null の間は等倍フィット相当。 */
    var viewport: GuiViewport? = null

    /** SYSTEM キーボード時の sticky Ctrl。ON の間、OS IME 確定文字を Ctrl 修飾付きで送る。 */
    var ctrlSticky: Boolean = false

    /** sticky Ctrl を 1 文字に適用したら呼ぶ (呼び出し側でトグル解除する)。 */
    var onCtrlConsumed: (() -> Unit)? = null

    private var imeShown: Boolean = false

    // --- 仮想カーソル（トラックパッド式の相対移動）---
    // RFB は絶対座標しか送れないので、こちらで仮想カーソル位置 (FB 座標) を保持し、
    // 指の移動量ぶんだけ動かして「現在位置」へ PointerEvent を送る。タップは現在位置で
    // クリック（タッチ位置へはジャンプしない）。リモート側 X サーバが実カーソルを描く。
    private var cursorFx = 0f
    private var cursorFy = 0f
    private var cursorInit = false
    private var dragHeld = false          // 左ボタン押下保持中（ダブルタップ→ドラッグ）
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // --- 2 本指ジェスチャ（ピンチ=ズーム / ドラッグ=パン or ホイール）---
    private var twoFinger = false        // 2 本指中フラグ (1 本に戻った後のクリック抑止)
    private var twoFingerActive = false  // span/centroid 追跡中
    private var prevSpan = 0f
    private var prevCx = 0f
    private var prevCy = 0f
    private var scrollAccumY = 0f        // 等倍時のホイール用 (画面 px 蓄積)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 物理キーボード / キー注入が即届くようフォーカスを確保（ソフト IME は FAB で出す）。
        requestFocus()
    }

    /** 表示座標 (x,y) → FB 座標。ズーム/パン (viewport) を反映。FB 未確定・画面外は null。 */
    private fun toFb(x: Float, y: Float): Pair<Int, Int>? {
        val client = rfb ?: return null
        val fbW = client.width
        val fbH = client.height
        if (fbW <= 0 || fbH <= 0 || width <= 0 || height <= 0) return null
        val fitScale = minOf(width.toFloat() / fbW, height.toFloat() / fbH)
        if (fitScale <= 0f) return null
        val vp = viewport
        val eff = fitScale * (vp?.scale ?: 1f)
        val left = (width - fbW * eff) / 2f + (vp?.panX ?: 0f)
        val top = (height - fbH * eff) / 2f + (vp?.panY ?: 0f)
        val fx = ((x - left) / eff).toInt().coerceIn(0, fbW - 1)
        val fy = ((y - top) / eff).toInt().coerceIn(0, fbH - 1)
        return fx to fy
    }

    /** 表示倍率 (フィット × ズーム)。未確定なら null。相対移動量を FB 量へ換算するのに使う。 */
    private fun effScale(): Float? {
        val c = rfb ?: return null
        if (c.width <= 0 || c.height <= 0 || width <= 0 || height <= 0) return null
        val fit = minOf(width.toFloat() / c.width, height.toFloat() / c.height)
        if (fit <= 0f) return null
        return fit * (viewport?.scale ?: 1f)
    }

    /** 仮想カーソルを FB 中央に初期化（接続直後の 1 回）。実カーソルを表示するため move も送る。 */
    private fun ensureCursor() {
        if (cursorInit) return
        val c = rfb ?: return
        if (c.width <= 0 || c.height <= 0) return
        cursorFx = c.width / 2f
        cursorFy = c.height / 2f
        cursorInit = true
        c.sendPointerEvent(0, cursorFx.toInt(), cursorFy.toInt())
    }

    /** 指の移動量 (画面 px) ぶん仮想カーソルを動かし、現在位置へ送る（ドラッグ中は左押下のまま）。 */
    private fun moveCursorBy(dxScreen: Float, dyScreen: Float) {
        val c = rfb ?: return
        ensureCursor()
        val eff = effScale() ?: return
        cursorFx = (cursorFx + dxScreen / eff).coerceIn(0f, (c.width - 1).toFloat())
        cursorFy = (cursorFy + dyScreen / eff).coerceIn(0f, (c.height - 1).toFloat())
        c.sendPointerEvent(if (dragHeld) RfbClient.BTN_LEFT else 0, cursorFx.toInt(), cursorFy.toInt())
    }

    /** 現在のカーソル位置で 1 クリック（ボタン押下→解放）。 */
    private fun clickAtCursor(button: Int) {
        val c = rfb ?: return
        ensureCursor()
        c.sendPointerEvent(button, cursorFx.toInt(), cursorFy.toInt())
        c.sendPointerEvent(0, cursorFx.toInt(), cursorFy.toInt())
    }

    /** 保持中の左ドラッグを解放する。 */
    private fun releaseDrag() {
        if (!dragHeld) return
        dragHeld = false
        rfb?.sendPointerEvent(0, cursorFx.toInt(), cursorFy.toInt())
    }

    private val gesture = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            // タップ = 現在のカーソル位置で左クリック（タッチ位置へは飛ばない）。
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (dragHeld) return false
                clickAtCursor(RfbClient.BTN_LEFT)
                return true
            }

            // 長押し = 現在位置で右クリック。
            override fun onLongPress(e: MotionEvent) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                clickAtCursor(RfbClient.BTN_RIGHT)
            }

            // ダブルタップ = 左押下を保持（そのまま動かせばドラッグ＝ウィンドウ移動/選択、離せばダブルクリック）。
            override fun onDoubleTap(e: MotionEvent): Boolean {
                ensureCursor()
                dragHeld = true
                rfb?.sendPointerEvent(RfbClient.BTN_LEFT, cursorFx.toInt(), cursorFy.toInt())
                return true
            }
        }
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        // 2 本指: ピンチ=ズーム / ドラッグ=ズーム中はパン・等倍時はホイール（gesture には渡さない）
        if (event.pointerCount >= 2) {
            if (dragHeld) releaseDrag() // 1→2 本指に増えたらドラッグ保持を解除
            if (!twoFingerActive) {
                twoFingerActive = true
                prevSpan = spanOf(event)
                val c = centroidOf(event); prevCx = c.first; prevCy = c.second
                scrollAccumY = 0f
            } else if (action == MotionEvent.ACTION_MOVE) {
                handleTwoFingerTransform(event)
            }
            twoFinger = true
            return true
        }
        twoFingerActive = false

        if (twoFinger) {
            // 2→1 本指へ戻った直後。全指が離れるまでクリック等を起こさない。
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                twoFinger = false
                scrollAccumY = 0f
            }
            return true
        }

        // 1 本指: タップ/長押し/ダブルタップは gesture が判定。移動は相対カーソル移動を
        // ここで手動処理する（gesture.onScroll に頼らず仮想カーソルを動かす）。
        gesture.onTouchEvent(event)
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                if (dx != 0f || dy != 0f) moveCursorBy(dx, dy)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragHeld) releaseDrag() // ドラッグ保持の終了 = 左ボタン解放
            }
        }
        return true
    }

    /** 2 本指の移動 1 フレーム分を処理: ピンチでズーム、並進はズーム中=パン / 等倍=ホイール。 */
    private fun handleTwoFingerTransform(event: MotionEvent) {
        val vp = viewport ?: return
        val c = centroidOf(event)
        val cx = c.first; val cy = c.second
        val span = spanOf(event)

        // 1) ピンチ → ズーム (centroid を固定点にして拡大)
        if (prevSpan > 0f && span > 0f) {
            val newScale = (vp.scale * (span / prevSpan)).coerceIn(1f, MAX_ZOOM)
            if (newScale != vp.scale) zoomAround(vp, newScale, cx, cy)
        }
        // 2) 並進: ズーム中はパン / 等倍はホイール
        val dx = cx - prevCx
        val dy = cy - prevCy
        if (vp.scale > 1f + 1e-3f) panBy(vp, dx, dy) else accumulateWheel(dy, cx, cy)

        prevSpan = span
        prevCx = cx
        prevCy = cy
    }

    private fun spanOf(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        return kotlin.math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    }

    private fun centroidOf(e: MotionEvent): Pair<Float, Float> {
        if (e.pointerCount < 2) return e.x to e.y
        return ((e.getX(0) + e.getX(1)) / 2f) to ((e.getY(0) + e.getY(1)) / 2f)
    }

    /** centroid (cx,cy) の下の FB 点を固定したまま [newScale] へズーム。 */
    private fun zoomAround(vp: GuiViewport, newScale: Float, cx: Float, cy: Float) {
        val client = rfb ?: return
        val fbW = client.width; val fbH = client.height
        if (fbW <= 0 || fbH <= 0 || width <= 0 || height <= 0) return
        val fitScale = minOf(width.toFloat() / fbW, height.toFloat() / fbH)
        if (fitScale <= 0f) return
        val oldEff = fitScale * vp.scale
        val oldLeft = (width - fbW * oldEff) / 2f + vp.panX
        val oldTop = (height - fbH * oldEff) / 2f + vp.panY
        val fbx = (cx - oldLeft) / oldEff
        val fby = (cy - oldTop) / oldEff
        val newEff = fitScale * newScale
        val newDw = fbW * newEff; val newDh = fbH * newEff
        val panX = (cx - fbx * newEff) - (width - newDw) / 2f
        val panY = (cy - fby * newEff) - (height - newDh) / 2f
        val (cpx, cpy) = clampPan(panX, panY, newDw, newDh)
        vp.apply(newScale, cpx, cpy)
    }

    private fun panBy(vp: GuiViewport, dx: Float, dy: Float) {
        val client = rfb ?: return
        val fbW = client.width; val fbH = client.height
        if (fbW <= 0 || fbH <= 0 || width <= 0 || height <= 0) return
        val eff = minOf(width.toFloat() / fbW, height.toFloat() / fbH) * vp.scale
        val (cpx, cpy) = clampPan(vp.panX + dx, vp.panY + dy, fbW * eff, fbH * eff)
        vp.apply(vp.scale, cpx, cpy)
    }

    /** パン量を画像が画面から外れない範囲にクランプ (拡大表示が画面より大きいときのみ移動可)。 */
    private fun clampPan(panX: Float, panY: Float, dw: Float, dh: Float): Pair<Float, Float> {
        val maxX = maxOf(0f, (dw - width) / 2f)
        val maxY = maxOf(0f, (dh - height) / 2f)
        return panX.coerceIn(-maxX, maxX) to panY.coerceIn(-maxY, maxY)
    }

    /** 等倍時の 2 本指縦移動 (画面 px) を貯めて一定量ごとにホイール 1 ノッチを送る。 */
    private fun accumulateWheel(dyScreen: Float, cx: Float, cy: Float) {
        val (fx, fy) = toFb(cx, cy) ?: return
        val c = rfb ?: return
        scrollAccumY += dyScreen
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

    /** OS ソフト IME を表示する (SYSTEM キーボードモードで使う)。冪等。 */
    fun showIme() {
        if (imeShown) return
        if (!isFocused) requestFocus()
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        imeShown = true
    }

    /** OS ソフト IME を隠す。冪等。 */
    fun hideIme() {
        if (!imeShown) return
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(windowToken, 0)
        imeShown = false
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
        /** ホイール 1 ノッチに必要な縦移動量(画面 px)。小さいほど敏感。 */
        const val WHEEL_STEP = 40f
        /** ピンチズームの最大倍率 (フィット基準)。 */
        const val MAX_ZOOM = 5f
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
                if (keysym != 0) {
                    if (view.ctrlSticky) {
                        GuiKeyMapper.sendKeysymWithCtrl(c, keysym)
                        view.onCtrlConsumed?.invoke()
                    } else {
                        c.tapKey(keysym)
                    }
                }
                i += Character.charCount(cp)
            }
        }
    }
}
