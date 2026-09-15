package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionRecordingTest {
    private val screen = ActionDefinition.Screen(1080, 2400, 0)
    private fun finger(id: Int, x: Float = 100f, y: Float = 200f) = ActionRecording.Contact(id, x, y)

    @Test fun keepsInnerPausesAndTrimsOnlyOuterIdleTime() {
        val recording = ActionRecording()
        recording.frame(5000, listOf(finger(1)))
        recording.frame(5080, emptyList())
        recording.frame(5220, listOf(finger(2)))
        recording.frame(5250, listOf(finger(2, 150f)))
        recording.frame(5300, emptyList())
        recording.finish(20_000)
        assertEquals(300L, recording.durationMs)
        val source = recording.source("percent", screen)
        val parsed = ActionDefinition.parse("version=2\nscreen=$screen\n$source")
        val steps = (parsed.steps.single() as ActionDefinition.Step.Repeat).body
        assertEquals(3, steps.size)
        assertEquals(140L, (steps[1] as ActionDefinition.Step.Wait).ms)
        assertTrue(steps.first() is ActionDefinition.Step.Stroke)
        assertTrue(steps.last() is ActionDefinition.Step.Stroke)
        assertEquals(ActionDefinition.CURRENT_TARGET, (steps.first() as ActionDefinition.Step.Stroke).target)
        assertEquals(parsed, ActionDefinition.parse(parsed.text))
    }

    @Test fun fingersMayJoinAndLeaveAtDifferentTimes() {
        val recording = ActionRecording()
        recording.frame(1000, listOf(finger(1)))
        recording.frame(1040, listOf(finger(1), finger(2, 300f)))
        recording.frame(1100, listOf(finger(2, 320f)))
        recording.frame(1160, emptyList())
        val parsed = ActionDefinition.parse("version=2\nscreen=$screen\n" + recording.source("px", screen))
        val stroke = (parsed.steps.single() as ActionDefinition.Step.Repeat).body.single() as ActionDefinition.Step.Stroke
        assertEquals(0L, stroke.path.first().ms)
        assertEquals(100L, stroke.path.last().ms)
        assertEquals(40L, stroke.secondPath.first().ms)
        assertEquals(160L, stroke.secondPath.last().ms)
        assertEquals(160L, stroke.ms)
    }

    @Test fun cancelledTouchDoesNotLeakIntoTheNextEpisode() {
        val recording = ActionRecording()
        recording.frame(1000, listOf(finger(1)))
        recording.cancelTouch()
        recording.frame(2000, listOf(finger(2)))
        recording.frame(2080, emptyList())
        assertEquals(1, recording.count)
        assertEquals(80L, recording.durationMs)
        assertFalse(recording.source("px", screen).contains("wait"))
        assertThrows(IllegalArgumentException::class.java) { ActionRecording().source("px", screen) }
    }

    @Test fun releaseFrameRetainsItsFinalPositionAndVelocityInterval() {
        val recording = ActionRecording()
        recording.frame(1000, listOf(finger(1, 10f)))
        recording.frame(1016, listOf(finger(1, 20f)))
        recording.frame(1023, emptyList(), released = listOf(finger(1, 80f)))
        val parsed = ActionDefinition.parse("version=2\nscreen=$screen\n" + recording.source("px", screen))
        val stroke = (parsed.steps.single() as ActionDefinition.Step.Repeat).body.single() as ActionDefinition.Step.Stroke
        assertEquals(ActionGesture.Point(80f, 200f, 23), stroke.path.last())
        assertEquals(listOf(0L, 16L, 23L), stroke.path.map { it.ms })
    }
}
