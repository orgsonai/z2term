package com.zerotoship.z2term.workspace

import org.junit.Assert.*
import org.junit.Test

class SessionLayoutTest {
    private val sessions = setOf("shell", "desktop", "other")
    @Test fun focusingEitherPaneKeepsBothSessionsAndRatio() {
        val layout = SessionLayout("shell", "desktop", horizontal = true, ratio = .7f)
        val focused = layout.select("desktop", sessions)
        assertEquals(layout.copy(focused = 1), focused)
        assertEquals(layout, focused.select("shell", sessions))
    }
    @Test fun newTabReplacesOnlyFocusedPane() {
        val layout = SessionLayout("shell", "desktop", focused = 1)
        assertEquals(layout.copy(second = "other"), layout.select("other", sessions))
        assertEquals(layout.copy(first = "other", focused = 0), layout.copy(focused = 0).select("other", sessions))
    }
    @Test fun closedOrDuplicateSessionRestoresSingleActivePane() {
        assertEquals(SessionLayout("shell"), SessionLayout("shell", "desktop").select("shell", setOf("shell")))
        assertEquals(SessionLayout("shell"), SessionLayout("shell", "shell").select("shell", sessions))
        assertEquals(SessionLayout("other"), SessionLayout("shell").select("other", sessions))
    }
    @Test fun dividerNeverHidesEitherPaneOrAllowsInvalidWeights() {
        assertEquals(.2f, SessionLayout().resize(-3f).ratio)
        assertEquals(.8f, SessionLayout().resize(3f).ratio)
        assertEquals(.5f, SessionLayout().resize(Float.NaN).ratio)
        assertEquals(.5f, SessionLayout().resize(Float.POSITIVE_INFINITY).ratio)
    }
}
