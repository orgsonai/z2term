package com.zerotoship.z2term.gui.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RdpNegotiationTest {
    @Test
    fun connectionRequestAdvertisesTlsAndCredSsp() {
        assertEquals(
            "030000130ee000000000000100080003000000",
            RdpNegotiation.connectionRequest().hex(),
        )
    }

    @Test
    fun connectionConfirmSelectsCredSsp() {
        val confirm = "030000130ed000000000000200080002000000".hexBytes()

        assertEquals(RdpNegotiation.PROTOCOL_HYBRID, RdpNegotiation.selectedProtocol(confirm))
    }

    @Test
    fun negotiationFailureKeepsTheServerFailureCode() {
        val failure = "030000130ed000000000000300080005000000".hexBytes()

        val error = assertThrows(RdpNegotiationException::class.java) {
            RdpNegotiation.selectedProtocol(failure)
        }
        assertEquals(5, error.failureCode)
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
