package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CredSspNtlmTest {
    @Test
    fun completesCredSspNtlmExchangeAgainstScriptedServer() {
        val credentials = CredSspNtlm.Credentials("User", "Domain", "Password", "COMPUTER")
        val subjectPublicKey = ByteArray(270) { (it * 13).toByte() }
        val random = ByteArray(56) { (it + 1).toByte() }
        val negotiate = NtlmWire.negotiate()
        val challenge = challengeWithTimestamp()
        val expectedAuthentication = NtlmWire.authenticate(
            negotiate,
            NtlmWire.parseChallenge(challenge),
            credentials.user,
            credentials.domain,
            credentials.password,
            credentials.workstation,
            FixedSecureRandom(random.copyOfRange(0, 24)),
        )
        val nonce = random.copyOfRange(24, 56)
        val serverSecurity = NtlmSessionSecurity(
            expectedAuthentication.exportedSessionKey,
            expectedAuthentication.flags,
            clientRole = false,
        )
        val serverResponse = CredSspTsRequest(
            pubKeyAuth = serverSecurity.wrap(CredSspBinding.serverHash(nonce, subjectPublicKey)),
        )
        val serverBytes = CredSspTsRequest(negoToken = Spnego.responseToken(challenge)).encode() +
            serverResponse.encode()
        val outputBytes = ByteArrayOutputStream()

        CredSspNtlm.authenticate(
            DataInputStream(ByteArrayInputStream(serverBytes)),
            DataOutputStream(outputBytes),
            subjectPublicKey,
            credentials,
            FixedSecureRandom(random),
        )

        val clientMessages = DataInputStream(ByteArrayInputStream(outputBytes.toByteArray()))
        val negotiateRequest = CredSspNtlm.readRequest(clientMessages)
        val bindingRequest = CredSspNtlm.readRequest(clientMessages)
        val credentialsRequest = CredSspNtlm.readRequest(clientMessages)
        assertArrayEquals(negotiate, Spnego.ntlmToken(negotiateRequest.negoToken!!))
        assertArrayEquals(expectedAuthentication.message, Spnego.ntlmToken(bindingRequest.negoToken!!))
        assertArrayEquals(nonce, bindingRequest.clientNonce)
        assertArrayEquals(
            CredSspBinding.clientHash(nonce, subjectPublicKey),
            serverSecurity.unwrap(bindingRequest.pubKeyAuth!!),
        )
        assertArrayEquals(
            CredSspNtlm.passwordCredentials(credentials),
            serverSecurity.unwrap(credentialsRequest.authInfo!!),
        )
        assertEquals(0, clientMessages.available())
    }

    @Test
    fun passwordCredentialsUseUtf16LeInsideTsCredentials() {
        val encoded = CredSspNtlm.passwordCredentials(
            CredSspNtlm.Credentials(user = "User", domain = "DOM", password = "pass"),
        )
        val outer = Der.Reader(encoded).read(Der.SEQUENCE).reader()
        val type = outer.read(0xA0).reader().read(Der.INTEGER)
        val credentials = outer.read(0xA1).reader().read(Der.OCTET_STRING).body
        outer.requireEnd()
        assertEquals(1, Der.decodeInteger(type))

        val passwordFields = Der.Reader(credentials).read(Der.SEQUENCE).reader()
        assertArrayEquals("DOM".toByteArray(Charsets.UTF_16LE), contextOctets(passwordFields, 0xA0))
        assertArrayEquals("User".toByteArray(Charsets.UTF_16LE), contextOctets(passwordFields, 0xA1))
        assertArrayEquals("pass".toByteArray(Charsets.UTF_16LE), contextOctets(passwordFields, 0xA2))
        passwordFields.requireEnd()
    }

    @Test
    fun readsExactlyOneDerRequestFromAContinuousTlsStream() {
        val first = CredSspTsRequest(negoToken = byteArrayOf(1, 2, 3))
        val second = CredSspTsRequest(errorCode = 0)
        val input = DataInputStream(ByteArrayInputStream(first.encode() + second.encode()))

        assertEquals(first, CredSspNtlm.readRequest(input))
        assertEquals(second, CredSspNtlm.readRequest(input))
    }

    @Test
    fun writerFlushesACompleteRequest() {
        val bytes = ByteArrayOutputStream()
        val request = CredSspTsRequest(authInfo = byteArrayOf(4, 5, 6))

        CredSspNtlm.writeRequest(DataOutputStream(bytes), request)

        assertArrayEquals(request.encode(), bytes.toByteArray())
    }

    @Test
    fun rejectsIndefiniteAndOversizedDerLengthsBeforeAllocation() {
        assertThrows(IOException::class.java) {
            CredSspNtlm.readDerMessage(DataInputStream(ByteArrayInputStream(byteArrayOf(0x30, 0x80.toByte()))))
        }
        assertThrows(IOException::class.java) {
            CredSspNtlm.readDerMessage(
                DataInputStream(ByteArrayInputStream(byteArrayOf(0x30, 0x84.toByte(), 1, 0, 0, 1))),
            )
        }
    }

    private fun contextOctets(reader: Der.Reader, tag: Int): ByteArray =
        reader.read(tag).reader().read(Der.OCTET_STRING).body

    private fun challengeWithTimestamp(): ByteArray {
        val base = CHALLENGE.hexBytes()
        val targetInfo = NtlmWire.encodeAvPairs(
            NtlmWire.decodeAvPairs(base.copyOfRange(68, base.size)).dropLast(1) +
                NtlmWire.AvPair(NtlmWire.AV_TIMESTAMP, "8877665544332211".hexBytes()) +
                NtlmWire.AvPair(NtlmWire.AV_EOL, byteArrayOf()),
        )
        return base.copyOf(68 + targetInfo.size).also { message ->
            NtlmWire.putU16(message, 40, targetInfo.size)
            NtlmWire.putU16(message, 42, targetInfo.size)
            targetInfo.copyInto(message, 68)
        }
    }

    private class FixedSecureRandom(private val bytes: ByteArray) : SecureRandom() {
        private var offset = 0

        override fun nextBytes(target: ByteArray) {
            require(offset + target.size <= bytes.size)
            bytes.copyInto(target, startIndex = offset, endIndex = offset + target.size)
            offset += target.size
        }
    }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val CHALLENGE =
            "4e544c4d53535000020000000c000c003800000033828ae20123456789abcdef" +
                "00000000000000002400240044000000060070170000000f5300650072007600" +
                "6500720002000c0044006f006d00610069006e0001000c005300650072007600" +
                "6500720000000000"
    }
}
