package com.zerotoship.z2term.automation

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.ScreenGravity
import kotlin.math.abs

/**
 * ほかのアプリの上に出す小さな帯 (1.0.95、issue #2)。3 つの使い方がある。
 *
 * - **操作マクロの実行中** ([Owner.MACRO]): 停止ボタンと進み具合。既定 OFF (操作マクロのタブで ON にする)。
 *   ■ が停止、‖ が一時停止 (一時停止中は ▶ で再開)。
 * - **AI の操作中** ([Owner.AI]、1.0.102): z2-ui の常駐通知が出ている間、同じ ‖ と ■ を出す。AI が続けて
 *   操作していると通知欄まで指が届かないため。AI の設定で切れる (既定 ON)。
 * - **マクロを選んで実行** ([Owner.PICKER]、1.0.102): `z2-action pick` で出す。名前を選んで ▶ で実行し、
 *   実行が始まったら停止の帯に替わる。エッジパネルにはアイコンを 1 つ置けば足りる。
 *
 * マクロは操作先のアプリを前面に出すので、z2term の停止ボタンは押せない。通知欄の「停止」は
 * 通知欄を下ろさないと見えない。そこで z2term のユーザー補助サービスの重ね窓に出す
 * (「他のアプリの上に表示」の許可は要らない)。
 *
 * - 帯は指で動かせる。置いた場所は覚えておき、次も同じ場所に出す (3 つの使い方で同じ場所)。
 * - 帯のボタン以外の場所は動かすための取っ手で、押しても何も起きない。
 * - 前面の窓の判定 ([com.zerotoship.z2term.edge.AndroidActions] の focusedTarget) は
 *   TYPE_APPLICATION の窓だけを見るので、この重ね窓は操作先・要素の検索に混ざらない。
 *   フォーカスも取らない (FLAG_NOT_FOCUSABLE)。
 * - ⚠ **合成の入力 (マクロや AI の指) では何も押さない。** AI が自分で再開・実行を押せないようにするため。
 *
 * ⚠ **マクロや AI の指がこの帯に当たると、操作先ではなく帯が受け取る。** Android は指を置いた位置の
 * いちばん上の窓へタッチを渡すため。そこで座標の操作を送る前に、指が通る範囲が帯に重なるなら
 * 帯を触れない窓にして (指は下のアプリへ素通りする) から送る ([clear] はマクロ、[stepAside] は z2-ui)。
 * 帯は消さずに薄くする ([DIM_ALPHA]、1.0.113): 消すと進み具合が読めなくなるため。
 * 窓の変更が入力に反映されるまで [SETTLE_MS] 待つ。送り終えたら [RESTORE_MS] 後に戻す (続けて同じ場所を
 * 押すときに毎回待たせないため)。重ならない操作は待たせない。
 */
@SuppressLint("StaticFieldLeak") // Holds the accessibility service only while its window is shown.
internal object ActionRunOverlay {
    enum class Owner { MACRO, AI, PICKER }

    private const val PREFS = "action_run_overlay"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"
    private const val KEY_PICKED = "picked"
    /** 触れない窓へ変えてから入力に反映されるまで (60 Hz で数フレーム分)。 */
    const val SETTLE_MS = 100L
    /** 帯を隠した操作が終わってから戻すまで。 */
    private const val RESTORE_MS = 700L
    /** 指が下を通る間の帯の濃さ。読めるが、押せないと分かる薄さ。 */
    private const val DIM_ALPHA = 0.35f

    private val main = Handler(Looper.getMainLooper())
    private fun post(ms: Long, block: () -> Unit): () -> Unit {
        val task = Runnable { block() }
        main.postDelayed(task, ms)
        return { main.removeCallbacks(task) }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean(KEY_ENABLED, value).apply() }

    private var service: AccessibilityService? = null
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var progress: TextView? = null
    private var nameView: TextView? = null
    private var toggleView: TextView? = null
    private var runName = ""
    private var hidden = false
    /** z2-ui の指が帯の下を通っている数。0 になるまで戻さない。 */
    private var held = 0
    private var cancelRestore: (() -> Unit)? = null
    /** いま出している帯の使い方。出していなければ null。メインスレッドから読む。 */
    var owner: Owner? = null
        private set

    /**
     * 停止の帯を出す (メインスレッド)。マクロは実行が始まったとき、AI は常駐通知を出したとき。
     * 設定が OFF なら [force] のときだけ出す (選んで実行した実行は、選んだ帯から続けて出す)。
     * ユーザー補助なしなら何もしない。出せたら true。
     */
    fun show(host: AccessibilityService?, name: String, stop: () -> Unit, toggle: () -> Unit,
        force: Boolean = false, owner: Owner = Owner.MACRO, stopLabel: String? = null): Boolean {
        dismiss()
        if (host == null || !(force || enabled(host))) return false
        fun dp(value: Int) = ActionUi.dp(host, value)
        val nameView = ActionUi.label(host, name, 11f, ActionUi.mutedColor).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(160)
        }
        val progressView = ActionUi.label(host, "", 13f, ActionUi.textColor, ActionUi.medium).apply {
            // ⚠ 進み具合は削らない (1.0.112)。160dp で切ると「1/6回目 · 手順 2/4」でも … になって回数が読めなかった。
            // 帯が伸びても画面からはみ出さないよう、大きさが変わるたびに [attach] が画面の中へ収め直す。
            // 上限は画面の幅からボタンと余白を引いた分だけ。
            // 最小の幅は、手順ごとに帯の幅が変わってちらつかないため。
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; minWidth = dp(120)
            maxWidth = ActionRuntime.screen(host).width - dp(12 + 4 + 48 + 48 + 4 + 16)
        }
        val toggleText = control(host, "‖", ActionUi.textColor, host.getString(R.string.action_macro_pause), toggle)
        val stopText = control(host, "■", ActionUi.dangerColor, stopLabel ?: host.getString(R.string.action_macro_stop), stop)
        val bar = handle(host).apply {
            setPadding(dp(12), dp(4), dp(4), dp(4))
            addView(LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                addView(nameView); addView(progressView)
            }, LinearLayout.LayoutParams(-2, -2))
            addView(toggleText, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4) })
            addView(stopText, LinearLayout.LayoutParams(-2, -2))
        }
        val shown = attach(host, bar, bar, owner, nameView, progressView) { x ->
            if (x >= stopText.left) stopText.performClick()
            else if (x >= toggleText.left - dp(4)) toggleText.performClick()
        }
        if (!shown) return false
        progress = progressView; this.nameView = nameView; toggleView = toggleText; runName = name
        return true
    }

    /**
     * マクロを選んで実行する帯 (`z2-action pick`、メインスレッド)。「名前 ▾」で一覧を開いて選び、▶ で実行、
     * × で閉じる。最後に選んだマクロを覚えておく。[run] が true を返したら (実行が始まったら) 閉じる。
     */
    fun showPicker(host: AccessibilityService, names: List<String>, run: (String) -> Boolean) {
        dismiss()
        require(names.isNotEmpty())
        fun dp(value: Int) = ActionUi.dp(host, value)
        var picked = prefs(host).getString(KEY_PICKED, null)?.takeIf { it in names } ?: names.first()
        val title = ActionUi.label(host, "", 11f, ActionUi.mutedColor).apply {
            text = host.getString(R.string.action_chooser_title)
        }
        val choice = ActionUi.label(host, "", 13f, ActionUi.textColor, ActionUi.medium).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(160); minWidth = dp(120)
        }
        val list = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(host).apply { visibility = View.GONE; isVerticalScrollBarEnabled = true; addView(list) }
        fun render() {
            choice.text = host.getString(R.string.action_chooser_choice, picked)
            choice.contentDescription = host.getString(R.string.action_chooser_open, picked)
            for (index in 0 until list.childCount) {
                val row = list.getChildAt(index) as TextView
                val selected = row.tag == picked
                row.setTextColor(if (selected) ActionUi.accentColor else ActionUi.textColor)
                row.isSelected = selected
            }
        }
        val runText = control(host, "▶", ActionUi.accentColor, host.getString(R.string.action_chooser_run)) {
            if (run(picked)) dismiss()
        }
        val closeText = control(host, "×", ActionUi.mutedColor, host.getString(R.string.action_chooser_close)) { dismiss() }
        val header = handle(host).apply {
            setPadding(dp(12), dp(4), dp(4), dp(4))
            addView(LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                addView(title); addView(choice)
            }, LinearLayout.LayoutParams(-2, -2))
            addView(runText, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4) })
            addView(closeText, LinearLayout.LayoutParams(-2, -2))
        }
        val window = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(ActionUi.cardColor); setStroke(dp(1), ActionUi.lineColor); cornerRadius = dp(8).toFloat()
            }
            addView(header, LinearLayout.LayoutParams(-2, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, minOf(names.size * dp(44), dp(264))))
        }
        // 一覧の開け閉めで窓の大きさが変わる。画面の下からはみ出さないよう収め直す。
        fun toggleList() {
            scroll.visibility = if (scroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refit()
        }
        choice.setOnClickListener { toggleList() }
        names.forEach { name ->
            list.addView(ActionUi.label(host, name, 13f).apply {
                tag = name
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(44)
                setPadding(dp(12), 0, dp(12), 0)
                background = ActionUi.pressable(host, 0)
                setOnClickListener {
                    picked = name
                    prefs(host).edit().putString(KEY_PICKED, name).apply()
                    render()
                    toggleList()
                }
                genuineTaps(this)
            }, LinearLayout.LayoutParams(-1, -2))
        }
        render()
        attach(host, window, header, Owner.PICKER, choice) { x ->
            if (x >= closeText.left) closeText.performClick()
            else if (x >= runText.left - dp(4)) runText.performClick()
            else choice.performClick()
        }
    }

    /** 押すのは指 (帯の onTouch) で判定する。読み上げからは各ボタンを直接押せる。 */
    private fun control(host: Context, glyph: String, color: Int, label: String, action: () -> Unit) =
        ActionUi.label(host, glyph, 18f, color).apply {
            gravity = Gravity.CENTER
            minWidth = ActionUi.dp(host, 48); minHeight = ActionUi.dp(host, 48)
            contentDescription = label
            // performClick は押せない状態でも呼び出しを通すので、ここで弾く。
            setOnClickListener { if (isEnabled) action() }
        }

    /** 子のボタンにタッチを渡さず、すべて受けて「動かす／押す」を決める取っ手。 */
    private fun handle(host: Context) = object : LinearLayout(host) {
        override fun onInterceptTouchEvent(ev: MotionEvent?) = true
    }.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            setColor(ActionUi.cardColor); setStroke(ActionUi.dp(host, 1), ActionUi.lineColor)
            cornerRadius = ActionUi.dp(host, 8).toFloat()
        }
    }

    /** 一覧の行は本物の指で押したときだけ選ぶ。一覧のスクロールは ScrollView が取る (取ったら行には CANCEL が届く)。 */
    @SuppressLint("ClickableViewAccessibility")
    private fun genuineTaps(view: View) {
        var tracking = false
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { tracking = !synthetic(event); v.isPressed = tracking }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (tracking && event.x in 0f..v.width.toFloat() && event.y in 0f..v.height.toFloat()) v.performClick()
                    tracking = false
                }
                MotionEvent.ACTION_CANCEL -> { tracking = false; v.isPressed = false }
            }
            true
        }
    }

    /**
     * [window] を重ね窓として出し、[handle] を指で動かせるようにする。動かさずに離したら [tap] に
     * 取っ手の中の x を渡す。置いた場所は覚えておく。[texts] の文字が変わったら置き直す ([refit])。
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attach(host: AccessibilityService, window: View, handle: View, owner: Owner,
        vararg texts: TextView, tap: (Float) -> Unit): Boolean {
        val wm = host.getSystemService(WindowManager::class.java)
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
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // マクロや AI の指 (合成の入力) では動かさず、押しもしない。
                    tracking = !synthetic(event)
                    downX = event.rawX; downY = event.rawY; startX = lp.x; startY = lp.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> if (tracking) {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt()
                        clamp(host, window, lp)
                        runCatching { wm.updateViewLayout(window, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> if (tracking) {
                    tracking = false
                    if (dragging) prefs(host).edit().putInt(KEY_X, lp.x).putInt(KEY_Y, lp.y).apply()
                    else tap(event.x)
                }
                MotionEvent.ACTION_CANCEL -> tracking = false
            }
            true
        }
        // 初回 (まだ動かしていない) は右上。前回の位置も画面の中に収める。大きさも出す前に決める ([clamp])。
        if (lp.x < 0 || lp.y < 0) { lp.x = Int.MAX_VALUE; lp.y = ActionUi.dp(host, 96) }
        clamp(host, window, lp)
        if (runCatching { wm.addView(window, lp) }.isFailure) return false
        service = host; root = window; params = lp; this.owner = owner
        // 文字が変わったら (進み具合・一時停止中・選んだ名前)、伸びた後の大きさで画面の中へ置き直す。
        texts.forEach { it.doAfterTextChanged { refit() } }
        return true
    }

    /**
     * 中身の大きさが変わったとき、画面からはみ出さない位置へ先に動かす。
     * ⚠ 窓が伸びてから収め直すのでは間に合わない (1.0.113)。この窓は画面の端で切られるので、右端に置いた帯は
     * 右へ伸びられず、切られた幅のまま文字が「…」になる (幅が変わらないので、伸びたことにも気付けない)。
     * そこで伸びた後の幅をここで測り、その幅が収まる位置へ動かしてから描かせる。
     */
    private fun refit() {
        val view = root ?: return
        val lp = params ?: return
        val host = service ?: return
        val before = listOf(lp.x, lp.y, lp.width, lp.height)
        clamp(host, view, lp)
        if (listOf(lp.x, lp.y, lp.width, lp.height) != before) runCatching { host.getSystemService(WindowManager::class.java).updateViewLayout(view, lp) }
    }

    /** 進み具合を書き換える。[loops] は外側から順に (何回目, 回数 または 無限なら null)。 */
    fun update(loops: List<Pair<Int, Int?>>, step: Int, size: Int) {
        val view = progress ?: return
        view.text = describe(view.context, loops, step, size)
    }

    /** 2 行目をそのまま書き換える (AI の帯では操作先のアプリ名)。 */
    fun setDetail(text: String) { progress?.text = text }

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

    /** 帯を閉じる (実行が終わったとき・AI の通知を消したとき・選ぶ帯の ×)。 */
    fun dismiss() {
        cancelRestore?.invoke(); cancelRestore = null
        val view = root
        if (view != null) runCatching { service?.getSystemService(WindowManager::class.java)?.removeViewImmediate(view) }
        service = null; root = null; params = null; progress = null; nameView = null; toggleView = null
        hidden = false; held = 0; owner = null
    }

    /** [owner] の帯を出しているときだけ閉じる。 */
    fun dismiss(owner: Owner) { if (this.owner == owner) dismiss() }

    /** 帯の画面上の四角 (指先の位置そのものより少し広め)。出していなければ null。 */
    private fun box(): Rect? = root?.takeIf { it.isAttachedToWindow }?.let { v ->
        val at = IntArray(2).also { v.getLocationOnScreen(it) }
        val margin = ActionUi.dp(v.context, 8)
        Rect(at[0] - margin, at[1] - margin, at[0] + v.width + margin, at[1] + v.height + margin)
    }

    /**
     * 座標の操作を送る前に呼ぶ (マクロ)。指が通る範囲 ([hits] が帯の画面上の四角で true を返す) が帯に重なるなら、
     * 帯を隠して入力に反映されるのを待ってから [action] を送る。[action] には終わりの知らせを渡す。
     * 戻り値は取り消し (エンジンの cancelStep)。
     */
    fun clear(hits: (Rect) -> Boolean, schedule: (Long, () -> Unit) -> (() -> Unit),
        done: (String?) -> Unit, action: ((String?) -> Unit) -> (() -> Unit)?): (() -> Unit)? {
        val box = box()
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

    /**
     * z2-ui の指を送る前に呼ぶ (メインスレッド)。指が通る範囲が帯に重ならなければ null。重なるなら帯を隠し、
     * いま隠したなら true (呼び手が [SETTLE_MS] 待つ)、もう隠れていたなら false を返す。null 以外のときは
     * 送り終えたら必ず [stepBack] を呼ぶ。
     */
    fun stepAside(hits: (Rect) -> Boolean): Boolean? {
        val box = box() ?: return null
        if (!hits(box)) return null
        held++
        cancelRestore?.invoke(); cancelRestore = null
        if (hidden) return false
        hide()
        return true
    }

    /** [stepAside] で隠した指が離れたとき。ほかに帯の下を通る指が無ければ、少し後で戻す。 */
    fun stepBack() {
        held = (held - 1).coerceAtLeast(0)
        if (held == 0 && hidden) restoreLater(::post)
    }

    /**
     * z2-ui が撮る前に呼ぶ (メインスレッド)。前の操作で隠したまま戻す予定の帯を、いま戻す。撮るたびに
     * 帯が写ったり写らなかったりすると「画面が変わった」と読まれるため。戻したら true (呼び手が待つ)。
     * 指が帯の下にいる間と、マクロが隠した帯 (マクロの指がまだ下にいるかもしれない) は戻さない。
     */
    fun restoreNow(): Boolean {
        if (!hidden || held > 0 || owner == Owner.MACRO) return false
        cancelRestore?.invoke(); cancelRestore = null
        show()
        return true
    }

    /** 指を素通りさせる。帯は薄くして見せたままにする (進み具合を読めるように)。 */
    private fun hide() {
        val view = root ?: return
        val lp = params ?: return
        hidden = true
        view.alpha = DIM_ALPHA
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { service?.getSystemService(WindowManager::class.java)?.updateViewLayout(view, lp) }
    }

    private fun show() {
        val view = root ?: return
        val lp = params ?: return
        hidden = false
        view.alpha = 1f
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { service?.getSystemService(WindowManager::class.java)?.updateViewLayout(view, lp) }
    }

    private fun restoreLater(schedule: (Long, () -> Unit) -> (() -> Unit)) {
        cancelRestore?.invoke()
        cancelRestore = schedule(RESTORE_MS) {
            cancelRestore = null
            if (held == 0) show()
        }
    }

    /**
     * 帯の大きさを測って窓の大きさに決め、はみ出さないよう画面の中へ収める。
     * ⚠ 大きさは view.width ではなく測り直した値を使う。画面の端で切られた窓の幅は、中身が求める幅より
     * 狭いことがある ([refit])。
     * ⚠ 窓の大きさを「中身に合わせる (WRAP_CONTENT)」のままにしない (1.0.114)。Android はそういう窓を
     * まずダイアログ向けの幅 (スマホで 320dp ほど) で測り、足りないと言われたときだけ広げる。進み具合が
     * その幅を超えると、文字が先に場所を取り、後ろの ‖ と ■ が押しつぶされて見切れていた
     * (文字は … で収まるので「足りない」とは言われない)。そこで画面の幅を上限に自分で測った大きさを渡す。
     */
    private fun clamp(context: Context, view: View, lp: WindowManager.LayoutParams) {
        val screen = ActionRuntime.screen(context)
        view.measure(View.MeasureSpec.makeMeasureSpec(screen.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screen.height, View.MeasureSpec.AT_MOST))
        lp.width = view.measuredWidth; lp.height = view.measuredHeight
        lp.x = lp.x.coerceIn(0, maxOf(0, screen.width - view.measuredWidth))
        lp.y = lp.y.coerceIn(0, maxOf(0, screen.height - view.measuredHeight))
    }

    private fun synthetic(event: MotionEvent) = event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD &&
        event.getToolType(0) == MotionEvent.TOOL_TYPE_UNKNOWN
}
