package com.zerotoship.z2term.gui.rdp

import java.io.IOException

/**
 * サーバーが CredSSP (ネットワークレベル認証) を選ばなかった。
 *
 * z2term は NLA のみを話すので、相手側で「ネットワークレベル認証を必須にする」を入れてもらう
 * しかない。**接続情報の打ち間違いとは別の失敗**なので、案内を分けられるよう専用の型にする。
 */
internal class RdpNlaUnsupportedException(val selectedProtocol: Int) :
    IOException("RDP server did not select CredSSP: 0x${selectedProtocol.toUInt().toString(16)}")

/** RDP の TPKT + X.224 Connection Request/Confirm と Enhanced Security negotiation。 */
internal object RdpNegotiation {
    const val PROTOCOL_SSL = 0x00000001
    const val PROTOCOL_HYBRID = 0x00000002
    const val PROTOCOL_HYBRID_EX = 0x00000008

    fun connectionRequest(
        requestedProtocols: Int = PROTOCOL_SSL or PROTOCOL_HYBRID,
    ): ByteArray {
        val packet = ByteArray(19)
        packet[0] = 3                    // TPKT version
        packet[1] = 0
        putU16Be(packet, 2, packet.size)
        packet[4] = 14                   // X.224 LI: bytes after this field
        packet[5] = 0xE0.toByte()        // Connection Request
        // dst-ref, src-ref, class-option remain zero
        packet[11] = 0x01                // RDP_NEG_REQ
        packet[12] = 0                   // flags
        putU16Le(packet, 13, 8)
        putU32Le(packet, 15, requestedProtocols)
        return packet
    }

    /** X.224 Connection Confirm を検証し、サーバーが選んだ protocol flag を返す。 */
    fun selectedProtocol(packet: ByteArray): Int {
        if (packet.size < 19) throw IOException("truncated X.224 Connection Confirm")
        if ((packet[0].toInt() and 0xFF) != 3 || packet[1].toInt() != 0) {
            throw IOException("invalid TPKT header")
        }
        val packetLength = u16Be(packet, 2)
        if (packetLength != packet.size) throw IOException("TPKT length mismatch: $packetLength != ${packet.size}")
        val li = packet[4].toInt() and 0xFF
        if (li + 5 > packet.size || (packet[5].toInt() and 0xF0) != 0xD0) {
            throw IOException("invalid X.224 Connection Confirm")
        }
        val negOffset = 5 + 6 // LI + fixed 6-byte X.224 confirm header
        if (negOffset + 8 > packet.size) throw IOException("RDP negotiation response is missing")
        val type = packet[negOffset].toInt() and 0xFF
        val length = u16Le(packet, negOffset + 2)
        if (length != 8 || negOffset + length > packet.size) throw IOException("invalid RDP negotiation length: $length")
        val value = u32Le(packet, negOffset + 4)
        return when (type) {
            0x02 -> value // RDP_NEG_RSP
            0x03 -> throw RdpNegotiationException(value)
            else -> throw IOException("unexpected RDP negotiation type: $type")
        }
    }

    private fun putU16Be(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value ushr 8).toByte()
        out[offset + 1] = value.toByte()
    }

    private fun putU16Le(out: ByteArray, offset: Int, value: Int) {
        out[offset] = value.toByte()
        out[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32Le(out: ByteArray, offset: Int, value: Int) {
        repeat(4) { out[offset + it] = (value ushr (it * 8)).toByte() }
    }

    private fun u16Be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun u16Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

internal class RdpNegotiationException(val failureCode: Int) :
    IOException("RDP security negotiation failed: 0x${failureCode.toUInt().toString(16)}")
