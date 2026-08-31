package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Client Info、licensing、Demand/Confirm Active による RDP connection sequence。 */
internal object RdpActivation {
    const val CAP_BITMAP = 2
    const val CAP_ORDER = 3
    const val CAP_SURFACE_COMMANDS = 0x1C
    const val CAP_BITMAP_CODECS = 0x1D
    private const val PDUTYPE_DEACTIVATE_ALL = 6
    private const val PDUTYPE_DATAPDU = 7
    private const val PDUTYPE2_UPDATE = 0x02
    private const val PDUTYPE2_SET_ERROR_INFO = 0x2F
    private const val UPDATETYPE_BITMAP = 0x0001
    private const val SEC_INFO_PKT = 0x40
    private const val SEC_LICENSE_PKT = 0x80

    data class ActiveSession(
        val shareId: Int,
        val pduSource: Int,
        val serverCapabilities: Set<Int>,
        val clientCapabilities: Set<Int>,
    )

    private data class Demand(val shareId: Int, val source: Int, val caps: Set<Int>)
    private data class ShareData(val type: Int, val body: ByteArray)

    fun activate(
        input: DataInputStream,
        output: DataOutputStream,
        session: RdpMcs.Session,
        credentials: CredSspNtlm.Credentials,
        settings: RdpMcs.ClientSettings = RdpMcs.ClientSettings(),
    ): ActiveSession {
        output.write(clientInfo(session, credentials))
        output.flush()
        var licensed = false
        repeat(16) {
            val data = mcsData(RdpTlsTransport.readTpkt(input), session.ioChannelId) ?: return@repeat
            if (securityFlags(data) and SEC_LICENSE_PKT != 0) {
                readLicense(data)
                licensed = true
                return@repeat
            }
            if (!licensed) throw IOException("RDP Demand Active arrived before licensing completed")
            val demand = readDemand(data)
            val confirmation = confirmActive(session, demand, settings)
            output.write(confirmation.first)
            output.flush()
            return ActiveSession(demand.shareId, demand.source, demand.caps, confirmation.second)
        }
        throw IOException("RDP server did not send Demand Active")
    }

    fun finalizeConnection(
        input: DataInputStream,
        output: DataOutputStream,
        session: RdpMcs.Session,
        active: ActiveSession,
    ) {
        finalizationPackets(session, active).forEach(output::write)
        output.flush()

        var received = 0
        repeat(32) {
            val payload = mcsData(RdpTlsTransport.readTpkt(input), session.ioChannelId) ?: return@repeat
            val data = readShareData(payload, active.shareId) ?: return@repeat
            when (data.type) {
                0x1F -> {
                    val body = Cursor(data.body)
                    if (body.le16() != 1) throw IOException("invalid Server Synchronize PDU")
                    body.le16()
                    body.end()
                    received = received or 0x01
                }
                0x14 -> {
                    val body = Cursor(data.body)
                    val action = body.le16()
                    body.le16()
                    body.le32()
                    body.end()
                    received = when (action) {
                        4 -> received or 0x02
                        2 -> received or 0x04
                        else -> throw IOException("unexpected Server Control action: " + action)
                    }
                }
                0x28 -> {
                    val body = Cursor(data.body)
                    if (body.remaining != 0) {
                        body.le16()
                        body.le16()
                        val flags = body.le16()
                        val entrySize = body.le16()
                        if (flags != 3 || entrySize != 4) throw IOException("invalid Server Font Map PDU")
                    }
                    body.end()
                    received = received or 0x08
                }
            }
            if (received == 0x0F) return
        }
        throw IOException("RDP connection finalization did not complete")
    }

    /** 通常状態のslow-path PDUを1つ読み、Bitmap Update本体なら返す。 */
    fun readBitmapUpdate(
        input: DataInputStream,
        session: RdpMcs.Session,
        active: ActiveSession,
    ): ByteArray? = bitmapUpdate(RdpTlsTransport.readTpkt(input), session, active)

    internal fun bitmapUpdate(
        packet: ByteArray,
        session: RdpMcs.Session,
        active: ActiveSession,
    ): ByteArray? {
        val payload = mcsData(packet, session.ioChannelId) ?: return null
        val data = readShareData(payload, active.shareId) ?: return null
        return when (data.type) {
            PDUTYPE2_UPDATE -> {
                val body = Cursor(data.body)
                if (body.le16() == UPDATETYPE_BITMAP) data.body else null
            }
            PDUTYPE2_SET_ERROR_INFO -> {
                val body = Cursor(data.body)
                val error = body.le32()
                body.end()
                throw IOException("RDP server reported errorInfo=0x${error.toUInt().toString(16)}")
            }
            else -> null
        }
    }

    internal fun finalizationPackets(
        session: RdpMcs.Session,
        active: ActiveSession,
    ): List<ByteArray> {
        fun control(action: Int) = Writer().apply {
            le16(action)
            le16(0)
            le32(0)
        }.array()
        return listOf(
            shareData(session, active.shareId, 0x1F, Writer().apply {
                le16(1)
                le16(session.userChannelId)
            }.array()),
            shareData(session, active.shareId, 0x14, control(4)),
            shareData(session, active.shareId, 0x14, control(1)),
            shareData(session, active.shareId, 0x27, Writer().apply {
                le16(0)
                le16(0)
                le16(3)
                le16(50)
            }.array()),
        )
    }

    internal fun clientInfo(
        session: RdpMcs.Session,
        credentials: CredSspNtlm.Credentials,
    ): ByteArray {
        fun utf16(value: String) = value.toByteArray(StandardCharsets.UTF_16LE)
        val values = listOf(utf16(credentials.domain), utf16(credentials.user), utf16(credentials.password))
        require(values.all { it.size <= 510 }) { "RDP Client Info credential is too long" }
        val flags = 0x00000001 or 0x00000002 or 0x00000008 or 0x00000010 or 0x00000020 or
            0x00000100 or 0x00004000 or 0x00010000 or 0x00020000 or 0x00080000
        val info = Writer().apply {
            le32(0); le32(flags)
            values.forEach { le16(it.size) }
            le16(0); le16(0)
            (values + listOf(ByteArray(0), ByteArray(0))).forEach { bytes(it); le16(0) }
            val address = utf16("127.0.0.1\u0000")
            le16(2); le16(address.size); bytes(address); le16(0)
        }.array()
        val secured = Writer().apply { le16(SEC_INFO_PKT); le16(0); bytes(info) }.array()
        return sendData(session.userChannelId, session.ioChannelId, secured)
    }

    internal fun parseDemandActive(payload: ByteArray): ActiveSession {
        val demand = readDemand(payload)
        return ActiveSession(demand.shareId, demand.source, demand.caps, emptySet())
    }

    internal fun confirmActive(
        session: RdpMcs.Session,
        active: ActiveSession,
        settings: RdpMcs.ClientSettings = RdpMcs.ClientSettings(),
    ): Pair<ByteArray, Set<Int>> =
        confirmActive(session, Demand(active.shareId, active.pduSource, active.serverCapabilities), settings)

    private fun readDemand(payload: ByteArray): Demand {
        val c = Cursor(payload)
        val total = c.le16()
        val type = c.le16()
        val source = c.le16()
        if (total != payload.size || type and 0x0F != 1) throw IOException("expected RDP Demand Active PDU")
        val shareId = c.le32()
        val descriptorLength = c.le16()
        val combinedLength = c.le16()
        c.bytes(descriptorLength)
        if (combinedLength < 4 || c.remaining < combinedLength + 4) {
            throw IOException("invalid Demand Active capability length")
        }
        val caps = c.sub(combinedLength)
        val count = caps.le16()
        caps.le16()
        val types = linkedSetOf<Int>()
        repeat(count) {
            val capType = caps.le16()
            val length = caps.le16()
            if (length < 4) throw IOException("invalid RDP capability length")
            caps.bytes(length - 4)
            types += capType
        }
        caps.end()
        c.le32()
        c.end()
        return Demand(shareId, source, types)
    }

    private fun confirmActive(
        session: RdpMcs.Session,
        demand: Demand,
        settings: RdpMcs.ClientSettings,
    ): Pair<ByteArray, Set<Int>> {
        val capabilities = clientCapabilities(settings)
        val combined = Writer().apply {
            le16(capabilities.size)
            le16(0)
            capabilities.forEach { bytes(it.second) }
        }.array()
        val descriptor = "Z2TERM\u0000".toByteArray(StandardCharsets.US_ASCII)
        val body = Writer().apply {
            le32(demand.shareId)
            le16(0x03EA)
            le16(descriptor.size)
            le16(combined.size)
            bytes(descriptor)
            bytes(combined)
        }.array()
        val pdu = Writer().apply {
            le16(body.size + 6)
            le16(0x13)
            le16(session.userChannelId)
            bytes(body)
        }.array()
        return sendData(session.userChannelId, session.ioChannelId, pdu) to
            capabilities.mapTo(linkedSetOf()) { it.first }
    }
    private fun clientCapabilities(s: RdpMcs.ClientSettings): List<Pair<Int, ByteArray>> {
        fun cap(type: Int, body: Writer.() -> Unit): Pair<Int, ByteArray> {
            val payload = Writer().apply(body).array()
            return type to Writer().apply {
                le16(type)
                le16(payload.size + 4)
                bytes(payload)
            }.array()
        }
        return listOf(
            cap(1) {
                le16(4); le16(7); le16(0x200); le16(0); le16(0); le16(0)
                le16(0); le16(0); le16(0); u8(0); u8(0)
            },
            cap(CAP_BITMAP) {
                // 24bppを優先する。32bpp圧縮はRDP 6.0 planar codecになり、今回広告しない。
                le16(24); le16(1); le16(1); le16(1); le16(s.width); le16(s.height)
                le16(0); le16(1); le16(1); u8(0); u8(0); le16(1); le16(0)
            },
            // Capability Set自体はconnection sequenceの必須集合。orderSupport[32]を全ゼロにし、
            // 実装していないPrimary/Secondary drawing orderは一つも広告しない。
            cap(CAP_ORDER) {
                zero(16); le32(0); le16(1); le16(20); le16(0); le16(1); le16(0)
                le16(2); zero(32); le16(0); le16(0); le32(0); le32(230400)
                le16(0); le16(0); le16(0); le16(0)
            },
            cap(0x13) { le16(2); u8(0); u8(0); zero(32) },
            cap(8) { le16(1); le16(25); le16(25) },
            cap(0x0D) { le16(1); le16(0); le32(s.keyboardLayout); le32(4); le32(0); le32(12); zero(64) },
            cap(0x0F) { le32(0) },
            cap(0x10) { zero(48) },
            cap(0x14) { le32(0); le32(1600) },
            cap(0x0C) { le16(0); le16(0) },
            cap(9) { le16(0); le16(0) },
            cap(0x0E) { le16(1); le16(0) },
            cap(5) { le16(0); le16(0); le16(2); le16(2) },
            cap(0x0A) { le16(6); le16(0) },
            cap(7) { zero(8) },
        )
    }

    private fun readLicense(payload: ByteArray) {
        val c = Cursor(payload)
        if (c.le16() and SEC_LICENSE_PKT == 0) throw IOException("invalid RDP licensing header")
        c.le16()
        if (c.u8() != 0xFF) throw IOException("RDP server requires unsupported licensing exchange")
        c.u8()
        val size = c.le16()
        if (size != c.remaining + 4) throw IOException("invalid RDP licensing length")
        val status = c.le32()
        c.le32()
        c.le16()
        c.bytes(c.le16())
        c.end()
        if (status != 7) throw IOException("RDP licensing failed")
    }

    private fun securityFlags(payload: ByteArray): Int =
        if (payload.size < 2) 0 else (payload[0].toInt() and 0xFF) or
            ((payload[1].toInt() and 0xFF) shl 8)

    private fun shareData(
        session: RdpMcs.Session,
        shareId: Int,
        type: Int,
        body: ByteArray,
    ): ByteArray {
        val payload = Writer().apply {
            le16(18 + body.size)
            le16(0x17)
            le16(session.userChannelId)
            le32(shareId)
            u8(0)
            u8(1)
            le16(body.size)
            u8(type)
            u8(0)
            le16(0)
            bytes(body)
        }.array()
        return sendData(session.userChannelId, session.ioChannelId, payload)
    }

    private fun readShareData(payload: ByteArray, expectedShareId: Int): ShareData? {
        val c = Cursor(payload)
        val totalLength = c.le16()
        val pduType = c.le16()
        c.le16()
        if (totalLength != payload.size) throw IOException("invalid Share Control PDU length")
        when (pduType and 0x0F) {
            PDUTYPE_DEACTIVATE_ALL -> throw EOFException("RDP session was deactivated")
            PDUTYPE_DATAPDU -> Unit
            else -> return null
        }
        val shareId = c.le32()
        c.u8()
        c.u8()
        val uncompressedLength = c.le16()
        val type = c.u8()
        val compressedType = c.u8()
        val compressedLength = c.le16()
        if (shareId != expectedShareId) throw IOException("Share Data PDU has a different shareId")
        if (compressedType != 0 || compressedLength != 0) {
            throw IOException("compressed Share Data PDU is not supported")
        }
        val body = c.bytes(c.remaining)
        if (uncompressedLength != body.size) throw IOException("invalid Share Data PDU length")
        return ShareData(type, body)
    }
    private fun mcsData(packet: ByteArray, channel: Int): ByteArray? {
        val c = Cursor(x224(packet))
        if (c.u8() ushr 2 != 26) throw IOException("expected MCS Send Data Indication")
        c.be16()
        val actualChannel = c.be16()
        c.u8()
        val payload = c.bytes(c.perLength())
        c.end()
        return payload.takeIf { actualChannel == channel }
    }

    private fun sendData(user: Int, channel: Int, payload: ByteArray): ByteArray {
        val mcs = Writer().apply {
            u8(25 shl 2)
            be16(user - 1001)
            be16(channel)
            u8(0x70)
            perLength(payload.size)
            bytes(payload)
        }.array()
        return Writer().apply {
            u8(3)
            u8(0)
            be16(mcs.size + 7)
            bytes(byteArrayOf(2, 0xF0.toByte(), 0x80.toByte()))
            bytes(mcs)
        }.array()
    }

    private fun x224(packet: ByteArray): ByteArray {
        if (packet.size < 8 || packet[0] != 3.toByte() ||
            !packet.copyOfRange(4, 7).contentEquals(byteArrayOf(2, 0xF0.toByte(), 0x80.toByte()))) {
            throw IOException("invalid X.224 Data PDU")
        }
        val size = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        if (size != packet.size) throw IOException("invalid TPKT length")
        return packet.copyOfRange(7, packet.size)
    }

    private class Writer {
        private val out = ByteArrayOutputStream()
        fun u8(v: Int) = out.write(v and 0xFF)
        fun le16(v: Int) = bytes(byteArrayOf(v.toByte(), (v ushr 8).toByte()))
        fun be16(v: Int) = bytes(byteArrayOf((v ushr 8).toByte(), v.toByte()))
        fun le32(v: Int) = bytes(ByteArray(4) { (v ushr (it * 8)).toByte() })
        fun bytes(v: ByteArray) = out.write(v)
        fun zero(n: Int) = bytes(ByteArray(n))
        fun array(): ByteArray = out.toByteArray()
        fun perLength(n: Int) {
            require(n in 0..0x7FFF)
            if (n > 0x7F) be16(n or 0x8000) else u8(n)
        }
    }

    private class Cursor(private val data: ByteArray) {
        private var offset = 0
        val remaining get() = data.size - offset

        fun u8(): Int {
            if (remaining < 1) throw IOException("truncated RDP packet")
            return data[offset++].toInt() and 0xFF
        }

        fun le16() = u8() or (u8() shl 8)
        fun be16() = (u8() shl 8) or u8()
        fun le32() = u8() or (u8() shl 8) or (u8() shl 16) or (u8() shl 24)

        fun bytes(n: Int): ByteArray {
            if (n < 0 || remaining < n) throw IOException("truncated RDP packet")
            return data.copyOfRange(offset, offset + n).also { offset += n }
        }

        fun sub(n: Int) = Cursor(bytes(n))

        fun perLength(): Int {
            val first = u8()
            return if (first and 0x80 == 0) first else ((first and 0x7F) shl 8) or u8()
        }

        fun end() {
            if (remaining != 0) throw IOException("trailing RDP data")
        }
    }
}
