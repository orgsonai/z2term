package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.sign

/** Serial continuations keep the pointer down; page resets and stops release it without a fling. */
internal class AndroidAutoScroll(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var flightSequence = 0L
    private var flight: Long? = null
    val inFlight get() = flight != null
    var running = false
        private set
    private var next: Runnable? = null
    private var finished: ((String?) -> Unit)? = null
    private var stoppedAt: Long? = null
    private var singleShot = false
    private var pointer: GestureDescription.StrokeDescription? = null
    private var pointerOwner = -1
    private var pointerX = 0f
    private var pointerY = 0f
    private var pointerReleaseOffset = 1f
    private var releaseAllowed = true

    fun recentlyRunning(): Boolean = running || stoppedAt?.let {
        SystemClock.uptimeMillis() - it < 500
    } == true

    fun stop(error: String? = null, completed: Boolean = false, releasePointer: Boolean = true) {
        if (running) stoppedAt = SystemClock.uptimeMillis()
        generation++
        running = false
        next?.let(main::removeCallbacks); next = null
        releaseAllowed = releasePointer
        val callback = finished; finished = null
        val result = error ?: if (singleShot && !completed)
            service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed) else null
        singleShot = false
        if (!inFlight) {
            if (releasePointer) release() else pointer = null
        }
        callback?.invoke(result)
    }

    fun outsideTouch(event: android.view.MotionEvent) {
        val synthetic = event.deviceId == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD &&
            event.getToolType(0) == android.view.MotionEvent.TOOL_TYPE_UNKNOWN
        // Physical input cancels the injected stream. Do not inject a new UP over that input.
        if (!synthetic) stop(releasePointer = false)
    }

    fun start(speedDp: Float, bounds: Rect, xPercent: Float, yPercent: Float, once: Boolean,
        stillTarget: () -> Boolean, done: (String?) -> Unit) {
        require(speedDp.isFinite() && speedDp != 0f && bounds.width() > 0 && bounds.height() > 0)
        stop()
        running = true; singleShot = once; stoppedAt = null; finished = done; releaseAllowed = true
        val token = generation
        val density = service.resources.displayMetrics.density
        val display = service.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val refreshRate = display?.refreshRate
        val sampleMs = if (android.os.Build.VERSION.SDK_INT >= 30 && refreshRate != null &&
            refreshRate.isFinite() && refreshRate > 0f) (1000 / refreshRate).toInt().coerceIn(1, 1000) else 100
        val x = bounds.left + bounds.width() * xPercent.coerceIn(10f, 90f) / 100f
        val centerY = bounds.top + bounds.height() * yPercent.coerceIn(10f, 90f) / 100f
        val span = bounds.height() * 0.8f
        val low = (centerY - span / 2).coerceIn(bounds.top + 1f, bounds.bottom - span - 1f)
        val high = low + span
        val direction = sign(speedDp)
        var motion: EdgeSwipeMotion? = null
        val tick = object : Runnable {
            override fun run() {
                if (!running || token != generation || inFlight) return
                if (!runCatching(stillTarget).getOrDefault(false)) { stop(); return }
                if (once) {
                    val timing = EdgeScrollTiming.plan(abs(speedDp), bounds.height() / density, sampleMs)
                    val distance = timing.distanceDp * density
                    val start = (centerY - distance / 2).coerceIn(bounds.top + 1f, bounds.bottom - distance - 1f)
                    val from = if (direction > 0) start else start + distance
                    val to = from + direction * distance
                    send(Path().apply { moveTo(x, from); lineTo(x, to) }, timing.durationMs, false, token, x, to)
                    return
                }
                val fresh = pointer == null
                val from = if (fresh) { if (direction > 0) low else high } else pointerY
                val available = if (direction > 0) high - from else from - low
                if (!fresh && available < 1f) { release(); return }
                val now = SystemClock.uptimeMillis()
                val clock = motion ?: EdgeSwipeMotion(abs(speedDp), now, sampleMs,
                    ViewConfiguration.get(service).scaledTouchSlop / density).also { motion = it }
                val distance = clock.distanceDp(now, available / density, fresh) * density
                val to = (from + direction * distance).coerceIn(low, high)
                send(Path().apply { moveTo(x, from); lineTo(x, to) }, clock.durationMs, true, token, x, to,
                    releaseOffset = -direction)
            }
        }
        next = tick
        resume()
    }

    private fun resume() {
        if (inFlight) return
        if (pointer != null && (!running || pointerOwner != generation)) {
            if (releaseAllowed) release() else { pointer = null; if (running) next?.let(main::post) }
        } else if (running) next?.let(main::post)
    }

    /** Identical coordinates produce no MOVE events. A one-pixel return clears recent velocity. */
    private fun release() {
        if (inFlight) return
        val held = pointer ?: return
        send(Path().apply {
            moveTo(pointerX, pointerY)
            lineTo(pointerX, pointerY + pointerReleaseOffset)
            lineTo(pointerX, pointerY)
        }, 160L, false, pointerOwner, pointerX, pointerY, held)
    }

    private fun send(path: Path, durationMs: Long, keepDown: Boolean, token: Int, x: Float, y: Float,
        previous: GestureDescription.StrokeDescription? = pointer, releaseOffset: Float = pointerReleaseOffset) {
        val stroke = previous?.continueStroke(path, 0, durationMs, keepDown)
            ?: GestureDescription.StrokeDescription(path, 0, durationMs, keepDown)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val id = ++flightSequence
        flight = id
        val watchdog = Runnable {
            if (flight != id) return@Runnable
            flight = null; pointer = if (keepDown) stroke else null
            pointerOwner = token; pointerX = x; pointerY = y
            pointerReleaseOffset = releaseOffset
            stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed))
        }
        main.postDelayed(watchdog, durationMs + 1000L)
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (flight != id) return
                flight = null; main.removeCallbacks(watchdog)
                pointer = if (keepDown) stroke else null
                pointerOwner = token; pointerX = x; pointerY = y
                pointerReleaseOffset = releaseOffset
                if (running && token == generation && singleShot) stop(completed = true)
                else resume()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (flight != id) return
                flight = null; pointer = null; main.removeCallbacks(watchdog)
                if (running && token == generation) stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed), releasePointer = false)
                else resume()
            }
        }
        try {
            if (service.dispatchGesture(gesture, callback, main)) return
        } catch (_: Exception) { /* Report through the same completion path. */ }
        flight = null; pointer = null; main.removeCallbacks(watchdog)
        stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed), releasePointer = false)
    }
}
