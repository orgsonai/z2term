package com.zerotoship.z2term.proot

/**
 * マクロのサンプル同梱と管理 CLI (`z2-macro`)。
 *
 * マクロ (トリガー → 判断 → アクション) は `docs/ja/MACRO-GUIDE.md` に書き方があるが、
 * **最初の 1 本を白紙から書くのが一番の壁**だった。動くサンプルを rootfs に同梱し、
 * `z2-macro install <名前>` で `~/.z2term/macros/` へ展開できるようにする。
 *
 * サンプル本体は [z2MacroSamples] が返し、[ProotLauncher] が
 * `/usr/local/share/z2term/macros/` へ書き出す。`z2-macro` はそこから HOME へコピーするだけ。
 * ユーザーが編集した後に上書きされないよう、install は既存ファイルを**上書きしない**
 * (`-f` を付けたときだけ上書き)。
 */

/** サンプルマクロ (ファイル名 → 中身)。コメントは [lang] に追従する。 */
fun z2MacroSamples(lang: String): Map<String, String> {
    val ja = lang != "en"
    val d = "${'$'}"

    // --- 1. 入門: イベントに反応する ---
    val watchBasic = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# watch-basic.sh — 入門用マクロ。イベントを見て反応する。")
            appendLine("# 準備: ⚙設定 →「システムイベント検知」を ON")
            appendLine("# 常駐: ⚙設定 → 常駐サーバー に  sh ~/.z2term/macros/watch-basic.sh  を登録")
        } else {
            appendLine("# watch-basic.sh — starter macro. React to system events.")
            appendLine("# Setup: Settings -> \"System event detection\" ON")
            appendLine("# Resident: register  sh ~/.z2term/macros/watch-basic.sh  under Settings -> Resident servers")
        }
        appendLine("EVENTS=~/.z2term/events.jsonl")
        appendLine("[ -f \"${d}EVENTS\" ] || : > \"${d}EVENTS\"")
        appendLine("tail -n0 -F \"${d}EVENTS\" 2>/dev/null | while IFS= read -r line; do")
        appendLine("  ev=${d}(printf '%s' \"${d}line\" | sed -n 's/.*\"event\":\"\\([^\"]*\\)\".*/\\1/p')")
        appendLine("  case \"${d}ev\" in")
        if (ja) {
            appendLine("    power_connected)   z2-toast \"充電を開始しました\" ;;")
            appendLine("    power_disconnected) z2-toast \"充電をやめました\" ;;")
        } else {
            appendLine("    power_connected)   z2-toast \"Charging started\" ;;")
            appendLine("    power_disconnected) z2-toast \"Charging stopped\" ;;")
        }
        appendLine("    headset_plugged)   z2-media play ;;")
        appendLine("    headset_unplugged) z2-media pause ;;")
        appendLine("  esac")
        appendLine("done")
    }

    // --- 2. z2-state を使う: 状況を見てから動く ---
    val batteryAlert = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# battery-alert.sh — 電池が減ったら知らせる。ただし今の状況を見て出し分ける。")
            appendLine("# z2-state で「画面が点いているか」を見て、点いていればトースト、消えていれば通知。")
            appendLine("# 準備: ⚙設定 →「システムイベント検知」を ON")
        } else {
            appendLine("# battery-alert.sh — warn on low battery, but adapt to the current state.")
            appendLine("# Uses z2-state to check whether the screen is on: toast if it is, notification if not.")
            appendLine("# Setup: Settings -> \"System event detection\" ON")
        }
        appendLine("EVENTS=~/.z2term/events.jsonl")
        appendLine("[ -f \"${d}EVENTS\" ] || : > \"${d}EVENTS\"")
        appendLine("tail -n0 -F \"${d}EVENTS\" 2>/dev/null | while IFS= read -r line; do")
        appendLine("  ev=${d}(printf '%s' \"${d}line\" | sed -n 's/.*\"event\":\"\\([^\"]*\\)\".*/\\1/p')")
        appendLine("  case \"${d}ev\" in battery_low|battery_level) ;; *) continue ;; esac")
        appendLine("  level=${d}(z2-state level)")
        if (ja) {
            appendLine("  # 充電中なら知らせない (勝手に減っているわけではないので)")
        } else {
            appendLine("  # Say nothing while charging (it is not actually draining)")
        }
        appendLine("  [ \"${d}(z2-state charging)\" = \"true\" ] && continue")
        appendLine("  [ \"${d}level\" -le 20 ] 2>/dev/null || continue")
        appendLine("  if [ \"${d}(z2-state screen)\" = \"on\" ]; then")
        if (ja) {
            appendLine("    z2-toast \"電池 ${d}{level}%\"")
            appendLine("  else")
            appendLine("    z2-notify -h \"電池注意\" \"残り ${d}{level}% です\"")
        } else {
            appendLine("    z2-toast \"Battery ${d}{level}%\"")
            appendLine("  else")
            appendLine("    z2-notify -h \"Low battery\" \"${d}{level}% left\"")
        }
        appendLine("  fi")
        appendLine("done")
    }

    // --- 3. z2-alarm を使う: 時刻で動く ---
    val dailyReport = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# daily-report.sh — 毎朝きまった時刻に電池と接続状態を読み上げる。")
            appendLine("# 使い方: まず時刻トリガーを 1 回だけ仕掛ける")
            appendLine("#   z2-alarm daily 07:00 morning")
            appendLine("# そのうえで、このスクリプトを常駐させる")
            appendLine("#   ⚙設定 → 常駐サーバー に  sh ~/.z2term/macros/daily-report.sh  を登録")
            appendLine("# cron と違い Doze 中でも起きる (省電力のため数分ずれることはある)。")
        } else {
            appendLine("# daily-report.sh — read out battery and connection every morning.")
            appendLine("# Usage: set the time trigger once")
            appendLine("#   z2-alarm daily 07:00 morning")
            appendLine("# then keep this script resident")
            appendLine("#   register  sh ~/.z2term/macros/daily-report.sh  under Settings -> Resident servers")
            appendLine("# Unlike cron this also fires during Doze (it may be a few minutes late).")
        }
        appendLine("EVENTS=~/.z2term/events.jsonl")
        appendLine("[ -f \"${d}EVENTS\" ] || : > \"${d}EVENTS\"")
        appendLine("tail -n0 -F \"${d}EVENTS\" 2>/dev/null | while IFS= read -r line; do")
        appendLine("  ev=${d}(printf '%s' \"${d}line\" | sed -n 's/.*\"event\":\"\\([^\"]*\\)\".*/\\1/p')")
        appendLine("  name=${d}(printf '%s' \"${d}line\" | sed -n 's/.*\"name\":\"\\([^\"]*\\)\".*/\\1/p')")
        appendLine("  [ \"${d}ev\" = \"alarm\" ] || continue")
        if (ja) {
            appendLine("  # z2-alarm に付けた名前で用途を分ける (morning 以外は無視)")
        } else {
            appendLine("  # Branch on the name given to z2-alarm (ignore anything but morning)")
        }
        appendLine("  [ \"${d}name\" = \"morning\" ] || continue")
        appendLine("  level=${d}(z2-state level)")
        appendLine("  if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=mobile; fi")
        if (ja) {
            appendLine("  z2-say \"おはようございます。電池は ${d}{level} パーセント、接続は ${d}{net} です\"")
        } else {
            appendLine("  z2-say \"Good morning. Battery ${d}{level} percent, network ${d}{net}\"")
        }
        appendLine("done")
    }

    // --- 4. 実用: 通知内のワンタイムコードを自動コピー (MACRO-GUIDE 5-5 と同じ内容) ---
    val otpClip = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# otp-clip.sh — 通知に含まれるワンタイムコード(4〜8桁)を自動でクリップボードへ入れ、")
            appendLine("# TTL 秒後に「値が変わっていなければ」自動で消す。")
            appendLine("# 準備: ⚙設定 →「通知検知」ON ＋ OS の「通知アクセス」許可")
            appendLine("# 常駐: ⚙設定 → 常駐サーバー に  sh ~/.z2term/macros/otp-clip.sh  を登録")
        } else {
            appendLine("# otp-clip.sh — copy a one-time code (4-8 digits) out of notifications, then")
            appendLine("# clear it after TTL seconds if the clipboard still holds that same value.")
            appendLine("# Setup: Settings -> \"Notification detection\" ON + grant OS notification access")
            appendLine("# Resident: register  sh ~/.z2term/macros/otp-clip.sh  under Settings -> Resident servers")
        }
        appendLine("TTL=60")
        appendLine("KEYWORDS='認証|確認|ワンタイム|コード|パスワード|code|otp|verification|verify|one[- ]?time'")
        appendLine("NOTIF=~/.z2term/notifications.jsonl")
        appendLine("[ -f \"${d}NOTIF\" ] || : > \"${d}NOTIF\"")
        appendLine("schedule_clear() {")
        appendLine("  code=${d}1")
        appendLine("  ( sleep \"${d}TTL\"")
        appendLine("    cur=${d}(z2-clip get 2>/dev/null)")
        appendLine("    if [ \"${d}cur\" = \"${d}code\" ]; then")
        appendLine("      z2-clip set \"\"")
        if (ja) {
            appendLine("      z2-toast \"コピーしたコードをクリアしました\"")
        } else {
            appendLine("      z2-toast \"Cleared the copied code\"")
        }
        appendLine("    fi")
        appendLine("  ) &")
        appendLine("}")
        appendLine("tail -n0 -F \"${d}NOTIF\" 2>/dev/null | while IFS= read -r line; do")
        appendLine("  [ -z \"${d}line\" ] && continue")
        appendLine("  case \"${d}line\" in")
        appendLine("    '{'*)")
        appendLine("      t=${d}(printf '%s' \"${d}line\" | sed -n 's/.*\"title\":\"\\([^\"]*\\)\".*/\\1/p')")
        appendLine("      x=${d}(printf '%s' \"${d}line\" | sed -n 's/.*\"text\":\"\\([^\"]*\\)\".*/\\1/p')")
        appendLine("      body=\"${d}t ${d}x\" ;;")
        appendLine("    *) body=${d}(printf '%s' \"${d}line\" | sed 's/^\\[[^]]*\\][[:space:]]*//') ;;")
        appendLine("  esac")
        appendLine("  [ -z \"${d}body\" ] && continue")
        appendLine("  printf '%s' \"${d}body\" | grep -Eiq \"${d}KEYWORDS\" || continue")
        appendLine("  code=${d}(printf '%s' \"${d}body\" | tr -d ' -' | grep -oE '[0-9]{4,8}' | head -n1)")
        appendLine("  [ -z \"${d}code\" ] && continue")
        appendLine("  z2-clip set \"${d}code\"")
        if (ja) {
            appendLine("  z2-toast \"コードをコピー: ${d}{code}\"")
        } else {
            appendLine("  z2-toast \"Copied code: ${d}{code}\"")
        }
        appendLine("  schedule_clear \"${d}code\"")
        appendLine("done")
    }

    return linkedMapOf(
        "watch-basic.sh" to watchBasic,
        "battery-alert.sh" to batteryAlert,
        "daily-report.sh" to dailyReport,
        "otp-clip.sh" to otpClip,
    )
}

/** `z2-macro` CLI 本体。同梱サンプルの一覧 / 導入 / 表示 / 実行。 */
fun z2MacroScript(lang: String): String {
    val ja = lang != "en"
    val d = "${'$'}"
    val src = "/usr/local/share/z2term/macros"

    val usage = if (ja) {
        listOf(
            "usage: z2-macro <サブコマンド>",
            "  list                同梱サンプルの一覧",
            "  install <名前|all>  ~/.z2term/macros/ へコピー (既存は上書きしない。-f で上書き)",
            "  show <名前>         中身を表示",
            "  run <名前>          その場で実行 (Ctrl-C で止める)",
            "  dir                 マクロの置き場所を表示",
        )
    } else {
        listOf(
            "usage: z2-macro <subcommand>",
            "  list                list bundled samples",
            "  install <name|all>  copy into ~/.z2term/macros/ (never overwrites; -f to force)",
            "  show <name>         print the script",
            "  run <name>          run it here (Ctrl-C to stop)",
            "  dir                 print where macros live",
        )
    }

    val msgInstalled = if (ja) "導入しました:" else "installed:"
    val msgExists = if (ja) "既にあります (上書きするには -f):" else "already exists (use -f to overwrite):"
    val msgNotFound = if (ja) "そんなサンプルはありません:" else "no such sample:"
    val msgHintResident = if (ja)
        "常駐させるには ⚙設定 → 常駐サーバー に次を登録してください:"
    else
        "To keep it running, register this under Settings -> Resident servers:"

    return """
        |#!/bin/sh
        |# z2term マクロ管理 (同梱サンプルの導入)。マクロの書き方は docs の MACRO-GUIDE を参照。
        |SRC=$src
        |DEST=${d}HOME/.z2term/macros
        |usage() {
        |${usage.joinToString("\n") { "|  echo '${it.replace("'", "'\\''")}' >&2" }}
        |  exit 1
        |}
        |# -f/--force はどこに書かれていてもよいよう、先に引数列から抜き出す
        |# (`install -f all` と `install all -f` のどちらでも同じ意味になる)。
        |force=0
        |rest=""
        |for a in "${d}@"; do
        |  case "${d}a" in
        |    -f|--force) force=1 ;;
        |    *) rest="${d}rest ${d}a" ;;
        |  esac
        |done
        |# 名前にスペースは入らない前提 (サンプルのファイル名) なので単純な再セットでよい。
        |set -- ${d}rest
        |
        |case "${d}1" in
        |  list)
        |    [ -d "${d}SRC" ] || { echo "no samples bundled" >&2; exit 1; }
        |    for f in "${d}SRC"/*.sh; do
        |      [ -e "${d}f" ] || continue
        |      name=${d}(basename "${d}f")
        |      # 2 行目のコメント (= 説明) を要約として見せる
        |      desc=${d}(sed -n '2s/^# *//p' "${d}f")
        |      printf '%-18s %s\n' "${d}name" "${d}desc"
        |    done ;;
        |  install)
        |    [ ${d}# -ge 2 ] || usage
        |    mkdir -p "${d}DEST" || exit 1
        |    if [ "${d}2" = "all" ]; then set -- install ${d}(cd "${d}SRC" && ls *.sh 2>/dev/null); fi
        |    shift
        |    for name in "${d}@"; do
        |      case "${d}name" in *.sh) ;; *) name="${d}name.sh" ;; esac
        |      if [ ! -f "${d}SRC/${d}name" ]; then echo "$msgNotFound ${d}name" >&2; continue; fi
        |      if [ -f "${d}DEST/${d}name" ] && [ "${d}force" != "1" ]; then
        |        echo "$msgExists ${d}DEST/${d}name" >&2; continue
        |      fi
        |      cp "${d}SRC/${d}name" "${d}DEST/${d}name" && chmod +x "${d}DEST/${d}name" || continue
        |      echo "$msgInstalled ${d}DEST/${d}name"
        |      echo "$msgHintResident"
        |      echo "  sh ${d}DEST/${d}name"
        |    done ;;
        |  show)
        |    [ ${d}# -ge 2 ] || usage
        |    name="${d}2"; case "${d}name" in *.sh) ;; *) name="${d}name.sh" ;; esac
        |    [ -f "${d}SRC/${d}name" ] || { echo "$msgNotFound ${d}name" >&2; exit 1; }
        |    cat "${d}SRC/${d}name" ;;
        |  run)
        |    [ ${d}# -ge 2 ] || usage
        |    name="${d}2"; case "${d}name" in *.sh) ;; *) name="${d}name.sh" ;; esac
        |    if [ -f "${d}DEST/${d}name" ]; then exec sh "${d}DEST/${d}name"; fi
        |    [ -f "${d}SRC/${d}name" ] || { echo "$msgNotFound ${d}name" >&2; exit 1; }
        |    exec sh "${d}SRC/${d}name" ;;
        |  dir)
        |    echo "${d}DEST" ;;
        |  *) usage ;;
        |esac
    """.trimMargin() + "\n"
}
