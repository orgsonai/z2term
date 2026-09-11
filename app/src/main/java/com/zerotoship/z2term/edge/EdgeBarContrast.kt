package com.zerotoship.z2term.edge

/** Linear luminance with a dead band so nearly-grey backgrounds do not flicker. */
internal object EdgeBarContrast {
    fun useBlack(luminance: Double, previous: Boolean?): Boolean {
        if (!luminance.isFinite() || luminance !in 0.0..1.0) return previous ?: false
        return when (previous) {
            true -> luminance >= 0.15
            false -> luminance > 0.21
            null -> luminance > 0.179
        }
    }
}
