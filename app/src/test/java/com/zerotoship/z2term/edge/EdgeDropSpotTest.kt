package com.zerotoship.z2term.edge

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeDropSpotTest {
    private val rows = listOf(listOf("a", "b", "c"), listOf("n"))

    @Test fun edgesStartNewRowsAndTheMiddleJoinsTheRow() {
        // 100 x 60 cell, band capped at 40: the edge band is 18 (30% of 60).
        assertEquals(EdgeDropSpot.ABOVE, EdgeDropSpot.at(50f, 10f, 100, 60, 40f))
        assertEquals(EdgeDropSpot.BELOW, EdgeDropSpot.at(50f, 50f, 100, 60, 40f))
        assertEquals(EdgeDropSpot.BEFORE, EdgeDropSpot.at(20f, 30f, 100, 60, 40f))
        assertEquals(EdgeDropSpot.AFTER, EdgeDropSpot.at(80f, 30f, 100, 60, 40f))
        // A tall item keeps the band at its cap, so most of it still places beside.
        assertEquals(EdgeDropSpot.BEFORE, EdgeDropSpot.at(20f, 60f, 100, 600, 40f))
    }

    @Test fun applyMovesBetweenRowsOrBeside() {
        assertEquals(listOf(listOf("a", "c"), listOf("b"), listOf("n")), EdgeDropSpot.BELOW.apply(rows, "b", "a"))
        assertEquals(listOf(listOf("c"), listOf("a", "b"), listOf("n")), EdgeDropSpot.ABOVE.apply(rows, "c", "a"))
        assertEquals(listOf(listOf("a", "b", "c"), listOf("n")), EdgeDropSpot.ABOVE.apply(rows, "a", "a"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "n")), EdgeDropSpot.BEFORE.apply(rows, "c", "n"))
        assertEquals(listOf(listOf("a", "b", "c", "n")), EdgeDropSpot.AFTER.apply(rows, "n", "c"))
    }
}
