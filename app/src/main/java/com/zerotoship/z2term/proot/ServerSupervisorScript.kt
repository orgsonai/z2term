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
 * **個別 ON/OFF**: スクリプトには (enabled/disabled を問わず) 全エントリの run ループを焼き込み、各ループは
 * [STATUS_DIR] 配下の `<id>.want` フラグ (`1`=起動 / それ以外=停止) を監視する。アプリが
 * [com.zerotoship.z2term.service.ServerDaemonManager.setWant] でこのフラグを書き換えると、supervisor を
 * 再起動せずに (＝他サーバーを止めずに) その 1 本だけを起動/停止できる。
 *
 * 各サーバーの稼働状態は [STATUS_DIR] 配下に `<id>.status` (`state=…` / `pid=…`) として書き出し、
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
     * [entries] (enabled/disabled を問わず command が非空の全件) を焼き込む supervisor スクリプト本文を返す。
     * 各ループは `<id>.want` フラグを監視し、`1` の間だけサーバーを起動・auto-restart する。フラグの
     * 初期値は各エントリの [ServerEntry.enabled] を反映する。id は一意なのでファイル名衝突は起きない。
     */
    fun generate(entries: List<ServerEntry>): String {
        val sb = StringBuilder()
        sb.append("#!/bin/sh\n")
        sb.append("# z2term server supervisor (auto-generated). Do not edit by hand.\n")
        sb.append("STATUS_DIR=").append(STATUS_DIR).append('\n')
        sb.append("mkdir -p \"\$STATUS_DIR\" 2>/dev/null || true\n")
        sb.append("rm -f \"\$STATUS_DIR\"/*.status 2>/dev/null || true\n\n")
        sb.append(
            """
            # 1 サーバーの常駐ループ。<id>.want が '1' の間だけ起動し、落ちたら再起動する。
            # want が '1' 以外になったら (アプリの個別 OFF) 実行中プロセスを止めて待機する。
            run_server() {
              name="${'$'}1"; cmd="${'$'}2"
              wantf="${'$'}STATUS_DIR/${'$'}name.want"
              while true; do
                if [ "${'$'}(cat "${'$'}wantf" 2>/dev/null)" != "1" ]; then
                  printf 'state=stopped\ncmd=%s\n' "${'$'}cmd" > "${'$'}STATUS_DIR/${'$'}name.status"
                  sleep 1
                  continue
                fi
                printf 'state=starting\ncmd=%s\n' "${'$'}cmd" > "${'$'}STATUS_DIR/${'$'}name.status"
                sh -c "${'$'}cmd" &
                spid=${'$'}!
                printf 'state=running\npid=%s\ncmd=%s\n' "${'$'}spid" "${'$'}cmd" > "${'$'}STATUS_DIR/${'$'}name.status"
                # プロセスが生きている間、want フラグを監視。OFF になったら kill して待機へ。
                while kill -0 "${'$'}spid" 2>/dev/null; do
                  if [ "${'$'}(cat "${'$'}wantf" 2>/dev/null)" != "1" ]; then
                    kill "${'$'}spid" 2>/dev/null
                    wait "${'$'}spid" 2>/dev/null
                    break
                  fi
                  sleep 1
                done
                if [ "${'$'}(cat "${'$'}wantf" 2>/dev/null)" != "1" ]; then
                  printf 'state=stopped\ncmd=%s\n' "${'$'}cmd" > "${'$'}STATUS_DIR/${'$'}name.status"
                  continue
                fi
                wait "${'$'}spid" 2>/dev/null
                rc=${'$'}?
                printf 'state=restarting\nlast_exit=%s\ncmd=%s\n' "${'$'}rc" "${'$'}cmd" > "${'$'}STATUS_DIR/${'$'}name.status"
                sleep 3
              done
            }

            """.trimIndent()
        )
        sb.append('\n')
        // want フラグの初期値を書き出す (enabled=1 / disabled=0)。以後アプリが個別に上書きする。
        for (e in entries) {
            val want = if (e.enabled) "1" else "0"
            sb.append("printf '%s' ").append(sq(want))
                .append(" > \"\$STATUS_DIR/").append(e.id).append(".want\"\n")
        }
        sb.append('\n')
        for (e in entries) {
            sb.append("run_server ").append(sq(e.id)).append(' ').append(sq(e.command)).append(" &\n")
        }
        sb.append("\n# keep the engine process alive until it is killed.\nwait\n")
        return sb.toString()
    }
}
