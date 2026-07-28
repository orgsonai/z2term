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

    return linkedMapOf(
        "watch-basic.sh" to watchBasic,
        "battery-alert.sh" to batteryAlert,
        "daily-report.sh" to dailyReport,
        "otp-clip.sh" to otpClip,
        "otp-sms.sh" to otpSms,
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
