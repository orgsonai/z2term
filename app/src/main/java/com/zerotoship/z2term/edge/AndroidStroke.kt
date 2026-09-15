package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.zerotoship.z2term.automation.ActionDefinition
import com.zerotoship.z2term.automation.ActionGesture
import com.zerotoship.z2term.automation.ActionGesturePlan

/** Reserve the input stream until Android finishes, including a stopped continuation's release. */
internal class AndroidStroke(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private var flight: Any? = null
    val inFlight get() = flight != null

    fun dispatch(step: ActionDefinition.Step.Stroke, screen: ActionDefinition.Screen,
        stillTarget: () -> Boolean = { true }, done: (String?) -> Unit): () -> Unit {
        check(!inFlight) { "Wait for the previous gesture to finish" }
        val tracks = step.tracks(screen)
        if (step.path.isNotEmpty() || step.easing != "linear" || tracks.size > 1)
            return timed(ActionGesturePlan.create(tracks, screen), step.ms, stillTarget, done)
        val p = tracks.single()
        val path = Path().apply {
            moveTo(p.first().x, p.first().y)
            if (p.first().x != p.last().x || p.first().y != p.last().y) lineTo(p.last().x, p.last().y)
        }
        val builder = GestureDescription.Builder()
        repeat(step.taps) { tap ->
            builder.addStroke(GestureDescription.StrokeDescription(path, tap * (step.ms + step.gapMs), step.ms))
        }
        val gesture = builder.build()
        val token = Any()
        var deliver = true
        flight = token
        val deadline = Runnable {
            if (flight === token) {
                flight = null
                if (deliver) { deliver = false; done("Android did not confirm gesture completion") }
            }
        }
        fun finish(error: String?) {
            if (flight !== token) return
            flight = null
            main.removeCallbacks(deadline)
            if (deliver) { deliver = false; done(error) }
        }
        main.postDelayed(deadline, step.durationMs + 1500)
        try {
            if (!service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = finish(null)
                override fun onCancelled(gestureDescription: GestureDescription?) = finish("Android cancelled the gesture")
            }, main)) finish("Android rejected the gesture")
        } catch (e: Exception) { finish(e.message ?: "Gesture failed") }
        return { deliver = false }
    }

    /** Queue a short portion of a continuous touch. Waiting for every completion before sending
     * the next segment adds a pause at every point and destroys flick velocity.
     * Stationary gaps are scheduled near their end; stopping releases the last queued positions.
     * Android's continuation queue follows the preceding event time, preserving each interval. */
    private fun timed(plan: List<ActionGesturePlan.Segment>, durationMs: Long,
        stillTarget: () -> Boolean, done: (String?) -> Unit): () -> Unit {
        val token = Any()
        flight = token
        val startedAt = SystemClock.uptimeMillis()
        var next = 0
        var pending = 0
        var lastEndAt = startedAt
        var previous = emptyMap<Int, GestureDescription.StrokeDescription>()
        var endpoint = emptyMap<Int, ActionGesture.Point>()
        var deliver = true
        var stopping = false
        var stopError: String? = null
        lateinit var pump: Runnable
        lateinit var deadline: Runnable
        fun finish(error: String?) {
            if (flight !== token) return
            flight = null
            main.removeCallbacks(pump); main.removeCallbacks(deadline)
            if (deliver) { deliver = false; done(error) }
        }
        fun stop(error: String?) {
            if (flight !== token || stopping) return
            stopping = true; stopError = error
            main.removeCallbacks(pump)
            if (previous.isEmpty()) { finish(error); return }
            main.removeCallbacks(deadline)
            main.postDelayed(deadline, (lastEndAt - SystemClock.uptimeMillis()).coerceAtLeast(0) + 1700)
            // The terminal segment already contains UP; wait for it without sending another touch.
            if (previous.values.none { it.willContinue() }) return
            try {
                val builder = GestureDescription.Builder()
                previous.filterValues { it.willContinue() }.forEach { (finger, stroke) ->
                    val p = endpoint.getValue(finger)
                    builder.addStroke(stroke.continueStroke(Path().apply { moveTo(p.x, p.y) }, 0, 1, false))
                }
                if (!service.dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) = finish(stopError)
                    override fun onCancelled(gestureDescription: GestureDescription?) = finish(stopError ?: "Android cancelled the gesture")
                }, main)) finish(error ?: "Android rejected touch release")
            } catch (e: Exception) { finish(error ?: e.message ?: "Touch release failed") }
        }
        deadline = Runnable {
            if (stopping) finish(stopError ?: "Android did not confirm touch release")
            else stop("Android did not confirm gesture completion")
        }
        pump = Runnable {
            if (flight === token && !stopping) {
                if (!runCatching(stillTarget).getOrDefault(false)) stop("Target window changed")
                else {
                    main.removeCallbacks(pump)
                    while (next < plan.size && flight === token && !stopping) {
                        val segment = plan[next]
                        val now = SystemClock.uptimeMillis()
                        // At most about 120 ms of movement is queued, plus Android delivery latency.
                        if (!segment.initial && startedAt + segment.endMs > now + 120) {
                            main.postAtTime(pump, startedAt + segment.endMs - 120)
                            break
                        }
                        val index = next
                        val last = index == plan.lastIndex
                        val reference = if (pending > 0) lastEndAt else now
                        val delay = (startedAt + segment.startMs - reference).coerceAtLeast(0)
                        try {
                            val strokes = segment.parts.associate { part ->
                                val path = Path().apply {
                                    moveTo(part.points.first().x, part.points.first().y)
                                    part.points.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                val start = (startedAt + part.startMs - reference).coerceAtLeast(0)
                                part.id to if (segment.initial)
                                    GestureDescription.StrokeDescription(path, start, part.durationMs, part.continues)
                                else previous.getValue(part.id).continueStroke(path, start, part.durationMs, part.continues)
                            }
                            val builder = GestureDescription.Builder()
                            strokes.values.forEach { builder.addStroke(it) }
                            val accepted = service.dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
                                override fun onCompleted(gestureDescription: GestureDescription?) {
                                    if (flight !== token) return
                                    pending--
                                    if (last) finish(if (stopping) stopError else null)
                                    else if (!stopping) main.post(pump)
                                }
                                override fun onCancelled(gestureDescription: GestureDescription?) {
                                    // Android has already cancelled the pointer stream (e.g. a physical touch).
                                    finish(if (stopping) stopError else "Android cancelled the gesture")
                                }
                            }, main)
                            if (!accepted) { stop("Android rejected the gesture"); break }
                            pending++; next++
                            previous = strokes; endpoint = segment.parts.associate { it.id to it.points.last() }
                            lastEndAt = reference + delay + segment.endMs - segment.startMs
                        } catch (e: Exception) { stop(e.message ?: "Gesture failed"); break }
                    }
                }
            }
        }
        main.postDelayed(deadline, durationMs + 2000)
        pump.run()
        return { deliver = false; stop(null) }
    }
}
