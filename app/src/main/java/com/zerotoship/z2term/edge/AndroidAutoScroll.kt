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

/**
 * Continuations are queued ahead of the pointer. Android schedules a continuation dispatched before
 * the previous one finishes right after its last event, so the finger never pauses between segments.
 * At the end of the range a second pointer lands at the start while the first one still moves, and
 * the first one lifts right after. The view never sees the touch end, so it never stops or flings.
 */
internal class AndroidAutoScroll(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var sequence = 0L
    /** Dispatched sequences not yet reported, oldest first. */
    private val pending = ArrayDeque<Long>()
    val inFlight get() = pending.isNotEmpty()
    var running = false
        private set
    private var next: (() -> Boolean)? = null
    private var finished: ((String?) -> Unit)? = null
    private var stoppedAt: Long? = null
    private var singleShot = false
    /** The last dispatched stroke when it keeps the pointer down, and where it ends. */
    private var pointer: GestureDescription.StrokeDescription? = null
    private var pointerX = 0f
    private var pointerY = 0f
    private var pointerReleaseOffset = 1f
    private var scheduledUntil = 0L
    /** Pointers landed since every pointer was last up, the held one included. */
    private var pointersDown = 0

    fun recentlyRunning(): Boolean = running || stoppedAt?.let {
        SystemClock.uptimeMillis() - it < 500
    } == true

    fun stop(error: String? = null, completed: Boolean = false, releasePointer: Boolean = true) {
        if (running) stoppedAt = SystemClock.uptimeMillis()
        generation++
        running = false
        next = null
        val callback = finished; finished = null
        val result = error ?: if (singleShot && !completed)
            service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed) else null
        singleShot = false
        if (!releasePointer) pointer = null else release()
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
        running = true; singleShot = once; stoppedAt = null; finished = done
        val token = generation
        val density = service.resources.displayMetrics.density
        val touchSlopPx = ViewConfiguration.get(service).scaledTouchSlop.toFloat()
        val display = service.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val refreshRate = display?.refreshRate
        val sampleMs = if (android.os.Build.VERSION.SDK_INT >= 30 && refreshRate != null &&
            refreshRate.isFinite() && refreshRate > 0f) (1000 / refreshRate).toInt().coerceIn(1, 1000) else 100
        val x = bounds.left + bounds.width() * xPercent.coerceIn(10f, 90f) / 100f
        val centerY = bounds.top + bounds.height() * yPercent.coerceIn(10f, 90f) / 100f
        // Keep the pointer off the top and bottom bars. A browser shows its toolbar again while
        // scrolling up, and a downward stroke starting on that toolbar does not scroll the page.
        val span = bounds.height() * 0.6f
        val margin = bounds.height() * 0.15f
        val low = (centerY - span / 2).coerceIn(bounds.top + margin, bounds.bottom - margin - span)
        val high = low + span
        val direction = sign(speedDp)
        val segmentMs = maxOf(80L, sampleMs * 3L + 1L)
        // px per ms. Each pointer crosses the range in at least one sample, so at most two are down.
        val velocity = (abs(speedDp) * density / 1000f).coerceAtMost(span / sampleMs)
        var onceSent = false
        // Returns whether another segment may be queued behind this one now.
        next = next@{
            if (!running || token != generation) return@next false
            if (!runCatching(stillTarget).getOrDefault(false)) { stop(); return@next false }
            if (once) {
                if (onceSent || inFlight) return@next false
                onceSent = true
                val timing = EdgeScrollTiming.plan(abs(speedDp), bounds.height() / density, sampleMs)
                val distance = timing.distanceDp * density
                val start = (centerY - distance / 2).coerceIn(bounds.top + 1f, bounds.bottom - distance - 1f)
                val from = if (direction > 0) start else start + distance
                val to = from + direction * distance
                val stroke = GestureDescription.StrokeDescription(
                    Path().apply { moveTo(x, from); lineTo(x, to) }, 0, timing.durationMs, false)
                send(listOf(stroke), timing.durationMs, null, x, to, token = token)
                return@next false
            }
            val fresh = pointer == null
            // A new gesture cancels anything still queued: wait for the previous lift to finish.
            if (fresh && inFlight) return@next false
            val begin = if (direction > 0) low else high
            val end = if (direction > 0) high else low
            // The first segment also covers the touch slop, so the view starts dragging on time.
            val v = velocity + if (fresh) touchSlopPx / segmentMs else 0f
            val plan = EdgeSwipeRelay.plan(if (fresh) null else pointerY, begin, end, direction * v,
                segmentMs, pointersDown)
            pointersDown = plan.pointersDown
            val held = pointer
            val strokes = plan.strokes.map {
                val path = Path().apply { moveTo(x, it.from); lineTo(x, it.to) }
                (if (it.continues) held else null)?.continueStroke(path, it.startMs, it.durationMs, it.keepDown)
                    ?: GestureDescription.StrokeDescription(path, it.startMs, it.durationMs, it.keepDown)
            }
            send(strokes, segmentMs, strokes.last(), x, plan.strokes.last().to, releaseOffset = -direction, token = token)
            true
        }
        pump()
    }

    /** Keep a few segments queued so a late callback does not leave the pointer still. */
    private fun pump() {
        while (running && pending.size < AHEAD) {
            val more = next?.invoke() ?: return
            if (!more) return
        }
    }

    /** Identical coordinates produce no MOVE events. A one-pixel return clears recent velocity. */
    private fun release() {
        val held = pointer ?: return
        val path = Path().apply {
            moveTo(pointerX, pointerY)
            lineTo(pointerX, pointerY + pointerReleaseOffset)
            lineTo(pointerX, pointerY)
        }
        send(listOf(held.continueStroke(path, 0, 160L, false)), 160L, null, pointerX, pointerY, token = NO_OWNER)
    }

    /** [x], [y]: where [held] ends. A continuation must start at the injector's last point. */
    private fun send(strokes: List<GestureDescription.StrokeDescription>, durationMs: Long,
        held: GestureDescription.StrokeDescription?, x: Float, y: Float,
        releaseOffset: Float = pointerReleaseOffset, token: Int) {
        val gesture = GestureDescription.Builder().apply { strokes.forEach(::addStroke) }.build()
        val id = ++sequence
        pending.addLast(id)
        pointer = held
        pointerX = x; pointerY = y; pointerReleaseOffset = releaseOffset
        val now = SystemClock.uptimeMillis()
        scheduledUntil = maxOf(scheduledUntil, now) + durationMs
        val watchdog = Runnable {
            if (id !in pending) return@Runnable
            pending.clear(); pointer = null
            stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed), releasePointer = false)
        }
        main.postDelayed(watchdog, scheduledUntil - now + 1000L)
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (!pending.remove(id)) return
                main.removeCallbacks(watchdog)
                if (running && token == generation && singleShot) stop(completed = true)
                else pump()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (!pending.remove(id)) return
                main.removeCallbacks(watchdog)
                // Android cancels everything queued with it; nothing is held down any more.
                pointer = null
                if (running && token == generation) stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed), releasePointer = false)
                else pump()
            }
        }
        try {
            if (service.dispatchGesture(gesture, callback, main)) return
        } catch (_: Exception) { /* Report through the same completion path. */ }
        pending.remove(id); main.removeCallbacks(watchdog); pointer = null
        stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed), releasePointer = false)
    }

    private companion object {
        /** Segments queued ahead: a callback may arrive this many segments late without a pause. */
        const val AHEAD = 3
        /** Releases belong to no scroll session; their results never stop a new one. */
        const val NO_OWNER = -1
    }
}
