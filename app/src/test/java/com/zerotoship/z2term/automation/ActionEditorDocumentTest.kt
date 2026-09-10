package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionEditorDocumentTest {
    @Test fun editingWaitPreservesCommentsCommandsAndCrLf() {
        val source = "# header\r\nversion=1\r\ntimeout=30\r\n\r\n# shell\r\ncommand printf '%s' '\$HOME # literal'\r\nwait 20\r\n"
        val document = ActionEditorDocument(source)
        val edited = document.replace(document.rows.last().line, "wait 800")
        assertEquals(source.replace("wait 20", "wait 800"), edited)
        assertEquals(source.replace("timeout=30", "timeout=60"), document.withHeader("timeout", "60"))
        assertEquals(source, ActionEditorDocument(edited).replace(document.rows.last().line, "wait 20"))
    }

    @Test fun reorderIncludesTargetDirectivesAndPreservesUnknownLines() {
        val source = "version=1\nlaunch org.example.first\n# target\nwait 800\ntarget org.example.second\ntap px 1 2\nfuture-action keep this\n"
        val document = ActionEditorDocument(source)
        val tap = document.rows.first { it.text.startsWith("tap ") }
        assertEquals("org.example.second", document.targetBefore(tap.line))
        val moved = ActionEditorDocument(document.move(tap.line, -1))
        assertEquals("org.example.first", moved.targetBefore(moved.rows.first { it.text.startsWith("tap ") }.line))
        assertTrue(moved.text.contains("# target\n"))
        assertTrue(moved.text.endsWith("future-action keep this\n"))
        assertEquals(source, ActionEditorDocument(moved.move(tap.line - 1, 1)).text)
    }

    @Test fun addingAndRemovingDoNotMergeSourceLines() {
        val document = ActionEditorDocument("version=1\n# keep")
        val appended = ActionEditorDocument(document.append("wait 10"))
        assertEquals("version=1\n# keep\nwait 10\n", appended.text)
        assertEquals("version=1\n# keep\n", appended.remove(appended.rows.single().line))
    }

    @Test fun malformedHeadersAndMultilineFormInputRequireTextEditing() {
        val duplicate = ActionEditorDocument("version=1\ntimeout=30\ntimeout=60\nwait 0")
        assertThrows(IllegalArgumentException::class.java) { duplicate.withHeader("timeout", "10") }
        assertThrows(IllegalArgumentException::class.java) { duplicate.append("wait 1\nkey home") }
        assertThrows(IllegalArgumentException::class.java) { duplicate.move(duplicate.rows.single().line, -1) }
    }
}
