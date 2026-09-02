package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/** CredSSP 後の T.125 MCS / T.124 GCC Basic Settings Exchange と channel connection。 */
internal object RdpMcs {
    private const val PROTOCOL_HYBRID = 0x00000002
    private const val REQUESTED_PROTOCOLS = 0x00000003 // SSL | HYBRID
    private const val MCS_BASE_CHANNEL_ID = 1001
    private const val MCS_GLOBAL_CHANNEL_ID = 1003

    private const val CS_CORE = 0xC001
    private const val CS_SECURITY = 0xC002
    private const val CS_NET = 0xC003
    private const val CS_CLUSTER = 0xC004
    private const val SC_CORE = 0x0C01
    private const val SC_SECURITY = 0x0C02
    private const val SC_NET = 0x0C03

    private const val CLIENT_SUPPORT_SKIP_CHANNEL_JOIN = 0x0800
    private const val CLIENT_SUPPORT_DYNVC_GFX_PROTOCOL = 0x0100
    private const val CLIENT_WANT_32BPP_SESSION = 0x0002
    private const val SERVER_SUPPORT_SKIP_CHANNEL_JOIN = 0x00000008

    data class ClientSettings(
        val width: Int = 1024,
        val height: Int = 768,
        val clientName: String = "Z2TERM",
        val keyboardLayout: Int = 0x00000409,
    ) {
        init {
            require(width in 200..8192) { "invalid RDP width: $width" }
            require(height in 200..8192) { "invalid RDP height: $height" }
        }
    }

    data class Session(
        val userChannelId: Int,
        val ioChannelId: Int,
        val serverVersion: Int,
        val serverEarlyCapabilityFlags: Int,
        val staticChannels: Map<String, Int> = emptyMap(),
        /**
         * channel id ごとの Channel Definition の option。
         *
         * ⛔ **送信時の flag は宣言した option と一致させる。** `CHANNEL_FLAG_SHOW_PROTOCOL` は
         * `CHANNEL_OPTION_SHOW_PROTOCOL` を宣言した channel でだけ立てられる ([MS-RDPBCGR] 2.2.6.1)。
         * 宣言していない channel (drdynvc) に立てると、相手側の endpoint は 8 バイトの
         * Channel PDU Header まで**データとして**受け取り、解釈できずに黙り込む。
         */
        val channelOptions: Map<Int, Int> = emptyMap(),
    )

    private data class ServerSettings(
        val ioChannelId: Int,
        val version: Int,
        val earlyCapabilityFlags: Int,
        val channelIds: List<Int>,
    )

    fun connect(
        input: DataInputStream,
        output: DataOutputStream,
        settings: ClientSettings = ClientSettings(),
    ): Session {
        output.write(connectInitial(settings))
        output.flush()
        val server = parseConnectResponse(RdpTlsTransport.readTpkt(input))

        output.write(erectDomainRequest())
        output.write(attachUserRequest())
        output.flush()
        val userChannelId = parseAttachUserConfirm(RdpTlsTransport.readTpkt(input))

        if (server.earlyCapabilityFlags and SERVER_SUPPORT_SKIP_CHANNEL_JOIN == 0) {
            joinChannel(input, output, userChannelId, userChannelId)
            joinChannel(input, output, userChannelId, server.ioChannelId)
            server.channelIds.forEach { joinChannel(input, output, userChannelId, it) }
        }

        return Session(
            userChannelId = userChannelId,
            ioChannelId = server.ioChannelId,
            serverVersion = server.version,
            serverEarlyCapabilityFlags = server.earlyCapabilityFlags,
            staticChannels = STATIC_CHANNELS.mapIndexedNotNull { index, channel ->
                server.channelIds.getOrNull(index)?.let { channel.first to it }
            }.toMap(),
            channelOptions = STATIC_CHANNELS.mapIndexedNotNull { index, channel ->
                server.channelIds.getOrNull(index)?.let { it to channel.second }
            }.toMap(),
        )
    }

    internal fun connectInitial(settings: ClientSettings = ClientSettings()): ByteArray {
        val clientData = clientData(settings)
        val gcc = Bytes().apply {
            u8(0) // ConnectData::key = object
            bytes(byteArrayOf(5, 0, 20, 124, 0, 1)) // ITU-T T.124 (02/98) OID
            perLength(clientData.size + 14)
            u8(0) // conferenceCreateRequest
            u8(0x08) // optional userData present
            u8(0) // NumericString "1" has its minimum length
            u8(0x10) // digit 1 + padding digit 0
            u8(0) // alignment padding
            u8(1) // one UserData set
            u8(0xC0) // value present + h221NonStandard
            u8(0) // H.221 key has its minimum length (4)
            bytes("Duca".toByteArray(StandardCharsets.US_ASCII))
            perLength(clientData.size)
            bytes(clientData)
        }.array()

        val body = Bytes().apply {
            berOctet(byteArrayOf(1)) // callingDomainSelector
            berOctet(byteArrayOf(1)) // calledDomainSelector
            bytes(byteArrayOf(0x01, 0x01, 0xFF.toByte())) // upwardFlag = TRUE
            domainParameters(intArrayOf(34, 2, 0, 1, 0, 1, 65535, 2))
            domainParameters(intArrayOf(1, 1, 1, 1, 0, 1, 1056, 2))
            domainParameters(intArrayOf(65535, 65535, 65535, 1, 0, 1, 65535, 2))
            berOctet(gcc)
        }.array()

        val mcs = Bytes().apply {
            bytes(byteArrayOf(0x7F, 0x65)) // [APPLICATION 101] Connect-Initial
            berLength(body.size)
            bytes(body)
        }.array()
        return tpkt(mcs)
    }

    private fun clientData(settings: ClientSettings): ByteArray = Bytes().apply {
        val core = Bytes().apply {
            le32(0x00080004) // RDP 5+; later features are advertised separately
            le16(settings.width)
            le16(settings.height)
            le16(0xCA01) // legacy 8bpp field; highColorDepth below takes precedence
            le16(0xAA03) // secure access sequence = Ctrl+Alt+Del
            le32(settings.keyboardLayout)
            le32(2600)
            fixedUtf16(settings.clientName, 32)
            le32(4) // enhanced 101/102-key keyboard
            le32(0)
            le32(12)
            zeros(64) // IME file name
            le16(0xCA01)
            le16(1)
            le32(0)
            le16(24) // 24bpp fallback
            // Classic の 15/16/24bpp に加え、Graphics Pipeline の XRGB_8888 を受け取る。
            // GFX を宣言しながら 32bpp を省くと Windows 11 は Basic Settings Exchange で切断する。
            le16(0x000F)
            le16(
                0x0001 or CLIENT_WANT_32BPP_SESSION or
                    CLIENT_SUPPORT_DYNVC_GFX_PROTOCOL or CLIENT_SUPPORT_SKIP_CHANNEL_JOIN,
            )
            zeros(64) // clientDigProductId
            u8(0) // connection type not advertised
            u8(0)
            le32(PROTOCOL_HYBRID)
            le32(0) // desktopPhysicalWidth: unspecified
            le32(0) // desktopPhysicalHeight: unspecified
            le16(0) // landscape
            le32(100) // desktop scale factor
            le32(100) // device scale factor
        }.array()
        check(core.size == 230) { "unexpected Client Core payload size: ${core.size}" }
        userDataBlock(CS_CORE, core)

        val cluster = Bytes().apply {
            le32(0x00000011) // redirection supported, version 5; no multitransport
            le32(0)
        }.array()
        userDataBlock(CS_CLUSTER, cluster)

        val security = Bytes().apply {
            le32(0) // Standard RDP Security is disabled by TLS/NLA
            le32(0x0000001B) // compatible methods field used by Enhanced Security clients
        }.array()
        userDataBlock(CS_SECURITY, security)

        val network = Bytes().apply {
            le32(STATIC_CHANNELS.size)
            for ((name, options) in STATIC_CHANNELS) {
                val encoded = name.toByteArray(StandardCharsets.US_ASCII)
                require(encoded.size <= 8)
                bytes(encoded)
                zeros(8 - encoded.size)
                le32(options)
            }
        }.array()
        userDataBlock(CS_NET, network)
    }.array()

    private fun parseConnectResponse(packet: ByteArray): ServerSettings {
        val payload = x224Payload(packet)
        val cursor = Cursor(payload)
        cursor.expect(0x7F, 0x66) // [APPLICATION 102] Connect-Response
        val response = cursor.sub(cursor.berLength())
        cursor.requireEnd()

        response.expect(0x0A)
        val resultLength = response.berLength()
        if (resultLength != 1 || response.u8() != 0) throw IOException("MCS Connect-Response failed")
        response.berInteger() // calledConnectId is ignored by RDP
        response.skipBerSequence() // negotiated domain parameters
        val gcc = response.berOctet()
        response.requireEnd()
        return parseConferenceCreateResponse(gcc)
    }

    private fun parseConferenceCreateResponse(encoded: ByteArray): ServerSettings {
        val cursor = Cursor(encoded)
        if (cursor.u8() != 0) throw IOException("invalid GCC ConnectData key")
        cursor.expect(5, 0, 20, 124, 0, 1)
        cursor.perLength() // [MS-RDPBCGR] says the response connectPDU length is ignored
        if (cursor.u8() != 0x14) throw IOException("invalid GCC ConferenceCreateResponse")
        cursor.be16() // nodeID, based at 1001
        cursor.perInteger() // tag
        if (cursor.u8() != 0) throw IOException("GCC conference creation failed")
        if (cursor.u8() != 1) throw IOException("GCC response must contain one UserData set")
        cursor.u8() // UserData selection
        if (cursor.perLength() != 0) throw IOException("invalid GCC H.221 key length")
        if (!cursor.bytes(4).contentEquals("McDn".toByteArray(StandardCharsets.US_ASCII))) {
            throw IOException("invalid server-to-client H.221 key")
        }
        val serverData = cursor.sub(cursor.perLength())

        var version: Int? = null
        var requestedProtocol: Int? = null
        var earlyCapabilities = 0
        var ioChannelId: Int? = null
        var encryptionMethod: Int? = null
        var encryptionLevel: Int? = null
        var channelIds = emptyList<Int>()
        while (serverData.remaining > 0) {
            val type = serverData.le16()
            val length = serverData.le16()
            if (length < 4) throw IOException("invalid GCC server block length: $length")
            val block = serverData.sub(length - 4)
            when (type) {
                SC_CORE -> {
                    version = block.le32()
                    if (block.remaining >= 4) requestedProtocol = block.le32()
                    if (block.remaining >= 4) earlyCapabilities = block.le32()
                }
                SC_SECURITY -> {
                    encryptionMethod = block.le32()
                    encryptionLevel = block.le32()
                }
                SC_NET -> {
                    ioChannelId = block.le16()
                    val channelCount = block.le16()
                    channelIds = List(channelCount) { block.le16() }
                    if (channelCount % 2 == 1 && block.remaining >= 2) block.le16()
                }
            }
            block.skipRemaining() // optional suffixes and blocks not used yet
        }
        if (requestedProtocol != null && requestedProtocol != REQUESTED_PROTOCOLS) {
            throw IOException("server echoed a different RDP protocol: 0x${requestedProtocol.toString(16)}")
        }
        if (encryptionMethod != 0 || encryptionLevel != 0) {
            throw IOException("server requested Standard RDP Security inside TLS")
        }
        return ServerSettings(
            ioChannelId = ioChannelId ?: throw IOException("GCC response has no I/O channel"),
            version = version ?: throw IOException("GCC response has no Server Core Data"),
            earlyCapabilityFlags = earlyCapabilities,
            channelIds = channelIds,
        )
    }

    internal fun parseAttachUserConfirm(packet: ByteArray): Int {
        val cursor = Cursor(x224Payload(packet))
        val choice = cursor.u8()
        if (choice ushr 2 != 11 || choice and 0x02 == 0) {
            throw IOException("expected MCS Attach User Confirm")
        }
        if (cursor.u8() != 0) throw IOException("MCS Attach User failed")
        val userId = cursor.be16() + MCS_BASE_CHANNEL_ID
        cursor.requireEnd()
        return userId
    }

    private fun joinChannel(
        input: DataInputStream,
        output: DataOutputStream,
        userChannelId: Int,
        channelId: Int,
    ) {
        val request = Bytes().apply {
            u8(14 shl 2)
            be16(userChannelId - MCS_BASE_CHANNEL_ID)
            be16(channelId)
        }.array()
        output.write(tpkt(request))
        output.flush()

        val response = Cursor(x224Payload(RdpTlsTransport.readTpkt(input)))
        val choice = response.u8()
        if (choice ushr 2 != 15 || response.u8() != 0) throw IOException("MCS Channel Join failed")
        val initiator = response.be16() + MCS_BASE_CHANNEL_ID
        val requested = response.be16()
        val joined = if (response.remaining >= 2) response.be16() else requested
        response.requireEnd()
        if (initiator != userChannelId || requested != channelId || joined != channelId) {
            throw IOException("MCS Channel Join response does not match request")
        }
    }

    private fun erectDomainRequest(): ByteArray = tpkt(
        Bytes().apply {
            u8(1 shl 2)
            perInteger(0)
            perInteger(0)
        }.array(),
    )

    private fun attachUserRequest(): ByteArray = tpkt(byteArrayOf((10 shl 2).toByte()))

    private fun tpkt(mcs: ByteArray): ByteArray = Bytes().apply {
        val length = 7 + mcs.size
        u8(3)
        u8(0)
        be16(length)
        bytes(byteArrayOf(2, 0xF0.toByte(), 0x80.toByte()))
        bytes(mcs)
    }.array()

    private fun x224Payload(packet: ByteArray): ByteArray {
        if (packet.size < 8 || packet[0] != 3.toByte() || packet[1] != 0.toByte()) {
            throw IOException("invalid TPKT packet")
        }
        val length = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        if (length != packet.size || !packet.copyOfRange(4, 7).contentEquals(byteArrayOf(2, 0xF0.toByte(), 0x80.toByte()))) {
            throw IOException("invalid X.224 Data PDU")
        }
        return packet.copyOfRange(7, packet.size)
    }

    private class Bytes {
        private val out = ByteArrayOutputStream()

        fun u8(value: Int) = out.write(value and 0xFF)
        fun le16(value: Int) = bytes(byteArrayOf(value.toByte(), (value ushr 8).toByte()))
        fun be16(value: Int) = bytes(byteArrayOf((value ushr 8).toByte(), value.toByte()))
        fun le32(value: Int) = bytes(ByteArray(4) { (value ushr (it * 8)).toByte() })
        fun bytes(value: ByteArray) = out.write(value)
        fun zeros(length: Int) = bytes(ByteArray(length))
        fun array(): ByteArray = out.toByteArray()

        fun fixedUtf16(value: String, size: Int) {
            val encoded = value.take((size / 2) - 1).toByteArray(StandardCharsets.UTF_16LE)
            bytes(encoded)
            zeros(size - encoded.size)
        }

        fun userDataBlock(type: Int, payload: ByteArray) {
            le16(type)
            le16(payload.size + 4)
            bytes(payload)
        }

        fun perLength(length: Int) {
            require(length in 0..0x7FFF)
            if (length > 0x7F) be16(length or 0x8000) else u8(length)
        }

        fun perInteger(value: Int) {
            when (value) {
                in 0..0xFF -> { u8(1); u8(value) }
                in 0..0xFFFF -> { u8(2); be16(value) }
                else -> throw IllegalArgumentException("unsupported PER integer: $value")
            }
        }

        fun berLength(length: Int) {
            when (length) {
                in 0..0x7F -> u8(length)
                in 0..0xFF -> { u8(0x81); u8(length) }
                in 0..0xFFFF -> { u8(0x82); be16(length) }
                else -> throw IllegalArgumentException("BER value too large: $length")
            }
        }

        fun berInteger(value: Int) {
            u8(0x02)
            when (value) {
                in 0..0x7F -> { u8(1); u8(value) }
                in 0..0x7FFF -> { u8(2); be16(value) }
                in 0..0x7FFFFF -> { u8(3); u8(value ushr 16); be16(value) }
                else -> throw IllegalArgumentException("unsupported BER integer: $value")
            }
        }

        fun berOctet(value: ByteArray) {
            u8(0x04)
            berLength(value.size)
            bytes(value)
        }

        fun domainParameters(values: IntArray) {
            val body = Bytes().apply { values.forEach(::berInteger) }.array()
            u8(0x30)
            berLength(body.size)
            bytes(body)
        }
    }

    const val CHANNEL_OPTION_SHOW_PROTOCOL = 0x00200000

    private val STATIC_CHANNELS = listOf(
        // INITIALIZED | ENCRYPT_RDP | SHOW_PROTOCOL。CLIPRDR 自身では圧縮を使わない。
        "cliprdr" to 0xC0200000.toInt(),
        // FreeRDP / mstsc と同じ INITIALIZED | ENCRYPT_RDP | COMPRESS_RDP。
        // ⛔ **SHOW_PROTOCOL を入れない** — drdynvc は Channel PDU Header を見せない channel。
        "drdynvc" to 0xC0800000.toInt(),
        // INITIALIZED | ENCRYPT_RDP | PRI_MED。⛔ **SHOW_PROTOCOL を入れない** (FreeRDP / mstsc と同じ)。
        // 音は途切れても構わないが、⚠ 宣言と送信フラグが食い違うと相手が黙る (→ drdynvc の教訓)。
        // ⚠ **優先度を名乗る (0.8.489)**: FreeRDP / mstsc はどちらも `PRI_MED` を付ける。
        //    仕様上は無くても通るはずだが、音だけ 1 通も来ない状態を追っているので、
        //    **広く使われている実装と違うところは潰しておく**。
        "rdpsnd" to 0xC4000000.toInt(),
    )

    private class Cursor(private val data: ByteArray) {
        private var offset = 0
        val remaining: Int get() = data.size - offset

        fun u8(): Int {
            if (remaining < 1) throw IOException("truncated RDP packet")
            return data[offset++].toInt() and 0xFF
        }

        fun le16(): Int = u8() or (u8() shl 8)
        fun be16(): Int = (u8() shl 8) or u8()
        fun le32(): Int = u8() or (u8() shl 8) or (u8() shl 16) or (u8() shl 24)

        fun bytes(length: Int): ByteArray {
            if (length < 0 || remaining < length) throw IOException("truncated RDP packet")
            return data.copyOfRange(offset, offset + length).also { offset += length }
        }

        fun sub(length: Int): Cursor = Cursor(bytes(length))
        fun expect(vararg expected: Int) = expected.forEach { if (u8() != it) throw IOException("unexpected RDP field") }
        fun skipRemaining() { offset = data.size }
        fun requireEnd() { if (remaining != 0) throw IOException("trailing RDP data: $remaining bytes") }

        fun perLength(): Int {
            val first = u8()
            return if (first and 0x80 == 0) first else ((first and 0x7F) shl 8) or u8()
        }

        fun perInteger(): Int {
            val length = perLength()
            if (length !in 1..4) throw IOException("invalid PER integer length: $length")
            var value = 0
            repeat(length) { value = (value shl 8) or u8() }
            return value
        }

        fun berLength(): Int {
            val first = u8()
            if (first and 0x80 == 0) return first
            val count = first and 0x7F
            if (count !in 1..2) throw IOException("unsupported BER length")
            var value = 0
            repeat(count) { value = (value shl 8) or u8() }
            return value
        }

        fun berInteger(): Int {
            expect(0x02)
            val length = berLength()
            if (length !in 1..4) throw IOException("invalid BER integer length: $length")
            var value = 0
            repeat(length) { value = (value shl 8) or u8() }
            return value
        }

        fun berOctet(): ByteArray {
            expect(0x04)
            return bytes(berLength())
        }

        fun skipBerSequence() {
            expect(0x30)
            val sequence = sub(berLength())
            repeat(8) { sequence.berInteger() }
            sequence.requireEnd()
        }
    }
}
