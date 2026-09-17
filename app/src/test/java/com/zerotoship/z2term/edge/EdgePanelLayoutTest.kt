package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EdgePanelLayoutTest {
    @Test fun memoTerminalAndEmptyTabsShareTheBoundedWindowAfterReload() {
        val dir = Files.createTempDirectory("edge-tab-frame").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("memo", mapOf("width" to "77%", "height" to "61%", "close" to "off"))
            store.setItem("memo:note", mapOf("type" to "note"))
            store.addTab("memo", "shell", "Terminal")
            store.addTab("memo", "empty", "Empty")
            store.setItem("shell:terminal", mapOf("type" to "terminal"))
            store.setPanel("other", emptyMap())
            val panels = EdgeStore(dir).panels()
            assertTrue(EdgePanelLayout.bounded(panels.first { it.id == "memo" }, panels))
            assertFalse(EdgePanelLayout.bounded(panels.first { it.id == "other" }, panels))
            store.removeItem("shell:terminal")
            val withoutTerminal = EdgeStore(dir).panels()
            assertFalse(EdgePanelLayout.bounded(withoutTerminal.first { it.id == "memo" }, withoutTerminal))
        } finally { dir.deleteRecursively() }
    }

    @Test fun clearingAnEditedLabelSurvivesSavingWithoutShowingInternalIds() {
        val dir = Files.createTempDirectory("edge-empty-label").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("panel", emptyMap())
            for (type in listOf("terminal", "note", "argument", "macro", "result", "run")) {
                val target = "panel:item_$type"
                store.setItem(target, mapOf("type" to type, "label" to "Title") +
                    if (type == "run") mapOf("icon" to "▶") else emptyMap())
                val before = store.item(target)
                store.saveItemDraft(target, before.fields + ("label" to ""), before.fields)
                assertEquals("", EdgePanelLayout.label(EdgeStore(dir).item(target), "Application"))
                if (type != "run") assertEquals("", EdgePanelLayout.label(before.copy(fields = mapOf("type" to type))))
            }
            assertEquals("Application", EdgePanelLayout.label(EdgeStore.Item("app", emptyMap()), "Application"))
        } finally { dir.deleteRecursively() }
    }
    @Test fun buttonsWithoutAnIconOrNameStillHaveAVisibleAction() {
        for (label in listOf(null, "", "  ")) {
            val fields = mapOf("type" to "run") + (label?.let { mapOf("label" to it) } ?: emptyMap())
            val item = EdgeStore.Item("item_opaque", fields)
            assertEquals("Run", EdgePanelLayout.label(item, buttonLabel = "Run"))
            assertEquals(if (label == null) "Application" else "", EdgePanelLayout.label(item, "Application", "Run"))
        }
        val iconOnly = EdgeStore.Item("icon", mapOf("type" to "run", "label" to "", "icon" to "▶"))
        assertEquals("", EdgePanelLayout.label(iconOnly, buttonLabel = "Run"))
    }

}
