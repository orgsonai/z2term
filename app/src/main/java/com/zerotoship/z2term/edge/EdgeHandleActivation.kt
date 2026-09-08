package com.zerotoship.z2term.edge

import kotlin.math.abs

/** Moving along or away from an edge must not activate an inward-swipe handle. */
internal object EdgeHandleActivation {
    fun opens(mode: String, right: Boolean, moved: Boolean, dx: Float, dy: Float, slop: Int): Boolean {
        val tap = !moved && abs(dx) <= slop && abs(dy) <= slop
        val inward = dx * (if (right) -1 else 1) > slop && abs(dx) > abs(dy)
        return (tap && mode in setOf("tap", "both")) || (inward && mode in setOf("swipe", "both"))
    }
}
