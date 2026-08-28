package com.zerotoship.z2term.gui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiCursorTest {
    @Test
    fun initializesOnceAtCenterAndKeepsPositionAcrossViewRecreation() {
        val cursor = GuiCursor()

        val initial = cursor.fitTo(800, 600)!!
        assertEquals(400f, initial.x)
        assertEquals(300f, initial.y)

        cursor.moveBy(50f, -25f, 800, 600)
        val afterRecreation = cursor.fitTo(800, 600)!!
        assertEquals(450f, afterRecreation.x)
        assertEquals(275f, afterRecreation.y)
    }

    @Test
    fun clampsSavedPositionWhenFramebufferShrinks() {
        val cursor = GuiCursor()
        cursor.fitTo(800, 600)
        cursor.moveBy(999f, 999f, 800, 600)

        val resized = cursor.fitTo(320, 200)!!

        assertEquals(319f, resized.x)
        assertEquals(199f, resized.y)
    }

    @Test
    fun exposesPressedStateForDrawing() {
        val cursor = GuiCursor()
        assertFalse(cursor.snapshot().pressed)

        cursor.setPressed(true)

        assertTrue(cursor.snapshot().pressed)
    }

    @Test
    fun defaultsToRelativeAndTogglesAbsoluteMode() {
        val cursor = GuiCursor()
        assertEquals(GuiCursor.Mode.RELATIVE, cursor.snapshot().mode)

        assertEquals(GuiCursor.Mode.ABSOLUTE, cursor.toggleMode())
        assertEquals(GuiCursor.Mode.ABSOLUTE, cursor.snapshot().mode)
    }

    @Test
    fun absoluteMoveUsesFramebufferCoordinatesAndClamps() {
        val cursor = GuiCursor()

        val moved = cursor.moveTo(700f, -10f, 640, 480)!!

        assertEquals(639f, moved.x)
        assertEquals(0f, moved.y)
    }
}
