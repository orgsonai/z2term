package com.zerotoship.z2term.core

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * 端末タブ構成の永続化 (書き込みのみ)。
 *
 * タブ構成 `{id, label, distro, cwd}` のリスト + activeId を DataStore に保存する。
 * **起動時の自動復元は現在無効** (ユーザー要望で常に新規 1 タブ起動)。save は将来の復元 UI や
 * デバッグのために残しているが、読み戻す経路は持たない。
 *
 * **割り切り (v1)**:
 *  - 復元するのは **タブ構成のみ**。シェルのプロセス状態・scrollback・作業ディレクトリは
 *    復元しない (プロセスは死んでいる)。各タブは通常どおり新規 PTY を起動し、シェルの
 *    既定ディレクトリ (HOME 等) で開始する。`cwd` はタブ表示用に保存しているだけ。
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
}
