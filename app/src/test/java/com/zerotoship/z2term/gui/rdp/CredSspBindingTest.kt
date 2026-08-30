package com.zerotoship.z2term.gui.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CredSspBindingTest {
    @Test
    fun versionSixBindingIncludesDirectionNullNonceAndSubjectPublicKey() {
        val nonce = ByteArray(32)
        val publicKey = byteArrayOf(1, 2, 3)

        val client = CredSspBinding.clientHash(nonce, publicKey)
        val server = CredSspBinding.serverHash(nonce, publicKey)

        assertEquals("e1e0e7bbbd1efcbe459a5253b2f0839a676027203f86155c53b75ceb87f89c4c", client.hex())
        assertEquals("b4f2c13c3a9badf1d36d23a85d33afc979a1ffec6a9fcc3e9b7026299ad0c77e", server.hex())
        assertFalse(client.contentEquals(server))
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
