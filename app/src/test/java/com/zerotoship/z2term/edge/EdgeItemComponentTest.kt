package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeItemComponentTest {
    @Test fun savedTypesRemainRunnableAfterEditingTheirComponentAndBehavior() {
        val definitions = listOf("run", "text", "toggle", "list", "input", "note", "terminal", "macro", "result", "view")
            .map { mapOf("type" to it, "label" to "keep", "run" to "printf example") } +
            listOf("text", "choice", "fixed").map {
                mapOf("type" to "argument", "argument-kind" to it, "choices" to "a|b", "default" to "a")
            } + mapOf("type" to "run", "button-state" to "on", "off" to "printf off")
        definitions.forEach { fields ->
            val original = EdgeStore.Item("saved", fields)
            val edited = fields.toMutableMap()
            EdgeItemComponent.apply(EdgeItemComponent.from(original), original, edited)
            EdgeStore.validateItem(edited)
            assertEquals(fields, edited)
        }
    }

    @Test fun inputCanChangeStorageWithoutLosingItsExistingFileOrCommand() {
        val original = EdgeStore.Item("input", mapOf("type" to "note", "file" to "draft.txt", "run" to "cat"))
        val values = original.fields.toMutableMap()
        EdgeItemComponent.apply(EdgeItemComponent.Selection("entry", "value"), original, values)
        assertEquals("argument", values["type"])
        assertEquals("text", values["argument-kind"])
        assertEquals("draft.txt", values["file"])
        assertEquals("cat", values["run"])
        EdgeStore.validateItem(values)
    }

    @Test fun newButtonsUseBindingsWithoutATemplateAndStateButtonsKeepTheirExecutionPath() {
        val fields = mutableMapOf("run" to "cat", "stdin" to "input", "result" to "output")
        EdgeItemComponent.apply(EdgeItemComponent.Selection("button", "execute"), null, fields)
        assertEquals("run", fields["type"])
        assertTrue(EdgeItemComponent.linked(EdgeStore.Item("button", fields)))
        assertFalse(EdgeItemComponent.linked(EdgeStore.Item("button", fields + ("button-state" to "on"))))
        EdgeStore.validateItem(fields)
    }

    @Test fun legacyMacroCanBecomeAStateButtonWithoutRecreatingTheItem() {
        val old = EdgeStore.Item("button", mapOf("type" to "macro", "run" to "printf on"))
        assertEquals("run", EdgeItemComponent.type(EdgeItemComponent.Selection("button", "state_button"), old))
    }
}
