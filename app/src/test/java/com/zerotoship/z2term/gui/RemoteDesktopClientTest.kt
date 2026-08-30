package com.zerotoship.z2term.gui

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteDesktopClientTest {
    @Test
    fun tapKeyKeepsDownUpOrderAtTheProtocolBoundary() {
        val client = RecordingClient()

        client.tapKey(0xFF0D)

        assertEquals(listOf(0xFF0D to true, 0xFF0D to false), client.keys)
    }

    private class RecordingClient : RemoteDesktopClient {
        override val width = 0
        override val height = 0
        override val desktopName = ""
        override val frame: Bitmap? = null
        override val frameLock = Any()
        override val redraw: StateFlow<Int> = MutableStateFlow(0)
        override var onRemoteClipboardText: ((String) -> Unit)? = null
        val keys = mutableListOf<Pair<Int, Boolean>>()

        override fun connect(timeoutMs: Int) = Unit
        override fun run() = Unit
        override fun sendPointerEvent(buttonMask: Int, x: Int, y: Int) = Unit
        override fun sendKeyEvent(keysym: Int, down: Boolean) {
            keys += keysym to down
        }
        override fun close() = Unit
    }
}
