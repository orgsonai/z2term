package com.zerotoship.z2term.gui.rdp

import java.security.MessageDigest

/** MS-CSSP 3.1.5 の CredSSP v5/v6 TLS public-key binding hash。 */
internal object CredSspBinding {
    private val CLIENT_MAGIC = "CredSSP Client-To-Server Binding Hash\u0000".toByteArray(Charsets.US_ASCII)
    private val SERVER_MAGIC = "CredSSP Server-To-Client Binding Hash\u0000".toByteArray(Charsets.US_ASCII)

    fun clientHash(clientNonce: ByteArray, subjectPublicKey: ByteArray): ByteArray =
        bindingHash(CLIENT_MAGIC, clientNonce, subjectPublicKey)

    fun serverHash(clientNonce: ByteArray, subjectPublicKey: ByteArray): ByteArray =
        bindingHash(SERVER_MAGIC, clientNonce, subjectPublicKey)

    private fun bindingHash(
        magic: ByteArray,
        clientNonce: ByteArray,
        subjectPublicKey: ByteArray,
    ): ByteArray {
        require(clientNonce.size == CredSspTsRequest.CLIENT_NONCE_BYTES) { "CredSSP nonce must be 32 bytes" }
        return MessageDigest.getInstance("SHA-256").run {
            update(magic)
            update(clientNonce)
            digest(subjectPublicKey)
        }
    }
}
