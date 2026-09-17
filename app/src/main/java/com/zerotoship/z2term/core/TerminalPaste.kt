package com.zerotoship.z2term.core

/** Enter must follow the paste envelope in the same write; PTY writes are asynchronous. */
internal fun terminalPasteBytes(text: String, bracketed: Boolean, submit: Boolean = false): ByteArray {
    val body = text.replace('\n', '\r').toByteArray(Charsets.UTF_8)
    val paste = if (bracketed) "\u001b[200~".toByteArray(Charsets.US_ASCII) + body +
        "\u001b[201~".toByteArray(Charsets.US_ASCII) else body
    return if (submit) paste + byteArrayOf(0x0d) else paste
}
