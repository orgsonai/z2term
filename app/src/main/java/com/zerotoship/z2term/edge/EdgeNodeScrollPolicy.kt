package com.zerotoship.z2term.edge

/** Directional actions disambiguate a vertical list from the pager containing it. */
internal object EdgeNodeScrollPolicy {
    enum class Action { UP, DOWN, BACKWARD, FORWARD }
    data class Actions(val up: Boolean, val down: Boolean, val left: Boolean, val right: Boolean,
        val page: Boolean, val backward: Boolean, val forward: Boolean)

    fun action(actions: Actions, className: String, rows: Int?, columns: Int?, speedDp: Float): Action? {
        if (speedDp > 0 && actions.up) return Action.UP
        if (speedDp < 0 && actions.down) return Action.DOWN
        // Do not use FORWARD/BACKWARD to bypass a missing vertical action at the end of a list.
        if (actions.up || actions.down || actions.left || actions.right || actions.page) return null
        if (className.contains("ViewPager") || className.contains("Horizontal")) return null
        val vertical = className in setOf("android.widget.ScrollView", "android.widget.ListView",
            "android.widget.ExpandableListView", "android.webkit.WebView") ||
            (rows != null && columns != null && rows > 1 && columns <= 1)
        if (!vertical) return null
        return if (speedDp > 0 && actions.backward) Action.BACKWARD
            else if (speedDp < 0 && actions.forward) Action.FORWARD else null
    }

    /** Unsupported amount arguments must not turn a whole-page action into a fractional estimate. */
    fun periodMs(speedDp: Float, distanceDp: Float, granular: Boolean): Long =
        if (granular) 40L else kotlin.math.ceil(distanceDp / kotlin.math.abs(speedDp) * 1000.0)
            .toLong().coerceAtLeast(250L)
}
