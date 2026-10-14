package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeBarColorTest {
    private fun rejects(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return }
        fail("Expected the value to be rejected")
    }

    @Test fun presetsAndHexHaveOneFillAndAutoWhiteBlackHaveNone() {
        for (name in listOf(null, "", "auto", "white", "black")) assertNull(EdgeBarColor.fill(name))
        assertEquals(0xFF1E88E5.toInt(), EdgeBarColor.fill("blue"))
        assertEquals(0xFF00FF7F.toInt(), EdgeBarColor.fill("#00ff7F"))
        assertNull(EdgeBarColor.fill("#12345"))
        assertNull(EdgeBarColor.fill("teal"))
        // Every value the editor offers is accepted, and every preset has a fill.
        for (name in EdgeBarColor.names) assertTrue(name, EdgeBarColor.valid(name))
        for (name in EdgeBarColor.names.drop(3)) assertNotNull(name, EdgeBarColor.fill(name))
    }

    @Test fun inkStaysVisibleOnTheFill() {
        assertEquals(0xFF000000.toInt(), EdgeBarColor.ink(0xFFFDD835.toInt()))
        assertEquals(0xFF000000.toInt(), EdgeBarColor.ink(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), EdgeBarColor.ink(0xFF8E24AA.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), EdgeBarColor.ink(0xFF000000.toInt()))
    }

    @Test fun panelAcceptsBarColoursAndSchemeNames() {
        for (value in listOf("", "auto", "white", "black", "gray", "pink", "#A0b1C2"))
            EdgeStore.validatePanel(mapOf("bar-color" to value))
        for (value in listOf("teal", "#fff", "#GGGGGG", "blue "))
            rejects { EdgeStore.validatePanel(mapOf("bar-color" to value)) }
        // A scheme is a theme name, including a user's own; only its shape is checked.
        for (value in listOf("", "Nord", "Catppuccin Latte", "わたしの配色"))
            EdgeStore.validatePanel(mapOf("scheme" to value))
        rejects { EdgeStore.validatePanel(mapOf("scheme" to "a\nb")) }
        rejects { EdgeStore.validatePanel(mapOf("scheme" to "x".repeat(81))) }
    }
}
