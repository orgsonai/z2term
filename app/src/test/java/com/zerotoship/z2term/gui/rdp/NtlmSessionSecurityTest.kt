package com.zerotoship.z2term.gui.rdp

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class NtlmSessionSecurityTest {
    private val flags = NtlmWire.NEGOTIATE_EXTENDED_SESSION_SECURITY or
        NtlmWire.NEGOTIATE_SIGN or NtlmWire.NEGOTIATE_SEAL or
        NtlmWire.NEGOTIATE_KEY_EXCHANGE or NtlmWire.NEGOTIATE_128
    private val key = ByteArray(16) { (it * 7).toByte() }

    @Test
    fun clientAndServerExchangeMultipleStatefulMessages() {
        val client = NtlmSessionSecurity(key, flags, clientRole = true)
        val server = NtlmSessionSecurity(key, flags, clientRole = false)

        val first = "client binding".toByteArray()
        val second = "credentials".toByteArray()
        val reply = "server binding".toByteArray()
        assertArrayEquals(first, server.unwrap(client.wrap(first)))
        assertArrayEquals(reply, client.unwrap(server.wrap(reply)))
        assertArrayEquals(second, server.unwrap(client.wrap(second)))
    }

    @Test
    fun sealedPayloadIsEncryptedAndTamperingIsRejected() {
        val client = NtlmSessionSecurity(key, flags, clientRole = true)
        val server = NtlmSessionSecurity(key, flags, clientRole = false)
        val plain = "not visible on wire".toByteArray()
        val sealed = client.wrap(plain)

        assertFalse(sealed.copyOfRange(16, sealed.size).contentEquals(plain))
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 1).toByte()
        assertThrows(IOException::class.java) { server.unwrap(sealed) }
    }
}
