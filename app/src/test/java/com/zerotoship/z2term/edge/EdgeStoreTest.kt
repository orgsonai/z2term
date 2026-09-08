package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EdgeStoreTest {
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected invalid definition to be rejected") }
        catch (_: IllegalArgumentException) { }
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
}
