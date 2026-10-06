package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeSwipeRelayTest {
    /** Plans [segments] in a row and checks every invariant the injector and the view rely on. */
    private fun run(velocity: Float, segments: Int, span: Float = 1000f, segmentMs: Long = 80L,
        check: (EdgeSwipeRelay.Plan) -> Unit = {}) {
        val begin = if (velocity > 0) 500f else 500f + span
        val end = if (velocity > 0) 500f + span else 500f
        var y: Float? = null
        var down = 0
        repeat(segments) {
            val plan = EdgeSwipeRelay.plan(y, begin, end, velocity, segmentMs, down)
            val strokes = plan.strokes
            assertTrue(strokes.size <= 20)
            assertEquals(0L, strokes.first().startMs)
            assertEquals(y != null, strokes.first().continues)
            strokes.drop(1).forEach { assertFalse(it.continues); assertTrue(it.startMs > 0) }
            strokes.forEach {
                assertTrue(it.durationMs >= 1)
                assertEquals(velocity, (it.to - it.from) / it.durationMs, 1e-3f)
            }
            for (i in 1 until strokes.size) {
                val previous = strokes[i - 1]; val stroke = strokes[i]
                assertEquals(begin, stroke.from, 0f)
                val lift = previous.startMs + previous.durationMs
                // Never an UP and a DOWN at the same time, and never three pointers.
                assertNotEquals(lift, stroke.startMs)
                if (i >= 2) assertTrue(strokes[i - 2].let { it.startMs + it.durationMs } <= stroke.startMs)
            }
            strokes.dropLast(1).forEach { assertFalse(it.keepDown) }
            val last = strokes.last()
            assertTrue(last.keepDown)
            assertEquals(segmentMs, last.startMs + last.durationMs)
            check(plan)
            y = last.to; down = plan.pointersDown
        }
    }

    @Test fun slowScrollKeepsOnePointerMoving() = run(1.6f, 6) { assertEquals(1, it.strokes.size) }

    @Test fun nextPointerLandsBeforeThePreviousLifts() {
        var relays = 0
        run(1.6f, 20) { plan ->
            plan.strokes.zipWithNext().forEach { (a, b) ->
                if (b.startMs < a.startMs + a.durationMs) relays++
            }
        }
        assertTrue(relays > 0)
    }

    @Test fun pointerIdsRunOutAndOneLiftLandsAgainAfterAMillisecond() {
        var relands = 0
        run(12f, 200) { plan ->
            assertTrue(plan.pointersDown in 1..EdgeSwipeRelay.MAX_POINTERS)
            plan.strokes.zipWithNext().forEach { (a, b) ->
                if (b.startMs == a.startMs + a.durationMs + 1) relands++
            }
        }
        assertTrue(relands > 0)
    }

    @Test fun upwardScrollMirrorsDownward() = run(-12f, 50)

    @Test fun topSpeedOnASmallWindowStaysWithinTheStrokeLimit() =
        run(150f / 8f, 50, span = 150f)
}
