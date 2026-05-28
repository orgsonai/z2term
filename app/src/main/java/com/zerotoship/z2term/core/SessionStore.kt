package com.zerotoship.z2term.core

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * 端末タブ構成の永続化 (セッション復元 v1)。
 *
 * アプリが OS に kill された後の再起動で「開いていた端末タブ」を復元するために、
 * タブ構成 `{id, label, distro, cwd}` のリスト + activeId を DataStore に保存する。
 *
 * **割り切り (v1)**:
 *  - 復元するのは **タブ構成のみ**。シェルのプロセス状態・scrollback は復元しない
 *    (プロセスは死んでいる)。各タブは通常どおり新規 PTY を起動し、`cwd` があれば
 *    起動直後に `cd <cwd>` を流して作業ディレクトリだけベストエフォートで戻す。
 *  - GUI タブ (Xvnc) は復元対象外 (VNC プロセスが消えているため)。端末タブのみ保存。
 *  - 既存の [com.zerotoship.z2term.service.TerminalService] (フォアグラウンド常駐) は
 *    アプリが **生きている間** セッションを保つ仕組み。本機能はプロセスが **完全に死んだ後**
 *    の復帰なので役割が違い、両立する。
 *
 * 保存形式は JSON 配列文字列 (DataStore の string key 1 本):
 *   `[{"id":..,"label":..,"distro":..,"cwd":..}, ...]` + 別 key に activeId。
 */
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "z2term_sessions")

object SessionStore {

    private const val TAG = "SessionStore"

    /** 復元用の 1 タブ分。distro は不明 (android-sh 等) なら null。 */
    data class Entry(
        val id: String,
        val label: String,
        val distro: String?,
        val cwd: String
    )

    data class Saved(val entries: List<Entry>, val activeId: String?)

    private val KEY_SESSIONS = stringPreferencesKey("sessions")
    private val KEY_ACTIVE = stringPreferencesKey("active_id")

    suspend fun save(context: Context, entries: List<Entry>, activeId: String?) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("label", e.label)
                put("distro", e.distro ?: JSONObject.NULL)
                put("cwd", e.cwd)
            })
        }
        runCatching {
            context.sessionDataStore.edit { p ->
                p[KEY_SESSIONS] = arr.toString()
                if (activeId != null) p[KEY_ACTIVE] = activeId else p.remove(KEY_ACTIVE)
            }
        }.onFailure { Log.w(TAG, "session save failed", it) }
    }

    /**
     * 起動時に 1 度だけ同期読み込みする。DataStore の単発読み出しは短時間なので、
     * setContent 前 (= タブ構成が確定している必要がある) に runBlocking で取得する。
     * 失敗時は空 (= 従来どおり 1 タブ起動)。
     */
    fun loadBlocking(context: Context): Saved = runCatching {
        runBlocking {
            val p = context.sessionDataStore.data.first()
            parse(p[KEY_SESSIONS], p[KEY_ACTIVE])
        }
    }.getOrElse {
        Log.w(TAG, "session load failed", it)
        Saved(emptyList(), null)
    }

    private fun parse(json: String?, activeId: String?): Saved {
        if (json.isNullOrBlank()) return Saved(emptyList(), activeId)
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return Saved(emptyList(), activeId)
        val list = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isBlank()) continue
            val distro = if (o.isNull("distro")) null else o.optString("distro", "").ifBlank { null }
            list.add(
                Entry(
                    id = id,
                    label = o.optString("label", "session"),
                    distro = distro,
                    cwd = o.optString("cwd", "")
                )
            )
        }
        return Saved(list, activeId)
    }
}
