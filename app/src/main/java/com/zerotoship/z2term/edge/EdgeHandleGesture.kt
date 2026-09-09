package com.zerotoship.z2term.edge

import kotlin.math.abs
import kotlin.math.sign

/** One owner per touch stream. Returning to the origin never turns a swipe into a tap. */
internal class EdgeHandleGesture(private val slop: Float) {
    enum class Kind { TAP, INWARD, OUTWARD, UP, DOWN }
    var kind = Kind.TAP
        private set

    fun reset() { kind = Kind.TAP }

    fun move(dx: Float, dy: Float, right: Boolean): Kind {
        if (kind == Kind.TAP && maxOf(abs(dx), abs(dy)) > slop) {
            kind = if (abs(dy) > abs(dx)) {
                if (dy < 0) Kind.UP else Kind.DOWN
            } else if (dx * (if (right) -1 else 1) > 0) Kind.INWARD else Kind.OUTWARD
        }
        return kind
    }

    companion object {
        /** Signed finger velocity in dp/s, proportional to displacement beyond touch slop. */
        fun scrollSpeed(displacementDp: Float, slopDp: Float, maximum: Int, variable: Boolean, rangeDp: Float = 160f): Float {
            require(rangeDp.isFinite() && rangeDp in 32f..2000f) { "gesture-range: 32–2000 dp" }
            val distance = (abs(displacementDp) - slopDp).coerceAtLeast(0f)
            if (distance == 0f) return 0f
            val fraction = if (variable) (distance / rangeDp).coerceIn(0f, 1f) else 1f
            return sign(displacementDp) * maximum.coerceIn(50, 40000) * fraction
        }
    }
}
