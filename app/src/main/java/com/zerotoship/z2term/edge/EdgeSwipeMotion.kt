package com.zerotoship.z2term.edge

/** Travel owed by the wall clock, including callback and pointer-reset time. */
internal class EdgeSwipeMotion(private val speedDp: Float, private val startedAtMs: Long,
    sampleMs: Int, private val touchSlopDp: Float) {
    val durationMs = maxOf(80L, sampleMs * 3L + 1L)
    private var deliveredDp = 0.0
    init {
        require(speedDp.isFinite() && speedDp > 0 && sampleMs in 1..1000)
        require(touchSlopDp.isFinite() && touchSlopDp >= 0)
    }

    fun distanceDp(nowMs: Long, availableDp: Float, newPointer: Boolean): Float {
        val slop = if (newPointer) touchSlopDp else 0f
        val expected = speedDp.toDouble() * (nowMs - startedAtMs + durationMs).coerceAtLeast(0) / 1000.0
        val useful = (expected - deliveredDp).coerceAtLeast(0.0)
            .coerceAtMost((availableDp - slop).coerceAtLeast(0f).toDouble())
        deliveredDp += useful
        return (useful + slop).toFloat().coerceAtMost(availableDp)
    }
}
