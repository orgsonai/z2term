package com.zerotoship.z2term.edge

/** Conservative rectangular viewport: never run a stroke through an overlapping keyboard. */
internal data class ScrollArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun aboveKeyboard(keyboard: ScrollArea): ScrollArea {
        if (keyboard.left >= keyboard.right || keyboard.top >= keyboard.bottom ||
            left >= keyboard.right || right <= keyboard.left ||
            top >= keyboard.bottom || bottom <= keyboard.top) return this
        return copy(bottom = maxOf(top, keyboard.top))
    }
}
