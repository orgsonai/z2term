package com.zerotoship.z2term.gui.rdp

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpMcsTest {
    @Test
    fun connectInitialCarriesBasicClientSettingsAndDesktopChannels() {
        val packet = RdpMcs.connectInitial(
            RdpMcs.ClientSettings(width = 800, height = 600, clientName = "Z2"),
        )

        val declaredLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        assertEquals(packet.size, declaredLength)
        assertArrayEquals(
            byteArrayOf(3, 0, packet[2], packet[3], 2, 0xF0.toByte(), 0x80.toByte(), 0x7F, 0x65),
            packet.copyOfRange(0, 9),
        )
        assertTrue(packet.containsSequence("Duca".toByteArray()))

        val core = packet.indexOfSequence(byteArrayOf(0x01, 0xC0.toByte(), 0xEA.toByte(), 0x00))
        assertTrue(core >= 0)
        val payload = core + 4
        assertArrayEquals(byteArrayOf(0x20, 0x03), packet.copyOfRange(payload + 4, payload + 6))
        assertArrayEquals(byteArrayOf(0x58, 0x02), packet.copyOfRange(payload + 6, payload + 8))
        assertArrayEquals(byteArrayOf(24, 0), packet.copyOfRange(payload + 136, payload + 138))
        assertArrayEquals(byteArrayOf(15, 0), packet.copyOfRange(payload + 138, payload + 140))
        assertArrayEquals(byteArrayOf(3, 9), packet.copyOfRange(payload + 140, payload + 142))
        assertArrayEquals(byteArrayOf(2, 0, 0, 0), packet.copyOfRange(payload + 208, payload + 212))

        assertTrue(packet.containsSequence(byteArrayOf(0x04, 0xC0.toByte(), 12, 0)))
        assertTrue(packet.containsSequence(byteArrayOf(0x02, 0xC0.toByte(), 12, 0)))
        assertTrue(packet.containsSequence(byteArrayOf(0x03, 0xC0.toByte())))
        assertTrue(packet.containsSequence("cliprdr".toByteArray()))
        assertTrue(packet.containsSequence("drdynvc".toByteArray()))
    }

    @Test
    fun parsesAttachUserConfirmUserId() {
        val packet = hex("03 00 00 0b 02 f0 80 2e 00 00 00")

        assertEquals(1001, RdpMcs.parseAttachUserConfirm(packet))
    }

    @Test
    fun rejectsFailedAttachUserConfirm() {
        val packet = hex("03 00 00 0b 02 f0 80 2e 01 00 00")

        assertThrows(IOException::class.java) { RdpMcs.parseAttachUserConfirm(packet) }
    }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean = indexOfSequence(needle) >= 0

    private fun ByteArray.indexOfSequence(needle: ByteArray): Int =
        indices.firstOrNull { start ->
            start + needle.size <= size && needle.indices.all { this[start + it] == needle[it] }
        } ?: -1

    private fun hex(value: String): ByteArray =
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
