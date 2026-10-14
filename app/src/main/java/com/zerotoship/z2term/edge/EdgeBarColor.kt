package com.zerotoship.z2term.edge

/**
 * Fixed colours for the edge bar. `auto`, `white` and `black` stay with the runtime, which paints
 * them as a black/white pair; everything here is a single fill whose outline is chosen by contrast.
 * No Android types: the panel validation that uses this runs in plain JVM tests.
 */
internal object EdgeBarColor {
    private val presets = linkedMapOf(
        "gray" to 0xFF9E9E9E.toInt(), "red" to 0xFFE53935.toInt(), "orange" to 0xFFFB8C00.toInt(),
        "yellow" to 0xFFFDD835.toInt(), "green" to 0xFF43A047.toInt(), "cyan" to 0xFF00ACC1.toInt(),
        "blue" to 0xFF1E88E5.toInt(), "purple" to 0xFF8E24AA.toInt(), "pink" to 0xFFD81B60.toInt(),
    )
    private val hex = Regex("#[0-9A-Fa-f]{6}")

    /** Values offered by the editor, in display order. */
    val names: List<String> = listOf("auto", "white", "black") + presets.keys

    fun valid(value: String): Boolean = value.isEmpty() || value in names || hex.matches(value)

    /** The fill for a preset or `#RRGGBB`; null for auto/white/black. */
    fun fill(value: String?): Int? = value?.let { presets[it] }
        ?: value?.takeIf { hex.matches(it) }?.let { 0xFF000000.toInt() or it.substring(1).toInt(16) }

    /** Black or white, whichever stays visible on [fill]. */
    fun ink(fill: Int): Int {
        fun channel(shift: Int): Double {
            val c = ((fill shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        return if (luminance > 0.4) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
}
