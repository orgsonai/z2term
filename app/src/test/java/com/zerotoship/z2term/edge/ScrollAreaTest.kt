package com.zerotoship.z2term.edge

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollAreaTest {
    private val app = ScrollArea(0, 80, 1080, 2400)

    @Test fun fullWindowExcludesDockedKeyboard() {
        assertEquals(app.copy(bottom = 1400), app.aboveKeyboard(ScrollArea(0, 1400, 1080, 2400)))
    }
    @Test fun alreadyResizedWindowKeepsItsBounds() {
        val resized = app.copy(bottom = 1400)
        assertEquals(resized, resized.aboveKeyboard(ScrollArea(0, 1400, 1080, 2400)))
    }
    @Test fun floatingKeyboardCannotIntersectTheStroke() {
        assertEquals(app.copy(bottom = 900), app.aboveKeyboard(ScrollArea(500, 900, 1050, 1600)))
    }
    @Test fun keyboardInAdjacentWindowDoesNotShrinkTarget() {
        val narrow = app.copy(right = 500)
        assertEquals(narrow, narrow.aboveKeyboard(ScrollArea(500, 1400, 1080, 2400)))
    }
    @Test fun fullscreenKeyboardLeavesNoAreaToScroll() {
        assertEquals(app.copy(bottom = app.top), app.aboveKeyboard(ScrollArea(0, 0, 1080, 2400)))
    }
}
