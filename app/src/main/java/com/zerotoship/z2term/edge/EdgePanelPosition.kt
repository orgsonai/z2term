package com.zerotoship.z2term.edge

/** Fractions of the remaining space, so even the 100% position stays inside the display. */
internal object EdgePanelPosition {
    fun fractions(fields: Map<String, String>): Pair<Float, Float> {
        fields["at"]?.takeIf { it.isNotBlank() }?.split(',')?.let { point ->
            return point[0].removeSuffix("%").toFloat() / 100f to point[1].removeSuffix("%").toFloat() / 100f
        }
        return when (fields["place"] ?: "handle") {
            "left" -> 0f to 0.5f
            "right" -> 1f to 0.5f
            "top" -> 0.5f to 0f
            "bottom" -> 0.5f to 1f
            "center" -> 0.5f to 0.5f
            else -> if (fields["handle"] == "button")
                (fields["x"]?.toFloatOrNull() ?: 85f) / 100f to (fields["y"]?.toFloatOrNull() ?: 30f) / 100f
            else (if (fields["side"] == "left") 0f else 1f) to (fields["offset"]?.toFloatOrNull() ?: 30f) / 100f
        }
    }
}
