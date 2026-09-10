package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EdgeNoteTest {
    @Test fun undoAndRedoSurviveSavingAndMultipleReopens() = withHistory { file, history ->
        EdgeNote(file, history).apply {
            edit("一行目\n二行目"); edit("一行目\n三行目"); save()
        }
        EdgeNote(file, history).apply {
            assertTrue(canUndo)
            assertEquals("一行目\n二行目", undo())
            save()
        }
        EdgeNote(file, history).apply {
            assertTrue(canRedo)
            assertEquals("一行目\n三行目", redo())
            assertTrue(dirty)
            save()
        }
        EdgeNote(file, history).apply {
            assertFalse(canRedo)
            undo(); edit("新しい編集"); save()
        }
        assertFalse(EdgeNote(file, history).canRedo)
    }

    @Test fun returningToSavedTextStillPersistsRedoHistory() = withHistory { file, history ->
        file.writeText("saved")
        EdgeNote(file, history).apply {
            edit("changed"); undo()
            assertFalse(dirty)
            assertTrue(needsSave)
            save()
            assertFalse(needsSave)
        }
        assertEquals("changed", EdgeNote(file, history).redo())
    }

    @Test fun staleAndDamagedHistoryNeverReplacesTheDocument() = withHistory { file, history ->
        EdgeNote(file, history).apply { edit("saved"); save() }
        file.writeText("external")
        EdgeNote(file, history).apply {
            assertEquals("external", text)
            assertFalse(canUndo); assertFalse(canRedo)
        }
        history.writeBytes(byteArrayOf(0, 0, 0, 1, 127, 127, 127, 127))
        assertEquals("external", EdgeNote(file, history).text)
        assertFalse(EdgeNote(file, history).canUndo)
    }

    @Test fun persistedHistoryHasCountAndUtf8ByteBounds() = withHistory { file, history ->
        EdgeNote(file, history).apply {
            repeat(150) { edit("value $it") }
            save()
        }
        EdgeNote(file, history).apply {
            var count = 0
            while (canUndo) { undo(); count++ }
            assertEquals(EdgeNote.HISTORY_COUNT, count)
            assertEquals("value 49", text)
        }
        EdgeNote(file, history).apply {
            repeat(20) { edit("字".repeat(20000) + it) }
            save()
        }
        assertTrue(history.length() <= EdgeNote.LIMIT + EdgeNote.HISTORY_BYTES + 416L)
        EdgeNote(file, history).apply {
            var count = 0
            while (canUndo) { undo(); count++ }
            assertTrue(count in 1..4)
            save()
        }
        assertTrue(EdgeNote(file, history).canRedo)
    }

    @Test fun historyWriteFailureKeepsSavedTextAndCanBeRetried() = withHistory { file, history ->
        history.parentFile!!.mkdirs()
        history.mkdir()
        File(history, "occupied").writeText("keep")
        val note = EdgeNote(file, history)
        note.edit("saved")
        try { note.save(); fail("Expected history write failure") } catch (_: IllegalStateException) { }
        assertEquals("saved", file.readText())
        assertFalse(note.dirty)
        assertTrue(note.needsSave)
        history.deleteRecursively()
        note.save()
        assertTrue(EdgeNote(file, history).canUndo)
    }

    private fun withHistory(block: (File, File) -> Unit) {
        val dir = Files.createTempDirectory("edge-note-history-test").toFile()
        try { block(File(dir, "note.txt"), File(dir, ".history/note")) }
        finally { dir.deleteRecursively() }
    }

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
