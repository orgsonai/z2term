package com.zerotoship.z2term.gui.rdp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class CredSspTsRequestTest {
    @Test
    fun versionOnlyMatchesTheDerShapeFromMsCssp() {
        val encoded = CredSspTsRequest().encode()

        assertEquals("3005a003020106", encoded.hex())
        assertEquals(CredSspTsRequest(), CredSspTsRequest.decode(encoded))
    }

    @Test
    fun allFieldsRoundTripWithoutInterpretingTheSpnegoToken() {
        val request = CredSspTsRequest(
            negoToken = byteArrayOf(1, 2, 3),
            authInfo = byteArrayOf(4, 5),
            pubKeyAuth = byteArrayOf(6, 7, 8),
            errorCode = 0xC000006DL,
            clientNonce = ByteArray(32) { it.toByte() },
        )

        val decoded = CredSspTsRequest.decode(request.encode())

        assertEquals(request, decoded)
        assertArrayEquals(request.negoToken, decoded.negoToken)
    }

    @Test
    fun malformedDerDoesNotReadPastThePacket() {
        assertThrows(IOException::class.java) {
            CredSspTsRequest.decode("3005a0030201".hexBytes())
        }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
