package com.zerotoship.z2term.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * 常駐サーバー 1 件の定義。ユーザーが設定で自由に追加でき、[com.zerotoship.z2term.service.ServerDaemonManager]
 * がエンジン (proot/z2root/chroot) 上で [command] を起動・常駐させる。
 *
 * サーバー本体 (sshd / smbd / httpd 等) は各自が distro に導入済みである前提で、アプリは
 * [command] を実行するだけ。特定サーバーをハードコードせず「起動コマンドを常駐させる」汎用機構。
 *
 * @param id       安定した一意 id (UI の並び替え・status ファイル名に使う)。
 * @param name     表示名 (例: http / smb / ssh)。status ファイル名にも使うので実体は sanitize する。
 * @param command  起動コマンド (例: `python3 -m http.server 8080`)。sh -c で実行する。
 * @param enabled  常駐対象に含めるか (OFF のエントリは起動しない)。
 */
data class ServerEntry(
    val id: String,
    val name: String,
    val command: String,
    val enabled: Boolean = true,
) {
    /** status ファイル名や supervisor 内トークンに使う安全な識別子。空になれば id を使う。 */
    fun safeToken(): String {
        val t = name.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
            .joinToString("")
            .trim('_')
        return t.ifBlank { id }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("command", command)
        .put("enabled", enabled)

    companion object {
        fun fromJson(o: JSONObject): ServerEntry = ServerEntry(
            id = o.optString("id").ifBlank { newId() },
            name = o.optString("name"),
            command = o.optString("command"),
            enabled = o.optBoolean("enabled", true),
        )

        /** DataStore に入れる JSON 文字列 (配列) へ直列化。 */
        fun encode(entries: List<ServerEntry>): String {
            val arr = JSONArray()
            entries.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        /** DataStore の JSON 文字列から復元。壊れていれば空リスト。 */
        fun decode(json: String?): List<ServerEntry> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }

        fun newId(): String = "srv-" + java.util.UUID.randomUUID().toString().take(8)

        /**
         * 追加時に選べる雛形。中身 (起動コマンド) は追加後に自由に編集できる。サーバー本体は
         * 各自が distro に導入する前提 (アプリは同梱しない)。1024 未満のポートは非 root エンジンで
         * bind できないため高ポート寄りの既定にしている。
         */
        data class Preset(val label: String, val name: String, val command: String)

        val PRESETS: List<Preset> = listOf(
            Preset("SSH (sshd)", "ssh", "sshd"),
            Preset("HTTP (python)", "http", "python3 -m http.server 8080"),
            Preset("SMB (samba)", "smb", "smbd -F --no-process-group"),
            Preset("FTP (vsftpd)", "ftp", "vsftpd"),
            Preset("VNC (Xvnc)", "vnc", "z2gui start 1280x720"),
            Preset("空 (自分で入力)", "server", ""),
        )
    }
}
