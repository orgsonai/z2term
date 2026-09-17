package com.zerotoship.z2term.ui.terminal

import com.zerotoship.z2term.core.terminalPasteBytes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GuideCommandTest {
    @Test fun longUnicodeCommandAndEnterUseOneWriteAfterCancellingTheLine() = runBlocking {
        val command = "printf '%s' '" + "日本語 ".repeat(1200) + "'"
        val writes = mutableListOf<ByteArray>()
        sendGuideCommand(command, write = { writes.add(it) },
            pasteAndSubmit = { writes.add(terminalPasteBytes(it, bracketed = true, submit = true)) })
        assertEquals(2, writes.size)
        assertArrayEquals(byteArrayOf(0x03), writes[0])
        assertEquals("\u001b[200~$command\u001b[201~\r", writes[1].toString(Charsets.UTF_8))
        assertFalse(writes[1].contains(0x0a.toByte()))
    }

    @Test fun ordinaryPasteNeverSubmitsAndNonBracketedShellsGetOnlyOneEnter() {
        assertEquals("\u001b[200~one\rtwo\u001b[201~",
            terminalPasteBytes("one\ntwo", bracketed = true).toString(Charsets.UTF_8))
        assertEquals("pwd", terminalPasteBytes("pwd", bracketed = false).toString(Charsets.UTF_8))
        assertEquals("pwd\r", terminalPasteBytes("pwd", bracketed = false, submit = true).toString(Charsets.UTF_8))
    }
}
