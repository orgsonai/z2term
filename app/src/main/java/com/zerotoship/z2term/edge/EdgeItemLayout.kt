package com.zerotoship.z2term.edge

/** Item dimensions use the current column width and panel height as their percentage bases. */
internal object EdgeItemLayout {
    fun validate(fields: Map<String, String>) {
        listOf("width", "height").forEach { key -> fields[key]?.takeIf { it.isNotBlank() }?.let { EdgeStore.dimension(it) } }
        require(fields["align"].orEmpty() in setOf("", "start", "center", "end")) { "align: start|center|end" }
        point(fields["at"].orEmpty())
    }
    fun point(raw: String): Pair<Float, Float>? {
        if (raw.isBlank()) return null
        val parts = raw.split(',')
        require(parts.size == 2 && parts.all {
            it.endsWith('%') && it.removeSuffix("%").toFloatOrNull()?.let { n -> n.isFinite() && n in 0f..100f } == true
        }) { "at: X%,Y% (0–100)" }
        return parts[0].removeSuffix("%").toFloat() / 100f to parts[1].removeSuffix("%").toFloat() / 100f
    }
    fun offset(fraction: Float, area: Int, size: Int): Int = ((area - size).coerceAtLeast(0) * fraction).toInt()
}
