package com.zerotoship.z2term.proot

/**
 * `z2run <gui-app ...>` ランチャ (P3 = CUI⇄GUI 連動)。
 *
 * 端末タブの proot 環境 (env: `DISPLAY=:N`/`Z2_DISPLAY=N`) で実行する前提。動作は次の通り:
 *
 *  1. `Z2_DISPLAY` 未設定 (P3 対応前 / 古い経路) なら、何もせず引数をそのまま `exec` する
 *     (= 透過なフォールバック)。これで既存コマンドの邪魔をしない。
 *  2. `:N` の Xvnc がまだ動いていなければ `z2gui start` をバックグラウンドで起動して立ち上げる。
 *     z2gui は wait し続けるので `&` で投げて先に進む。Xvnc が UNIX ソケットを開くまで短時間ポーリング。
 *  3. **z2term への通知**: `/storage/app/z2gui.events` (proot 内) に `OPEN N\n` を append。
 *     このパスは proot バインドで Android 側の `getExternalFilesDir(null)` = `/storage/emulated/0/Android/data/.../files/`
 *     と同じ実体を指す。z2term の `GuiEventWatcher` (FileObserver) がここを監視していて、
 *     `OPEN N` を見つけると対応する GUI タブを開く / 前面化する。
 *  4. 引数があれば `exec "$@"` でユーザーの GUI アプリへバトンタッチ。引数なし (`z2run` 単体) は
 *     「GUI タブを開いて Xvnc を立ち上げるだけ」として静かに終了。
 *
 * 設計上の要点:
 * - 自前で X クライアントを起こすので、`xclock &` 等とは独立に「タブを必ず開く」副作用を一度だけ実行する
 *   ためのフックがあると便利、というのが z2run の存在理由。
 * - z2term 側 (`GuiEventWatcher`) は **新規追記分の行だけ** 読むため、ファイルが長期間残っても問題ない
 *   (再起動跨ぎは [GuiEventWatcher] が起動時にファイル末尾までシークして「過去分」を捨てる)。
 * - 失敗時の握り潰しは最小限。echo の `>>` 失敗 (権限) だけは握り潰す (端末側で permission denied が
 *   ノイズになるため)。Xvnc 起動失敗は z2gui 側ログ (`/tmp/z2gui-xvnc-N.log`) で追える。
 */
fun z2runScript(): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    return """
        |#!/bin/sh
        |# z2term: CUI⇄GUI 連動ランチャ (端末から GUI アプリを起動すると GUI タブが自動で開く)。
        |# 使い方: z2run [gui-app ...]
        |#   端末タブには env Z2_DISPLAY=N / DISPLAY=:N が proot から注入されているため、
        |#   ここで明示指定する必要はない (未設定なら何もせず exec フォールバック)。
        |
        |# 1) Z2_DISPLAY 未設定 (P3 非対応経路) は透過: 引数があれば exec、無ければ no-op で終了。
        |if [ -z "${d}{Z2_DISPLAY}" ]; then
        |  if [ ${d}# -gt 0 ]; then exec "${d}@"; else exit 0; fi
        |fi
        |
        |DISPLAY_NUM="${d}{Z2_DISPLAY}"
        |XSOCK="/tmp/.X11-unix/X${d}{DISPLAY_NUM}"
        |
        |# 2) z2term に「GUI タブを開け」と先に通知する (Xvnc 起動より先で OK)。
        |#    GuiSession.start 側も同じ Xvnc を立てに行くが z2gui の x_alive ガードで二重起動は防止される。
        |#    早めに通知することで、ユーザーが GUI タブに切替えて寸法を確定し始める時間を稼げる。
        |mkdir -p /storage/app 2>/dev/null
        |echo "OPEN ${d}{DISPLAY_NUM}" >> /storage/app/z2gui.events 2>/dev/null || true
        |
        |# 3) Xvnc :N が無ければ z2gui で起動。z2gui は wait し続けるので & で投げて進む。
        |#    Z2_NO_TERM=1 で xterm の同時起動を抑止 (z2run はユーザー指定の GUI アプリだけを出したい)。
        |if [ ! -e "${d}XSOCK" ]; then
        |  Z2_NO_TERM=1 setsid /usr/local/bin/z2gui start </dev/null >"/tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log" 2>&1 &
        |  # 待ち時間は GUI 一式が導入済みか未導入かで動的に変える:
        |  #   導入済み → 10 秒 (Xvnc 起動だけ)
        |  #   未導入 → 5 分 (apk/apt/pacman のダウンロード + 展開を含む)
        |  # `z2gui check` は detect_pm + has Xvnc/openbox/term をするだけなので安価。
        |  STATUS=${d}(/usr/local/bin/z2gui check 2>/dev/null | tail -1)
        |  case "${d}STATUS" in
        |    GUI_INSTALLED) MAX_TICKS=100 ;;   # 100 * 0.1s = 10s
        |    *)             MAX_TICKS=3000 ;;  # 3000 * 0.1s = 5min
        |  esac
        |  i=0
        |  while [ ${d}i -lt ${d}MAX_TICKS ] && [ ! -e "${d}XSOCK" ]; do
        |    sleep 0.1; i=${d}((i+1))
        |  done
        |  if [ ! -e "${d}XSOCK" ]; then
        |    echo "z2run: Xvnc :${d}{DISPLAY_NUM} が起動しませんでした。/tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log を確認してください。" >&2
        |    [ ${d}# -gt 0 ] || exit 1
        |    # 引数がある場合はそのまま exec する (X 接続失敗のエラーは GUI アプリ側で出る)。
        |  fi
        |fi
        |
        |# 4) 引数があればユーザーの GUI アプリへバトンタッチ。無ければここで終了。
        |if [ ${d}# -gt 0 ]; then exec "${d}@"; fi
    """.trimMargin() + "\n"
}
