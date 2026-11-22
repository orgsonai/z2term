package com.zerotoship.z2term.automation

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * `z2-ui` の許可の保存先。
 *
 * ⚠ **共有ホーム (`~/.z2term/`) に置かない。** AI はターミナルで root として動くので、
 * そこにあるファイルは AI が自分で書き換えて「全部許可」にできてしまう。アプリの
 * SharedPreferences はターミナルからは見えない場所にある。テキストを正本にする方針
 * (AUTOMATION-ROADMAP) の例外。
 * ⚠ ただしこれは「AI がうっかり広げない」ための置き場で、同じ uid で動くプロセスに対する
 * 防壁ではない。AI 側の権限設定 (使ってよいコマンドを `z2-ui` に絞る等) と組み合わせて使う。
 *
 * 広げる変更の承認は [UiBridge] が持つ。ここは読み書きだけ。
 */
internal class UiBridgeSettings(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 「AI からのアプリ操作」。既定は OFF。 */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            val editor = prefs.edit().putBoolean(KEY_ENABLED, value)
            // ON にし直したら「停止」の印も消す (それが再開の唯一の手段)。
            if (value) editor.putBoolean(KEY_STOPPED, false)
            editor.apply()
        }

    /** 通知の「停止」で止めた印。人が ON にし直すまで `stopped` を返す。 */
    var stopped: Boolean
        get() = prefs.getBoolean(KEY_STOPPED, false)
        set(value) { prefs.edit().putBoolean(KEY_STOPPED, value).apply() }

    /**
     * 「動きが止まったか」を区画ごとにも見るか (1.0.42 の厳しめの判定)。既定 ON。一部がずっと動いている
     * 画面で待たされるときに OFF にする (画面全体の平均だけで見る)。呼び出しの `--settle-check` が優先。
     */
    var strictSettle: Boolean
        get() = prefs.getBoolean(KEY_STRICT_SETTLE, true)
        set(value) { prefs.edit().putBoolean(KEY_STRICT_SETTLE, value).apply() }

    /** AI 操作の指の軌跡を画面に描くか (1.0.46)。人が見て追うためのもの。既定 OFF。撮る画像には写さない。 */
    var showTrail: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TRAIL, false)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_TRAIL, value).apply() }

    /**
     * AI の操作中、操作先のアプリの上に ‖ と ■ の帯を出すか (1.0.102、issue #2)。既定 ON。
     * AI が続けて操作していると、通知欄の「停止」まで指が届かないため。
     */
    var showBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BAR, true)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_BAR, value).apply() }

    /** AI が最後に MCP で繋いだ時刻 (epoch ms、0 = まだ)。登録できたかを画面で確かめるためだけのもの (1.0.176)。 */
    var mcpLastConnect: Long
        get() = prefs.getLong(KEY_MCP_LAST_CONNECT, 0L)
        set(value) { prefs.edit().putLong(KEY_MCP_LAST_CONNECT, value).apply() }

    /**
     * AI にシリアルのタブを使わせるか (1.0.208)。既定は OFF。アプリ操作 ([enabled]) とは別に決める。
     * ルーターの設定を AI が書き換える事故を避けるため、「読むだけ」の段階を置く。
     */
    var serialAccess: SerialAccess
        get() = SerialAccess.parse(prefs.getString(KEY_SERIAL, null))
        set(value) { prefs.edit().putString(KEY_SERIAL, value.key).apply() }

    var grantMinutes: Int
        get() = UiBridgePolicy.grantMinutes(prefs.getInt(KEY_GRANT, UiBridgePolicy.GRANT_DEFAULT_MINUTES))
        set(value) { prefs.edit().putInt(KEY_GRANT, UiBridgePolicy.grantMinutes(value)).apply() }

    /** パッケージ名 (と `*`) → 段階。 */
    fun levels(): Map<String, UiBridgePolicy.Level> {
        val json = runCatching { JSONObject(prefs.getString(KEY_LEVELS, null) ?: "{}") }.getOrDefault(JSONObject())
        return json.keys().asSequence().mapNotNull { key ->
            runCatching { key to UiBridgePolicy.Level.parse(json.getString(key)) }.getOrNull()
        }.toMap()
    }

    fun setLevel(pattern: String, level: UiBridgePolicy.Level?) {
        val json = JSONObject(levels().mapValues { it.value.key })
        if (level == null) json.remove(pattern) else json.put(pattern, level.key)
        prefs.edit().putString(KEY_LEVELS, json.toString()).apply()
    }

    companion object {
        private const val PREFS = "ui_bridge"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_STOPPED = "stopped"
        private const val KEY_GRANT = "grant_minutes"
        private const val KEY_LEVELS = "levels"
        private const val KEY_STRICT_SETTLE = "strict_settle"
        private const val KEY_SHOW_TRAIL = "show_trail"
        private const val KEY_SHOW_BAR = "show_bar"
        private const val KEY_MCP_LAST_CONNECT = "mcp_last_connect"
        private const val KEY_SERIAL = "serial_access"
    }
}

/** AI とシリアル (1.0.208)。OFF = 道具を見せない / READ = 読むだけ / SEND = 送るのも可。 */
internal enum class SerialAccess(val key: String) {
    OFF("off"), READ("read"), SEND("send");

    companion object {
        fun parse(key: String?): SerialAccess = entries.firstOrNull { it.key == key } ?: OFF
    }
}
