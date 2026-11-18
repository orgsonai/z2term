package com.zerotoship.z2term.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NoWrapViewTest {

    @Test
    fun wrapIsTheDefaultWidth() {
        assertEquals(50, ptyColumns(50, noWrap = false, altScreen = false))
    }

    @Test
    fun noWrapWidensTheShellButNotFullScreenPrograms() {
        assertEquals(NO_WRAP_COLS, ptyColumns(50, noWrap = true, altScreen = false))
        assertEquals(50, ptyColumns(50, noWrap = true, altScreen = true))
    }

    @Test
    fun screensWiderThanTheLimitKeepTheirWidth() {
        assertEquals(NO_WRAP_COLS + 40, ptyColumns(NO_WRAP_COLS + 40, noWrap = true, altScreen = false))
    }

    @Test
    fun scrollRangeIsZeroWhenContentFits() {
        assertEquals(0f, maxScrollX(50, 10f, 600f, 10f))
        assertEquals(1520f, maxScrollX(200, 10f, 500f, 10f))
    }

    @Test
    fun visibleCursorDoesNotMoveTheView() {
        assertEquals(100f, scrollXToShowColumn(100f, 30, 10f, 500f, 10f, 1520f))
    }

    @Test
    fun cursorPastTheRightEdgeBringsTheViewAlong() {
        assertEquals(160f, scrollXToShowColumn(0f, 60, 10f, 500f, 10f, 1520f))
    }

    @Test
    fun returningToTheLineStartShowsTheLeftEdge() {
        assertEquals(0f, scrollXToShowColumn(800f, 0, 10f, 500f, 10f, 1520f))
    }
}
