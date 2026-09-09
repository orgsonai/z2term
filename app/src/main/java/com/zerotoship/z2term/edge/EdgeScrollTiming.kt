package com.zerotoship.z2term.edge

import kotlin.math.ceil

/** Never shorten a swipe past Android's MOVE sampling interval: DOWN/UP alone is a tap. */
internal object EdgeScrollTiming {
    data class Timing(val distanceDp: Float, val durationMs: Long, val pauseMs: Long)

    fun plan(speedDp: Float, heightDp: Float, sampleMs: Int = 16): Timing {
        require(speedDp.isFinite() && speedDp > 0 && heightDp.isFinite() && heightDp >= 96)
        require(sampleMs in 1..1000)
        val minimumDuration = sampleMs * 3L + 1L
        val distance = (speedDp * 0.12f).coerceAtLeast(32f).coerceAtMost(heightDp * 0.8f)
        val period = ceil(distance.toDouble() / speedDp.toDouble() * 1000.0).toLong().coerceAtLeast(1)
        // Three intermediate MOVE samples, with the final one just before UP at high speed.
        // A short path whose duration is below sampleMs produces only DOWN/UP on Android.
        val duration = period.coerceIn(minimumDuration, maxOf(120L, minimumDuration))
        return Timing(distance, duration, (period - duration).coerceAtLeast(0))
    }
}
