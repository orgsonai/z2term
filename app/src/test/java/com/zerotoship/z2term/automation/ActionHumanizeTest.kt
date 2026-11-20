package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class ActionHumanizeTest {
    private val screen = ActionDefinition.Screen(1080, 2400, 0)
    private val area = ActionHumanize.Area(0, 0, 1080, 2400)
    private fun steps(body: String) = ActionDefinition.parse("version=2\nscreen=current\ntarget org.example.app\n$body", screen).steps
        .filterIsInstance<ActionDefinition.Step.Stroke>()
    private val radius = 1080 * 0.006f

    @Test fun headerIsOnOrOff() {
        assertTrue(ActionDefinition.parse("version=1\nhuman=on\nwait 1").human)
        assertFalse(ActionDefinition.parse("version=1\nhuman=off\nwait 1").human)
        assertFalse(ActionDefinition.parse("version=1\nwait 1").human)
        assertThrows(IllegalArgumentException::class.java) { ActionDefinition.parse("version=1\nhuman=yes\nwait 1") }
    }

    @Test fun tapsMoveALittleAndVaryTheirLength() {
        val random = Random(1)
        val tap = steps("tap percent 50 50").single()
        val (x, y) = tap.points(screen)
        val seen = (1..200).map { ActionHumanize.stroke(tap, screen, area, random) }
        assertTrue(seen.all { it.path.isEmpty() && it.unit == "px" && it.x1 == it.x2 && it.y1 == it.y2 })
        assertTrue(seen.all { hypot(it.x1 - x, it.y1 - y) <= radius + 0.01f })
        assertTrue(seen.map { it.x1 }.distinct().size > 100)
        assertTrue(seen.all { it.ms in 60..110 })
        val hold = ActionHumanize.stroke(steps("long-press percent 50 50 3000").single(), screen, area, random)
        assertTrue(hold.ms in 2760..3000)
        val double = ActionHumanize.stroke(steps("double-tap percent 50 50 300").single(), screen, area, random)
        assertTrue(double.taps == 2 && double.gapMs in 255..300)
    }

    @Test fun swipesBendStartAndStopSlowlyAndKeepTheirEnds() {
        val swipe = steps("swipe px 100 2000 100 400 600").single()
        val human = ActionHumanize.stroke(swipe, screen, area, Random(7))
        val path = human.path
        assertTrue(path.size > 10)
        assertEquals(0L, path.first().ms)
        assertEquals(human.ms, path.last().ms)
        assertTrue(human.ms in 552..648)
        assertTrue(path.zipWithNext().all { (a, b) -> a.ms < b.ms && b.y <= a.y + 1.5f })   // 後戻りしない
        assertTrue(hypot(path.first().x - 100, path.first().y - 2000) <= radius + 0.01f)
        assertTrue(hypot(path.last().x - 100, path.last().y - 400) <= radius * 1.6f + 0.01f)
        val middle = path[path.size / 2]
        // まっすぐではない (曲がり方は毎回違うので、多くの回で横へ 2 px 以上ふくらむことを見る)
        val bent = (1..40).count { seed ->
            ActionHumanize.stroke(swipe, screen, area, Random(seed)).path.let { p ->
                val sx = p.first().x; val ex = p.last().x
                p.any { point -> abs(point.x - (sx + (ex - sx) * (2000 - point.y) / 1600)) > 2 }
            }
        }
        assertTrue(bent > 30)
        // 動き出しと止まり際は真ん中より遅い
        fun speed(i: Int) = hypot(path[i + 1].x - path[i].x, path[i + 1].y - path[i].y) / (path[i + 1].ms - path[i].ms)
        assertTrue(speed(0) < speed(path.indexOf(middle)))
        assertTrue(speed(path.size - 3) < speed(path.indexOf(middle)))
        human.tracks(screen)
    }

    @Test fun dragsKeepTheirShapeAndTotalTimeAndStayInsideTheWindow() {
        val drag = steps("drag px 100 10,10 500,10 500,500 10,500").single()
        val human = ActionHumanize.stroke(drag, screen, ActionHumanize.Area(0, 0, 1080, 2400), Random(3))
        assertEquals(drag.path.size, human.path.size)
        assertEquals(drag.path.last().ms, human.path.last().ms)
        assertTrue(human.path.zipWithNext().all { (a, b) -> a.ms < b.ms })
        assertTrue(human.path.zip(drag.path).all { (h, d) -> hypot(h.x - d.x, h.y - d.y) <= radius + 2f })
        assertTrue(human.path.all { it.x >= 0 && it.y >= 0 })
        val edge = ActionHumanize.stroke(steps("tap px 0 0").single(), screen, ActionHumanize.Area(0, 100, 1080, 2400), Random(5))
        assertTrue(edge.x1 >= 0 && edge.y1 >= 100)
        human.tracks(screen)
    }

    @Test fun twoFingerGesturesOnlyShiftTogether() {
        val pinch = steps("pinch-out percent 50 50 10 40 400").single()
        val human = ActionHumanize.stroke(pinch, screen, area, Random(9))
        val before = pinch.tracks(screen)
        val after = human.tracks(screen)
        val dx = after[0][0].x - before[0][0].x
        assertTrue(after.flatten().zip(before.flatten()).all { (a, b) -> abs(a.x - b.x - dx) < 0.01f && a.ms == b.ms })
    }

    @Test fun waitsVaryByTenPercentUpTo300Ms() {
        val random = Random(11)
        assertEquals(0L, ActionHumanize.waitMs(0, random))
        assertTrue((1..200).map { ActionHumanize.waitMs(1000, random) }.all { it in 900..1100 })
        assertTrue((1..200).map { ActionHumanize.waitMs(30000, random) }.all { it in 29700..30300 })
    }
}
