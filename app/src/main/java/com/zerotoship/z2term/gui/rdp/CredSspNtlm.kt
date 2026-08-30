package com.zerotoship.z2term.gui.rdp

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/** TLS 済み RDP transport 上で CredSSP v5/v6 + SPNEGO/NTLMv2 認証を完了する。 */
internal object CredSspNtlm {
    data class Credentials(
        val user: String,
        val domain: String = "",
        val password: String,
        val workstation: String = "",
    )

    fun authenticate(
        input: DataInputStream,
        output: DataOutputStream,
        subjectPublicKey: ByteArray,
        credentials: Credentials,
        secureRandom: SecureRandom = SecureRandom(),
    ) {
        val negotiate = NtlmWire.negotiate()
        writeRequest(output, CredSspTsRequest(negoToken = Spnego.initialToken(negotiate)))

        val challengeRequest = readRequest(input)
        checkError(challengeRequest)
        val serverVersion = minOf(challengeRequest.version, CredSspTsRequest.VERSION)
        if (serverVersion < 5) throw IOException("CredSSP v5 or newer is required for secure public-key binding")
        val challengeToken = challengeRequest.negoToken ?: throw IOException("CredSSP response has no SPNEGO token")
        val challenge = NtlmWire.parseChallenge(Spnego.ntlmToken(challengeToken))
        val authentication = NtlmWire.authenticate(
            negotiateMessage = negotiate,
            challenge = challenge,
            user = credentials.user,
            domain = credentials.domain,
            password = credentials.password,
            workstation = credentials.workstation,
            secureRandom = secureRandom,
        )
        val security = NtlmSessionSecurity(authentication.exportedSessionKey, authentication.flags)
        val nonce = ByteArray(CredSspTsRequest.CLIENT_NONCE_BYTES).also(secureRandom::nextBytes)
        val clientBinding = CredSspBinding.clientHash(nonce, subjectPublicKey)
        writeRequest(
            output,
            CredSspTsRequest(
                version = serverVersion,
                negoToken = Spnego.responseToken(authentication.message),
                pubKeyAuth = security.wrap(clientBinding),
                clientNonce = nonce,
            ),
        )

        val bindingResponse = readRequest(input)
        checkError(bindingResponse)
        val sealedServerBinding = bindingResponse.pubKeyAuth
            ?: throw IOException("CredSSP response has no server public-key binding")
        val serverBinding = security.unwrap(sealedServerBinding)
        val expectedBinding = CredSspBinding.serverHash(nonce, subjectPublicKey)
        if (!MessageDigest.isEqual(expectedBinding, serverBinding)) {
            throw IOException("CredSSP server public-key binding mismatch")
        }

        writeRequest(
            output,
            CredSspTsRequest(
                version = serverVersion,
                authInfo = security.wrap(passwordCredentials(credentials)),
            ),
        )
    }

    internal fun passwordCredentials(credentials: Credentials): ByteArray {
        val passwordCreds = Der.sequence(
            Der.context(0, Der.octetString(credentials.domain.toByteArray(Charsets.UTF_16LE))),
            Der.context(1, Der.octetString(credentials.user.toByteArray(Charsets.UTF_16LE))),
            Der.context(2, Der.octetString(credentials.password.toByteArray(Charsets.UTF_16LE))),
        )
        return Der.sequence(
            Der.context(0, Der.integer(1)), // TSPasswordCreds
            Der.context(1, Der.octetString(passwordCreds)),
        )
    }

    internal fun readRequest(input: DataInputStream): CredSspTsRequest =
        CredSspTsRequest.decode(readDerMessage(input))

    internal fun writeRequest(output: DataOutputStream, request: CredSspTsRequest) {
        output.write(request.encode())
        output.flush()
    }

    internal fun readDerMessage(input: DataInputStream): ByteArray {
        val tag = input.readUnsignedByte()
        if (tag != Der.SEQUENCE) throw IOException("CredSSP packet is not a DER sequence")
        val firstLength = input.readUnsignedByte()
        val lengthBytes: ByteArray
        val length: Int
        if (firstLength and 0x80 == 0) {
            lengthBytes = byteArrayOf(firstLength.toByte())
            length = firstLength
        } else {
            val count = firstLength and 0x7F
            if (count !in 1..4) throw IOException("invalid CredSSP DER length")
            val encodedLength = ByteArray(count).also(input::readFully)
            if (encodedLength[0].toInt() == 0) throw IOException("non-minimal CredSSP DER length")
            var parsed = 0L
            encodedLength.forEach { parsed = (parsed shl 8) or (it.toLong() and 0xFF) }
            if (parsed < 0x80 || parsed > MAX_REQUEST_BYTES) throw IOException("invalid CredSSP packet length: $parsed")
            lengthBytes = byteArrayOf(firstLength.toByte()) + encodedLength
            length = parsed.toInt()
        }
        if (length > MAX_REQUEST_BYTES) throw IOException("CredSSP packet is too large: $length")
        return byteArrayOf(tag.toByte()) + lengthBytes + ByteArray(length).also(input::readFully)
    }

    private fun checkError(request: CredSspTsRequest) {
        val error = request.errorCode
        if (error != null && error != 0L) throw CredSspAuthenticationException(error)
    }

    private const val MAX_REQUEST_BYTES = 16 * 1024 * 1024
}

internal class CredSspAuthenticationException(val status: Long) :
    IOException("CredSSP authentication failed: 0x${status.toString(16)}")
