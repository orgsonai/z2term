package com.zerotoship.z2term.gui

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
 *  - 1 本指移動        : カーソルを相対移動（リモート側 X カーソルが動く）
 *  - 単タップ          : 現在位置で左クリック（タッチ位置へは飛ばない）
 *                        ⚠ 送るのはダブルタップの猶予が過ぎてから（下記）
 *  - 長押し            : 動かさず [GuiCursor.HOLD_MS] 保持＝現在位置で右クリック
 *                        （タップの時間を越えてから輪が出る。タップでは何も出ない）
 *  - ダブルタップ＋移動 : 左押下を保持したまま移動（ウィンドウ移動・選択。離すと解放）
 *                        **時間制限は無い** — 押したまま狙いを定めてから動かしてよい
 *  - ダブルタップして離す: 左クリック（1 回目のタップと合わせてダブルクリック）
 *  - ピンチ            : ズーム（[GuiViewport] を更新、[GuiScreen] と共有）
 *  - 2 本指移動        : ズーム中はパン / 等倍時はホイールスクロール
 *
 * ※ 単タップ長押しの右クリックは M8-6 T3/T4/T5 で一度**廃止**した。長押しタイマーがピンチや
 *   ダブルタップドラッグと干渉して誤右クリック・ドラッグ解除を起こしていたため。
 *   ⭐ **0.8.431 で戻した**（利用者の要望。右クリックはメニューを出す一番よく使う操作なので、
 *   ダブルタップの 2 回目を保持する道だけでは遠い）。今度干渉しないのは、①ダブルタップの
 *   2 回目 (`dtHolding` 中) では長押しを始めない ②2 本指になった時点で捨てる
 *   ③touch slop を超えたら捨てる ④成立したら同じタッチの離しをタップにしない (`holdFired`)、
 *   を入口で守っているから。
 *
 * ※ ⛔ **ダブルタップ側の右クリックは 0.8.435 で外した。** 長押しで右クリックが出せるように
 *   なった時点で**同じ操作への 2 本目の道**になっていたうえ、⚠ **タイマーが先に発火すると
 *   判定が終わってしまい、そのあと動かしてもドラッグに入れない**（利用者の報告
 *   「ダブルタップ判定でドラッグできない」）。⇒ ダブルタップの 2 回目は**ドラッグかクリックか
 *   だけ**を見る。時間で勝手に決めないので、**押したまま狙いを定めてから動かせる**。
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

    /** 仮想カーソルの位置と形 (GuiSession / GuiScreen と共有)。 */
    var cursor: GuiCursor? = null

    /** SYSTEM キーボード時の sticky Ctrl。ON の間、OS IME 確定文字を Ctrl 修飾付きで送る。 */
    var ctrlSticky: Boolean = false

    /** sticky Ctrl を 1 文字に適用したら呼ぶ (呼び出し側でトグル解除する)。 */
    var onCtrlConsumed: (() -> Unit)? = null

    private var imeShown: Boolean = false

    // --- 仮想カーソル（トラックパッド式の相対移動）---
    // RFB は絶対座標しか送れないので、GuiSession の GuiCursor に位置 (FB 座標) を保持し、
    // 指の移動量ぶんだけ動かして「現在位置」へ PointerEvent を送る。タップは現在位置で
    // クリック（タッチ位置へはジャンプしない）。カーソルの絵は GuiScreen が必ず描く。
    private var dragHeld = false          // 左ボタン押下保持中（ダブルタップ→ドラッグ）
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // --- タップ / ダブルタップの判定 (0.8.436 で GestureDetector から自前へ) ---
    //
    // ⛔ **1 回目のタップの左クリックを、その場で送らない。**
    // 送ってしまうと、続けてダブルタップドラッグに入ったとき相手側には
    // 「クリック → 至近距離ですぐ押下」= **ダブルクリックしてから引きずった**と見える
    // (利用者の報告「ダブルタップ後わずかに停止してドラッグしないと正しく動作しない」。
    //  止めると間隔が空くので、相手のダブルクリック判定から外れて正しくドラッグになる)。
    // ⇒ タップの左クリックは [doubleTapMs] だけ**保留**し、
    //    ・2 回目の指が来たら **捨てる**（ドラッグ or ダブルクリックのどちらかになる）
    //    ・関係ないタップが来たら **その場で送って**取りこぼさない
    //    ・時間切れなら送る（＝ふつうの単クリック）
    // ⚠ **代償はクリックが [doubleTapMs] 遅れること。** 相手のダブルクリック判定より短い
    //    間隔で「クリック → 押下」を出さないためには、これしか方法が無い
    //    (位置をずらして誤魔化す手は、相手側のしきい値と拡大率に依存するので使わない)。
    //
    // ダブルタップの 2 回目を押している間 (`dtHolding`):
    //  - touchSlop 超え → 左ドラッグへ切替（押したまま狙いを定めてからでよい）
    //  - 離した        → 左クリック 2 連打 = ダブルクリック（1 回目を捨てているため）
    private var dtHolding = false
    private var pendingClick = false
    private var lastTapUpTime = 0L
    private var lastTapUpX = 0f
    private var lastTapUpY = 0f
    private var downX = 0f
    private var downY = 0f
    private var movedFar = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat()
    private val doubleTapMs = ViewConfiguration.getDoubleTapTimeout().toLong()

    // --- 1 本指長押し → 右クリック (0.8.431) ---
    // **動かさずに [GuiCursor.HOLD_MS] 保持したら右クリック**を送る。押している間は
    // [GuiCursor.holdStart] を立てて [GuiScreen] に輪を描かせる（何も出ないと「押しているつもり」
    // がどこまで進んだのか分からない）。ダブルタップ側と同じく**指を離すのを待たない**。
    // ⚠ [holdFired] を立てないと、離した時に「ふつうのタップ」として扱われ、**右クリックの
    // 直後に左クリックが出る**（指は動いていないので、そのままではタップと区別が付かない）。
    private var pendingHoldClick = false
    private var holdFired = false
    private var holdDownX = 0f
    private var holdDownY = 0f

    // --- 2 本指ジェスチャ（ピンチ=ズーム / ドラッグ=パン or ホイール）---
    private var twoFinger = false        // 2 本指中フラグ (1 本に戻った後のクリック抑止)
    private var twoFingerActive = false  // span/centroid 追跡中
    private var prevSpan = 0f
    private var prevCx = 0f
    private var prevCy = 0f
    private var scrollAccumY = 0f        // ホイール用 (画面 px 蓄積)
    // --- 3 本指ジェスチャ (アプリ内スクロール) ---
    private var scrollGesture = false    // 一度 3 本指になったら全指が離れるまでスクロール扱い
    private var prevCx3 = 0f
    private var prevCy3 = 0f

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 物理キーボード / キー注入が即届くようフォーカスを確保（ソフト IME は FAB で出す）。
        requestFocus()
    }

    override fun onDetachedFromWindow() {
        // タブ切替や切断で View が破棄されても、相手側の左ボタンと共有カーソルの
        // 押下表示を保持したままにしない。
        dtHolding = false
        cancelPendingClick()
        cancelHoldClick()
        holdFired = false
        releaseDrag()
        super.onDetachedFromWindow()
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

    /** 仮想カーソルを FB 中央に初期化（セッション中の初回だけ）。 */
    private fun ensureCursor(): GuiCursor.Snapshot? {
        val c = rfb ?: return null
        return cursor?.fitTo(c.width, c.height)
    }

    /** 指の移動量 (画面 px) ぶん仮想カーソルを動かし、現在位置へ送る（ドラッグ中は左押下のまま）。 */
    private fun moveCursorBy(dxScreen: Float, dyScreen: Float) {
        val c = rfb ?: return
        val eff = effScale() ?: return
        val pos = cursor?.moveBy(dxScreen / eff, dyScreen / eff, c.width, c.height) ?: return
        c.sendPointerEvent(if (dragHeld) RfbClient.BTN_LEFT else 0, pos.x.toInt(), pos.y.toInt())
    }

    /** 画面上で触れている位置へ仮想カーソルを直接移動する（絶対座標モード）。 */
    private fun moveCursorTo(xScreen: Float, yScreen: Float) {
        val c = rfb ?: return
        val (fx, fy) = toFb(xScreen, yScreen) ?: return
        val pos = cursor?.moveTo(fx.toFloat(), fy.toFloat(), c.width, c.height) ?: return
        c.sendPointerEvent(if (dragHeld) RfbClient.BTN_LEFT else 0, pos.x.toInt(), pos.y.toInt())
    }

    /** 現在のカーソル位置で 1 クリック（ボタン押下→解放）。 */
    private fun clickAtCursor(button: Int) {
        val c = rfb ?: return
        val pos = ensureCursor() ?: return
        c.sendPointerEvent(button, pos.x.toInt(), pos.y.toInt())
        c.sendPointerEvent(0, pos.x.toInt(), pos.y.toInt())
    }

    /** 保持中の左ドラッグを解放する。 */
    private fun releaseDrag() {
        if (!dragHeld) return
        dragHeld = false
        cursor?.setPressed(false)
        val pos = ensureCursor() ?: return
        rfb?.sendPointerEvent(0, pos.x.toInt(), pos.y.toInt())
    }

    /** 1 本指を動かさず [GuiCursor.HOLD_MS] 保持したら、指を離すのを待たずに右クリックを送る。 */
    private val holdClickRunnable = Runnable {
        if (!pendingHoldClick) return@Runnable
        pendingHoldClick = false
        holdFired = true            // この指を離しても左クリックにしない
        cursor?.endHold()
        // 触覚は「右クリックを送った」合図。輪が閉じるのと同時に鳴らす。
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        clickAtCursor(RfbClient.BTN_RIGHT)
    }

    /**
     * 輪を出し始める（指を置いてから [GuiCursor.HOLD_RING_DELAY_MS] 後）。
     *
     * ⛔ **指を置いた瞬間には出さない。** タップやドラッグのたびに一瞬光ってうるさい
     * (0.8.433・利用者の指摘)。**タップと長押しは別の操作**なので、タップの時間を
     * 越えて初めて「長押しが始まった」ことを見せる。
     */
    private val holdRingRunnable = Runnable {
        if (!pendingHoldClick) return@Runnable
        // postDelayed と同じ時計 (uptimeMillis) で輪の進み具合を計る。イベントの時刻を使うと
        // 数 ms 過去から数え始めて、輪が閉じた後に少し間があく。
        cursor?.beginHold(SystemClock.uptimeMillis())
    }

    /** 長押し判定を開始する（指を置いた所を覚え、輪と右クリックのタイマーを張る）。 */
    private fun beginHoldClick(e: MotionEvent) {
        pendingHoldClick = true
        holdFired = false
        holdDownX = e.x
        holdDownY = e.y
        removeCallbacks(holdRingRunnable)
        removeCallbacks(holdClickRunnable)
        postDelayed(holdRingRunnable, GuiCursor.HOLD_RING_DELAY_MS)
        postDelayed(holdClickRunnable, GuiCursor.HOLD_MS)
    }

    /** 長押し判定を捨てる（移動・2 本指・指を離した・ダブルタップへ移行）。輪も消す。 */
    private fun cancelHoldClick() {
        pendingHoldClick = false
        removeCallbacks(holdRingRunnable)
        removeCallbacks(holdClickRunnable)
        cursor?.endHold()
    }

    /** 保留していたタップの左クリックを送る（ダブルタップが来なかった＝ふつうの単クリック）。 */
    private val singleClickRunnable = Runnable {
        if (!pendingClick) return@Runnable
        pendingClick = false
        clickAtCursor(RfbClient.BTN_LEFT)
        performClick()
    }

    /** 保留中のタップを**今すぐ**送る。関係ないタッチが始まったときに取りこぼさないため。 */
    private fun flushPendingClick() {
        if (!pendingClick) return
        removeCallbacks(singleClickRunnable)
        singleClickRunnable.run()
    }

    /** 保留中のタップを**捨てる**。ダブルタップの 2 回目が来たときだけ使う。 */
    private fun cancelPendingClick() {
        pendingClick = false
        removeCallbacks(singleClickRunnable)
    }

    /**
     * この ACTION_DOWN が直前のタップの続き（＝ダブルタップの 2 回目）か。
     * 判定は Android の作法に合わせ、**時間 ([doubleTapMs]) と距離 ([doubleTapSlop])** の両方で見る。
     */
    private fun isSecondTap(e: MotionEvent): Boolean =
        pendingClick &&
            (e.eventTime - lastTapUpTime) <= doubleTapMs &&
            kotlin.math.hypot(e.x - lastTapUpX, e.y - lastTapUpY) <= doubleTapSlop

    // クリック通知 (performClick) は [singleClickRunnable] で出している。
    // lint はこのメソッド本体しか見ないため検出できず誤検知になる。
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        // 2 本指: ピンチ=ズーム / ドラッグ=ズーム中はパン・等倍時はホイール
        // 3 本指: アプリ内スクロール (縦移動をホイールへ。ズーム/パンはしない)
        if (event.pointerCount >= 2) {
            if (dragHeld) releaseDrag() // 1→2 本指に増えたらドラッグ保持を解除
            dtHolding = false           // 2 本指へ移行 → ダブルタップの判定は破棄 (T3)
            cancelHoldClick()           // 2 本指はズーム/パン。長押し右クリックにはしない
            holdFired = false
            // 一度でも 3 本指になったら、指が 2 本に減っても全部離すまでスクロール扱い。
            if (event.pointerCount >= 3) scrollGesture = true
            if (!twoFingerActive) {
                twoFingerActive = true
                prevSpan = spanOf(event)
                val c = centroidOf(event); prevCx = c.first; prevCy = c.second
                val a = avgCentroid(event); prevCx3 = a.first; prevCy3 = a.second
                scrollAccumY = 0f
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (scrollGesture) handleThreeFingerScroll(event)
                else handleTwoFingerTransform(event)
            }
            twoFinger = true
            return true
        }
        twoFingerActive = false
        scrollGesture = false

        if (twoFinger) {
            // 2→1 本指へ戻った直後。全指が離れるまでクリック等を起こさない。
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                twoFinger = false
                scrollAccumY = 0f
            }
            return true
        }

        // 1 本指: タップ / ダブルタップ / 長押し / 移動をすべてここで判定する。
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                downX = event.x
                downY = event.y
                movedFar = false
                // ⭐ **ここが肝**: 2 回目の指なら 1 回目のクリックを**送らずに捨てる**。
                // 別のタップなら取りこぼさないよう**その場で送る**
                // (⚠ 絶対座標モードでカーソルを動かす前に送る。動かした後だと、
                //  保留していたクリックが今触った場所へ飛んでしまう)。
                if (isSecondTap(event)) {
                    cancelPendingClick()
                    ensureCursor()
                    dtHolding = true
                } else {
                    flushPendingClick()
                    dtHolding = false
                }
                if (cursor?.snapshot()?.mode == GuiCursor.Mode.ABSOLUTE) {
                    moveCursorTo(event.x, event.y)
                }
                // ダブルタップの 2 回目はドラッグ / ダブルクリックの判定に任せる
                // (長押し右クリックはここでは始めない)。
                if (!dtHolding) beginHoldClick(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!movedFar &&
                    kotlin.math.hypot(event.x - downX, event.y - downY) > touchSlop
                ) {
                    movedFar = true
                }
                if (dtHolding) {
                    // ダブルタップ保持中。slop を超えて動いたら左ドラッグへ切替。
                    // ⭐ **待ち時間は無い** — ここで止まっていても判定は終わらないので、
                    // 狙いを定めてから動かしてもドラッグになる。
                    if (movedFar) {
                        dtHolding = false
                        dragHeld = true
                        val pos = ensureCursor()
                        if (pos != null) {
                            cursor?.setPressed(true)
                            rfb?.sendPointerEvent(RfbClient.BTN_LEFT, pos.x.toInt(), pos.y.toInt())
                            // ボタンを押した位置から、slop を越えた現在位置までの最初の移動も
                            // 捨てずに送る。絶対は現在の指位置、相対はその差分を使う。
                            if (cursor?.snapshot()?.mode == GuiCursor.Mode.ABSOLUTE) {
                                moveCursorTo(event.x, event.y)
                            } else {
                                moveCursorBy(event.x - lastTouchX, event.y - lastTouchY)
                            }
                        }
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    return true
                }
                if (pendingHoldClick && movedFar) {
                    // 指が動いた = カーソルを運びたいだけ。右クリックにはしない。
                    cancelHoldClick()
                }
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                if (cursor?.snapshot()?.mode == GuiCursor.Mode.ABSOLUTE) {
                    moveCursorTo(event.x, event.y)
                } else if (dx != 0f || dy != 0f) {
                    moveCursorBy(dx, dy)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelHoldClick()   // 時間前に離した = 長押しではない。輪を消す
                when {
                    dragHeld -> releaseDrag() // ドラッグ保持の終了 = 左ボタン解放
                    dtHolding -> {
                        // ダブルタップの 2 回目を、動かさずに離した。
                        // ⭐ **1 回目のクリックは捨ててあるので、ここで 2 連打**して
                        // ダブルクリックにする (押していた長さは見ない)。
                        dtHolding = false
                        if (action == MotionEvent.ACTION_UP) {
                            clickAtCursor(RfbClient.BTN_LEFT)
                            clickAtCursor(RfbClient.BTN_LEFT)
                        }
                    }
                    action == MotionEvent.ACTION_UP && !holdFired && !movedFar -> {
                        // ふつうのタップ。⚠ **ここでは送らない** — 次にダブルタップの
                        // 2 回目が来るかもしれないので、[doubleTapMs] だけ保留する。
                        lastTapUpTime = event.eventTime
                        lastTapUpX = event.x
                        lastTapUpY = event.y
                        pendingClick = true
                        removeCallbacks(singleClickRunnable)
                        postDelayed(singleClickRunnable, doubleTapMs)
                    }
                }
                holdFired = false
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

    /** 全ポインタの重心 (3 本指スクロール用)。 */
    private fun avgCentroid(e: MotionEvent): Pair<Float, Float> {
        val n = e.pointerCount
        if (n <= 0) return e.x to e.y
        var sx = 0f; var sy = 0f
        for (i in 0 until n) { sx += e.getX(i); sy += e.getY(i) }
        return (sx / n) to (sy / n)
    }

    /** 3 本指の縦移動をホイールに変換 (アプリ内スクロール)。ズーム/パンはしない。 */
    private fun handleThreeFingerScroll(event: MotionEvent) {
        val (cx, cy) = avgCentroid(event)
        val dy = cy - prevCy3
        prevCx3 = cx
        prevCy3 = cy
        accumulateWheel(dy, cx, cy)
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
