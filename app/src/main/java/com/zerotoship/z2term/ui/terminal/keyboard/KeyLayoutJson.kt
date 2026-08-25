package com.zerotoship.z2term.ui.terminal.keyboard

import org.json.JSONArray
import org.json.JSONObject

/**
 * [KeyLayoutCodec] の木と JSON 文字列の橋（0.8.408・段階 2）。
 *
 * ⚠ **`org.json` に触るのはこのファイルだけ**。理由は [KeyLayoutCodec] の頭に書いた
 * （JVM のユニットテストでは `org.json` が動かないので、往復を固定できる層と分けてある）。
 * ここは「木 ⇄ JSONObject」の機械的な変換しかしないので、目で追える短さに保つ。
 *
 * ⚠ `JSONObject(Map)` / `JSONArray(Collection)` を**使わない**。入れ子の `Map` を
 * `JSONObject` へ包み直さない実装があり、`toString()` したときに Kotlin の `Map` の
 * `toString()`（`{a=1}`）がそのまま JSON へ混ざる。自前で再帰する。
 */
object KeyLayoutJson {

    // ---- 木 → JSON --------------------------------------------------------------------

    fun toJsonString(layout: KeyLayout): String =
        toJsonObject(KeyLayoutCodec.encode(layout)).toString()

    fun toJsonString(layouts: List<KeyLayout>): String =
        JSONArray().apply {
            KeyLayoutCodec.encodeAll(layouts).forEach { put(toJsonObject(it)) }
        }.toString()

    private fun toJsonObject(node: Map<*, *>): JSONObject {
        val o = JSONObject()
        node.forEach { (k, v) ->
            val key = k as? String ?: return@forEach
            o.put(key, wrap(v))
        }
        return o
    }

    private fun wrap(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> toJsonObject(value)
        is List<*> -> JSONArray().apply { value.forEach { put(wrap(it)) } }
        else -> value
    }

    // ---- JSON → 木 --------------------------------------------------------------------

    /** 1 枚読む。⚠ 読めなければ null（例外は投げない — 貼り付けた文字列が JSON とも限らない）。 */
    fun fromJsonString(json: String): KeyLayout? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return KeyLayoutCodec.decode(unwrap(o) as? Map<*, *> ?: return null)
    }

    /** 束を読む。⚠ **読めない 1 件のために他を落とさない**（空文字 / 壊れていれば空リスト）。 */
    fun listFromJsonString(json: String): List<KeyLayout> {
        if (json.isBlank()) return emptyList()
        val a = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val nodes = unwrap(a) as? List<*> ?: return emptyList()
        return KeyLayoutCodec.decodeAll(nodes)
    }

    private fun unwrap(value: Any?): Any? = when {
        value == null || value === JSONObject.NULL -> null
        value is JSONObject -> {
            val m = LinkedHashMap<String, Any?>()
            value.keys().forEach { k -> m[k] = unwrap(value.opt(k)) }
            m
        }
        value is JSONArray -> (0 until value.length()).map { unwrap(value.opt(it)) }
        else -> value
    }
}

/**
 * いま使うレイアウトを選ぶ（0.8.408・段階 2）。
 *
 * @param layoutsJson 保存してある束（`AppSettings.Snapshot.keyboardLayoutsJson`）。
 * @param activeId 選んである id（空 = 既定のプリセットを使う）。
 * @return 選ばれたレイアウト。**空文字・見つからない・壊れている → null**。
 *
 * ⚠ null を返したら、呼出し側は**黙って既定のプリセットへ戻す**。ここで例外にしたり空の
 * レイアウトを返したりすると、**キーボードが 1 枚も出ない端末**ができてしまう。設定を
 * 持ち出して別の端末で戻したときに、その端末に無い id が選ばれている状況は普通に起きる。
 */
fun activeKeyLayout(layoutsJson: String, activeId: String): KeyLayout? {
    if (activeId.isBlank()) return null
    return KeyLayoutJson.listFromJsonString(layoutsJson).firstOrNull { it.id == activeId }
}
