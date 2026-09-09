package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.sign

/** Serial, callback-driven strokes. Never queue a second injection while one is in flight. */
internal class AndroidAutoScroll(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var flightToken: Int? = null
    private val inFlight get() = flightToken != null
    var running = false
        private set
    private var next: Runnable? = null
    private var finished: ((String?) -> Unit)? = null
    private var stoppedAt: Long? = null
    private var singleShot = false
    fun recentlyRunning(): Boolean = running || stoppedAt?.let {
        android.os.SystemClock.uptimeMillis() - it < 500
    } == true

    fun stop(error: String? = null, completed: Boolean = false) {
        if (running) stoppedAt = android.os.SystemClock.uptimeMillis()
        generation++
        running = false
        next?.let(main::removeCallbacks)
        next = null
        val callback = finished
        finished = null
        val result = error ?: if (singleShot && !completed)
            service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed) else null
        singleShot = false
        callback?.invoke(result)
        // A dispatched stroke has no cancellation API. Its bounded duration ends; no successor is sent.
    }

    /** dispatchGesture uses VIRTUAL_KEYBOARD + UNKNOWN; physical touches must stop even in flight.
     * Outside events retain device/tool identity even when Android redacts their coordinates. */
    fun outsideTouch(event: android.view.MotionEvent) {
        val synthetic = event.deviceId == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD &&
            event.getToolType(0) == android.view.MotionEvent.TOOL_TYPE_UNKNOWN
        if (!synthetic) stop()
    }

    fun start(speedDp: Float, bounds: Rect, xPercent: Float, yPercent: Float, once: Boolean,
        stillTarget: () -> Boolean, done: (String?) -> Unit) {
        check(!inFlight) { "Wait for the previous scroll stroke to finish" }
        require(speedDp.isFinite() && speedDp != 0f && bounds.width() > 0 && bounds.height() > 0)
        stop()
        running = true
        singleShot = once
        stoppedAt = null
        finished = done
        val token = generation
        val density = service.resources.displayMetrics.density
        // Match AccessibilityService.dispatchGesture's display-based sample interval.
        // Older Android implementations (or an unavailable display) use a 100 ms sample.
        val display = service.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val refreshRate = display?.refreshRate
        val sampleMs = if (android.os.Build.VERSION.SDK_INT >= 30 && refreshRate != null &&
            refreshRate.isFinite() && refreshRate > 0f) (1000 / refreshRate).toInt().coerceIn(1, 1000) else 100
        val timing = EdgeScrollTiming.plan(abs(speedDp), bounds.height() / density, sampleMs)
        val distance = timing.distanceDp * density
        val x = bounds.left + bounds.width() * xPercent.coerceIn(10f, 90f) / 100f
        val centerY = bounds.top + bounds.height() * yPercent.coerceIn(10f, 90f) / 100f
        val low = (centerY - distance / 2).coerceIn(bounds.top + 1f, bounds.bottom - distance - 1f)
        val from = if (speedDp > 0) low else low + distance
        val to = from + sign(speedDp) * distance
        val stroke = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(Path().apply { moveTo(x, from); lineTo(x, to) }, 0, timing.durationMs)
        ).build()
        val tick = object : Runnable {
            override fun run() {
                if (!running || token != generation) return
                if (!runCatching(stillTarget).getOrDefault(false)) { stop(); return }
                flightToken = token
                val watchdog = Runnable {
                    if (flightToken == token) flightToken = null
                    if (token == generation) stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed))
                }
                main.postDelayed(watchdog, 1500)
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (flightToken == token) flightToken = null
                        main.removeCallbacks(watchdog)
                        if (running && token == generation) {
                            if (once) stop(completed = true)
                            else next?.let { main.postDelayed(it, timing.pauseMs) }
                        }
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (flightToken == token) flightToken = null
                        main.removeCallbacks(watchdog)
                        if (token == generation) stop()
                    }
                }
                try {
                    if (!service.dispatchGesture(stroke, callback, main)) {
                        if (flightToken == token) flightToken = null
                        main.removeCallbacks(watchdog)
                        stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed))
                    }
                } catch (e: Exception) {
                    if (flightToken == token) flightToken = null
                    main.removeCallbacks(watchdog)
                    stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed))
                }
            }
        }
        next = tick
        main.post(tick)
    }
}
