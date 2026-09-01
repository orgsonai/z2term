package com.zerotoship.z2term.gui.rdp

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Client Info、licensing、Demand/Confirm Active による RDP connection sequence。 */
internal object RdpActivation {
    private const val TAG = "RdpActivation"
    const val CAP_BITMAP = 2
    const val CAP_ORDER = 3
    const val CAP_SURFACE_COMMANDS = 0x1C
    const val CAP_BITMAP_CODECS = 0x1D
    /** TS_TIME_ZONE_INFORMATION の大きさ (Bias + 名前 2 つ + 切替日 2 つ + Bias 2 つ)。 */
    private const val TIME_ZONE_INFORMATION_BYTES = 172
    private const val PDUTYPE_DEMAND_ACTIVE = 1
    private const val PDUTYPE_DEACTIVATE_ALL = 6
    private const val PDUTYPE_DATAPDU = 7
    private const val PDUTYPE2_UPDATE = 0x02
    private const val PDUTYPE2_REFRESH_RECT = 0x21
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
        repeat(16) {
            val data = mcsData(RdpTlsTransport.readTpkt(input), session.ioChannelId) ?: return@repeat
            if (!isShareControlPdu(data)) {
                // security header が付く PDU。⚠ **licensing は来ないことがある** — ライセンスの
                // 要らない相手 (ワークステーション版の Windows など) は License Error PDU を送らず
                // そのまま Demand Active へ進む。「必ず来る」と書くとそういう相手に一切繋がらない
                // (0.8.461・実機で判明)。auto-detect / multitransport / heartbeat は**応答しなくても
                // 接続は進む**ので読み飛ばす (こちらは capability で何も広告していない)。
                val flags = securityFlags(data)
                if (flags and SEC_LICENSE_PKT != 0) readLicense(data)
                else Log.i(TAG, "RDP: skipped PDU with security flags 0x%04X".format(flags))
                return@repeat
            }
            // Share Control PDU だが Demand Active とは限らない。Windows は capability 交換の
            // 直前に Monitor Layout などの Data PDU を挟むことがある。⛔ **知らない PDU で
            // 接続ごと諦めない** — 応答の要らないものは読み飛ばして Demand Active を待つ。
            // ⚠ セッションを畳む Deactivate All だけは読み飛ばさず、その場で終わらせる。
            val pduType = shareControlType(data)
            if (pduType != PDUTYPE_DEMAND_ACTIVE) {
                if (pduType == PDUTYPE_DEACTIVATE_ALL) throw EOFException("RDP session was deactivated")
                // ⚠ **サーバーからの苦情を読み飛ばさない。** 断られた理由はここにしか出てこないので、
                // 黙って捨てると「何か落ちた」としか分からなくなる (0.8.465)。
                connectionErrorInfo(data)?.let { error ->
                    throw IOException("RDP server reported errorInfo=0x${error.toString(16)}")
                }
                Log.i(TAG, "RDP: skipped share control PDU type $pduType (${data.size} bytes) ${head(data)}")
                return@repeat
            }
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
            val data = readShareData(payload, active.shareId) ?: run {
                Log.i(TAG, "RDP finalize: skipped ${payload.size} bytes ${head(payload)}")
                return@repeat
            }
            Log.i(TAG, "RDP finalize: pduType2=0x%02X received=0x%X".format(data.type, received))
            // ⚠ **ここでも苦情を捨てない。** Confirm Active を断られるとサーバーは Set Error Info を
            // 送ってから切るので、拾わないと理由の無い EOF にしか見えない (0.8.468)。
            if (data.type == PDUTYPE2_SET_ERROR_INFO) {
                val body = Cursor(data.body)
                val error = body.le32().toLong() and 0xFFFFFFFFL
                if (error != 0L) throw IOException("RDP server reported errorInfo=0x${error.toString(16)}")
                return@repeat
            }
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
        val channel = channelData(packet)
        val payload = channel.payload.takeIf { channel.channelId == session.ioChannelId } ?: run {
            Log.i(TAG, "RDP rx: other channel (${packet.size} bytes)")
            return null
        }
        return bitmapUpdatePayload(payload, active)
    }

    internal fun bitmapUpdatePayload(
        payload: ByteArray,
        active: ActiveSession,
    ): ByteArray? {
        val data = readShareData(payload, active.shareId) ?: run {
            Log.i(TAG, "RDP rx: not share data (${payload.size} bytes) ${head(payload)}")
            return null
        }
        return when (data.type) {
            PDUTYPE2_UPDATE -> {
                val body = Cursor(data.body)
                val updateType = body.le16()
                Log.i(TAG, "RDP rx: update type=$updateType (${data.body.size} bytes)")
                if (updateType == UPDATETYPE_BITMAP) data.body else null
            }
            PDUTYPE2_SET_ERROR_INFO -> {
                val body = Cursor(data.body)
                val error = body.le32().toLong() and 0xFFFFFFFFL
                body.end()
                // ⚠⚠ **errorInfo = 0 は「エラー無し」** (ERRINFO_NONE)。Windows は接続が
                // 落ち着いた直後にこれを 1 つ送ってくるので、値を見ずに投げると
                // **繋がった瞬間に必ず切れる** (0.8.469・実機で判明)。
                if (error != 0L) throw IOException("RDP server reported errorInfo=0x${error.toString(16)}")
                null
            }
            else -> {
                Log.i(TAG, "RDP rx: pduType2=0x%02X (${data.body.size} bytes)".format(data.type))
                null
            }
        }
    }

    internal data class ChannelData(val channelId: Int, val payload: ByteArray)

    internal fun channelData(packet: ByteArray): ChannelData {
        val c = Cursor(x224(packet))
        if (c.u8() ushr 2 != 26) throw IOException("expected MCS Send Data Indication")
        c.be16()
        val channel = c.be16()
        c.u8()
        val payload = c.bytes(c.perLength())
        c.end()
        return ChannelData(channel, payload)
    }

    internal fun virtualChannelPacket(
        session: RdpMcs.Session,
        channelId: Int,
        payload: ByteArray,
    ): ByteArray = sendData(session.userChannelId, channelId, payload)

    /**
     * 画面全体を送り直すよう頼む (Refresh Rect PDU)。
     *
     * 接続直後にサーバーが自発的に描いてくれるとは限らない。Windows は Save Session Info を
     * 最後に黙り込むことがあり、こちらから要求しないと**何も届かない**。
     * ⚠ 矩形の right / bottom は**内側を含む**座標なので 1 引く。
     */
    fun refreshRect(
        session: RdpMcs.Session,
        active: ActiveSession,
        width: Int,
        height: Int,
    ): ByteArray {
        val body = Writer().apply {
            u8(1)                    // numberOfAreas
            zero(3)                  // pad3Octets
            le16(0); le16(0); le16(width - 1); le16(height - 1)
        }.array()
        return shareData(session, active.shareId, PDUTYPE2_REFRESH_RECT, body)
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
            // TS_EXTENDED_INFO_PACKET。⚠ **最後まで書かないと Windows に拒否される**
            // (errorInfo 0x1118 = SECURITYDATATOOSHORT9。0.8.465・実機で判明)。clientAddress で
            // 止めても寛容な実装は通してしまうので、緩い相手だけで確かめると気付けない。
            val address = utf16("127.0.0.1\u0000")
            val clientDir = utf16("\u0000")
            le16(2); le16(address.size); bytes(address)   // clientAddressFamily(AF_INET), cbClientAddress
            le16(clientDir.size); bytes(clientDir)        // cbClientDir, clientDir
            zero(TIME_ZONE_INFORMATION_BYTES)             // clientTimeZone: 全ゼロ = UTC
            le32(0)                                       // clientSessionId
            le32(0)                                       // performanceFlags
            le16(0)                                       // cbAutoReconnectCookie
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
                // 末尾は refreshRectSupport / suppressOutputSupport。⭐ **refreshRect を 1 にする** —
                // これが 0 だと「画面を送り直して」と頼む手段が無く、サーバーが自発的に送って
                // こないときに黒い画面のまま待つしかない (0.8.471・実機で判明)。
                le16(4); le16(7); le16(0x200); le16(0); le16(0); le16(0)
                le16(0); le16(0); le16(0); u8(1); u8(0)
            },
            cap(CAP_BITMAP) {
                // 24bppを優先する。32bpp圧縮はRDP 6.0 planar codecになり、今回広告しない。
                le16(24); le16(1); le16(1); le16(1); le16(s.width); le16(s.height)
                le16(0); le16(1); le16(1); u8(0); u8(0); le16(1); le16(0)
            },
            // Capability Set自体はconnection sequenceの必須集合。orderSupport[32]を全ゼロにし、
            // 実装していないPrimary/Secondary drawing orderは一つも広告しない。
            cap(CAP_ORDER) {
                // orderFlags = NEGOTIATEORDERSUPPORT | ZEROBOUNDSDELTASSUPPORT。
                // ⚠ 後者は仕様上**必ず立てる**ことになっている (MS-RDPBCGR 2.2.7.1.3)。
                // 描画 Order を 1 つも使わなくても、立てないと弾く相手がいる。
                zero(16); le32(0); le16(1); le16(20); le16(0); le16(1); le16(0)
                le16(0x000A); zero(32); le16(0); le16(0); le32(0); le32(230400)
                le16(0); le16(0); le16(0); le16(0)
            },
            cap(0x13) { le16(2); u8(0); u8(0); zero(32) },
            cap(8) { le16(1); le16(25); le16(25) },
            cap(0x0D) { le16(1); le16(0); le32(s.keyboardLayout); le32(4); le32(0); le32(12); zero(64) },
            cap(0x0F) { le32(0) },
            cap(0x10) { zero(48) },
            // Offscreen Bitmap Cache。使わないので支援レベル 0 だが、**集合から抜くと
            // 必須が欠けたとみなす相手がいる**ので「対応しない」と明示して送る。
            cap(0x11) { le32(0); le16(0); le16(0) },
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

    /**
     * この PDU が **security header の付かない Share Control PDU** か。
     *
     * TLS (Enhanced RDP Security) では、Demand Active や Share Data のような通常の PDU に
     * security header が**付かない**。付くのは licensing / auto-detect / multitransport /
     * heartbeat のような特定の PDU だけなので、受信側は毎回どちらなのかを見分ける必要がある。
     *
     * 見分けは **先頭 2 バイト (Share Control Header の totalLength) が PDU 全体の長さと
     * 一致するか**で行う。
     * ⛔ **security flag のビットで見分けてはいけない** — Share Control PDU の totalLength が
     * たまたま `SEC_LICENSE_PKT` (0x80) 等のビットを含むことがあり、通常の画面更新を
     * licensing と誤読する (0.8.463)。
     */
    private fun isShareControlPdu(payload: ByteArray): Boolean =
        payload.size >= 6 &&
            ((payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)) == payload.size

    /**
     * capability 交換より前に届いた Data PDU が Set Error Info なら、その errorInfo。
     * shareId がまだ決まっていない段階なので [readShareData] は使えない。
     */
    private fun connectionErrorInfo(payload: ByteArray): Long? = runCatching {
        val c = Cursor(payload)
        c.le16(); c.le16(); c.le16()          // Share Control Header
        c.le32(); c.u8(); c.u8(); c.le16()    // shareId, pad1, streamId, uncompressedLength
        if (c.u8() != PDUTYPE2_SET_ERROR_INFO) return null
        c.u8(); c.le16()                      // compressedType, compressedLength
        // 0 は ERRINFO_NONE (エラー無し)。理由として扱わない。
        (c.le32().toLong() and 0xFFFFFFFFL).takeIf { it != 0L }
    }.getOrNull()

    /** Share Control Header の pduType (下位 4 bit)。[isShareControlPdu] が true のときだけ意味を持つ。 */
    private fun shareControlType(payload: ByteArray): Int =
        ((payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)) and 0x0F

    /** 診断用。読み飛ばした PDU が何だったかを後から突き合わせられるように先頭だけ残す。 */
    private fun head(payload: ByteArray): String =
        payload.take(24).joinToString(" ") { "%02X".format(it) }

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
        // security header が付く PDU (auto-detect / multitransport / heartbeat 等) は
        // Share Data ではない。⛔ 長さ違いとして例外にしてはいけない — 接続シーケンスの
        // 途中にも受信ループ中にも普通に混ざる。
        if (!isShareControlPdu(payload)) return null
        val c = Cursor(payload)
        c.le16()
        val pduType = c.le16()
        c.le16()
        when (pduType and 0x0F) {
            PDUTYPE_DEACTIVATE_ALL -> throw EOFException("RDP session was deactivated")
            PDUTYPE_DATAPDU -> Unit
            else -> return null
        }
        val shareId = c.le32()
        c.u8()
        c.u8()
        // uncompressedLength。⛔ **正しさの判定に使わない** — 「PDU 全体の長さ」を入れる実装と
        // 「本体だけの長さ」を入れる実装があり、どちらかに合わせるともう一方を必ず弾く
        // (0.8.466・実機で判明)。圧縮された PDU は下で明示的に断っているので、展開のための
        // この値をここで使う理由が無い。
        c.le16()
        val type = c.u8()
        val compressedType = c.u8()
        val compressedLength = c.le16()
        if (shareId != expectedShareId) throw IOException("Share Data PDU has a different shareId")
        if (compressedType != 0 || compressedLength != 0) {
            throw IOException("compressed Share Data PDU is not supported")
        }
        return ShareData(type, c.bytes(c.remaining))
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
