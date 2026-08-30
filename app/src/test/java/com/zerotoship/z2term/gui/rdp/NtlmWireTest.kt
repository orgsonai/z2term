package com.zerotoship.z2term.gui.rdp

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NtlmWireTest {
    @Test
    fun negotiateMessageCarriesRequiredFlagsAndVersion() {
        val message = NtlmWire.negotiate()

        assertEquals("NTLMSSP\u0000", message.copyOfRange(0, 8).toString(Charsets.US_ASCII))
        assertEquals(1, NtlmWire.u32(message, 8))
        assertEquals(NtlmWire.CLIENT_FLAGS, NtlmWire.u32(message, 12))
        assertEquals(40, message.size)
    }

    @Test
    fun parsesMicrosoftChallengeExample() {
        val challenge = NtlmWire.parseChallenge(CHALLENGE.hexBytes())

        assertEquals(0xE28A8233u, challenge.flags.toUInt())
        assertEquals("0123456789abcdef", challenge.serverChallenge.hex())
        assertEquals("Domain", challenge.avPairs[0].value.toString(Charsets.UTF_16LE))
        assertEquals("Server", challenge.avPairs[1].value.toString(Charsets.UTF_16LE))
        assertEquals(NtlmWire.AV_EOL, challenge.avPairs.last().id)
    }

    @Test
    fun authenticateBuildsBoundNtlmv2MessageAndValidMic() {
        val negotiate = NtlmWire.negotiate()
        val challenge = NtlmWire.parseChallenge(CHALLENGE.hexBytes())
        val randomBytes = (
            "aaaaaaaaaaaaaaaa" +
                "55555555555555555555555555555555"
            ).hexBytes()

        val authentication = NtlmWire.authenticate(
            negotiateMessage = negotiate,
            challenge = challenge,
            user = "User",
            domain = "Domain",
            password = "Password",
            workstation = "COMPUTER",
            secureRandom = FixedSecureRandom(randomBytes),
            nowMillis = -11_644_473_600_000L,
        )
        val message = authentication.message

        assertEquals(3, NtlmWire.u32(message, 8))
        assertEquals(0xE2888235u, authentication.flags.toUInt())
        assertArrayEquals("Domain".toByteArray(Charsets.UTF_16LE), securityBuffer(message, 28))
        assertArrayEquals("User".toByteArray(Charsets.UTF_16LE), securityBuffer(message, 36))
        assertArrayEquals("COMPUTER".toByteArray(Charsets.UTF_16LE), securityBuffer(message, 44))
        assertArrayEquals("55555555555555555555555555555555".hexBytes(), authentication.exportedSessionKey)
        assertFalse(securityBuffer(message, 12).all { it == 0.toByte() })

        val ntResponse = securityBuffer(message, 20)
        val pairsOffset = 16 + 28
        val pairs = NtlmWire.decodeAvPairs(ntResponse.copyOfRange(pairsOffset, ntResponse.size - 4))
        assertTrue(NtlmWire.u32(pairs.first { it.id == NtlmWire.AV_FLAGS }.value, 0) and NtlmWire.AV_FLAG_MIC_PRESENT != 0)
        assertArrayEquals(ByteArray(16), pairs.first { it.id == NtlmWire.AV_CHANNEL_BINDINGS }.value)
        assertArrayEquals(byteArrayOf(), pairs.first { it.id == NtlmWire.AV_TARGET_NAME }.value)

        val actualMic = message.copyOfRange(72, 88)
        val withoutMic = message.copyOf().also { it.fill(0, 72, 88) }
        val expectedMic = hmacMd5(authentication.exportedSessionKey, negotiate + challenge.raw + withoutMic)
        assertArrayEquals(expectedMic, actualMic)
    }

    @Test
    fun rejectsTruncatedChallengeSecurityBuffer() {
        val challenge = CHALLENGE.hexBytes().also { NtlmWire.putU32(it, 44, it.size + 1) }

        assertThrows(java.io.IOException::class.java) { NtlmWire.parseChallenge(challenge) }
    }

    private fun securityBuffer(message: ByteArray, offset: Int): ByteArray {
        val length = NtlmWire.u16(message, offset)
        val payloadOffset = NtlmWire.u32(message, offset + 4)
        return message.copyOfRange(payloadOffset, payloadOffset + length)
    }

    private fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacMD5").run {
            init(SecretKeySpec(key, "HmacMD5"))
            doFinal(data)
        }

    private class FixedSecureRandom(private val bytes: ByteArray) : SecureRandom() {
        private var offset = 0

        override fun nextBytes(target: ByteArray) {
            require(offset + target.size <= bytes.size)
            bytes.copyInto(target, endIndex = offset + target.size, startIndex = offset)
            offset += target.size
        }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val CHALLENGE =
            "4e544c4d53535000020000000c000c003800000033828ae20123456789abcdef" +
                "00000000000000002400240044000000060070170000000f5300650072007600" +
                "6500720002000c0044006f006d00610069006e0001000c005300650072007600" +
                "6500720000000000"
    }
}
