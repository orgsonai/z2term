package com.zerotoship.z2term.gui.rdp

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** X.224 で CredSSP を選び、その同じ TCP 接続を TLS へ昇格させた結果。 */
internal class RdpTlsTransport private constructor(
    private val socket: SSLSocket,
    val input: DataInputStream,
    val output: DataOutputStream,
    val serverCertificate: X509Certificate,
) : Closeable {
    /** CredSSP v5+ の binding hash に入れる SubjectPublicKey の BIT STRING 本体。 */
    val subjectPublicKey: ByteArray = subjectPublicKey(serverCertificate.publicKey.encoded)

    /** TLS の内側で NLA (CredSSP/NTLMv2) を完了し、後続の RDP MCS PDU を送れる状態にする。 */
    fun authenticate(credentials: CredSspNtlm.Credentials) {
        CredSspNtlm.authenticate(input, output, subjectPublicKey, credentials)
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { socket.close() }
    }

    companion object {
        private const val MAX_NEGOTIATION_PACKET = 64 * 1024

        /**
         * Windows の RDP 証明書は自己署名が一般的なので、暗黙にシステム CA だけへも、全許可へも
         * 寄せない。[certificateVerifier] は UI 側の保存済み fingerprint / 明示承認を受け取る境界。
         */
        fun connect(
            host: String,
            port: Int,
            timeoutMs: Int,
            certificateVerifier: (X509Certificate) -> Boolean,
        ): RdpTlsTransport {
            val plain = Socket()
            try {
                plain.tcpNoDelay = true
                plain.connect(InetSocketAddress(host, port), timeoutMs)
                plain.soTimeout = timeoutMs
                val input = DataInputStream(BufferedInputStream(plain.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(plain.getOutputStream()))
                output.write(RdpNegotiation.connectionRequest())
                output.flush()
                val selected = RdpNegotiation.selectedProtocol(readTpkt(input))
                if (selected and RdpNegotiation.PROTOCOL_HYBRID == 0) {
                    throw IOException("RDP server did not select CredSSP: 0x${selected.toUInt().toString(16)}")
                }

                val trustManager = RecordingTrustManager()
                val context = SSLContext.getInstance("TLS")
                context.init(null, arrayOf(trustManager), SecureRandom())
                val ssl = context.socketFactory.createSocket(plain, host, port, true) as SSLSocket
                ssl.soTimeout = timeoutMs
                ssl.startHandshake()
                val certificate = trustManager.serverChain?.firstOrNull()
                    ?: (ssl.session.peerCertificates.firstOrNull() as? X509Certificate)
                    ?: throw CertificateException("RDP server sent no X.509 certificate")
                if (!certificateVerifier(certificate)) {
                    throw CertificateException("RDP server certificate was not accepted")
                }
                ssl.soTimeout = 0
                return RdpTlsTransport(
                    socket = ssl,
                    input = DataInputStream(BufferedInputStream(ssl.inputStream)),
                    output = DataOutputStream(BufferedOutputStream(ssl.outputStream)),
                    serverCertificate = certificate,
                )
            } catch (e: Exception) {
                runCatching { plain.close() }
                throw e
            }
        }

        internal fun readTpkt(input: DataInputStream): ByteArray {
            val header = ByteArray(4)
            input.readFully(header)
            if ((header[0].toInt() and 0xFF) != 3 || header[1].toInt() != 0) {
                throw IOException("invalid TPKT header")
            }
            val length = ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
            if (length !in 4..MAX_NEGOTIATION_PACKET) throw IOException("invalid TPKT length: $length")
            return header + ByteArray(length - header.size).also(input::readFully)
        }

        /** SubjectPublicKeyInfo ::= SEQUENCE { algorithm, subjectPublicKey BIT STRING }。 */
        internal fun subjectPublicKey(subjectPublicKeyInfo: ByteArray): ByteArray {
            val outer = Der.Reader(subjectPublicKeyInfo)
            val sequence = outer.read(Der.SEQUENCE).reader()
            outer.requireEnd()
            sequence.read(Der.SEQUENCE) // AlgorithmIdentifier は binding の対象外
            val bitString = sequence.read(Der.BIT_STRING).body
            sequence.requireEnd()
            if (bitString.isEmpty() || bitString[0].toInt() != 0) {
                throw CertificateException("unsupported SubjectPublicKey BIT STRING")
            }
            return bitString.copyOfRange(1, bitString.size)
        }
    }

    /** 証明書の信頼判断は TLS 後に [certificateVerifier] で行うため、ここでは chain を記録する。 */
    private class RecordingTrustManager : X509TrustManager {
        var serverChain: Array<out X509Certificate>? = null
            private set

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) throw CertificateException("RDP server sent an empty certificate chain")
            serverChain = chain.copyOf()
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
