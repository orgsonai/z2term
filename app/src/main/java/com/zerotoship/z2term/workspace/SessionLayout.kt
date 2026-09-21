package com.zerotoship.z2term.workspace

/** Session identity is independent of the viewport that currently owns its dimensions. */
internal data class SessionLayout(
    val first: String? = null,
    val second: String? = null,
    val focused: Int = 0,
    val horizontal: Boolean = false,
    val ratio: Float = 0.5f,
) {
    fun select(active: String, available: Set<String>): SessionLayout {
        if (first !in available || second !in available || first == second) return SessionLayout(first = active)
        return when (active) {
            first -> copy(focused = 0)
            second -> copy(focused = 1)
            else -> if (focused == 0) copy(first = active) else copy(second = active)
        }
    }
    fun resize(value: Float) = copy(ratio = if (value.isFinite()) value.coerceIn(0.2f, 0.8f) else 0.5f)
}
