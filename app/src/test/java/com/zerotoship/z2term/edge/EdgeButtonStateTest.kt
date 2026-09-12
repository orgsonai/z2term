package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EdgeButtonStateTest {
    @Test fun learnedSourcesSurviveRestartButNeverFollowEditedDefinitions() {
        val dir = Files.createTempDirectory("edge-source").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", emptyMap())
            store.setItem("main:light", mapOf("button-state" to "on", "run" to "light.sh"))
            val item = store.item("main:light")
            assertTrue(store.saveButtonState("main", item, true, "torch"))
            assertEquals("torch", EdgeStore(dir).buttonSource("main", item))
            store.setItem("main:light", mapOf("button-source" to "process"))
            val changed = store.item("main:light")
            assertNull(store.buttonSource("main", changed))
            assertNull(store.buttonState("main", changed))
            assertFalse(store.saveButtonState("main", item, false, "torch"))
            store.removeItem("main:light")
            assertFalse(store.saveButtonState("main", changed, true, "screen"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun rememberedStateSurvivesReopenAndRejectsChangedCommands() {
        val dir = Files.createTempDirectory("edge-button").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", emptyMap())
            store.setItem("main:lamp", mapOf("button-state" to "on", "run" to "echo on", "off" to "echo off"))
            val item = store.item("main:lamp")
            assertTrue(item.isStateButton)
            assertNull(store.buttonState("main", item))
            assertEquals("echo on", item.actionCommand(false))
            assertEquals("echo off", item.actionCommand(true))
            assertTrue(store.saveButtonState("main", item, true))
            assertEquals(true, EdgeStore(dir).buttonState("main", item))
            store.setItem("main:lamp", mapOf("label" to "Light"))
            assertEquals(true, store.buttonState("main", store.item("main:lamp")))
            store.setItem("main:lamp", mapOf("run" to "echo changed"))
            assertNull(store.buttonState("main", store.item("main:lamp")))
            assertFalse(store.saveButtonState("main", item, false))
            store.removeItem("main:lamp")
            assertFalse(store.saveButtonState("main", item, true))
            assertFalse(dir.resolve("main/lamp.button-state").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun sameCommandMayToggleAndNormalLaunchesStayStateless() {
        val toggle = EdgeStore.Item("x", mapOf("button-state" to "on", "run" to "echo toggle"))
        assertEquals(toggle.actionCommand(false), toggle.actionCommand(true))
        val normal = EdgeStore.Item("x", mapOf("run" to "echo launch", "off" to "echo ignored"))
        assertFalse(normal.isStateButton)
        assertEquals("echo launch", normal.actionCommand(true))
    }

    @Test fun observationsMustBeExplicitBooleanValues() {
        for (value in listOf("on", "true", "1", " on\n")) assertEquals(true, EdgeStore.parseButtonState(value))
        for (value in listOf("off", "false", "0")) assertEquals(false, EdgeStore.parseButtonState(value))
        for (value in listOf("", "success", "on\noff")) assertNull(EdgeStore.parseButtonState(value))
    }
}
