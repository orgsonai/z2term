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

/** 選択ドラッグが画面端にある間、自動スクロールを繰り返す間隔。 */
private const val AUTO_SCROLL_INTERVAL_MS = 45L
/** フリング(慣性スクロール)の減衰係数。1 フレーム(約16ms)ごとに速度へ乗算。 */
private const val FLING_DECELERATION = 0.90f
/** マウスレポーティング有効時、スワイプを 1 ホイールノッチに換算するピクセル量。 */
private const val MOUSE_WHEEL_STEP_PX = 40f

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

    /** sticky Ctrl を確定文字へ 1 回適用したとき呼ばれる (呼び出し側で解除＝ワンショット)。 */
    var onCtrlConsumed: (() -> Unit)? = null

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
    private var mouseWheelAccumDy: Float = 0f

    // --- 選択 UX 補助 (拡大鏡 / 端で自動スクロール) ---
    private var magnifier: android.widget.Magnifier? = null
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var autoScrollDir: Int = 0  // -1=最新へ / +1=過去へ / 0=停止
    private var autoScrollRowsPerTick: Int = 1  // 端から離れるほど増やす
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            val sess = session ?: return
            if (autoScrollDir == 0 || touchMode == TouchMode.NONE) return
            repeat(autoScrollRowsPerTick) { sess.scrollBy(autoScrollDir) }
            applySelectionAt(lastTouchX, lastTouchY)
            showMagnifierAt(lastTouchX, lastTouchY)
            postDelayed(this, AUTO_SCROLL_INTERVAL_MS)
        }
    }

    // --- フリング(慣性スクロール) ---
    // 正 = 過去へ / 負 = 最新へ。1 フレームあたりに進める行数 (小数)。
    private var flingVelocityRows: Float = 0f
    private val flingRunnable = object : Runnable {
        override fun run() {
            val sess = session ?: return
            // 選択中・速度ほぼ0 で停止
            if (touchMode != TouchMode.NONE) { flingVelocityRows = 0f; return }
            if (flingVelocityRows > -0.5f && flingVelocityRows < 0.5f) { flingVelocityRows = 0f; return }
            val delta = if (flingVelocityRows > 0)
                flingVelocityRows.toInt().coerceAtLeast(1)
            else
                flingVelocityRows.toInt().coerceAtMost(-1)
            sess.scrollBy(delta)
            flingVelocityRows *= FLING_DECELERATION
            postDelayed(this, 16)
        }
    }

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
                // 走行中のフリングを止める (タップで即停止)
                flingVelocityRows = 0f
                removeCallbacks(flingRunnable)
                scrollAccumDy = 0f
                mouseWheelAccumDy = 0f
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (scaleDetector.isInProgress) return false
                val sess = session ?: return false
                val m = sess.cellMetrics.value
                if (m.lineHeight <= 0f) return false
                // マウスモード中の「上方向フリング (=次へ進む方向)」は scrollback==0 の
                // ときだけ no-op (もう過去側に戻る余地が無く、wheel イベントは onScroll で
                // 送り済みなので空回りさせない)。scrollback > 0 のときは scrollback fling
                // で最新側へ慣性スクロール、下方向フリングも従来通り scrollback 慣性。
                if (sess.emulator.mouseEnabled
                    && velocityY < 0f
                    && sess.scrollOffset.value == 0
                ) return true
                // velocityY > 0 (指を下へ振る) = 過去へ。/30 で 1 フレームあたり行数へ。
                flingVelocityRows = velocityY / m.lineHeight / 30f
                removeCallbacks(flingRunnable)
                post(flingRunnable)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val sess = session ?: return
                // 慣性スクロール中に長押し選択へ移る場合は止める
                flingVelocityRows = 0f
                removeCallbacks(flingRunnable)
                val cell = pixelToAbsCell(e.x, e.y) ?: return
                selectionAnchorRow = cell.first
                selectionAnchorCol = cell.second
                touchMode = TouchMode.SELECTING
                sess.setSelection(
                    TerminalSelection.of(cell.first, cell.second, cell.first, cell.second)
                )
                lastTouchX = e.x
                lastTouchY = e.y
                showMagnifierAt(e.x, e.y)
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
                // 選択中のドラッグは onTouchEvent 側で直接処理するためここには来ない。
                val m = sess.cellMetrics.value
                if (m.lineHeight <= 0f) return false
                // マウスレポーティング有効時、指を上方向 (distanceY > 0 = TUI で次へ進めたい)
                // **かつ scrollback の最下端 (scrollOffset == 0)** のときだけ wheel-down を
                // PTY へ送る。scrollback で過去ログを見ている途中 (scrollOffset > 0) は、
                // 上方向を scrollback の「最新側へ戻る」操作として吸収する。これをしないと
                // wheel 送信の writeBytes が scrollback を 0 にリセットして「いきなり最下端
                // へジャンプ」する違和感の原因になる (TerminalSession.writeBytes 参照)。
                // 下方向 (distanceY < 0 = 過去を見たい) は多くの読み物系 TUI が wheel-up を
                // 端末 scrollback に任せる設計なので、常に scrollback 操作にフォールバック。
                val atBottom = sess.scrollOffset.value == 0
                if (sess.emulator.mouseEnabled && distanceY > 0f && atBottom) {
                    sendMouseWheelFromSwipe(e2.x, e2.y, distanceY, sess)
                    return true
                }
                // 通常のドラッグ / scrollback で過去を見ている間 / マウスモードでも下方向は
                // ターミナルをスクロール。
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
                val wasFocused = isFocused
                if (!isFocused) requestFocus()
                // 既にフォーカス済みのタップが URL / OSC8 リンク上なら開く。
                // フォーカス目的の初回タップでは開かない (誤爆防止)。
                if (wasFocused) {
                    val cell = pixelToAbsCell(e.x, e.y)
                    if (cell != null) {
                        val url = UrlFinder.urlAt(sess.emulator.buffer, cell.first, cell.second)
                        if (url != null && openUri(url)) return true
                    }
                }
                if (imeEnabled) requestKeyboard()
                performClick()
                return true
            }
        }
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // quick scale (1本指ダブルタップ+ドラッグでズーム) は OFF。有効だと
        // ScaleGestureDetector が単指 DOWN を内部の double-tap 監視に取り込み、
        // GestureDetector.onLongPress が間欠的に発火しなくなる (2本指ピンチ後に
        // 直るのはその状態機械がリセットされるため)。本アプリは 2 本指ピンチのみ
        // 使うので影響なし。
        scaleDetector.isQuickScaleEnabled = false
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

    /** OS ソフトキーボードを明示的に隠す (設定シートを開くときなど)。 */
    fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(windowToken, 0)
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

        // 選択中 (長押し選択 / ハンドル調整) は detectors を通さず直接処理する。
        // GestureDetector は onLongPress 後 onScroll を送らない (mInLongPress) ため、
        // SELECTING のドラッグ追従もここで生 MOTION_MOVE から処理する。
        if (touchMode != TouchMode.NONE) {
            when (action) {
                MotionEvent.ACTION_MOVE -> {
                    applySelectionAt(event.x, event.y)
                    updateEdgeAutoScroll(event.x, event.y)
                    showMagnifierAt(event.x, event.y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    endSelectionGesture()
                }
            }
            return true
        }

        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }

    /** 現在の touchMode に応じて (x,y) のセルへ選択を反映する。 */
    private fun applySelectionAt(x: Float, y: Float) {
        val sess = session ?: return
        lastTouchX = x
        lastTouchY = y
        val cell = pixelToAbsCell(x, y) ?: return
        val sel = sess.selection.value
        val newSel = when (touchMode) {
            TouchMode.SELECTING ->
                TerminalSelection.of(selectionAnchorRow, selectionAnchorCol, cell.first, cell.second)
            TouchMode.ADJUSTING_START ->
                if (sel != null) TerminalSelection.of(cell.first, cell.second, sel.endAbsRow, sel.endCol) else null
            TouchMode.ADJUSTING_END ->
                if (sel != null) TerminalSelection.of(sel.startAbsRow, sel.startCol, cell.first, cell.second) else null
            TouchMode.NONE -> null
        } ?: return
        sess.setSelection(newSel)
    }

    /**
     * 指が画面上下端付近にあるとき、距離に応じた速度で自動スクロールして選択を伸ばす。
     * 画面外へ引っ張るほど速くなる (端内=1行, 画面外近=2, 中=3, 遠=5 行/tick)。
     * 大量スクロールバックでも端の外へ引っ張れば一気に遡れる。
     */
    private fun updateEdgeAutoScroll(x: Float, y: Float) {
        lastTouchX = x
        lastTouchY = y
        val m = session?.cellMetrics?.value ?: return
        val edge = (m.lineHeight * 2.5f).coerceAtLeast(80f)
        val (dir, rows) = when {
            y < 0 -> +1 to speedForDistance(-y, m.lineHeight)
            y < edge -> +1 to 1
            y > height -> -1 to speedForDistance(y - height, m.lineHeight)
            y > height - edge -> -1 to 1
            else -> 0 to 1
        }
        autoScrollRowsPerTick = rows
        if (dir != autoScrollDir) {
            autoScrollDir = dir
            removeCallbacks(autoScrollRunnable)
            if (dir != 0) post(autoScrollRunnable)
        }
    }

    /** 画面外へのはみ出し距離 [dist] (px) を 1tick あたりの行数へ。 */
    private fun speedForDistance(dist: Float, lineHeight: Float): Int = when {
        dist < lineHeight * 2 -> 2
        dist < lineHeight * 5 -> 3
        else -> 5
    }

    /** 選択ジェスチャ終了: 自動スクロール停止 + 拡大鏡を消す (選択結果は保持)。 */
    private fun endSelectionGesture() {
        touchMode = TouchMode.NONE
        autoScrollDir = 0
        removeCallbacks(autoScrollRunnable)
        dismissMagnifier()
    }

    // ---- 拡大鏡 (Magnifier) -------------------------------------------------

    /**
     * 端末を描画している View (Compose の AndroidComposeView) を拡大対象にする。
     * この View 自身は透明オーバーレイで文字を描かないため、親方向へ辿って
     * 実際に端末を描く View を探す。
     */
    private fun terminalDrawingView(): View {
        var p = parent
        while (p is View) {
            if (p.javaClass.simpleName.contains("AndroidComposeView")) return p
            p = p.parent
        }
        return rootView
    }

    private fun showMagnifierAt(x: Float, y: Float) {
        runCatching {
            val drawView = terminalDrawingView()
            val mag = magnifier ?: android.widget.Magnifier(drawView).also { magnifier = it }
            // この View 座標 → 拡大対象 View 座標へ変換
            val mine = IntArray(2); getLocationInWindow(mine)
            val theirs = IntArray(2); drawView.getLocationInWindow(theirs)
            val sx = x + (mine[0] - theirs[0])
            val sy = y + (mine[1] - theirs[1])
            // 拡大鏡ウィンドウを指の真上へ固定オフセットで出す。2 引数版は OEM 既定の
            // 位置任せで「バラバラ」になり、指で隠れることがあるため 4 引数版(API 29+)で
            // 拡大鏡中心を明示する。表示元(sx,sy)はそのまま、ウィンドウだけ上へずらす。
            val density = resources.displayMetrics.density
            val lift = mag.height / 2f + 24f * density
            val magCenterY = (sy - lift).coerceAtLeast(mag.height / 2f)
            mag.show(sx, sy, sx, magCenterY)
        }
    }

    private fun dismissMagnifier() {
        magnifier?.dismiss()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // 画面左右端のシステム「戻る」ジェスチャ領域を除外し、端ぴったりでも
        // 長押し選択を開始できるようにする (API 29+)。端の縦帯のみ対象。
        val strip = (40 * resources.displayMetrics.density).toInt()
        systemGestureExclusionRects = listOf(
            android.graphics.Rect(0, 0, strip, height),
            android.graphics.Rect(width - strip, 0, width, height)
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(autoScrollRunnable)
        removeCallbacks(flingRunnable)
        magnifier?.dismiss()
        magnifier = null
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private enum class Handle { START, END }

    /**
     * 選択端ハンドルの当たり判定。掴みやすいよう判定半径を広く取り (≒ 3 セル分)、
     * 「ハンドルそのもの」でなく **末端付近をドラッグ** すれば調整に入れるようにする。
     * 両端が範囲内なら近い方を選ぶ。左端 (col 0) でも掴めるよう x はクランプしない。
     */
    private fun hitTestHandle(x: Float, y: Float): Handle? {
        val sess = session ?: return null
        val sel = sess.selection.value ?: return null
        val m = sess.cellMetrics.value
        if (m.cellW <= 0f || m.lineHeight <= 0f) return null

        val topAbsRow = currentTopAbsRow(sess, m) ?: return null
        // 半径は行高 2.2 倍と 96px の大きい方。指の腹で確実に掴めるサイズ。
        val radius = (m.lineHeight * 2.2f).coerceAtLeast(96f)
        val r2 = radius * radius

        val hPad = m.horizontalPaddingPx
        var startD = Float.MAX_VALUE
        val startCanvasRow = sel.startAbsRow - topAbsRow
        if (startCanvasRow in 0 until m.canvasRows) {
            val sx = sel.startCol * m.cellW + hPad
            val sy = (startCanvasRow + 1) * m.lineHeight
            startD = (x - sx) * (x - sx) + (y - sy) * (y - sy)
        }
        var endD = Float.MAX_VALUE
        val endCanvasRow = sel.endAbsRow - topAbsRow
        if (endCanvasRow in 0 until m.canvasRows) {
            val ex = (sel.endCol + 1) * m.cellW + hPad
            val ey = (endCanvasRow + 1) * m.lineHeight
            endD = (x - ex) * (x - ex) + (y - ey) * (y - ey)
        }
        return when {
            startD > r2 && endD > r2 -> null
            startD <= endD -> Handle.START
            else -> Handle.END
        }
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
    /** URL / OSC8 リンクを外部アプリ (ブラウザ等) で開く。開けたら true。 */
    private fun openUri(raw: String): Boolean {
        val uri = runCatching { android.net.Uri.parse(raw) }.getOrNull() ?: return false
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    /**
     * マウスレポーティング有効時の「次へ進める方向のスワイプ」を SGR ホイールイベント
     * (wheel-down, button 65) へ変換して PTY へ送る。
     *
     * GestureDetector.onScroll の distanceY は「直前イベントから上方向に動いた量」で、
     * distanceY > 0 = 指が上に動いた = ユーザーから見て「次へ進めたい」操作。呼び出し元で
     * 正方向だけ通している前提。逆方向 (指を下) は呼び出し元で scrollback 操作にフォール
     * バックさせる (多くの読み物系 TUI が wheel-up を「端末 scrollback に任せる」設計で
     * 無視するため、ここで wheel-up を送っても何も起きない)。
     *
     * [MOUSE_WHEEL_STEP_PX] ぶんスワイプするごとに 1 ノッチ送る (長いスワイプ = 多行送り)。
     * 端数は [mouseWheelAccumDy] に持ち越す。
     */
    private fun sendMouseWheelFromSwipe(
        x: Float,
        y: Float,
        distanceY: Float,
        sess: TerminalSession
    ) {
        if (distanceY <= 0f) return
        mouseWheelAccumDy += distanceY
        val notches = (mouseWheelAccumDy / MOUSE_WHEEL_STEP_PX).toInt()
        if (notches <= 0) return
        // 座標は画面内セルに丸める。pixelToAbsCell は absolute row を返すので
        // 画面 row へ落とす (scrollback 表示中でも画面内 0..rows-1 に収まるよう coerce)。
        val emu = sess.emulator
        val buf = emu.buffer
        val cell = pixelToAbsCell(x, y)
        val screenRow0 = if (cell != null) {
            (cell.first - buf.scrollbackSize).coerceIn(0, (buf.rows - 1).coerceAtLeast(0))
        } else 0
        val col0 = cell?.second?.coerceIn(0, (buf.columns - 1).coerceAtLeast(0)) ?: 0
        repeat(notches) {
            val bytes = emu.encodeMouseEvent(
                button = com.zerotoship.z2term.emulator.TerminalEmulator.MOUSE_BTN_WHEEL_DOWN,
                col0 = col0, row0 = screenRow0, press = true
            ) ?: return@repeat
            sess.writeBytes(bytes)
        }
        mouseWheelAccumDy -= notches * MOUSE_WHEEL_STEP_PX
    }

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
        // 描画は hPad 分右へずらしているので、タッチ x から余白を引いて列へ変換。
        val canvasCol = ((x - m.horizontalPaddingPx) / m.cellW).toInt().coerceIn(0, m.canvasCols - 1)
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
        val raw = editable.toString()
        editable.clear()
        val sess = session ?: return
        // sticky Ctrl が ON のときは、OS IME の確定文字を制御コードに変換して送る。
        // (システムキーボードでも内蔵 CTRL ボタン → c で Ctrl+C が効くようにする。
        //  GUI 側 GuiInputConnection.sendAsKeysyms と同じワンショット方式。)
        if (targetView.ctrlSticky) {
            val out = ArrayList<Byte>(raw.length)
            for (ch in raw) {
                val cb = AndroidKeyMapper.controlByteFor(ch)
                if (cb != null) {
                    out.add(cb)
                } else {
                    val s = if (ch == '\n') "\r" else ch.toString()
                    s.toByteArray(Charsets.UTF_8).forEach { out.add(it) }
                }
            }
            sess.writeBytes(out.toByteArray())
            targetView.onCtrlConsumed?.invoke()
            return
        }
        sess.writeBytes(raw.replace('\n', '\r').toByteArray(Charsets.UTF_8))
    }
}
