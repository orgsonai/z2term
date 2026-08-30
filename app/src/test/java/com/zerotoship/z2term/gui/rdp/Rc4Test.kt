package com.zerotoship.z2term.gui.rdp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Rc4Test {
    @Test
    fun matchesPublishedRc4Vector() {
        val encrypted = Rc4("Key".toByteArray()).process("Plaintext".toByteArray())

        assertEquals("bbf316e8d940af0ad3", encrypted.hex())
    }

    @Test
    fun keepsCipherStateAcrossCalls() {
        val oneShot = Rc4("Secret".toByteArray()).process("message checksum".toByteArray())
        val streaming = Rc4("Secret".toByteArray())

        assertArrayEquals(
            oneShot,
            streaming.process("message".toByteArray()) + streaming.process(" checksum".toByteArray()),
        )
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
