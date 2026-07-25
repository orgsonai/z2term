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

    // --- 2. z2-state を使う: 状況を見てから動く ---
    val batteryAlert = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# battery-alert.sh — 電池が減ったら知らせる。ただし今の状況を見て出し分ける。")
            appendLine("# z2-state で「画面が点いているか」を見て、点いていればトースト、消えていれば通知。")
            appendLine("# ログ形式・追記方向のどちらにも依存しない (差分を読み、イベント名で照合する)。")
            appendLine("# 準備: ⚙設定 →「システムイベント検知」を ON")
        } else {
            appendLine("# battery-alert.sh — warn on low battery, but adapt to the current state.")
            appendLine("# Uses z2-state to check whether the screen is on: toast if it is, notification if not.")
            appendLine("# Independent of log format and write direction (diffs the log, matches on event names).")
            appendLine("# Setup: Settings -> \"System event detection\" ON")
        }
        append(diffSetup(d, ja, "events.jsonl", "battery-alert"))
        appendLine()
        if (ja) {
            appendLine("# 値そのものはログから読まず z2-state で取る。だから形式が変わっても影響を受けない。")
        } else {
            appendLine("# Read the value from z2-state, not from the log, so the format never matters.")
        }
        appendLine("handle() {")
        appendLine("  printf '%s\\n' \"${d}1\" | while IFS= read -r rec; do")
        appendLine("    case \"${d}rec\" in *battery_low*|*battery_level*) ;; *) continue ;; esac")
        appendLine("    level=${d}(z2-state level)")
        if (ja) {
            appendLine("    # 充電中なら知らせない (勝手に減っているわけではないので)")
        } else {
            appendLine("    # Say nothing while charging (it is not actually draining)")
        }
        appendLine("    [ \"${d}(z2-state charging)\" = \"true\" ] && continue")
        appendLine("    [ \"${d}level\" -le 20 ] 2>/dev/null || continue")
        appendLine("    if [ \"${d}(z2-state screen)\" = \"on\" ]; then")
        if (ja) {
            appendLine("      z2-toast \"電池 ${d}{level}%\"")
            appendLine("    else")
            appendLine("      z2-notify -h \"電池注意\" \"残り ${d}{level}% です\"")
        } else {
            appendLine("      z2-toast \"Battery ${d}{level}%\"")
            appendLine("    else")
            appendLine("      z2-notify -h \"Low battery\" \"${d}{level}% left\"")
        }
        appendLine("    fi")
        appendLine("  done")
        appendLine("}")
        append(diffLoop(d, ja, "handle"))
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
            appendLine("# ログ形式・追記方向のどちらにも依存しない (差分を読み、名前で照合する)。")
        } else {
            appendLine("# daily-report.sh — read out battery and connection every morning.")
            appendLine("# Usage: set the time trigger once")
            appendLine("#   z2-alarm daily 07:00 morning")
            appendLine("# then keep this script resident")
            appendLine("#   register  sh ~/.z2term/macros/daily-report.sh  under Settings -> Resident servers")
            appendLine("# Unlike cron this also fires during Doze (it may be a few minutes late).")
            appendLine("# Independent of log format and write direction (diffs the log, matches on names).")
        }
        append(diffSetup(d, ja, "events.jsonl", "daily-report"))
        appendLine()
        if (ja) {
            appendLine("# alarm と、z2-alarm に付けた名前 (morning) の両方が新着に出たときだけ動く。")
            appendLine("# 複数行テンプレートだと 2 つが別々の行に出るので、行ごとではなく塊のまま見る。")
        } else {
            appendLine("# Fire only when both 'alarm' and the name given to z2-alarm ('morning') show up.")
            appendLine("# A multi-line template splits them across lines, so check the chunk as a whole.")
        }
        appendLine("handle() {")
        appendLine("  case \"${d}1\" in *alarm*) ;; *) return ;; esac")
        appendLine("  case \"${d}1\" in *morning*) ;; *) return ;; esac")
        appendLine("  level=${d}(z2-state level)")
        appendLine("  if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=mobile; fi")
        if (ja) {
            appendLine("  z2-say \"おはようございます。電池は ${d}{level} パーセント、接続は ${d}{net} です\"")
        } else {
            appendLine("  z2-say \"Good morning. Battery ${d}{level} percent, network ${d}{net}\"")
        }
        appendLine("}")
        append(diffLoop(d, ja, "handle"))
    }

    // --- 4. 実用: 通知内のワンタイムコードを自動コピー (MACRO-GUIDE 5-6 と同じ内容) ---
    val otpClip = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# otp-clip.sh — 通知に含まれるワンタイムコード(4〜8桁)を自動でクリップボードへ入れ、")
            appendLine("# TTL 秒後に「値が変わっていなければ」自動で消す。")
            appendLine("# ログ形式・追記方向のどちらにも依存しない (詳細は handle() と下のループのコメント)。")
            appendLine("# 準備: ⚙設定 →「通知検知」ON ＋ OS の「通知アクセス」許可")
            appendLine("# 常駐: ⚙設定 → 常駐サーバー に  sh ~/.z2term/macros/otp-clip.sh  を登録")
        } else {
            appendLine("# otp-clip.sh — copy a one-time code (4-8 digits) out of notifications, then")
            appendLine("# clear it after TTL seconds if the clipboard still holds that same value.")
            appendLine("# Independent of both log format and write direction (see handle() and the loop below).")
            appendLine("# Setup: Settings -> \"Notification detection\" ON + grant OS notification access")
            appendLine("# Resident: register  sh ~/.z2term/macros/otp-clip.sh  under Settings -> Resident servers")
        }
        append(otpClipBody(d, ja))
    }

    // --- 5. 実用: SMS 内のワンタイムコードを自動コピー (通知でなく SMS を直読み = 伏せ字を迂回) ---
    val otpSms = buildString {
        appendLine("#!/bin/sh")
        if (ja) {
            appendLine("# otp-sms.sh — 受信 SMS に含まれるワンタイムコード(4〜8桁)を自動でクリップボードへ入れ、")
            appendLine("# TTL 秒後に「値が変わっていなければ」自動で消す。otp-clip.sh の SMS 版。")
            appendLine("# 通知と違い SMS 本文は機微通知の伏せ字(Android 15+)やロック状態の影響を受けないので確実。")
            appendLine("# 準備: ⚙設定 →「SMS 検知」ON ＋ OS の SMS 受信許可")
            appendLine("# 常駐: ⚙設定 → 常駐サーバー に  sh ~/.z2term/macros/otp-sms.sh  を登録")
        } else {
            appendLine("# otp-sms.sh — copy a one-time code (4-8 digits) out of incoming SMS, then")
            appendLine("# clear it after TTL seconds if the clipboard still holds that same value.")
            appendLine("# The SMS variant of otp-clip.sh. Unlike notifications, SMS bodies are never")
            appendLine("# redacted by sensitive-notification protection (Android 15+) and work while locked.")
            appendLine("# Setup: Settings -> \"SMS detection\" ON + grant the OS SMS permission")
            appendLine("# Resident: register  sh ~/.z2term/macros/otp-sms.sh  under Settings -> Resident servers")
        }
        append(otpClipBody(d, ja, "sms.jsonl", "otp-sms"))
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

    return """$head
DIR="${d}HOME/.z2term/rss"
FEEDS="${d}DIR/feeds.txt"
SEEN="${d}DIR/seen.txt"
NEW="${d}DIR/new.txt"
LATEST="${d}DIR/latest.txt"
KEEP=500                                  # $cKeep

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
""" else """
#
# Setup: put the collecting side in place first
#   z2-macro install rss
# Widget: assign "rss-open" to a button in the status widget's settings.
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
    val cPoll = if (ja) "ログを見に行く間隔(秒)" else "how often to poll the log (seconds)"
    return """
POLL=2                                    # $cPoll
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
 * `otp-clip.sh` の本体 (シェバンとヘッダコメントを除く部分)。
 *
 * 差分読みは [diffSetup] / [diffLoop] に共通化してある。otp-clip 固有の難しさは
 * **自由な形式ほど「コードに見えるが違う数字」が紛れ込む**こと。日時・エポック・
 * `{key}` の通知 ID・`{pkg}` を除去し、コードはキーワードからの位置で選ぶ
 * (先頭一致だと `{key}` を含むテンプレートで通知 ID を取り違える)。
 */
private fun otpClipBody(d: String, ja: Boolean, log: String = "notifications.jsonl", tag: String = "otp-clip"): String {
    val copied = if (ja) "コードをコピー: ${d}{code}" else "Copied code: ${d}{code}"
    val cleared = if (ja) "コピーしたコードをクリアしました" else "Cleared the copied code"
    val cTtl = if (ja) "コピーから何秒でクリアするか" else "seconds before the copy is cleared"
    val cClear = if (ja) {
        "# TTL 秒後、クリップボードがコピー時の値のままなら空にする(別物をコピーしていたら残す)。"
    } else {
        "# After TTL, clear the clipboard only if it still holds the code we copied."
    }
    val cStrip = if (ja) {
        "# コードに見えるが違うものを先に消す: 日時 / 時刻 / 9 桁以上(エポック等) /\n" +
            "  # '|' を含むトークン ({key} の通知 ID) / ドット区切り識別子 ({pkg})。最後に \"123-456\" を詰める。"
    } else {
        "# Drop things that look like a code but are not: dates / times / 9+ digit runs (epochs) /\n" +
            "  # tokens containing '|' (notification id from {key}) / dotted ids ({pkg}). Then join \"123-456\"."
    }
    val cPick = if (ja) {
        "# 「最初に見つかった数字」ではなくキーワードの直後を優先し、無ければ直前の最も近い数字。\n" +
            "  # 自由な形式では前後にメタ情報の数字が混ざるため、位置で選ばないと取り違える。\n" +
            "  # 数字列は必ず最大長で切り出し、長い数字列の一部を切り取らない。"
    } else {
        "# Prefer digits right after the keyword, else the nearest ones before it.\n" +
            "  # With a free-form template, metadata digits sit nearby, so position is what disambiguates.\n" +
            "  # Always take maximal digit runs so a long run never yields a partial match."
    }
    val cSave = if (ja) {
        "# RSTART/RLENGTH は awk の組み込みグローバルで、下の match() に壊されるため先に退避する。"
    } else {
        "# RSTART/RLENGTH are awk globals that the match() calls below clobber, so save them first."
    }
    val cMulti = if (ja) "複数行でも 1 つの塊として扱う" else "treat multi-line records as one blob"
    val cNoKw = if (ja) "キーワード無し = 認証通知ではない" else "no keyword = not an auth notification"
    val cAfter = if (ja) "キーワードの直後を優先" else "prefer what follows the keyword"
    val cBefore = if (ja) "無ければ直前の最も近い数字" else "otherwise the nearest digits before it"

    return """
TTL=60                                    # $cTtl
KEYWORDS='認証|確認|ワンタイム|コード|パスワード|code|otp|verification|verify|one[- ]?time'
""" + diffSetup(d, ja, log, tag) + """
$cClear
schedule_clear() {
  code=${d}1
  ( sleep "${d}TTL"
    cur=${d}(z2-clip get 2>/dev/null)
    if [ "${d}cur" = "${d}code" ]; then
      z2-clip set ""
      z2-toast "$cleared"
    fi
  ) &
}

handle() {
  raw=${d}1

  $cStrip
  scan=${d}(printf '%s' "${d}raw" | sed \
    -e 's/[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}[T ][0-9:+-]*/ /g' \
    -e 's/[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}/ /g' \
    -e 's/[0-9]\{1,2\}:[0-9]\{2\}\(:[0-9]\{2\}\)\?/ /g' \
    -e 's/[0-9]\{9,\}/ /g' \
    -e 's/[^ ]*|[^ ]*/ /g' \
    -e 's/[A-Za-z0-9_]\{1,\}\.[A-Za-z0-9_.]\{1,\}/ /g' \
    -e 's/\([0-9]\)-\([0-9]\)/\1\2/g' \
    -e 's/\([0-9]\)-\([0-9]\)/\1\2/g')

  $cPick
  code=${d}(printf '%s' "${d}scan" | awk -v kw="${d}KEYWORDS" '
    function firstcode(s,   r) {
      while (match(s, /[0-9]+/)) {
        r = substr(s, RSTART, RLENGTH)
        if (length(r) >= 4 && length(r) <= 8) return r
        s = substr(s, RSTART + RLENGTH)
      }
      return ""
    }
    function lastcode(s,   r, best) {
      best = ""
      while (match(s, /[0-9]+/)) {
        r = substr(s, RSTART, RLENGTH)
        if (length(r) >= 4 && length(r) <= 8) best = r
        s = substr(s, RSTART + RLENGTH)
      }
      return best
    }
    { buf = buf " " ${d}0 }                    # $cMulti
    END {
      if (!match(tolower(buf), kw)) exit       # $cNoKw
      $cSave
      ks = RSTART; kl = RLENGTH
      c = firstcode(substr(buf, ks + kl))      # $cAfter
      if (c == "") c = lastcode(substr(buf, 1, ks - 1))   # $cBefore
      if (c != "") print c
    }')
  [ -z "${d}code" ] && return

  z2-clip set "${d}code"
  z2-toast "$copied"
  schedule_clear "${d}code"
}
""" + diffLoop(d, ja, "handle")
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
