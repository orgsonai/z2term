package com.zerotoship.z2term.viewer

import com.zerotoship.z2term.edge.EdgeStore
import com.zerotoship.z2term.edge.EdgePanelCommands
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ViewerOptionsTest {
    @Test fun oldItemsAndNewItemsRemainManualEvenWithAnInterval() {
        for (fields in listOf(emptyMap(), mapOf("type" to "view", "every" to "30"),
            mapOf("view-refresh" to "manual", "every" to "60"))) {
            val options = ViewerOptions.from(fields)
            assertFalse(options.automatic)
            assertEquals(0L, options.interval(60))
            assertTrue(options.showRefresh)
            assertTrue(options.showExpand)
        }
    }

    @Test fun controlsAreIndependentOfAutomaticUpdatesAndPersistInExports() {
        val dir = Files.createTempDirectory("viewer-options").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", emptyMap())
            val fields = mapOf("type" to "view", "run" to "example.sh view", "view-refresh" to "auto",
                "every" to "30", "view-refresh-button" to "off", "view-expand-button" to "on")
            store.setItem("main:page", fields)
            val loaded = EdgeStore(dir).item("main:page")
            val options = ViewerOptions.from(loaded.fields)
            assertTrue(options.automatic)
            assertEquals(30L, options.interval(loaded.every))
            assertEquals(0L, options.interval(0))
            assertFalse(options.showRefresh)
            assertTrue(options.showExpand)
            assertEquals(fields, loaded.fields)
            val exported = EdgePanelCommands.generate(store.panels(), "main")
            for (key in listOf("view-refresh", "view-refresh-button", "view-expand-button")) {
                assertTrue(exported.contains("$key=${fields.getValue(key)}"))
            }
            store.setItem("main:page", mapOf("view-refresh-button" to "on", "view-expand-button" to "off"))
            val changed = ViewerOptions.from(store.item("main:page").fields)
            assertTrue(changed.showRefresh)
            assertFalse(changed.showExpand)
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidOptionsAreRejectedBeforeChangingSavedItems() {
        val dir = Files.createTempDirectory("viewer-invalid-options").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", emptyMap())
            store.setItem("main:page", mapOf("type" to "view", "run" to "example.sh view"))
            val original = store.item("main:page").fields
            for ((key, invalid) in listOf("view-refresh" to "always", "view-refresh-button" to "yes",
                "view-expand-button" to "false", "view-refresh" to "")) {
                try {
                    store.setItem("main:page", mapOf(key to invalid))
                    fail("Invalid $key was accepted")
                } catch (_: IllegalArgumentException) { }
                assertEquals(original, store.item("main:page").fields)
            }
        } finally { dir.deleteRecursively() }
    }
}
