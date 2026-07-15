package com.zerotoship.z2term.proot

import com.zerotoship.z2term.settings.ServerEntry

/**
 * 常駐サーバー用 supervisor スクリプトの生成。
 *
 * エンジン (proot/z2root/chroot) 下で **すべてのサーバーの親になる 1 本のプロセス**として起動され、
 * 各サーバーを **auto-restart ループ**付きで起動して自身は `wait` で生き続ける。エンジンプロセスを
 * kill すると (= [com.zerotoship.z2term.service.ServerDaemonManager.stop]) この supervisor と全サーバーが
 * まとめて終了する。
 *
 * 各サーバーの稼働状態は [STATUS_DIR] 配下に `<token>.status` (`state=…` / `pid=…`) として書き出し、
 * アプリ側は rootfs の実体パス (`filesDir/distros/<id>/var/lib/z2term-servers/`) を読んで一覧に反映する。
 */
object ServerSupervisorScript {

    /** rootfs 内のスクリプト配置先 (ProotLauncher の command に渡す絶対パス)。 */
    const val SCRIPT_PATH = "/usr/local/bin/z2term-server-supervisor"

    /** status ファイルを置く rootfs 内ディレクトリ (var/lib は bind されない実ディレクトリ)。 */
    const val STATUS_DIR = "/var/lib/z2term-servers"

    /** アプリから読むときの rootfs ルートからの相対パス。 */
    const val STATUS_REL = "var/lib/z2term-servers"

    /** sh のシングルクォート内へ安全に埋め込む (`'` → `'\''`)。 */
    private fun sq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * [entries] (enabled のみ想定) を起動する supervisor スクリプト本文を返す。
     * token が衝突しないよう重複には連番を付ける。
     */
    fun generate(entries: List<ServerEntry>): String {
        val seen = HashMap<String, Int>()
        val sb = StringBuilder()
        sb.append("#!/bin/sh\n")
        sb.append("# z2term server supervisor (auto-generated). Do not edit by hand.\n")
        sb.append("STATUS_DIR=").append(STATUS_DIR).append('\n')
        sb.append("mkdir -p \"\$STATUS_DIR\" 2>/dev/null || true\n")
        sb.append("rm -f \"\$STATUS_DIR\"/*.status 2>/dev/null || true\n\n")
        sb.append(
            """
            run_server() {
              name="${'$'}1"; cmd="${'$'}2"
              while true; do
                printf 'state=starting\ncmd=%s\n' "${'$'}cmd" > "${'$'}STATUS_DIR/${'$'}name.status"
                sh -c "${'$'}cmd" &
                spid=${'$'}!
                printf 'state=running\npid=%s\ncmd=%s\n' "${'$'}spid" "${'$'}cmd" > "${'$'}STATUS_DIR/${'$'}name.status"
                wait "${'$'}spid"
                rc=${'$'}?
                printf 'state=restarting\nlast_exit=%s\ncmd=%s\n' "${'$'}rc" "${'$'}cmd" > "${'$'}STATUS_DIR/${'$'}name.status"
                sleep 3
              done
            }

            """.trimIndent()
        )
        sb.append('\n')
        for (e in entries) {
            var token = e.safeToken()
            val n = seen.getOrDefault(token, 0)
            seen[token] = n + 1
            if (n > 0) token = "${token}_$n"
            sb.append("run_server ").append(sq(token)).append(' ').append(sq(e.command)).append(" &\n")
        }
        sb.append("\n# keep the engine process alive until it is killed.\nwait\n")
        return sb.toString()
    }
}
