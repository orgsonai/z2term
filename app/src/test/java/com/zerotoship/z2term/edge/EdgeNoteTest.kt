package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EdgeNoteTest {
    @Test fun editingUndoRedoAndReopeningPreserveMultilineUnicode() {
        val dir = Files.createTempDirectory("edge-note-test").toFile()
        try {
            val file = File(dir, "note.txt")
            val note = EdgeNote(file)
            note.edit("日本語\nsecond line")
            note.edit("日本語\nthird line")
            assertEquals("日本語\nsecond line", note.undo())
            assertEquals("日本語\nthird line", note.redo())
            note.save()
            assertFalse(note.dirty)
            assertEquals("日本語\nthird line", EdgeNote(file).text)
            note.undo(); note.edit("別の文")
            assertFalse(note.canRedo)
        } finally { dir.deleteRecursively() }
    }

    @Test fun externalEditsAreNotOverwrittenAndRecoveryKeepsOurEdits() {
        val dir = Files.createTempDirectory("edge-note-conflict-test").toFile()
        try {
            val file = File(dir, "note.txt").apply { writeText("original") }
            val note = EdgeNote(file)
            note.edit("our edit")
            file.writeText("external edit")
            try { note.save(); fail("Expected conflict") } catch (_: IllegalStateException) { }
            assertEquals("external edit", file.readText())
            assertTrue(note.dirty)
            val recovered = note.recover(File(dir, "recovery"))
            assertEquals("our edit", recovered.readText())
            assertFalse(note.dirty)
            assertEquals("external edit", file.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun sizeLimitCountsUtf8BytesAndDoesNotReplacePreviousText() {
        val dir = Files.createTempDirectory("edge-note-size-test").toFile()
        try {
            val note = EdgeNote(File(dir, "note.txt"))
            note.edit("small")
            try { note.edit("字".repeat(22000)); fail("Expected size limit") } catch (_: IllegalArgumentException) { }
            assertEquals("small", note.text)
            val huge = File(dir, "huge.txt").apply { writeText("x".repeat(65537)) }
            try { EdgeNote(huge); fail("Expected size limit") } catch (_: IllegalArgumentException) { }
        } finally { dir.deleteRecursively() }
    }
}
