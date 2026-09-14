package com.zerotoship.z2term.gui

import android.graphics.Bitmap
import com.zerotoship.z2term.ui.terminal.input.KeyModifiers
import com.zerotoship.z2term.ui.terminal.keyboard.NamedKey
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class GuiModifiedKeysTest {
    private class Recorder : RemoteDesktopClient {
        val events = mutableListOf<Pair<Int, Boolean>>()
        override val width = 1
        override val height = 1
        override val desktopName = ""
        override val frame: Bitmap? = null
        override val frameLock = Any()
        override val redraw = MutableStateFlow(0)
        override var onRemoteClipboardText: ((String) -> Unit)? = null
        override fun connect(timeoutMs: Int) = Unit
        override fun run() = Unit
        override fun sendPointerEvent(buttonMask: Int, x: Int, y: Int) = Unit
        override fun sendKeyEvent(keysym: Int, down: Boolean) { events.add(keysym to down) }
        override fun close() = Unit
    }

    @Test fun altArrowIsOneChordWithoutAnEscapeOrLiteralBracket() {
        val client = Recorder()
        GuiKeyMapper.sendNamedKey(client, NamedKey.UP, KeyModifiers(alt = true))
        assertEquals(listOf(
            0xFFE9 to true, 0xFF52 to true, 0xFF52 to false, 0xFFE9 to false,
        ), client.events)
    }

    @Test fun combinedModifiersAreReleasedBeforeTheNextKey() {
        val client = Recorder()
        GuiKeyMapper.sendNamedKey(client, NamedKey.F5, KeyModifiers(ctrl = true, alt = true, shift = true))
        GuiKeyMapper.sendNamedKey(client, NamedKey.LEFT, KeyModifiers())
        assertEquals(listOf(
            0xFFE1 to true, 0xFFE3 to true, 0xFFE9 to true,
            0xFFC2 to true, 0xFFC2 to false,
            0xFFE9 to false, 0xFFE3 to false, 0xFFE1 to false,
            0xFF51 to true, 0xFF51 to false,
        ), client.events)
    }

    @Test fun controlSpaceAndPunctuationAreNotDropped() {
        val client = Recorder()
        GuiKeyMapper.sendBytes(client, byteArrayOf(0x00))
        GuiKeyMapper.sendBytes(client, byteArrayOf(0x1C))
        assertEquals(listOf(
            0xFFE3 to true, 0x20 to true, 0x20 to false, 0xFFE3 to false,
            0xFFE3 to true, 0x5C to true, 0x5C to false, 0xFFE3 to false,
        ), client.events)
    }
}
