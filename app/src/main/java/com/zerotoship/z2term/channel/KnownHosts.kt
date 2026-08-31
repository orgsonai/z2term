package com.zerotoship.z2term.channel

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * known_hosts 永続ストア (JSON Array、DataStore Preferences)。
 *
 * 各エントリ: { host, type, key (base64) }
 */
private val Context.knownHostsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "z2term_known_hosts")

class KnownHostsStore(private val context: Context) {

    data class Entry(val host: String, val type: String, val keyBase64: String)

    val entries: Flow<List<Entry>> = context.knownHostsDataStore.data.map { p ->
        decode(p[KEY])
    }

    suspend fun replaceAll(list: List<Entry>) {
        context.knownHostsDataStore.edit { p -> p[KEY] = encode(list) }
    }

    suspend fun add(entry: Entry) {
        context.knownHostsDataStore.edit { p ->
            val list = decode(p[KEY]).toMutableList()
            list.add(entry)
            p[KEY] = encode(list)
        }
    }

    suspend fun remove(host: String, keyBase64: String? = null) {
        context.knownHostsDataStore.edit { p ->
            val list = decode(p[KEY]).filterNot {
                it.host == host && (keyBase64 == null || it.keyBase64 == keyBase64)
            }
            p[KEY] = encode(list)
        }
    }

    private fun decode(raw: String?): List<Entry> {
        if (raw.isNullOrEmpty()) return emptyList()
        val arr = try { JSONArray(raw) } catch (e: Exception) { return emptyList() }
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Entry(
                host = o.optString("host"),
                type = o.optString("type"),
                keyBase64 = o.optString("key")
            )
        }
    }

    private fun encode(list: List<Entry>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("host", it.host)
                put("type", it.type)
                put("key", it.keyBase64)
            })
        }
        return arr.toString()
    }

    companion object {
        private val KEY = stringPreferencesKey("entries")
    }
}

/**
 * JSch [HostKeyRepository] の DataStore 実装。
 *
 * - 起動時に store の内容をインメモリ ConcurrentHashMap にロード
 * - check() は同期、add() は非同期で DataStore に反映
 */
class DataStoreHostKeyRepository(
    private val store: KnownHostsStore,
    scope: CoroutineScope
) : HostKeyRepository {

    /** host (lowercase) -> entries の一覧 */
    private val map = ConcurrentHashMap<String, MutableList<KnownHostsStore.Entry>>()

    init {
        scope.launch {
            store.entries.collect { list ->
                map.clear()
                list.forEach { e ->
                    map.getOrPut(e.host.lowercase()) { mutableListOf() }.add(e)
                }
            }
        }
    }

    private val scopeRef = scope

    override fun check(host: String?, keyBytes: ByteArray?): Int {
        if (host == null || keyBytes == null) return HostKeyRepository.NOT_INCLUDED
        val candidates = map[host.lowercase()] ?: return HostKeyRepository.NOT_INCLUDED
        val keyB64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        return if (candidates.any { it.keyBase64 == keyB64 })
            HostKeyRepository.OK
        else
            HostKeyRepository.CHANGED
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) {
        val keyB64 = Base64.encodeToString(hostkey.key.toByteArray(Charsets.ISO_8859_1), Base64.NO_WRAP)
        val entry = KnownHostsStore.Entry(
            host = hostkey.host.lowercase(),
            type = hostkey.type,
            keyBase64 = keyB64
        )
        map.getOrPut(entry.host) { mutableListOf() }.add(entry)
        scopeRef.launch { store.add(entry) }
    }

    override fun remove(host: String, type: String?) {
        remove(host, type, null)
    }

    override fun remove(host: String, type: String?, keyBytes: ByteArray?) {
        val keyB64 = keyBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        val hostKey = host.lowercase()
        map[hostKey]?.removeAll { e ->
            (type == null || e.type == type) && (keyB64 == null || e.keyBase64 == keyB64)
        }
        scopeRef.launch { store.remove(host, keyB64) }
    }

    override fun getKnownHostsRepositoryID(): String = "z2term_known_hosts"

    override fun getHostKey(): Array<HostKey> = map.values.flatten().map { e ->
        HostKey(e.host, parseType(e.type), Base64.decode(e.keyBase64, Base64.NO_WRAP))
    }.toTypedArray()

    override fun getHostKey(host: String?, type: String?): Array<HostKey> {
        val list = map[host?.lowercase()] ?: return emptyArray()
        return list.filter { type == null || it.type == type }.map { e ->
            HostKey(e.host, parseType(e.type), Base64.decode(e.keyBase64, Base64.NO_WRAP))
        }.toTypedArray()
    }

    private fun parseType(t: String): Int = when (t.lowercase()) {
        "ssh-dss" -> HostKey.SSHDSS
        "ssh-rsa" -> HostKey.SSHRSA
        "ecdsa-sha2-nistp256" -> HostKey.ECDSA256
        "ecdsa-sha2-nistp384" -> HostKey.ECDSA384
        "ecdsa-sha2-nistp521" -> HostKey.ECDSA521
        "ssh-ed25519" -> HostKey.ED25519
        else -> HostKey.GUESS
    }
}

/**
 * 不明ホスト鍵の検証を UI に問い合わせるためのシングルトン。
 *
 * JSch 側 (ワーカースレッド) は [requestVerify] を呼んで結果を blocking で待つ。
 * UI 側は [prompt] StateFlow を監視してダイアログを表示、結果を [resolve] で返す。
 */
object HostKeyVerifier {
    data class Prompt(
        val host: String,
        val keyType: String,
        val fingerprint: String,
        val message: String,
        /** 見出し。空なら SSH ホスト鍵の見出しを使う (RDP 証明書などが差し替える)。 */
        val title: String = ""
    )

    @Volatile
    private var _prompt: Prompt? = null
    val current: Prompt? get() = _prompt

    private val lock = Object()
    private var pending: java.util.concurrent.CompletableFuture<Boolean>? = null

    /** Compose 側で観察用 */
    val flow: kotlinx.coroutines.flow.MutableStateFlow<Prompt?> =
        kotlinx.coroutines.flow.MutableStateFlow(null)

    /** JSch UserInfo スレッドから呼ばれる。UI 応答まで blocking */
    fun requestVerify(prompt: Prompt): Boolean {
        val fut = java.util.concurrent.CompletableFuture<Boolean>()
        synchronized(lock) {
            pending = fut
            _prompt = prompt
            flow.value = prompt
        }
        return try {
            fut.get()
        } catch (e: Exception) {
            false
        } finally {
            synchronized(lock) {
                pending = null
                _prompt = null
                flow.value = null
            }
        }
    }

    fun resolve(accept: Boolean) {
        synchronized(lock) {
            pending?.complete(accept)
        }
    }
}
