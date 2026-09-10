package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ActionStoreEditingTest {
    private val screen = ActionDefinition.Screen(100, 200, 0)

    @Test fun concurrentCliSaveRejectsStaleGuiSaveAndDelete() {
        val dir = Files.createTempDirectory("action-edit-conflict").toFile()
        try {
            val store = ActionStore(dir)
            store.save("demo", "version=1\nwait 10", screen)
            val expected = store.readText("demo")
            ActionStore(dir).save("demo", "version=1\nwait 20", screen)
            val latest = store.readText("demo")
            assertThrows(ActionStore.EditConflict::class.java) {
                store.saveEdited("demo", "version=1\nwait 30", screen, expected)
            }
            assertThrows(ActionStore.EditConflict::class.java) { store.deleteEdited("demo", expected) }
            assertEquals(latest, store.readText("demo"))
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally { dir.deleteRecursively() }
    }

    @Test fun newNamesDoNotOverwriteAndDeletedDraftsAreNotResurrected() {
        val dir = Files.createTempDirectory("action-edit-name").toFile()
        try {
            val store = ActionStore(dir)
            store.saveEdited("demo", "version=1\nwait 10", screen, null)
            val expected = store.readText("demo")
            assertThrows(ActionStore.EditConflict::class.java) {
                store.saveEdited("demo", "version=1\nwait 20", screen, null)
            }
            store.delete("demo")
            assertThrows(ActionStore.EditConflict::class.java) {
                store.saveEdited("demo", expected, screen, expected)
            }
            assertTrue(store.names().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun brokenTextCanBeRepairedButInvalidReplacementDoesNotTouchDisk() {
        val dir = Files.createTempDirectory("action-edit-repair").toFile()
        try {
            val broken = "# preserve while editing\nversion=1\nunknown"
            dir.resolve("demo.actions").writeText(broken)
            val store = ActionStore(dir)
            assertEquals(broken, store.readText("demo"))
            assertThrows(IllegalArgumentException::class.java) { store.read("demo") }
            assertThrows(IllegalArgumentException::class.java) { store.saveEdited("demo", broken, screen, broken) }
            assertEquals(broken, store.readText("demo"))
            store.saveEdited("demo", "version=1\nwait 1", screen, broken)
            assertEquals(listOf(ActionDefinition.Step.Wait(1)), store.read("demo").steps)
        } finally { dir.deleteRecursively() }
    }
}
