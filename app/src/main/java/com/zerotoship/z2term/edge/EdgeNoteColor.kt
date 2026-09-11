package com.zerotoship.z2term.edge

/** Opaque RGB only; an empty field inherits the existing theme. */
internal object EdgeNoteColor {
    private val rgb = Regex("#[0-9a-fA-F]{6}")
    fun parse(value: String?): Int? {
        if (value == null || !rgb.matches(value)) return null
        return (0xff000000L or value.substring(1).toLong(16)).toInt()
    }
}
