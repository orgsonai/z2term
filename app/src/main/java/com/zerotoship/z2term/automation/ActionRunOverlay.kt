package com.zerotoship.z2term.automation

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.ScreenGravity
import kotlin.math.abs

/**
 * 操作マクロの実行中だけ、ほかのアプリの上に出す停止ボタンと進み具合 (1.0.95、issue #2)。
 *
 * マクロは操作先のアプリを前面に出すので、z2term の停止ボタンは押せない。通知欄の「停止」は
 * 通知欄を下ろさないと見えない。そこで z2term のユーザー補助サービスの重ね窓に小さな帯を出す
 * (「他のアプリの上に表示」の許可は要らない)。既定 OFF (操作マクロのタブで ON にする)。
 *
 * - 帯は指で動かせる。置いた場所は覚えておき、次の実行も同じ場所に出す。
 * - ■ が停止、‖ が一時停止 (一時停止中は ▶ で再開)。帯のほかの場所は動かすための取っ手で、押しても何も起きない。
 * - 前面の窓の判定 ([com.zerotoship.z2term.edge.AndroidActions] の focusedTarget) は
 *   TYPE_APPLICATION の窓だけを見るので、この重ね窓は操作先・要素の検索に混ざらない。
 *   フォーカスも取らない (FLAG_NOT_FOCUSABLE)。
 *
 * ⚠ **マクロの指がこの帯に当たると、操作先ではなく帯が受け取る。** Android は指を置いた位置の
 * いちばん上の窓へタッチを渡すため。そこで座標の操作 (タップ・スワイプ・オートスクロール) を送る前に、
 * 指が通る範囲が帯に重なるなら [clear] で帯を隠し (触れない窓にして) から送る。窓の変更が入力に
 * 反映されるまで [SETTLE_MS] 待つ。送り終えたら [RESTORE_MS] 後に戻す (続けて同じ場所を押すマクロで
 * 毎回待たせないため)。重ならない操作は待たせない。それでも合成の入力が届いたときは何もしない
 * (自分で自分を止めない)。
 */
@SuppressLint("StaticFieldLeak") // Holds the accessibility service only while its window is shown.
internal object ActionRunOverlay {
    private const val PREFS = "action_run_overlay"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"
    /** 触れない窓へ変えてから入力に反映されるまで (60 Hz で数フレーム分)。 */
    private const val SETTLE_MS = 100L
    /** 帯を隠した操作が終わってから戻すまで。 */
    private const val RESTORE_MS = 700L

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean(KEY_ENABLED, value).apply() }

    private var service: AccessibilityService? = null
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var progress: TextView? = null
    private var nameView: TextView? = null
    private var toggleView: TextView? = null
    private var runName = ""
    private var hidden = false
    private var cancelRestore: (() -> Unit)? = null

    /** 実行が始まったとき (メインスレッド)。OFF・ユーザー補助なしなら何もしない。 */
    @SuppressLint("ClickableViewAccessibility")
    fun show(host: AccessibilityService?, name: String, stop: () -> Unit, toggle: () -> Unit) {
        dismiss()
        if (host == null || !enabled(host)) return
        fun dp(value: Int) = ActionUi.dp(host, value)
        val wm = host.getSystemService(WindowManager::class.java)
        val nameView = ActionUi.label(host, name, 11f, ActionUi.mutedColor).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(160)
        }
        val progressView = ActionUi.label(host, "", 13f, ActionUi.textColor, ActionUi.medium).apply {
            // 幅を先に取っておく: 文字が増えて帯が伸びると、右端に置いた帯が画面からはみ出す。
            maxLines = 1; maxWidth = dp(160); minWidth = dp(120)
        }
        // 押すのは指 (帯の onTouch) で判定する。読み上げからは各ボタンを直接押せる。
        fun control(glyph: String, color: Int, label: String, action: () -> Unit) = ActionUi.label(host, glyph, 18f, color).apply {
            gravity = Gravity.CENTER
            minWidth = dp(48); minHeight = dp(48)
            contentDescription = label
            // performClick は押せない状態でも呼び出しを通すので、ここで弾く。
            setOnClickListener { if (isEnabled) action() }
        }
        val toggleText = control("‖", ActionUi.textColor, host.getString(R.string.action_macro_pause), toggle)
        val stopText = control("■", ActionUi.dangerColor, host.getString(R.string.action_macro_stop), stop)
        // 子のボタンにタッチを渡さず、帯がすべて受けて「動かす／押す」を決める。
        val bar = object : LinearLayout(host) {
            override fun onInterceptTouchEvent(ev: MotionEvent?) = true
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(ActionUi.cardColor); setStroke(dp(1), ActionUi.lineColor); cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(4), dp(4), dp(4))
            addView(LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                addView(nameView); addView(progressView)
            }, LinearLayout.LayoutParams(-2, -2))
            addView(toggleText, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4) })
            addView(stopText, LinearLayout.LayoutParams(-2, -2))
        }
        val saved = prefs(host)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = ScreenGravity.TOP_LEFT
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            x = saved.getInt(KEY_X, -1); y = saved.getInt(KEY_Y, -1)
        }
        val slop = ViewConfiguration.get(host).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var dragging = false; var tracking = false
        bar.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // マクロ自身の指 (合成の入力) では動かさず、止めもしない。
                    tracking = !synthetic(event)
                    downX = event.rawX; downY = event.rawY; startX = lp.x; startY = lp.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> if (tracking) {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt()
                        clamp(host, view, lp)
                        runCatching { wm.updateViewLayout(view, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> if (tracking) {
                    tracking = false
                    if (dragging) prefs(host).edit().putInt(KEY_X, lp.x).putInt(KEY_Y, lp.y).apply()
                    else if (event.x >= stopText.left) stopText.performClick()
                    else if (event.x >= toggleText.left - dp(4)) toggleText.performClick()
                }
                MotionEvent.ACTION_CANCEL -> tracking = false
            }
            true
        }
        if (runCatching { wm.addView(bar, lp) }.isFailure) return
        service = host; root = bar; params = lp; progress = progressView
        this.nameView = nameView; toggleView = toggleText; runName = name
        // 初回 (まだ動かしていない) は右上。大きさが決まってから置く。前回の位置も画面の中に収める。
        bar.post {
            if (root !== bar) return@post
            if (lp.x < 0 || lp.y < 0) { lp.x = Int.MAX_VALUE; lp.y = dp(96) }
            clamp(host, bar, lp)
            runCatching { wm.updateViewLayout(bar, lp) }
        }
    }

    /** 進み具合を書き換える。[loops] は外側から順に (何回目, 回数 または 無限なら null)。 */
    fun update(loops: List<Pair<Int, Int?>>, step: Int, size: Int) {
        val view = progress ?: return
        view.text = describe(view.context, loops, step, size)
    }

    /** 「2/5回目 · 3/10回目 · 手順 2/4」。帯と通知の本文で同じ書き方にする。 */
    fun describe(context: Context, loops: List<Pair<Int, Int?>>, step: Int, size: Int): String {
        val parts = loops.map { (pass, count) ->
            if (count == null) context.getString(R.string.action_overlay_loop_endless, pass)
            else context.getString(R.string.action_overlay_loop, pass, count)
        }.toMutableList()
        if (size > 0) parts += context.getString(R.string.action_overlay_step, step, size)
        return parts.joinToString(" · ")
    }

    /** 一時停止中は ‖ を ▶ に変え、名前の前に「一時停止中」と出す。再開の画面待ちの間は ▶ を押せない。 */
    fun setPaused(paused: Boolean, resuming: Boolean) {
        val toggle = toggleView ?: return
        val context = toggle.context
        toggle.text = if (paused) "▶" else "‖"
        toggle.contentDescription = context.getString(if (paused) R.string.action_macro_resume else R.string.action_macro_pause)
        toggle.isEnabled = !resuming
        toggle.alpha = if (resuming) 0.4f else 1f
        nameView?.text = when {
            resuming -> context.getString(R.string.action_macro_resuming, runName)
            paused -> context.getString(R.string.action_macro_paused, runName)
            else -> runName
        }
    }

    /** 実行が終わったとき。 */
    fun dismiss() {
        cancelRestore?.invoke(); cancelRestore = null
        val view = root
        if (view != null) runCatching { service?.getSystemService(WindowManager::class.java)?.removeViewImmediate(view) }
        service = null; root = null; params = null; progress = null; nameView = null; toggleView = null; hidden = false
    }

    /**
     * 座標の操作を送る前に呼ぶ。指が通る範囲 ([hits] が帯の画面上の四角で true を返す) が帯に重なるなら、
     * 帯を隠して入力に反映されるのを待ってから [action] を送る。[action] には終わりの知らせを渡す。
     * 戻り値は取り消し (エンジンの cancelStep)。
     */
    fun clear(hits: (Rect) -> Boolean, schedule: (Long, () -> Unit) -> (() -> Unit),
        done: (String?) -> Unit, action: ((String?) -> Unit) -> (() -> Unit)?): (() -> Unit)? {
        val view = root
        val box = view?.takeIf { it.isAttachedToWindow }?.let { v ->
            val at = IntArray(2).also { v.getLocationOnScreen(it) }
            // 指先の位置そのものより少し広めに避ける。
            val margin = ActionUi.dp(v.context, 8)
            Rect(at[0] - margin, at[1] - margin, at[0] + v.width + margin, at[1] + v.height + margin)
        }
        // 重ならない操作は待たせない (隠している最中なら、戻す予定もそのまま)。
        if (box == null || !hits(box)) return action(done)
        cancelRestore?.invoke(); cancelRestore = null
        val finished: (String?) -> Unit = { error -> restoreLater(schedule); done(error) }
        if (hidden) return action(finished)
        hide()
        var cancelled = false
        var inner: (() -> Unit)? = null
        val cancelSettle = schedule(SETTLE_MS) {
            if (cancelled) return@schedule
            inner = try { action(finished) } catch (e: Exception) { finished(e.message ?: "Action failed"); null }
        }
        return { cancelled = true; cancelSettle(); inner?.invoke(); restoreLater(schedule) }
    }

    private fun hide() {
        val view = root ?: return
        val lp = params ?: return
        hidden = true
        view.visibility = View.INVISIBLE
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { service?.getSystemService(WindowManager::class.java)?.updateViewLayout(view, lp) }
    }

    private fun restoreLater(schedule: (Long, () -> Unit) -> (() -> Unit)) {
        cancelRestore?.invoke()
        cancelRestore = schedule(RESTORE_MS) {
            cancelRestore = null
            val view = root ?: return@schedule
            val lp = params ?: return@schedule
            hidden = false
            view.visibility = View.VISIBLE
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { service?.getSystemService(WindowManager::class.java)?.updateViewLayout(view, lp) }
        }
    }

    /** 帯がはみ出さないよう画面の中へ収める。 */
    private fun clamp(context: Context, view: View, lp: WindowManager.LayoutParams) {
        val screen = ActionRuntime.screen(context)
        lp.x = lp.x.coerceIn(0, maxOf(0, screen.width - view.width))
        lp.y = lp.y.coerceIn(0, maxOf(0, screen.height - view.height))
    }

    private fun synthetic(event: MotionEvent) = event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD &&
        event.getToolType(0) == MotionEvent.TOOL_TYPE_UNKNOWN
}
