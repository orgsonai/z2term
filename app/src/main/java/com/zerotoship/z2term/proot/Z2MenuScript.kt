package com.zerotoship.z2term.proot

/**
 * `z2menu` — distro に**実際に入っている** GUI アプリの一覧を返すヘルパ (0.8.498)。
 *
 * ```
 * z2menu          openbox の pipe menu 用 XML を出す (デスクトップ右クリック → メニュー)
 * z2menu list     名前 <TAB> コマンド <TAB> 説明 <TAB> 端末フラグ <TAB> 分類 の TSV を出す
 * ```
 *
 * ## なぜ要るか
 *
 * openbox の既定メニュー (`/etc/xdg/openbox/menu.xml`) は distro が用意した**固定の一覧**で、
 * 入っていないアプリが大量に並ぶ。押しても何も起きない項目ばかりのメニューは、無いより分かりにくい。
 * `.desktop` を読んで **`Exec` の実体が PATH にあるものだけ**を出せば、押せば必ず起動する一覧になる。
 *
 * ## 判定 (freedesktop.org Desktop Entry 仕様)
 *
 * - `[Desktop Entry]` セクションだけを読む (`[Desktop Action …]` は無視)
 * - `Type=Application` 以外は捨てる (Link / Directory)
 * - `NoDisplay=true` / `Hidden=true` は捨てる (メニューに出すな、の指示)
 * - `TryExec` があればその実体を確認する。無ければ捨てる
 * - `Exec` の**先頭のコマンド**が PATH に無ければ捨てる
 * - `Exec` のフィールドコード (`%f %F %u %U %d %D %n %N %i %c %k %v %m`) は除去する。
 *   ⚠ 除去しないと、引数を取らない起動でアプリが `%U` という名前のファイルを開こうとする
 * - `Terminal=true` は除外する。GUI スタックは特定の端末を要求しない
 * - 同じファイル名 (desktop id) が複数のディレクトリにあるときは**先に読んだ方**を採る。
 *   探索順は `~/.local/share` → `/usr/local/share` → `/usr/share` で、利用者が置いた分が勝つ
 *
 * ## 移植性
 *
 * ⛔ **awk は POSIX の範囲で書くこと。** Alpine の busybox awk には `ENDFILE` も
 * `delete array` の全消しも無い。ファイルの切れ目は `FNR==1` で見て、最後の 1 件は `END` で出す。
 * 値は連想配列ではなく**スカラー変数**に持つ (どの awk でも同じに動く)。
 *
 * @param lang アプリの言語コード。`.desktop` の `Name[<lang>]` / `Comment[<lang>]` を引くのに使う。
 */
fun z2menuScript(lang: String = "ja"): String {
    val d = "${'$'}"  // シェル/awk の $ (Kotlin テンプレートと衝突しないように)
    val t = CliText(lang)

    // .desktop の言語サフィックスは POSIX ロケール表記 (ja / zh_CN)。アプリの言語コード
    // (ja / en / zh-CN) の `-` を `_` に直す。英語は素の Name= がそれなので空にする。
    val nameSuffix = when (lang) {
        "en" -> ""
        else -> lang.replace('-', '_')
    }

    val noApps = t(
        en = "(no application found)",
        ja = "(アプリが見つかりません)",
        "zh-CN" to "(未找到应用)",
        "zh-TW" to "(找不到應用程式)",
        "es" to "(no se encontró ninguna aplicación)"
    )
    // 分類のラベル。`.desktop` の Categories= を freedesktop の主分類に丸めて使う。
    val catLabels = listOf(
        "AudioVideo" to t(en = "Sound & Video", ja = "音と映像", "zh-CN" to "影音", "zh-TW" to "影音", "es" to "Sonido y vídeo"),
        "Development" to t(en = "Development", ja = "開発", "zh-CN" to "开发", "zh-TW" to "開發", "es" to "Desarrollo"),
        "Graphics" to t(en = "Graphics", ja = "画像", "zh-CN" to "图像", "zh-TW" to "圖像", "es" to "Gráficos"),
        "Game" to t(en = "Games", ja = "ゲーム", "zh-CN" to "游戏", "zh-TW" to "遊戲", "es" to "Juegos"),
        "Network" to t(en = "Internet", ja = "ネット", "zh-CN" to "网络", "zh-TW" to "網路", "es" to "Internet"),
        "Office" to t(en = "Office", ja = "文書", "zh-CN" to "办公", "zh-TW" to "辦公", "es" to "Oficina"),
        "System" to t(en = "System", ja = "システム", "zh-CN" to "系统", "zh-TW" to "系統", "es" to "Sistema"),
        "Utility" to t(en = "Utilities", ja = "道具", "zh-CN" to "工具", "zh-TW" to "工具", "es" to "Utilidades"),
        "Other" to t(en = "Other", ja = "その他", "zh-CN" to "其他", "zh-TW" to "其他", "es" to "Otros"),
    ).joinToString(";") { "${it.first}=${it.second}" }

    val usage = t(
        en = "usage: z2menu [list]   (no argument: openbox pipe menu XML)",
        ja = "usage: z2menu [list]   (引数なし: openbox の pipe menu XML)",
        "zh-CN" to "用法: z2menu [list]   (无参数: openbox pipe menu XML)",
        "zh-TW" to "用法: z2menu [list]   (無參數: openbox pipe menu XML)",
        "es" to "uso: z2menu [list]   (sin argumentos: XML de pipe menu de openbox)"
    )

    return """
        |#!/bin/sh
        |# z2term: 入っている GUI アプリ (.desktop) の一覧。
        |#   z2menu        openbox の pipe menu 用 XML
        |#   z2menu list   名前<TAB>コマンド<TAB>説明<TAB>端末<TAB>分類 の TSV
        |# ⚠ pipe menu はメニューを開くたびに実行される。重い処理を足さないこと。
        |
        |LANGSFX="$nameSuffix"
        |CATLABELS="$catLabels"
        |TAB=${d}(printf '\t')
        |
        |# .desktop を読んで TSV にする。⚠ awk は POSIX の範囲で書くこと (busybox awk 対応)。
        |scan_desktop() {
        |  set --
        |  for dir in "${d}{HOME:-/root}/.local/share/applications" \
        |             /usr/local/share/applications /usr/share/applications; do
        |    [ -d "${d}dir" ] || continue
        |    for f in "${d}dir"/*.desktop; do
        |      [ -f "${d}f" ] && set -- "${d}@" "${d}f"
        |    done
        |  done
        |  [ ${d}# -eq 0 ] && return 0
        |  awk -v NAMEL="Name[${d}LANGSFX]" -v COMMENTL="Comment[${d}LANGSFX]" '
        |  function reset() {
        |    type_ = ""; name = ""; namel = ""; comment = ""; commentl = ""
        |    ex = ""; tryex = ""; nodisp = ""; hidden = ""; term = ""; cats = ""
        |  }
        |  function base(p,   n, a) { n = split(p, a, "/"); return a[n] }
        |  function lc(s) { return tolower(s) }
        |  function category(s) {
        |    if (s ~ /AudioVideo|Audio|Video|Music|Player/) return "AudioVideo"
        |    if (s ~ /Development|IDE|TextEditor/)          return "Development"
        |    if (s ~ /Graphics|Photography|Viewer/)         return "Graphics"
        |    if (s ~ /Game/)                                return "Game"
        |    if (s ~ /Network|WebBrowser|Email/)            return "Network"
        |    if (s ~ /Office|Spreadsheet|WordProcessor/)    return "Office"
        |    if (s ~ /Settings|System|Security/)            return "System"
        |    if (s ~ /Utility|Accessories|FileManager|FileTools/) return "Utility"
        |    return "Other"
        |  }
        |  function emit(   n, c, cmd, id) {
        |    if (type_ != "Application") return
        |    if (lc(nodisp) == "true" || lc(hidden) == "true") return
        |    if (ex == "") return
        |    n = (namel != "" ? namel : name)
        |    if (n == "") return
        |    id = base(curfile)
        |    if (id in seen) return
        |    seen[id] = 1
        |    c = (commentl != "" ? commentl : comment)
        |    cmd = ex
        |    # フィールドコードを外す。⚠ %% は本物の % なので、先に印へ逃がして最後に戻す。
        |    #    印に制御文字 (\001 等) を使わない: 8 進エスケープの解釈が awk 実装で揺れる。
        |    gsub(/%%/, "@@Z2PCT@@", cmd)
        |    gsub(/%[fFuUdDnNickvm]/, "", cmd)
        |    gsub(/@@Z2PCT@@/, "%", cmd)
        |    sub(/^[ \t]+/, "", cmd); sub(/[ \t]+${d}/, "", cmd)
        |    if (cmd == "") return
        |    printf "%s\t%s\t%s\t%s\t%s\n", n, cmd, c, (lc(term) == "true" ? "1" : "0"), category(cats)
        |  }
        |  FNR == 1 { if (started) emit(); started = 1; insec = 0; curfile = FILENAME; reset() }
        |  /^[ \t]*[#;]/ { next }
        |  /^[ \t]*\[/ {
        |    sub(/^[ \t]+/, "")
        |    insec = (${d}0 ~ /^\[Desktop Entry\]/) ? 1 : 0
        |    next
        |  }
        |  insec {
        |    eq = index(${d}0, "=")
        |    if (eq == 0) next
        |    k = substr(${d}0, 1, eq - 1); v = substr(${d}0, eq + 1)
        |    sub(/[ \t]+${d}/, "", k); sub(/^[ \t]+/, "", v)
        |    if      (k == "Type")       type_ = v
        |    else if (k == "Name")       name = v
        |    else if (k == NAMEL)        namel = v
        |    else if (k == "Comment")    comment = v
        |    else if (k == COMMENTL)     commentl = v
        |    else if (k == "Exec")       ex = v
        |    else if (k == "TryExec")    tryex = v
        |    else if (k == "NoDisplay")  nodisp = v
        |    else if (k == "Hidden")     hidden = v
        |    else if (k == "Terminal")   term = v
        |    else if (k == "Categories") cats = v
        |  }
        |  END { if (started) emit() }
        |  ' "${d}@"
        |}
        |
        |# TSV から「実体が PATH に無いもの」と Terminal=true を落として名前順に並べる。
        |#
        |# ⛔ **read の IFS に TAB を渡して列へ割らないこと。** TAB は IFS の「空白」なので
        |#    **連続した TAB が 1 つに畳まれる**。Comment= の無い .desktop (かなり多い) で列が
        |#    1 つずつ手前へずれ、説明の欄に端末フラグの "0" が出る。行はそのまま持ち、
        |#    判定に要る 2 列だけを前から剥がして取る。
        |list_apps() {
        |  scan_desktop | while IFS= read -r line; do
        |    name=${d}{line%%"${d}TAB"*}
        |    rest=${d}{line#*"${d}TAB"}   # 名前を落とす
        |    cmd=${d}{rest%%"${d}TAB"*}   # コマンド
        |    [ -n "${d}cmd" ] || continue
        |    after_cmd=${d}{rest#*"${d}TAB"}
        |    rest=${d}{rest#*"${d}TAB"}   # 説明へ
        |    rest=${d}{rest#*"${d}TAB"}   # 端末フラグへ
        |    term=${d}{rest%%"${d}TAB"*}
        |    [ "${d}term" = "1" ] && continue
        |    # Exec の先頭語が実体。`env A=B app` のような形もそのまま command -v で見る。
        |    bin=${d}{cmd%% *}
        |    command -v "${d}bin" >/dev/null 2>&1 || continue
        |    # Alpine の xterm は引数無しだと core font `fixed` を要求し、既存 GUI 環境に
        |    # font-misc-misc が無い場合は窓を作る前に終了する。以前の自動起動と同じく Xft の
        |    # monospace を明示し、☰ と openbox のどちらから起こしても同じにする。
        |    if [ "${d}{bin##*/}" = "xterm" ]; then
        |      case " ${d}cmd " in
        |        *" -fa "*|*" -fn "*) : ;;
        |        *) cmd="${d}bin -fa monospace -fs 11${d}{cmd#"${d}bin"}"
        |           line="${d}name${d}TAB${d}cmd${d}TAB${d}after_cmd" ;;
        |      esac
        |    fi
        |    printf '%s\n' "${d}line"
        |  done | sort -f
        |}
        |
        |# openbox の pipe menu。項目が多いときだけ分類のサブメニューに分ける
        |# (少ないうちから階層にすると、指で辿る手数が増えるだけなので)。
        |menu_xml() {
        |  list_apps | awk -F'\t' -v none="$noApps" -v catlabels="${d}CATLABELS" '
        |  function esc(s) {
        |    gsub(/&/, "\\&amp;", s); gsub(/</, "\\&lt;", s)
        |    gsub(/>/, "\\&gt;", s); gsub(/"/, "\\&quot;", s)
        |    return s
        |  }
        |  function item(i, ind,   e) {
        |    print ind "<item label=\"" esc(nm[i]) "\"><action name=\"Execute\"><execute>" esc(cmd[i]) "</execute></action></item>"
        |  }
        |  BEGIN {
        |    nl = split(catlabels, pairs, ";")
        |    for (p = 1; p <= nl; p++) {
        |      eq = index(pairs[p], "=")
        |      if (eq > 0) label[substr(pairs[p], 1, eq - 1)] = substr(pairs[p], eq + 1)
        |    }
        |  }
        |  { n++; nm[n] = ${d}1; cmd[n] = ${d}2; trm[n] = ${d}4; cat[n] = ${d}5; cnt[${d}5]++ }
        |  END {
        |    print "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        |    print "<openbox_pipe_menu>"
        |    if (n == 0) {
        |      # 空でも item を 1 つ出す。openbox は中身が無い pipe menu を「壊れている」と扱う。
        |      print "  <item label=\"" esc(none) "\"><action name=\"Execute\"><execute>true</execute></action></item>"
        |    } else if (n <= 20) {
        |      for (i = 1; i <= n; i++) item(i, "  ")
        |    } else {
        |      no = split("AudioVideo Development Graphics Game Network Office System Utility Other", order, " ")
        |      for (c = 1; c <= no; c++) {
        |        k = order[c]
        |        if (cnt[k] == 0) continue
        |        print "  <menu id=\"z2-cat-" k "\" label=\"" esc(label[k] != "" ? label[k] : k) "\">"
        |        for (i = 1; i <= n; i++) if (cat[i] == k) item(i, "    ")
        |        print "  </menu>"
        |      }
        |    }
        |    print "</openbox_pipe_menu>"
        |  }
        |  '
        |}
        |
        |case "${d}{1:-menu}" in
        |  menu) menu_xml ;;
        |  list) list_apps ;;
        |  *)    echo "$usage" >&2; exit 1 ;;
        |esac
    """.trimMargin() + "\n"
}
