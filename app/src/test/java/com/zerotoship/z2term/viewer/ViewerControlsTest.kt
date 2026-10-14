package com.zerotoship.z2term.viewer

import org.junit.Assert.*
import org.junit.Test

// Built directly: org.json is an Android stub in JVM unit tests, so parse() is not exercised here.
class ViewerControlsTest {
    private fun action(id: String, param: Boolean) =
        ViewerControls.Action(id, id, listOf(id), toolbar = false, confirmation = "", fields = emptyList(), param = param)
    private val controls = ViewerControls("", "example.sh", emptyList(),
        listOf(action("remove", param = true), action("feeds", param = false)))

    @Test fun aParamActionTakesExactlyOneValueFromTheLink() {
        val (found, value) = controls.link("remove/2026-10 a&b")!!
        assertEquals("remove", found.id)
        assertEquals("2026-10 a&b", value)
        // The first slash separates; later ones stay in the value.
        assertEquals("a/b", controls.link("remove/a/b")!!.second)
        assertNull(controls.link("remove"))
        assertNull(controls.link("remove/"))
        assertNull(controls.link("remove/" + "x".repeat(4097)))
        assertNull(controls.link("remove/a\u0000b"))
    }

    @Test fun anOrdinaryActionIgnoresLinksThatCarryAValue() {
        assertNull(controls.link("feeds")!!.second)
        assertEquals("feeds", controls.link("feeds")!!.first.id)
        assertNull(controls.link("feeds/x"))
        assertNull(controls.link("missing"))
        assertNull(controls.link("missing/x"))
    }

    @Test fun everyNewFieldTypeIsKnown() {
        assertTrue(ViewerControls.FIELD_TYPES.containsAll(listOf("text", "multiline", "number", "switch", "date", "time", "datetime", "choice")))
    }
}
