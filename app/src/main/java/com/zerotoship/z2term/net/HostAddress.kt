package com.zerotoship.z2term.net

/**
 * ホスト入力を接続用と表示用に揃える小さな共通処理。
 *
 * 接続 API へ渡す IPv6 リテラルは `2001:db8::1`、`host:port` や HTTP の authority に
 * 出すときは `[2001:db8::1]:22` でなければならない。画面ではどちらの書き方を貼っても
 * 接続できるよう、外側の角括弧は保存・接続前に外す。
 */
object HostAddress {
    /** ホスト欄用。前後の空白と、IPv6 URI で使う外側の角括弧を取り除く。 */
    fun normalize(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length >= 2 && trimmed.first() == '[' && trimmed.last() == ']') {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    /** URI / 表示用ホスト。IPv6 リテラルだけを角括弧で囲む。 */
    fun authorityHost(host: String): String {
        val normalized = normalize(host)
        return if (':' in normalized) "[$normalized]" else normalized
    }

    /** 曖昧さのない `host:port` 表記。 */
    fun hostPort(host: String, port: Int): String = "${authorityHost(host)}:$port"

    /** HTTP Host ヘッダー等。既定ポートならポートを省略する。 */
    fun authority(host: String, port: Int, defaultPort: Int): String =
        if (port == defaultPort) authorityHost(host) else hostPort(host, port)

    /** JSch の known_hosts キー。既定 SSH ポート以外は `[host]:port` 形式になる。 */
    fun knownHostKey(host: String, port: Int): String =
        if (port == 22) normalize(host) else hostPort(host, port)
}
