package com.zerotoship.z2term.automation

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.ListView
import android.widget.ArrayAdapter
import kotlinx.coroutines.*
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.AndroidActions
import com.zerotoship.z2term.edge.EdgeSettingsUi
import com.zerotoship.z2term.edge.EdgeRuntime

/** A short-lived selection overlay. Coordinates and UI selectors return to the editor without executing actions. */
internal object ActionCoordinatePicker {
    data class Result(val request: String, val screen: ActionDefinition.Screen, val points: List<Float>?, val error: String?, val selector: ActionSelector? = null)
    private var session: Session? = null
    private var result: Result? = null
    val active get() = session != null
    fun isActive(request: String) = session?.request == request
    fun consume(request: String): Result? = result?.takeIf { it.request == request }?.also { result = null }

    fun start(service: AndroidActions, request: String, swipe: Boolean, target: String? = null) {
        check(!active && !ActionRuntime.running && !AndroidActions.gesturesInFlight()) { service.getString(R.string.action_pick_busy) }
        // Only UI-element selection has an app target. Coordinate selection measures the screen.
        val launch = target?.let {
            ActionDefinition.packageName(it)
            service.packageManager.getLaunchIntentForPackage(it)
                ?: error(service.getString(R.string.action_pick_launch_failed, it))
        }
        val next = Session(service, request, target, swipe, ActionRuntime.screen(service))
        EdgeRuntime.suspendForActions(true)
        result = null; session = next
        try {
            launch?.let { service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            next.show()
        } catch (e: Exception) {
            next.finish(null, e.message, returnToEditor = false)
            throw e
        }
    }

    fun cancel() {
        session?.let { it.finish(null, it.service.getString(R.string.action_pick_cancelled), returnToEditor = false) }
    }

    private class Session(val service: AndroidActions, val request: String, val target: String?,
        val swipe: Boolean, val screen: ActionDefinition.Screen) {
        private val elements = target != null
        private val main = Handler(Looper.getMainLooper())
        private val wm = service.getSystemService(WindowManager::class.java)
        private var window: View? = null
        private var capturing = !elements
        private var loading = false
        private var choices = emptyList<ActionSelector>()
        private var lookup: Job? = null
        private var bottom = false
        private var points: List<Float>? = null
        private val deadline = SystemClock.elapsedRealtime() + 120_000
        private val monitor = object : Runnable {
            override fun run() {
                if (session !== this@Session) return
                val valid = runCatching {
                    unlocked() && ActionRuntime.screen(service) == screen && AndroidActions.connected() &&
                        SystemClock.elapsedRealtime() < deadline
                }.getOrDefault(false)
                if (!valid) finish(null, service.getString(R.string.action_pick_cancelled))
                else main.postDelayed(this, 200)
            }
        }
        private fun unlocked() = service.getSystemService(PowerManager::class.java).isInteractive &&
            !service.getSystemService(KeyguardManager::class.java).isKeyguardLocked

        fun show() { render(); main.post(monitor) }

        private fun readElements() {
            loading = true; render()
            lookup = CoroutineScope(Dispatchers.Main.immediate).launch {
                try {
                    val found = AndroidActions.inspectUi(requireNotNull(target)).flatMap { it.selectors() }.distinct()
                    if (session !== this@Session) return@launch
                    choices = found
                    if (found.isEmpty()) Toast.makeText(service, R.string.action_ui_empty, Toast.LENGTH_LONG).show()
                } catch (e: CancellationException) { throw e } catch (e: Exception) { Toast.makeText(service, e.message, Toast.LENGTH_LONG).show() } finally {
                    if (session === this@Session) {
                        loading = false
                        runCatching { render() }.onFailure { finish(null, it.message) }
                    }
                }
            }
        }

        private fun render() {
            window?.let { wm.removeViewImmediate(it) }; window = null
            // This bar floats over someone else's app, so it states its own ground and border
            // rather than borrowing whatever is behind it.
            val controls = EdgeSettingsUi.column(service).apply {
                background = EdgeSettingsUi.frame(service, fill = EdgeSettingsUi.canvas(service))
                setPadding(EdgeSettingsUi.dp(service, 12), EdgeSettingsUi.dp(service, 10),
                    EdgeSettingsUi.dp(service, 12), EdgeSettingsUi.dp(service, 10))
            }
            controls.addView(EdgeSettingsUi.body(service, service.getString(
                if (elements) R.string.action_ui_prepare else if (!capturing) R.string.action_pick_prepare else if (swipe) R.string.action_pick_swipe else R.string.action_pick_point)).apply {
                textSize = 13f
                setLineSpacing(EdgeSettingsUi.dp(service, 3).toFloat(), 1f)
            }, LinearLayout.LayoutParams(-1, -2))
            val row = EdgeSettingsUi.row(service)
            controls.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = EdgeSettingsUi.dp(service, 10)
            })
            fun button(label: Int, kind: EdgeSettingsUi.Kind = EdgeSettingsUi.Kind.OUTLINE,
                enabled: Boolean = true, action: () -> Unit) =
                EdgeSettingsUi.button(service, service.getString(label), kind, action).apply {
                    isEnabled = enabled
                    row.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (row.childCount > 0) leftMargin = EdgeSettingsUi.dp(service, 8)
                    })
                }
            val accept = button(if (elements) R.string.action_ui_read else if (capturing) R.string.action_edit_apply else R.string.action_edit_pick,
                EdgeSettingsUi.Kind.PRIMARY, !loading && (!capturing || points != null)) {
                try {
                    if (elements) {
                        check(AndroidActions.targetMatches(requireNotNull(target))) { service.getString(R.string.action_pick_foreground, target) }
                        readElements()
                    }
                    else if (!capturing) { capturing = true; points = null; render() }
                    else {
                        val selected = points ?: return@button
                        check(ActionRuntime.screen(service) == screen && unlocked()) { service.getString(R.string.action_pick_cancelled) }
                        finish(selected, null)
                    }
                } catch (e: Exception) {
                    Toast.makeText(service, e.message, Toast.LENGTH_LONG).show()
                    if (window == null) finish(null, e.message)
                }
            }
            if (capturing) button(R.string.action_pick_navigate) {
                capturing = false; points = null
                try { render() } catch (e: Exception) { finish(null, e.message) }
            }
            button(R.string.action_pick_move) {
                bottom = !bottom
                try { render() } catch (e: Exception) { finish(null, e.message) }
            }
            button(android.R.string.cancel, EdgeSettingsUi.Kind.QUIET) {
                finish(null, service.getString(R.string.action_pick_cancelled))
            }
            if (elements && choices.isNotEmpty()) {
                controls.addView(EdgeSettingsUi.hairline(service), LinearLayout.LayoutParams(-1,
                    EdgeSettingsUi.dp(service, 1)).apply { topMargin = EdgeSettingsUi.dp(service, 10) })
                controls.addView(ListView(service).apply {
                    divider = null
                    adapter = ArrayAdapter(service, android.R.layout.simple_list_item_1, choices.map { it.toString() })
                    setOnItemClickListener { _, _, index, _ ->
                        try {
                            check(AndroidActions.targetMatches(requireNotNull(target))) {
                                service.getString(R.string.action_pick_foreground, target)
                            }
                            check(unlocked() && ActionRuntime.screen(service) == screen) { service.getString(R.string.action_pick_cancelled) }
                            finish(null, null, selector = choices[index])
                        } catch (e: Exception) { Toast.makeText(service, e.message, Toast.LENGTH_LONG).show() }
                    }
                }, LinearLayout.LayoutParams(-1, minOf(screen.height / 3, EdgeSettingsUi.dp(service, 300))))
            }
            val content = if (capturing) FrameLayout(service).apply {
                addView(object : View(service) {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = EdgeSettingsUi.accent(service); strokeWidth = 4f
                    }
                    private var tracking = false
                    private var startX = 0f
                    private var startY = 0f
                    override fun onDraw(canvas: Canvas) {
                        val p = points ?: return
                        val origin = IntArray(2).also { getLocationOnScreen(it) }
                        canvas.drawLine(p[0] - origin[0], p[1] - origin[1], p[2] - origin[0], p[3] - origin[1], paint)
                        canvas.drawCircle(p[0] - origin[0], p[1] - origin[1], 10f, paint)
                        canvas.drawCircle(p[2] - origin[0], p[3] - origin[1], 10f, paint)
                    }
                    override fun performClick(): Boolean { super.performClick(); return true }
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        if (event.pointerCount != 1 || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                            tracking = false; points = null; accept.isEnabled = false; invalidate(); return true
                        }
                        val x = event.rawX.coerceIn(0f, (screen.width - 1).toFloat())
                        val y = event.rawY.coerceIn(0f, (screen.height - 1).toFloat())
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                tracking = true; startX = x; startY = y; points = null; accept.isEnabled = false
                            }
                            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> if (tracking) {
                                points = if (swipe) listOf(startX, startY, x, y) else listOf(x, y, x, y)
                                if (event.actionMasked == MotionEvent.ACTION_UP) {
                                    tracking = false; accept.isEnabled = true; performClick()
                                }
                            }
                        }
                        invalidate()
                        return true
                    }
                }, FrameLayout.LayoutParams(-1, -1))
                // Qualify the side: inside this apply the FrameLayout's own View.bottom (an Int) wins.
                addView(controls, FrameLayout.LayoutParams(-1, -2,
                    if (this@Session.bottom) Gravity.BOTTOM else Gravity.TOP).apply {
                    val inset = EdgeSettingsUi.dp(service, 36)
                    val side = EdgeSettingsUi.dp(service, 12)
                    leftMargin = side; rightMargin = side
                    if (this@Session.bottom) bottomMargin = inset else topMargin = inset
                })
            } else controls
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                if (capturing) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = (if (bottom && !capturing) Gravity.BOTTOM else Gravity.TOP) or Gravity.LEFT
                if (!capturing) y = EdgeSettingsUi.dp(service, 36)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            wm.addView(content, params)
            window = content
        }

        fun finish(points: List<Float>?, error: String?, returnToEditor: Boolean = true, selector: ActionSelector? = null) {
            if (session !== this) return
            session = null
            main.removeCallbacks(monitor)
            lookup?.cancel(); lookup = null
            window?.let { runCatching { wm.removeViewImmediate(it) } }; window = null
            result = Result(request, screen, points, error, selector)
            runCatching { EdgeRuntime.suspendForActions(false) }
            if (returnToEditor && unlocked()) runCatching {
                service.startActivity(Intent(service, ActionMacrosActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
    }
}
