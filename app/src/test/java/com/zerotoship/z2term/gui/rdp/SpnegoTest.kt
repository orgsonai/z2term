package com.zerotoship.z2term.gui.rdp

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpnegoTest {
    @Test
    fun initialTokenContainsSpnegoAndNtlmNegotiate() {
        val negotiate = NtlmWire.negotiate()
        val token = Spnego.initialToken(negotiate)

        assertEquals(0x60, token[0].toInt() and 0xFF)
        assertArrayEquals(negotiate, Spnego.ntlmToken(token))
    }

    @Test
    fun responseTokenRoundTripsNtlmAuthenticate() {
        val authenticate = "NTLMSSP\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(72) { it.toByte() }

        assertArrayEquals(authenticate, Spnego.ntlmToken(Spnego.responseToken(authenticate)))
    }

    @Test
    fun rejectsResponseWithoutNtlmToken() {
        assertThrows(IOException::class.java) { Spnego.ntlmToken(Der.sequence(Der.integer(1))) }
    }
}
