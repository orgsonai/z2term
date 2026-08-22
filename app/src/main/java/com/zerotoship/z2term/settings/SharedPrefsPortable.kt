package com.zerotoship.z2term.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences の中身をまるごと JSON にする / 戻す (0.8.379・持ち出し用)。
 *
 * [PrefsPortable] の SharedPreferences 版。⚠ **DataStore 用の 1 本だけでは足りない** —
 * タイルの割り当てとアイコンのドット絵は、**アプリのプロセスが生きていない状態**で読まれる
 * (タイルもウィジェットもそう) ため SharedPreferences 側にあり、そちらを持ち出す道が無いと
 * 「マクロは戻ったのに、どの枠に何を置いたかは消えている」ことになる。
 *
 * **キーを 1 つずつ書き写さない**のは [PrefsPortable] と同じ理由。書き写す方式は、項目を
 * 足すたびに持ち出しから漏れ、**漏れたことは機種変のときにしか分からない**。
 *
 * 型は 1 文字のタグで保つ (`b` Boolean / `i` Int / `l` Long / `f` Float / `s` String / `S` Set)。
 * SharedPreferences が取り得る型はこれで全部。
 */
object SharedPrefsPortable {

    /** [prefs] を `{"key":{"t":"s","v":…}}` の JSON にする。 */
    fun toJson(prefs: SharedPreferences): String {
        val o = JSONObject()
        prefs.all.forEach { (name, value) ->
            val e = JSONObject()
            when (value) {
                is Boolean -> { e.put("t", "b"); e.put("v", value) }
                is Int -> { e.put("t", "i"); e.put("v", value) }
                is Long -> { e.put("t", "l"); e.put("v", value) }
                is Float -> { e.put("t", "f"); e.put("v", value.toDouble()) }
                is String -> { e.put("t", "s"); e.put("v", value) }
                is Set<*> -> {
                    e.put("t", "S")
                    e.put("v", JSONArray().apply { value.forEach { put(it.toString()) } })
                }
                // 未知の型は落とす (無理に運ぶより、無いことが分かる方がまし)。
                else -> return@forEach
            }
            o.put(name, e)
        }
        return o.toString()
    }

    /**
     * [json] を [prefs] へ書き戻す。**既存の値は消さない** — 復元は「上書き」ではなく
     * 「追加・更新」で、バックアップに無いものはそのまま残る (古いバックアップを戻したときに、
     * 新しく作ったタイルや絵を巻き戻さないため)。
     */
    fun applyTo(prefs: SharedPreferences, json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        prefs.edit {
            o.keys().forEach { name ->
                val e = o.optJSONObject(name) ?: return@forEach
                when (e.optString("t")) {
                    "b" -> putBoolean(name, e.optBoolean("v"))
                    "i" -> putInt(name, e.optInt("v"))
                    "l" -> putLong(name, e.optLong("v"))
                    "f" -> putFloat(name, e.optDouble("v").toFloat())
                    "s" -> putString(name, e.optString("v"))
                    "S" -> {
                        val arr = e.optJSONArray("v") ?: return@forEach
                        putStringSet(name, (0 until arr.length()).map { arr.optString(it) }.toSet())
                    }
                }
            }
        }
    }
}
