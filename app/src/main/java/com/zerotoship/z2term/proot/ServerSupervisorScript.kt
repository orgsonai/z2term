package com.zerotoship.z2term.proot

/**
 * 常駐サーバー用 supervisor スクリプトの生成。
 *
 * エンジン (proot/z2root/chroot) 下で **すべてのサーバーの親になる 1 本のプロセス**として起動され、
 * 各サーバーを **auto-restart ループ**付きで起動して自身は監視ループで生き続ける。エンジンプロセスを
 * kill すると (= [com.zerotoship.z2term.service.ServerDaemonManager.stop]) この supervisor と全サーバーが
 * まとめて終了する。
 *
 * ## ジョブファイル方式 (0.8.198・A3)
 *
 * **スクリプトはエントリを焼き込まない固定文字列**で、代わりに [STATUS_DIR] 配下のファイルを見る:
 *
 * | ファイル | 誰が書くか | 意味 |
 * |---|---|---|
 * | `<id>.job`    | アプリ | 実行するコマンド本文。**これが在ることがサーバーの定義**。消すと止まって片付く |
 * | `<id>.want`   | アプリ | `1` = 起動 / それ以外 = 停止 (個別 ON/OFF) |
 * | `<id>.status` | supervisor | `state=` / `pid=` / `restarts=` / `last_exit=` / `cmd=` |
 * | `<id>.log`    | supervisor | そのサーバーの標準出力・標準エラー |
 * | `<id>.exits`  | supervisor | 終了の履歴 (`<epoch> <rc>` を直近 [EXIT_KEEP] 行) |
 *
 * supervisor は監視ループで `*.job` を拾い、まだ動かしていないものがあれば run ループを起こす。
 * これにより **サーバーを追加・変更・削除しても supervisor 全体を再起動しなくてよい** (無停止リロード)。
 * 従来は「登録時点の全エントリの run ループを焼き込んだ 1 本の sh」だったため、起動後に追加した
 * エントリには対応するループが無く、反映に全体再起動 = 他のサーバーの巻き添え停止が必要だった。
 *
 * run ループはコマンド本文の変化も見ていて、`<id>.job` が書き換わったら**そのサーバーだけ**を
 * 再起動する (編集の反映も無停止)。
 *
 * ## 見張りの間隔 ([POLL_SECONDS]・0.8.268)
 *
 * このスクリプトはエンジン (proot/z2root) の中で動く。**エンジン下では外部コマンドを 1 回起こす
 * だけで ptrace 越しに数千 syscall になる**ため、見張りの間隔がそのまま端末の発熱と電池に効く。
 * 0.8.267 までは 1 秒ごとに `cat` を 2 回起こしていて (= サーバー 1 本あたり毎秒 3 プロセス、
 * 停止中のサーバーも同じ頻度)、常駐しているだけでエンジンが CPU を数 % 使い続けていた。
 *
 * そこで:
 *  - 見張りの間隔を [POLL_SECONDS] 秒に広げる
 *  - `<id>.want` は 1 行しかないのでシェル組み込みの `read` で読む (プロセスを起こさない)
 *  - **停止中のサーバーは `.status` を毎周期書き直さない** (中身が変わったときだけ書く)
 *
 * 代償として、個別 ON/OFF・追加・編集・削除の反映と、落ちたサーバーの再起動が最大
 * [POLL_SECONDS] 秒遅れる。常駐サーバーは「動き続けること」が仕事で、秒単位の応答は要らない。
 */
object ServerSupervisorScript {

    /** rootfs 内のスクリプト配置先 (ProotLauncher の command に渡す絶対パス)。 */
    const val SCRIPT_PATH = "/usr/local/bin/z2term-server-supervisor"

    /** status ファイルを置く rootfs 内ディレクトリ (var/lib は bind されない実ディレクトリ)。 */
    const val STATUS_DIR = "/var/lib/z2term-servers"

    /** アプリから読むときの rootfs ルートからの相対パス。 */
    const val STATUS_REL = "var/lib/z2term-servers"

    /**
     * サーバーごとのログをこのサイズまで許し、超えていたら後半 [LOG_KEEP] だけ残す。
     *
     * 「ローテーションしない」という既存方針 ([com.zerotoship.z2term.service.LogWriter]) は
     * **マクロが過去に遡って集計するログ** (events.jsonl 等) の話で、途中で切り替わると解析が
     * 面倒になるためだった。サーバーの標準出力は解析対象ではなく、常駐で延々と増え続ける方が
     * 実害が大きいので、こちらは上限を持たせる。
     */
    const val LOG_MAX_BYTES = 1024 * 1024
    /** 切り詰めたあとに残す量。 */
    const val LOG_KEEP_BYTES = 512 * 1024
    /** 終了コード履歴として残す行数。 */
    const val EXIT_KEEP = 20

    /**
     * 見張りの間隔 (秒)。KDoc の「見張りの間隔」参照 — エンジン下ではここが発熱と電池に直結する。
     * 縮めるときは「秒あたり何プロセス起こすことになるか」を必ず数えること。
     */
    const val POLL_SECONDS = 5

    /**
     * supervisor スクリプト本文を返す。**エントリには依存しない** (ジョブは実行時にファイルで渡す)。
     *
     * 注意: 生成シェルスクリプトでは `trimMargin` を使わない。行頭に `|` が残ると POSIX sh では
     * 常に構文エラーになり、スクリプトごと起動不能になる (0.8.187 の事故)。`trimIndent` を使い、
     * `ServerSupervisorScriptTest` が `sh -n` で構文を検証している。
     */
    fun generate(): String = """
        #!/bin/sh
        # z2term server supervisor (auto-generated). Do not edit by hand.
        STATUS_DIR=$STATUS_DIR
        LOG_MAX=$LOG_MAX_BYTES
        LOG_KEEP=$LOG_KEEP_BYTES
        EXIT_KEEP=$EXIT_KEEP
        # 見張りの間隔(秒)。エンジン(proot/z2root)下では外部コマンドを 1 回起こすだけで
        # ptrace 越しに数千 syscall になるため、ここを詰めると常駐しているだけで端末が温まる。
        POLL=$POLL_SECONDS

        # 常駐サーバーとして起動されたことを子プロセスへ伝える。sshd wrapper のように
        # 「既定では自分を背景化して即 exit する」コマンドは、これを見て前景常駐へ切り替える
        # (背景化されると supervisor が落ちたと誤認して再起動ループになる)。
        Z2_SUPERVISED=1
        export Z2_SUPERVISED

        mkdir -p "${'$'}STATUS_DIR" 2>/dev/null || true
        # 前回の残骸を掃除する。.claimed が残っていると、その id の run ループが二度と
        # 起こされない (= サーバーが黙って起動しない) ので必ず消す。
        rm -f "${'$'}STATUS_DIR"/*.status "${'$'}STATUS_DIR"/*.claimed 2>/dev/null || true

        # ログが大きくなり過ぎていたら後半だけ残す。
        # **そのサーバーが動いていない瞬間にだけ呼ぶこと** — 実行中に差し替えると、走っている
        # プロセスの fd が古い実体を掴んだままになり、以後の出力がどこにも現れなくなる。
        trim_log() {
          logf="${'$'}1"
          [ -f "${'$'}logf" ] || return 0
          sz=`wc -c < "${'$'}logf" 2>/dev/null || echo 0`
          [ "${'$'}sz" -gt "${'$'}LOG_MAX" ] 2>/dev/null || return 0
          tail -c "${'$'}LOG_KEEP" "${'$'}logf" > "${'$'}logf.tmp" 2>/dev/null && mv "${'$'}logf.tmp" "${'$'}logf"
        }

        # 1 サーバーの常駐ループ。
        #  - <id>.job が在る間だけ生きる (消えたら片付けて抜ける = 削除の無停止反映)
        #  - <id>.want が '1' の間だけ実際に起動し、落ちたら再起動する
        #  - <id>.job の中身が変わったら、そのサーバーだけ再起動する (編集の無停止反映)
        run_server() {
          name="${'$'}1"
          jobf="${'$'}STATUS_DIR/${'$'}name.job"
          wantf="${'$'}STATUS_DIR/${'$'}name.want"
          logf="${'$'}STATUS_DIR/${'$'}name.log"
          exitf="${'$'}STATUS_DIR/${'$'}name.exits"
          statf="${'$'}STATUS_DIR/${'$'}name.status"
          restarts=0
          laststop=''
          while [ -f "${'$'}jobf" ]; do
            cmd=`cat "${'$'}jobf" 2>/dev/null`
            # .want は '1' か '0' の 1 行しかないので組み込みの read で読む (プロセスを起こさない)。
            want=''
            read want < "${'$'}wantf" 2>/dev/null
            if [ -z "${'$'}cmd" ] || [ "${'$'}want" != "1" ]; then
              # 停止中は中身が変わったときだけ書く。毎周期書き直すと、動いてすらいない
              # サーバーの分までエンジンとディスクが回り続ける。
              if [ "${'$'}laststop" != "${'$'}restarts:${'$'}cmd" ]; then
                printf 'state=stopped\nrestarts=%s\ncmd=%s\n' "${'$'}restarts" "${'$'}cmd" > "${'$'}statf"
                laststop="${'$'}restarts:${'$'}cmd"
              fi
              sleep "${'$'}POLL"
              continue
            fi
            laststop=''
            trim_log "${'$'}logf"
            printf 'state=starting\nrestarts=%s\ncmd=%s\n' "${'$'}restarts" "${'$'}cmd" > "${'$'}statf"
            sh -c "${'$'}cmd" >> "${'$'}logf" 2>&1 &
            spid=${'$'}!
            printf 'state=running\npid=%s\nrestarts=%s\ncmd=%s\n' "${'$'}spid" "${'$'}restarts" "${'$'}cmd" > "${'$'}statf"
            # 動いている間、停止指示 / 削除 / コマンド変更を POLL 秒ごとに見る。
            # kill -0 と read と test は組み込みなので、ここで起こすプロセスは sleep と
            # (want が生きているときだけの) cat の 2 つに抑える。
            while kill -0 "${'$'}spid" 2>/dev/null; do
              now=''
              read now < "${'$'}wantf" 2>/dev/null
              if [ "${'$'}now" != "1" ] || [ ! -f "${'$'}jobf" ]; then
                kill "${'$'}spid" 2>/dev/null
                break
              fi
              newcmd=`cat "${'$'}jobf" 2>/dev/null`
              if [ "${'$'}newcmd" != "${'$'}cmd" ]; then
                kill "${'$'}spid" 2>/dev/null
                break
              fi
              sleep "${'$'}POLL"
            done
            # kill した場合もここで回収する (wait は 1 回だけ呼ぶこと。二度目は
            # 「そんな子は居ない」で無関係な終了コードを拾ってしまう)。
            wait "${'$'}spid" 2>/dev/null
            rc=${'$'}?
            stamp=`date +%s 2>/dev/null || echo 0`
            printf '%s %s\n' "${'$'}stamp" "${'$'}rc" >> "${'$'}exitf"
            tail -n "${'$'}EXIT_KEEP" "${'$'}exitf" > "${'$'}exitf.tmp" 2>/dev/null && mv "${'$'}exitf.tmp" "${'$'}exitf"
            want=''
            read want < "${'$'}wantf" 2>/dev/null
            if [ "${'$'}want" != "1" ] || [ ! -f "${'$'}jobf" ]; then
              printf 'state=stopped\nlast_exit=%s\nrestarts=%s\ncmd=%s\n' "${'$'}rc" "${'$'}restarts" "${'$'}cmd" > "${'$'}statf"
              continue
            fi
            restarts=`expr ${'$'}restarts + 1`
            printf 'state=restarting\nlast_exit=%s\nrestarts=%s\ncmd=%s\n' "${'$'}rc" "${'$'}restarts" "${'$'}cmd" > "${'$'}statf"
            sleep 3
          done
          rm -f "${'$'}statf" "${'$'}STATUS_DIR/${'$'}name.claimed" 2>/dev/null
        }

        # 監視ループ: 新しく置かれた .job を拾って run ループを起こす。
        # アプリがサーバーを追加・変更・削除しても supervisor 自身と他サーバーは止めない。
        while true; do
          for jobf in "${'$'}STATUS_DIR"/*.job; do
            [ -f "${'$'}jobf" ] || continue
            base=${'$'}{jobf##*/}
            name=${'$'}{base%.job}
            [ -f "${'$'}STATUS_DIR/${'$'}name.claimed" ] && continue
            : > "${'$'}STATUS_DIR/${'$'}name.claimed"
            run_server "${'$'}name" &
          done
          sleep "${'$'}POLL"
        done
    """.trimIndent() + "\n"
}
