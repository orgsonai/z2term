package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeScrollTimingTest {
    @Test fun highSpeedStillContainsMoveEventsBeforeUp() {
        for (sampleMs in listOf(8, 11, 16, 33, 100)) {
            for (height in listOf(96f, 400f, 800f, 1200f)) {
                val fast = EdgeScrollTiming.plan(40000f, height, sampleMs)
                val intermediateMoves = (fast.durationMs - 1) / sampleMs
                assertTrue(intermediateMoves >= 3)
                val lastMoveFraction = intermediateMoves * sampleMs.toFloat() / fast.durationMs
                assertTrue(lastMoveFraction >= 0.95f)
                assertTrue(fast.distanceDp <= height * 0.8f)
                assertEquals(0L, fast.pauseMs)
            }
        }
    }

    @Test fun increasingSpeedDoesNotDecreaseUsefulTravelPerSecond() {
        for (height in listOf(96f, 400f, 800f, 1200f)) {
            var previous = 0f
            for (speed in listOf(50f, 600f, 2000f, 10000f, 40000f)) {
                val timing = EdgeScrollTiming.plan(speed, height)
                val delivered = timing.distanceDp * 1000 / (timing.durationMs + timing.pauseMs)
                assertTrue(delivered >= previous)
                previous = delivered
            }
        }
    }

    @Test fun slowSpeedStillClearsTouchSlopWithoutBusyLooping() {
        val timing = EdgeScrollTiming.plan(2.5f, 800f)
        assertEquals(32f, timing.distanceDp, 0f)
        assertEquals(120L, timing.durationMs)
        assertEquals(12680L, timing.pauseMs)
    }

    @Test fun displacementCanReachTheNewMaximum() {
        assertEquals(40000f, EdgeHandleGesture.scrollSpeed(160f, 0f, 40000, true), 0f)
        assertEquals(-40000f, EdgeHandleGesture.scrollSpeed(-160f, 0f, 40000, true), 0f)
    }
}
