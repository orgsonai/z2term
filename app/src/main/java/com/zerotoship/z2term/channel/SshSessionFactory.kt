package com.zerotoship.z2term.channel

import android.content.Context
import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.zerotoship.z2term.service.NetGuard
import com.zerotoship.z2term.net.HostAddress
import java.util.Properties

/**
 * [SshProfile] から JSch [Session] を構築する共通ファクトリ。
 *
 * シェル接続 ([SshChannel]) と SFTP ([com.zerotoship.z2term.channel.SftpClient]) で
 * 認証方式・known_hosts 検証・UserInfo を共有するために切り出した。
 *
 * ## 踏み台 (`-J`)
 *
 * [SshProfile.jumpHosts] があると、**手前から順に繋いで、その中を通って次へ出る**
 * ([JumpProxy])。段数に上限は設けていない (OpenSSH の `-J a,b,c` と同じ形)。
 *
 * ⚠ **踏み台のセッションはここで繋がる。** 転送を張るには手前が開通している必要があるため、
 * 「未接続の Session を返す」約束が守れるのは**最後の 1 本だけ**。⇒ 戻り値を [SshLink] に
 * して、途中の段の後片付けを呼び出し側が忘れられないようにしてある。
 *
 * ⚠ **鍵は段ごとに別の [JSch] へ登録する。** JSch の identity はインスタンス全体で共有され、
 * どのセッションでも順に試されるので、1 つにまとめると踏み台の鍵を本来の接続先へ差し出して
 * `Too many authentication failures` で切られる。
 */
object SshSessionFactory {

    private const val TAG = "SshSessionFactory"

    const val CONNECT_TIMEOUT_MS = 15_000

    /**
     * プロファイルに従い [SshLink] を生成する。
     *
     * ⚠ **通信量の上限 (0.8.388) はここで見る**。シェルも SFTP も常駐トンネルもこの 1 か所を
     * 通るので、入口を増やさずに全部を止められる。⚠ 家の中への接続は止めない
     * ([NetGuard.isLocalTarget]) — モバイル通信を使わない相手を止める理由がない。
     * ⚠ 踏み台があるとき、**端末が実際に電波を使って繋ぐ相手は 1 段目**なので、上限は
     * 1 段目で判定する (本来の接続先は踏み台の中＝相手側の回線で解決される)。
     * ⚠ 名前解決を伴うので、**IO スレッドから呼ぶ**という元々の約束がここでも要る。
     */
    fun create(profile: SshProfile, context: Context): SshLink {
        val target = HostAddress.normalize(profile.host)
        val hops = profile.jumpHosts
            .filter { it.host.isNotBlank() }
            .map { it.copy(host = HostAddress.normalize(it.host)) }
        NetGuard.ensureAllowed(context, hops.firstOrNull()?.host ?: target)

        val opened = ArrayList<Session>(hops.size)
        try {
            var proxy: Proxy? = null
            hops.forEach { hop ->
                val session = open(context, hop.user, hop.host, hop.port, hop.credentials())
                proxy?.let { session.setProxy(it) }
                session.connect(CONNECT_TIMEOUT_MS)
                Log.i(TAG, "jump host reached: ${hop.describe()}")
                opened += session
                proxy = JumpProxy(session)
            }
            val session = open(context, profile.user, target, profile.port, profile.credentials())
            proxy?.let { session.setProxy(it) }
            return SshLink(session, opened)
        } catch (e: Throwable) {
            // ⚠ 途中で折れたら、開いた段を**奥から順に**畳む (手前を先に切ると奥が宙に浮く)。
            opened.asReversed().forEach { runCatching { it.disconnect() } }
            throw e
        }
    }

    /** 未接続の Session を 1 本作る。段ごとに [JSch] を分けるのはクラスの説明のとおり。 */
    private fun open(
        context: Context,
        user: String,
        host: String,
        port: Int,
        credentials: SshCredentials,
    ): Session {
        val jsch = JSch()
        jsch.hostKeyRepository = KnownHostsHolder.repository(context)

        val usesKey = credentials.authType == SshProfile.AuthType.PUBLIC_KEY &&
            credentials.privateKey.isNotBlank()
        if (usesKey) {
            val keyBytes = credentials.privateKey.toByteArray(Charsets.UTF_8)
            val passphrase = credentials.keyPassphrase.takeIf { it.isNotEmpty() }
                ?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity(HostAddress.hostPort(host, port), keyBytes, null, passphrase)
        }

        val session = jsch.getSession(user, host, port)
        if (credentials.authType == SshProfile.AuthType.PASSWORD &&
            credentials.password.isNotEmpty()
        ) {
            session.setPassword(credentials.password)
        }
        session.setConfig(Properties().apply {
            put("StrictHostKeyChecking", "ask")
            put(
                "PreferredAuthentications",
                if (credentials.authType == SshProfile.AuthType.PUBLIC_KEY)
                    "publickey,password,keyboard-interactive"
                else
                    "password,keyboard-interactive,publickey"
            )
        })
        session.userInfo = VerifyingUserInfo(user, host, port, credentials)
        return session
    }
}

/**
 * 1 つの接続先へ通じている経路まるごと。踏み台が無ければ [session] 1 本だけを持つ。
 *
 * ⭐ **後片付けを 1 か所にまとめるための入れ物。** 踏み台のセッションは [session] にぶら
 * さがっていない別物なので、[session] を `disconnect()` しただけでは**踏み台だけが繋がった
 * まま残る**。呼び出し側は必ず [close] を通す。
 */
class SshLink internal constructor(
    /** 本来の接続先のセッション。⚠ **未接続**。呼び出し側が [connect] すること。 */
    val session: Session,
    /** 踏み台のセッション (手前から順)。**すでに接続済み**。 */
    private val jumps: List<Session>,
) : AutoCloseable {

    /** 経由した踏み台の数。0 なら直接繋いでいる。 */
    val jumpCount: Int get() = jumps.size

    /** 経路全体が生きているか。踏み台が 1 段でも切れていれば false。 */
    val isConnected: Boolean get() = session.isConnected && jumps.all { it.isConnected }

    fun connect(timeoutMs: Int = SshSessionFactory.CONNECT_TIMEOUT_MS) {
        session.connect(timeoutMs)
    }

    /**
     * 生存確認を経路の全段に入れる。**繋ぐ前に呼ぶこと** (JSch は接続の最後にこの値を
     * ソケットの読み取りタイムアウトへ写す)。
     *
     * ⚠ **効くのはソケットを持つ段だけ**。踏み台の中を通る段は生のソケットを持たないので
     * ([JumpProxy])、時間切れが起きず keepalive も鳴らない。経路が死んだことは**1 段目**が
     * 気付き、そこが切れれば奥も道連れに落ちるので、常駐の再接続はそれで回る。
     */
    fun enableKeepAlive(intervalMs: Int, countMax: Int) {
        (jumps + session).forEach { s ->
            runCatching {
                s.serverAliveInterval = intervalMs
                s.serverAliveCountMax = countMax
            }
        }
    }

    /** 奥から順に畳む。手前を先に切ると奥のセッションが宙に浮く。 */
    override fun close() {
        runCatching { session.disconnect() }
        jumps.asReversed().forEach { runCatching { it.disconnect() } }
    }
}

/**
 * JSch UserInfo の最小実装。
 *
 * - パスフレーズ要求は鍵のパスフレーズを返す
 * - パスワード要求はパスワードを返す (PASSWORD 認証時に呼ばれる場合もある)
 * - promptYesNo は known_hosts の確認 → [HostKeyVerifier] 経由で UI に問い合わせ
 *
 * ⚠ **1 段ごとに別のものを渡す。** 踏み台の鍵を確認するダイアログに本来の接続先の名前が
 * 出ると、どの相手の鍵を承認したのか分からなくなる。
 */
internal class VerifyingUserInfo(
    private val user: String,
    private val host: String,
    private val port: Int,
    private val credentials: SshCredentials,
) : UserInfo {

    private var passwordTried = false
    private var passphraseTried = false

    override fun getPassphrase(): String? = credentials.keyPassphrase.takeIf { it.isNotEmpty() }
    override fun getPassword(): String? = credentials.password.takeIf { it.isNotEmpty() }

    override fun promptPassword(message: String?): Boolean {
        return if (!passwordTried) {
            passwordTried = true
            credentials.password.isNotEmpty()
        } else false
    }

    override fun promptPassphrase(message: String?): Boolean {
        return if (!passphraseTried) {
            passphraseTried = true
            credentials.keyPassphrase.isNotEmpty()
        } else false
    }

    override fun promptYesNo(message: String?): Boolean {
        val msg = message ?: return false
        val fingerprint = extractFingerprint(msg)
        val keyType = extractKeyType(msg)
        return HostKeyVerifier.requestVerify(
            HostKeyVerifier.Prompt(
                host = "$user@${HostAddress.hostPort(host, port)}",
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
