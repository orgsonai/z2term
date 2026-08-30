package com.zerotoship.z2term.gui.rdp

import java.io.IOException
import java.security.MessageDigest

/** MS-NLMP connection-oriented SIGN/SEAL。送受信で別々の RC4 状態と sequence number を持つ。 */
internal class NtlmSessionSecurity(
    exportedSessionKey: ByteArray,
    private val flags: Int,
    clientRole: Boolean = true,
) {
    private val sendSigningKey: ByteArray
    private val receiveSigningKey: ByteArray
    private val sendCipher: Rc4
    private val receiveCipher: Rc4
    private var sendSequence = 0
    private var receiveSequence = 0

    init {
        require(exportedSessionKey.size == 16)
        require(flags and NtlmWire.NEGOTIATE_EXTENDED_SESSION_SECURITY != 0)
        require(flags and NtlmWire.NEGOTIATE_SIGN != 0)
        require(flags and NtlmWire.NEGOTIATE_SEAL != 0)
        val clientSign = signingKey(exportedSessionKey, CLIENT_SIGNING_MAGIC)
        val serverSign = signingKey(exportedSessionKey, SERVER_SIGNING_MAGIC)
        val clientSeal = sealingKey(exportedSessionKey, flags, CLIENT_SEALING_MAGIC)
        val serverSeal = sealingKey(exportedSessionKey, flags, SERVER_SEALING_MAGIC)
        if (clientRole) {
            sendSigningKey = clientSign
            receiveSigningKey = serverSign
            sendCipher = Rc4(clientSeal)
            receiveCipher = Rc4(serverSeal)
        } else {
            sendSigningKey = serverSign
            receiveSigningKey = clientSign
            sendCipher = Rc4(serverSeal)
            receiveCipher = Rc4(clientSeal)
        }
    }

    @Synchronized
    fun wrap(plainText: ByteArray): ByteArray {
        val sequence = littleEndian(sendSequence)
        val encrypted = sendCipher.process(plainText)
        val checksum = NtlmV2Crypto.hmacMd5(sendSigningKey, sequence + plainText).copyOf(8)
        val wireChecksum = if (flags and NtlmWire.NEGOTIATE_KEY_EXCHANGE != 0) {
            sendCipher.process(checksum)
        } else {
            checksum
        }
        val signature = littleEndian(SIGNATURE_VERSION) + wireChecksum + sequence
        sendSequence++
        return signature + encrypted
    }

    @Synchronized
    fun unwrap(sealed: ByteArray): ByteArray {
        if (sealed.size < SIGNATURE_SIZE) throw IOException("truncated NTLM sealed message")
        val signature = sealed.copyOfRange(0, SIGNATURE_SIZE)
        if (NtlmWire.u32(signature, 0) != SIGNATURE_VERSION) throw IOException("invalid NTLM signature version")
        if (NtlmWire.u32(signature, 12) != receiveSequence) throw IOException("invalid NTLM sequence number")
        val plainText = receiveCipher.process(sealed.copyOfRange(SIGNATURE_SIZE, sealed.size))
        val receivedChecksum = signature.copyOfRange(4, 12)
        val checksum = if (flags and NtlmWire.NEGOTIATE_KEY_EXCHANGE != 0) {
            receiveCipher.process(receivedChecksum)
        } else {
            receivedChecksum
        }
        val sequence = littleEndian(receiveSequence)
        val expected = NtlmV2Crypto.hmacMd5(receiveSigningKey, sequence + plainText).copyOf(8)
        if (!MessageDigest.isEqual(expected, checksum)) throw IOException("invalid NTLM message signature")
        receiveSequence++
        return plainText
    }

    companion object {
        private const val SIGNATURE_VERSION = 1
        private const val SIGNATURE_SIZE = 16
        private val CLIENT_SIGNING_MAGIC = magic("session key to client-to-server signing key magic constant")
        private val SERVER_SIGNING_MAGIC = magic("session key to server-to-client signing key magic constant")
        private val CLIENT_SEALING_MAGIC = magic("session key to client-to-server sealing key magic constant")
        private val SERVER_SEALING_MAGIC = magic("session key to server-to-client sealing key magic constant")

        internal fun signingKey(exportedSessionKey: ByteArray, magic: ByteArray): ByteArray =
            MessageDigest.getInstance("MD5").digest(exportedSessionKey + magic)

        internal fun sealingKey(exportedSessionKey: ByteArray, flags: Int, magic: ByteArray): ByteArray {
            val material = when {
                flags and NtlmWire.NEGOTIATE_128 != 0 -> exportedSessionKey
                flags and NtlmWire.NEGOTIATE_56 != 0 -> exportedSessionKey.copyOf(7)
                else -> exportedSessionKey.copyOf(5)
            }
            return MessageDigest.getInstance("MD5").digest(material + magic)
        }

        private fun magic(value: String): ByteArray = (value + '\u0000').toByteArray(Charsets.US_ASCII)

        private fun littleEndian(value: Int): ByteArray = ByteArray(4).also { NtlmWire.putU32(it, 0, value) }
    }
}
