package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ActionStoreTest {
    @Test fun invalidReplacementKeepsSavedMacroAndScreen() {
        val dir = Files.createTempDirectory("action-store").toFile()
        try {
            val store = ActionStore(dir)
            val screen = ActionDefinition.Screen(100, 200, 0)
            val saved = store.save("sample", "version=1\nscreen=current\ntap px 2 3", screen)
            try { store.save("sample", "version=1\nunknown", screen); fail("Invalid save accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(ActionDefinition.CURRENT_TARGET, (saved.steps.single() as ActionDefinition.Step.Stroke).target)
            assertEquals(saved, store.read("sample"))
            assertEquals(listOf("sample"), ActionStore(dir).names())
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
            store.delete("sample")
            assertTrue(store.names().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun refusesPathTraversalAndSymlinkDestinations() {
        val dir = Files.createTempDirectory("action-paths").toFile()
        try {
            val store = ActionStore(dir)
            val screen = ActionDefinition.Screen(100, 200, 0)
            val original = dir.resolve("original").apply { writeText("keep") }
            Files.createSymbolicLink(dir.resolve("link.actions").toPath(), original.toPath())
            for (name in listOf("../escape", "a/b", "a b", "", "link")) {
                try { store.save(name, "version=1\nwait 1", screen); fail("Unsafe name accepted") }
                catch (_: IllegalArgumentException) { }
            }
            assertEquals("keep", original.readText())
        } finally { dir.deleteRecursively() }
    }
}
