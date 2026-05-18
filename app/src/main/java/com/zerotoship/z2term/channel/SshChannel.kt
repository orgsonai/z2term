package com.zerotoship.z2term.channel

import android.content.Context
import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

/**
 * SSH 経由のリモートシェルチャンネル。JSch (mwiede fork) を使用。
 *
 * 接続: `connect(profile)` で同期接続。失敗時は例外。
 *
 * M6 で追加された挙動:
 * - PUBLIC_KEY 認証 (秘密鍵 PEM + 任意 passphrase)
 * - DataStoreHostKeyRepository による known_hosts 検証
 * - StrictHostKeyChecking=ask + UserInfo で未知ホストは UI に確認
 */
class SshChannel private constructor(
    private val session: Session,
    private val channel: ChannelShell,
    /** 接続時に確立できたポート転送の人間可読サマリ (UI バナー用) */
    val forwardSummary: List<String> = emptyList()
) : ProcessChannel {

    override val reader: InputStream = channel.inputStream
    override val writer: OutputStream = channel.outputStream

    override val isAlive: Boolean
        get() = channel.isConnected && session.isConnected

    override val exitCode: Int?
        get() = if (channel.isClosed) channel.exitStatus else null

    override fun resize(rows: Int, cols: Int) {
        if (!isAlive) return
        try {
            channel.setPtySize(cols, rows, cols * 8, rows * 16)
        } catch (e: Exception) {
            Log.w(TAG, "setPtySize failed: ${e.message}")
        }
    }

    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }

    companion object {
        private const val TAG = "SshChannel"

        /**
         * プロファイルに従って同期接続。
         * 呼び出し元は IO Dispatcher で実行すること。
         */
        fun connect(profile: SshProfile, rows: Int, cols: Int, context: Context): SshChannel {
            val jsch = JSch()
            jsch.hostKeyRepository = KnownHostsHolder.repository(context)

            if (profile.authType == SshProfile.AuthType.PUBLIC_KEY && profile.privateKey.isNotBlank()) {
                val keyBytes = profile.privateKey.toByteArray(Charsets.UTF_8)
                val passphrase = profile.keyPassphrase.takeIf { it.isNotEmpty() }
                    ?.toByteArray(Charsets.UTF_8)
                jsch.addIdentity(profile.id, keyBytes, null, passphrase)
            }

            val session = jsch.getSession(profile.user, profile.host, profile.port)
            if (profile.authType == SshProfile.AuthType.PASSWORD && profile.password.isNotEmpty()) {
                session.setPassword(profile.password)
            }
            session.setConfig(Properties().apply {
                put("StrictHostKeyChecking", "ask")
                put(
                    "PreferredAuthentications",
                    if (profile.authType == SshProfile.AuthType.PUBLIC_KEY)
                        "publickey,password,keyboard-interactive"
                    else
                        "password,keyboard-interactive,publickey"
                )
            })
            session.userInfo = VerifyingUserInfo(profile)
            session.connect(CONNECT_TIMEOUT_MS)

            // M7: -L ローカルポート転送をセッション開通直後に設定。
            // 失敗した転送は警告ログに留め、確立できたものはサマリを返す。
            val summary = mutableListOf<String>()
            for (fwd in profile.forwards) {
                try {
                    val assigned = session.setPortForwardingL(
                        fwd.bindAddress, fwd.localPort, fwd.remoteHost, fwd.remotePort
                    )
                    summary += "${fwd.bindAddress}:${assigned} → ${fwd.remoteHost}:${fwd.remotePort}"
                    Log.i(TAG, "PortForwardL: ${fwd.bindAddress}:$assigned → ${fwd.remoteHost}:${fwd.remotePort}")
                } catch (e: Exception) {
                    Log.w(TAG, "PortForwardL failed for $fwd: ${e.message}")
                    summary += "✗ ${fwd.bindAddress}:${fwd.localPort} (${e.message})"
                }
            }

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")
            channel.setPtySize(cols, rows, cols * 8, rows * 16)
            channel.connect(CONNECT_TIMEOUT_MS)
            Log.i(TAG, "SSH connected to ${profile.user}@${profile.host}:${profile.port}")
            return SshChannel(session, channel, summary)
        }

        private const val CONNECT_TIMEOUT_MS = 15_000
    }
}

/**
 * JSch UserInfo の最小実装。
 *
 * - パスフレーズ要求は profile.keyPassphrase を返す
 * - パスワード要求は profile.password を返す (PASSWORD 認証時に呼ばれる場合もある)
 * - promptYesNo は known_hosts の確認 → [HostKeyVerifier] 経由で UI に問い合わせ
 *
 * JSch がメッセージ文字列で何を聞いているかを解釈する必要があるが、
 * 多くの場合 "authenticity of host" / "key fingerprint" などのキーワードで判別可能。
 */
private class VerifyingUserInfo(private val profile: SshProfile) : UserInfo {

    private var passwordTried = false
    private var passphraseTried = false

    override fun getPassphrase(): String? = profile.keyPassphrase.takeIf { it.isNotEmpty() }
    override fun getPassword(): String? = profile.password.takeIf { it.isNotEmpty() }

    override fun promptPassword(message: String?): Boolean {
        return if (!passwordTried) { passwordTried = true; profile.password.isNotEmpty() } else false
    }

    override fun promptPassphrase(message: String?): Boolean {
        return if (!passphraseTried) { passphraseTried = true; profile.keyPassphrase.isNotEmpty() } else false
    }

    override fun promptYesNo(message: String?): Boolean {
        val msg = message ?: return false
        val fingerprint = extractFingerprint(msg)
        val keyType = extractKeyType(msg)
        return HostKeyVerifier.requestVerify(
            HostKeyVerifier.Prompt(
                host = "${profile.user}@${profile.host}:${profile.port}",
                keyType = keyType,
                fingerprint = fingerprint,
                message = msg
            )
        )
    }

    override fun showMessage(message: String?) { /* no-op (TerminalSession には流さない) */ }

    private fun extractFingerprint(msg: String): String {
        val re = Regex("""key fingerprint is\s+([^\s\n.]+)""", RegexOption.IGNORE_CASE)
        return re.find(msg)?.groupValues?.get(1) ?: "(unknown)"
    }

    private fun extractKeyType(msg: String): String {
        val re = Regex("""(RSA|DSA|ECDSA|ED25519)\s+key fingerprint""", RegexOption.IGNORE_CASE)
        return re.find(msg)?.groupValues?.get(1)?.uppercase() ?: "(unknown)"
    }
}
