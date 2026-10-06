package com.zerotoship.z2term.edge

import kotlin.math.abs
import kotlin.math.floor

/**
 * Plans one segment of a continuous vertical swipe that never lifts the last pointer. When a pointer
 * reaches the end of the range, the next one lands at the start while it still moves, and it lifts a
 * millisecond later. The view keeps one unbroken touch, so it neither stops nor flings at the joint.
 */
internal object EdgeSwipeRelay {
    /** [continues]: the stroke continues the pointer held from the previous segment. */
    data class Stroke(val startMs: Long, val durationMs: Long, val from: Float, val to: Float,
        val keepDown: Boolean, val continues: Boolean)
    data class Plan(val strokes: List<Stroke>, val pointersDown: Int)

    /** MotionEventInjector hands out pointer ids 0..9 and reuses them only once all are up. */
    const val MAX_POINTERS = 10

    /**
     * [y]: where the held pointer is, or null to land a new one at [begin]. [velocity]: px per ms,
     * signed by direction. [pointersDown]: pointers landed since all were last up, the held one included.
     */
    fun plan(y: Float?, begin: Float, end: Float, velocity: Float, segmentMs: Long, pointersDown: Int): Plan {
        require(velocity.isFinite() && velocity != 0f && segmentMs >= 2)
        val strokes = mutableListOf<Stroke>()
        var continues = y != null
        var at = y ?: begin
        var t = 0L
        var down = if (y == null) 1 else pointersDown
        val speed = abs(velocity)
        while (true) {
            // A continuation's first sample may hold only continued pointers: land after time 0.
            val land = floor(t + abs(end - at) / speed).toLong().coerceAtLeast(t + 1)
            if (land + 1 >= segmentMs) {
                strokes += Stroke(t, segmentMs - t, at, at + velocity * (segmentMs - t), true, continues)
                return Plan(strokes, down)
            }
            if (down < MAX_POINTERS) {
                // Lift a millisecond after the next pointer lands, moving with it, so the view never
                // sees zero pointers and the distance between the two never changes.
                strokes += Stroke(t, land + 1 - t, at, at + velocity * (land + 1 - t), false, continues)
                t = land; down++
            } else {
                // Out of pointer ids: lift, and land again a millisecond later in the same gesture.
                // The view catches its own fling before it moves, without waiting for a callback.
                strokes += Stroke(t, land - t, at, at + velocity * (land - t), false, continues)
                t = land + 1; down = 1
            }
            continues = false; at = begin
        }
    }
}
