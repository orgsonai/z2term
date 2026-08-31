package com.zerotoship.z2term.gui.rdp

import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
        // Order Capability Setは必須だが、実装側のorderSupport配列は全ゼロ。
        assertTrue(confirmation.second.contains(RdpActivation.CAP_ORDER))
        assertFalse(confirmation.second.contains(RdpActivation.CAP_SURFACE_COMMANDS))
        assertFalse(confirmation.second.contains(RdpActivation.CAP_BITMAP_CODECS))
    }

    @Test
    fun extractsSlowPathBitmapUpdateAndRejectsDeactivateAll() {
        val active = RdpActivation.ActiveSession(0x11223344, 1002, emptySet(), emptySet())
        val bitmap = le16(1) + le16(0)
        val shareData = le16(18 + bitmap.size) + le16(0x17) + le16(1002) +
            le32(active.shareId) + byteArrayOf(0, 1) + le16(bitmap.size) +
            byteArrayOf(0x02, 0) + le16(0) + bitmap

        assertArrayEquals(bitmap, RdpActivation.bitmapUpdate(serverPacket(shareData), session, active))

        val nonBitmap = shareData.copyOf().also {
            it[it.size - bitmap.size] = 2 // Palette Update
        }
        assertEquals(null, RdpActivation.bitmapUpdate(serverPacket(nonBitmap), session, active))

        val deactivate = le16(6) + le16(0x16) + le16(1002)
        assertThrows(EOFException::class.java) {
            RdpActivation.bitmapUpdate(serverPacket(deactivate), session, active)
        }
    }

    private fun capability(type: Int, body: ByteArray) = le16(type) + le16(body.size + 4) + body
    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte())
    private fun le32(v: Int) = ByteArray(4) { (v ushr (it * 8)).toByte() }
    private fun be16(v: ByteArray, i: Int) =
        ((v[i].toInt() and 0xFF) shl 8) or (v[i + 1].toInt() and 0xFF)

    private fun serverPacket(payload: ByteArray): ByteArray {
        val mcs = byteArrayOf((26 shl 2).toByte()) + be16Bytes(3) +
            be16Bytes(session.ioChannelId) + byteArrayOf(0x70) + perLength(payload.size) + payload
        val body = byteArrayOf(2, 0xF0.toByte(), 0x80.toByte()) + mcs
        return byteArrayOf(3, 0) + be16Bytes(body.size + 4) + body
    }

    private fun perLength(value: Int): ByteArray =
        if (value <= 0x7F) byteArrayOf(value.toByte()) else be16Bytes(value or 0x8000)

    private fun be16Bytes(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean = indices.any { start ->
        start + needle.size <= size && needle.indices.all { this[start + it] == needle[it] }
    }
}
