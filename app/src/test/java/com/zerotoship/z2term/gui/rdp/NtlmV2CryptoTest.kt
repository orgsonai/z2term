package com.zerotoship.z2term.gui.rdp

import org.junit.Assert.assertEquals
import org.junit.Test

class NtlmV2CryptoTest {
    @Test
    fun md4MatchesRfc1320Vectors() {
        assertEquals("31d6cfe0d16ae931b73c59d7e0c089c0", Md4.digest(byteArrayOf()).hex())
        assertEquals("a448017aaf21d8525fc10ae87aa6729d", Md4.digest("abc".toByteArray()).hex())
        assertEquals(
            "d79e1c308aa5bbcdeea8ed63df412da9",
            Md4.digest("abcdefghijklmnopqrstuvwxyz".toByteArray()).hex(),
        )
    }

    @Test
    fun responseKeyMatchesTheMsNlmpNtlmv2Example() {
        assertEquals(
            "0c868a403bfd7a93a3001ef22ef02e3f",
            NtlmV2Crypto.responseKeyNt("User", "Domain", "Password").hex(),
        )
    }

    @Test
    fun challengeResponsesMatchTheMsNlmpExample() {
        val responseKey = "0c868a403bfd7a93a3001ef22ef02e3f".hexBytes()
        val response = NtlmV2Crypto.computeResponse(
            responseKey = responseKey,
            serverChallenge = "0123456789abcdef".hexBytes(),
            clientChallenge = "aaaaaaaaaaaaaaaa".hexBytes(),
            timestamp = "0000000000000000".hexBytes(),
            targetInfo = (
                "02000c0044006f006d00610069006e00" +
                    "01000c00530065007200760065007200" +
                    "00000000"
                ).hexBytes(),
        )

        assertEquals(
            "68cd0ab851e51c96aabc927bebef6a1c" +
                "01010000000000000000000000000000" +
                "aaaaaaaaaaaaaaaa00000000" +
                "02000c0044006f006d00610069006e00" +
                "01000c00530065007200760065007200" +
                "0000000000000000",
            response.ntChallengeResponse.hex(),
        )
        assertEquals(
            "86c35097ac9cec102554764a57cccc19aaaaaaaaaaaaaaaa",
            response.lmChallengeResponse.hex(),
        )
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
