package com.zerotoship.z2term.gui.rdp

import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** MS-NLMP 3.3.2 のNTLMv2応答とセッション基底鍵の導出。 */
internal object NtlmV2Crypto {
    data class Response(
        val ntChallengeResponse: ByteArray,
        val lmChallengeResponse: ByteArray,
        val sessionBaseKey: ByteArray,
    )

    fun ntHash(password: String): ByteArray = Md4.digest(password.utf16Le())

    fun responseKeyNt(user: String, domain: String, password: String): ByteArray = hmacMd5(
        key = ntHash(password),
        message = (user.uppercase(Locale.ROOT) + domain).utf16Le(),
    )

    /**
     * @param timestamp Windows FILETIME の8バイト little-endian値。CHALLENGE_MESSAGEにあればそれを使う。
     * @param targetInfo CHALLENGE_MESSAGEのAV_PAIR列（MsvAvEOLを含む）。
     */
    fun computeResponse(
        responseKey: ByteArray,
        serverChallenge: ByteArray,
        clientChallenge: ByteArray,
        timestamp: ByteArray,
        targetInfo: ByteArray,
        suppressLmResponse: Boolean = false,
    ): Response {
        require(responseKey.size == 16)
        require(serverChallenge.size == 8)
        require(clientChallenge.size == 8)
        require(timestamp.size == 8)
        val temp = Der.concat(
            byteArrayOf(1, 1),
            ByteArray(6),
            timestamp,
            clientChallenge,
            ByteArray(4),
            targetInfo,
            ByteArray(4),
        )
        val proof = hmacMd5(responseKey, serverChallenge + temp)
        val ntResponse = proof + temp
        val lmResponse = if (suppressLmResponse) {
            ByteArray(24)
        } else {
            hmacMd5(responseKey, serverChallenge + clientChallenge) + clientChallenge
        }
        return Response(
            ntChallengeResponse = ntResponse,
            lmChallengeResponse = lmResponse,
            sessionBaseKey = hmacMd5(responseKey, proof),
        )
    }

    internal fun hmacMd5(key: ByteArray, message: ByteArray): ByteArray =
        Mac.getInstance("HmacMD5").run {
            init(SecretKeySpec(key, "HmacMD5"))
            doFinal(message)
        }

    private fun String.utf16Le(): ByteArray = toByteArray(StandardCharsets.UTF_16LE)
}
