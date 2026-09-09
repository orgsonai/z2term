package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EdgeStoreTest {
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected invalid definition to be rejected") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun initialPanelDoesNotOverwriteExistingDefinitions() {
        val dir = Files.createTempDirectory("edge-initial-test").toFile()
        try {
            val store = EdgeStore(dir)
            assertTrue(store.ensureInitialPanel("Main"))
            store.setPanel("main", mapOf("size" to "4", "label" to "Custom"))
            store.setItem("main:action", mapOf("run" to "echo kept"))
            assertFalse(store.ensureInitialPanel("Replacement"))
            assertEquals("Custom", store.panel("main").fields["label"])
            assertEquals("4", store.panel("main").fields["size"])
            assertEquals("echo kept", store.item("main:action").command)
        } finally { dir.deleteRecursively() }
    }

    @Test fun itemDraftRejectsStaleEditsAndAllowsClearingOptionalFields() {
        val dir = Files.createTempDirectory("edge-draft-test").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", emptyMap())
            store.saveItemDraft("main:status", mapOf("type" to "text", "every" to "10"), null)
            val snapshot = store.item("main:status").fields
            store.setItem("main:status", mapOf("run" to "echo changed"))
            try {
                store.saveItemDraft("main:status", snapshot, snapshot)
                fail("Stale editor overwrote external changes")
            } catch (_: IllegalStateException) { }
            val current = store.item("main:status").fields
            store.saveItemDraft("main:status", current - "every", current)
            assertFalse(store.item("main:status").fields.containsKey("every"))
            assertEquals("echo changed", store.item("main:status").command)
            rejects { store.saveItemDraft("main:status", mapOf("type" to "invalid"), current - "every") }
            assertEquals(current - "every", store.item("main:status").fields)
        } finally { dir.deleteRecursively() }
    }

    @Test fun commandsAreLiteralAndPreserveEquals() {
        val command = "printf '%s' \"a=b;\${value}\""
        val parsed = EdgeStore.parse("# comment\ntype=text\nrun=$command\nlabel=日本語\n")
        assertEquals(command, parsed["run"])
        assertEquals(parsed, EdgeStore.parse(EdgeStore.encode(parsed)))
    }

    @Test fun idsCannotEscapeTheirPanelOrBeAmbiguous() {
        for (id in listOf("../x:y", "x:../y", "x:y:z", "x", "x:", "x/y:z", "x:y\n")) {
            rejects { EdgeStore.target(id) }
        }
        assertEquals("main" to "battery-1", EdgeStore.target("main:battery-1"))
    }

    @Test fun malformedFieldsDoNotSilentlyBecomeDefaults() {
        rejects { EdgeStore.parse("type=text\ntype=run") }
        rejects { EdgeStore.parse("label") }
        rejects { EdgeStore.validateItem(mapOf("type" to "unknown")) }
        rejects { EdgeStore.validateItem(mapOf("every" to "-1")) }
        rejects { EdgeStore.validateItem(mapOf("every" to "1")) }
        rejects { EdgeStore.validateItem(mapOf("timeout" to "forever")) }
        rejects { EdgeStore.validateItem(mapOf("run" to "echo first\nrun=second")) }
        rejects { EdgeStore.validatePanel(mapOf("x" to "NaN")) }
        rejects { EdgeStore.validatePanel(mapOf("y" to "Infinity")) }
        rejects { EdgeStore.validatePanel(mapOf("size" to "0")) }
    }

    @Test fun handleOptionsPersistAndRejectInvalidUpdates() {
        val dir = Files.createTempDirectory("edge-handle-test").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", mapOf("handle" to "bar", "size" to "2", "length" to "1",
                "open" to "swipe", "alpha" to "0.3"))
            store.setItem("main:back", mapOf("run" to "z2-key back"))
            for (fields in listOf(mapOf("open" to "slide"), mapOf("alpha" to "NaN"),
                mapOf("alpha" to "0"), mapOf("alpha" to "1.1"), mapOf("size" to "1"))) {
                rejects { store.setPanel("main", fields) }
            }
            store.setPanel("main", mapOf("side" to "left", "offset" to "100"))
            val panel = EdgeStore(dir).panel("main")
            assertEquals("swipe", panel.fields["open"])
            assertEquals("0.3", panel.fields["alpha"])
            assertEquals("2", panel.fields["size"])
            assertEquals("100", panel.fields["offset"])
            assertEquals("z2-key back", panel.items.single().command)
            store.setPanel("main", mapOf("handle" to "button", "open" to "both"))
            assertEquals("both", store.panel("main").fields["open"])
        } finally { dir.deleteRecursively() }
    }

    @Test fun movingItemsPersistsBothDirectionsAndPreservesContent() {
        val dir = Files.createTempDirectory("edge-order-test").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", mapOf("layout" to "grid"))
            listOf("a", "b", "c").forEach { store.setItem("main:$it", mapOf("run" to "echo $it")) }
            store.moveItem("main", "a", "c", after = true)
            assertEquals(listOf("b", "c", "a"), store.panel("main").items.map { it.id })
            store.moveItem("main", "a", "b")
            assertEquals(listOf("a", "b", "c"), store.panel("main").items.map { it.id })
            assertEquals(listOf(0, 1, 2), store.panel("main").items.map { it.order })
            assertEquals("echo a", store.item("main:a").command)
            rejects { store.moveItem("main", "missing", "a") }
            rejects { store.setPanel("main", mapOf("layout" to "invalid")) }
            assertEquals("grid", store.panel("main").fields["layout"])
        } finally { dir.deleteRecursively() }
    }

    @Test fun tabsRejectInvalidGraphsAndDeletionDetachesWithoutDeletingChildren() {
        val dir = Files.createTempDirectory("edge-tabs-test").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", mapOf("handle" to "bar"))
            store.addTab("main", "work", "Work")
            store.addTab("main", "home", "Home")
            assertEquals(listOf("work", "home"), store.panel("main").tabs)
            rejects { store.setPanel("main", mapOf("tabs" to "missing")) }
            rejects { store.setPanel("work", mapOf("tabs" to "main")) }
            rejects { store.setPanel("other", mapOf("tabs" to "work")) }
            rejects { store.setPanel("main", mapOf("tabs" to "work,work")) }
            store.removePanel("work")
            assertEquals(listOf("home"), store.panel("main").tabs)
            store.removePanel("main")
            assertEquals("Home", store.panel("home").fields["label"])
            assertEquals(1, store.panels().size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun deletingOnePanelDoesNotDeleteOtherPanelsOrLinkedFiles() {
        val dir = Files.createTempDirectory("edge-delete-test").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", mapOf("handle" to "bar"))
            store.setPanel("keep", mapOf("handle" to "button"))
            store.setItem("main:a", mapOf("label" to "A"))
            store.setItem("keep:b", mapOf("label" to "B"))
            java.nio.file.Files.createSymbolicLink(java.io.File(dir, "main/link").toPath(), java.io.File(dir, "keep").toPath())
            rejects { store.removePanel("../keep") }
            assertEquals(1, store.removePanel("main"))
            assertEquals("B", store.item("keep:b").fields["label"])
            rejects { store.removePanel("main") }
        } finally { dir.deleteRecursively() }
    }

    @Test fun panelDimensionsPersistAndInvalidUpdatesLeaveTheFileIntact() {
        val dir = Files.createTempDirectory("edge-dimensions-test").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", mapOf("width" to "80%", "height" to "240.5"))
            for (invalid in listOf("0", "-1", "101%", "NaN", "Infinity", "24dp", "10001", "")) {
                rejects { store.setPanel("main", mapOf("width" to invalid)) }
                rejects { store.setPanel("main", mapOf("height" to invalid)) }
            }
            assertEquals("80%", store.panel("main").fields["width"])
            assertEquals("240.5", store.panel("main").fields["height"])
            assertEquals(800, EdgeStore.dimensionPixels("80%", 1000, 3f))
            assertEquals(720, EdgeStore.dimensionPixels("240", 1000, 3f))
            assertEquals(1000, EdgeStore.dimensionPixels("10000", 1000, 3f))
            assertEquals(1, EdgeStore.dimensionPixels("0.01%", 1000, 3f))
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidUpdatePreservesExistingDefinitionAndMovePreservesItems() {
        val dir = Files.createTempDirectory("edge-store-test").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", mapOf("handle" to "button", "x" to "20"))
            store.setItem("main:clock", mapOf("type" to "text", "run" to "date", "order" to "2"))
            store.setItem("main:note", mapOf("type" to "input", "run" to "cat", "order" to "1"))
            rejects { store.setItem("main:clock", mapOf("every" to "bad")) }
            assertEquals("date", store.item("main:clock").command)
            assertFalse(store.item("main:clock").fields.containsKey("every"))
            store.setPanel("main", mapOf("x" to "85", "y" to "60"))
            assertEquals(listOf("note", "clock"), store.panel("main").items.map { it.id })
            assertEquals("85", store.panel("main").fields["x"])
            assertFalse(store.enabled())
            store.enable(true)
            assertTrue(store.enabled())
            store.enable(false)
            assertEquals(2, store.panel("main").items.size)
        } finally { dir.deleteRecursively() }
    }
    @Test fun presentationChangesPreserveItemsAndRejectInvalidPositions() {
        val dir = Files.createTempDirectory("edge-presentation-test").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("apps", mapOf("width" to "64", "height" to "60%", "fit" to "fixed",
                "place" to "right", "flow" to "vertical", "labels" to "off", "tabbar" to "auto"))
            store.setItem("apps:launch", mapOf("run" to "echo unchanged"))
            val original = store.panel("apps").fields
            for ((key, value) in listOf("at" to "NaN%,0%", "at" to "101%,0%", "at" to "0,0",
                "columns" to "0", "icon-size" to "193", "fit" to "unknown", "title" to "yes")) {
                rejects { store.setPanel("apps", mapOf(key to value)) }
                assertEquals(original, store.panel("apps").fields)
            }
            store.setPanel("apps", mapOf("at" to "0%,100%", "columns" to "auto"))
            store.setPanel("apps", mapOf("at" to ""))
            assertEquals("echo unchanged", EdgeStore(dir).item("apps:launch").command)
            assertEquals(1f to 0.5f, EdgePanelPosition.fractions(store.panel("apps").fields))
        } finally { dir.deleteRecursively() }
    }

}
