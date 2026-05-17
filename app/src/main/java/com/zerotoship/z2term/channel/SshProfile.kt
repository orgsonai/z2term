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
 * セキュリティ注意 (M5):
 * - パスワードは DataStore に平文保存 (M6 で Android Keystore + 暗号化)
 * - 公開鍵認証は未対応
 */
data class SshProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val password: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("port", port)
        put("user", user)
        put("password", password)
    }

    companion object {
        fun fromJson(o: JSONObject): SshProfile = SshProfile(
            id = o.optString("id"),
            name = o.optString("name"),
            host = o.optString("host"),
            port = o.optInt("port", 22),
            user = o.optString("user"),
            password = o.optString("password")
        )
    }
}

private val Context.sshDataStore: DataStore<Preferences> by preferencesDataStore(name = "z2term_ssh")

/**
 * SSH プロファイルの永続ストア。
 * 配列を JSON 文字列にエンコードして 1 つの Preferences key に格納する簡易方式。
 */
class SshProfileStore(private val context: Context) {

    val profiles: Flow<List<SshProfile>> = context.sshDataStore.data.map { p ->
        val raw = p[KEY] ?: return@map emptyList()
        val arr = try { JSONArray(raw) } catch (e: Exception) { return@map emptyList() }
        List(arr.length()) { SshProfile.fromJson(arr.getJSONObject(it)) }
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
