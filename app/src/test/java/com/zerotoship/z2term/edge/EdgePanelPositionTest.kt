package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgePanelPositionTest {
    @Test fun customPositionOverridesPlacementAndCanBeCleared() {
        assertEquals(0f to 1f, EdgePanelPosition.fractions(mapOf("place" to "center", "at" to "0%,100%")))
        assertEquals(0.5f to 0.5f, EdgePanelPosition.fractions(mapOf("place" to "center", "at" to "")))
    }

    @Test fun followingAHandleUsesItsSavedPosition() {
        assertEquals(0f to 0.75f, EdgePanelPosition.fractions(mapOf("handle" to "bar", "side" to "left", "offset" to "75")))
        assertEquals(0.2f to 0.6f, EdgePanelPosition.fractions(mapOf("handle" to "button", "x" to "20", "y" to "60")))
    }

}
