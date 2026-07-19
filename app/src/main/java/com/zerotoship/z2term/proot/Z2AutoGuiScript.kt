package com.zerotoship.z2term.proot

/**
 * `z2-autogui` = 「端末で GUI アプリを起動したら GUI タブを自動で開く」自動連動 (P-自動GUI)。
 *
 * [z2runScript] の `z2run <app>` を**明示的に打たなくても**、GUI アプリを起動しただけで
 * 同じ副作用 (`:N` の Xvnc 確保 + z2term への `OPEN N` 通知 → GUI タブが自動で開く) を起こす。
 *
 * 仕組み:
 *  - シェルの preexec フック (bash: DEBUG トラップ / zsh: `add-zsh-hook preexec`) が、
 *    これから実行されるコマンドラインを `/usr/local/bin/z2-autogui` に渡す。
 *  - z2-autogui は先頭の実コマンドを取り出し、それが GUI バイナリ (libX11/libxcb/GTK/Qt 等に
 *    リンク、または既知の GUI ランチャ名) かどうかを判定。GUI のときだけ z2run と同じ起動
 *    シーケンス (OPEN 通知 + Xvnc 起動待ち) を実行する。CUI コマンドは即 `exit 0` で素通り。
 *  - 判定は重い `ldd` を毎回走らせないよう、バイナリ実体パスごとに結果を `$TMPDIR/z2-autogui` に
 *    キャッシュする。
 *
 * `Z2_DISPLAY` 未設定 (SSH/GUI 内部シェル等) では何もしない。フックは interactive shell の rc
 * からのみ仕込まれるので、スクリプト実行 (非対話 bash) には影響しない。
 */

/** `/usr/local/bin/z2-autogui` 本体。preexec フックから「これから実行されるコマンド語」を引数で受ける。 */
fun z2AutoGuiScript(): String {
    val d = "${'$'}"
    return """
        |#!/bin/sh
        |# z2term: CUI で GUI アプリを起動すると GUI タブを自動で開く判定ヘルパー (preexec から呼ばれる)。
        |# 引数: これから実行されるコマンド語 (フックが word 分割済み)。GUI と判定したときだけ Xvnc を確保する。
        |
        |# Z2_DISPLAY 未設定 (P 非対応経路 / SSH / GUI 内部) は完全に無視。
        |[ -z "${d}{Z2_DISPLAY}" ] && exit 0
        |[ ${d}# -eq 0 ] && exit 0
        |
        |# 先頭の環境変数代入 (FOO=bar)・透過ラッパ (env/sudo/nohup/…)・オプション (-x) を読み飛ばし、
        |# 実際に起動されるコマンド名を 1 つ取り出す。
        |cmd=
        |for tok in "${d}@"; do
        |  case "${d}tok" in
        |    *=*) continue ;;
        |    env|sudo|nohup|setsid|exec|command|nice|stdbuf|time|doas) continue ;;
        |    -*) continue ;;
        |    *) cmd="${d}tok"; break ;;
        |  esac
        |done
        |[ -z "${d}cmd" ] && exit 0
        |
        |# パス解決。ビルトイン/関数/エイリアス (パスでない) は GUI ではないので無視。
        |bin=${d}(command -v "${d}cmd" 2>/dev/null) || exit 0
        |case "${d}bin" in */*) ;; *) exit 0 ;; esac
        |
        |# GUI 判定 (バイナリ実体パスごとにキャッシュ。ldd は初回のみ)。
        |# ディレクトリ名の末尾は判定ロジックの世代。判定を直したら上げる = 古い判定結果を捨てる
        |# (一度 no と誤判定されるとキャッシュのせいで直しても効かないため)。
        |cache_dir="${d}{TMPDIR:-/tmp}/z2-autogui2"
        |mkdir -p "${d}cache_dir" 2>/dev/null
        |real=${d}(readlink -f "${d}bin" 2>/dev/null || echo "${d}bin")
        |key=${d}(printf '%s' "${d}real" | tr '/ ' '__')
        |cache="${d}cache_dir/${d}key"
        |if [ -f "${d}cache" ]; then
        |  isgui=${d}(cat "${d}cache" 2>/dev/null)
        |else
        |  # 既知の GUI ランチャ名 (空白区切り)。ldd を待たず即 GUI 扱いにする。
        |  GUI_NAMES="xterm xeyes xclock xcalc xlogo xmessage st urxvt rxvt qterminal konsole xfce4-terminal lxterminal gnome-terminal alacritty kitty firefox firefox-esr chromium chromium-browser google-chrome google-chrome-stable midori surf thunderbird thunderbird-esr seamonkey thunar pcmanfm pcmanfm-qt nautilus nemo dolphin code codium geany mousepad gedit kate leafpad featherpad inkscape gimp krita blender libreoffice soffice abiword gnumeric vlc mpv smplayer feh eog ristretto gpicview sxiv galculator qalculate-gtk gnome-calculator evince atril zathura xpdf okular xournalpp wireshark virt-manager remmina"
        |  base="${d}{cmd##*/}"   # フルパス/相対パス起動でも名前で照合できるよう basename を取る
        |  # ldd を当てる対象。ラッパーが「実体を exec するだけの sh」のことがあるので、
        |  # よくある実体の置き場 (/usr/lib/<name>/<name>) があればそちらを見る。
        |  # 例: Arch の thunderbird は /usr/sbin/thunderbird が sh・実体は /usr/lib/thunderbird/thunderbird。
        |  target="${d}real"
        |  for cand in "/usr/lib/${d}base/${d}base" "/usr/lib64/${d}base/${d}base" "/opt/${d}base/${d}base"; do
        |    [ -x "${d}cand" ] && { target="${d}cand"; break; }
        |  done
        |  case " ${d}GUI_NAMES " in
        |    *" ${d}base "*) isgui=yes ;;
        |    *)
        |      # .desktop を持つならデスクトップアプリ。ldd が効かないラッパー/スクリプト系を拾う。
        |      # 誤爆を避けるためファイル名の完全一致だけを見る (Exec= 行の走査はしない)。
        |      if [ -f "/usr/share/applications/${d}base.desktop" ] ||
        |         [ -f "/usr/local/share/applications/${d}base.desktop" ]; then
        |        isgui=yes
        |      elif ldd "${d}target" 2>/dev/null | grep -Eq 'libX11|libxcb|libwayland|libgtk|libgdk|libQt|libqt'; then
        |        isgui=yes
        |      else
        |        isgui=no
        |      fi ;;
        |  esac
        |  printf '%s' "${d}isgui" > "${d}cache" 2>/dev/null
        |fi
        |[ "${d}isgui" = yes ] || exit 0
        |
        |# --- ここから GUI 起動シーケンス (z2run と同等) ---
        |DISPLAY_NUM="${d}{Z2_DISPLAY}"
        |XSOCK="/tmp/.X11-unix/X${d}{DISPLAY_NUM}"
        |
        |# z2term に「GUI タブを開け」と通知 (Xvnc 起動より先で OK。idempotent)。
        |mkdir -p /storage/app 2>/dev/null
        |echo "OPEN ${d}{DISPLAY_NUM}" >> /storage/app/z2gui.events 2>/dev/null || true
        |
        |# Xvnc :N が無ければ z2gui で起動。z2gui は wait し続けるので & で投げて進む。
        |# Z2_NO_TERM=1 で xterm の同時起動を抑止 (ユーザー指定 GUI アプリだけを出したい)。
        |if [ ! -e "${d}XSOCK" ]; then
        |  Z2_NO_TERM=1 setsid /usr/local/bin/z2gui start </dev/null >"/tmp/z2-autogui-z2gui-${d}{DISPLAY_NUM}.log" 2>&1 &
        |  # 導入済みなら Xvnc 起動だけ (10s)、未導入なら apk/apt/pacman の取得込み (最大 5min) 待つ。
        |  STATUS=${d}(/usr/local/bin/z2gui check 2>/dev/null | tail -1)
        |  case "${d}STATUS" in
        |    GUI_INSTALLED) MAX_TICKS=100 ;;
        |    *)             MAX_TICKS=3000 ;;
        |  esac
        |  i=0
        |  while [ ${d}i -lt ${d}MAX_TICKS ] && [ ! -e "${d}XSOCK" ]; do
        |    sleep 0.1; i=${d}((i+1))
        |  done
        |fi
        |exit 0
    """.trimMargin() + "\n"
}

/**
 * bash 用 preexec 相当フック (DEBUG トラップ)。interactive bash の rc に 1 度だけ仕込む。
 * これから実行されるコマンド ([d]BASH_COMMAND) を z2-autogui に渡す。z2term が仕込む prompt
 * フック (history -a / __z2term_osc7) と自己再帰は除外し、Z2_DISPLAY 未設定なら即 return。
 */
fun autoGuiBashHookBlock(marker: String): String {
    val d = "${'$'}"
    return """
        |$marker
        |if [ -n "${d}BASH_VERSION" ]; then
        |  __z2term_autogui() {
        |    [ -z "${d}Z2_DISPLAY" ] && return
        |    [ -n "${d}COMP_LINE" ] && return
        |    case "${d}BASH_COMMAND" in
        |      __z2term_autogui*|__z2term_osc7*|history*) return ;;
        |    esac
        |    /usr/local/bin/z2-autogui ${d}BASH_COMMAND >/dev/null 2>&1
        |  }
        |  case "${d}(trap -p DEBUG)" in
        |    *__z2term_autogui*) ;;
        |    *) trap '__z2term_autogui' DEBUG ;;
        |  esac
        |fi
        |# <<< z2term autogui <<<
    """.trimMargin()
}

/**
 * zsh 用 preexec フック。interactive zsh の rc に 1 度だけ仕込む。preexec の第 1 引数
 * (実行されるコマンドライン) を `${'$'}{(z)1}` で word 分割し z2-autogui へ渡す。
 */
fun autoGuiZshHookBlock(marker: String): String {
    val d = "${'$'}"
    return """
        |$marker
        |if [ -n "${d}ZSH_VERSION" ]; then
        |  __z2term_autogui() {
        |    [ -z "${d}Z2_DISPLAY" ] && return
        |    /usr/local/bin/z2-autogui ${d}{(z)1} >/dev/null 2>&1
        |  }
        |  autoload -Uz add-zsh-hook 2>/dev/null && add-zsh-hook preexec __z2term_autogui
        |fi
        |# <<< z2term autogui <<<
    """.trimMargin()
}
