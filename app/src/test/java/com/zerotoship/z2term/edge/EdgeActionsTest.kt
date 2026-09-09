package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeActionsTest {
    @Test fun argumentsRoundTripWithoutBecomingExtraActions() {
        val actions = listOf(EdgeActions.Action(EdgeActions.Type.COMMAND, "printf '%s' '日本語|x+y%z'\necho done"),
            EdgeActions.Action(EdgeActions.Type.WAIT, "500"), EdgeActions.Action(EdgeActions.Type.BACK))
        val encoded = EdgeActions.encode(actions)
        assertFalse(encoded.contains('\n'))
        assertEquals(actions, EdgeActions.decode(encoded))
    }

    @Test fun legacyBindingsMigrateWithoutConfusingDisabledAndAbsent() {
        val legacy = mapOf("handle" to "bar", "gesture-scroll" to "variable", "gesture-up" to "echo old")
        assertEquals(EdgeActions.Type.PANEL, EdgeActions.binding(legacy, EdgeActions.Trigger.INWARD).single().type)
        assertTrue(EdgeActions.binding(legacy, EdgeActions.Trigger.TAP).isEmpty())
        assertEquals(EdgeActions.Type.SCROLL_VARIABLE, EdgeActions.binding(legacy, EdgeActions.Trigger.UP).single().type)
        assertTrue(EdgeActions.binding(legacy + ("actions-up" to ""), EdgeActions.Trigger.UP).isEmpty())
        assertEquals(EdgeActions.Type.BACK, EdgeActions.binding(legacy + ("actions-up" to "back"), EdgeActions.Trigger.UP).single().type)
    }

    @Test fun malformedAndUnboundedSequencesAreRejected() {
        for (raw in listOf("unknown", "back:argument", "command", "command:", "wait:-1", "wait:30001",
            "wait:30000|wait:30000|wait:1", "launch:bad%3Bcommand", "command:%XX",
            List(17) { "back" }.joinToString("|"))) {
            try { EdgeActions.decode(raw); fail("Accepted $raw") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun allTriggersPersistThroughThePanelStoreValidator() {
        val fields = EdgeActions.Trigger.entries.associate { it.key to "wait:10|back" } +
            mapOf("scroll-x" to "25", "scroll-y" to "75")
        EdgeStore.validatePanel(fields)
        assertEquals(fields, EdgeStore.parse(EdgeStore.encode(fields)))
        for (bad in listOf("NaN", "9", "91")) {
            try { EdgeStore.validatePanel(mapOf("scroll-x" to bad)); fail("Accepted $bad") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
