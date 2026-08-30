package com.zerotoship.z2term.gui.rdp

import java.io.IOException

/** CredSSP が運ぶ SPNEGO token のうち、NTLMSSP に必要な最小部分。 */
internal object Spnego {
    private val SPNEGO_OID = byteArrayOf(0x2B, 0x06, 0x01, 0x05, 0x05, 0x02)
    private val NTLM_OID = byteArrayOf(0x2B, 0x06, 0x01, 0x04, 0x01, 0x82.toByte(), 0x37, 0x02, 0x02, 0x0A)
    private val NTLM_SIGNATURE = "NTLMSSP\u0000".toByteArray(Charsets.US_ASCII)

    fun initialToken(ntlmNegotiate: ByteArray): ByteArray = Der.tlv(
        0x60, // GSS InitialContextToken [APPLICATION 0]
        Der.concat(
            Der.tlv(Der.OBJECT_IDENTIFIER, SPNEGO_OID),
            Der.context(
                0,
                Der.sequence(
                    Der.context(0, Der.sequence(Der.tlv(Der.OBJECT_IDENTIFIER, NTLM_OID))),
                    Der.context(2, Der.octetString(ntlmNegotiate)),
                ),
            ),
        ),
    )

    fun responseToken(ntlmAuthenticate: ByteArray): ByteArray =
        Der.context(1, Der.sequence(Der.context(2, Der.octetString(ntlmAuthenticate))))

    /** server の NegTokenResp から NTLM CHALLENGE_MESSAGE を取り出す。 */
    fun ntlmToken(encoded: ByteArray): ByteArray = findNtlmToken(Der.Reader(encoded))
        ?: throw IOException("SPNEGO response has no NTLM token")

    private fun findNtlmToken(reader: Der.Reader): ByteArray? {
        while (reader.hasRemaining()) {
            val value = reader.read()
            if (value.tag == Der.OCTET_STRING && value.body.startsWith(NTLM_SIGNATURE)) return value.body
            if (value.tag and 0x20 != 0) findNtlmToken(value.reader())?.let { return it }
        }
        return null
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
