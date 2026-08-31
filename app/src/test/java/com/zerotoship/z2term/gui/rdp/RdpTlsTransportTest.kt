package com.zerotoship.z2term.gui.rdp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException

class RdpTlsTransportTest {
    @Test
    fun readTpktReadsOneWholePacket() {
        val packet = "030000130ed000000000000200080002000000".hexBytes()
        val input = DataInputStream(ByteArrayInputStream(packet + byteArrayOf(99)))

        assertArrayEquals(packet, RdpTlsTransport.readTpkt(input))
        assertEquals(99, input.readUnsignedByte())
    }

    @Test
    fun readTpktRejectsTruncation() {
        val truncated = "030000130ed0".hexBytes()

        assertThrows(EOFException::class.java) {
            RdpTlsTransport.readTpkt(DataInputStream(ByteArrayInputStream(truncated)))
        }
    }

    @Test
    fun subjectPublicKeyDropsTheSpkiAlgorithmAndUnusedBitCount() {
        // Minimal structural SPKI: SEQUENCE { SEQUENCE {}, BIT STRING 00 01 02 03 }.
        val spki = "30083000030400010203".hexBytes()

        assertArrayEquals(byteArrayOf(1, 2, 3), RdpTlsTransport.subjectPublicKey(spki))
    }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun onlyACertificateThatForbidsSigningTriggersTheRsaRetry() {
        // Windows が自動生成する RDP 証明書 = keyEncipherment / dataEncipherment だけ。
        // digitalSignature が無いので TLS 1.3 と ECDHE では相手が署名できない。
        val windowsSelfSigned = booleanArrayOf(false, false, true, true, false, false, false, false, false)
        assertTrue(RdpTlsTransport.signingIsForbidden(windowsSelfSigned))

        // 署名できる証明書、拡張そのものが無い証明書、空の拡張はやり直しの対象にしない
        // (握手の失敗を別の理由で握り潰さないため)。
        assertFalse(RdpTlsTransport.signingIsForbidden(booleanArrayOf(true, false, true)))
        assertFalse(RdpTlsTransport.signingIsForbidden(null))
        assertFalse(RdpTlsTransport.signingIsForbidden(booleanArrayOf()))
    }
}
