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
            appendLine("# ログ形式・追記方向のどちらにも依存しない (差分を読み、イベント名で照合する)。")
            appendLine("# 準備: ⚙設定 →「システムイベント検知」を ON")
            appendLine("# 常駐: ⚙設定 → 常駐サーバー に  sh ~/.z2term/macros/watch-basic.sh  を登録")
        } else {
            appendLine("# watch-basic.sh — starter macro. React to system events.")
            appendLine("# Independent of log format and write direction (diffs the log, matches on event names).")
            appendLine("# Setup: Settings -> \"System event detection\" ON")
            appendLine("# Resident: register  sh ~/.z2term/macros/watch-basic.sh  under Settings -> Resident servers")
        }
        append(diffSetup(d, ja, "events.jsonl", "watch-basic"))
        appendLine()
        if (ja) {
            appendLine("# 新着の塊を 1 行ずつ見て、イベント名が含まれていたら反応する。")
            appendLine("# JSON の \"event\":\"...\" もテンプレートの {event} も、名前がそのまま出るので同じ書き方で拾える。")
        } else {
            appendLine("# Walk the new chunk line by line and react when an event name appears.")
            appendLine("# The name shows up verbatim in both JSON (\"event\":\"...\") and a {event} template.")
        }
        appendLine("handle() {")
        appendLine("  printf '%s\\n' \"${d}1\" | while IFS= read -r rec; do")
        appendLine("    case \"${d}rec\" in")
        if (ja) {
            appendLine("      *power_connected*)    z2-toast \"充電を開始しました\" ;;")
            appendLine("      *power_disconnected*) z2-toast \"充電をやめました\" ;;")
        } else {
            appendLine("      *power_connected*)    z2-toast \"Charging started\" ;;")
            appendLine("      *power_disconnected*) z2-toast \"Charging stopped\" ;;")
        }
        appendLine("      *headset_plugged*)    z2-media play ;;")
        appendLine("      *headset_unplugged*)  z2-media pause ;;")
        appendLine("    esac")
        appendLine("  done")
        appendLine("}")
        append(diffLoop(d, ja, "handle"))
    }

    // --- 2. z2-state を使う: 状況を見てから動く (z2-when が起こす「使い切り」の形) ---
    val batteryAlert = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# battery-alert.sh — 電池が減ったら知らせる。ただし今の状況を見て出し分ける。")
            appendLine("# z2-state で「画面が点いているか」を見て、点いていればトースト、消えていれば通知。")
            appendLine("# 準備: ⚙設定 →「システムイベント検知」を ON")
            appendLine("# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh")
            appendLine()
            appendLine("# 残量は z2-when が渡してくれる。手で試すときのために z2-state も見ておく。")
        } else {
            appendLine("# battery-alert.sh — warn on low battery, but adapt to the current state.")
            appendLine("# Uses z2-state to check whether the screen is on: toast if it is, notification if not.")
            appendLine("# Setup: Settings -> \"System event detection\" ON")
            appendLine("# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh")
            appendLine()
            appendLine("# z2-when hands the level over; fall back to z2-state so this also runs by hand.")
        }
        appendLine("level=${d}{Z2_WHEN_LEVEL:-${d}(z2-state level)}")
        appendLine()
        if (ja) {
            appendLine("# 充電中なら知らせない (勝手に減っているわけではないので)")
        } else {
            appendLine("# Say nothing while charging (it is not actually draining)")
        }
        appendLine("[ \"${d}(z2-state charging)\" = \"true\" ] && exit 0")
        appendLine()
        appendLine("if [ \"${d}(z2-state screen)\" = \"on\" ]; then")
        if (ja) {
            appendLine("  z2-toast \"電池 ${d}{level}%\"")
            appendLine("else")
            appendLine("  z2-notify -h \"電池注意\" \"残り ${d}{level}% です\"")
        } else {
            appendLine("  z2-toast \"Battery ${d}{level}%\"")
            appendLine("else")
            appendLine("  z2-notify -h \"Low battery\" \"${d}{level}% left\"")
        }
        appendLine("fi")
    }

    // --- 3. 時刻で動く (z2-when time: が OS のアラームで起こす) ---
    val dailyReport = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# daily-report.sh — 毎朝きまった時刻に電池と接続状態を読み上げる。")
            appendLine("# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh")
            appendLine("# 時刻は OS のアラームで起こすので Doze 中でも動く (省電力のため数分ずれることはある)。")
            appendLine("# 検知の ON/OFF には依存しない。")
        } else {
            appendLine("# daily-report.sh — read out battery and connection every morning.")
            appendLine("# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh")
            appendLine("# The OS alarm wakes it, so it fires during Doze too (may be a few minutes late).")
            appendLine("# Does not depend on the detection switches.")
        }
        appendLine()
        appendLine("level=${d}(z2-state level)")
        if (ja) {
            appendLine("if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=モバイル; fi")
            appendLine("z2-say \"おはようございます。電池は ${d}{level} パーセント、接続は ${d}{net} です\"")
        } else {
            appendLine("if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=mobile; fi")
            appendLine("z2-say \"Good morning. Battery ${d}{level} percent, network ${d}{net}\"")
        }
    }

    // --- 4. 実用: 通知内のワンタイムコードを自動コピー (MACRO-GUIDE 5-6 と同じ内容) ---
    val otpClip = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# otp-clip.sh — 通知に含まれるワンタイムコードを自動でクリップボードへ入れ、")
            appendLine("# TTL 秒後に「値が変わっていなければ」自動で消す。")
            appendLine("# 準備: ⚙設定 →「通知検知」ON ＋ OS の「通知アクセス」許可")
            appendLine("# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh")
            appendLine("# ⚠ Android 15+ は機微通知のコードを伏せ字にすることがある。確実に取るなら SMS 版")
            appendLine("#    (otp-sms.sh) を使う — SMS 本文は伏せ字にならない。")
        } else {
            appendLine("# otp-clip.sh — copy a one-time code out of notifications, then clear it after")
            appendLine("# TTL seconds if the clipboard still holds that same value.")
            appendLine("# Setup: Settings -> \"Notification detection\" ON + grant OS notification access")
            appendLine("# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh")
            appendLine("# ⚠ Android 15+ may redact codes in sensitive notifications. For a reliable path use")
            appendLine("#    the SMS variant (otp-sms.sh) — SMS bodies are never redacted.")
        }
        append(otpWhenBody(d, ja))
    }

    // --- 5. 実用: SMS 内のワンタイムコードを自動コピー (通知でなく SMS を直読み = 伏せ字を迂回) ---
    val otpSms = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# otp-sms.sh — 受信 SMS に含まれるワンタイムコードを自動でクリップボードへ入れ、")
            appendLine("# TTL 秒後に「値が変わっていなければ」自動で消す。otp-clip.sh の SMS 版。")
            appendLine("# 通知と違い SMS 本文は機微通知の伏せ字(Android 15+)やロック状態の影響を受けないので確実。")
            appendLine("# 準備: ⚙設定 →「SMS 検知」ON ＋ OS の SMS 受信許可")
            appendLine("# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh")
        } else {
            appendLine("# otp-sms.sh — copy a one-time code out of incoming SMS, then clear it after")
            appendLine("# TTL seconds if the clipboard still holds that same value.")
            appendLine("# The SMS variant of otp-clip.sh. Unlike notifications, SMS bodies are never")
            appendLine("# redacted by sensitive-notification protection (Android 15+) and work while locked.")
            appendLine("# Setup: Settings -> \"SMS detection\" ON + grant the OS SMS permission")
            appendLine("# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh")
        }
        append(otpWhenBody(d, ja))
    }

    // --- 6. 実用: フィード購読 (時刻トリガーで 1 回だけ走る「使い切り」の形) ---
    val rss = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# rss.sh — フィードを見に行って、新着だけを通知とテキストに残す。")
            appendLine("# 他のサンプルと違い**常駐しない**。時刻トリガーで 1 回走って終わる形の見本でもある。")
        } else {
            appendLine("# rss.sh — poll feeds and keep only what is new, as a notification and as text.")
            appendLine("# Unlike the other samples this one does **not** stay resident: it is the")
            appendLine("# 'run once from a time trigger and exit' shape.")
        }
        append(rssBody(d, ja))
    }

    // --- 7. 実用: 集めた記事を 1 本ずつ開く (状態ウィジェットのボタンから叩く用) ---
    val rssOpen = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# rss-open.sh — rss.sh が集めた記事を、新しいものから 1 本ずつブラウザで開く。")
            appendLine("# 状態ウィジェットのマクロボタンに出るので、タップするたびに次の 1 本が開く。")
        } else {
            appendLine("# rss-open.sh — open what rss.sh collected, newest first, one article per tap.")
            appendLine("# It shows up as a macro button on the status widget, so each tap opens the next one.")
        }
        append(rssOpenBody(d, ja))
    }

    // --- 8. 実用: 通知でリマインド (単発 = z2-alarm / 繰り返し = z2-when time: の使い分けの見本) ---
    val remind = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# remind.sh — 通知でリマインドする。単発 (30分後・18:30) と繰り返し (毎日・平日・毎週)。")
            appendLine("# アプリを閉じていても鳴る (OS のアラームで起こされるため。「検知」ON も不要)。")
            appendLine("# 通知のボタンからスヌーズできる。**アプリ側に予定機能を作らないための見本**でもある。")
        } else {
            appendLine("# remind.sh — remind you with a notification: one-shot (in 30m, at 18:30) or")
            appendLine("# repeating (daily / weekdays / weekly). Fires with the app closed (an OS alarm")
            appendLine("# wakes it; no detection switch needed). Snooze from the notification buttons.")
        }
        append(remindBody(d, ja))
    }

    return linkedMapOf(
        "watch-basic.sh" to watchBasic,
        "battery-alert.sh" to batteryAlert,
        "daily-report.sh" to dailyReport,
        "otp-clip.sh" to otpClip,
        "otp-sms.sh" to otpSms,
        "remind.sh" to remind,
        "rss.sh" to rss,
        "rss-open.sh" to rssOpen,
    )
}

/**
 * フィード購読サンプルの本体。
 *
 * **アプリ側に RSS 機能を作らないための見本**でもある。定期実行 (`z2-when time:`)・通知
 * (`z2-notify -b`)・ブラウザで開く (`z2-open`)・ライブ tail ウィジェットという既存の
 * 汎用部品だけで購読が成立することを示す。用途限定の画面を 1 枚も増やさずに済む。
 *
 * 設計上の要点:
 *  - **既読は「見た行を引き算する」**。`z2scan` のベースライン差分と同じやり方で、
 *    フィード側の日付や順序を信用しない (どちらも当てにならない)。
 *  - **解析は python3 に任せる**。RSS と Atom は形が揺れるので `grep`/`sed` で切ると
 *    フィードを 1 本増やすたびに壊れる。標準ライブラリだけで足りるので pip は要らない。
 *  - **1 本落ちても他は続ける**。取得失敗で全体が止まると、電波の悪い日に何も来なくなる。
 *  - `latest.txt` の行に **URL を残す**。ライブ tail ウィジェットは行に URL があれば
 *    タップでそれを開くので、一覧から直接読める。
 */
private fun rssBody(d: String, ja: Boolean): String {
    val head = if (ja) """
#
# 準備:
#   1) 読みたい URL を 1 行 1 本で書く:  ~/.z2term/rss/feeds.txt
#   2) 定期実行を仕掛ける (30 分ごと):
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) 通知の「開く」で最新の 1 本をブラウザへ (任意):
#        z2-when event:notify_action run '[ "${d}Z2_WHEN_EVENT_NAME" = rss ] && z2-open "${d}(head -1 ~/.z2term/rss/new.txt | cut -f1)"'
#   4) ウィジェット (任意): ライブ tail で ~/.z2term/rss/latest.txt を「先頭 (head)」表示。
#      行に URL が入っているので、タップするとその記事が開く。
#
# 必要: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
# 電池: 取りに行くほど食う。30 分より短くしないこと。
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""" else """
#
# Setup:
#   1) One feed URL per line in:  ~/.z2term/rss/feeds.txt
#   2) Poll every 30 minutes:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) Optional - let the notification's button open the newest item:
#        z2-when event:notify_action run '[ "${d}Z2_WHEN_EVENT_NAME" = rss ] && z2-open "${d}(head -1 ~/.z2term/rss/new.txt | cut -f1)"'
#   4) Optional - widget: point a live tail at ~/.z2term/rss/latest.txt in "start (head)" mode.
#      Each line carries its URL, so tapping a line opens that article.
#
# Needs: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
# Battery: the more often you poll the more it costs. Do not go below 30 minutes.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
"""

    val cKeep = if (ja) "seen/latest に残す行数の上限" else "max lines kept in seen/latest"
    val cNoPy = if (ja) "python3 が要ります (apk add python3 など)" else "python3 is required (e.g. apk add python3)"
    val cWriteFeeds = if (ja) "フィードの URL を 1 行に 1 本書いてください:" else "Write one feed URL per line in:"
    val cFeedsHint = if (ja) "# 1 行に 1 本、フィードの URL を書く (# で始まる行は無視)" else "# One feed URL per line (lines starting with # are ignored)"
    val cSkipFail = if (ja) "取得や解析に失敗した 1 本は黙って飛ばす (他のフィードは続ける)" else "silently skip a feed that fails to fetch or parse (the rest continue)"
    val cDiff = if (ja) {
        "# 既読を引いて新着だけにする (z2scan のベースライン差分と同じやり方)。\n# フィードの日付や並び順は当てにしない — どちらも当てにならない。"
    } else {
        "# Subtract what we have seen to leave only what is new (same trick as z2scan's baseline diff).\n# Feed dates and ordering are not trusted; neither is reliable."
    }
    val cPrepend = if (ja) {
        "# 新着が上に来るように積む (ウィジェットの「先頭 (head)」表示でそのまま読める)。"
    } else {
        "# Stack newest-first so the widget's \"start (head)\" mode reads correctly."
    }
    val cNotify = if (ja) "新着 %s 件" else "%s new"
    val cOpen = if (ja) "開く" else "Open"
    val cListNone = if (ja) "まだ何も集めていません。まず引数なしで実行してください。" else "Nothing collected yet — run it once with no arguments first."
    val cListHint = if (ja) "一覧: rss.sh list [件数]" else "List them with: rss.sh list [count]"
    val cListDoc = if (ja) {
        "# 集めた記事を読みやすく並べて出す。色は使わない (ウィジェットやファイル表示と見え方を揃える)。\n" +
            "# 端末のときは OSC 8 で**題名そのものをリンク**にし、URL の行を並べない — 長い URL は\n" +
            "# 折り返して題名と混ざり、一覧として読めなくなるため。端末以外 (パイプ・リダイレクト) では\n" +
            "# エスケープが邪魔なので素のテキストに落とし、URL も見えるようにする。\n" +
            "# latest.txt は 1 行 1 記事の素データのままにしておく (ウィジェットの tail と rss-open.sh が読む)。"
    } else {
        "# Print what was collected as a readable list (no colour — it should look the same as the file/widget).\n" +
            "# latest.txt itself stays one-article-per-line raw data (the widget tail and rss-open.sh read it)."
    }

    return """$head
DIR="${d}HOME/.z2term/rss"
FEEDS="${d}DIR/feeds.txt"
SEEN="${d}DIR/seen.txt"
NEW="${d}DIR/new.txt"
LATEST="${d}DIR/latest.txt"
KEEP=500                                  # $cKeep

$cListDoc
if [ "${d}1" = list ]; then
  [ -f "${d}LATEST" ] || { echo "$cListNone"; exit 0; }
  tty=0; [ -t 1 ] && tty=1
  head -n "${d}{2:-20}" "${d}LATEST" | awk -v tty="${d}tty" '
    {
      url = ${d}NF                          # URL は行末の 1 語 (空白を含まないため)
      t = ${d}0
      sub(/[ \t]+[^ \t]+${d}/, "", t)       # 末尾の URL を落として題名だけにする
      host = url
      sub(/^https?:\/\//, "", host)
      sub(/\/.*${d}/, "", host)
      if (tty)
        printf "[%2d] \033]8;;%s\033\\%s  (%s)\033]8;;\033\\\n", NR, url, t, host
      else
        printf "[%2d] %s  (%s)\n     %s\n", NR, t, host, url
    }'
  exit 0
fi

mkdir -p "${d}DIR" || exit 1
command -v python3 >/dev/null 2>&1 || { echo "$cNoPy" >&2; exit 1; }
if [ ! -f "${d}FEEDS" ]; then
  printf '%s\n' '$cFeedsHint' > "${d}FEEDS"
  echo "$cWriteFeeds ${d}FEEDS"
  exit 0
fi

: > "${d}DIR/.raw"
while IFS= read -r url; do
  case "${d}url" in ''|'#'*) continue ;; esac
  # $cSkipFail
  python3 - "${d}url" >> "${d}DIR/.raw" <<'Z2RSS_PY'
import sys, urllib.request, xml.etree.ElementTree as ET

req = urllib.request.Request(sys.argv[1], headers={"User-Agent": "z2term-rss/1"})
try:
    with urllib.request.urlopen(req, timeout=20) as r:
        root = ET.fromstring(r.read())
except Exception:
    sys.exit(0)

for it in root.iter():
    if it.tag.split("}")[-1] not in ("item", "entry"):
        continue
    title = link = ""
    for c in it:
        t = c.tag.split("}")[-1]
        if t == "title" and not title:
            title = (c.text or "").strip()
        elif t == "link" and not link:
            link = (c.get("href") or c.text or "").strip()
    if link.startswith("http"):
        print(link + "\t" + (title or link))
Z2RSS_PY
done < "${d}FEEDS"

$cDiff
touch "${d}SEEN"
grep -Fxv -f "${d}SEEN" "${d}DIR/.raw" 2>/dev/null | grep . > "${d}NEW"
rm -f "${d}DIR/.raw"
n=${d}(grep -c . "${d}NEW" 2>/dev/null)
[ "${d}{n:-0}" -eq 0 ] && exit 0

cat "${d}NEW" "${d}SEEN" | head -n "${d}KEEP" > "${d}SEEN.t" && mv "${d}SEEN.t" "${d}SEEN"
$cPrepend
{ awk -F'\t' '{ print ${d}2 "  " ${d}1 }' "${d}NEW"; cat "${d}LATEST" 2>/dev/null; } \
  | head -n "${d}KEEP" > "${d}LATEST.t" && mv "${d}LATEST.t" "${d}LATEST"

z2-notify -h -n rss -b "$cOpen" "${d}(printf '$cNotify' "${d}n")" "${d}(cut -f2 "${d}NEW" | head -3)"

# 端末から直に走らせたときだけ一覧の出し方を案内する (自動実行のログを汚さない)。
[ -t 1 ] && echo "$cListHint"
"""
}

/**
 * 集めた記事を 1 本ずつ開くサンプルの本体。
 *
 * **ウィジェットに「行ごとのタップ」を作らずに済ませるための答え**でもある。ライブ tail の
 * 本文は 1 つの TextView に流し込む作りで (RemoteViews は行数ぶんの View を生やせない)、
 * 行を個別に押させるには一覧ウィジェットへの作り替えが要る。一方、**状態ウィジェットの
 * マクロボタンは既に「タップで `~/.z2term/macros/` 配下の `.sh` を実行」**なので、開く側を
 * マクロで書けばアプリを 1 行も変えずに「タップで次の記事」が成立する。
 *
 * ⚠ このコメントで `macros/` の後に `*` を続けて書かないこと。Kotlin は**ブロックコメントが
 * 入れ子になる**ので、`/` と `*` が並んだ時点でコメントが 1 段深く開き、閉じ側がずれて
 * **以降のコードが丸ごとコメントに飲まれる**。
 *
 * 開いた URL を [OPENED] に貯めて引き算するので、**押すたびに次の 1 本**へ進む
 * (同じ記事が何度も開かない)。`rss.sh` の既読管理と同じ考え方。
 */
private fun rssOpenBody(d: String, ja: Boolean): String {
    val head = if (ja) """
#
# 準備: 先に集める側を仕掛ける
#   z2-macro install rss
# ウィジェット: 状態ウィジェットの設定で「rss-open」をボタンに割り当てる。
#
# z2-run: 状態ウィジェットのボタンに rss-open を割り当てる (端末で試すなら sh ~/.z2term/macros/rss-open.sh)
""" else """
#
# Setup: put the collecting side in place first
#   z2-macro install rss
# Widget: assign "rss-open" to a button in the status widget's settings.
#
# z2-run: assign "rss-open" to a status-widget button (to try it here: sh ~/.z2term/macros/rss-open.sh)
"""
    val cNone = if (ja) "まだ記事がありません" else "No articles yet"
    val cAllRead = if (ja) "新しい記事はありません" else "Nothing new to open"
    val cPick = if (ja) {
        "# latest.txt は「タイトル  URL」。まだ開いていない先頭の URL を 1 本だけ取る。"
    } else {
        "# latest.txt lines are \"title  URL\". Take the first URL that has not been opened yet."
    }
    val cCap = if (ja) "開いた記録が増え続けないよう上限をかける" else "cap the opened list so it cannot grow forever"

    return """$head
DIR="${d}HOME/.z2term/rss"
LATEST="${d}DIR/latest.txt"
OPENED="${d}DIR/opened.txt"

[ -f "${d}LATEST" ] || { z2-toast "$cNone"; exit 0; }
touch "${d}OPENED"
$cPick
url=${d}(awk '{ print ${d}NF }' "${d}LATEST" | grep '^http' | grep -Fxv -f "${d}OPENED" | head -1)
[ -n "${d}url" ] || { z2-toast "$cAllRead"; exit 0; }

echo "${d}url" >> "${d}OPENED"
# $cCap
tail -n 500 "${d}OPENED" > "${d}OPENED.t" && mv "${d}OPENED.t" "${d}OPENED"
z2-open "${d}url"
"""
}

/*
 * --- サンプル共通: ログの「新着だけ」を読む部品 ---
 *
 * 通知/イベントのログは**ユーザーが形式を自由に決められ**(任意テンプレート・改行入りも可)、
 * さらに ⚙設定の「新着を上」で**先頭追記**にも切り替えられる。そのため、素朴な
 * 「1 行 = 1 レコード」「`tail -F` で末尾を追う」実装は次の 2 通りで破綻する:
 *  - 複数行テンプレートでは 1 レコードが複数行に割れ、必要な情報が同じ行に揃わない。
 *  - 先頭追記ではファイル末尾に新着が来ないので `tail -F` が永久に何も拾わない。
 *
 * そこで**前回スナップショットとの差分**を見る。差分が前回内容の前後どちらに付いたかで
 * 追記方向を自動判別でき、差分は行に割らず塊のまま渡すので複数行テンプレートも扱える。
 *
 * [diffSetup] が変数と準備、[diffLoop] が監視ループを吐く。両者の間に各サンプル固有の
 * ハンドラ関数を置く。サンプルは `z2-macro install <名前>` で 1 ファイルずつ展開する
 * 教材なので、**生成後のスクリプトは自己完結**させる (共通ファイルへの依存を作らない)。
 */

/** 差分読みの変数と準備。[log] は `~/.z2term/` 配下のファイル名、[tag] は作業ファイルの識別名。 */
private fun diffSetup(d: String, ja: Boolean, log: String, tag: String): String {
    val cPoll = if (ja) "ログを見に行く間隔(秒)。⚠ 詰めないこと (下記)" else "how often to poll the log (seconds). Do NOT shorten (see below)"
    // ⚠ **この既定値を小さくしないこと** (0.8.273)。0.8.272 まで 2 秒だった結果、これを常駐させた
    // 端末で「待っているだけ」の CPU が常時 5% 前後になり、電池の減りとして表に出た (実測)。
    // 同じ理由でアプリ側の supervisor も 1 秒 → 5 秒へ広げてある (ServerSupervisorScript 参照)。
    val cCost = if (ja) {
        """
# ⚠ **POLL を詰めないでください。** このスクリプトはエンジン (proot/z2root) の中で動くので、
# 外部コマンドを 1 回起こすだけで ptrace 越しに数千 syscall になります。1 周で sleep と wc を
# 起こすため、2 秒間隔にすると**待っているだけでエンジンが CPU を数 % 使い続け**、常駐中は
# 端末が Doze に入れないぶん電池が目に見えて減ります (実測: 60 秒あたり CPU 3 秒)。
# ⚠ そもそも **z2-when に登録できるきっかけなら、この形ではなく z2-when を使ってください。**
# 待ち受けはアプリ側がやるので、常駐スクリプトが 1 本も要らず、待っている間のコストがゼロです。
"""
    } else {
        """
# ⚠ **Do not shorten POLL.** This script runs inside the engine (proot/z2root), where starting a
# single external command costs thousands of ptrace-mediated syscalls. Each pass starts a sleep and
# a wc, so a 2-second interval keeps **the engine burning a few percent of CPU while doing nothing**,
# and a resident script also keeps the device out of Doze — visible as battery drain (measured:
# ~3 seconds of CPU per minute).
# ⚠ More importantly: **if z2-when can express your trigger, use z2-when instead of this shape.**
# The app does the waiting, so no resident script is needed and idling costs nothing.
"""
    }
    return """$cCost
POLL=15                                   # $cPoll
LOG=${d}HOME/.z2term/$log
SNAP=${d}HOME/.z2term/.$tag.snap
WORK=${d}HOME/.z2term/.$tag.work

[ -f "${d}LOG" ] || : > "${d}LOG"
"""
}

/** 差分読みの監視ループ。新着の塊を [handler] に渡す。 */
private fun diffLoop(d: String, ja: Boolean, handler: String): String {
    val cBase = if (ja) {
        "# 初回は「今ある分」を既読の基準にするだけで、過去ログには反応しない。"
    } else {
        "# The first pass only records a baseline, so existing entries never fire."
    }
    val cSame = if (ja) "サイズが同じなら変化なしとみなす" else "same size = nothing new"
    val cWhole = if (ja) {
        "# 直前が空 = 全体が新着。(起動時に必ず基準を取るので過去ログの誤発火にはならない)"
    } else {
        "# Previously empty = all of it is new. (The startup baseline keeps old entries from firing.)"
    }
    val cAppend = if (ja) "前回内容で「始まる」→ 末尾追記(新着が下)" else "starts with the old content -> appended (newest last)"
    val cPrepend = if (ja) "前回内容で「終わる」→ 先頭追記(新着が上)" else "ends with the old content -> prepended (newest first)"
    val cElse = if (ja) {
        "# どちらでもない = 書き換え/掃除。基準を貼り直すだけで発火しない。"
    } else {
        "# Neither = rewritten/cleaned. Just re-baseline without firing."
    }
    val cTrunc = if (ja) "# cn < pn (truncate された) も基準の貼り直しだけ。" else "# cn < pn (truncated) also just re-baselines."
    return """
$cBase
cp "${d}LOG" "${d}SNAP" 2>/dev/null || : > "${d}SNAP"

while :; do
  sleep "${d}POLL"
  [ -f "${d}LOG" ] || continue

  cn=${d}(wc -c < "${d}LOG"  2>/dev/null || echo 0)
  pn=${d}(wc -c < "${d}SNAP" 2>/dev/null || echo 0)
  [ "${d}cn" = "${d}pn" ] && continue           # $cSame

  new=''
  if [ "${d}cn" -gt "${d}pn" ] && [ "${d}pn" -eq 0 ]; then
    $cWhole
    new=${d}(cat "${d}LOG")
  elif [ "${d}cn" -gt "${d}pn" ]; then
    grew=${d}((cn - pn))
    head -c "${d}pn" "${d}LOG" > "${d}WORK" 2>/dev/null
    if cmp -s "${d}WORK" "${d}SNAP"; then
      new=${d}(tail -c "${d}grew" "${d}LOG")  # $cAppend
    else
      tail -c "${d}pn" "${d}LOG" > "${d}WORK" 2>/dev/null
      if cmp -s "${d}WORK" "${d}SNAP"; then
        new=${d}(head -c "${d}grew" "${d}LOG")  # $cPrepend
      fi
      $cElse
    fi
  fi
  $cTrunc

  cp "${d}LOG" "${d}SNAP" 2>/dev/null
  [ -n "${d}new" ] && $handler "${d}new"
done
"""
}

/**
 * OTP をクリップボードへ入れて TTL 秒後に消す本体 (`notify:otp` / `sms:otp` 共用・0.8.273)。
 *
 * 0.8.272 まではここが**ログを 2 秒ごとに見張る常駐スクリプト**で、本文から 4〜8 桁を取り出す
 * awk (日時・エポック・通知 ID・パッケージ名を先に潰し、キーワードからの位置で選ぶ) を抱えていた。
 * `notify:otp` / `sms:otp` が**その抽出まで済ませて `Z2_WHEN_OTP` に入れてくれる**ので、
 * 同じことが常駐なしで書ける。実際に常駐版を動かしていた端末では、待っているだけでエンジンが
 * CPU を常時 5% 前後使い続けていた (実測) — 同じものが数行で書けるなら、既定のサンプルは
 * そちらであるべき。
 *
 * ⚠ 抽出そのものの作法 (メタ情報の数字を先に消す・キーワードからの位置で選ぶ) は、
 * `z2-when` に無いきっかけで同じことをしたくなったときのために MACRO-GUIDE 5-6 に残してある。
 */
private fun otpWhenBody(d: String, ja: Boolean): String {
    val copied = if (ja) "コードをコピー: ${d}{code}" else "Copied code: ${d}{code}"
    val cleared = if (ja) "コピーしたコードをクリアしました" else "Cleared the copied code"
    val cTtl = if (ja) "コピーから何秒でクリアするか" else "seconds before the copy is cleared"
    val cCode = if (ja) {
        "# コードの抽出はアプリ側が済ませてある。取れなかったときは何もしない。"
    } else {
        "# The app already extracted the code. Do nothing when it could not."
    }
    val cClear = if (ja) {
        "# TTL 秒後、クリップボードがコピー時の値のままなら空にする。\n" +
            "# その間に別のものをコピーしていたら、そちらは消さずに残す。"
    } else {
        "# After TTL, clear the clipboard only if it still holds the code we copied\n" +
            "# (anything copied since then is left alone)."
    }
    return """
TTL=60                                    # $cTtl

$cCode
code=${d}Z2_WHEN_OTP
[ -n "${d}code" ] || exit 0

z2-clip set "${d}code"
z2-toast "$copied"

$cClear
sleep "${d}TTL"
[ "${d}(z2-clip get 2>/dev/null)" = "${d}code" ] || exit 0
z2-clip set ""
z2-toast "$cleared"
"""
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
    // 常駐させないサンプル (時刻トリガーで 1 回走るもの・ウィジェットのボタンから叩くもの) がある。
    // そういうスクリプトは先頭に `# z2-run: <動かし方>` を書いておき、install はそれを出す。
    // ⚠ 一律に「常駐サーバーに登録」と案内すると、**使い切りのスクリプトを常駐させてしまう**
    //   (終了するたび supervisor が再起動するので、フィード取得なら延々と取りに行く)。
    val msgHintRun = if (ja) "動かし方:" else "How to run it:"

    return """
        |#!/bin/sh
        |# z2term マクロ管理 (同梱サンプルの導入)。マクロの書き方は docs の MACRO-GUIDE を参照。
        |SRC=$src
        |DEST=${d}HOME/.z2term/macros
        |usage() {
        |${usage.joinToString("\n|") { "  echo '${it.replace("'", "'\\''")}' >&2" }}
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
        |      hint=${d}(sed -n 's/^# z2-run: //p' "${d}DEST/${d}name" 2>/dev/null | head -1)
        |      if [ -n "${d}hint" ]; then
        |        echo "$msgHintRun"
        |        echo "  ${d}hint"
        |      else
        |        echo "$msgHintResident"
        |        echo "  sh ${d}DEST/${d}name"
        |      fi
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

/**
 * リマインダーサンプルの本体。
 *
 * **アプリ側に「予定」機能を作らないための見本**でもある (rss.sh と同じ立ち位置)。
 * 必要な部品はすべて揃っている — 単発は `z2-alarm`、繰り返しは `z2-when time:`、鳴らすのは
 * `z2-notify -b`、返事は `event:notify_action`、アプリを開かず足すのは `z2-tile` + `z2-ask`。
 *
 * 設計上の要点:
 *  - **単発と繰り返しで置き場を分ける**。繰り返しは `z2-when` のルールとして残るので、
 *    自動化タブに並び ▶ で試せる。単発をルールにすると発火後に**死んだルールが溜まる**ので、
 *    こちらは `z2-alarm` の予約 (鳴れば消える) にして、拾い役の `event:alarm` を 1 本だけ常設する。
 *  - **本文はファイルに置き、通知の名前 (`-n`) には id だけ入れる**。`z2-notify -n <名前>` が
 *    `event:notify_action` の `Z2_WHEN_EVENT_NAME` にそのまま返るので、これで単発・繰り返し
 *    どちらのボタンも 1 本のルールで受けられる。名前に本文を入れると、空白や絵文字が
 *    混ざった瞬間に突き合わせが壊れる。
 *  - **受け口は 2 本だけ**。予定を何件足しても `z2-when` のルールは増えない。
 */
/** 「いつ？」を聞き直す回数。⚠ 無限に聞かない — 通知が消えない相手になってしまう。 */
private const val ASK_TRIES = 3

private fun remindBody(d: String, ja: Boolean): String {
    val head = if (ja) """
#
# 準備 (1 回だけ):
#   sh ~/.z2term/macros/remind.sh setup     受け口 2 本とタイル 2 枠を登録
#
# 構文:
#   remind.sh <いつ> <本文>          予定を足す
#   remind.sh list                   一覧 (先頭が番号)
#   remind.sh del <番号|all>         取り消し (rm でも可)
#   remind.sh peek                   一覧を通知で見る (「消す」ボタン付き)
#   remind.sh ask                    通知の返信欄で聞いて足す (タイル用)
#   remind.sh setup                  受け口とタイルを登録 (最初に 1 回)
#   remind.sh help                   この説明
#
# <いつ> の書き方 — 1 回だけ:
#   30m / 90s / 2h                   いまから
#   18:30                            次の 18:30 (過ぎていれば明日)
#   明日 18:30 / 明後日 / 3日後 07:00  日付で (3d / 明日の18:30 も同じ)
#   07/30 19:00                      月日 (過ぎていれば来年)
#   2026 07/30 19:00                 年つき
#   202607301900 / 07301900          数字だけ (年月日時分 / 月日時分)
#   ⚠ 時刻を書かなければ**今と同じ時刻**になる (明後日 / 3日後 / 07/30 …)
#
# <いつ> の書き方 — 繰り返し:
#   毎日 07:00 / 平日 09:00          毎日 / 月〜金
#   毎週 月 09:00                    その曜日
#   毎月 15 09:00                    その日 (毎月15日 09:00 も同じ)
#   毎年 07/30 19:00                 その月日
#   毎 19:00 / 毎 水 19:00           「毎」だけでも書ける (次の語で決まる)
#   毎 15 19:00 / 毎 07/30 19:00     → 毎日 / 毎週 / 毎月 / 毎年
#
# 例:
#   remind.sh 30m 薬を飲む
#   remind.sh 明日 18:30 ゴミ出し
#   remind.sh 毎月 25 10:00 家賃
#   remind.sh list        →  1  ⏰ 07/30 18:30  ゴミ出し
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (以降は remind.sh 30m … で足すだけ)
""" else """
#
# Setup (once):
#   sh ~/.z2term/macros/remind.sh setup   register the two hooks and two tiles
#
# Syntax:
#   remind.sh <when> <text>          add a reminder
#   remind.sh list                   list them (the number comes first)
#   remind.sh del <n|all>            cancel (rm works too)
#   remind.sh peek                   show the list in a notification (with a Delete button)
#   remind.sh ask                    ask in a notification reply box (for the tile)
#   remind.sh setup                  register the hooks and tiles (once)
#   remind.sh help                   this text
#
# <when> - one-shot:
#   30m / 90s / 2h                   from now
#   18:30                            the next 18:30 (tomorrow if it passed)
#   tomorrow 18:30 / 3d 07:00        by day (3d = 3 days from now)
#   07/30 19:00                      month/day (next year if it passed)
#   2030 07/30 19:00                 with a year
#   203007301900 / 07301900          digits only (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ leave the time out and it keeps **the current time of day** (3d / 07/30 ...)
#
# <when> - repeating:
#   daily 07:00 / weekday 09:00      every day / Mon-Fri
#   weekly mon 09:00                 that weekday
#   monthly 15 09:00                 that day of the month
#   yearly 07/30 19:00               that month and day
#   every 19:00 / every wed 19:00    "every" alone works (the next word decides)
#   every 15 19:00 / every 07/30 19:00  -> daily / weekly / monthly / yearly
#
# Examples:
#   remind.sh 30m take pills
#   remind.sh tomorrow 18:30 the bins
#   remind.sh monthly 25 10:00 rent
#   remind.sh list        ->  1  ⏰ 07/30 18:30  the bins
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (then just: remind.sh 30m ...)
"""
    val cStore = if (ja) {
        """# 予定 1 件 = ${d}DIR/<id>.txt の 1 行。TAB 区切りで  種別 / 予定の表記 / when の id / 本文。
#   種別: once (これから鳴る単発) / fired (鳴った単発) / repeat (繰り返し)
#   when の id: repeat のときだけ入る (削除で z2-when remove するため)。それ以外は '-'。
# 本文を末尾に置くのは、TAB 以外の文字をそのまま持たせるため。"""
    } else {
        """# One reminder = one line in ${d}DIR/<id>.txt, tab separated: kind / label / when-id / text.
#   kind: once (still to fire) / fired (already fired) / repeat (recurring)
#   when-id: only for repeat (so delete can call z2-when remove). '-' otherwise.
# The text goes last so it can hold anything but a TAB."""
    }
    val cParse = if (ja) {
        """# 引数 (1〜3 語) を読んで下記を決める。
#   KIND … once|repeat   PLAN … 一覧に出す表記   SPEC … z2-alarm/z2-when へ渡す形   USED … 使った語数"""
    } else {
        """# Read 1-3 words and decide:
#   KIND ... once|repeat   PLAN ... label to show   SPEC ... what z2-alarm/z2-when takes   USED ... words used"""
    }
    val pDaily = if (ja) "毎日 " else "daily "
    val pWeekday = if (ja) "平日 " else "weekdays "
    val pWeekly = if (ja) "毎週" else "weekly "
    val pMonthly = if (ja) "毎月 " else "monthly "
    val pYearly = if (ja) "毎年 " else "yearly "
    val mDay = if (ja) "日" else ""
    // 「毎 …」の展開先。⚠ **その言語で case が拾える語**であること (日本語面なら「毎日」等)。
    val eDaily = if (ja) "毎日" else "daily"
    val eWeekly = if (ja) "毎週" else "weekly"
    val eMonthly = if (ja) "毎月" else "monthly"
    val eYearly = if (ja) "毎年" else "yearly"
    val mBadDate = if (ja) {
        "その日付はありません (例: 07/30):"
    } else {
        "no such date (e.g. 07/30):"
    }
    val cEvery = if (ja) {
        """  # 「毎 …」の簡易指定 (要望)。**次の語の形**で 毎日 / 毎週 / 毎月 / 毎年 を決める。
  #   毎 19:00 → 毎日 / 毎 水 19:00 → 毎週 / 毎 15 19:00 → 毎月 / 毎 07/30 19:00 → 毎年
  # ⚠ 語数は変わらない (「毎」1 語が「毎日」等に置き換わるだけ) ので USED はそのままでよい。"""
    } else {
        """  # The short "every ..." form. **The shape of the next word** picks daily/weekly/monthly/yearly:
  #   every 19:00 -> daily / every wed 19:00 -> weekly / every 15 19:00 -> monthly /
  #   every 07/30 19:00 -> yearly
  # ⚠ The word count does not change (one word swapped for another), so USED still holds."""
    }
    val cExpand = if (ja) {
        "# 「毎」の次に来た語から、どの繰り返しかを決める。"
    } else {
        "# Decide which recurrence \"every\" meant, from the word that follows it."
    }
    val cDigits = if (ja) {
        """    # 数字だけで書く形 (要望): 202607301900 = 年月日時分 / 07301900 = 月日時分。
    # ⚠ 12 桁 → 8 桁の順に見る (8 桁のパターンは 12 桁にも当たってしまうため)。"""
    } else {
        """    # All-digit forms: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ Check 12 digits before 8 (the 8-digit pattern also matches a 12-digit string)."""
    }
    val cYmd = if (ja) {
        """  # 年月日で書かれたもの。⚠ z2-alarm は日付を渡せないので、ここでも秒差へ寄せる。"""
    } else {
        """  # Written as a date. ⚠ z2-alarm cannot take a date, so this also folds into a delay."""
    }
    val cCivil = if (ja) {
        """# 年月日 → 1970-01-01 からの通算日 (うるう年込み・Howard Hinnant の days_from_civil)。
# ⚠ date -d "2026-07-30" は busybox で当てにならないので自前で数える。
# ⚠ 日数の差だけを使い、時刻は [day_epoch] に任せる (こうするとタイムゾーンが相殺される)。"""
    } else {
        """# Civil date -> days since 1970-01-01 (leap years included; Howard Hinnant's days_from_civil).
# ⚠ date -d "2026-07-30" is not dependable on busybox, so the count is done here.
# ⚠ Only the day difference is used and the time is left to [day_epoch], which cancels out the zone."""
    }
    val cCron = if (ja) {
        """# HH:MM と曜日欄から cron 式を作る (分 時 日 月 曜日)。
# ⚠ 先頭 0 を落とすのに expr を使わないこと — 結果が 0 のとき終了コードが 1 になり、
#   `expr "${d}m" + 0 || echo "${d}m"` の右側まで走って値が 2 行に化ける (実際に踏んだ)。"""
    } else {
        """# Build a cron expression (min hour dom month dow) from HH:MM and a weekday field.
# Do NOT use expr to strip a leading zero: it exits 1 when the result is 0, so the right-hand
# side of `expr "${d}m" + 0 || echo "${d}m"` also runs and the value ends up two lines long."""
    }
    val afterLabel = if (ja) {
        """# "30m" → "30分後 (14:35)"。今の時刻を足して見せるのは、登録した直後に確かめられるように。
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num; unit=秒 ;; m) sec=${d}((num*60)); unit=分 ;; h) sec=${d}((num*3600)); unit=時間 ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "${d}num${d}unit後 (${d}at)" || echo "${d}num${d}unit後"
}"""
    } else {
        """# "30m" -> "in 30m (14:35)". Showing the wall-clock time lets you check it right away.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "in ${d}num${d}u (${d}at)" || echo "in ${d}num${d}u"
}"""
    }
    val mUsageAdd = if (ja) "usage: remind.sh <いつ> <本文>" else "usage: remind.sh <when> <text>"
    val mBadWhen = if (ja) {
        "いつ？ が分かりません (例: 30m / 18:30 / 明日 18:30 / 3日後 09:00 / 毎日 07:00):"
    } else {
        "cannot read the time (try: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):"
    }
    val mBadTime = if (ja) "時刻は HH:MM で書いてください:" else "write the time as HH:MM:"
    val mBadRange = if (ja) {
        "時刻の範囲が違います (00:00〜23:59):"
    } else {
        "time out of range (00:00-23:59):"
    }
    val mNoTime = if (ja) {
        "時刻が書かれていません (例: 毎日 07:00):"
    } else {
        "no time given (e.g. daily 07:00):"
    }
    val mBadDow = if (ja) "曜日が分かりません:" else "unknown weekday:"
    val mPastTime = if (ja) {
        "その時刻はもう過ぎています:"
    } else {
        "that time has already passed:"
    }
    val cDays = if (ja) {
        """    # 日付で書く言い方。時刻を省いたら**今と同じ時刻**にする (既定時刻を勝手に決めない)。
    # 「明日の18:30」のように 1 語で来ることもあるので、くっついた形も受ける。"""
    } else {
        """    # Day-based wording. With no time, keep **the current time of day** (never invent a default).
    # It can also arrive as a single word, so the glued form is accepted too."""
    }
    val cDayEpoch = if (ja) {
        """# 「N 日後の HH:MM」を epoch 秒にする。⚠ date -d "tomorrow" は busybox に無いので、
# 今日の 0 時を出してから日数と時刻を足す。
# ⚠ 先頭 0 の付いた値 ("08") を ${d}(()) に渡すと 8 進数と解釈されるので必ず落とす。
# ⚠ 1 日 = 86400 秒として足すので、夏時間のある地域では切り替え日に 1 時間ずれる。"""
    } else {
        """# Turn "HH:MM, N days from now" into epoch seconds. ⚠ busybox has no date -d "tomorrow",
# so today's midnight is derived first and the days and time added on top.
# ⚠ A value with a leading zero ("08") is read as octal by ${d}(()), so strip it.
# ⚠ A day is added as 86400s, so it can be an hour off on a DST switch day."""
    }
    val cFmtAt = if (ja) {
        """# 一覧に出す日時。⚠ 「明日」のまま覚えると日付が変わった後にズレて見えるので、実日付にする。"""
    } else {
        """# The label shown in the list. ⚠ Keeping "tomorrow" would read wrong once the date rolls over."""
    }
    val mNoBody = if (ja) "リマインドの本文を書いてください" else "say what to remind you about"
    val mNoAlarm = if (ja) "予約できませんでした" else "could not schedule it"
    val mNone = if (ja) "予定はありません" else "nothing scheduled"
    val mFired = if (ja) " (通知済)" else " (fired)"
    val mRemoved = if (ja) "消しました:" else "removed:"
    val mNoSuchNum = if (ja) {
        "その番号はありません (remind.sh list で確認):"
    } else {
        "no such entry (check remind.sh list):"
    }
    val mUsageDel = if (ja) "usage: remind.sh del <番号|all>" else "usage: remind.sh del <n|all>"
    val bDone = if (ja) "完了" else "Done"
    // ⚠ 通知のボタンは 1 語で書く (空白があると引数が割れる)。
    val bDelete = if (ja) "消す" else "Delete"
    val mAskDel = if (ja) "どれを消す？" else "Remove which one?"
    val mAskDelH = if (ja) "番号 / all" else "number / all"
    val mDeletedTitle = if (ja) "🗑 消しました" else "🗑 Removed"
    val cPeek = if (ja) {
        """# 一覧を通知で見せる (タイル「予定」から)。**番号を残す** — 消すときに指すものだから。
# ⚠ 一覧を出したのに消す手段が無く、set したら消せないと言われた (要望) ので「消す」を付ける。"""
    } else {
        """# Show the list in a notification (from the "list" tile). **Keep the numbers** — they are what
# you point at to remove one. ⚠ Listing without a way to remove was the complaint; hence the button."""
    }
    val cAskDel = if (ja) {
        """# 「消す」ボタン。番号を聞いて [cmd_del] へ渡すだけ (端末の remind.sh del と同じ経路)。"""
    } else {
        """# The "delete" button: ask for a number and hand it to [cmd_del] (the same path as the CLI)."""
    }
    val bS1 = if (ja) "10分後" else "+10min"
    val bS2 = if (ja) "1時間後" else "+1h"
    val mAgain = if (ja) "にもう一度:" else "- again in"
    val mTitle = if (ja) "⏰ リマインド" else "⏰ Reminders"
    val mAsk1 = if (ja) "何をリマインド？" else "Remind you about what?"
    val mAsk1H = if (ja) "例: 薬を飲む" else "e.g. take pills"
    val mAsk2 = if (ja) "いつ？" else "When?"
    val mAsk2H = if (ja) "30m / 18:30 / 明日 18:30 / 3日後 / 毎日 07:00" else "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00"
    val mAskAgain = if (ja) "もう一度入力してください" else "please enter it again"
    val mAskGiveUp = if (ja) {
        "${ASK_TRIES} 回とも読めませんでした。端末からも登録できます: remind.sh 30m 薬を飲む"
    } else {
        "Could not read it ${ASK_TRIES} times. You can also add it from the terminal: remind.sh 30m take pills"
    }
    val mOkTitle = if (ja) "⏰ 登録しました" else "⏰ Reminder set"
    val mNgTitle = if (ja) "⚠ 登録できませんでした" else "⚠ Not set"
    val cHooks = if (ja) {
        """  # 予定が鳴ったのを拾う 1 本と、通知ボタンの返事を拾う 1 本。**この 2 本だけ**で、
  # 予定を何件足しても増えない。どちらも「検知」の ON/OFF に関係なく働く。"""
    } else {
        """  # One hook for "a reminder fired", one for "a notification button was tapped". Just these
  # two, no matter how many reminders you add. Neither depends on the detection switches."""
    }
    val cTiles = if (ja) {
        """  # 空いている枠と、すでに自分が使っている枠だけに置く (他人の割り当ては触らない)。
  # 引数付きのマクロ名は 0.8.275 からそのまま書ける (それより前は sh + フルパスで書くこと)。"""
    } else {
        """  # Only fill empty slots and slots already ours (never overwrite someone else's).
  # A macro name with arguments works from 0.8.275 on (before that, write sh + full path)."""
    }
    val mSetupHooks = if (ja) "受け口を登録しました:" else "hooks registered:"
    val mSetupTiles = if (ja) "タイル:" else "tiles:"
    val mPathHint = if (ja) {
        "⚠ このタブでは remind.sh を名前で打てません。新しいタブを開くか、" +
            "このタブで export PATH=${d}HOME/.z2term/macros:${d}PATH を打ってください。"
    } else {
        "Note: this tab cannot run remind.sh by name yet. Open a new tab, or run " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH here."
    }
    val cPathHint = if (ja) {
        """  # ⚠ マクロ置き場が PATH に入るのは 0.8.287 から。**それ以前に開いたタブ**は古い PATH の
  # ままなので、名前で打てるか確かめて、駄目なら開き直しを案内する (黙っていると
  # `command not found` の理由が分からない)。"""
    } else {
        """  # ⚠ The macro directory has only been on PATH since 0.8.287, so **tabs opened before that**
  # still carry the old one. Check whether the name resolves and say what to do if not
  # (otherwise `command not found` has no visible reason)."""
    }
    val mPlace = if (ja) {
        "⚠ タイルはご自身でクイック設定パネルの鉛筆(編集)から並べてください。"
    } else {
        "Note: you still have to place the tiles yourself, from the quick-settings pencil/edit screen."
    }
    val lRemind = if (ja) "リマインド" else "remind"
    val lList = if (ja) "予定" else "list"
    val cSelf = if (ja) {
        "# 自分が出した通知だけ相手にする (id は必ず r で始まる)"
    } else {
        "# Only react to our own notifications (our ids always start with r)"
    }
    val cKeepRepeat = if (ja) {
        "      # 繰り返しはここで消さない (明日もまた鳴ってほしいので)。単発だけ片付ける。"
    } else {
        "      # Do not delete a repeating one here: it should fire again tomorrow."
    }
    val cAsk = if (ja) {
        """# タイル/通知から聞く経路。⚠ **結果は必ず通知で返す** — ここは画面を見ていない前提の
# 入口なので、エラーを標準エラーへ出して終わると「押したのに何も起きない」になる
# (実際そうなっていた。理由はタイルの run.log にしか残らなかった)。
#   読めなかったら → 何が駄目かを付けて $ASK_TRIES 回まで聞き直す (前の入力は返信欄に残す)
#   登録できたら   → 予定と本文を通知で見せる"""
    } else {
        """# The path used from the tile / a notification. ⚠ **Always answer with a notification**:
# nobody is looking at a terminal here, so failing to stderr reads as "I tapped it and
# nothing happened" (it did — the reason only reached the tile's run.log).
#   Unreadable -> say why and ask again, up to $ASK_TRIES times (the previous answer is kept)
#   Scheduled  -> show the plan and the text in a notification"""
    }
    val cSnoozeCancel = if (ja) {
        "スヌーズ中に完了を押したときの予約を残さない"
    } else {
        "drop the snooze alarm if it was snoozed before being done"
    }

    return """$head
DIR="${d}HOME/.z2term/remind"
SELF="${d}HOME/.z2term/macros/remind.sh"
SNOOZE1=10m
SNOOZE2=1h
KEEP_DONE_DAYS=3

mkdir -p "${d}DIR"

$cStore

die() { echo "${d}1" >&2; exit 1; }

$cParse
parse_when() {
  KIND=; PLAN=; SPEC=; USED=0; WHY=; hhmm=; dowf=; downame=; days=
  dom=; mon=; ymd=; yy=; mm2=; dd2=
  w1=${d}1; w2=${d}2; w3=${d}3

$cEvery
  case ${d}w1 in
    毎|every) w1=${d}(expand_every "${d}w2") ;;
  esac

  case ${d}w1 in
$cDays
    明日|あした|翌日|tomorrow)
                      KIND=once; days=1; USED=1
                      if is_hhmm "${d}w2"; then hhmm=${d}w2; USED=2; fi ;;
    明後日|あさって)   KIND=once; days=2; USED=1
                      if is_hhmm "${d}w2"; then hhmm=${d}w2; USED=2; fi ;;
    明日*)            KIND=once; days=1; USED=1; hhmm=${d}{w1#明日}; hhmm=${d}{hhmm#の} ;;
    明後日*)          KIND=once; days=2; USED=1; hhmm=${d}{w1#明後日}; hhmm=${d}{hhmm#の} ;;
    [0-9]*日後*)      KIND=once; days=${d}{w1%%日後*}; USED=1
                      rest=${d}{w1#*日後}; rest=${d}{rest#の}
                      if [ -n "${d}rest" ]; then hhmm=${d}rest
                      elif is_hhmm "${d}w2"; then hhmm=${d}w2; USED=2; fi ;;
    [0-9]*d)          KIND=once; days=${d}{w1%d}; USED=1
                      if is_hhmm "${d}w2"; then hhmm=${d}w2; USED=2; fi ;;
    毎日|daily)       KIND=repeat; hhmm=${d}w2; USED=2; dowf=daily ;;
    平日|weekday)     KIND=repeat; hhmm=${d}w2; USED=2; dowf=1-5 ;;
    毎週|weekly)      KIND=repeat; hhmm=${d}w3; USED=3; downame=${d}w2
                      dowf=${d}(dow_of "${d}w2") || { WHY="$mBadDow ${d}w2"; return 1; } ;;
    毎月|monthly)     KIND=repeat; dom=${d}{w2%日}; hhmm=${d}w3; USED=3 ;;
    毎年|yearly)      KIND=repeat; USED=3; hhmm=${d}w3
                      mon=${d}{w2%%/*}; dom=${d}{w2#*/} ;;
    毎日=*|daily=*)   KIND=repeat; hhmm=${d}{w1#*=}; USED=1; dowf=daily ;;
    平日=*|weekday=*) KIND=repeat; hhmm=${d}{w1#*=}; USED=1; dowf=1-5 ;;
    毎日*)            KIND=repeat; hhmm=${d}{w1#毎日}; USED=1; dowf=daily ;;
    平日*)            KIND=repeat; hhmm=${d}{w1#平日}; USED=1; dowf=1-5 ;;
    毎月*)            KIND=repeat; USED=2; rest=${d}{w1#毎月}
                      dom=${d}{rest%日}; hhmm=${d}w2 ;;
    毎年*)            KIND=repeat; USED=2; rest=${d}{w1#毎年}
                      mon=${d}{rest%%/*}; dom=${d}{rest#*/}; hhmm=${d}w2 ;;
    [0-9]*[smh])      KIND=once; USED=1
                      # ⚠ 数字以外が混じった "1.5h" "3x0m" をここで弾く。通すと after_label の
                      #   ${d}((num*60)) が壊れ、予約は入らないのに登録できたように見える。
                      case ${d}{w1%[smh]} in
                        *[!0-9]*|"") WHY="$mBadWhen ${d}w1"; return 1 ;;
                      esac
                      PLAN="${d}(after_label "${d}w1")"; SPEC="in ${d}w1"; return 0 ;;
$cDigits
    [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])
                      KIND=once; USED=1; ymd=1
                      yy=${d}(cut2 "${d}w1" 1); yy=${d}yy${d}(cut2 "${d}w1" 3)
                      mm2=${d}(cut2 "${d}w1" 5); dd2=${d}(cut2 "${d}w1" 7)
                      hhmm="${d}(cut2 "${d}w1" 9):${d}(cut2 "${d}w1" 11)" ;;
    [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])
                      KIND=once; USED=1; ymd=1
                      mm2=${d}(cut2 "${d}w1" 1); dd2=${d}(cut2 "${d}w1" 3)
                      hhmm="${d}(cut2 "${d}w1" 5):${d}(cut2 "${d}w1" 7)" ;;
    20[0-9][0-9])     KIND=once; USED=2; ymd=1; yy=${d}w1
                      case ${d}w2 in
                        [0-9]*/[0-9]*) mm2=${d}{w2%%/*}; dd2=${d}{w2#*/} ;;
                        *) WHY="$mBadWhen ${d}w2"; return 1 ;;
                      esac
                      if is_hhmm "${d}w3"; then hhmm=${d}w3; USED=3; fi ;;
    [0-9]*/[0-9]*)    KIND=once; USED=1; ymd=1
                      mm2=${d}{w1%%/*}; dd2=${d}{w1#*/}
                      if is_hhmm "${d}w2"; then hhmm=${d}w2; USED=2; fi ;;
    [0-9]*:[0-9]*)    KIND=once; USED=1; hhmm=${d}w1 ;;
    *) WHY="$mBadWhen ${d}w1"; return 1 ;;
  esac

$cYmd
  if [ -n "${d}ymd" ]; then
    y=${d}{yy:-${d}(date +%Y)}
    check_md "${d}mm2" "${d}dd2" || return 1
    [ -z "${d}hhmm" ] || check_hhmm "${d}hhmm" || return 1
    now=${d}(date +%s)
    tgt=${d}(day_epoch "${d}(days_between "${d}y" "${d}mm2" "${d}dd2")" "${d}hhmm")
    # 年を書いていない ∧ その日付が過ぎている → 来年のこと (「07/30」と書く人はそう思っている)。
    if [ "${d}tgt" -le "${d}now" ] && [ -z "${d}yy" ]; then
      tgt=${d}(day_epoch "${d}(days_between "${d}((y+1))" "${d}mm2" "${d}dd2")" "${d}hhmm")
    fi
    sec=${d}(( tgt - now ))
    [ "${d}sec" -gt 0 ] || { WHY="$mPastTime ${d}(fmt_at "${d}tgt")"; return 1; }
    PLAN=${d}(fmt_at "${d}tgt"); SPEC="in ${d}{sec}s"
    return 0
  fi

  # 毎月 / 毎年。cron の日・月の欄を使う (曜日は *)。
  if [ -n "${d}dom" ]; then
    check_md "${d}{mon:-1}" "${d}dom" || return 1
    check_hhmm "${d}hhmm" || return 1
    if [ -n "${d}mon" ]; then
      PLAN="$pYearly${d}{mon}/${d}{dom} ${d}hhmm"
      SPEC="time:cron=${d}(cron_of "${d}hhmm" '*' "${d}(strip0 "${d}dom")" "${d}(strip0 "${d}mon")")"
    else
      PLAN="$pMonthly${d}dom$mDay ${d}hhmm"
      SPEC="time:cron=${d}(cron_of "${d}hhmm" '*' "${d}(strip0 "${d}dom")")"
    fi
    return 0
  fi

  # 日付で書かれたもの (明日 / 明後日 / N日後) は秒差にして z2-alarm へ渡す。
  # ⚠ z2-alarm の at は「次の HH:MM」しか取れず**日付を渡せない**ので、in <秒>s へ寄せる。
  if [ -n "${d}days" ]; then
    case ${d}days in *[!0-9]*|"") WHY="$mBadWhen ${d}w1"; return 1 ;; esac
    [ -z "${d}hhmm" ] || check_hhmm "${d}hhmm" || return 1
    tgt=${d}(day_epoch "${d}days" "${d}hhmm")
    sec=${d}(( tgt - ${d}(date +%s) ))
    [ "${d}sec" -gt 0 ] || { WHY="$mPastTime ${d}(fmt_at "${d}tgt")"; return 1; }
    PLAN=${d}(fmt_at "${d}tgt"); SPEC="in ${d}{sec}s"
    return 0
  fi

  check_hhmm "${d}hhmm" || return 1

  if [ "${d}KIND" = once ]; then
    PLAN=${d}hhmm; SPEC="at ${d}hhmm"
  elif [ "${d}dowf" = daily ]; then
    PLAN="$pDaily${d}hhmm"; SPEC="time:daily=${d}hhmm"
  elif [ -n "${d}downame" ]; then
    PLAN="$pWeekly${d}downame ${d}hhmm"; SPEC="time:cron=${d}(cron_of "${d}hhmm" "${d}dowf")"
  else
    PLAN="$pWeekday${d}hhmm"; SPEC="time:cron=${d}(cron_of "${d}hhmm" "${d}dowf")"
  fi
  return 0
}

$cDayEpoch
day_epoch() {
  now=${d}(date +%s)
  ch=${d}(date +%H); cm=${d}(date +%M); cs=${d}(date +%S)
  ch=${d}{ch#0}; [ -n "${d}ch" ] || ch=0
  cm=${d}{cm#0}; [ -n "${d}cm" ] || cm=0
  cs=${d}{cs#0}; [ -n "${d}cs" ] || cs=0
  if [ -n "${d}2" ]; then
    hh=${d}{2%%:*}; mm=${d}{2##*:}
    hh=${d}{hh#0}; [ -n "${d}hh" ] || hh=0
    mm=${d}{mm#0}; [ -n "${d}mm" ] || mm=0
  else
    hh=${d}ch; mm=${d}cm
  fi
  echo ${d}(( now - ch*3600 - cm*60 - cs + ${d}1*86400 + hh*3600 + mm*60 ))
}

$cFmtAt
fmt_at() {
  y=${d}(date -d "@${d}1" +%Y 2>/dev/null)
  # ⚠ 年をまたぐものは年も出す。「07/30」だけだと来年のことなのか分からない。
  if [ -n "${d}y" ] && [ "${d}y" != "${d}(date +%Y)" ]; then
    date -d "@${d}1" +'%Y/%m/%d %H:%M' 2>/dev/null || echo "${d}1"
  else
    date -d "@${d}1" +'%m/%d %H:%M' 2>/dev/null || echo "${d}1"
  fi
}

$cExpand
expand_every() {
  case ${d}1 in
    [0-9]:[0-9][0-9]|[0-9][0-9]:[0-9][0-9]) echo "$eDaily" ;;
    [0-9]*/[0-9]*)                          echo "$eYearly" ;;
    [0-9]*)                                 echo "$eMonthly" ;;
    *)                                      echo "$eWeekly" ;;
  esac
}

# 次の語が HH:MM か。時刻を省いた「明日 電話する」と「明日 18:30 電話する」を見分ける。
is_hhmm() {
  case ${d}1 in
    [0-9]:[0-9][0-9]|[0-9][0-9]:[0-9][0-9]) return 0 ;;
    *) return 1 ;;
  esac
}

# 時刻の検査。⚠ **書式だけでなく範囲も見る** — "18:70" は書式に通ってしまい、
# そのまま予約すると鳴らない予定が「登録できた」顔で一覧に並ぶ。
check_hhmm() {
  [ -n "${d}1" ] || { WHY="$mNoTime"; return 1; }
  echo "${d}1" | grep -Eq '^[0-9]{1,2}:[0-9]{2}${d}' || { WHY="$mBadTime ${d}1"; return 1; }
  hh=${d}{1%%:*}; mm=${d}{1##*:}
  hh=${d}{hh#0}; [ -n "${d}hh" ] || hh=0
  mm=${d}{mm#0}; [ -n "${d}mm" ] || mm=0
  { [ "${d}hh" -le 23 ] && [ "${d}mm" -le 59 ]; } || { WHY="$mBadRange ${d}1"; return 1; }
  return 0
}

$cCron
# ${d}3 = 日の欄 / ${d}4 = 月の欄 (省略で *)。毎月 / 毎年はここを埋める。
cron_of() {
  h=${d}{1%%:*}; m=${d}{1##*:}
  h=${d}{h#0}; [ -n "${d}h" ] || h=0
  m=${d}{m#0}; [ -n "${d}m" ] || m=0
  echo "${d}m ${d}h ${d}{3:-*} ${d}{4:-*} ${d}2"
}

# 先頭 0 を落とす ("07" → "7")。cron の欄と算術に渡す前に必ず通す。
strip0() { v=${d}{1#0}; [ -n "${d}v" ] || v=0; echo "${d}v"; }

# ${d}1 の ${d}2 文字目から 2 文字。数字だけの日時 (202607301900) を割るのに使う。
cut2() { printf '%s' "${d}1" | cut -c"${d}2-${d}((${d}2+1))"; }

$cCivil
days_from_civil() {
  y=${d}1; m=${d}2; d_=${d}3
  if [ "${d}m" -le 2 ]; then y=${d}((y-1)); sm=${d}((m+9)); else sm=${d}((m-3)); fi
  era=${d}((y/400))
  yoe=${d}((y - era*400))
  doy=${d}(( (153*sm + 2)/5 + d_-1 ))
  doe=${d}(( yoe*365 + yoe/4 - yoe/100 + doy ))
  echo ${d}(( era*146097 + doe - 719468 ))
}

# 今日から見て「その年月日」が何日後か (負なら過去)。[day_epoch] に渡して使う。
days_between() {
  ty=${d}(strip0 "${d}(date +%Y)"); tm=${d}(strip0 "${d}(date +%m)"); td=${d}(strip0 "${d}(date +%d)")
  a=${d}(days_from_civil "${d}ty" "${d}tm" "${d}td")
  b=${d}(days_from_civil "${d}(strip0 "${d}1")" "${d}(strip0 "${d}2")" "${d}(strip0 "${d}3")")
  echo ${d}((b - a))
}

# 月と日の検査。⚠ 2/31 のような「書けるが存在しない日」も弾く (通すと予定が別の日に化ける)。
check_md() {
  m=${d}(strip0 "${d}1"); d_=${d}(strip0 "${d}2")
  case "${d}1${d}2" in *[!0-9]*|"") WHY="$mBadDate ${d}1/${d}2"; return 1 ;; esac
  { [ "${d}m" -ge 1 ] && [ "${d}m" -le 12 ] && [ "${d}d_" -ge 1 ] && [ "${d}d_" -le 31 ]; } ||
    { WHY="$mBadDate ${d}1/${d}2"; return 1; }
  # その月に無い日 (4/31・2/30…) は days_from_civil が翌月へ回り込むので、戻して確かめる。
  y=${d}{yy:-${d}(date +%Y)}; y=${d}(strip0 "${d}y")
  n=${d}(days_from_civil "${d}y" "${d}m" "${d}d_")
  [ "${d}(date -d "@${d}((n*86400 + 43200))" -u +%d 2>/dev/null || echo "${d}d_")" = "${d}(printf '%02d' "${d}d_")" ] ||
    { WHY="$mBadDate ${d}1/${d}2"; return 1; }
  return 0
}

dow_of() {
  case ${d}1 in
    日|日曜|日曜日|sun) echo 0 ;;  月|月曜|月曜日|mon) echo 1 ;;
    火|火曜|火曜日|tue) echo 2 ;;  水|水曜|水曜日|wed) echo 3 ;;
    木|木曜|木曜日|thu) echo 4 ;;  金|金曜|金曜日|fri) echo 5 ;;
    土|土曜|土曜日|sat) echo 6 ;;
    *) return 1 ;;
  esac
}

$afterLabel

cmd_add() {
  [ ${d}# -ge 1 ] || die "$mUsageAdd"
  parse_when "${d}1" "${d}2" "${d}3" || die "${d}{WHY:-$mBadWhen ${d}1}"
  shift "${d}USED"
  body=${d}*
  [ -n "${d}body" ] || die "$mNoBody"

  sweep_done
  id="${d}(date +%s)${d}${d}"
  wid=-

  if [ "${d}KIND" = repeat ]; then
    wid=${d}(z2-when "${d}SPEC" run "sh ${d}SELF fire ${d}id" 2>&1) || die "${d}wid"
    wid=${d}(echo "${d}wid" | tr -d ' \t\r\n')
  else
    z2-alarm ${d}SPEC "r${d}id" >/dev/null || die "$mNoAlarm"
  fi

  printf '%s\t%s\t%s\t%s\n' "${d}KIND" "${d}PLAN" "${d}wid" "${d}body" > "${d}DIR/${d}id.txt"
  z2-toast "⏰ ${d}PLAN — ${d}body"
  printf '%s\t%s\n' "${d}PLAN" "${d}body"
}

cmd_fire() {
  id=${d}{1#r}
  f="${d}DIR/${d}id.txt"
  [ -f "${d}f" ] || exit 0
  kind=${d}(cut -f1 "${d}f"); body=${d}(cut -f4- "${d}f")

  z2-notify -h -n "r${d}id" -b $bDone -b $bS1 -b $bS2 "⏰ ${d}body"

  if [ "${d}kind" = once ]; then
    plan=${d}(cut -f2 "${d}f"); wid=${d}(cut -f3 "${d}f")
    printf '%s\t%s\t%s\t%s\n' fired "${d}plan" "${d}wid" "${d}body" > "${d}f"
  fi
}

cmd_reply() {
  # 一覧通知の「消す」ボタン。⚠ 予定の通知 (r…) より先に見る — remind-list も r で始まるので、
  # 後ろに置くと id="emind-list" として拾われてしまう。
  case ${d}1 in
    remind-list) [ "${d}2" = "$bDelete" ] && ask_delete; exit 0 ;;
  esac
  case ${d}1 in r*) id=${d}{1#r} ;; *) exit 0 ;; esac   $cSelf
  f="${d}DIR/${d}id.txt"
  [ -f "${d}f" ] || exit 0
  kind=${d}(cut -f1 "${d}f"); wid=${d}(cut -f3 "${d}f"); body=${d}(cut -f4- "${d}f")

  case ${d}2 in
    $bDone)
$cKeepRepeat
      if [ "${d}kind" != repeat ]; then
        z2-alarm cancel "r${d}id" >/dev/null 2>&1   # $cSnoozeCancel
        rm -f "${d}f"
      fi
      z2-toast "✅ ${d}body" ;;
    $bS1|$bS2)
      case ${d}2 in $bS1) sp=${d}SNOOZE1 ;; *) sp=${d}SNOOZE2 ;; esac
      z2-alarm in "${d}sp" "r${d}id" >/dev/null || exit 1
      [ "${d}kind" = repeat ] ||
        printf '%s\t%s\t%s\t%s\n' once "${d}(after_label "${d}sp")" "${d}wid" "${d}body" > "${d}f"
      z2-toast "💤 ${d}2 $mAgain ${d}body" ;;
  esac
}

each() {
  n=0
  for f in "${d}DIR"/*.txt; do
    [ -f "${d}f" ] || continue
    n=${d}((n+1))
    id=${d}(basename "${d}f" .txt)
    "${d}1" "${d}n" "${d}id" "${d}f"
  done
  return 0
}

row() {
  kind=${d}(cut -f1 "${d}3"); plan=${d}(cut -f2 "${d}3"); body=${d}(cut -f4- "${d}3")
  case ${d}kind in
    repeat) mark=🔁 ;; fired) mark=✔ ;; *) mark=⏰ ;;
  esac
  [ "${d}kind" = fired ] && plan="${d}plan$mFired"
  printf '%s\t%s %s\t%s\n' "${d}1" "${d}mark" "${d}plan" "${d}body"
}

cmd_list() {
  out=${d}(each row)
  if [ -z "${d}out" ]; then echo "$mNone"; else echo "${d}out"; fi
}

$cPeek
cmd_peek() {
  out=${d}(each row | tr '\t' ' ')
  if [ -z "${d}out" ]; then
    z2-notify -n remind-list "$mTitle" "$mNone"
    return
  fi
  z2-notify -n remind-list -b $bDelete "$mTitle" "${d}out"
}

$cAskDel
ask_delete() {
  out=${d}(each row | tr '\t' ' ')
  [ -n "${d}out" ] || { z2-notify -n remind-list "$mTitle" "$mNone"; return; }
  # ⚠ ボタンを押すと元の通知は閉じるので、番号が見えるように一覧を出し直してから聞く。
  z2-notify -n remind-list "$mTitle" "${d}out"
  n=${d}(z2-ask -H "$mAskDelH" "$mAskDel") || return
  [ -n "${d}n" ] || return
  msg=${d}(cmd_del "${d}n" 2>&1) || { z2-notify -n remind-ng "$mNgTitle" "${d}msg"; return; }
  z2-notify -n remind-ok "$mDeletedTitle" "${d}msg"
}

del_one() {
  kind=${d}(cut -f1 "${d}3"); wid=${d}(cut -f3 "${d}3"); body=${d}(cut -f4- "${d}3")
  [ "${d}kind" = repeat ] && [ "${d}wid" != - ] && z2-when remove "${d}wid" >/dev/null 2>&1
  [ "${d}kind" = once ] && z2-alarm cancel "r${d}2" >/dev/null 2>&1
  rm -f "${d}3"
  echo "$mRemoved ${d}body"
}

TARGET=
del_if_match() {
  [ "${d}1" = "${d}TARGET" ] || [ "${d}2" = "${d}TARGET" ] || return 0
  del_one "${d}@"; HIT=1
}

cmd_del() {
  [ -n "${d}1" ] || die "$mUsageDel"
  if [ "${d}1" = all ]; then each del_one; return; fi
  TARGET=${d}1; HIT=0
  each del_if_match
  [ "${d}HIT" = 1 ] || die "$mNoSuchNum ${d}TARGET"
}

sweep_done() {
  find "${d}DIR" -name '*.txt' -mtime "+${d}KEEP_DONE_DAYS" 2>/dev/null | while read -r f; do
    [ "${d}(cut -f1 "${d}f")" = fired ] && rm -f "${d}f"
  done
}

$cAsk
cmd_ask() {
  body=${d}(z2-ask -H "$mAsk1H" "$mAsk1") || exit 0
  [ -n "${d}body" ] || exit 0

  q=$mAsk2; prev=; ok=; n=0
  while [ "${d}n" -lt $ASK_TRIES ]; do
    n=${d}((n+1))
    if [ -n "${d}prev" ]; then
      w=${d}(z2-ask -H "$mAsk2H" -d "${d}prev" "${d}q") || exit 0
    else
      w=${d}(z2-ask -H "$mAsk2H" "${d}q") || exit 0
    fi
    [ -n "${d}w" ] || exit 0
    set -- ${d}w
    if parse_when "${d}1" "${d}2" "${d}3"; then ok=1; break; fi
    # ⚠ 打ち直しやすいように、読めなかった入力を -d で返信欄に入れておく。
    prev=${d}w
    q="⚠ ${d}WHY — $mAskAgain"
  done
  [ "${d}ok" = 1 ] || { z2-notify -n remind-ng "$mNgTitle" "$mAskGiveUp"; exit 1; }

  set -- ${d}w
  out=${d}(cmd_add "${d}@" "${d}body" 2>&1) || { z2-notify -n remind-ng "$mNgTitle" "${d}out"; exit 1; }
  z2-notify -n remind-ok "$mOkTitle" "${d}(echo "${d}out" | tr '\t' ' ')"
}

cmd_setup() {
$cHooks
  z2-when list 2>/dev/null | grep -q "${d}SELF fire" ||
    z2-when 'event:alarm' run "sh ${d}SELF fire \"${d}Z2_WHEN_EVENT_NAME\"" >/dev/null
  z2-when list 2>/dev/null | grep -q "${d}SELF reply" ||
    z2-when 'event:notify_action' run "sh ${d}SELF reply \"${d}Z2_WHEN_EVENT_NAME\" \"${d}Z2_WHEN_ACTION\"" >/dev/null

$cTiles
  free=${d}(z2-tile list | awk -F'\t' '${d}2=="-" || index(${d}3, "remind") { print ${d}1 }')
  set -- ${d}free
  [ -n "${d}1" ] && z2-tile set "${d}1" 'remind.sh ask'  -l $lRemind >/dev/null
  [ -n "${d}2" ] && z2-tile set "${d}2" 'remind.sh peek' -l $lList >/dev/null

  echo "$mSetupHooks"
  z2-when list | grep "${d}SELF" | cut -f1,3
  echo "$mSetupTiles"
  z2-tile list
  echo
  echo "$mPlace"
$cPathHint
  command -v remind.sh >/dev/null 2>&1 || { echo; echo "$mPathHint"; }
}

case ${d}1 in
  ''|list|ls)  cmd_list ;;
  peek)        cmd_peek ;;
  add)         shift; cmd_add "${d}@" ;;
  del|rm)      shift; cmd_del "${d}@" ;;
  fire)        shift; cmd_fire "${d}@" ;;
  reply)       shift; cmd_reply "${d}@" ;;
  ask)         cmd_ask ;;
  setup)       cmd_setup ;;
  -h|--help|help) awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 { exit }' "${d}0" ;;
  *)           cmd_add "${d}@" ;;
esac
"""
}
