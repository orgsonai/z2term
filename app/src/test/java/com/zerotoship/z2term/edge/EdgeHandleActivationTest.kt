package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeHandleActivationTest {
    @Test fun swipeModeIgnoresTapAndOutwardOrVerticalMotion() {
        assertFalse(EdgeHandleActivation.opens("swipe", true, false, 0f, 0f, 8))
        assertFalse(EdgeHandleActivation.opens("swipe", true, true, 50f, 0f, 8))
        assertFalse(EdgeHandleActivation.opens("swipe", true, true, -10f, 50f, 8))
        assertTrue(EdgeHandleActivation.opens("swipe", true, true, -50f, 2f, 8))
        assertTrue(EdgeHandleActivation.opens("swipe", false, true, 50f, 2f, 8))
    }

    @Test fun tapModeDoesNotOpenAfterDraggingBackToOrigin() {
        assertTrue(EdgeHandleActivation.opens("tap", true, false, 2f, 1f, 8))
        assertFalse(EdgeHandleActivation.opens("tap", true, true, 0f, 0f, 8))
        assertFalse(EdgeHandleActivation.opens("tap", true, false, -50f, 0f, 8))
        assertFalse(EdgeHandleActivation.opens("tap", true, true, -50f, 0f, 8))
    }

    @Test fun bothModeAcceptsEitherButStillRejectsVerticalMotion() {
        assertTrue(EdgeHandleActivation.opens("both", true, false, 0f, 0f, 8))
        assertTrue(EdgeHandleActivation.opens("both", true, true, -50f, 0f, 8))
        assertFalse(EdgeHandleActivation.opens("both", true, true, 0f, 50f, 8))
        assertFalse(EdgeHandleActivation.opens("swipe", true, true, -8f, 0f, 8))
    }
}
