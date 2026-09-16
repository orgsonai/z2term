package com.zerotoship.z2term.edge

import com.zerotoship.z2term.ui.snippets.ShellHistory
import org.junit.Assert.*
import org.junit.Test

class EdgeTerminalHistoryTest {
    @Test fun arrowsBrowseTheRegularShellHistoryAndRestoreTheDraft() {
        val history = EdgeTerminalHistory()
        history.load(ShellHistory.merge(ShellHistory.parseBash("pwd\nls -la\n"),
            ShellHistory.parseZsh(": 10:0;echo 日本語\n")).map { it.command })
        assertNull(history.newer("unfinished "))
        assertEquals("echo 日本語", history.older("unfinished "))
        assertEquals("ls -la", history.older("echo 日本語"))
        assertEquals("pwd", history.older("ls -la"))
        assertEquals("pwd", history.older("pwd"))
        assertEquals("ls -la", history.newer("pwd"))
        assertEquals("echo 日本語", history.newer("ls -la"))
        assertEquals("unfinished ", history.newer("echo 日本語"))
        assertFalse(history.browsing)
    }

    @Test fun editingARecalledLineDoesNotLoseItOrTheOriginalDraft() {
        val history = EdgeTerminalHistory()
        history.load(listOf("ls", "pwd"))
        assertEquals("ls", history.older("new input"))
        assertEquals("pwd", history.older("ls -la"))
        assertEquals("ls -la", history.newer("pwd"))
        assertEquals("new input", history.newer("ls -la"))
    }

    @Test fun acceptedCommandsJoinThisOpeningWithoutDuplicatingSharedEntries() {
        val history = EdgeTerminalHistory()
        history.load(listOf("pwd", "ls"))
        history.accepted("echo current")
        history.accepted("echo current")
        history.load(listOf("pwd", "echo current", "ls"))
        assertEquals("echo current", history.older(""))
        assertEquals("pwd", history.older("echo current"))
        assertEquals("ls", history.older("pwd"))
        val reopened = EdgeTerminalHistory()
        reopened.load(listOf("pwd", "ls"))
        assertEquals("pwd", reopened.older(""))
    }

    @Test fun unsupportedHistoryEntriesAreSkippedRatherThanTruncatedOrFlattened() {
        val history = EdgeTerminalHistory()
        history.load(listOf("first\nsecond", "x".repeat(16385), "bad\u0000text", "", "  ", "printf '%s' '\$(exit 9)'"))
        assertEquals("printf '%s' '\$(exit 9)'", history.older("draft"))
        assertEquals("draft", history.newer("printf '%s' '\$(exit 9)'"))
        history.load(emptyList())
        assertNull(history.older("still typing"))
    }
}
