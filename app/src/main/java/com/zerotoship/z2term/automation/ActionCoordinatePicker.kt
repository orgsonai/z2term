package com.zerotoship.z2term.automation

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
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
import com.zerotoship.z2term.edge.EdgeEditorUi
import com.zerotoship.z2term.edge.EdgeRuntime

/** A short-lived selection overlay. Coordinates and UI selectors return to the editor without executing actions. */
internal object ActionCoordinatePicker {
    data class Result(val request: String, val screen: ActionDefinition.Screen, val points: List<Float>?, val error: String?, val selector: ActionSelector? = null)
    private var session: Session? = null
    private var result: Result? = null
    val active get() = session != null
    fun isActive(request: String) = session?.request == request
    fun consume(request: String): Result? = result?.takeIf { it.request == request }?.also { result = null }

    fun start(service: AndroidActions, request: String, target: String, swipe: Boolean, elements: Boolean = false) {
        check(!active && !ActionRuntime.running && !AndroidActions.gesturesInFlight()) { service.getString(R.string.action_pick_busy) }
        ActionDefinition.packageName(target)
        val launch = service.packageManager.getLaunchIntentForPackage(target)
            ?: error(service.getString(R.string.action_pick_target))
        val next = Session(service, request, target, swipe, ActionRuntime.screen(service), elements)
        EdgeRuntime.suspendForActions(true)
        result = null; session = next
        try {
            service.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            next.show()
        } catch (e: Exception) {
            next.finish(null, e.message, returnToEditor = false)
            throw e
        }
    }

    fun cancel() {
        session?.let { it.finish(null, it.service.getString(R.string.action_pick_cancelled), returnToEditor = false) }
    }

    private class Session(val service: AndroidActions, val request: String, val target: String,
        val swipe: Boolean, val screen: ActionDefinition.Screen, val elements: Boolean) {
        private val main = Handler(Looper.getMainLooper())
        private val wm = service.getSystemService(WindowManager::class.java)
        private var window: View? = null
        private var capturing = false
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
                    val found = AndroidActions.inspectUi(target).flatMap { it.selectors() }.distinct()
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
            val controls = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(EdgeEditorUi.surface(service))
            }
            controls.addView(EdgeEditorUi.label(service, service.getString(
                if (elements) R.string.action_ui_prepare else if (!capturing) R.string.action_pick_prepare else if (swipe) R.string.action_pick_swipe else R.string.action_pick_point), true))
            val row = LinearLayout(service)
            controls.addView(row)
            fun button(label: Int, enabled: Boolean = true, action: () -> Unit) =
                EdgeEditorUi.button(service, service.getString(label), action = action).apply {
                    isEnabled = enabled; row.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
                }
            val accept = button(if (elements) R.string.action_ui_read else if (capturing) R.string.action_edit_apply else R.string.action_edit_pick,
                !loading && (!capturing || points != null)) {
                try {
                    check(AndroidActions.targetMatches(target)) { service.getString(R.string.action_pick_target) }
                    if (elements) readElements()
                    else if (!capturing) { capturing = true; points = null; render() }
                    else {
                        val selected = points ?: return@button
                        val bounds = AndroidActions.coordinateTargetBounds(target)
                        check(bounds != null && bounds.contains(selected[0].toInt(), selected[1].toInt()) &&
                            bounds.contains(selected[2].toInt(), selected[3].toInt())) { service.getString(R.string.action_pick_target) }
                        check(ActionRuntime.screen(service) == screen && unlocked()) { service.getString(R.string.action_pick_cancelled) }
                        finish(selected, null)
                    }
                } catch (e: Exception) {
                    Toast.makeText(service, e.message, Toast.LENGTH_LONG).show()
                    if (window == null) finish(null, e.message)
                }
            }
            button(R.string.action_pick_move) {
                bottom = !bottom
                try { render() } catch (e: Exception) { finish(null, e.message) }
            }
            button(android.R.string.cancel) { finish(null, service.getString(R.string.action_pick_cancelled)) }
            if (elements && choices.isNotEmpty()) {
                controls.addView(ListView(service).apply {
                    adapter = ArrayAdapter(service, android.R.layout.simple_list_item_1, choices.map { it.toString() })
                    setOnItemClickListener { _, _, index, _ ->
                        try {
                            check(AndroidActions.targetMatches(target) && unlocked() && ActionRuntime.screen(service) == screen) {
                                service.getString(R.string.action_pick_target)
                            }
                            finish(null, null, selector = choices[index])
                        } catch (e: Exception) { Toast.makeText(service, e.message, Toast.LENGTH_LONG).show() }
                    }
                }, LinearLayout.LayoutParams(-1, minOf(screen.height / 3, EdgeEditorUi.dp(service, 300))))
            }
            val content = if (capturing) FrameLayout(service).apply {
                addView(object : View(service) {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 180, 45); strokeWidth = 4f }
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
                    val inset = EdgeEditorUi.dp(service, 36)
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
                if (!capturing) y = EdgeEditorUi.dp(service, 36)
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
