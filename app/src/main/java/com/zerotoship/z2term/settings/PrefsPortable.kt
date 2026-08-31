package com.zerotoship.z2term.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * DataStore (Preferences) の中身をまるごと JSON にする / 戻す (0.8.239・持ち出し用)。
 *
 * **フィールドを 1 つずつ書き写さない**のが要点。設定は 60 以上あり、書き写す方式にすると
 * **項目を足すたびに持ち出しから漏れる**（そして漏れたことは機種変のときにしか分からない）。
 * キーと値をそのまま運べば、将来フィールドが増えても自動で付いてくる。
 *
 * 型は 1 文字のタグで保つ (`b` Boolean / `i` Int / `l` Long / `f` Float / `s` String / `S` Set)。
 * DataStore の Preferences が取り得る型はこれで全部。
 */
object PrefsPortable {

    /**
     * [prefs] を `{"key":{"t":"s","v":…}}` の JSON にする。
     *
     * [exclude] に挙げたキー名は載せない。⚠ **秘密を持つ設定はここで落とす** —
     * 「キーと値をそのまま運ぶ」方式は項目が増えても漏れない代わりに、**秘密も自動で乗る**
     * ([AppSettings.EXPORT_EXCLUDE])。
     */
    fun toJson(prefs: Preferences, exclude: Set<String> = emptySet()): String {
        val o = JSONObject()
        prefs.asMap().forEach { (key, value) ->
            if (key.name in exclude) return@forEach
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
            o.put(key.name, e)
        }
        return o.toString()
    }

    /**
     * [json] を [prefs] へ書き戻す。**既存の値は消さない** — 復元は「上書き」ではなく
     * 「追加・更新」で、バックアップに無い設定はそのまま残る（新しい版で増えた設定を、
     * 古いバックアップを戻したときに巻き戻さないため）。
     */
    fun applyTo(prefs: MutablePreferences, json: String, exclude: Set<String> = emptySet()) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        o.keys().forEach { name ->
            if (name in exclude) return@forEach
            val e = o.optJSONObject(name) ?: return@forEach
            when (e.optString("t")) {
                "b" -> prefs[booleanPreferencesKey(name)] = e.optBoolean("v")
                "i" -> prefs[intPreferencesKey(name)] = e.optInt("v")
                "l" -> prefs[longPreferencesKey(name)] = e.optLong("v")
                "f" -> prefs[floatPreferencesKey(name)] = e.optDouble("v").toFloat()
                "s" -> prefs[stringPreferencesKey(name)] = e.optString("v")
                "S" -> {
                    val arr = e.optJSONArray("v") ?: return@forEach
                    prefs[stringSetPreferencesKey(name)] =
                        (0 until arr.length()).map { arr.optString(it) }.toSet()
                }
            }
        }
    }
}
