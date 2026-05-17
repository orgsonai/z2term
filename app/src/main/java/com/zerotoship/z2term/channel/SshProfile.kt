package com.zerotoship.z2term.channel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * SSH 接続プロファイル。
 *
 * - PASSWORD 認証: password フィールドを使う
 * - PUBLIC_KEY 認証: privateKey (PEM テキスト) + 任意の keyPassphrase
 *
 * 永続化時、password / privateKey / keyPassphrase は [KeystoreCrypt] で
 * AES-GCM 暗号化されてから DataStore に書かれる。
 */
data class SshProfile(
    val id: String,
    val name: String,
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
    val initCommand: String = ""
) {

    enum class AuthType { PASSWORD, PUBLIC_KEY }

    /** 永続化用 JSON: 機密フィールドは Keystore 暗号化される */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("port", port)
        put("user", user)
        put("authType", authType.name)
        put("password", KeystoreCrypt.encrypt(password))
        put("privateKey", KeystoreCrypt.encrypt(privateKey))
        put("keyPassphrase", KeystoreCrypt.encrypt(keyPassphrase))
        put("initCommand", initCommand)
    }

    companion object {
        fun fromJson(o: JSONObject): SshProfile = SshProfile(
            id = o.optString("id"),
            name = o.optString("name"),
            host = o.optString("host"),
            port = o.optInt("port", 22),
            user = o.optString("user"),
            authType = runCatching {
                AuthType.valueOf(o.optString("authType", AuthType.PASSWORD.name))
            }.getOrDefault(AuthType.PASSWORD),
            password = runCatching { KeystoreCrypt.decrypt(o.optString("password")) }.getOrDefault(""),
            privateKey = runCatching { KeystoreCrypt.decrypt(o.optString("privateKey")) }.getOrDefault(""),
            keyPassphrase = runCatching { KeystoreCrypt.decrypt(o.optString("keyPassphrase")) }.getOrDefault(""),
            initCommand = o.optString("initCommand")
        )
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
