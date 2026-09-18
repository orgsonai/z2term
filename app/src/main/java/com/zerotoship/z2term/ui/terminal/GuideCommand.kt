package com.zerotoship.z2term.ui.terminal

import kotlinx.coroutines.delay

/** Paste as one editing operation, then submit outside the bracketed-paste envelope. */
internal suspend fun sendGuideCommand(command: String, write: (ByteArray) -> Unit, pasteAndSubmit: (String) -> Unit) {
    write(byteArrayOf(0x03))
    // INTR flushes the pending input queue; let the shell return to its prompt first.
    delay(150)
    pasteAndSubmit(command)
}

/** Preserve localized names and deferred shell expansion as one shell argument. */
internal fun guideShellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
