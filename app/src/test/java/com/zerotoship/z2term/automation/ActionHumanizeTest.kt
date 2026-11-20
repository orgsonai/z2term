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
        assertEquals(ActionHumanize.DEFAULT_LEVEL, ActionDefinition.parse("version=1\nhuman=on\nwait 1").humanLevel)
        assertEquals(8, ActionDefinition.parse("version=1\nhuman=8\nwait 1").humanLevel)
        assertFalse(ActionDefinition.parse("version=1\nhuman=0\nwait 1").human)
        assertThrows(IllegalArgumentException::class.java) { ActionDefinition.parse("version=1\nhuman=21\nwait 1") }
    }

    @Test fun tapsMoveALittleAndVaryTheirLength() {
        val random = Random(1)
        val tap = steps("tap percent 50 50").single()
        val (x, y) = tap.points(screen)
        val seen = (1..200).map { ActionHumanize.stroke(tap, screen, area, random, level = 2) }
        assertTrue(seen.all { it.path.isEmpty() && it.unit == "px" && it.x1 == it.x2 && it.y1 == it.y2 })
        assertTrue(seen.all { hypot(it.x1 - x, it.y1 - y) <= radius + 0.01f })
        assertTrue(seen.map { it.x1 }.distinct().size > 100)
        assertTrue(seen.all { it.ms in 60..110 })
        val hold = ActionHumanize.stroke(steps("long-press percent 50 50 3000").single(), screen, area, random, level = 2)
        assertTrue(hold.ms in 2760..3000)
        val double = ActionHumanize.stroke(steps("double-tap percent 50 50 300").single(), screen, area, random, level = 2)
        assertTrue(double.taps == 2 && double.gapMs in 255..300)
    }

    @Test fun swipesBendStartAndStopSlowlyAndKeepTheirEnds() {
        val swipe = steps("swipe px 100 2000 100 400 600").single()
        val human = ActionHumanize.stroke(swipe, screen, area, Random(7), level = 2)
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
            ActionHumanize.stroke(swipe, screen, area, Random(seed), level = 2).path.let { p ->
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

    @Test fun dragsPassEveryPointSlowDownAtCornersAndBend() {
        // 逆 V: 左下 → 上の頂点 → 右下。1 区間 300 ms。
        val drag = steps("drag px 300 200,800 500,500 800,800").single()
        assertTrue(drag.waypoints)
        assertFalse(steps("touch px 1,1,0 2,2,16 3,3,32").single().waypoints)
        val human = ActionHumanize.stroke(drag, screen, area, Random(3), level = 2)
        val path = human.path
        assertEquals(600L, path.last().ms)
        assertEquals(0L, path.first().ms)
        assertTrue(path.zipWithNext().all { (a, b) -> a.ms < b.ms })
        assertTrue(path.size > 20)                                   // 16 ms ごとの点になる
        // 決めた点はどれも通る (ずらし + 角の丸め 4 px + 震え 0.5 px の内側)
        for (p in drag.path) assertTrue(path.minOf { hypot(it.x - p.x, it.y - p.y) } <= radius + 6f)
        // 頂点の近くは区間の中ほどより遅い (止まりはしない)
        fun speedAt(target: Float): Float {
            val i = path.indices.drop(1).minByOrNull { abs(path[it].x - target) }!!
            return hypot(path[i].x - path[i - 1].x, path[i].y - path[i - 1].y) / (path[i].ms - path[i - 1].ms)
        }
        assertTrue(speedAt(500f) < speedAt(350f))
        assertTrue(speedAt(500f) > 0f)
        // 多くの回で、区間がまっすぐではない
        val bent = (1..40).count { seed ->
            val moved = ActionHumanize.stroke(drag, screen, area, Random(seed), level = 2).path
            // 全体のずれを戻してから、左の区間の直線 (y = 800 - (x - 200)) からの離れを見る
            val ox = moved.first().x - 200; val oy = moved.first().y - 800
            moved.map { it.x - ox to it.y - oy }.filter { it.first in 260f..440f }.any { (x, y) ->
                abs(y - (800 - (x - 200))) / 1.414f > 2.5f
            }
        }
        assertTrue(bent > 25)
        human.tracks(screen)
        val edge = ActionHumanize.stroke(steps("tap px 0 0").single(), screen, ActionHumanize.Area(0, 100, 1080, 2400), Random(5), level = 2)
        assertTrue(edge.x1 >= 0 && edge.y1 >= 100)
    }

    @Test fun recordedTouchesKeepTheirPointsAndTotalTime() {
        val touch = steps("touch px 10,10,0 20,12,16 30,15,32 40,20,48 50,30,64").single()
        val human = ActionHumanize.stroke(touch, screen, area, Random(4), level = 2)
        assertEquals(touch.path.size, human.path.size)
        assertEquals(touch.path.last().ms, human.path.last().ms)
        assertTrue(human.path.zipWithNext().all { (a, b) -> a.ms < b.ms })
        assertTrue(human.path.zip(touch.path).all { (h, d) -> hypot(h.x - d.x, h.y - d.y) <= radius + 2f })
    }

    @Test fun twoFingerGesturesOnlyShiftTogether() {
        val pinch = steps("pinch-out percent 50 50 10 40 400").single()
        val human = ActionHumanize.stroke(pinch, screen, area, Random(9), level = 2)
        val before = pinch.tracks(screen)
        val after = human.tracks(screen)
        val dx = after[0][0].x - before[0][0].x
        assertTrue(after.flatten().zip(before.flatten()).all { (a, b) -> abs(a.x - b.x - dx) < 0.01f && a.ms == b.ms })
    }

    @Test fun waitsVaryByTenPercentUpTo300Ms() {
        val random = Random(11)
        assertEquals(0L, ActionHumanize.waitMs(0, random, level = 2))
        assertTrue((1..200).map { ActionHumanize.waitMs(1000, random, level = 2) }.all { it in 900..1100 })
        assertTrue((1..200).map { ActionHumanize.waitMs(30000, random, level = 2) }.all { it in 29700..30300 })
    }

    @Test fun largerLevelsMoveMoreAndZeroLeavesTheStepAlone() {
        val drag = steps("drag px 300 200,800 500,500 800,800").single()
        assertSame(drag, ActionHumanize.stroke(drag, screen, area, Random(1), level = 0))
        fun deviation(level: Int) = (1..30).map { seed ->
            val path = ActionHumanize.stroke(drag, screen, area, Random(seed), level = level).path
            val ox = path.first().x - 200; val oy = path.first().y - 800
            path.filter { it.x - ox in 260f..440f }.maxOfOrNull { abs(it.y - oy - (800 - (it.x - ox - 200))) / 1.414f } ?: 0f
        }.average()
        val low = deviation(1); val high = deviation(10)
        assertTrue("$low < $high", high > low * 4)
        // 強さ 10 でも決めた点の近くは通り (外れは 25 px ほどまで)、時刻の決まりは守る
        val strong = ActionHumanize.stroke(drag, screen, area, Random(2), level = 10)
        assertTrue(strong.path.zipWithNext().all { (a, b) -> a.ms < b.ms })
        assertEquals(600L, strong.path.last().ms)
        strong.tracks(screen)
        assertEquals(1000L, ActionHumanize.waitMs(1000, Random(1), level = 0))
        assertTrue((1..200).map { ActionHumanize.waitMs(1000, Random(it), level = 10) }.let { w -> w.max() - w.min() > 600 })
    }

    @Test fun strength20StillSendsValidGestures() {
        assertEquals(20, ActionDefinition.parse("version=1\nhuman=20\nwait 1").humanLevel)
        val all = steps("tap percent 50 50\nlong-press percent 50 50 700\ndouble-tap percent 50 50 100\n" +
            "swipe percent 20 60 80 60 800\ndrag percent 100 20,40 50,30 80,40\npinch-out percent 50 50 10 40 400\n" +
            "touch px 10,10,0 20,12,16 30,15,32 40,20,48")
        for (seed in 1..30) for (step in all) {
            val human = ActionHumanize.stroke(step, screen, area, Random(seed), level = 20)
            human.tracks(screen).forEach { track -> assertTrue(track.zipWithNext().all { (a, b) -> a.ms < b.ms }) }
            if (human.path.isEmpty()) assertTrue(human.ms in 60..3000 && human.gapMs in 40..300)
        }
        assertTrue((1..100).all { ActionHumanize.waitMs(1000, Random(it), level = 20) >= 0 })
    }
}
