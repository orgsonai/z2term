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
 * Range ends hand off by a fling; stops release without one.
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
    private var releaseAllowed = true
    private var touchSlopPx = 0f
    /** x, y and direction of a lift whose fling no later touch has caught yet. */
    private var handOffAt: FloatArray? = null

    fun recentlyRunning(): Boolean = running || stoppedAt?.let {
        SystemClock.uptimeMillis() - it < 500
    } == true

    fun stop(error: String? = null, completed: Boolean = false, releasePointer: Boolean = true) {
        if (running) stoppedAt = SystemClock.uptimeMillis()
        generation++
        running = false
        next = null
        releaseAllowed = releasePointer
        val callback = finished; finished = null
        val result = error ?: if (singleShot && !completed)
            service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed) else null
        singleShot = false
        if (!releasePointer) { pointer = null; handOffAt = null }
        else if (pointer != null) release()
        else if (!inFlight) catchFling()
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
        touchSlopPx = ViewConfiguration.get(service).scaledTouchSlop.toFloat()
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
        val minimumMs = sampleMs * 3L + 1L
        val segmentMs = maxOf(80L, minimumMs)
        // The injector keeps the queued timing exactly, so a constant step is a constant speed.
        val speedPx = abs(speedDp) * density
        val stepPx = speedPx * segmentMs / 1000f
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
                send(Path().apply { moveTo(x, from); lineTo(x, to) }, timing.durationMs, false, token, x, to)
                return@next false
            }
            val fresh = pointer == null
            // A new stroke cancels anything still queued: wait for the previous lift to finish.
            if (fresh && inFlight) return@next false
            val from = if (fresh) { if (direction > 0) low else high } else pointerY
            val end = if (direction > 0) high else low
            val slop = if (fresh) touchSlopPx else 0f
            val distance = (stepPx + slop).coerceAtMost(abs(end - from))
            // A step cut short by the range end keeps the speed, not the duration. A fixed duration
            // capped the speed at range / segment once one step covered the whole range.
            val durationMs = if (distance >= stepPx + slop) segmentMs else
                kotlin.math.ceil((distance - slop).coerceAtLeast(0f) / speedPx * 1000f).toLong()
                    .coerceIn(minimumMs, segmentMs)
            val to = from + direction * distance
            // Lift at the end of the range while still moving. The view keeps scrolling by its
            // own fling until the next stroke's DOWN catches it, so the reset does not pause.
            val handOff = abs(end - to) < 1f
            send(Path().apply { moveTo(x, from); lineTo(x, to) }, durationMs, !handOff, token, x, to,
                releaseOffset = -direction, handOff = handOff)
            !handOff
        }
        pump()
    }

    /** Keep a few segments queued so a late callback does not leave the pointer still. */
    private fun pump() {
        if (!running) {
            if (!inFlight && releaseAllowed) catchFling()
            return
        }
        while (running && pending.size < AHEAD) {
            val more = next?.invoke() ?: return
            if (!more) return
        }
    }

    /** Identical coordinates produce no MOVE events. A one-pixel return clears recent velocity. */
    private fun release() {
        val held = pointer ?: return
        send(Path().apply {
            moveTo(pointerX, pointerY)
            lineTo(pointerX, pointerY + pointerReleaseOffset)
            lineTo(pointerX, pointerY)
        }, 160L, false, NO_OWNER, pointerX, pointerY, held)
    }

    /** A stop just after a hand-off leaves the view flinging. A short drag, never a tap, catches it. */
    private fun catchFling() {
        val (x, y, direction) = handOffAt ?: return
        val to = y + direction * (touchSlopPx + 2f)
        send(Path().apply { moveTo(x, y); lineTo(x, to) }, 50L, true, NO_OWNER, x, to, null, -direction)
        // Stopped: release the catching pointer right behind it.
        release()
    }

    /** [x], [y]: the exact end of [path]. A continuation must start at the injector's last point. */
    private fun send(path: Path, durationMs: Long, keepDown: Boolean, token: Int, x: Float, y: Float,
        previous: GestureDescription.StrokeDescription? = pointer, releaseOffset: Float = pointerReleaseOffset,
        handOff: Boolean = false) {
        val stroke = previous?.continueStroke(path, 0, durationMs, keepDown)
            ?: GestureDescription.StrokeDescription(path, 0, durationMs, keepDown)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val id = ++sequence
        pending.addLast(id)
        handOffAt = null
        pointer = if (keepDown) stroke else null
        pointerX = x; pointerY = y; pointerReleaseOffset = releaseOffset
        val now = SystemClock.uptimeMillis()
        scheduledUntil = maxOf(scheduledUntil, now) + durationMs
        val watchdog = Runnable {
            if (id !in pending) return@Runnable
            pending.clear(); pointer = null; handOffAt = null
            stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed), releasePointer = false)
        }
        main.postDelayed(watchdog, scheduledUntil - now + 1000L)
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (!pending.remove(id)) return
                main.removeCallbacks(watchdog)
                if (handOff && !inFlight && pointer == null) handOffAt = floatArrayOf(x, y, -releaseOffset)
                if (running && token == generation && singleShot) stop(completed = true)
                else pump()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (!pending.remove(id)) return
                main.removeCallbacks(watchdog)
                // Android cancels everything queued with it; nothing is held down any more.
                pointer = null; handOffAt = null
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
        /** Releases and catches belong to no scroll session; their results never stop a new one. */
        const val NO_OWNER = -1
    }
}
