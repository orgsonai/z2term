package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialKeyLayoutTest {
    @Test fun defaultBarKeepsExistingKeysAndControlShortcuts() {
        val layout = specialKeyLayout()
        assertTrue(layout.validate().isEmpty())
        assertEquals(listOf("ESC", "TAB", "CTRL", "←", "↓", "↑", "→", "⏎", "^C", "^D", "^L"),
            layout.allKeys().map { it.label })
        assertEquals(listOf(KeyAction.Chord(setOf(ModKey.CTRL), text = "c")),
            layout.allKeys()[8].actionsFor(KeyGesture.TAP))
        assertTrue(layout.allKeys()[7].repeatable)
    }

    @Test fun editingAndSerializationKeepTheBarIndependentOfFullKeyboardFaces() {
        val original = specialKeyLayout("補助キー").withEditorDefaults()
        assertNull(original.symbolRows)
        val edited = original.copy(rows = listOf(KeyRow(listOf(
            KeySlot.of(KeyDef.named("F5", NamedKey.F5)),
            KeySlot.of(KeyDef.text("文字列", "日本語")),
        ))))
        val restored = KeyLayoutCodec.decode(KeyLayoutCodec.encode(edited))!!
        assertEquals(edited, restored)
        assertEquals(original, restored.restoreDefaults())
        assertNull(restored.withEditorDefaults().symbolRows)
    }
}
