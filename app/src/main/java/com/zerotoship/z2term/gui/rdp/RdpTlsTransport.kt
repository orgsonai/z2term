package com.zerotoship.z2term.gui.rdp

import android.util.Log
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
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** X.224 で CredSSP を選び、その同じ TCP 接続を TLS へ昇格させた結果。 */
internal class RdpTlsTransport private constructor(
    private val socket: SSLSocket,
    val input: DataInputStream,
    val output: DataOutputStream,
    val serverCertificate: X509Certificate,
) : Closeable {
    private val writeLock = Any()
    /** CredSSP v5+ の binding hash に入れる SubjectPublicKey の BIT STRING 本体。 */
    val subjectPublicKey: ByteArray = subjectPublicKey(serverCertificate.publicKey.encoded)

    /** TLS の内側で NLA (CredSSP/NTLMv2) を完了し、後続の RDP MCS PDU を送れる状態にする。 */
    fun authenticate(credentials: CredSspNtlm.Credentials) {
        CredSspNtlm.authenticate(input, output, subjectPublicKey, credentials)
    }

    /** NLA 後の Basic Settings Exchange と MCS channel connection を完了する。 */
    fun connectMcs(settings: RdpMcs.ClientSettings = RdpMcs.ClientSettings()): RdpMcs.Session =
        RdpMcs.connect(input, output, settings)

    /** Client Info、licensing、Demand/Confirm Active を完了し、画面更新を受けられる状態にする。 */
    fun activate(
        session: RdpMcs.Session,
        credentials: CredSspNtlm.Credentials,
        settings: RdpMcs.ClientSettings = RdpMcs.ClientSettings(),
    ): RdpActivation.ActiveSession = RdpActivation.activate(input, output, session, credentials, settings)

    /** Synchronize / Control / Font List/Map を交換し、通常の画面更新を受信できる状態にする。 */
    fun finalizeConnection(session: RdpMcs.Session, active: RdpActivation.ActiveSession) =
        RdpActivation.finalizeConnection(input, output, session, active)

    /** 画面全体を送り直すよう頼む。接続直後に何も描かれないときの取っかかり。 */
    fun requestRefresh(session: RdpMcs.Session, active: RdpActivation.ActiveSession, width: Int, height: Int) {
        synchronized(writeLock) {
            output.write(RdpActivation.refreshRect(session, active, width, height))
            output.flush()
        }
    }

    /** ポインター／キーの slow-path Input Event を、他の送信と混線しないようまとめて送る。 */
    fun sendInputEvents(
        session: RdpMcs.Session,
        active: RdpActivation.ActiveSession,
        events: List<RdpInput.Event>,
    ) {
        if (events.isEmpty()) return
        synchronized(writeLock) {
            output.write(RdpActivation.inputEvents(session, active, events))
            output.flush()
        }
    }

    /** 通常状態のslow-path PDUを1つ読み、classic Bitmap Updateならその本体を返す。 */
    fun readBitmapUpdate(session: RdpMcs.Session, active: RdpActivation.ActiveSession): ByteArray? =
        RdpActivation.readBitmapUpdate(input, session, active)

    fun readChannelData(): RdpActivation.ChannelData =
        RdpActivation.channelData(readTpkt(input))

    fun sendVirtualChannel(session: RdpMcs.Session, channelId: Int, message: ByteArray) {
        synchronized(writeLock) {
            var offset = 0
            do {
                val count = minOf(CHANNEL_CHUNK_BYTES, message.size - offset)
                val first = offset == 0
                val last = offset + count >= message.size
                val payload = ByteArray(8 + count)
                putLe32(payload, 0, message.size)
                putLe32(
                    payload,
                    4,
                    (if (first) CHANNEL_FLAG_FIRST else 0) or
                        (if (last) CHANNEL_FLAG_LAST else 0) or CHANNEL_FLAG_SHOW_PROTOCOL,
                )
                if (count > 0) message.copyInto(payload, 8, offset, offset + count)
                output.write(RdpActivation.virtualChannelPacket(session, channelId, payload))
                offset += count
            } while (offset < message.size)
            output.flush()
        }
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { socket.close() }
    }

    companion object {
        private const val TAG = "RdpTlsTransport"
        private const val MAX_NEGOTIATION_PACKET = 64 * 1024
        private const val CHANNEL_FLAG_FIRST = 0x00000001
        private const val CHANNEL_FLAG_LAST = 0x00000002
        private const val CHANNEL_FLAG_SHOW_PROTOCOL = 0x00000010
        private const val CHANNEL_CHUNK_BYTES = 16 * 1024

        private fun putLe32(target: ByteArray, offset: Int, value: Int) {
            repeat(4) { target[offset + it] = (value ushr (it * 8)).toByte() }
        }

        /**
         * 署名に使えない証明書を出す相手のために、**署名を要求しない鍵交換**へ絞る組み合わせ。
         * 優先順に並べてあり、端末が実際に持っているものだけを使う。
         *
         * RSA 鍵交換ではクライアントが premaster secret を証明書の公開鍵で**暗号化**するだけで、
         * サーバーは署名しない。よって `keyEncipherment` しか持たない証明書でも成立する。
         * TLS 1.3 には RSA 鍵交換が無い (必ず署名する) ので、この経路は TLS 1.2 に限られる。
         */
        private val RSA_KEY_EXCHANGE_SUITES = listOf(
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
        )

        /**
         * Windows の RDP 証明書は自己署名が一般的なので、暗黙にシステム CA だけへも、全許可へも
         * 寄せない。[certificateVerifier] は UI 側の保存済み fingerprint / 明示承認を受け取る境界。
         *
         * ⚠⚠ **Windows が自動生成する RDP 証明書は `digitalSignature` を持たない**
         * (`keyUsage` が `keyEncipherment, dataEncipherment` だけ)。TLS 1.3 と ECDHE では
         * **サーバーが証明書の鍵で署名する**ので、Android の TLS 実装はこれを規格違反として
         * `KEY_USAGE_BIT_INCORRECT` で拒否する (PC 側の OpenSSL は見逃すため、同じ相手へ
         * PC からは繋がるのに端末からは繋がらない、という形で出る)。
         * ⇒ **証明書に `digitalSignature` が無いと分かったときだけ**、署名を要求しない
         * RSA 鍵交換へ絞って 1 度だけやり直す。⛔ 常に絞ってはいけない — まともな証明書の
         * 相手まで前方秘匿性を失う。⭐ 鍵交換が RSA になっても**認証情報は守られる**:
         * CredSSP は NTLM のセッション鍵で TLS の公開鍵をバインドする ([CredSspBinding]) ので、
         * 中間者がいれば公開鍵が一致せず検出できる。
         */
        fun connect(
            host: String,
            port: Int,
            timeoutMs: Int,
            certificateVerifier: (X509Certificate) -> Boolean,
        ): RdpTlsTransport {
            try {
                return connectOnce(host, port, timeoutMs, certificateVerifier, rsaKeyExchangeOnly = false)
            } catch (first: SSLException) {
                // ⚠ **やり直すかどうかを、記録した証明書では決められない。** keyUsage の検査は
                // 証明書チェーンを受け取った直後 = TrustManager が呼ばれる**前**に走るので、
                // ここで弾かれた相手の証明書は 1 枚も手に入らない (0.8.461・実機で判明)。
                // ⇒ **TLS の握手が成立しなかったこと自体**を条件にする。TCP 接続の失敗や
                // NLA 非対応 ([RdpNlaUnsupportedException]) は SSLException ではないので、
                // ここには落ちてこない。
                val fallback = try {
                    connectOnce(host, port, timeoutMs, certificateVerifier, rsaKeyExchangeOnly = true)
                } catch (second: Exception) {
                    // ⛔ **落とした側の失敗で元の理由を隠さない。** 利用者が見るのは 1 行だけなので、
                    // 「弱い設定でも駄目だった」ではなく最初に断られた理由を返す。
                    throw first
                }
                if (signingIsForbidden(fallback.serverCertificate.keyUsage)) {
                    Log.i(TAG, "RDP: certificate forbids signing; using RSA key exchange")
                } else {
                    // 署名できる証明書なのに TLS 1.3/ECDHE が通らなかった = 別の理由がある。
                    // 繋がってはいるので止めないが、握り潰さず残す。
                    Log.w(TAG, "RDP: fell back to RSA key exchange for another reason", first)
                }
                return fallback
            }
        }

        /** `keyUsage` の 0 番目が `digitalSignature`。拡張自体が無ければ用途の制限が無い。 */
        internal fun signingIsForbidden(keyUsage: BooleanArray?): Boolean =
            keyUsage != null && keyUsage.isNotEmpty() && !keyUsage[0]

        private fun connectOnce(
            host: String,
            port: Int,
            timeoutMs: Int,
            certificateVerifier: (X509Certificate) -> Boolean,
            rsaKeyExchangeOnly: Boolean,
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
                    throw RdpNlaUnsupportedException(selected)
                }

                val trustManager = RecordingTrustManager()
                val context = SSLContext.getInstance("TLS")
                context.init(null, arrayOf(trustManager), SecureRandom())
                val ssl = context.socketFactory.createSocket(plain, host, port, true) as SSLSocket
                ssl.soTimeout = timeoutMs
                if (rsaKeyExchangeOnly) restrictToRsaKeyExchange(ssl)
                ssl.startHandshake()
                val certificate = trustManager.serverChain?.firstOrNull()
                    ?: (ssl.session.peerCertificates.firstOrNull() as? X509Certificate)
                    ?: throw CertificateException("RDP server sent no X.509 certificate")
                if (!certificateVerifier(certificate)) {
                    throw CertificateException("RDP server certificate was not accepted")
                }
                ssl.soTimeout = 0
                Log.i(TAG, "RDP TLS: ${ssl.session.protocol} / ${ssl.session.cipherSuite}")
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

        /**
         * この 1 回の握手を TLS 1.2 + RSA 鍵交換だけに絞る。端末がどれも持っていなければ、
         * 絞る意味が無いので**元の失敗を返す** (無音で別の失敗に化けさせない)。
         */
        private fun restrictToRsaKeyExchange(ssl: SSLSocket) {
            val protocols = ssl.supportedProtocols.filter { it == "TLSv1.2" }
            val suites = RSA_KEY_EXCHANGE_SUITES.filter { it in ssl.supportedCipherSuites }
            if (protocols.isEmpty() || suites.isEmpty()) {
                throw SSLHandshakeException("no RSA key exchange suite is available on this device")
            }
            ssl.enabledProtocols = protocols.toTypedArray()
            ssl.enabledCipherSuites = suites.toTypedArray()
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
