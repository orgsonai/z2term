package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionGestureTest {
    private val screen = ActionDefinition.Screen(1001, 2001, 0)
    private fun parse(body: String) = ActionDefinition.parse("version=2\nscreen=$screen\n$body")
    private fun stroke(body: String) = parse(body).steps.single() as ActionDefinition.Step.Stroke

    @Test fun oldSwipesStayLinearAndAccelerationReachesTheEndWithoutPausing() {
        val old = stroke("swipe percent 10 80 90 20 160")
        assertEquals("linear", old.easing)
        val accelerated = stroke("swipe px 0 100 1000 100 160 accelerate")
        val points = accelerated.tracks(screen).single()
        assertEquals(ActionGesture.Point(1000f, 100f, 160), points.last())
        val distances = points.zipWithNext().map { (a, b) -> b.x - a.x }
        assertTrue(distances.first() < distances.last())
        val plan = ActionGesturePlan.create(accelerated.tracks(screen), screen)
        assertEquals(160L, plan.last().endMs)
        assertFalse(plan.last().parts.single().continues)
        assertEquals(1000f, plan.last().parts.single().points.last().x, 0.001f)
        assertEquals(parse("swipe px 0 100 1000 100 160 accelerate"),
            ActionDefinition.parse(parse("swipe px 0 100 1000 100 160 accelerate").text))
    }

    @Test fun doubleTapAndTwoFingerActionsHaveDistinctContacts() {
        val tap = stroke("double-tap percent 50 50 100")
        assertEquals(2, tap.taps)
        assertEquals(260L, tap.durationMs)
        val inward = stroke("pinch-in percent 50 50 60 20 400")
        val paths = inward.tracks(screen)
        assertEquals(2, paths.size)
        assertTrue(paths[0].first().x < paths[0].last().x)
        assertTrue(paths[1].first().x > paths[1].last().x)
        val two = stroke("swipe-two percent 35 75 35 25 30 0 450 accelerate")
        val twoPaths = two.tracks(screen)
        assertEquals(twoPaths[0].map { it.ms }, twoPaths[1].map { it.ms })
        assertTrue(twoPaths[0].indices.all { twoPaths[1][it].x - twoPaths[0][it].x == 300f })
    }

    @Test fun validatesEveryPointAndBothFingers() {
        for (body in listOf(
            "swipe-path px 1,1,0 1001,1,20 2,2,40",
            "swipe-path px 1,1,0 2,2,0",
            "swipe-path px 1,1,10 2,2,20",
            "swipe-path px NaN,1,0 2,2,20",
            "swipe-path px 1,1,0 2,2,30001",
            "swipe px 1 1 2 2 100 fast",
            "pinch-in percent 5 50 60 20 400",
            "pinch-out percent 50 50 60 20 400",
            "swipe-two percent 80 50 90 10 30 0 400",
            "touch px 1,1,0 2,2,20 | 3,3,30 4,4,40",
            "double-tap px 1 1 301"
        )) assertThrows(IllegalArgumentException::class.java) { parse(body) }
    }

    @Test fun unequalFingerDownAndUpTimesAreRetained() {
        val touch = stroke("touch px 10,10,0 90,10,160 | 10,100,40 90,100,240")
        val plan = ActionGesturePlan.create(touch.tracks(screen), screen)
        assertEquals(listOf(0L, 40L), plan.first().parts.map { it.startMs })
        val firstUp = plan.flatMap { it.parts }.single { it.id == 0 && !it.continues }
        val secondUp = plan.flatMap { it.parts }.single { it.id == 1 && !it.continues }
        assertEquals(160L, firstUp.endMs)
        assertEquals(240L, secondUp.endMs)
    }

    @Test fun stationaryHoldEmitsDownThenUpWithoutEmptyContinuations() {
        val touch = stroke("touch px 50,50,0 50,50,2500")
        val plan = ActionGesturePlan.create(touch.tracks(screen), screen)
        assertEquals(2, plan.size)
        assertTrue(plan.first().parts.single().downOnly)
        assertEquals(0L, plan.first().startMs)
        assertEquals(2500L, plan.last().endMs)
        assertFalse(plan.last().parts.single().continues)
    }

    @Test fun fastLiftOffSampleSurvivesSamplingAndNoTailIsAdded() {
        val recording = ActionGesture.Recording()
        recording.add(10f, 20f, 0)
        recording.add(20f, 20f, 16)
        recording.add(25f, 20f, 20)
        recording.add(80f, 20f, 23, up = true)
        assertEquals(ActionGesture.Point(80f, 20f, 23), recording.points.last())
        assertEquals(listOf(0L, 16L, 23L), recording.points.map { it.ms })
    }
}
