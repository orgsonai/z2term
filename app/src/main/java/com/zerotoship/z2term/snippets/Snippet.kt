package com.zerotoship.z2term.snippets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
 * 初回のみサンプル 1 件 (`ls -la --color=auto`) をシードする ([ensureSeeded])。
 * 以降はユーザーが「+ 新規」で積み上げ。ユーザーが全削除してもシードは復活しない。
 */
class SnippetStore(private val context: Context) {

    val snippets: Flow<List<Snippet>> = context.snippetDataStore.data.map { p ->
        readList(p[KEY])
    }

    /**
     * 初回だけサンプルスニペットを投入する (冪等)。`SEEDED` フラグで二度目以降は何もしない
     * → ユーザーが消したサンプルが再出現しない。既存スニペットがあれば上書きもしない。
     *
     * 既に `SEEDED=true` で過去にシード済みのユーザーには、別フラグ [SEEDED_APK] で
     * Alpine の apk サンプルだけ後追い投入する (既存スニペットに「追記」のみ、上書きしない)。
     */
    suspend fun ensureSeeded() {
        context.snippetDataStore.edit { p ->
            val firstTime = p[SEEDED] != true
            if (firstTime) {
                p[SEEDED] = true
                if (p[KEY] == null) {
                    p[KEY] = serialize(defaultSeeds())
                }
            } else if (p[SEEDED_APK] != true) {
                // 過去にシード済みのユーザーへ apk サンプルを追記投入 (1 回だけ)。
                val list = readList(p[KEY]).toMutableList()
                for (s in apkSeeds()) {
                    if (list.none { it.id == s.id }) list.add(s)
                }
                p[KEY] = serialize(list)
            }
            p[SEEDED_APK] = true
        }
    }

    /** 初回シードに投入するサンプル一覧 (ラベルは投入時点のアプリ言語に追従)。 */
    private fun defaultSeeds(): List<Snippet> = buildList {
        add(Snippet(
            id = "sample:ls",
            label = context.getString(com.zerotoship.z2term.R.string.snippet_sample_ls_label),
            command = "ls -la --color=auto"
        ))
        addAll(apkSeeds())
    }

    /** Alpine 用 apk サンプル。pacman/apt は対象外 (Alpine 専用)。 */
    private fun apkSeeds(): List<Snippet> = listOf(
        Snippet(
            id = "sample:apk-update",
            label = context.getString(com.zerotoship.z2term.R.string.snippet_sample_apk_update_label),
            command = "apk update",
        ),
        Snippet(
            id = "sample:apk-add",
            // command 末尾を半角スペースで止めてユーザーがパッケージ名を追記できる形に。
            label = context.getString(com.zerotoship.z2term.R.string.snippet_sample_apk_add_label),
            command = "apk add ",
        ),
    )

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

    /** リスト全体を指定順で保存する (ドラッグ並べ替えの確定用)。 */
    suspend fun replaceAll(list: List<Snippet>) {
        context.snippetDataStore.edit { p -> p[KEY] = serialize(list) }
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
        private val SEEDED = booleanPreferencesKey("seeded")
        private val SEEDED_APK = booleanPreferencesKey("seeded_apk")
    }
}
