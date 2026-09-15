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
        for (text in listOf("version=3\nwait 0", "version=1\nversion=1\nwait 0", "version=1\ntimeout=301\nwait 0",
            "version=1\nunknown 20", "version=1\ncommand", "version=1\nwait 0\nscreen=10x10@0",
            "version=1\n" + "wait 0\n".repeat(65), "version=1\ncommand " + "a".repeat(65536))) {
            rejects { ActionDefinition.parse(text) }
        }
    }
    @Test fun shellArgumentsRemainLiteralInDefinitions() {
        val command = "printf '%s' \"a=b; "+'$'+"(printf x)\""
        val definition = ActionDefinition.parse("version=1\ncommand $command")
        assertEquals(command, (definition.steps.single() as ActionDefinition.Step.Command).text)
    }
}
