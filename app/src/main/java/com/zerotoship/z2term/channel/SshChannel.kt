package com.zerotoship.z2term.channel

import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

/**
 * SSH 経由のリモートシェルチャンネル。JSch (mwiede fork) を使用。
 *
 * 接続: `connect(profile)` で同期接続。失敗時は例外。
 *
 * 注意 (M5 基礎部分のスコープ):
 * - 公開鍵認証は未実装 (パスワードのみ)
 * - ホスト鍵検証は no (本番運用時は要厳格化)
 * - known_hosts 永続化は未対応
 */
class SshChannel private constructor(
    private val session: Session,
    private val channel: ChannelShell
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
        fun connect(profile: SshProfile, rows: Int, cols: Int): SshChannel {
            val jsch = JSch()
            // 鍵認証: PEM テキストを匿名 identity として登録
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
            // 簡易設定: 既知 host 検証は無効 (M6 で known_hosts 対応)
            session.setConfig(Properties().apply {
                put("StrictHostKeyChecking", "no")
                put(
                    "PreferredAuthentications",
                    if (profile.authType == SshProfile.AuthType.PUBLIC_KEY)
                        "publickey,password,keyboard-interactive"
                    else
                        "password,keyboard-interactive,publickey"
                )
            })
            session.connect(CONNECT_TIMEOUT_MS)

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")
            channel.setPtySize(cols, rows, cols * 8, rows * 16)
            channel.connect(CONNECT_TIMEOUT_MS)
            Log.i(TAG, "SSH connected to ${profile.user}@${profile.host}:${profile.port}")
            return SshChannel(session, channel)
        }

        private const val CONNECT_TIMEOUT_MS = 15_000
    }
}
