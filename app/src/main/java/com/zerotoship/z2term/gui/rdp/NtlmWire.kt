package com.zerotoship.z2term.gui.rdp

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/** MS-NLMPの3メッセージとAV_PAIR。数値はすべてlittle-endian。 */
internal object NtlmWire {
    private val SIGNATURE = byteArrayOf('N'.code.toByte(), 'T'.code.toByte(), 'L'.code.toByte(), 'M'.code.toByte(), 'S'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), 0)
    private val VERSION = byteArrayOf(10, 0, 0x61, 0x4A, 0, 0, 0, 0x0F)

    const val NEGOTIATE_UNICODE = 0x00000001
    const val REQUEST_TARGET = 0x00000004
    const val NEGOTIATE_SIGN = 0x00000010
    const val NEGOTIATE_SEAL = 0x00000020
    const val NEGOTIATE_NTLM = 0x00000200
    const val NEGOTIATE_ALWAYS_SIGN = 0x00008000
    const val NEGOTIATE_EXTENDED_SESSION_SECURITY = 0x00080000
    const val NEGOTIATE_TARGET_INFO = 0x00800000
    const val NEGOTIATE_VERSION = 0x02000000
    const val NEGOTIATE_128 = 0x20000000
    const val NEGOTIATE_KEY_EXCHANGE = 0x40000000
    const val NEGOTIATE_56: Int = Int.MIN_VALUE

    /** MS-NLMP §4.2.4のAUTHENTICATE_MESSAGEと同じ能力集合。 */
    const val CLIENT_FLAGS: Int = -0x1D777DCB // unsigned 0xE2888235

    const val AV_EOL = 0
    const val AV_FLAGS = 6
    const val AV_TIMESTAMP = 7
    const val AV_TARGET_NAME = 9
    const val AV_CHANNEL_BINDINGS = 10
    const val AV_FLAG_MIC_PRESENT = 0x00000002

    data class AvPair(val id: Int, val value: ByteArray)

    data class Challenge(
        val raw: ByteArray,
        val flags: Int,
        val serverChallenge: ByteArray,
        val targetInfo: ByteArray,
        val avPairs: List<AvPair>,
    ) {
        fun av(id: Int): ByteArray? = avPairs.firstOrNull { it.id == id }?.value
    }

    data class Authentication(
        val message: ByteArray,
        val exportedSessionKey: ByteArray,
        val flags: Int,
    )

    fun negotiate(): ByteArray = ByteArray(40).also { out ->
        SIGNATURE.copyInto(out)
        putU32(out, 8, 1)
        putU32(out, 12, CLIENT_FLAGS)
        putSecurityBuffer(out, 16, 0, out.size)
        putSecurityBuffer(out, 24, 0, out.size)
        VERSION.copyInto(out, 32)
    }

    fun parseChallenge(message: ByteArray): Challenge {
        requireMessage(message, type = 2, minSize = 48)
        val flags = u32(message, 20)
        val targetInfo = readSecurityBuffer(message, 40)
        return Challenge(
            raw = message.copyOf(),
            flags = flags,
            serverChallenge = message.copyOfRange(24, 32),
            targetInfo = targetInfo,
            avPairs = decodeAvPairs(targetInfo),
        )
    }

    fun authenticate(
        negotiateMessage: ByteArray,
        challenge: Challenge,
        user: String,
        domain: String,
        password: String,
        workstation: String,
        secureRandom: SecureRandom = SecureRandom(),
        nowMillis: Long = System.currentTimeMillis(),
    ): Authentication {
        require(user.isNotEmpty()) { "RDP user must not be empty" }
        // REQUEST_TARGETはclient requestでありserver応答では落ちる（MS-NLMP §4.2.4の例も同じ）。
        val flags = (challenge.flags and CLIENT_FLAGS) or REQUEST_TARGET
        requireFlag(flags, NEGOTIATE_UNICODE, "Unicode")
        requireFlag(flags, NEGOTIATE_NTLM, "NTLM")
        requireFlag(flags, NEGOTIATE_EXTENDED_SESSION_SECURITY, "extended session security")
        requireFlag(flags, NEGOTIATE_SIGN, "signing")
        requireFlag(flags, NEGOTIATE_SEAL, "sealing")

        val timestampFromServer = challenge.av(AV_TIMESTAMP)
        val timestamp = timestampFromServer ?: windowsFileTime(nowMillis)
        if (timestamp.size != 8) throw IOException("invalid NTLM timestamp length: ${timestamp.size}")
        val targetInfo = bindingTargetInfo(challenge.avPairs)
        val clientChallenge = ByteArray(8).also(secureRandom::nextBytes)
        val responseKey = NtlmV2Crypto.responseKeyNt(user, domain, password)
        val responses = NtlmV2Crypto.computeResponse(
            responseKey = responseKey,
            serverChallenge = challenge.serverChallenge,
            clientChallenge = clientChallenge,
            timestamp = timestamp,
            targetInfo = targetInfo,
            suppressLmResponse = timestampFromServer != null,
        )
        val keyExchange = flags and NEGOTIATE_KEY_EXCHANGE != 0
        val exportedSessionKey = if (keyExchange) ByteArray(16).also(secureRandom::nextBytes)
            else responses.sessionBaseKey.copyOf()
        val encryptedSessionKey = if (keyExchange) Rc4(responses.sessionBaseKey).process(exportedSessionKey)
            else byteArrayOf()

        val domainBytes = domain.utf16Le()
        val userBytes = user.utf16Le()
        val workstationBytes = workstation.utf16Le()
        val includeVersion = flags and NEGOTIATE_VERSION != 0
        val headerSize = if (includeVersion) 88 else 80 // MIC always follows the optional Version.
        val payloads = listOf(
            responses.lmChallengeResponse,
            responses.ntChallengeResponse,
            domainBytes,
            userBytes,
            workstationBytes,
            encryptedSessionKey,
        )
        val offsets = IntArray(payloads.size)
        var next = headerSize
        payloads.forEachIndexed { index, bytes -> offsets[index] = next; next += bytes.size }
        val message = ByteArray(next)
        SIGNATURE.copyInto(message)
        putU32(message, 8, 3)
        payloads.forEachIndexed { index, bytes ->
            putSecurityBuffer(message, 12 + index * 8, bytes.size, offsets[index])
            bytes.copyInto(message, offsets[index])
        }
        putU32(message, 60, flags)
        val micOffset = if (includeVersion) 72 else 64
        if (includeVersion) VERSION.copyInto(message, 64)
        // MIC field is zero while its own HMAC is calculated.
        val mic = NtlmV2Crypto.hmacMd5(exportedSessionKey, negotiateMessage + challenge.raw + message)
        mic.copyInto(message, micOffset)
        return Authentication(message, exportedSessionKey, flags)
    }

    internal fun bindingTargetInfo(pairs: List<AvPair>): ByteArray {
        val output = pairs.filter { it.id !in setOf(AV_EOL, AV_FLAGS, AV_CHANNEL_BINDINGS, AV_TARGET_NAME) }
            .toMutableList()
        val oldFlags = pairs.firstOrNull { it.id == AV_FLAGS }?.value?.takeIf { it.size == 4 }?.let { u32(it, 0) } ?: 0
        output += AvPair(AV_FLAGS, ByteArray(4).also { putU32(it, 0, oldFlags or AV_FLAG_MIC_PRESENT) })
        output += AvPair(AV_CHANNEL_BINDINGS, ByteArray(16))
        output += AvPair(AV_TARGET_NAME, byteArrayOf())
        output += AvPair(AV_EOL, byteArrayOf())
        return encodeAvPairs(output)
    }

    internal fun decodeAvPairs(bytes: ByteArray): List<AvPair> {
        val result = mutableListOf<AvPair>()
        var offset = 0
        while (offset < bytes.size) {
            if (bytes.size - offset < 4) throw IOException("truncated NTLM AV_PAIR")
            val id = u16(bytes, offset)
            val length = u16(bytes, offset + 2)
            offset += 4
            if (length > bytes.size - offset) throw IOException("truncated NTLM AV_PAIR value")
            val value = bytes.copyOfRange(offset, offset + length)
            offset += length
            result += AvPair(id, value)
            if (id == AV_EOL) {
                if (length != 0 || offset != bytes.size) throw IOException("invalid NTLM AV_EOL")
                return result
            }
        }
        if (bytes.isNotEmpty()) throw IOException("NTLM TargetInfo has no AV_EOL")
        return result
    }

    internal fun encodeAvPairs(pairs: List<AvPair>): ByteArray {
        val size = pairs.sumOf { 4 + it.value.size }
        val out = ByteArray(size)
        var offset = 0
        pairs.forEach { pair ->
            require(pair.id in 0..0xFFFF && pair.value.size <= 0xFFFF)
            putU16(out, offset, pair.id)
            putU16(out, offset + 2, pair.value.size)
            pair.value.copyInto(out, offset + 4)
            offset += 4 + pair.value.size
        }
        return out
    }

    private fun requireMessage(message: ByteArray, type: Int, minSize: Int) {
        if (message.size < minSize || !MessageDigest.isEqual(message.copyOfRange(0, 8), SIGNATURE) || u32(message, 8) != type) {
            throw IOException("invalid NTLM message type $type")
        }
    }

    private fun readSecurityBuffer(message: ByteArray, fieldOffset: Int): ByteArray {
        if (fieldOffset + 8 > message.size) throw IOException("truncated NTLM security buffer")
        val length = u16(message, fieldOffset)
        val maxLength = u16(message, fieldOffset + 2)
        val offset = u32(message, fieldOffset + 4)
        if (maxLength < length || offset < 0 || offset > message.size - length) {
            throw IOException("invalid NTLM security buffer")
        }
        return message.copyOfRange(offset, offset + length)
    }

    private fun putSecurityBuffer(out: ByteArray, offset: Int, length: Int, payloadOffset: Int) {
        require(length in 0..0xFFFF)
        putU16(out, offset, length)
        putU16(out, offset + 2, length)
        putU32(out, offset + 4, payloadOffset)
    }

    private fun requireFlag(flags: Int, flag: Int, name: String) {
        if (flags and flag == 0) throw IOException("RDP server did not negotiate NTLM $name")
    }

    private fun windowsFileTime(nowMillis: Long): ByteArray {
        val ticks = (nowMillis + 11_644_473_600_000L) * 10_000L
        return ByteArray(8).also { out -> repeat(8) { out[it] = (ticks ushr (8 * it)).toByte() } }
    }

    private fun String.utf16Le(): ByteArray = toByteArray(StandardCharsets.UTF_16LE)

    internal fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    internal fun u32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    internal fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    internal fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }
}
