package com.zerotoship.z2term.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyLayoutWidthFormattingTest {
    @Test
    fun sliderValueIsStoredAndDisplayedInTenths() {
        assertEquals(1.3f, snapKeyWidthToTenth(1.3000001f), 0.0001f)
        assertEquals("1.3", formatKeyWidthSliderValue(1.3000001f))
        assertEquals("0.2", formatKeyWidthSliderValue(0.20000002f))
        assertEquals("5.0", formatKeyWidthSliderValue(4.9999995f))
    }

    @Test
    fun freeformEditorValueDoesNotExposeFloatNoise() {
        assertEquals("1.3", formatEditorFloat(1.3000001f))
        assertEquals("1", formatEditorFloat(1.0000001f))
        assertEquals("0.333", formatEditorFloat(0.33333334f))
    }
}
