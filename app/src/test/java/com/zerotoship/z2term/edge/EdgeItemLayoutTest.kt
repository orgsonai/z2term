package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeItemLayoutTest {
    @Test fun dimensionsAndPositionCanBeSavedAlongsideOldFields() {
        EdgeStore.validateItem(mapOf("type" to "note", "file" to "draft.txt", "width" to "50%",
            "height" to "160", "at" to "100%,0%", "align" to "end"))
        EdgeStore.validatePanel(mapOf("flow" to "free"))
        assertEquals(150, EdgeStore.dimensionPixels("50%", 300, 2f))
        assertEquals(320, EdgeStore.dimensionPixels("160", 600, 2f))
    }
    @Test fun edgeCoordinatesAccountForTheItemSize() {
        val point = EdgeItemLayout.point("100%,50%")!!
        assertEquals(220, EdgeItemLayout.offset(point.first, 300, 80))
        assertEquals(200, EdgeItemLayout.offset(point.second, 600, 200))
        assertEquals(0, EdgeItemLayout.offset(1f, 100, 200))
        assertNull(EdgeItemLayout.point(""))
    }
    @Test fun invalidDimensionsAndCoordinatesAreRejectedBeforeSaving() {
        for ((key, value) in listOf("width" to "0", "height" to "101%", "height" to "NaN",
            "at" to "0,50", "at" to "50%,101%", "at" to "NaN%,0%", "align" to "outside")) {
            assertThrows(IllegalArgumentException::class.java) { EdgeStore.validateItem(mapOf(key to value)) }
        }
    }
}
