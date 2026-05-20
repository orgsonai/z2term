package com.zerotoship.z2term.snippets

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
 * よく使うコマンド (スニペット) 1 件。
 *
 * - `label` が空なら command 先頭をリストに表示。
 * - 実行時は常に「挿入のみ」(末尾 Enter は付けない)。ユーザーが Enter キーで確定する。
 *   仮に shell の補完や中間引数追記をしたいケースに対応するため。
 */
data class Snippet(
    val id: String,
    val label: String,
    val command: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("command", command)
    }

    companion object {
        fun fromJson(o: JSONObject): Snippet = Snippet(
            id = o.optString("id"),
            label = o.optString("label"),
            command = o.optString("command")
        )
    }
}

private val Context.snippetDataStore: DataStore<Preferences> by preferencesDataStore(name = "z2term_snippets")

/**
 * スニペット永続化。SshProfileStore と同じパターン (JSONArray を 1 つの String キーに保存)。
 *
 * 初回起動時のシード値は持たず空リスト。ユーザーが「+ 新規」で 0 件から積み上げる。
 */
class SnippetStore(private val context: Context) {

    val snippets: Flow<List<Snippet>> = context.snippetDataStore.data.map { p ->
        readList(p[KEY])
    }

    suspend fun upsert(snippet: Snippet) {
        context.snippetDataStore.edit { p ->
            val list = readList(p[KEY]).toMutableList()
            val idx = list.indexOfFirst { it.id == snippet.id }
            if (idx >= 0) list[idx] = snippet else list.add(snippet)
            p[KEY] = serialize(list)
        }
    }

    suspend fun delete(id: String) {
        context.snippetDataStore.edit { p ->
            val list = readList(p[KEY]).filterNot { it.id == id }
            p[KEY] = serialize(list)
        }
    }

    /**
     * [from] のスニペットを [to] の位置へ移動 (並び替え用)。
     * インデックスが範囲外なら無視。
     */
    suspend fun reorder(from: Int, to: Int) {
        context.snippetDataStore.edit { p ->
            val list = readList(p[KEY]).toMutableList()
            if (from !in list.indices || to !in list.indices || from == to) return@edit
            val item = list.removeAt(from)
            list.add(to, item)
            p[KEY] = serialize(list)
        }
    }

    private fun readList(raw: String?): List<Snippet> {
        if (raw == null) return emptyList()
        val arr = try { JSONArray(raw) } catch (e: Exception) { return emptyList() }
        return List(arr.length()) { Snippet.fromJson(arr.getJSONObject(it)) }
    }

    private fun serialize(list: List<Snippet>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    companion object {
        private val KEY = stringPreferencesKey("snippets")
    }
}
