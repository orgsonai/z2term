package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeHandleGestureTest {
    @Test fun directionLocksUntilTheNextTouch() {
        val gesture = EdgeHandleGesture(8f)
        assertEquals(EdgeHandleGesture.Kind.TAP, gesture.move(2f, -8f, false))
        assertEquals(EdgeHandleGesture.Kind.UP, gesture.move(2f, -9f, false))
        assertEquals(EdgeHandleGesture.Kind.UP, gesture.move(80f, 0f, false))
        gesture.reset()
        assertEquals(EdgeHandleGesture.Kind.INWARD, gesture.move(-20f, 1f, true))
        assertEquals(EdgeHandleGesture.Kind.INWARD, gesture.move(0f, -80f, true))
    }

    @Test fun horizontalDirectionIsRelativeToTheHandleSide() {
        val left = EdgeHandleGesture(8f)
        val right = EdgeHandleGesture(8f)
        assertEquals(EdgeHandleGesture.Kind.INWARD, left.move(20f, 0f, false))
        assertEquals(EdgeHandleGesture.Kind.OUTWARD, right.move(20f, 0f, true))
    }

    @Test fun variableSpeedHasADeadZoneAndIsBoundedInBothDirections() {
        fun speed(dy: Float) = EdgeHandleGesture.scrollSpeed(dy, 8f, 600, true)
        assertEquals(0f, speed(8f), 0f)
        assertTrue(speed(40f) < speed(100f))
        assertEquals(600f, speed(10000f), 0f)
        assertEquals(-600f, speed(-10000f), 0f)
        assertEquals(-speed(90f), speed(-90f), 0f)
        assertEquals(0f, speed(0f), 0f)
    }

    @Test fun widerRangeSpreadsSpeedAcrossTheConfiguredDistance() {
        fun speed(dy: Float) = EdgeHandleGesture.scrollSpeed(dy, 8f, 40000, true, 640f)
        assertEquals(0f, speed(8f), 0f)
        assertEquals(62.5f, speed(9f), 0f)
        assertEquals(2500f, speed(48f), 0f)
        assertEquals(10000f, speed(168f), 0f)
        assertEquals(40000f, speed(648f), 0f)
        assertEquals(40000f, speed(1000f), 0f)
        assertEquals(-10000f, speed(-168f), 0f)
        assertEquals(10000f, EdgeHandleGesture.scrollSpeed(40f, 0f, 40000, true), 0f)
        assertEquals(40000f, EdgeHandleGesture.scrollSpeed(40f, 0f, 40000, false, 640f), 0f)
    }

    @Test fun fixedSpeedIgnoresDistanceButPreservesDirectionAndDeadZone() {
        assertEquals(500f, EdgeHandleGesture.scrollSpeed(20f, 8f, 500, false), 0f)
        assertEquals(-500f, EdgeHandleGesture.scrollSpeed(-200f, 8f, 500, false), 0f)
        assertEquals(0f, EdgeHandleGesture.scrollSpeed(5f, 8f, 500, false), 0f)
    }
}
