package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeSwipeMotionTest {
    @Test fun callbackDelayIsIncludedInTheNextMovement() {
        val clock = EdgeSwipeMotion(600f, 1000L, 8, 8f)
        assertEquals(104f, clock.distanceDp(1000L, 800f, true), 0.01f)
        // Previous 160ms movement plus 20ms of dispatch/callback overhead.
        assertEquals(108f, clock.distanceDp(1180L, 800f, false), 0.01f)
    }

    @Test fun pointerResetTimeAndTouchSlopDoNotLowerTheAverageSpeed() {
        val clock = EdgeSwipeMotion(600f, 0, 16, 8f)
        var useful = 0f
        var now = 0L
        repeat(100) { i ->
            val fresh = i % 5 == 0
            if (i > 0 && fresh) now += 180L
            val distance = clock.distanceDp(now, 800f, fresh)
            useful += distance - if (fresh) 8f else 0f
            now += clock.durationMs + 15L
        }
        assertEquals(600f, useful * 1000 / (now - 15), 0.1f)
    }

    @Test fun lowSpeedsRemainFractionalInsteadOfWaitingBetweenShortSwipes() {
        val clock = EdgeSwipeMotion(2.5f, 0, 8, 8f)
        assertEquals(8.4f, clock.distanceDp(0, 800f, true), 0.001f)
        repeat(20) { i -> assertEquals(0.4f, clock.distanceDp((i + 1) * 160L, 800f, false), 0.001f) }
    }

    @Test fun owedTravelAfterAPointerResetIsSpreadOverSeveralMovements() {
        val clock = EdgeSwipeMotion(1000f, 0, 8, 8f)
        assertEquals(40f, clock.distanceDp(0, 40f, true), 0f)
        // 248dp is owed; one movement repays 240dp (1.5x its own share) plus the touch slop.
        assertEquals(248f, clock.distanceDp(120, 800f, true), 0.01f)
        assertEquals(188f, clock.distanceDp(300, 800f, false), 0.01f)
    }
}
