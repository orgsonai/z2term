package com.zerotoship.z2term.gui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuiEventWatcherTest {
    @Test
    fun `OPEN event carries display and distro`() {
        assertEquals(GuiOpenEvent(3, "arch"), parseGuiOpenEvent("OPEN 3 arch"))
        assertEquals(GuiOpenEvent(1, "ubuntu-24.04"), parseGuiOpenEvent(" OPEN  1  ubuntu-24.04 "))
    }

    @Test
    fun `legacy OPEN event remains accepted`() {
        assertEquals(GuiOpenEvent(2, null), parseGuiOpenEvent("OPEN 2"))
    }

    @Test
    fun `invalid OPEN events are rejected`() {
        for (line in listOf("OPEN", "OPEN 0 arch", "OPEN x arch", "CLOSE 1 arch", "OPEN 1 ../arch", "OPEN 1 arch extra")) {
            assertNull(line, parseGuiOpenEvent(line))
        }
    }
}
