package com.zerotoship.z2term.channel

import android.content.Context
import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.zerotoship.z2term.net.HostAddress
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
 *
 * 踏み台 (`-J`) を経由する場合も入口は同じ。経路まるごとが [SshLink] に入っていて、
 * [close] で**奥から順に**畳まれる。
 */
class SshChannel private constructor(
    private val link: SshLink,
    private val channel: ChannelShell,
    /** 接続時に確立できたポート転送の人間可読サマリ (UI バナー用) */
    val forwardSummary: List<String> = emptyList()
) : ProcessChannel {

    override val reader: InputStream = channel.inputStream
    override val writer: OutputStream = channel.outputStream

    override val isAlive: Boolean
        get() = channel.isConnected && link.isConnected

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
        runCatching { link.close() }
    }

    companion object {
        private const val TAG = "SshChannel"

        /**
         * プロファイルに従って同期接続。
         * 呼び出し元は IO Dispatcher で実行すること。
         */
        fun connect(profile: SshProfile, rows: Int, cols: Int, context: Context): SshChannel {
            // 認証 / known_hosts 検証 / UserInfo / 踏み台は SshSessionFactory に共通化
            // (SFTP・サービス経路・常駐トンネルと共有)。
            val link = SshSessionFactory.create(profile, context)
            try {
                link.connect(CONNECT_TIMEOUT_MS)

                // M7: ポート転送をセッション開通直後に設定。向きは PortForward.reverse で決まる。
                // 失敗した転送は警告ログに留め、確立できたものはサマリを返す。
                val result = PortForwarding.apply(link.session, profile.forwards)
                val summary = result.established + result.failed.map { "✗ ${it.describe()}" }

                val channel = link.session.openChannel("shell") as ChannelShell
                channel.setPtyType("xterm-256color")
                channel.setPtySize(cols, rows, cols * 8, rows * 16)
                channel.connect(CONNECT_TIMEOUT_MS)
                Log.i(
                    TAG,
                    "SSH connected to ${profile.user}@${HostAddress.hostPort(profile.host, profile.port)}" +
                        if (link.jumpCount > 0) " via ${link.jumpCount} jump host(s)" else "",
                )
                return SshChannel(link, channel, summary)
            } catch (e: Throwable) {
                // ⚠ 踏み台まで開いた後で折れることがある。畳まないと経由先だけ繋がったまま残る。
                runCatching { link.close() }
                throw e
            }
        }

        private const val CONNECT_TIMEOUT_MS = 15_000
    }
}
