package com.zerotoship.z2term.gui.rdp

import java.io.IOException

/** MS-CSSP 2.2.1 の TSRequest。SPNEGO/NTLM token はこの層では不透明な byte 列として運ぶ。 */
internal data class CredSspTsRequest(
    val version: Int = VERSION,
    val negoToken: ByteArray? = null,
    val authInfo: ByteArray? = null,
    val pubKeyAuth: ByteArray? = null,
    val errorCode: Long? = null,
    val clientNonce: ByteArray? = null,
) {
    fun encode(): ByteArray {
        require(version in 2..VERSION) { "unsupported CredSSP version: $version" }
        clientNonce?.let { require(it.size == CLIENT_NONCE_BYTES) { "CredSSP nonce must be 32 bytes" } }
        val fields = buildList {
            add(Der.context(0, Der.integer(version.toLong())))
            negoToken?.let { token ->
                // NegoData ::= SEQUENCE OF NegoDataItem; NegoDataItem ::= SEQUENCE { negoToken [0] OCTET STRING }
                add(Der.context(1, Der.sequence(Der.sequence(Der.context(0, Der.octetString(token))))))
            }
            authInfo?.let { add(Der.context(2, Der.octetString(it))) }
            pubKeyAuth?.let { add(Der.context(3, Der.octetString(it))) }
            errorCode?.let { add(Der.context(4, Der.integer(it))) }
            clientNonce?.let { add(Der.context(5, Der.octetString(it))) }
        }
        return Der.tlv(Der.SEQUENCE, Der.concat(*fields.toTypedArray()))
    }

    companion object {
        const val VERSION = 6
        const val CLIENT_NONCE_BYTES = 32

        fun decode(encoded: ByteArray): CredSspTsRequest {
            val outer = Der.Reader(encoded)
            val sequence = outer.read(Der.SEQUENCE)
            outer.requireEnd()
            val fields = sequence.reader()
            var version: Int? = null
            var negoToken: ByteArray? = null
            var authInfo: ByteArray? = null
            var pubKeyAuth: ByteArray? = null
            var errorCode: Long? = null
            var clientNonce: ByteArray? = null
            while (fields.hasRemaining()) {
                val field = fields.read()
                when (field.tag) {
                    0xA0 -> {
                        val value = field.singleInteger()
                        if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                            throw IOException("CredSSP version is out of range: $value")
                        }
                        version = value.toInt()
                    }
                    0xA1 -> negoToken = decodeNegoToken(field)
                    0xA2 -> authInfo = field.singleOctetString()
                    0xA3 -> pubKeyAuth = field.singleOctetString()
                    0xA4 -> errorCode = field.singleInteger()
                    0xA5 -> clientNonce = field.singleOctetString()
                    else -> throw IOException("unsupported TSRequest field 0x${field.tag.toString(16)}")
                }
            }
            val decodedVersion = version ?: throw IOException("TSRequest has no version")
            if (decodedVersion < 2) throw IOException("unsupported CredSSP version: $decodedVersion")
            clientNonce?.let {
                if (it.size != CLIENT_NONCE_BYTES) throw IOException("invalid CredSSP nonce length: ${it.size}")
            }
            return CredSspTsRequest(decodedVersion, negoToken, authInfo, pubKeyAuth, errorCode, clientNonce)
        }

        private fun decodeNegoToken(field: Der.Value): ByteArray {
            val context = field.reader()
            val items = context.read(Der.SEQUENCE).reader()
            context.requireEnd()
            val item = items.read(Der.SEQUENCE).reader()
            items.requireEnd()
            val tokenField = item.read(0xA0)
            item.requireEnd()
            return tokenField.singleOctetString()
        }

        private fun Der.Value.singleInteger(): Long {
            val reader = reader()
            val value = reader.read(Der.INTEGER)
            reader.requireEnd()
            return Der.decodeInteger(value)
        }

        private fun Der.Value.singleOctetString(): ByteArray {
            val reader = reader()
            val value = reader.read(Der.OCTET_STRING)
            reader.requireEnd()
            return value.body
        }
    }

    override fun equals(other: Any?): Boolean = other is CredSspTsRequest &&
        version == other.version &&
        negoToken.contentEqualsNullable(other.negoToken) &&
        authInfo.contentEqualsNullable(other.authInfo) &&
        pubKeyAuth.contentEqualsNullable(other.pubKeyAuth) &&
        errorCode == other.errorCode &&
        clientNonce.contentEqualsNullable(other.clientNonce)

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + (negoToken?.contentHashCode() ?: 0)
        result = 31 * result + (authInfo?.contentHashCode() ?: 0)
        result = 31 * result + (pubKeyAuth?.contentHashCode() ?: 0)
        result = 31 * result + (errorCode?.hashCode() ?: 0)
        result = 31 * result + (clientNonce?.contentHashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null -> other == null
    other == null -> false
    else -> contentEquals(other)
}
