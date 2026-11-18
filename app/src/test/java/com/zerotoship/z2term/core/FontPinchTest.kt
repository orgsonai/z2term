package com.zerotoship.z2term.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FontPinchTest {

    @Test
    fun eachTabScalesFromItsOwnSize() {
        assertEquals(15f, scaledFontSize(10f, 1.5f))
        assertEquals(24f, scaledFontSize(16f, 1.5f))
    }

    @Test
    fun sizesAreQuantizedToHalfPoints() {
        assertEquals(13.5f, scaledFontSize(13f, 1.03f))
    }

    @Test
    fun differentSizesMeetAtTheLimits() {
        assertEquals(4f, scaledFontSize(10f, 0.1f))
        assertEquals(4f, scaledFontSize(20f, 0.1f))
        assertEquals(32f, scaledFontSize(10f, 8f))
        assertEquals(32f, scaledFontSize(20f, 8f))
    }
}
