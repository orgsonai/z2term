package com.zerotoship.z2term.channel

import android.content.Context
import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.zerotoship.z2term.net.HostAddress
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream

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
            // 認証 / known_hosts 検証 / UserInfo は SshSessionFactory に共通化 (SFTP と共有)
            val session = SshSessionFactory.create(profile, context)
            session.connect(CONNECT_TIMEOUT_MS)

            // M7: -L ローカルポート転送をセッション開通直後に設定。
            // 失敗した転送は警告ログに留め、確立できたものはサマリを返す。
            val summary = mutableListOf<String>()
            for (fwd in profile.forwards) {
                try {
                    val assigned = session.setPortForwardingL(
                        HostAddress.normalize(fwd.bindAddress),
                        fwd.localPort,
                        HostAddress.normalize(fwd.remoteHost),
                        fwd.remotePort,
                    )
                    summary += "${HostAddress.hostPort(fwd.bindAddress, assigned)} → " +
                        HostAddress.hostPort(fwd.remoteHost, fwd.remotePort)
                    Log.i(TAG, "PortForwardL: ${summary.last()}")
                } catch (e: Exception) {
                    Log.w(TAG, "PortForwardL failed for $fwd: ${e.message}")
                    summary += "✗ ${fwd.bindAddress}:${fwd.localPort} (${e.message})"
                }
            }

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")
            channel.setPtySize(cols, rows, cols * 8, rows * 16)
            channel.connect(CONNECT_TIMEOUT_MS)
            Log.i(TAG, "SSH connected to ${profile.user}@${HostAddress.hostPort(profile.host, profile.port)}")
            return SshChannel(session, channel, summary)
        }

        private const val CONNECT_TIMEOUT_MS = 15_000
    }
}
