package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionDefinitionTest {
    private val screen = ActionDefinition.Screen(1080, 2400, 0)
    private fun parse(body: String) = ActionDefinition.parse("version=1\nscreen=current\ntarget org.example.app\n$body", screen)
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Invalid macro was accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun coordinatesAndTargetRoundTripWithoutDependingOnCurrentDisplay() {
        val definition = parse("tap percent 50 50\nlong-press px 100 200 600\nswipe percent 50 80 50 20 400")
        assertEquals(definition, ActionDefinition.parse(definition.text))
        val first = definition.steps.first() as ActionDefinition.Step.Stroke
        assertEquals("org.example.app", first.target)
        assertEquals(listOf(539.5f, 1199.5f, 539.5f, 1199.5f), first.points(screen))
    }
    @Test fun launchSetsTargetForFollowingStrokesAndExplicitTargetCanChangeIt() {
        val definition = parse("launch org.example.other\ntap px 2 3\ntarget org.example.last\ntap px 4 5")
        assertEquals(listOf("org.example.other", "org.example.last"),
            definition.steps.filterIsInstance<ActionDefinition.Step.Stroke>().map { it.target })
    }
    @Test fun rejectsOutOfScreenNonFiniteMixedUnitsAndExcessiveDurations() {
        for (line in listOf("tap px 1080 10", "tap percent 101 20", "tap px -1 3", "tap px NaN 2",
            "tap px Infinity 2", "tap dp 1 2", "tap px 2", "tap px 2 3 4", "long-press px 1 2 499",
            "long-press px 1 2 3001", "swipe percent 0 0 50 50 0", "scroll 0 1000 50 50",
            "scroll -600 30001 50 50", "wait 30001")) rejects { parse(line) }
    }
    @Test fun dragPassesEveryPointWithoutLiftingAtAnEvenPace() {
        val definition = parse("drag px 80 100,200 300,200 300,600")
        assertEquals(definition, ActionDefinition.parse(definition.text))
        val stroke = definition.steps.single() as ActionDefinition.Step.Stroke
        assertEquals(160L, stroke.ms)
        assertEquals(listOf(ActionGesture.Point(100f, 200f, 0), ActionGesture.Point(300f, 200f, 80), ActionGesture.Point(300f, 600f, 160)),
            stroke.timedPoints(screen))
        assertEquals(1, stroke.tracks(screen).size)
        // 百分率でも同じ場所を指す。
        val percent = (parse("drag percent 100 0,0 100,100").steps.single() as ActionDefinition.Step.Stroke).timedPoints(screen)
        assertEquals(listOf(0f, 0f, 1079f, 2399f), listOf(percent[0].x, percent[0].y, percent[1].x, percent[1].y))
        for (line in listOf("drag px 80 1,2", "drag px 19 1,2 3,4", "drag px 1001 1,2 3,4", "drag dp 80 1,2 3,4",
            "drag px 80 1,2 3", "drag px 80 1,2 1080,4", "drag px 1000 " + List(32) { "1,$it" }.joinToString(" "),
            "drag px 20 " + List(257) { "1,$it" }.joinToString(" "))) rejects { parse(line) }
    }
    @Test fun geometryIsRequiredForCoordinateActions() {
        rejects { ActionDefinition.parse("version=1\ntap px 1 2") }
        rejects { ActionDefinition.parse("version=1\ntarget org.example.app\ntap px 1 2") }
        assertNull(ActionDefinition.parse("version=1\nwait 20\nkey back").screen)
    }
    @Test fun unspecifiedAndCurrentTargetsRoundTripWithoutInventingAnApp() {
        for (version in listOf(1, 2)) {
            val source = "version=$version\nscreen=current\ntap px 1 2\nscroll -600 1000 50 50"
            val parsed = ActionDefinition.parse(source, screen)
            assertEquals(ActionDefinition.CURRENT_TARGET, (parsed.steps[0] as ActionDefinition.Step.Stroke).target)
            assertEquals(ActionDefinition.CURRENT_TARGET, (parsed.steps[1] as ActionDefinition.Step.Scroll).target)
            assertEquals(parsed, ActionDefinition.parse(parsed.text))
            assertFalse(parsed.text.contains("target "))
        }
        val parsed = parse("tap px 1 2\ntarget current\ntap px 3 4\nlaunch org.example.other\ntap px 5 6")
        assertEquals(listOf("org.example.app", "current", "org.example.other"),
            parsed.steps.filterIsInstance<ActionDefinition.Step.Stroke>().map { it.target })
        rejects { parse("launch current") }
        rejects { parse("target currnet\ntap px 1 2") }
    }
    @Test fun rejectsDuplicateHeadersUnknownStepsAndUnboundedFiles() {
        for (text in listOf("version=3\nwait 0", "version=1\nversion=1\nwait 0", "version=1\ntimeout=301\nwait 0", "version=1\ntimeout=-1\nwait 0",
            "version=1\nunknown 20", "version=1\ncommand", "version=1\nwait 0\nscreen=10x10@0",
            "version=1\n" + "wait 0\n".repeat(65), "version=1\ncommand " + "a".repeat(65536))) {
            rejects { ActionDefinition.parse(text) }
        }
    }
    @Test fun timeoutZeroMeansNoLimit() {
        assertTrue(ActionDefinition.parse("version=1\ntimeout=0\nwait 0").unlimited)
        assertFalse(ActionDefinition.parse("version=1\ntimeout=1\nwait 0").unlimited)
    }
    @Test fun shellArgumentsRemainLiteralInDefinitions() {
        val command = "printf '%s' \"a=b; "+'$'+"(printf x)\""
        val definition = ActionDefinition.parse("version=1\ncommand $command")
        assertEquals(command, (definition.steps.single() as ActionDefinition.Step.Command).text)
    }

    @Test fun trailingCommentsNeedSpaceOnBothSidesAndKeepShellAndHeadersIntact() {
        val definition = ActionDefinition.parse("version=2 # 見出しの説明\nscreen=current  # 画面\ntarget org.example.app\n" +
            "wait 800 # 待つ\nrepeat 2   # 2 回\n  click text=#5\n  click text=No. #\nend # ここまで\nkey back #\n" +
            "command echo a # シェルに渡す\nif charging # 充電中\n  wait 1\nelse # でなければ\n  wait 2\nend", screen)
        val steps = definition.steps
        assertEquals(ActionDefinition.Step.Wait(800), steps[0])
        val body = (steps[1] as ActionDefinition.Step.Repeat).body.map { (it as ActionDefinition.Step.Ui).selector }
        assertEquals(listOf(ActionSelector.parse("text=#5"), ActionSelector.parse("text=No.")), body)
        assertEquals(ActionDefinition.Step.Key("back"), steps[2])
        assertEquals("echo a # シェルに渡す", (steps[3] as ActionDefinition.Step.Command).text)
        assertTrue(steps[4] is ActionDefinition.Step.Branch)
        // screen=current は寸法に置き換わり、行末のコメントは残る。
        assertTrue(definition.text.startsWith("version=2 # 見出しの説明\nscreen=1080x2400@0  # 画面\n"))
        assertEquals(definition, ActionDefinition.parse(definition.text))
    }
    @Test fun commentPositionRule() {
        assertEquals(2, ActionDefinition.commentStart("  # 全体"))
        assertEquals(9, ActionDefinition.commentStart("wait 800 # x"))
        assertEquals(9, ActionDefinition.commentStart("wait 800\t#"))
        assertNull(ActionDefinition.commentStart("click text=#5"))
        assertNull(ActionDefinition.commentStart("click text=a#b"))
        assertNull(ActionDefinition.commentStart("  command echo # x"))
        assertEquals("wait 800", ActionDefinition.code("  wait 800   # x"))
    }
}
