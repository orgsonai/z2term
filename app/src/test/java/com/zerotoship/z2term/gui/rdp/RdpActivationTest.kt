package com.zerotoship.z2term.gui.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpActivationTest {
    private val session = RdpMcs.Session(1004, 1003, 0x00080004, 8)

    @Test
    fun clientInfoCarriesUnicodeCredentials() {
        val packet = RdpActivation.clientInfo(
            session,
            CredSspNtlm.Credentials(user = "z2test", domain = "LAB", password = "z2pass"),
        )
        assertEquals(packet.size, be16(packet, 2))
        assertEquals(25, (packet[7].toInt() and 0xFF) ushr 2)
        assertTrue(packet.containsSequence("z2test".toByteArray(Charsets.UTF_16LE)))
        assertTrue(packet.containsSequence("z2pass".toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun parsesDemandAndOmitsAdvancedBitmapCodecs() {
        val caps = capability(1, ByteArray(20)) +
            capability(2, ByteArray(24)) + capability(0x1C, ByteArray(4))
        val combined = le16(3) + le16(0) + caps
        val body = le32(0x11223344) + le16(4) + le16(combined.size) +
            "RDP\u0000".toByteArray() + combined + le32(0)
        val demand = le16(body.size + 6) + le16(0x11) + le16(1002) + body

        val active = RdpActivation.parseDemandActive(demand)
        assertEquals(0x11223344, active.shareId)
        assertEquals(setOf(1, 2, 0x1C), active.serverCapabilities)

        val confirmation = RdpActivation.confirmActive(session, active)
        assertTrue(confirmation.second.contains(RdpActivation.CAP_BITMAP))
        assertFalse(confirmation.second.contains(RdpActivation.CAP_SURFACE_COMMANDS))
        assertFalse(confirmation.second.contains(RdpActivation.CAP_BITMAP_CODECS))
    }

    private fun capability(type: Int, body: ByteArray) = le16(type) + le16(body.size + 4) + body
    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte())
    private fun le32(v: Int) = ByteArray(4) { (v ushr (it * 8)).toByte() }
    private fun be16(v: ByteArray, i: Int) =
        ((v[i].toInt() and 0xFF) shl 8) or (v[i + 1].toInt() and 0xFF)

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean = indices.any { start ->
        start + needle.size <= size && needle.indices.all { this[start + it] == needle[it] }
    }
}
