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
 *  3. **z2term への通知**: `/storage/app/z2gui.events` (proot 内) に `OPEN N distro\n` を append。
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
fun z2runScript(lang: String = "ja"): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    // Xvnc 起動失敗時のエラー文 (z2run は 1 行しかメッセージを出さない)
    val t = CliText(lang)
    val errMsg = t(
        en = "z2run: Xvnc :${d}{DISPLAY_NUM} did not start. See /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log.",
        ja = "z2run: Xvnc :${d}{DISPLAY_NUM} が起動しませんでした。/tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log を確認してください。",
        "zh-CN" to "z2run: Xvnc :${d}{DISPLAY_NUM} 没有启动。请查看 /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log。",
        "zh-TW" to "z2run: Xvnc :${d}{DISPLAY_NUM} 沒有啟動。請查看 /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log。",
        "es" to "z2run: Xvnc :${d}{DISPLAY_NUM} no se inició. Consulta /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log."
    )
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
        |# GUI子プロセスだけがglycin用bubblewrap互換入口を先に見る。親シェルのPATHは変えない。
        |export PATH="$Z2TERM_GUI_COMPAT_DIR:${d}{PATH:-/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin}"
        |
        |DISPLAY_NUM="${d}{Z2_DISPLAY}"
        |XSOCK="/tmp/.X11-unix/X${d}{DISPLAY_NUM}"
        |
        |# 2) z2term に「GUI タブを開け」と先に通知する (Xvnc 起動より先で OK)。
        |#    GuiSession.start 側も同じ Xvnc を立てに行くが z2gui の x_alive ガードで二重起動は防止される。
        |#    早めに通知することで、ユーザーが GUI タブに切替えて寸法を確定し始める時間を稼げる。
        |mkdir -p /storage/app 2>/dev/null
        |if [ -n "${d}{Z2_DISTRO_ID:-}" ]; then
        |  echo "OPEN ${d}{DISPLAY_NUM} ${d}{Z2_DISTRO_ID}" >> /storage/app/z2gui.events 2>/dev/null || true
        |else
        |  # 旧起動経路との互換。新しいProotLauncherは常にZ2_DISTRO_IDを注入する。
        |  echo "OPEN ${d}{DISPLAY_NUM}" >> /storage/app/z2gui.events 2>/dev/null || true
        |fi
        |
        |# 3) Xvnc :N が無ければ z2gui で起動。z2gui は wait し続けるので & で投げて進む。
        |STARTED_NOW=0
        |if [ ! -e "${d}XSOCK" ]; then
        |  STARTED_NOW=1
        |  setsid /usr/local/bin/z2gui start </dev/null >"/tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log" 2>&1 &
        |  # 待ち時間は GUI 一式が導入済みか未導入かで動的に変える:
        |  #   導入済み → 10 秒 (Xvnc 起動だけ)
        |  #   未導入 → 5 分 (apk/apt/pacman のダウンロード + 展開を含む)
        |  # `z2gui check` は detect_pm + GUI 基盤の導入確認だけなので安価。
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
        |    echo "$errMsg" >&2
        |    [ ${d}# -gt 0 ] || exit 1
        |    # 引数がある場合はそのまま exec する (X 接続失敗のエラーは GUI アプリ側で出る)。
        |  fi
        |fi
        |
        |# 4) GUI セッションの土台 (XDG_RUNTIME_DIR / D-Bus セッションバス) を引き継ぐ (0.8.498)。
        |#    ⚠ z2gui は GUI 側で立っているので、**別タブのここには環境が伝わらない**。z2gui が
        |#       控えに書いたアドレスを読んで、同じバスに相乗りする。これが無いと、補助プロセスを
        |#       別プロセスとして起こす作りのアプリ (ファイル管理系の KIO 等) が軒並み起動に失敗し、
        |#       サムネイル・ゴミ箱・接続機器の一覧がまとめて出なくなる。
        |XDGDIR="/tmp/z2gui-xdg-${d}{DISPLAY_NUM}"
        |if [ "${d}STARTED_NOW" = "1" ]; then
        |  # 今 GUI を起こした場合、バスは X より少し遅れて立つ。⚠ 立たない環境もあるので待ち切らない。
        |  k=0
        |  while [ ${d}k -lt 30 ] && [ ! -r "${d}XDGDIR/dbus-address" ]; do sleep 0.1; k=${d}((k+1)); done
        |fi
        |if [ -d "${d}XDGDIR" ]; then
        |  export XDG_RUNTIME_DIR="${d}{XDG_RUNTIME_DIR:-${d}XDGDIR}"
        |  if [ -z "${d}{DBUS_SESSION_BUS_ADDRESS:-}" ] && [ -r "${d}XDGDIR/dbus-address" ]; then
        |    DBUS_ADDR=${d}(cat "${d}XDGDIR/dbus-address" 2>/dev/null)
        |    [ -n "${d}DBUS_ADDR" ] && export DBUS_SESSION_BUS_ADDRESS="${d}DBUS_ADDR"
        |  fi
        |fi
        |# Xvnc 用の非 SHM 描画経路に固定する。
        |export QT_QPA_PLATFORM="${d}{QT_QPA_PLATFORM:-xcb}"
        |export QT_XCB_NO_MITSHM="${d}{QT_XCB_NO_MITSHM:-1}"
        |export QT_X11_NO_MITSHM="${d}{QT_X11_NO_MITSHM:-1}"
        |export GDK_BACKEND="${d}{GDK_BACKEND:-x11}"
        |export GDK_RENDERING="${d}{GDK_RENDERING:-image}"
        |
        |# 5) 引数があればユーザーの GUI アプリへバトンタッチ。無ければここで終了。
        |if [ ${d}# -gt 0 ]; then exec "${d}@"; fi
    """.trimMargin() + "\n"
}
