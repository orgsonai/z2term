package com.zerotoship.z2term.channel

import android.content.Context
import com.zerotoship.z2term.gui.rfb.VncTarget
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * ポート転送 1 件分の設定。[reverse] で向きが変わる。
 *
 * **`-L` (reverse=false・既定)**: 遠くのサービスを**こちらへ引き込む**。
 * 端末の `bindAddress:localPort` で待ち受け、リモートから見た `remoteHost:remotePort` へ繋ぐ。
 * bindAddress を 127.0.0.1 にすると端末上の他アプリだけ、0.0.0.0 にすると同 Wi-Fi の他機からも届く (注意)。
 * 例: `localPort=8080, remoteHost=localhost, remotePort=80` で、リモートの HTTP を端末の
 * `127.0.0.1:8080` として見る。
 *
 * **`-R` (reverse=true)**: 逆向き。**こちら側を遠くから触れるようにする** (A2)。
 * リモートの `bindAddress:remotePort` で待ち受け、端末から見た `remoteHost:localPort` へ繋ぐ。
 * 携帯回線のスマホは外から直接繋げないので、スマホ側から自宅サーバーへ張った接続を逆走させる。
 * 例: `reverse=true, remotePort=2222, remoteHost=127.0.0.1, localPort=2222` で、
 * 自宅サーバーの `127.0.0.1:2222` からスマホの sshd に入れる。
 * ⚠ **`-R` は常駐 ([SshProfile.residentTunnel]) と組み合わせて初めて意味を持つ**
 * (入りたい時にスマホ側でタブを開いている必要があるなら、外から入る意味が無い)。
 */
data class PortForward(
    /** 待ち受けアドレス。`-L` は端末側、`-R` はリモート側 (既定: 127.0.0.1) */
    val bindAddress: String = "127.0.0.1",
    /** `-L`: 端末側の待ち受けポート / `-R`: 端末から見た接続先ポート */
    val localPort: Int,
    /** `-L`: リモートから見た接続先ホスト / `-R`: 端末から見た接続先ホスト */
    val remoteHost: String,
    /** `-L`: リモートから見た接続先ポート / `-R`: リモート側の待ち受けポート */
    val remotePort: Int,
    /** true なら `-R` (リモート → 端末)。既定は `-L`。 */
    val reverse: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("bindAddress", bindAddress)
        put("localPort", localPort)
        put("remoteHost", remoteHost)
        put("remotePort", remotePort)
        put("reverse", reverse)
    }

    /** 一覧に出す 1 行 (`-L 127.0.0.1:8080 → localhost:80` のような形)。 */
    fun describe(): String = if (reverse)
        "-R $bindAddress:$remotePort → $remoteHost:$localPort"
    else
        "-L $bindAddress:$localPort → $remoteHost:$remotePort"

    companion object {
        fun fromJson(o: JSONObject): PortForward = PortForward(
            bindAddress = o.optString("bindAddress", "127.0.0.1"),
            localPort = o.optInt("localPort"),
            remoteHost = o.optString("remoteHost"),
            remotePort = o.optInt("remotePort"),
            reverse = o.optBoolean("reverse", false)
        )
    }
}

/**
 * 接続先の主な入口。旧データにはこの項目が無いため、読み込み時の既定は [SSH]。
 * SSH の無い NAS / WebDAV サーバーも同じ接続先一覧へ登録できるようにする。
 */
enum class ConnectionProtocol {
    SSH,
    WEBDAV,
    SMB
}

/** SSH 接続先の外側に並べるリモートサービス。 */
enum class RemoteServiceProtocol(val defaultPort: Int) {
    FTP(21),
    SMB(445),
    WEBDAV(443),
    VNC(VncTarget.DEFAULT_PORT)
}

/**
 * 1 件の FTP / SMB / WebDAV / VNC 接続設定。
 *
 * [useSshTunnel] が true のとき、[host]:[remotePort] は SSH サーバーから見た接続先で、
 * Android 側の 127.0.0.1:[localPort] へ一時的に `-L` 転送する。[localPort] が 0 なら
 * OS に空きポートを選ばせる。false のときはサービス個別の [host] を使わず、親の
 * [SshProfile.host]:[remotePort] へ直接接続する。
 */
data class RemoteService(
    val id: String,
    val protocol: RemoteServiceProtocol,
    val name: String = "",
    val useSshTunnel: Boolean = true,
    val host: String = "localhost",
    val remotePort: Int = protocol.defaultPort,
    val localPort: Int = 0,
    val user: String = "",
    /** 平文。永続化時は [KeystoreCrypt] で暗号化する。 */
    val password: String = "",
    /** FTP の開始フォルダ / SMB 共有名 / WebDAV のベースパス。 */
    val path: String = "",
    /** SMB 認証ドメイン。 */
    val domain: String = "",
    /** WebDAV だけで使用。true=https / false=http。 */
    val webDavHttps: Boolean = true,
) {
    val label: String get() = name.ifBlank { protocol.name }

    /** 転送時はサービスホスト、直通時はこのサービスを登録した SSH ホストを使う。 */
    fun connectionHost(sshProfile: SshProfile): String =
        if (useSshTunnel) host else sshProfile.host

    fun endpointDescription(sshProfile: SshProfile? = null): String = buildString {
        append(if (sshProfile == null) host else connectionHost(sshProfile))
            .append(':').append(remotePort)
        if (path.isNotBlank()) append('/').append(path.trim('/'))
    }

    fun toJson(encryptSecrets: Boolean): JSONObject = JSONObject().apply {
        put("id", id)
        put("protocol", protocol.name)
        put("name", name)
        put("useSshTunnel", useSshTunnel)
        put("host", host)
        put("remotePort", remotePort)
        put("localPort", localPort)
        put("user", user)
        put("password", if (encryptSecrets) KeystoreCrypt.encrypt(password) else password)
        put("path", path)
        put("domain", domain)
        put("webDavHttps", webDavHttps)
    }

    companion object {
        fun fromJson(o: JSONObject, encryptedSecrets: Boolean): RemoteService {
            val protocol = runCatching {
                RemoteServiceProtocol.valueOf(o.optString("protocol"))
            }.getOrDefault(RemoteServiceProtocol.VNC)
            val rawPassword = o.optString("password")
            return RemoteService(
                id = o.optString("id"),
                protocol = protocol,
                name = o.optString("name"),
                useSshTunnel = o.optBoolean("useSshTunnel", true),
                host = o.optString("host", "localhost"),
                remotePort = o.optInt("remotePort", protocol.defaultPort),
                localPort = o.optInt("localPort", 0),
                user = o.optString("user"),
                password = if (encryptedSecrets) {
                    runCatching { KeystoreCrypt.decrypt(rawPassword) }.getOrDefault("")
                } else rawPassword,
                path = o.optString("path"),
                domain = o.optString("domain"),
                webDavHttps = o.optBoolean("webDavHttps", true),
            )
        }
    }
}

/**
 * SSH / WebDAV / SMB 共通接続先。クラス名は保存互換のため維持する。
 *
 * - PASSWORD 認証: password フィールドを使う
 * - PUBLIC_KEY 認証: privateKey (PEM テキスト) + 任意の keyPassphrase
 *
 * 永続化時、password / privateKey / keyPassphrase は [KeystoreCrypt] で
 * AES-GCM 暗号化されてから DataStore に書かれる。
 *
 * M7 で [forwards] を追加 (-L ローカルポート転送)。0.8.221 で [residentTunnel] と `-R` を追加 (A2)。
 */
data class SshProfile(
    val id: String,
    val name: String,
    val protocol: ConnectionProtocol = ConnectionProtocol.SSH,
    val host: String,
    val port: Int = 22,
    val user: String,
    val authType: AuthType = AuthType.PASSWORD,
    /** PASSWORD 認証時のパスワード (平文。永続化時に暗号化) */
    val password: String = "",
    /** PUBLIC_KEY 認証時の秘密鍵 PEM (平文。永続化時に暗号化) */
    val privateKey: String = "",
    /** 鍵にパスフレーズがある場合 (平文。永続化時に暗号化) */
    val keyPassphrase: String = "",
    /** 接続後に自動実行するコマンド (空なら何もしない) */
    val initCommand: String = "",
    /** ポート転送のリスト (空なら何もしない)。向きは [PortForward.reverse] で決まる */
    val forwards: List<PortForward> = emptyList(),
    /**
     * **常駐トンネル (A2)**: true なら、SSH タブを開いていなくてもこの接続を張り続け、
     * [forwards] を生かしたままにする ([com.zerotoship.z2term.service.TunnelManager])。
     *
     * 既定 false = 明示 opt-in。`-R` は常駐と組み合わせて初めて意味を持つので、
     * 実質ここが `-R` の opt-in も兼ねる。
     */
    val residentTunnel: Boolean = false,
    /**
     * **リモート VNC (A1)** の RFB ポート。画面 `:N` は `5900+N` なので `:1` なら 5901。
     * SSH で入るのと同じサーバのデスクトップを、同じ登録から開けるようにするためのもの。
     * ⚠ SSH の [port] とは無関係 (VNC は SSH を経由しない別の接続)。
     */
    val vncPort: Int = VncTarget.DEFAULT_PORT,
    /**
     * VNC 認証のパスワード (平文。永続化時に暗号化)。
     * ⚠ **SSH の [password] とは別物**。VNC 側は 8 文字までしか効かない (RFB の仕様)。
     */
    val vncPassword: String = "",
    /** WebDAV の URL 内パスとは別に使う SMB の共有名。 */
    val remotePath: String = "",
    /** SMB の認証ドメイン。SSH / WebDAV では空。 */
    val domain: String = "",
    /** SSH を踏み台にして開く FTP / SMB / WebDAV / VNC。 */
    val services: List<RemoteService> = emptyList(),
) {
    val hasSsh: Boolean get() = protocol == ConnectionProtocol.SSH

    val fileProtocolLabel: String get() = when (protocol) {
        ConnectionProtocol.SSH -> "SFTP"
        ConnectionProtocol.WEBDAV -> "WebDAV"
        ConnectionProtocol.SMB -> "SMB"
    }

    fun endpointDescription(): String = when (protocol) {
        ConnectionProtocol.SSH -> "$user@$host:$port"
        ConnectionProtocol.WEBDAV -> host
        ConnectionProtocol.SMB -> buildString {
            append("//").append(host).append(':').append(port)
            if (remotePath.isNotBlank()) append('/').append(remotePath.trim('/'))
        }
    }

    /** この接続先のデスクトップを開くための接続情報 (A1)。 */
    fun toVncTarget(): VncTarget = VncTarget(
        host = host,
        port = vncPort,
        password = vncPassword,
        name = name
    )

    enum class AuthType { PASSWORD, PUBLIC_KEY }

    /** 永続化用 JSON: 機密フィールドは Keystore 暗号化される */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("port", port)
        put("user", user)
        put("protocol", protocol.name)
        put("authType", authType.name)
        put("residentTunnel", residentTunnel)
        put("vncPort", vncPort)
        put("vncPassword", KeystoreCrypt.encrypt(vncPassword))
        put("password", KeystoreCrypt.encrypt(password))
        put("privateKey", KeystoreCrypt.encrypt(privateKey))
        put("keyPassphrase", KeystoreCrypt.encrypt(keyPassphrase))
        put("initCommand", initCommand)
        put("forwards", JSONArray().also { arr ->
            forwards.forEach { arr.put(it.toJson()) }
        })
        put("remotePath", remotePath)
        put("domain", domain)
        put("services", JSONArray().also { arr ->
            services.forEach { arr.put(it.toJson(encryptSecrets = true)) }
        })
    }

    /**
     * 持ち出し用の JSON (**Keystore 暗号化を通さない平文**)。
     *
     * Keystore の鍵は端末に紐づくので、暗号化済みの値をそのまま持ち出しても**移した先で復号
     * できない**。持ち出しでは平文に戻し、ファイル全体をパスフレーズで暗号化し直す
     * ([com.zerotoship.z2term.backup.BackupManager])。呼び元が秘密を含めない選択をした場合、
     * 秘密フィールドは空で渡ってくる。
     */
    fun toPlainJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("protocol", protocol.name)
        put("host", host)
        put("port", port)
        put("user", user)
        put("authType", authType.name)
        put("residentTunnel", residentTunnel)
        put("vncPort", vncPort)
        put("vncPassword", vncPassword)
        put("password", password)
        put("privateKey", privateKey)
        put("keyPassphrase", keyPassphrase)
        put("initCommand", initCommand)
        put("remotePath", remotePath)
        put("domain", domain)
        put("services", JSONArray().also { arr ->
            services.forEach { arr.put(it.toJson(encryptSecrets = false)) }
        })
        put("forwards", JSONArray().also { arr -> forwards.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        /** [toPlainJson] の逆 (持ち出しから戻すとき用。保存時に改めて Keystore 暗号化される)。 */
        fun fromPlainJson(o: JSONObject): SshProfile = SshProfile(
            id = o.optString("id"),
            name = o.optString("name"),
            protocol = runCatching {
                ConnectionProtocol.valueOf(o.optString("protocol", ConnectionProtocol.SSH.name))
            }.getOrDefault(ConnectionProtocol.SSH),
            host = o.optString("host"),
            port = o.optInt("port", 22),
            user = o.optString("user"),
            authType = runCatching {
                AuthType.valueOf(o.optString("authType", AuthType.PASSWORD.name))
            }.getOrDefault(AuthType.PASSWORD),
            vncPort = o.optInt("vncPort", VncTarget.DEFAULT_PORT),
            vncPassword = o.optString("vncPassword"),
            password = o.optString("password"),
            privateKey = o.optString("privateKey"),
            keyPassphrase = o.optString("keyPassphrase"),
            initCommand = o.optString("initCommand"),
            remotePath = o.optString("remotePath"),
            domain = o.optString("domain"),
            services = servicesFromJson(o, encryptedSecrets = false),
            forwards = runCatching {
                val arr = o.optJSONArray("forwards") ?: return@runCatching emptyList()
                List(arr.length()) { PortForward.fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList()),
            residentTunnel = o.optBoolean("residentTunnel", false)
        )

        fun fromJson(o: JSONObject): SshProfile = SshProfile(
            id = o.optString("id"),
            name = o.optString("name"),
            protocol = runCatching {
                ConnectionProtocol.valueOf(o.optString("protocol", ConnectionProtocol.SSH.name))
            }.getOrDefault(ConnectionProtocol.SSH),
            host = o.optString("host"),
            port = o.optInt("port", 22),
            user = o.optString("user"),
            authType = runCatching {
                AuthType.valueOf(o.optString("authType", AuthType.PASSWORD.name))
            }.getOrDefault(AuthType.PASSWORD),
            vncPort = o.optInt("vncPort", VncTarget.DEFAULT_PORT),
            vncPassword = runCatching { KeystoreCrypt.decrypt(o.optString("vncPassword")) }.getOrDefault(""),
            password = runCatching { KeystoreCrypt.decrypt(o.optString("password")) }.getOrDefault(""),
            privateKey = runCatching { KeystoreCrypt.decrypt(o.optString("privateKey")) }.getOrDefault(""),
            keyPassphrase = runCatching { KeystoreCrypt.decrypt(o.optString("keyPassphrase")) }.getOrDefault(""),
            initCommand = o.optString("initCommand"),
            remotePath = o.optString("remotePath"),
            domain = o.optString("domain"),
            services = servicesFromJson(o, encryptedSecrets = true),
            forwards = runCatching {
                val arr = o.optJSONArray("forwards") ?: return@runCatching emptyList()
                List(arr.length()) { PortForward.fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList()),
            residentTunnel = o.optBoolean("residentTunnel", false)
        )

        /** 0.8.438 以前の VNC 欄は、初回読込時だけ新しいサービス 1 件として扱う。 */
        private fun servicesFromJson(o: JSONObject, encryptedSecrets: Boolean): List<RemoteService> {
            if (o.has("services")) {
                val arr = o.optJSONArray("services") ?: return emptyList()
                return List(arr.length()) {
                    RemoteService.fromJson(arr.getJSONObject(it), encryptedSecrets)
                }
            }
            val protocol = runCatching {
                ConnectionProtocol.valueOf(o.optString("protocol", ConnectionProtocol.SSH.name))
            }.getOrDefault(ConnectionProtocol.SSH)
            if (protocol != ConnectionProtocol.SSH) return emptyList()
            val rawPassword = o.optString("vncPassword")
            val password = if (encryptedSecrets) {
                runCatching { KeystoreCrypt.decrypt(rawPassword) }.getOrDefault("")
            } else rawPassword
            return listOf(
                RemoteService(
                    id = "legacy-vnc-" + o.optString("id"),
                    protocol = RemoteServiceProtocol.VNC,
                    name = "VNC",
                    host = "localhost",
                    remotePort = o.optInt("vncPort", VncTarget.DEFAULT_PORT),
                    password = password,
                )
            )
        }
    }
}

private val Context.sshDataStore: DataStore<Preferences> by preferencesDataStore(name = "z2term_ssh")

class SshProfileStore(private val context: Context) {

    val profiles: Flow<List<SshProfile>> = context.sshDataStore.data.map { p ->
        readList(p[KEY])
    }

    suspend fun upsert(profile: SshProfile) {
        context.sshDataStore.edit { p ->
            val list = readList(p[KEY]).toMutableList()
            val idx = list.indexOfFirst { it.id == profile.id }
            if (idx >= 0) list[idx] = profile else list.add(profile)
            p[KEY] = serialize(list)
        }
    }

    suspend fun delete(id: String) {
        context.sshDataStore.edit { p ->
            val list = readList(p[KEY]).filterNot { it.id == id }
            p[KEY] = serialize(list)
        }
    }

    /**
     * 接続先をまるごと JSON にする (持ち出し用・0.8.239)。
     *
     * ⚠ **保存されている秘密は Keystore 暗号化されていて、端末の外では復号できない**
     * (Keystore の鍵は端末に紐づく)。そのため持ち出しでは [SshProfile.toJson] ではなく
     * **平文に戻した JSON** を作り、パスフレーズで暗号化し直すのが呼び元の責任
     * ([com.zerotoship.z2term.backup.BackupManager])。ここは平文/秘密なしの選択だけを引き受ける。
     */
    suspend fun exportRaw(includeSecrets: Boolean): String {
        val list = profiles.first()
        val arr = JSONArray()
        list.forEach { p ->
            val strip = if (includeSecrets) p else
                p.copy(
                    password = "",
                    privateKey = "",
                    keyPassphrase = "",
                    vncPassword = "",
                    services = p.services.map { it.copy(password = "") },
                )
            arr.put(strip.toPlainJson())
        }
        return arr.toString()
    }

    /** 持ち出した接続先を取り込む (同じ id は置き換え・無いものは追加)。 */
    suspend fun importRaw(json: String) {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return
        context.sshDataStore.edit { p ->
            val list = readList(p[KEY]).toMutableList()
            for (i in 0 until arr.length()) {
                val prof = runCatching { SshProfile.fromPlainJson(arr.getJSONObject(i)) }.getOrNull() ?: continue
                val idx = list.indexOfFirst { it.id == prof.id }
                if (idx >= 0) list[idx] = prof else list.add(prof)
            }
            p[KEY] = serialize(list)
        }
    }

    private fun readList(raw: String?): List<SshProfile> {
        if (raw == null) return emptyList()
        val arr = try { JSONArray(raw) } catch (e: Exception) { return emptyList() }
        return List(arr.length()) { SshProfile.fromJson(arr.getJSONObject(it)) }
    }

    private fun serialize(list: List<SshProfile>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    companion object {
        private val KEY = stringPreferencesKey("profiles")
    }
}
