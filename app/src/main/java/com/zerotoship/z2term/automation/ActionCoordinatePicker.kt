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
    data class Result(val request: String, val screen: ActionDefinition.Screen, val points: List<Float>?, val error: String?, val selector: ActionSelector? = null,
        val durationMs: Long? = null, val paths: List<List<ActionGesture.Point>>? = null, val recorded: String? = null)
    private var session: Session? = null
    private var result: Result? = null
    val active get() = session != null
    fun isActive(request: String) = session?.request == request
    fun consume(request: String): Result? = result?.takeIf { it.request == request }?.also { result = null }

    fun start(service: AndroidActions, request: String, swipe: Boolean, target: String? = null, fingers: Int = 1, freehand: Boolean = false, recording: Boolean = false, live: Boolean = false) {
        check(!active && !ActionRuntime.running && !AndroidActions.gesturesInFlight()) { service.getString(R.string.action_pick_busy) }
        // Only UI-element selection has an app target. Coordinate selection measures the screen.
        val launch = target?.takeUnless { it == ActionDefinition.CURRENT_TARGET }?.let {
            ActionDefinition.packageName(it)
            service.packageManager.getLaunchIntentForPackage(it)
                ?: error(service.getString(R.string.action_pick_launch_failed, it))
        }
        val next = Session(service, request, target, swipe, ActionRuntime.screen(service), fingers, freehand, recording, live)
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

    /** Keep the return destination with this request, without retaining an activity instance. */
    fun returnTo(request: String, activity: android.app.Activity) {
        session?.takeIf { it.request == request }?.returnIntent = Intent(activity, activity::class.java)
    }

    fun cancel() {
        session?.let { it.finish(null, it.service.getString(R.string.action_pick_cancelled), returnToEditor = false) }
    }

    private class Session(val service: AndroidActions, val request: String, val target: String?,
        val swipe: Boolean, val screen: ActionDefinition.Screen, val fingers: Int, val freehand: Boolean, val recording: Boolean, val live: Boolean) {
        var returnIntent = Intent(service, ActionMacrosActivity::class.java)
        private val elements = target != null
        private val main = Handler(Looper.getMainLooper())
        private val wm = service.getSystemService(WindowManager::class.java)
        private var window: View? = null
        private var capturing = !elements && !live
        private var loading = false
        private var choices = emptyList<ActionSelector>()
        private var readTarget: String? = null
        private var lookup: Job? = null
        private var bottom = false
        private var points: List<Float>? = null
        private var durationMs: Long? = null
        private var paths: List<List<ActionGesture.Point>>? = null
        private val sequence = ActionRecording()
        private var rootReader: ActionRootRecorder? = null
        private var recordReady = !live
        private var updateRecording: (() -> Unit)? = null
        private var controlsView: View? = null
        private val excludedIds = mutableSetOf<Int>()
        private var seenIds = emptySet<Int>()
        private var ready = false
        private fun clearSelection() { points = null; durationMs = null; paths = null; ready = false }
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

        fun show() {
            render(); main.post(monitor)
            if (recording && live) {
                rootReader = ActionRootRecorder(screen).also { reader ->
                    reader.start(ready = { recordReady = true; updateRecording?.invoke() }, frame = { frame ->
                        try {
                            val ids = frame.contacts.map { it.id }.toSet()
                            excludedIds.retainAll(ids)
                            val controls = controlsView
                            val origin = IntArray(2)
                            controls?.getLocationOnScreen(origin)
                            frame.contacts.filter { it.id !in seenIds }.forEach { contact ->
                                if (controls != null && contact.x >= origin[0] && contact.x < origin[0] + controls.width &&
                                    contact.y >= origin[1] && contact.y < origin[1] + controls.height) excludedIds += contact.id
                            }
                            seenIds = ids
                            sequence.frame(frame.ms, frame.contacts.filter { it.id !in excludedIds }, frame.released)
                            updateRecording?.invoke()
                        } catch (e: Exception) { finish(null, e.message) }
                    }, failed = { finish(null, service.getString(R.string.action_record_root_error, it)) })
                }
            }
        }

        private fun readElements() {
            loading = true; choices = emptyList(); readTarget = null; render()
            lookup = CoroutineScope(Dispatchers.Main.immediate).launch {
                try {
                    val packageName = if (target == ActionDefinition.CURRENT_TARGET)
                        AndroidActions.focusedPackage() ?: error(service.getString(R.string.action_run_no_foreground))
                        else requireNotNull(target)
                    val found = AndroidActions.inspectUi(packageName).flatMap { it.selectors() }.distinct()
                    if (session !== this@Session) return@launch
                    readTarget = packageName; choices = found
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
                if (recording) { if (live) R.string.action_record_live_hint else R.string.action_record_overlay_hint } else if (elements) R.string.action_ui_prepare else if (!capturing) R.string.action_pick_prepare else if (fingers == 2) R.string.action_pick_two else if (freehand) R.string.action_pick_freehand else if (swipe) R.string.action_pick_swipe else R.string.action_pick_point)).apply {
                textSize = 13f
                setLineSpacing(EdgeSettingsUi.dp(service, 3).toFloat(), 1f)
            }, LinearLayout.LayoutParams(-1, -2))
            val summary = EdgeSettingsUi.body(service, "").apply { textSize = 12f }
            controls.addView(summary)
            fun updateSummary() {
                summary.text = if (recording) {
                    if (!recordReady) service.getString(R.string.action_record_connecting)
                    else service.getString(R.string.action_record_summary, sequence.count, sequence.durationMs)
                } else if (ready && swipe) service.getString(R.string.action_gesture_summary,
                    durationMs ?: 0L, paths?.firstOrNull()?.let { ActionGesture.speed(it).toInt() } ?: 0) else ""
                summary.visibility = if (summary.text.isEmpty()) View.GONE else View.VISIBLE
            }
            updateSummary()
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
            val accept = button(if (recording) R.string.action_record_stop else if (elements) R.string.action_ui_read else if (capturing) R.string.action_edit_apply else R.string.action_edit_pick,
                EdgeSettingsUi.Kind.PRIMARY, !loading && (if (recording) recordReady && sequence.count > 0 && !sequence.touching else !capturing || ready)) {
                try {
                    if (recording) {
                        check(ActionRuntime.screen(service) == screen && unlocked()) { service.getString(R.string.action_pick_cancelled) }
                        val text = sequence.source("percent", screen)
                        finish(null, null, recorded = text)
                    } else if (elements) {
                        readElements()
                    }
                    else if (!capturing) { capturing = true; clearSelection(); render() }
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
            updateRecording = {
                updateSummary()
                accept.isEnabled = recordReady && sequence.count > 0 && !sequence.touching
            }
            controlsView = controls
            if (capturing && !recording) button(R.string.action_pick_navigate) {
                capturing = false; clearSelection()
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
                            check(AndroidActions.targetMatches(requireNotNull(readTarget))) {
                                service.getString(R.string.action_pick_foreground, readTarget)
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
                    private var ignoreRecordTouch = false
                    private var tracking = false
                    private var startedAt = 0L
                    private var ids = emptyList<Int>()
                    private var recordings = emptyList<ActionGesture.Recording>()
                    override fun onDraw(canvas: Canvas) {
                        val origin = IntArray(2).also { getLocationOnScreen(it) }
                        val traces = paths ?: return
                        traces.forEach { trace ->
                            val shown = if (recording || freehand || fingers == 2) trace else listOf(trace.first(), trace.last())
                            shown.zipWithNext().forEach { (a, b) ->
                                canvas.drawLine(a.x - origin[0], a.y - origin[1], b.x - origin[0], b.y - origin[1], paint)
                            }
                            canvas.drawCircle(shown.first().x - origin[0], shown.first().y - origin[1], 10f, paint)
                            canvas.drawCircle(shown.last().x - origin[0], shown.last().y - origin[1], 10f, paint)
                        }
                    }
                    override fun performClick(): Boolean { super.performClick(); return true }
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        if (recording) {
                            if (event.actionMasked == MotionEvent.ACTION_DOWN) ignoreRecordTouch = false
                            if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.pointerCount > 2) {
                                sequence.cancelTouch(); ignoreRecordTouch = true; paths = sequence.preview(); invalidate()
                                updateRecording?.invoke(); return true
                            }
                            if (ignoreRecordTouch) return true
                            try {
                                val origin = IntArray(2).also { getLocationOnScreen(it) }
                                fun contacts(history: Int? = null) = (0 until event.pointerCount).map { index ->
                                    ActionRecording.Contact(event.getPointerId(index),
                                        ((if (history == null) event.getX(index) else event.getHistoricalX(index, history)) + origin[0])
                                            .coerceIn(0f, (screen.width - 1).toFloat()),
                                        ((if (history == null) event.getY(index) else event.getHistoricalY(index, history)) + origin[1])
                                            .coerceIn(0f, (screen.height - 1).toFloat()))
                                }
                                if (event.actionMasked == MotionEvent.ACTION_MOVE)
                                    for (i in 0 until event.historySize) sequence.frame(event.getHistoricalEventTime(i), contacts(i))
                                val current = contacts()
                                sequence.frame(event.eventTime, current)
                                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP)
                                    sequence.frame(event.eventTime, current.filter { it.id != event.getPointerId(event.actionIndex) })
                                paths = sequence.preview(); updateRecording?.invoke(); invalidate()
                            } catch (e: Exception) {
                                sequence.cancelTouch(); ignoreRecordTouch = true; paths = sequence.preview(); invalidate()
                                Toast.makeText(service, e.message, Toast.LENGTH_LONG).show()
                                updateRecording?.invoke()
                            }
                            return true
                        }
                        fun reset() {
                            tracking = false; ids = emptyList(); recordings = emptyList()
                            clearSelection(); accept.isEnabled = false; updateSummary(); invalidate()
                        }
                        if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.pointerCount > fingers) {
                            reset(); return true
                        }
                        val origin = IntArray(2).also { getLocationOnScreen(it) }
                        fun x(index: Int, history: Int? = null) =
                            ((if (history == null) event.getX(index) else event.getHistoricalX(index, history)) + origin[0])
                                .coerceIn(0f, (screen.width - 1).toFloat())
                        fun y(index: Int, history: Int? = null) =
                            ((if (history == null) event.getY(index) else event.getHistoricalY(index, history)) + origin[1])
                                .coerceIn(0f, (screen.height - 1).toFloat())
                        fun begin() {
                            tracking = true; startedAt = event.eventTime
                            ids = (0 until fingers).map { event.getPointerId(it) }
                            recordings = (0 until fingers).map { index ->
                                ActionGesture.Recording().apply { add(x(index), y(index), 0) }
                            }
                            paths = recordings.map { it.points.toList() }
                        }
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> { reset(); if (fingers == 1) begin() }
                            MotionEvent.ACTION_POINTER_DOWN -> if (fingers == 2 && event.pointerCount == 2) {
                                reset(); begin()
                            }
                            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (tracking) {
                                val up = event.actionMasked != MotionEvent.ACTION_MOVE
                                val elapsed = (event.eventTime - startedAt).coerceAtLeast(1)
                                val maximum = if (freehand || fingers == 2) ActionGesture.MAX_MS else 3000L
                                if (swipe && elapsed > maximum) {
                                    reset()
                                    Toast.makeText(service, service.getString(R.string.action_pick_too_long, maximum / 1000), Toast.LENGTH_LONG).show()
                                    return true
                                }
                                val indices = ids.map { event.findPointerIndex(it) }
                                if (indices.any { it < 0 }) { reset(); return true }
                                // Historical samples keep fast curves and late flick acceleration.
                                if (swipe) for (history in 0 until event.historySize) {
                                    val time = event.getHistoricalEventTime(history) - startedAt
                                    if (time >= 0) indices.forEachIndexed { finger, index ->
                                        recordings[finger].add(x(index, history), y(index, history), time)
                                    }
                                }
                                indices.forEachIndexed { finger, index ->
                                    recordings[finger].add(x(index), y(index), if (swipe) elapsed else elapsed.coerceAtMost(ActionGesture.MAX_MS), up)
                                }
                                paths = recordings.map { it.points.toList() }
                                val first = paths!!.first()
                                val a = if (swipe) first.first() else first.last()
                                val b = first.last()
                                points = listOf(a.x, a.y, b.x, b.y)
                                if (up) {
                                    tracking = false; ready = true; durationMs = elapsed
                                    accept.isEnabled = true; updateSummary(); performClick()
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

        fun finish(points: List<Float>?, error: String?, returnToEditor: Boolean = true, selector: ActionSelector? = null, recorded: String? = null) {
            if (session !== this) return
            session = null
            main.removeCallbacks(monitor)
            lookup?.cancel(); lookup = null
            rootReader?.stop(); rootReader = null; updateRecording = null; controlsView = null
            window?.let { runCatching { wm.removeViewImmediate(it) } }; window = null
            result = Result(request, screen, points, error, selector,
                if (recorded != null) sequence.durationMs else durationMs.takeIf { points != null }, paths?.takeIf { points != null }, recorded)
            runCatching { EdgeRuntime.suspendForActions(false) }
            if (returnToEditor && unlocked()) runCatching {
                service.startActivity(Intent(returnIntent)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
    }
}
