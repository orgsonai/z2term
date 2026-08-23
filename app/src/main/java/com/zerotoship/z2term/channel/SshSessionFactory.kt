package com.zerotoship.z2term.channel

import android.content.Context
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.zerotoship.z2term.service.NetGuard
import java.util.Properties

/**
 * [SshProfile] から JSch [Session] を構築する共通ファクトリ。
 *
 * シェル接続 ([SshChannel]) と SFTP ([com.zerotoship.z2term.channel.SftpClient]) で
 * 認証方式・known_hosts 検証・UserInfo を共有するために切り出した。
 *
 * 返す Session は **未接続**。呼び出し側で `connect(timeout)` すること
 * (IO Dispatcher 上で行う)。
 */
object SshSessionFactory {

    const val CONNECT_TIMEOUT_MS = 15_000

    /**
     * プロファイルに従い未接続の Session を生成する。
     *
     * ⚠ **通信量の上限 (0.8.388) はここで見る**。シェルも SFTP も常駐トンネルもこの 1 か所を
     * 通るので、入口を増やさずに全部を止められる。⚠ 家の中への接続は止めない
     * ([NetGuard.isLocalTarget]) — モバイル通信を使わない相手を止める理由がない。
     * ⚠ 名前解決を伴うので、**IO スレッドから呼ぶ**という元々の約束がここでも要る。
     */
    fun create(profile: SshProfile, context: Context): Session {
        NetGuard.ensureAllowed(context, profile.host)
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
        return session
    }
}

/**
 * JSch UserInfo の最小実装。
 *
 * - パスフレーズ要求は profile.keyPassphrase を返す
 * - パスワード要求は profile.password を返す (PASSWORD 認証時に呼ばれる場合もある)
 * - promptYesNo は known_hosts の確認 → [HostKeyVerifier] 経由で UI に問い合わせ
 */
internal class VerifyingUserInfo(private val profile: SshProfile) : UserInfo {

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

    override fun showMessage(message: String?) { /* no-op */ }

    private fun extractFingerprint(msg: String): String {
        val re = Regex("""key fingerprint is\s+([^\s\n.]+)""", RegexOption.IGNORE_CASE)
        return re.find(msg)?.groupValues?.get(1) ?: "(unknown)"
    }

    private fun extractKeyType(msg: String): String {
        val re = Regex("""(RSA|DSA|ECDSA|ED25519)\s+key fingerprint""", RegexOption.IGNORE_CASE)
        return re.find(msg)?.groupValues?.get(1)?.uppercase() ?: "(unknown)"
    }
}
