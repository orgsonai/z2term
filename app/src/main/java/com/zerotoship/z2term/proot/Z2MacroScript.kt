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
    // 言語ごとの文言を選ぶ道具。英語と日本語はここ、他の言語は proot/lang/ の言語ファイルに名前で置く ([CliText])。
    val t = CliText(lang)
    val d = "${'$'}"

    // --- 1. 入門: 出来事に反応する (待ち受けは z2-when がやる) ---
    val watchBasic = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.watchBasic.1",
            en = listOf(
            "# watch-basic.sh — starter macro. React to what happens on the device.",
                "# The app (z2-when) does the waiting, so this is not resident: it runs once and exits.",
                "# Setup: Settings -> \"System event detection\" ON",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (headsets: a second rule with 'event:headset_*')",
            ),
            ja = listOf(
            "# watch-basic.sh — 入門用マクロ。端末の出来事に反応する。",
                "# 待ち受けはアプリ側 (z2-when) がやるので常駐させない。起きたときに 1 回走って終わる。",
                "# 準備: ⚙設定 →「システムイベント検知」を ON",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (イヤホンは 'event:headset_*' でもう 1 本)",
            )
        ).forEach { appendLine(it) }
        appendLine()
        t.lines("macro.watchBasic.2",
            en = listOf(
            "# What happened arrives in Z2_WHEN_EVENT (z2-when events lists the names).",
                "# To try it by hand:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            ja = listOf(
            "# 何が起きたかは Z2_WHEN_EVENT に入る (使える名前は z2-when events で一覧できる)。",
                "# 手で試すときは:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            )
        ).forEach { appendLine(it) }
        appendLine("event=${d}{Z2_WHEN_EVENT:-}")
        t.lines("macro.watchBasic.3",
            en = listOf(
            "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT is empty. To try by hand: Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            ja = listOf(
            "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT が空です。手で試すなら Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            )
        ).forEach { appendLine(it) }
        appendLine()
        appendLine("case \"${d}event\" in")
        t.lines("macro.watchBasic.4",
            en = listOf(
            "  power_connected)    z2-toast \"Charging started\" ;;",
                "  power_disconnected) z2-toast \"Charging stopped\" ;;",
            ),
            ja = listOf(
            "  power_connected)    z2-toast \"充電を開始しました\" ;;",
                "  power_disconnected) z2-toast \"充電をやめました\" ;;",
            )
        ).forEach { appendLine(it) }
        appendLine("  headset_plugged)    z2-media play ;;")
        appendLine("  headset_unplugged)  z2-media pause ;;")
        appendLine("esac")
    }

    // --- 2. z2-state を使う: 状況を見てから動く (z2-when が起こす「使い切り」の形) ---
    val batteryAlert = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.batteryAlert.1",
            en = listOf(
            "# battery-alert.sh — warn on low battery, but adapt to the current state.",
                "# Uses z2-state to check whether the screen is on: toast if it is, notification if not.",
                "# Setup: Settings -> \"System event detection\" ON",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# z2-when hands the level over; fall back to z2-state so this also runs by hand.",
            ),
            ja = listOf(
            "# battery-alert.sh — 電池が減ったら知らせる。ただし今の状況を見て出し分ける。",
                "# z2-state で「画面が点いているか」を見て、点いていればトースト、消えていれば通知。",
                "# 準備: ⚙設定 →「システムイベント検知」を ON",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# 残量は z2-when が渡してくれる。手で試すときのために z2-state も見ておく。",
            )
        ).forEach { appendLine(it) }
        appendLine("level=${d}{Z2_WHEN_LEVEL:-${d}(z2-state level)}")
        appendLine()
        t.lines("macro.batteryAlert.2",
            en = listOf(
            "# Say nothing while charging (it is not actually draining)",
            ),
            ja = listOf(
            "# 充電中なら知らせない (勝手に減っているわけではないので)",
            )
        ).forEach { appendLine(it) }
        appendLine("[ \"${d}(z2-state charging)\" = \"true\" ] && exit 0")
        appendLine()
        appendLine("if [ \"${d}(z2-state screen)\" = \"on\" ]; then")
        t.lines("macro.batteryAlert.3",
            en = listOf(
            "  z2-toast \"Battery ${d}{level}%\"",
                "else",
                "  z2-notify -h \"Low battery\" \"${d}{level}% left\"",
            ),
            ja = listOf(
            "  z2-toast \"電池 ${d}{level}%\"",
                "else",
                "  z2-notify -h \"電池注意\" \"残り ${d}{level}% です\"",
            )
        ).forEach { appendLine(it) }
        appendLine("fi")
    }

    // --- 3. 時刻で動く (z2-when time: が OS のアラームで起こす) ---
    val dailyReport = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.dailyReport.1",
            en = listOf(
            "# daily-report.sh — read out battery and connection every morning.",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# The OS alarm wakes it, so it fires during Doze too (may be a few minutes late).",
                "# Does not depend on the detection switches.",
            ),
            ja = listOf(
            "# daily-report.sh — 毎朝きまった時刻に電池と接続状態を読み上げる。",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# 時刻は OS のアラームで起こすので Doze 中でも動く (省電力のため数分ずれることはある)。",
                "# 検知の ON/OFF には依存しない。",
            )
        ).forEach { appendLine(it) }
        appendLine()
        appendLine("level=${d}(z2-state level)")
        t.lines("macro.dailyReport.2",
            en = listOf(
            "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=mobile; fi",
                "z2-say \"Good morning. Battery ${d}{level} percent, network ${d}{net}\"",
            ),
            ja = listOf(
            "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=モバイル; fi",
                "z2-say \"おはようございます。電池は ${d}{level} パーセント、接続は ${d}{net} です\"",
            )
        ).forEach { appendLine(it) }
    }

    // --- 4. 実用: 通知内のワンタイムコードを自動コピー (MACRO-GUIDE 5-6 と同じ内容) ---
    val otpClip = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.otpClip",
            en = listOf(
            "# otp-clip.sh — copy a one-time code out of notifications, then clear it after",
                "# TTL seconds if the clipboard still holds that same value.",
                "# Setup: Settings -> \"Notification detection\" ON + grant OS notification access",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ may redact codes in sensitive notifications. For a reliable path use",
                "#    the SMS variant (otp-sms.sh) — SMS bodies are never redacted.",
            ),
            ja = listOf(
            "# otp-clip.sh — 通知に含まれるワンタイムコードを自動でクリップボードへ入れ、",
                "# TTL 秒後に「値が変わっていなければ」自動で消す。",
                "# 準備: ⚙設定 →「通知検知」ON ＋ OS の「通知アクセス」許可",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ は機微通知のコードを伏せ字にすることがある。確実に取るなら SMS 版",
                "#    (otp-sms.sh) を使う — SMS 本文は伏せ字にならない。",
            )
        ).forEach { appendLine(it) }
        append(otpWhenBody(d, t))
    }

    // --- 5. 実用: SMS 内のワンタイムコードを自動コピー (通知でなく SMS を直読み = 伏せ字を迂回) ---
    val otpSms = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.otpSms",
            en = listOf(
            "# otp-sms.sh — copy a one-time code out of incoming SMS, then clear it after",
                "# TTL seconds if the clipboard still holds that same value.",
                "# The SMS variant of otp-clip.sh. Unlike notifications, SMS bodies are never",
                "# redacted by sensitive-notification protection (Android 15+) and work while locked.",
                "# Setup: Settings -> \"SMS detection\" ON + grant the OS SMS permission",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            ja = listOf(
            "# otp-sms.sh — 受信 SMS に含まれるワンタイムコードを自動でクリップボードへ入れ、",
                "# TTL 秒後に「値が変わっていなければ」自動で消す。otp-clip.sh の SMS 版。",
                "# 通知と違い SMS 本文は機微通知の伏せ字(Android 15+)やロック状態の影響を受けないので確実。",
                "# 準備: ⚙設定 →「SMS 検知」ON ＋ OS の SMS 受信許可",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            )
        ).forEach { appendLine(it) }
        append(otpWhenBody(d, t))
    }

    // --- 6. 実用: 着信通知の電話番号をコピーボタンで渡す (通知の種別で拾う = 権限を増やさない) ---
    val unknownCall = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.unknownCall",
            en = listOf(
            "# unknown-call.sh — show a copy notification for phone numbers in call notifications.",
                "# Its \"Copy\" button puts the number on the clipboard (why it waits for a press: see below).",
                "# Setup: Settings -> \"Notification detection\" ON + grant OS notification access",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# To catch missed calls too, register a second rule (same script, different category):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            ja = listOf(
            "# unknown-call.sh — 着信通知に含まれる電話番号をコピー通知に出す。",
                "# 通知の「コピー」ボタンで番号がクリップボードへ入る (押すまで待つ理由は下に)。",
                "# 準備: ⚙設定 →「通知検知」ON ＋ OS の「通知アクセス」許可",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# 不在着信も控えたいなら、種別違いでもう 1 本登録する (中身は同じでよい):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            )
        ).forEach { appendLine(it) }
        append(unknownCallBody(d, t))
    }

    // --- 7. 実用: フィード購読 (時刻トリガーで 1 回だけ走る「使い切り」の形) ---
    val rss = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.rss",
            en = listOf(
            "# rss.sh — poll feeds and keep only what is new, as a notification and as text.",
                "# Unlike the other samples this one does **not** stay resident: it is the",
                "# 'run once from a time trigger and exit' shape.",
            ),
            ja = listOf(
            "# rss.sh — フィードを見に行って、新着だけを通知とテキストに残す。",
                "# 他のサンプルと違い**常駐しない**。時刻トリガーで 1 回走って終わる形の見本でもある。",
            )
        ).forEach { appendLine(it) }
        append(rssBody(d, t))
    }

    // --- 8. 実用: 集めた記事を 1 本ずつ開く (状態ウィジェットのボタンから叩く用) ---
    val rssOpen = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.rssOpen",
            en = listOf(
            "# rss-open.sh — open what rss.sh collected, newest first, one article per tap.",
                "# It shows up as a macro button on the status widget, so each tap opens the next one.",
            ),
            ja = listOf(
            "# rss-open.sh — rss.sh が集めた記事を、新しいものから 1 本ずつブラウザで開く。",
                "# 状態ウィジェットのマクロボタンに出るので、タップするたびに次の 1 本が開く。",
            )
        ).forEach { appendLine(it) }
        append(rssOpenBody(d, t))
    }

    // --- 9. 実用: 通知でリマインド (単発 = z2-alarm / 繰り返し = z2-when time: の使い分けの見本) ---
    val remind = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.remind",
            en = listOf(
            "# remind.sh — remind you with a notification: one-shot (in 30m, at 18:30) or",
                "# repeating (daily / weekdays / weekly). Fires with the app closed (an OS alarm",
                "# wakes it; no detection switch needed). Snooze from the notification buttons.",
            ),
            ja = listOf(
            "# remind.sh — 通知でリマインドする。単発 (30分後・18:30) と繰り返し (毎日・平日・毎週)。",
                "# アプリを閉じていても鳴る (OS のアラームで起こされるため。「検知」ON も不要)。",
                "# 通知のボタンからスヌーズできる。**アプリ側に予定機能を作らないための見本**でもある。",
            )
        ).forEach { appendLine(it) }
        append(remindBody(d, t))
    }

    // --- 10. 実用: QR にして渡す (端末に絵を出す / PNG に保存する・使い切り) ---
    val qr = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.qr",
            en = listOf(
            "# qr.sh — turn text or a file into a QR code: drawn here, or saved as a PNG.",
                "# Hands something to another device without retyping it. Runs once, no residency.",
            ),
            ja = listOf(
            "# qr.sh — テキストやファイルを QR にして、この端末に絵で出す / PNG に保存する。",
                "# 別の端末やカメラへ「打ち直さずに渡す」ための道具。常駐しない使い切りのマクロ。",
            )
        ).forEach { appendLine(it) }
        append(qrBody(d, t))
    }

    // --- 11. 実用: Markdown を読む (端末に整形して出す / アプリの読み物画面で開く) ---
    val md = buildString {
        appendLine("#!/bin/sh")
        t.lines("macro.md",
            en = listOf(
            "# md.sh — read a Markdown file here, formatted, or open it inside the app.",
                "# Headings, lists, tables, code, links; pictures show in place. Runs once, no residency.",
            ),
            ja = listOf(
            "# md.sh — Markdown をこの端末に整形して出す / アプリの読み物画面で開く。",
                "# 見出し・箇条書き・表・コード・リンクを整え、絵は本文の位置に出す。常駐しない使い切り。",
            )
        ).forEach { appendLine(it) }
        append(mdBody(d, t))
    }

    return linkedMapOf(
        "watch-basic.sh" to watchBasic,
        "battery-alert.sh" to batteryAlert,
        "daily-report.sh" to dailyReport,
        "otp-clip.sh" to otpClip,
        "otp-sms.sh" to otpSms,
        "unknown-call.sh" to unknownCall,
        "remind.sh" to remind,
        "translate.sh" to z2TranslationMacro(lang),
        "rss.sh" to rss,
        "rss-open.sh" to rssOpen,
        "qr.sh" to qr,
        "md.sh" to md,
        "voice.sh" to z2VoiceMacro(lang),
    )
}

/** Notification labels are protocol values: accept every bundled language, including older installs. */
internal val rssNotificationAction = """case "${'$'}Z2_WHEN_EVENT_NAME" in rss:*) case "${'$'}Z2_WHEN_ACTION" in 一覧|List|列表|Lista|목록) sh "${'$'}HOME/.z2term/macros/rss.sh" view ;; *) z2-open "${'$'}{Z2_WHEN_EVENT_NAME#rss:}" ;; esac ;; esac"""

/**
 * フィード購読サンプルの本体。
 *
 * **アプリ側に RSS 機能を作らないための見本**でもある。定期実行 (`z2-when time:`)・通知
 * (`z2-notify -b`)・ブラウザで開く (`z2-open`)・一覧を読む (`z2-view`) という既存の
 * 汎用部品だけで購読が成立することを示す。用途限定の画面を 1 枚も増やさずに済む。
 *
 * 設計上の要点:
 *  - **既読は「見た行を引き算する」**。`z2scan` のベースライン差分と同じやり方で、
 *    フィード側の日付や順序を信用しない (どちらも当てにならない)。
 *  - **解析は python3 に任せる**。RSS と Atom は形が揺れるので `grep`/`sed` で切ると
 *    フィードを 1 本増やすたびに壊れる。標準ライブラリだけで足りるので pip は要らない。
 *  - **1 本落ちても他は続ける**。取得失敗で全体が止まると、電波の悪い日に何も来なくなる。
 *  - `latest.txt` の行に **URL を残す**。端末の一覧表示や `rss-open` と互換を保つ。
 *    通知の「一覧」は `rss.sh view` が作る頁を開く。
 *  - **見逃したくないものは通知を分ける** (`important.txt`・0.8.334)。まとめ通知の本文には
 *    3 件しか載らないので、流量の多いフィードが同時に更新されると**大事な 1 本が押し出される**。
 *    当たった記事は 1 件ずつ別の通知にする (通知 id はアプリ側で個別に振られるため、分ければ
 *    上書きも省略もされない)。⚠ 個別通知は `HITMAX` 件まで — 語の書き方を間違えて全記事が
 *    当たったときに通知シェードを埋め尽くさないため。
 *  - **通知の名前に URL を入れる** (`-n "rss:<URL>"`)。ボタンを押すと `notify_action` の `name`
 *    としてそのまま返るので、通知が何枚出ていても**押した記事**が開く。`new.txt` の先頭を読む
 *    やり方だと、通知が複数あるとき常に最新の 1 本しか開けない。
 *
 * ⚠ この 2 つは**端末上で育った拡張をアプリへ取り込んだもの** (0.8.334)。同梱サンプルに無い
 * まま端末側だけが進んでいたため、`z2-macro list` が「差分あり」と言い続ける状態だった。
 */
private fun rssBody(d: String, t: CliText): String {
    val feedManager = rssFeedsScript(d, t)
    val cSubscriptions = rssFeedsLabel(t)
    val head = t("macro.rssBody.head",
        en = """
#
# Setup:
#   1) One feed URL per line in:  ~/.z2term/rss/feeds.txt
#   2) Poll every 30 minutes:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) Optional - one feed or word per line that must not get buried:  ~/.z2term/rss/important.txt
#      Anything matching gets a notification of its own, so a busy feed cannot push it out.
#      Write part of a URL or part of a title (e.g. example.org).
#   4) Register notification actions in Automation:
#        z2-when event:notify_action run '$rssNotificationAction'
#      Open sends that article to your browser; List opens everything collected, to read inside z2term.
#      Note: opening a screen from a notification button needs the "display over other apps" permission.
#   5) Read the collected list: sh ~/.z2term/macros/rss.sh view
#      Manage both rules in Command list -> Automation -> Automation rules.
#      Edit an existing notification rule instead of adding another one.
#   6) Notifications: by default the scheduled poll notifies and a fetch from the list does not.
#        sh ~/.z2term/macros/rss.sh notify off        # scheduled poll: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # Refresh / Articles on the list: on / off
#      Both are also switches under Settings on the list, to change at any time.
#
# Needs: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   Thumbnails (Settings on the list) also need Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# Battery: the more often you poll the more it costs. Do not go below 30 minutes.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
        ja = """
#
# 準備:
#   1) 読みたい URL を 1 行 1 本で書く:  ~/.z2term/rss/feeds.txt
#   2) 定期実行を仕掛ける (30 分ごと):
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) 見逃したくないフィード / 語を 1 行 1 本で書く (任意):  ~/.z2term/rss/important.txt
#      ここに当たった記事は 1 本ずつ別の通知になるので、流量の多いフィードに埋もれない。
#      書くのは URL の一部でも題名の一部でもよい (例: example.org)。
#   4) 通知の操作を自動化へ登録する:
#        z2-when event:notify_action run '$rssNotificationAction'
#      「開く」はその記事をブラウザへ、「一覧」は集めた記事を読み物にして z2term の中で開く。
#      ⚠ 通知のボタンから画面を開くには「他のアプリの上に表示」の許可が要る (エッジパネルと同じ)。
#   5) 集めた記事の一覧を開く: sh ~/.z2term/macros/rss.sh view
#      2 本のルールは「コマンド一覧 -> 自動化 -> 自動化ルール」で変更できる。
#      通知のルールが登録済みなら、追加せず既存のルールを編集する。
#   6) 新着の通知: 最初は、定期巡回では通知し、一覧から取りに行ったときは通知しない。
#        sh ~/.z2term/macros/rss.sh notify off        # 定期巡回の通知: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # 一覧の「更新」「記事一覧」の通知: on / off
#      どちらも一覧の「設定」のスイッチで、いつでも変えられる。
#
# 必要: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   サムネイル画像 (一覧の「設定」) を使うなら Pillow も (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# 電池: 取りに行くほど食う。30 分より短くしないこと。
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
"""
    )

    val cKeep = t("macro.cKeep", en = "max lines kept in seen/latest", ja = "seen/latest に残す行数の上限")
    val cNoPy = t("macro.cNoPy",
        en = "python3 is required (e.g. apk add python3)",
        ja = "python3 が要ります (apk add python3 など)"
    )
    val cWriteFeeds = t("macro.cWriteFeeds",
        en = "Write one feed URL per line in:",
        ja = "フィードの URL を 1 行に 1 本書いてください:"
    )
    val cFeedsHint = t("macro.cFeedsHint",
        en = "# One feed URL per line (lines starting with # are ignored)",
        ja = "# 1 行に 1 本、フィードの URL を書く (# で始まる行は無視)"
    )
    val cSkipFail = t("macro.cSkipFail",
        en = "silently skip a feed that fails to fetch or parse (the rest continue)",
        ja = "取得や解析に失敗した 1 本は黙って飛ばす (他のフィードは続ける)"
    )
    val cDiff = t("macro.cDiff",
        en = "# Subtract what we have seen to leave only what is new (same trick as z2scan's baseline diff).\n# Feed dates and ordering are not trusted; neither is reliable.",
        ja = "# 既読を引いて新着だけにする (z2scan のベースライン差分と同じやり方)。\n# フィードの日付や並び順は当てにしない — どちらも当てにならない。"
    )
    val cPrepend = t("macro.cPrepend",
        en = "# Stack newest-first so the widget's \"start (head)\" mode reads correctly.",
        ja = "# 新着が上に来るように積む (ウィジェットの「先頭 (head)」表示でそのまま読める)。"
    )
    val cNotify = t("macro.cNotify", en = "%s new", ja = "新着 %s 件")
    val cOpen = t("macro.cOpen", en = "Open", ja = "開く")
    val cListNone = t("macro.cListNone",
        en = "Nothing collected yet — run it once with no arguments first.",
        ja = "まだ何も集めていません。まず引数なしで実行してください。"
    )
    val cListHint = t("macro.cListHint",
        en = "List them with: rss.sh list [count]",
        ja = "一覧: rss.sh list [件数]"
    )
    val cList = t("macro.cList", en = "List", ja = "一覧")
    val cPageTitle = t("macro.cPageTitle",
        en = "Feeds", ja = "購読中のフィード"
    )
    val cPageSites = t("macro.cPageSites", en = "%s sites", ja = "%s サイト")
    val cPageArticles = t("macro.cPageArticles", en = "%s articles", ja = "記事 %s 本")
    /**
     * 読み物 (HTML) を作る理由。⚠ **通知は「気付く」ためのもので「読む」のには向かない**
     * (利用者の指摘)。⚠ サーバーは立てない — 読み物 1 枚のために常駐は増やさない。
     * z2-view がアプリの中で開く (JavaScript も外部通信も切った状態)。
     */
    val cPageDoc = t("macro.cPageDoc",
        en = "# Build the reading page (z2-view shows it inside the app; no server, no browser).\n" +
            "# Grouped by site, newest first, with an Open button per article. The phone's theme\n" +
            "# arrives as CSS variables, so the page follows the terminal colours.",
        ja = "# 読み物 (HTML) を作る。z2-view がアプリの中で開く (サーバーもブラウザも要らない)。\n" +
            "# サイトごとにまとめ、新しい順。1 本ずつに「開く」を置く。端末のテーマが CSS 変数で\n" +
            "# 届くので、配色は端末に揃う。"
    )
    val cViewHint = t("macro.cViewHint",
        en = "Read them with: rss.sh view",
        ja = "読み物: rss.sh view"
    )
    val cHitMax = t("macro.cHitMax",
        en = "max number of per-article notifications in one run (the rest go to the summary)",
        ja = "1 回に出す個別通知の上限 (超えた分はまとめ通知へ回す)"
    )
    val cImportantTemplate = t.lines("macro.cImportantTemplate",
        en = listOf(
            "# One feed or word per line that must not get buried (lines starting with # are ignored).",
                "# Part of a URL or part of a title works. Each match gets its own notification.",
                "# e.g.", "#example.org",
            ),
        ja = listOf(
            "# 見逃したくないフィード / 語を 1 行 1 本 (# で始まる行は無視)。",
                "# URL の一部でも題名の一部でもよい。当たった記事は 1 本ずつ別の通知になる。",
                "# 例:", "#example.org",
            )
    )
    val cSplitDoc = t("macro.cSplitDoc",
        en = "# Split off what must not be missed. The summary body only carries 3 lines, so a busy feed\n" +
            "# updating at the same time pushes the one that mattered out. Matches get **their own\n" +
            "# notification** (the app hands out a separate id per notification, so nothing is replaced).",
        ja = "# 見逃したくないものを切り分ける。まとめ通知は本文に 3 件しか載らないので、流量の多い\n" +
            "# フィードが同時に更新されると重要な 1 本が押し出される。当たった記事は**通知を分ける**\n" +
            "# (通知 ID はアプリ側で 1 件ずつ別に振られるので、分ければ上書きも省略もされない)。"
    )
    val cNameDoc = t("macro.cNameDoc",
        en = "# Put the URL in the notification's name: pressing the button hands it back as {name} in\n" +
            "# notify_action, so the right article opens even with several notifications on screen (setup 4).",
        ja = "# 通知の名前に URL を入れておく。ボタンを押すと notify_action の {name} でそのまま返るので、\n" +
            "# どの通知の「開く」なのかを取り違えずに開ける (準備 4 のルール)。"
    )
    val cListDoc = t("macro.cListDoc",
        en = "# Print what was collected as a readable list (no colour — it should look the same as the file/widget).\n" +
            "# latest.txt itself stays one-article-per-line raw data (the widget tail and rss-open.sh read it).",
        ja = "# 集めた記事を読みやすく並べて出す。色は使わない (ウィジェットやファイル表示と見え方を揃える)。\n" +
            "# 端末のときは OSC 8 で**題名そのものをリンク**にし、URL の行を並べない — 長い URL は\n" +
            "# 折り返して題名と混ざり、一覧として読めなくなるため。端末以外 (パイプ・リダイレクト) では\n" +
            "# エスケープが邪魔なので素のテキストに落とし、URL も見えるようにする。\n" +
            "# latest.txt は 1 行 1 記事の素データのままにしておく (ウィジェットの tail と rss-open.sh が読む)。"
    )

    val cSettings = t("macro.cSettings",
        en = "Settings",
        ja = "設定"
    )
    val cArchive = t("macro.cArchive",
        en = "Archive",
        ja = "アーカイブ"
    )
    val cDelete = t("macro.cDelete",
        en = "Delete",
        ja = "削除"
    )
    val cThumbs = t("macro.cThumbs",
        en = "Thumbnail images",
        ja = "サムネイル画像"
    )
    val cSave = t("macro.cSave",
        en = "Keep past articles",
        ja = "過去の記事の保存"
    )
    val cNotifyPoll = t("macro.cNotifyPoll",
        en = "Notify new articles (scheduled poll)",
        ja = "新着を通知（定期巡回）"
    )
    val cNotifyView = t("macro.cNotifyView",
        en = "Notify new articles (fetched from the list)",
        ja = "新着を通知（一覧から取りに行ったとき）"
    )
    val cDeleteSite = t("macro.cDeleteSite",
        en = "Delete all articles of this site",
        ja = "このサイトの記事をすべて削除"
    )
    val cConfirmSite = t("macro.cConfirmSite",
        en = "Remove %s articles of %h from the list?",
        ja = "%h の記事 %s 本を一覧から削除しますか？"
    )
    val cArticles = t("macro.cArticles",
        en = "Articles",
        ja = "記事一覧"
    )
    val cPrev = t("macro.cPrev",
        en = "Previous",
        ja = "前へ"
    )
    val cNext = t("macro.cNext",
        en = "Next",
        ja = "次へ"
    )
    val cCount = t("macro.cCount",
        en = "%s articles",
        ja = "%s 本"
    )
    val cUndated = t("macro.cUndated",
        en = "Undated",
        ja = "日付不明"
    )
    val cArchiveEmpty = t("macro.cArchiveEmpty",
        en = "Nothing kept yet. Turn on Keep past articles in Settings, and collected articles will stay here.",
        ja = "保存した記事はまだありません。「設定」の「過去の記事の保存」を「保存する」にすると、取得した記事がここに残ります。"
    )
    val cSearch = t("macro.cSearch",
        en = "Search",
        ja = "検索"
    )
    val cSearchWord = t("macro.cSearchWord",
        en = "Words (separate with spaces to require all of them)",
        ja = "探す言葉 (空白で区切ると、すべてを含む記事)"
    )
    val cByMonth = t("macro.cByMonth",
        en = "By month",
        ja = "月ごとに見る"
    )
    val cNoHit = t("macro.cNoHit",
        en = "Nothing found.",
        ja = "見つかりませんでした。"
    )
    val cDropMonth = t("macro.cDropMonth",
        en = "Delete this month",
        ja = "この月を削除"
    )
    val cDropConfirm = t("macro.cDropConfirm",
        en = "Delete the %s kept articles of %m? This cannot be undone.",
        ja = "%m の保存記事 %s 本を削除しますか？ 元に戻せません。"
    )
    val cOnOff = t("macro.cOnOff",
        en = "Specify on or off",
        ja = "on か off を指定してください"
    )
    val cNoPillow = t("macro.cNoPillow",
        en = "Thumbnail images need Pillow for Python (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
        ja = "サムネイル画像には Python の Pillow が要ります (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)"
    )

    return """$head
DIR="${d}HOME/.z2term/rss"
FEEDS="${d}DIR/feeds.txt"
SEEN="${d}DIR/seen.txt"
NEW="${d}DIR/new.txt"
LATEST="${d}DIR/latest.txt"
IMPORTANT="${d}DIR/important.txt"
HITS="${d}DIR/.hits"
REST="${d}DIR/.rest"
ARTICLES="${d}DIR/articles.tsv"
PAGE="${d}DIR/latest.html"
KEEP=500                                  # $cKeep
HITMAX=5                                  # $cHitMax
SETTINGS="${d}DIR/settings.conf"
THUMBDIR="${d}DIR/thumbs"
IMGHINTS="${d}DIR/.img"
# Whether to fetch thumbnail images (switched in Settings on the list). Off by default.
THUMBS=${d}(sed -n 's/^thumbs=//p' "${d}SETTINGS" 2>/dev/null | head -n 1)
[ "${d}THUMBS" = on ] || THUMBS=off
# Whether to keep every collected article (also in Settings). Off by default.
ARCHDIR="${d}DIR/archive"
ARCHIVE=${d}(sed -n 's/^archive=//p' "${d}SETTINGS" 2>/dev/null | head -n 1)
[ "${d}ARCHIVE" = on ] || ARCHIVE=off
# Whether new articles are notified (also in Settings): from the scheduled poll (on by default),
# and from a fetch inside the list screen, Refresh or Articles (off by default — the list is on screen).
NOTIFY=${d}(sed -n 's/^notify=//p' "${d}SETTINGS" 2>/dev/null | head -n 1)
[ "${d}NOTIFY" = off ] || NOTIFY=on
NOTIFY_VIEW=${d}(sed -n 's/^notify_view=//p' "${d}SETTINGS" 2>/dev/null | head -n 1)
[ "${d}NOTIFY_VIEW" = on ] || NOTIFY_VIEW=off
save_settings() {
  mkdir -p "${d}DIR" &&
    printf 'thumbs=%s\narchive=%s\nnotify=%s\nnotify_view=%s\n' "${d}THUMBS" "${d}ARCHIVE" "${d}NOTIFY" "${d}NOTIFY_VIEW" > "${d}SETTINGS"
}

$feedManager

$cPageDoc
build_page() {
  # 0.8.628 より前から使っている人のぶんは latest.txt から起こす (時刻は分からないので 0)。
  if [ ! -s "${d}ARTICLES" ] && [ -s "${d}LATEST" ]; then
    awk '{ url = ${d}NF; t = ${d}0; sub(/[ \t]+[^ \t]+${d}/, "", t); print "0\t" url "\t" t }' \
      "${d}LATEST" > "${d}ARTICLES"
  fi
  mkdir -p "${d}DIR" || return 1
  touch "${d}ARTICLES" || return 1
  # ${d}1 = where to write the controls (given only when the page is shown). Z2RSS_OPEN = site to leave open.
  python3 - "${d}ARTICLES" "${d}PAGE" "${d}THUMBDIR" "${d}THUMBS" "${d}ARCHIVE" "${d}{1:-}" "${d}{Z2RSS_OPEN:-}" \
    "$cPageTitle" "$cPageSites" "$cPageArticles" "$cOpen" "$cSubscriptions" "$cSettings" "$cArchive" "$cDelete" \
    "$cThumbs" "$cSave" \
    "$cDeleteSite" "$cConfirmSite" "${d}NOTIFY" "${d}NOTIFY_VIEW" "$cNotifyPoll" "$cNotifyView" <<'Z2RSS_HTML'
import base64, hashlib, html, json, sys, time, os, tempfile

src, dst, thumb_dir, thumbs, archive, controls, opened = sys.argv[1:8]
(title, l_sites, l_articles, l_open, l_feeds, l_settings, l_archive, l_delete,
 l_thumbs, l_keep,
 l_delete_site, l_confirm_site) = sys.argv[8:20]
notify, notify_view, l_notify, l_notify_view = sys.argv[20:24]

def key(url):
    return hashlib.sha1(url.encode("utf-8")).hexdigest()

# Images are embedded: the viewer blocks network loads, so a remote image cannot be shown.
# A total budget keeps the page light; the newest articles use it first.
budget = 2200000
def picture(url):
    global budget
    if thumbs != "on":
        return ""
    try:
        with open(os.path.join(thumb_dir, key(url) + ".jpg"), "rb") as f:
            data = f.read()
    except OSError:
        return ""
    size = (len(data) + 2) // 3 * 4
    if not data or size > budget:
        return ""
    budget -= size
    return ('<img alt="" width="96" height="64" src="data:image/jpeg;base64,'
            + base64.b64encode(data).decode("ascii") + '">')

def save(path, text):
    fd, pending = tempfile.mkstemp(dir=os.path.dirname(path), prefix=".page-")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            out.write(text)
        os.replace(pending, path)
    finally:
        if os.path.exists(pending):
            os.unlink(pending)

def switch(label, value):
    # A switch hands over "on" / "off", the same words the settings command takes.
    return {"label": label, "type": "switch", "default": value}

actions = [
    {"id": "feeds", "label": l_feeds, "args": ["feeds"]},
    {"id": "settings", "label": l_settings, "args": ["settings"],
     "fields": [switch(l_thumbs, thumbs), switch(l_keep, archive),
                switch(l_notify, notify), switch(l_notify_view, notify_view)]},
    {"id": "archive", "label": l_archive, "args": ["archive"]},
    # One action deletes any article: each link carries the article's key (z2-action:d/KEY).
    {"id": "d", "label": l_delete, "args": ["remove"], "param": True},
]

rows = []
for line in open(src, encoding="utf-8", errors="replace"):
    part = line.rstrip("\n").split("\t")
    if len(part) < 3:
        continue
    url = part[1].strip()
    # 端末の外から来た文字なので、http(s) 以外は載せない (javascript: などを作らせない)。
    if not url.startswith("http://") and not url.startswith("https://"):
        continue
    stamp = int(part[0]) if part[0].isdigit() else 0
    rows.append([stamp, url, "\t".join(part[2:]).strip() or url, picture(url)])

groups = {}
for row in rows:
    host = row[1].split("://", 1)[-1].split("/", 1)[0]
    groups.setdefault(host, []).append(row)

# A site's delete asks with its own name and count, so each site keeps one action of its own.
# The viewer accepts a limited number of actions; articles need none of their own.
site_actions = {}
for i, (host, items) in enumerate(groups.items()):
    if len(actions) < 495:
        site_actions[host] = "s" + str(i)
        actions.append({"id": "s" + str(i), "label": l_delete_site, "args": ["remove-site", host],
                        "confirm": l_confirm_site.replace("%h", host).replace("%s", str(len(items)))})

style = (
    '<style>'
    ':root{color-scheme:light dark}'
    'body{margin:0;background:var(--z2-bg,#fff);color:var(--z2-fg,#111);'
    'font:16px/1.55 -apple-system,"Noto Sans JP","Noto Sans",sans-serif}'
    'header{padding:14px 16px;border-bottom:1px solid var(--z2-line,#ddd)}'
    'h1{margin:0;font-size:17px;font-weight:600}'
    'header p{margin:3px 0 0;font-size:13px;color:var(--z2-dim,#666)}'
    'header nav{display:flex;gap:20px;margin-top:6px;font-size:14px}'
    'header nav a{color:var(--z2-accent,#0a7d3f);text-decoration:none;padding:8px 0}'
    'details{padding:0 16px}'
    'summary{position:sticky;top:0;padding:14px 0;font-size:14px;font-weight:600;'
    'color:var(--z2-accent,#0a7d3f);background:var(--z2-bg,#fff);'
    'border-bottom:1px solid var(--z2-line,#ddd)}'
    'summary span{float:right;font-weight:400;color:var(--z2-dim,#666)}'
    'article{display:flex;gap:12px;align-items:flex-start;padding:11px 0;'
    'border-bottom:1px solid var(--z2-line,#eee)}'
    'a.i{flex:none;line-height:0}'
    'a.i img{display:block;width:96px;height:64px;object-fit:cover}'
    'div.b{flex:1;min-width:0}'
    'a.t{display:block;color:inherit;text-decoration:none}'
    'p.m{display:flex;justify-content:space-between;align-items:center;'
    'margin:6px 0 0;font-size:12px;color:var(--z2-dim,#666)}'
    'p.m span{display:flex;gap:22px;align-items:center}'
    'a.x{color:inherit;text-decoration:none;padding:4px 0}'
    'p.s{margin:0;padding:8px 0;text-align:right;font-size:12px;color:var(--z2-dim,#666);'
    'border-bottom:1px solid var(--z2-line,#eee)}'
    'a.o{color:var(--z2-accent,#0a7d3f);text-decoration:none;'
    'border:1px solid currentColor;padding:3px 12px}'
    '</style>'
)

out = ['<!doctype html><html><head><meta charset="utf-8">',
       '<meta name="viewport" content="width=device-width,initial-scale=1">',
       '<title>' + html.escape(title) + '</title>', style, '</head><body>',
       '<header><h1>' + html.escape(title) + '</h1><p>'
       + html.escape(l_sites.replace("%s", str(len(groups)))) + ' · '
       + html.escape(l_articles.replace("%s", str(len(rows)))) + '</p>'
       # These live in the page: toolbar buttons stack vertically in a narrow panel and hide the articles.
       '<nav><a href="z2-action:feeds">' + html.escape(l_feeds) + '</a>'
       '<a href="z2-action:archive">' + html.escape(l_archive) + '</a>'
       '<a href="z2-action:settings">' + html.escape(l_settings) + '</a></nav></header>']
for host, items in groups.items():
    # One folded block per site. The viewer runs no JavaScript, so this is <details>.
    out.append('<details' + (' open' if host == opened else '') + '><summary>' + html.escape(host)
               + '<span>' + str(len(items)) + '</span></summary>')
    if host in site_actions:
        # Removes many at once, so the action asks first (confirm in the controls).
        out.append('<p class="s"><a class="x" href="z2-action:' + site_actions[host] + '">'
                   + html.escape(l_delete_site) + '</a></p>')
    for stamp, url, name, image in items:
        link = html.escape(url, quote=True)
        when = time.strftime("%m-%d %H:%M", time.localtime(stamp)) if stamp else ""
        thumb = '<a class="i" href="' + link + '">' + image + '</a>' if image else ''
        drop = '<a class="x" href="z2-action:d/' + key(url) + '">' + html.escape(l_delete) + '</a>'
        out.append('<article>' + thumb + '<div class="b">'
                   '<a class="t" href="' + link + '">' + html.escape(name) + '</a>'
                   '<p class="m"><time>' + when + '</time><span>' + drop
                   + '<a class="o" href="' + link + '">' + html.escape(l_open) + '</a></span></p></div></article>')
    out.append('</details>')
out.append('</body></html>')
save(dst, "\n".join(out))
if controls:
    save(controls, json.dumps({"handler": "rss.sh", "refresh": ["refresh-view"], "actions": actions},
                              ensure_ascii=False))
Z2RSS_HTML
}

# Remove articles from the list. They stay in seen.txt, so the next poll does not bring them back,
# and they stay in the archive if they were kept. Prints the site of what was removed.
# ${d}1 = key (one article; ${d}2 is the hash of its URL) / site (every article of a site; ${d}2 is the host).
remove_article() {
  python3 - "${d}ARTICLES" "${d}LATEST" "${d}1" "${d}2" <<'Z2RSS_DEL'
import hashlib, os, re, sys, tempfile

articles, latest, kind, wanted = sys.argv[1:5]
if kind not in ("key", "site") or not wanted or (kind == "key" and not re.fullmatch(r"[0-9a-f]{40}", wanted)):
    sys.exit(1)

def matches(url):
    if kind == "site":
        return url.split("://", 1)[-1].split("/", 1)[0] == wanted
    return hashlib.sha1(url.encode("utf-8")).hexdigest() == wanted

def rewrite(path, keep):
    try:
        lines = open(path, encoding="utf-8", errors="replace").readlines()
    except FileNotFoundError:
        return
    kept = [line for line in lines if keep(line)]
    if len(kept) == len(lines):
        return
    fd, pending = tempfile.mkstemp(dir=os.path.dirname(path), prefix=".del-")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            out.writelines(kept)
        os.replace(pending, path)
    finally:
        if os.path.exists(pending):
            os.unlink(pending)

gone = set()
def in_list(line):
    part = line.rstrip("\n").split("\t")
    if len(part) >= 3 and matches(part[1].strip()):
        gone.add(part[1].strip())
        return False
    return True

rewrite(articles, in_list)
# latest.txt lines are "title  URL": the URL is the last word.
rewrite(latest, lambda line: (line.split() or [""])[-1] not in gone)
for url in gone:
    print(url.split("://", 1)[-1].split("/", 1)[0])
    break
Z2RSS_DEL
}

# Keep what was collected in one file per month (only when Settings says to keep).
# The list holds the newest 500; this is never trimmed. Images are hard links to thumbs (no extra space).
archive_new() {
  [ "${d}ARCHIVE" = on ] || return 0
  mkdir -p "${d}ARCHDIR/thumbs" || return 0
  awk -F'\t' -v now="${d}1" '{ print now "\t" ${d}1 "\t" ${d}2 }' "${d}NEW" >> "${d}ARCHDIR/${d}(date +%Y-%m).tsv"
}

# When keeping starts, take in the articles and images already on the list.
archive_seed() {
  mkdir -p "${d}ARCHDIR/thumbs" || return 1
  touch "${d}ARTICLES"
  python3 - "${d}ARTICLES" "${d}ARCHDIR" "${d}THUMBDIR" <<'Z2RSS_SEED'
import hashlib, os, sys, tempfile, time

articles, archive, thumbs = sys.argv[1:4]

def rows(path):
    found = []
    for line in open(path, encoding="utf-8", errors="replace"):
        part = line.rstrip("\n").split("\t")
        if len(part) >= 3 and part[1].startswith(("http://", "https://")):
            found.append((int(part[0]) if part[0].isdigit() else 0, part[1], "\t".join(part[2:])))
    return found

months = {}
for name in os.listdir(archive):
    if name.endswith(".tsv"):
        months[name[:-4]] = rows(os.path.join(archive, name))
known = {url for items in months.values() for _, url, _ in items}

changed = set()
for stamp, url, name in rows(articles):
    if url in known:
        continue
    known.add(url)
    # Articles with no known arrival time share one bundle.
    month = time.strftime("%Y-%m", time.localtime(stamp)) if stamp else "0000-00"
    months.setdefault(month, []).append((stamp, url, name))
    changed.add(month)
    src = os.path.join(thumbs, hashlib.sha1(url.encode("utf-8")).hexdigest() + ".jpg")
    dst = os.path.join(archive, "thumbs", os.path.basename(src))
    if os.path.exists(src) and not os.path.exists(dst):
        try:
            os.link(src, dst)
        except OSError:
            pass

for month in changed:
    items = sorted(months[month], key=lambda row: row[0])
    fd, pending = tempfile.mkstemp(dir=archive, prefix=".seed-")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            out.writelines(str(s) + "\t" + u + "\t" + n + "\n" for s, u, n in items)
        os.replace(pending, os.path.join(archive, month + ".tsv"))
    finally:
        if os.path.exists(pending):
            os.unlink(pending)
Z2RSS_SEED
}

# The page for kept articles. One page for everything would be heavy: one month, 50 per page.
# ${d}1 = month, ${d}2 = page, ${d}3 = search words (when given, every month is searched).
view_archive() (
  mkdir -p "${d}ARCHDIR/thumbs" || exit 1
  arch_work=${d}(mktemp -d "${d}DIR/.archive-XXXXXXXX") || exit 1
  trap 'rm -rf "${d}arch_work"' 0
  python3 - "${d}ARCHDIR" "${d}arch_work" "${d}THUMBS" "${d}{1:-}" "${d}{2:-0}" "${d}{3:-}" \
    "$cArchive" "$cArticles" "$cOpen" "$cPrev" "$cNext" "$cCount" "$cUndated" \
    "$cArchiveEmpty" \
    "$cSearch" "$cSearchWord" "$cByMonth" "$cNoHit" \
    "$cDropMonth" "$cDropConfirm" <<'Z2RSS_ARCH'
import base64, hashlib, html, json, os, re, sys, time

archive, work, thumbs, month, page, query = sys.argv[1:7]
(title, l_back, l_open, l_prev, l_next, l_count, l_undated, l_empty,
 l_search, l_word, l_months, l_none, l_drop, l_drop_confirm) = sys.argv[7:21]
PER = 50

months = sorted((n[:-4] for n in os.listdir(archive) if re.fullmatch(r"\d{4}-\d{2}\.tsv", n)), reverse=True)
query = " ".join(query.split())[:200]
words = query.lower().split()
if words:
    month = ""
elif month not in months:
    month = months[0] if months else ""
page = int(page) if page.isdigit() and len(page) < 6 else 0

def load(name):
    found = []
    for line in open(os.path.join(archive, name + ".tsv"), encoding="utf-8", errors="replace"):
        part = line.rstrip("\n").split("\t")
        if len(part) >= 3 and part[1].startswith(("http://", "https://")):
            found.append((int(part[0]) if part[0].isdigit() else 0, part[1], "\t".join(part[2:]).strip() or part[1]))
    found.reverse()                         # newest first
    return found

rows = []
if words:
    # Search every month: articles whose title or URL contains all the words.
    for name in months:
        for row in load(name):
            text = (row[2] + " " + row[1]).lower()
            if all(word in text for word in words):
                rows.append(row)
elif month:
    rows = load(month)
pages = max(1, (len(rows) + PER - 1) // PER)
page = min(page, pages - 1)

def name_of(m):
    return l_undated if m == "0000-00" else m

def picture(url):
    if thumbs != "on":
        return ""
    name = hashlib.sha1(url.encode("utf-8")).hexdigest() + ".jpg"
    try:
        with open(os.path.join(archive, "thumbs", name), "rb") as f:
            data = f.read()
    except OSError:
        return ""
    return ('<img alt="" width="96" height="64" src="data:image/jpeg;base64,'
            + base64.b64encode(data).decode("ascii") + '">') if data else ""

actions = [{"id": "back", "label": l_back, "args": ["show"]}]
def link(action_id, label, args, current=False, **more):
    if current:
        return '<b>' + html.escape(label) + '</b>'
    actions.append(dict({"id": action_id, "label": label, "args": args}, **more))
    return '<a href="z2-action:' + action_id + '">' + html.escape(label) + '</a>'

style = (
    '<style>'
    ':root{color-scheme:light dark}'
    'body{margin:0;background:var(--z2-bg,#fff);color:var(--z2-fg,#111);'
    'font:16px/1.55 -apple-system,"Noto Sans JP","Noto Sans",sans-serif}'
    'header{padding:14px 16px;border-bottom:1px solid var(--z2-line,#ddd)}'
    'h1{margin:0;font-size:17px;font-weight:600}'
    'header p{margin:3px 0 0;font-size:13px;color:var(--z2-dim,#666);overflow-wrap:anywhere}'
    'nav{display:flex;flex-wrap:wrap;gap:6px 20px;align-items:center;font-size:14px}'
    'header nav{margin-top:6px}'
    'nav a{color:var(--z2-accent,#0a7d3f);text-decoration:none;padding:8px 0}'
    'nav.g{padding:6px 16px;border-bottom:1px solid var(--z2-line,#ddd)}'
    'nav.g span{color:var(--z2-dim,#666)}'
    'nav.g a.x{margin-left:auto;color:var(--z2-dim,#666);font-size:12px}'
    'main{padding:0 16px}'
    'article{display:flex;gap:12px;align-items:flex-start;padding:11px 0;'
    'border-bottom:1px solid var(--z2-line,#eee)}'
    'a.i{flex:none;line-height:0}'
    'a.i img{display:block;width:96px;height:64px;object-fit:cover}'
    'div.b{flex:1;min-width:0}'
    'a.t{display:block;color:inherit;text-decoration:none}'
    'p.m{display:flex;justify-content:space-between;align-items:center;gap:12px;'
    'margin:6px 0 0;font-size:12px;color:var(--z2-dim,#666)}'
    'a.o{flex:none;color:var(--z2-accent,#0a7d3f);text-decoration:none;'
    'border:1px solid currentColor;padding:3px 12px}'
    '</style>'
)

def turn(to):
    return ["archive-search", query, str(to)] if words else ["archive", month, str(to)]

def pager(tag):
    if pages < 2:
        return ''
    bits = []
    if page > 0:
        bits.append(link("prev" + tag, l_prev, turn(page - 1)))
    bits.append('<span>' + str(page + 1) + ' / ' + str(pages) + '</span>')
    if page < pages - 1:
        bits.append(link("next" + tag, l_next, turn(page + 1)))
    return '<nav class="g">' + ''.join(bits) + '</nav>'

what = '「' + query + '」' if words else name_of(month)
head = ['<a href="z2-action:back">' + html.escape(l_back) + '</a>']
if months:
    if words:
        head.append(link("months", l_months, ["archive"]))
    head.append(link("search", l_search, ["archive-search"],
                     fields=[{"label": l_word, "type": "text", "default": query, "required": True}]))
out = ['<!doctype html><html><head><meta charset="utf-8">',
       '<meta name="viewport" content="width=device-width,initial-scale=1">',
       '<title>' + html.escape(title) + '</title>', style, '</head><body>',
       '<header><h1>' + html.escape(title) + '</h1><p>'
       + html.escape((what + ' · ' if what else '') + l_count.replace("%s", str(len(rows))))
       + '</p><nav>' + ''.join(head) + '</nav></header>']
if not months:
    out.append('<main><p>' + html.escape(l_empty) + '</p></main>')
else:
    if not words:
        # The months. Plain text links, so many months do not weigh the page down.
        # One action opens any month; each link carries the month (z2-action:month/YYYY-MM).
        actions.append({"id": "month", "label": l_months, "args": ["archive"], "param": True})
        out.append('<nav class="g">' + ''.join(
            '<b>' + html.escape(name_of(m)) + '</b>' if m == month else
            '<a href="z2-action:month/' + m + '">' + html.escape(name_of(m)) + '</a>' for m in months[:200]) + '</nav>')
        # Removing a month cannot be undone, so the action asks first.
        out.append('<nav class="g">' + link(
            "drop", l_drop, ["archive-drop", month],
            confirm=l_drop_confirm.replace("%m", name_of(month)).replace("%s", str(len(rows)))
        ).replace('<a ', '<a class="x" ', 1) + '</nav>')
    out.append(pager("") + '<main>')
    if not rows:
        out.append('<p>' + html.escape(l_none) + '</p>')
    for stamp, url, name in rows[page * PER:(page + 1) * PER]:
        href = html.escape(url, quote=True)
        # A search crosses months, so its dates carry the year.
        when = time.strftime("%Y-%m-%d %H:%M" if words else "%m-%d %H:%M", time.localtime(stamp)) if stamp else ""
        host = url.split("://", 1)[-1].split("/", 1)[0]
        image = picture(url)
        thumb = '<a class="i" href="' + href + '">' + image + '</a>' if image else ''
        out.append('<article>' + thumb + '<div class="b">'
                   '<a class="t" href="' + href + '">' + html.escape(name) + '</a>'
                   '<p class="m"><span>' + html.escape(host) + (' · ' + when if when else '') + '</span>'
                   '<a class="o" href="' + href + '">' + html.escape(l_open) + '</a></p></div></article>')
    out.append('</main>' + pager("2"))
out.append('</body></html>')
with open(os.path.join(work, "page.html"), "w", encoding="utf-8") as f:
    f.write("\n".join(out))
refresh = turn(page) if words or month else ["archive"]
with open(os.path.join(work, "controls.json"), "w", encoding="utf-8") as f:
    json.dump({"handler": "rss.sh", "refresh": refresh, "actions": actions}, f, ensure_ascii=False)
Z2RSS_ARCH
  [ ${d}? -eq 0 ] || exit 1
  z2-view --controls "${d}arch_work/controls.json" "${d}arch_work/page.html" "$cArchive"
)

# Remove one month of kept articles and its images (images of articles still listed stay with the list).
drop_archive_month() {
  python3 - "${d}ARCHDIR" "${d}1" <<'Z2RSS_DROP'
import hashlib, os, re, sys

archive, month = sys.argv[1:3]
if not re.fullmatch(r"\d{4}-\d{2}", month):
    sys.exit(1)
path = os.path.join(archive, month + ".tsv")
try:
    lines = open(path, encoding="utf-8", errors="replace").readlines()
except FileNotFoundError:
    sys.exit(0)                             # already gone (a second tap)
for line in lines:
    part = line.rstrip("\n").split("\t")
    if len(part) >= 3:
        name = hashlib.sha1(part[1].encode("utf-8")).hexdigest() + ".jpg"
        try:
            os.unlink(os.path.join(archive, "thumbs", name))
        except OSError:
            pass
os.unlink(path)
Z2RSS_DROP
}

# Fetch a thumbnail per article and store it small (only when Settings says to fetch).
# The image is the one named in the feed, else the article page's og:image.
# Only the newest 60 are tried and one run stops after 10 seconds; the next poll fills the rest.
fetch_thumbs() {
  [ "${d}THUMBS" = on ] || return 0
  [ -s "${d}ARTICLES" ] || return 0
  mkdir -p "${d}THUMBDIR" || return 0
  touch "${d}IMGHINTS"
  kept=; [ "${d}ARCHIVE" = on ] && mkdir -p "${d}ARCHDIR/thumbs" && kept="${d}ARCHDIR/thumbs"
  python3 - "${d}ARTICLES" "${d}IMGHINTS" "${d}THUMBDIR" "${d}kept" <<'Z2RSS_THUMB'
import hashlib, html, io, os, re, sys, time, urllib.error, urllib.request
from concurrent.futures import ThreadPoolExecutor, wait
from urllib.parse import urljoin
try:
    from PIL import Image, ImageOps
except Exception:
    sys.exit(0)

Image.MAX_IMAGE_PIXELS = 40000000
articles, hints_file, out, kept = sys.argv[1:5]
AGENT = {"User-Agent": "Mozilla/5.0 (compatible; z2term-rss/1)"}
WEB = ("http://", "https://")

def key(url):
    return hashlib.sha1(url.encode("utf-8")).hexdigest()

urls = []
for line in open(articles, encoding="utf-8", errors="replace"):
    part = line.rstrip("\n").split("\t")
    if len(part) >= 3 and part[1].startswith(WEB):
        urls.append(part[1])

# Drop images of articles no longer listed, and leftovers of an interrupted run.
keep = {key(u) for u in urls}
for name in os.listdir(out):
    if name.split(".")[0] not in keep:
        try:
            os.unlink(os.path.join(out, name))
        except OSError:
            pass

hints = {}
for line in open(hints_file, encoding="utf-8", errors="replace"):
    part = line.rstrip("\n").split("\t")
    if len(part) == 2:
        hints.setdefault(part[0], part[1])

def get(url, limit):
    req = urllib.request.Request(url, headers=AGENT)
    with urllib.request.urlopen(req, timeout=6) as r:
        return r.read(limit + 1), r.geturl()

def og_image(url):
    data, final = get(url, 400000)
    text = data.decode("utf-8", "replace")
    for tag in re.findall(r"<meta\b[^>]*>", text, re.I):
        if not re.search(r'(?:property|name)\s*=\s*["\']?(?:og:image|twitter:image)["\'\s>]', tag, re.I):
            continue
        m = re.search(r'content\s*=\s*(?:"([^"]*)"|\'([^\']*)\')', tag, re.I)
        found = html.unescape((m.group(1) or m.group(2) or "") if m else "").strip()
        if found:
            return urljoin(final, found)
    return ""

def work(url):
    base = os.path.join(out, key(url))
    def none():
        # No image, or one that cannot be read: leave a marker so it is not tried every time.
        open(base + ".none", "w").close()
    try:
        image = hints.get(url) or og_image(url)
        if not image.startswith(WEB):
            return none()
        data, _ = get(image, 4000000)
    except urllib.error.HTTPError:
        return none()
    except Exception:
        # A network failure is retried later, an hour apart, so a dead host does not slow every poll.
        open(base + ".fail", "w").close()
        return
    try:
        if len(data) > 4000000:
            return none()
        im = Image.open(io.BytesIO(data))
        im.draft("RGB", (384, 256))
        im = im.convert("RGBA")
        flat = Image.new("RGB", im.size, (255, 255, 255))
        flat.paste(im, mask=im.split()[3])
        flat = ImageOps.fit(flat, (192, 128))
        pending = os.path.join(out, ".t-" + key(url))
        flat.save(pending, "JPEG", quality=62, optimize=True)
        os.replace(pending, base + ".jpg")
    except Exception:
        return none()
    if kept:
        # When articles are kept, a second name keeps the image after it leaves the list.
        try:
            os.link(base + ".jpg", os.path.join(kept, key(url) + ".jpg"))
        except OSError:
            pass

def recent(path):
    try:
        return time.time() - os.path.getmtime(path) < 3600
    except OSError:
        return False

need = [u for u in urls[:60]
        if not os.path.exists(os.path.join(out, key(u) + ".jpg"))
        and not os.path.exists(os.path.join(out, key(u) + ".none"))
        and not recent(os.path.join(out, key(u) + ".fail"))]
if need:
    pool = ThreadPoolExecutor(max_workers=6)
    wait([pool.submit(work, u) for u in need], timeout=10)
os._exit(0)                         # do not wait for what ran out of time
Z2RSS_THUMB
  return 0
}

case "${d}1" in
  feeds|feed-add|feed-edit|feed-remove)
    command -v python3 >/dev/null 2>&1 || { echo "$cNoPy" >&2; exit 1; }
    manage_feeds "${d}@"
    exit ${d}? ;;
esac

# Kept articles: rss.sh archive [month [page]]
#   search: rss.sh archive-search <words> [page]    remove a month: rss.sh archive-drop <month>
if [ "${d}1" = archive-drop ]; then
  drop_archive_month "${d}2" || exit 1
  set -- archive
fi
if [ "${d}1" = archive ] || [ "${d}1" = archive-search ]; then
  command -v python3 >/dev/null 2>&1 || { echo "$cNoPy" >&2; exit 1; }
  if [ "${d}1" = archive-search ]; then view_archive "" "${d}{3:-0}" "${d}2"; else view_archive "${d}2" "${d}3" ""; fi
  exit ${d}?
fi

# Delete on an article: remove just that one and show the list again with its site left open.
if [ "${d}1" = remove ]; then
  Z2RSS_OPEN=${d}(remove_article key "${d}2") || exit 1
  export Z2RSS_OPEN
  set -- show
fi
# Delete on a site: remove every listed article of that site.
if [ "${d}1" = remove-site ]; then
  remove_article site "${d}2" >/dev/null || exit 1
  set -- show
fi

# Notifications from the shell: rss.sh notify on|off (scheduled poll) / notify-view on|off (from the list).
# Only the choice is saved; nothing is shown or fetched.
if [ "${d}1" = notify ] || [ "${d}1" = notify-view ]; then
  case "${d}2" in on|off) ;; *) echo "$cOnOff" >&2; exit 1 ;; esac
  if [ "${d}1" = notify ]; then NOTIFY=${d}2; label="$cNotifyPoll"; else NOTIFY_VIEW=${d}2; label="$cNotifyView"; fi
  save_settings || exit 1
  echo "${d}label: ${d}2"
  exit 0
fi

# Settings on the list: thumbnails / keep collected articles / notify from the poll / notify from the list.
# An older caller hands over only the first two; the notification choices then stay as saved.
[ "${d}1" = set-thumbs ] && set -- settings "${d}2" "${d}ARCHIVE"
if [ "${d}1" = settings ]; then
  set -- settings "${d}2" "${d}3" "${d}{4:-${d}NOTIFY}" "${d}{5:-${d}NOTIFY_VIEW}"
  for v in "${d}2" "${d}3" "${d}4" "${d}5"; do
    case "${d}v" in on|off) ;; *) echo "$cOnOff" >&2; exit 1 ;; esac
  done
  if [ "${d}2" = on ] && ! python3 -c 'import PIL' 2>/dev/null; then
    echo "$cNoPillow" >&2
    exit 1
  fi
  was=${d}THUMBS
  THUMBS=${d}2; ARCHIVE=${d}3; NOTIFY=${d}4; NOTIFY_VIEW=${d}5
  save_settings || exit 1
  if [ "${d}3" = on ]; then archive_seed || exit 1; fi
  # Right after thumbnails are turned on, fetch them for the articles already listed.
  if [ "${d}2" = on ] && [ "${d}was" != on ]; then set -- refresh-view; else set -- show; fi
fi

if [ "${d}1" = view ] || [ "${d}1" = refresh-view ] || [ "${d}1" = show ]; then
  # From inside the list screen (Refresh, Articles) the feeds are polled before the list is built.
  # view from a notification or a terminal opens what is already collected.
  if [ "${d}1" = refresh-view ] || { [ "${d}1" = view ] && [ -n "${d}Z2_VIEW_SESSION" ]; }; then
    Z2RSS_NO_PAGE=1 Z2RSS_FROM_VIEW=1 sh "${d}0" || exit 1
  fi
  # The first view on a fresh install comes before any poll: the folder may not exist yet.
  mkdir -p "${d}DIR" || exit 1
  controls=${d}(mktemp "${d}DIR/.controls-XXXXXXXX") || exit 1
  trap 'rm -f "${d}controls"' 0
  build_page "${d}controls" || exit 1
  z2-view --controls "${d}controls" "${d}PAGE" "$cPageTitle"
  exit ${d}?
fi

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
if [ ! -f "${d}IMPORTANT" ]; then
  printf '%s\n' ${cImportantTemplate.joinToString(" \\\n    ") { "'" + it + "'" }} > "${d}IMPORTANT"
fi

: > "${d}DIR/.raw"
: > "${d}IMGHINTS"
while IFS= read -r url; do
  case "${d}url" in ''|'#'*) continue ;; esac
  # $cSkipFail
  python3 - "${d}url" "${d}IMGHINTS" "${d}THUMBS" >> "${d}DIR/.raw" <<'Z2RSS_PY'
import html, re, sys, urllib.request, xml.etree.ElementTree as ET

req = urllib.request.Request(sys.argv[1], headers={"User-Agent": "z2term-rss/1"})
try:
    with urllib.request.urlopen(req, timeout=20) as r:
        root = ET.fromstring(r.read())
except Exception:
    sys.exit(0)

# Note the image a feed names for each article, only when thumbnails are fetched.
hints = open(sys.argv[2], "a", encoding="utf-8") if sys.argv[3] == "on" else None

def picture(c, t):
    kind = (c.get("type") or "") + (c.get("medium") or "")
    if c.get("url") and (t == "thumbnail" or (t in ("content", "enclosure") and "image" in kind)):
        return c.get("url").strip()
    if t in ("encoded", "description", "content", "summary") and c.text:
        m = re.search(r'<img\b[^>]*?\bsrc\s*=\s*["\']([^"\']+)', c.text, re.I)
        if m:
            return html.unescape(m.group(1)).strip()
    return ""

for it in root.iter():
    if it.tag.split("}")[-1] not in ("item", "entry"):
        continue
    title = link = image = ""
    for c in it:
        t = c.tag.split("}")[-1]
        if t == "title" and not title:
            title = (c.text or "").strip()
        elif t == "link" and not link:
            link = (c.get("href") or c.text or "").strip()
        if hints and not image:
            image = picture(c, t)
    if link.startswith("http"):
        print(link + "\t" + (title or link))
        if hints and image.startswith("http") and not re.search(r"\s", image) and not re.search(r"\s", link):
            hints.write(link + "\t" + image + "\n")
Z2RSS_PY
done < "${d}FEEDS"

$cDiff
touch "${d}SEEN"
grep -Fxv -f "${d}SEEN" "${d}DIR/.raw" 2>/dev/null | grep . > "${d}NEW"
rm -f "${d}DIR/.raw"
n=${d}(grep -c . "${d}NEW" 2>/dev/null)
if [ "${d}{n:-0}" -eq 0 ]; then
  # Nothing new, but images still missing are fetched.
  fetch_thumbs
  rm -f "${d}IMGHINTS"
  exit 0
fi

cat "${d}NEW" "${d}SEEN" | head -n "${d}KEEP" > "${d}SEEN.t" && mv "${d}SEEN.t" "${d}SEEN"
$cPrepend
{ awk -F'\t' '{ print ${d}2 "  " ${d}1 }' "${d}NEW"; cat "${d}LATEST" 2>/dev/null; } \
  | head -n "${d}KEEP" > "${d}LATEST.t" && mv "${d}LATEST.t" "${d}LATEST"
# 読み物にはいつ来たかも要る (latest.txt は 1 行 1 記事の素データのままにしておく)。
stamp=${d}(date +%s)
{ awk -F'\t' -v now="${d}stamp" '{ print now "\t" ${d}1 "\t" ${d}2 }' "${d}NEW"; \
  cat "${d}ARTICLES" 2>/dev/null; } \
  | head -n "${d}KEEP" > "${d}ARTICLES.t" && mv "${d}ARTICLES.t" "${d}ARTICLES"
archive_new "${d}stamp"
fetch_thumbs
rm -f "${d}IMGHINTS"
# When called from the list screen, the caller builds the page next.
[ -n "${d}Z2RSS_NO_PAGE" ] || build_page || true

# Notify only when chosen: a fetch from the list follows notify_view, every other run follows notify.
if [ -n "${d}Z2RSS_FROM_VIEW" ]; then want=${d}NOTIFY_VIEW; else want=${d}NOTIFY; fi
if [ "${d}want" = on ]; then
$cSplitDoc
  : > "${d}HITS"
  if grep -q '^[^#]' "${d}IMPORTANT" 2>/dev/null; then
    grep -v '^[[:space:]]*#' "${d}IMPORTANT" | grep . > "${d}DIR/.pat"
    [ -s "${d}DIR/.pat" ] && grep -F -f "${d}DIR/.pat" "${d}NEW" | head -n "${d}HITMAX" > "${d}HITS"
    rm -f "${d}DIR/.pat"
  fi
  if [ -s "${d}HITS" ]; then
    grep -Fxv -f "${d}HITS" "${d}NEW" > "${d}REST"
  else
    cat "${d}NEW" > "${d}REST"
  fi

$cNameDoc
  TAB=${d}(printf '\t')
  while IFS="${d}TAB" read -r u t; do
    [ -n "${d}u" ] || continue
    host=${d}{u#*://}; host=${d}{host%%/*}
    z2-notify -h -n "rss:${d}u" -b "$cOpen" -b "$cList" "${d}{t:-${d}u}" "${d}host"
  done < "${d}HITS"

  rn=${d}(grep -c . "${d}REST" 2>/dev/null)
  if [ "${d}{rn:-0}" -gt 0 ]; then
    z2-notify -h -n "rss:${d}(head -1 "${d}REST" | cut -f1)" -b "$cOpen" -b "$cList" \
      "${d}(printf '$cNotify' "${d}rn")" "${d}(cut -f2 "${d}REST" | head -3)"
  fi
  rm -f "${d}HITS" "${d}REST"
fi

# 端末から直に走らせたときだけ出し方を案内する (自動実行のログを汚さない)。
# ⚠ ここを `[ -t 1 ] && echo` で終わらせない — 端末でないときに終了コードが 1 になり、
#   z2-when の記録が「失敗」になる。
if [ -t 1 ]; then
  echo "$cListHint"
  echo "$cViewHint"
fi
exit 0
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
private fun rssOpenBody(d: String, t: CliText): String {
    val head = t("macro.rssOpenBody.head",
        en = """
#
# Setup: put the collecting side in place first
#   z2-macro install rss
# Widget: assign "rss-open" to a button in the status widget's settings.
#
# z2-run: assign "rss-open" to a status-widget button (to try it here: sh ~/.z2term/macros/rss-open.sh)
""",
        ja = """
#
# 準備: 先に集める側を仕掛ける
#   z2-macro install rss
# ウィジェット: 状態ウィジェットの設定で「rss-open」をボタンに割り当てる。
#
# z2-run: 状態ウィジェットのボタンに rss-open を割り当てる (端末で試すなら sh ~/.z2term/macros/rss-open.sh)
"""
    )
    val cNone = t("macro.cNone", en = "No articles yet", ja = "まだ記事がありません")
    val cAllRead = t("macro.cAllRead", en = "Nothing new to open", ja = "新しい記事はありません")
    val cPick = t("macro.cPick",
        en = "# latest.txt lines are \"title  URL\". Take the first URL that has not been opened yet.",
        ja = "# latest.txt は「タイトル  URL」。まだ開いていない先頭の URL を 1 本だけ取る。"
    )
    val cCap = t("macro.cCap",
        en = "cap the opened list so it cannot grow forever",
        ja = "開いた記録が増え続けないよう上限をかける"
    )

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
private fun otpWhenBody(d: String, t: CliText): String {
    val copied = t("macro.copied", en = "Copied code: ${d}{code}", ja = "コードをコピー: ${d}{code}")
    val cleared = t("macro.cleared", en = "Cleared the copied code", ja = "コピーしたコードをクリアしました")
    val otpTitle = t("macro.otpTitle", en = "One-time code", ja = "認証コード")
    val cTtl = t("macro.cTtl",
        en = "seconds before the copy is cleared (only applies when it went in directly)",
        ja = "コピーから何秒でクリアするか (直に入れられたときだけ効く)"
    )
    val cCode = t("macro.cCode",
        en = "# The app already extracted the code. Do nothing when it could not.",
        ja = "# コードの抽出はアプリ側が済ませてある。取れなかったときは何もしない。"
    )
    val cTry = t("macro.cTry",
        en = "# Try to put it in directly. This only lands while you are **looking at the app**:\n" +
            "# Android 10+ lets only the app in front write to the clipboard, so a run in the\n" +
            "# background is dropped silently. Read it back to see whether it landed.",
        ja = "# まず直に入れてみる。入るのは**この画面を見ているとき**だけ — Android 10+ は前面の\n" +
            "# アプリしかクリップボードに書けないので、裏で走ったぶんは黙って捨てられる。\n" +
            "# 入ったかどうかは読み返して確かめる (書けなくてもエラーにはならないため)。"
    )
    val cFallback = t("macro.cFallback",
        en = "  # It did not land = you are not looking at the app. Hand it over through the\n" +
            "  # notification's \"Copy\" button instead (pressing it brings the app to the front).\n" +
            "  # ⚠ What goes in that way is not cleared by TTL below: clearing also needs the front.",
        ja = "  # 入らなかった = 画面を見ていない。通知の「コピー」ボタンで渡す (押した瞬間だけ前面に出る)。\n" +
            "  # ⚠ この道で入れたぶんは下の TTL では消えない — 消すのにも前面にいることが要るため。"
    )
    val cClear = t("macro.cClear",
        en = "# After TTL, clear the clipboard only if it still holds the code we copied\n" +
            "# (anything copied since then is left alone).",
        ja = "# TTL 秒後、クリップボードがコピー時の値のままなら空にする。\n" +
            "# その間に別のものをコピーしていたら、そちらは消さずに残す。"
    )
    return """
TTL=60                                    # $cTtl

$cCode
code=${d}Z2_WHEN_OTP
[ -n "${d}code" ] || exit 0

$cTry
z2-clip set "${d}code" 2>/dev/null
if [ "${d}(z2-clip get 2>/dev/null)" != "${d}code" ]; then
$cFallback
  z2-notify -h -c "${d}code" "$otpTitle" "${d}code"
  exit 0
fi
z2-toast "$copied"

$cClear
sleep "${d}TTL"
[ "${d}(z2-clip get 2>/dev/null)" = "${d}code" ] || exit 0
z2-clip set ""
z2-toast "$cleared"
"""
}

/**
 * 着信通知に含まれる電話番号をコピーボタンで渡すサンプル。
 * 電話帳は照合しない。登録済みでも番号が表示されていれば対象になる。
 * 既存ルールとの互換性のため、ファイル名 unknown-call.sh は維持する。
 */
private fun unknownCallBody(d: String, t: CliText): String {
    val cHow = t("macro.cHow",
        en = """
# ■ Copy phone numbers from call notifications
#   Check the title, then the text, for a bare phone number and offer a Copy button.
#   Saved and unsaved callers are both included; contacts are not queried.
#   A name alone is not enough to recover a number. Only notification access is used.
#   The filename unknown-call.sh is kept so existing rules continue to work.
""",
        ja = """
# ■ 着信通知の電話番号をコピーする
#   題名、本文の順に番号そのものを探し、通知の「コピー」ボタンで渡す。
#   電話帳への登録有無は区別しない。登録済みの相手でも番号が表示されていれば対象。
#   名前しか通知されない場合は番号を取得できない。使う権限は通知アクセスだけ。
#   既存のルールをそのまま使えるよう、ファイル名 unknown-call.sh は維持している。
"""
    )
    val cIsNum = t("macro.cIsNum",
        en = """
# Is it a bare number? Strip every character a phone number may use (digits + - ( ) space);
# if **nothing is left** and 7-15 digits remain, treat it as a number.
#   -> A field containing a name or other letters is not a bare number; check the other field.
#   -> "Unknown"/"Private number" also fall out here (loosen the test below if you want those).
# ⚠ Do not write this as case [!...]: the ) inside the pattern is read as the case separator.
""",
        ja = """
# 「番号そのもの」か? 電話番号で使う文字 (数字 + - ( ) 空白) を全部消して**何も残らず**、
# かつ数字が 7〜15 桁あれば番号とみなす。
#   → 名前などの文字が混ざる欄は番号そのものではないので、もう一方の欄を見る。
#   → 「非通知」「不明な発信者」も数字が足りずここで外れる (拾いたいなら下の判定を緩める)。
# ⚠ case の [!...] で書かないこと — パターン中の ) が case の区切りに読まれて構文エラーになる。
"""
    )
    val cScan = t("macro.cScan",
        en = "# The caller is usually the title, but some phone apps put it in the text. Check both.",
        ja = "# 発信者は題名に出るのが普通だが、電話アプリによっては本文側に出る。両方を見る。"
    )
    val cSkip = t("macro.cSkip",
        en = "# No bare number in either field: there is nothing to copy.",
        ja = "# どちらの欄にも番号そのものが無ければ、コピー通知を出さない。"
    )
    val cWhat = t("macro.cWhat",
        en = "# The category tells which one fired (same value as the notify:category= you registered).",
        ja = "# 着信中と不在着信のどちらで動いたかは種別で分かる (登録した notify:category= と同じ値)。"
    )
    val cCopy = t("macro.cCopy",
        en = """
# Hand the number over through the notification's "Copy" button (-c). Calling z2-clip set here
# would not land: Android 10+ only lets the app in front write to the clipboard, and during a
# call that is the phone app. Pressing the button brings z2term to the front for that instant.
""",
        ja = """
# 番号は**通知の「コピー」ボタン**で渡す (-c)。ここで z2-clip set を呼んでも入らない —
# Android 10+ は前面のアプリしかクリップボードに書けず、着信中に前面にいるのは電話アプリ
# だから。ボタンを押した瞬間だけ z2term が前面に出るので、そのときに確実に入る。
"""
    )
    val missed = t("macro.missed", en = "Missed call", ja = "不在着信")
    val incoming = t("macro.incoming", en = "Incoming call", ja = "着信")
    val title = t("macro.title", en = "Copy phone number", ja = "電話番号のコピー通知")
    return """$cHow$cIsNum
is_number() {
  [ -z "${d}(printf '%s' "${d}1" | tr -d '0-9+() -')" ] || return 1
  digits=${d}(printf '%s' "${d}1" | tr -cd '0-9')
  [ ${d}{#digits} -ge 7 ] && [ ${d}{#digits} -le 15 ]
}

$cScan
num=""
for s in "${d}Z2_WHEN_NOTI_TITLE" "${d}Z2_WHEN_NOTI_TEXT"; do
  if is_number "${d}s"; then num="${d}s"; break; fi
done

$cSkip
[ -n "${d}num" ] || exit 0

$cWhat
case "${d}Z2_WHEN_NOTI_CATEGORY" in
  missed_call) what="$missed" ;;
  *)           what="$incoming" ;;
esac
$cCopy
z2-notify -h -c "${d}num" "${d}{what}: $title" "${d}num"
"""
}

/** `z2-macro` CLI 本体。同梱サンプルの一覧 / 導入 / 表示 / 実行。 */
fun z2MacroScript(lang: String): String {
    // 言語ごとの文言を選ぶ道具。英語と日本語はここ、他の言語は proot/lang/ の言語ファイルに名前で置く ([CliText])。
    val t = CliText(lang)
    val d = "${'$'}"
    val src = "/usr/local/share/z2term/macros"

    val usage = t.lines("macro.usage",
        en = listOf(
            "usage: z2-macro <subcommand>",
                "  list                list bundled samples (marked new / same / differs)",
                "  install <name|all>  copy into ~/.z2term/macros/ (never overwrites; -f to force)",
                "  remove <name>       delete your copy of a bundled sample (stops if it differs; -f to force)",
                "  diff <name>         what differs between your copy and the bundled one (yours on the left)",
                "  show <name>         print the script",
                "  run <name>          run it here (Ctrl-C to stop)",
                "  dir                 print where macros live",
                "  actions ...         Android action macros (z2-action --help)",
            ),
        ja = listOf(
            "usage: z2-macro <サブコマンド>",
                "  list                同梱サンプルの一覧 (未導入 / 同じ / 差分あり つき)",
                "  install <名前|all>  ~/.z2term/macros/ へコピー (既存は上書きしない。-f で上書き)",
                "  remove <名前>       入れた同梱サンプルを消す (同梱版と違うときは止まる。-f で消す)",
                "  diff <名前>         端末のコピーと同梱版の違いを見る (左が自分の側)",
                "  show <名前>         中身を表示",
                "  run <名前>          その場で実行 (Ctrl-C で止める)",
                "  dir                 マクロの置き場所を表示",
                "  actions ...         Android操作マクロ (z2-action --help)",
            )
    )

    // `list` の状態列。
    //
    // ⚠ 3 つ目を「要更新」と書かないこと。分かるのは**同梱版と違う**ことだけで、どちらが
    // 新しいかは分からない。実際、端末側の `rss.sh` が同梱版より機能が多い (アプリへ
    // 取り込んでいない拡張がある) 例があり、「要更新」に釣られて `-f` を打つと消える。
    // ⚠ 幅揃えは **printf に任せない**。`%-Ns` が数えるのは**バイト数**で、全角 1 文字は
    // 3 バイト・見た目 2 桁なので、`未導入` と `同じ` を同じ `%-12s` に流すと説明の開始位置が
    // 2 桁ずれる (0.8.333 で実機の出力を見て気付いた)。ここで見た目の桁を数えて空白を足す。
    fun padVisual(s: String, cols: Int): String =
        s + " ".repeat((cols - s.sumOf { if (it.code < 0x80) 1 else 2 }).coerceAtLeast(0))

    val stCols = t.of("macro.stCols", en = 7, ja = 8)
    val stNew = padVisual(t("macro.stNew", en = "new", ja = "未導入"), stCols)
    val stSame = padVisual(t("macro.stSame", en = "same", ja = "同じ"), stCols)
    val stDiff = padVisual(t("macro.stDiff", en = "differs", ja = "差分あり"), stCols)

    val msgInstalled = t("macro.msgInstalled", en = "installed:", ja = "導入しました:")
    // 「既にある」を**同じ / 違う**で言い分ける (0.8.332)。一律「既にあります」だと、同梱版が
    // 直っていても気付けず、古いコピーを使い続けることになる (remind.sh で実際に起きた)。
    val msgSame = t("macro.msgSame", en = "the same thing is already installed:", ja = "同じ内容がすでに入っています:")
    val msgOutdated = t("macro.msgOutdated", en = "yours differs from the bundled one:", ja = "同梱版と中身が違います:")
    // ⚠ 「同梱版が新しい」と断定しない。端末側の方が進んでいることが実際にある
    //    (アプリへ取り込んでいない拡張)。**先に diff** を見せてから -f を出す順にする。
    val msgHowUpdate = t("macro.msgHowUpdate",
        en = "look first: z2-macro diff %s   /   replace with the bundled one: z2-macro install -f %s (your own edits go too)",
        ja = "まず違いを見る: z2-macro diff %s   /   同梱版で置き換える: z2-macro install -f %s (自分で書き換えた分は消えます)"
    )
    val msgNotInstalled = t("macro.msgNotInstalled",
        en = "not installed yet (run z2-macro install first):",
        ja = "まだ導入していません (先に z2-macro install):"
    )
    // `remove` (1.0.6)。案内の片付けから呼ぶが、install の対になる一般のコマンド。
    // ⚠ **同梱版と違うコピーは黙って消さない。** 自分で書き換えた分かもしれず、消すと戻せない
    //    (同じものなら install で入れ直せる)。違うときは止めて、見る手段と消す手段を出す。
    val msgRemoved = t("macro.msgRemoved", en = "removed:", ja = "消しました:")
    val msgAbsent = t("macro.msgAbsent", en = "not installed:", ja = "入っていません:")
    val msgRemoveDiffers = t("macro.msgRemoveDiffers",
        en = "yours differs from the bundled one, so it was not removed:",
        ja = "同梱版と中身が違うので消していません:"
    )
    val msgHowRemove = t("macro.msgHowRemove",
        en = "look first: z2-macro diff %s   /   remove it anyway: z2-macro remove -f %s (your own edits go too)",
        ja = "まず違いを見る: z2-macro diff %s   /   それでも消す: z2-macro remove -f %s (自分で書き換えた分も消えます)"
    )
    val msgNoDiffTool = t("macro.msgNoDiffTool",
        en = "no diff here. You can still read the bundled one with z2-macro show.",
        ja = "この環境に diff がありません。中身は z2-macro show で見られます。"
    )
    val msgSameAsBundled = t("macro.msgSameAsBundled", en = "identical to the bundled one.", ja = "同梱版と同じです。")
    val msgNotFound = t("macro.msgNotFound", en = "no such sample:", ja = "そんなサンプルはありません:")
    val msgHintResident = t("macro.msgHintResident",
        en = "To keep it running, register this under Settings -> Resident servers:",
        ja = "常駐させるには ⚙設定 → 常駐サーバー に次を登録してください:"
    )
    // 常駐させないサンプル (時刻トリガーで 1 回走るもの・ウィジェットのボタンから叩くもの) がある。
    // そういうスクリプトは先頭に `# z2-run: <動かし方>` を書いておき、install はそれを出す。
    // ⚠ 一律に「常駐サーバーに登録」と案内すると、**使い切りのスクリプトを常駐させてしまう**
    //   (終了するたび supervisor が再起動するので、フィード取得なら延々と取りに行く)。
    val msgHintRun = t("macro.msgHintRun", en = "How to run it:", ja = "動かし方:")

    return """
        |#!/bin/sh
        |# z2term マクロ管理 (同梱サンプルの導入)。マクロの書き方は docs の MACRO-GUIDE を参照。
        |SRC=$src
        |DEST=${d}HOME/.z2term/macros
        |usage() {
        |${usage.joinToString("\n|") { "  echo '${it.replace("'", "'\\''")}' >&2" }}
        |  exit 1
        |}
        |# `--help` は**間違いではない**ので、標準出力へ出して 0 で終わる (usage は stderr + 1)。
        |helpme() {
        |${usage.joinToString("\n|") { "  echo '${it.replace("'", "'\\''")}'" }}
        |  exit 0
        |}
        |# 端末のコピー (${d}2) が同梱版 (${d}1) と同じ中身か。
        |# ⚠ 比べる手段が無いときは「違う」と答える。「同じ」と嘘をつくと、直った同梱版があるのに
        |#   一生気付けない (remind.sh が 2 週間ぶん古いまま使われていた実例)。
        |same_as_bundled() {
        |  [ -f "${d}2" ] || return 1
        |  if command -v cmp >/dev/null 2>&1; then cmp -s "${d}1" "${d}2"; return; fi
        |  a=${d}(cksum < "${d}1" 2>/dev/null); b=${d}(cksum < "${d}2" 2>/dev/null)
        |  [ -n "${d}a" ] && [ "${d}a" = "${d}b" ]
        |}
        |# Delegate before sample-name normalization so file paths retain spaces.
        |if [ "${d}{1:-}" = actions ]; then shift; exec z2-action "${d}@"; fi
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
        |      # 状態を出す (0.8.332)。install は既存を上書きしない = 一度入れたコピーは
        |      # **同梱版が直っても黙ってそのまま**になるので、一覧の時点で違いが分かるようにする。
        |      if [ ! -f "${d}DEST/${d}name" ]; then st='$stNew'
        |      elif same_as_bundled "${d}SRC/${d}name" "${d}DEST/${d}name"; then st='$stSame'
        |      else st='$stDiff'
        |      fi
        |      printf '%-18s %s %s\n' "${d}name" "${d}st" "${d}desc"
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
        |        # 「既にあります」で終わらせない。同じなら安心してよく、違うなら**同梱版が
        |        # 直っている**可能性があるので、次に打つ手まで出す。
        |        if same_as_bundled "${d}SRC/${d}name" "${d}DEST/${d}name"; then
        |          echo "$msgSame ${d}DEST/${d}name" >&2
        |        else
        |          echo "$msgOutdated ${d}DEST/${d}name" >&2
        |          printf '  $msgHowUpdate\n' "${d}name" "${d}name" >&2
        |        fi
        |        continue
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
        |  remove)
        |    # 同梱サンプルのコピーだけを消す。自分で書いたマクロ (同梱に無い名前) には触らない。
        |    [ ${d}# -ge 2 ] || usage
        |    shift
        |    for name in "${d}@"; do
        |      case "${d}name" in *.sh) ;; *) name="${d}name.sh" ;; esac
        |      if [ ! -f "${d}SRC/${d}name" ]; then echo "$msgNotFound ${d}name" >&2; continue; fi
        |      if [ ! -f "${d}DEST/${d}name" ]; then echo "$msgAbsent ${d}DEST/${d}name"; continue; fi
        |      if [ "${d}force" != "1" ] && ! same_as_bundled "${d}SRC/${d}name" "${d}DEST/${d}name"; then
        |        echo "$msgRemoveDiffers ${d}DEST/${d}name" >&2
        |        printf '  $msgHowRemove\n' "${d}name" "${d}name" >&2
        |        continue
        |      fi
        |      rm -f "${d}DEST/${d}name" && echo "$msgRemoved ${d}DEST/${d}name"
        |    done ;;
        |  diff)
        |    # 左が端末のコピー・右が同梱版 (`-` が自分の側、`+` が同梱版で増えた行)。
        |    # ⚠ -f で上書きする前に**自分で書き換えていないか**を見るためのもの。
        |    [ ${d}# -ge 2 ] || usage
        |    name="${d}2"; case "${d}name" in *.sh) ;; *) name="${d}name.sh" ;; esac
        |    [ -f "${d}SRC/${d}name" ] || { echo "$msgNotFound ${d}name" >&2; exit 1; }
        |    [ -f "${d}DEST/${d}name" ] || { echo "$msgNotInstalled ${d}DEST/${d}name" >&2; exit 1; }
        |    if same_as_bundled "${d}SRC/${d}name" "${d}DEST/${d}name"; then
        |      echo "$msgSameAsBundled"; exit 0
        |    fi
        |    command -v diff >/dev/null 2>&1 || { echo "$msgNoDiffTool" >&2; exit 1; }
        |    diff "${d}DEST/${d}name" "${d}SRC/${d}name" ;;
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
        |  -h|--help|help) helpme ;;
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
internal const val ASK_TRIES = 3

private fun remindBody(d: String, t: CliText): String {
    val head = t("macro.remindBody.head",
        en = """
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
#   sat 21:00                       next matching weekday, once (later today also counts)
#   Japanese: 土曜日の夜九時 = sat 21:00; 朝七時 = 07:00; 夜九時半 = 21:30
#   Japanese: 夜12時 is ambiguous; use 00:00 with an explicit day for midnight.
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
""",
        ja = """
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
#   土曜日の夜九時 / 土曜 21:00    次の該当曜日に1回 (今日でも時刻が先なら今日)
#   朝七時 / 夜九時半                07:00 / 21:30 (数字・漢数字・全角数字に対応)
#   午前十二時 / 午後十二時          00:00 / 12:00。夜12時は日付と00:00で指定
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
"""
    )
    val cStore = t("macro.cStore",
        en = """# One reminder = one line in ${d}DIR/<id>.txt, tab separated: kind / label / when-id / text.
#   kind: once (still to fire) / fired (already fired) / repeat (recurring)
#   when-id: only for repeat (so delete can call z2-when remove). '-' otherwise.
# The text goes last so it can hold anything but a TAB.""",
        ja = """# 予定 1 件 = ${d}DIR/<id>.txt の 1 行。TAB 区切りで  種別 / 予定の表記 / when の id / 本文。
#   種別: once (これから鳴る単発) / fired (鳴った単発) / repeat (繰り返し)
#   when の id: repeat のときだけ入る (削除で z2-when remove するため)。それ以外は '-'。
# 本文を末尾に置くのは、TAB 以外の文字をそのまま持たせるため。"""
    )
    val cParse = t("macro.cParse",
        en = """# Read 1-3 words and decide:
#   KIND ... once|repeat   PLAN ... label to show   SPEC ... what z2-alarm/z2-when takes   USED ... words used""",
        ja = """# 引数 (1〜3 語) を読んで下記を決める。
#   KIND … once|repeat   PLAN … 一覧に出す表記   SPEC … z2-alarm/z2-when へ渡す形   USED … 使った語数"""
    )
    val pDaily = t("macro.pDaily", en = "daily ", ja = "毎日 ")
    val pWeekday = t("macro.pWeekday", en = "weekdays ", ja = "平日 ")
    val pWeekly = t("macro.pWeekly", en = "weekly ", ja = "毎週")
    val pMonthly = t("macro.pMonthly", en = "monthly ", ja = "毎月 ")
    val pYearly = t("macro.pYearly", en = "yearly ", ja = "毎年 ")
    val mDay = t("macro.mDay", en = "", ja = "日")
    // 「毎 …」の展開先。⚠ **その言語で case が拾える語**であること (日本語面なら「毎日」等)。
    val eDaily = t("macro.eDaily", en = "daily", ja = "毎日")
    val eWeekly = t("macro.eWeekly", en = "weekly", ja = "毎週")
    val eMonthly = t("macro.eMonthly", en = "monthly", ja = "毎月")
    val eYearly = t("macro.eYearly", en = "yearly", ja = "毎年")
    val mBadDate = t("macro.mBadDate",
        en =         "no such date (e.g. 07/30):",
        ja =         "その日付はありません (例: 07/30):"
    )
    val cEvery = t("macro.cEvery",
        en = """  # The short "every ..." form. **The shape of the next word** picks daily/weekly/monthly/yearly:
  #   every 19:00 -> daily / every wed 19:00 -> weekly / every 15 19:00 -> monthly /
  #   every 07/30 19:00 -> yearly
  # ⚠ The word count does not change (one word swapped for another), so USED still holds.""",
        ja = """  # 「毎 …」の簡易指定 (要望)。**次の語の形**で 毎日 / 毎週 / 毎月 / 毎年 を決める。
  #   毎 19:00 → 毎日 / 毎 水 19:00 → 毎週 / 毎 15 19:00 → 毎月 / 毎 07/30 19:00 → 毎年
  # ⚠ 語数は変わらない (「毎」1 語が「毎日」等に置き換わるだけ) ので USED はそのままでよい。"""
    )
    val cExpand = t("macro.cExpand",
        en = "# Decide which recurrence \"every\" meant, from the word that follows it.",
        ja = "# 「毎」の次に来た語から、どの繰り返しかを決める。"
    )
    val cDigits = t("macro.cDigits",
        en = """    # All-digit forms: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ Check 12 digits before 8 (the 8-digit pattern also matches a 12-digit string).""",
        ja = """    # 数字だけで書く形 (要望): 202607301900 = 年月日時分 / 07301900 = 月日時分。
    # ⚠ 12 桁 → 8 桁の順に見る (8 桁のパターンは 12 桁にも当たってしまうため)。"""
    )
    val cYmd = t("macro.cYmd",
        en = """  # Written as a date. ⚠ z2-alarm cannot take a date, so this also folds into a delay.""",
        ja = """  # 年月日で書かれたもの。⚠ z2-alarm は日付を渡せないので、ここでも秒差へ寄せる。"""
    )
    val cCivil = t("macro.cCivil",
        en = """# Civil date -> days since 1970-01-01 (leap years included; Howard Hinnant's days_from_civil).
# ⚠ date -d "2026-07-30" is not dependable on busybox, so the count is done here.
# ⚠ Only the day difference is used and the time is left to [day_epoch], which cancels out the zone.""",
        ja = """# 年月日 → 1970-01-01 からの通算日 (うるう年込み・Howard Hinnant の days_from_civil)。
# ⚠ date -d "2026-07-30" は busybox で当てにならないので自前で数える。
# ⚠ 日数の差だけを使い、時刻は [day_epoch] に任せる (こうするとタイムゾーンが相殺される)。"""
    )
    val cCron = t("macro.cCron",
        en = """# Build a cron expression (min hour dom month dow) from HH:MM and a weekday field.
# Do NOT use expr to strip a leading zero: it exits 1 when the result is 0, so the right-hand
# side of `expr "${d}m" + 0 || echo "${d}m"` also runs and the value ends up two lines long.""",
        ja = """# HH:MM と曜日欄から cron 式を作る (分 時 日 月 曜日)。
# ⚠ 先頭 0 を落とすのに expr を使わないこと — 結果が 0 のとき終了コードが 1 になり、
#   `expr "${d}m" + 0 || echo "${d}m"` の右側まで走って値が 2 行に化ける (実際に踏んだ)。"""
    )
    val afterLabel = t("macro.afterLabel",
        en = """# "30m" -> "in 30m (14:35)". Showing the wall-clock time lets you check it right away.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "in ${d}num${d}u (${d}at)" || echo "in ${d}num${d}u"
}""",
        ja = """# "30m" → "30分後 (14:35)"。今の時刻を足して見せるのは、登録した直後に確かめられるように。
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num; unit=秒 ;; m) sec=${d}((num*60)); unit=分 ;; h) sec=${d}((num*3600)); unit=時間 ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "${d}num${d}unit後 (${d}at)" || echo "${d}num${d}unit後"
}"""
    )
    val mUsageAdd = t("macro.mUsageAdd",
        en = "usage: remind.sh <when> <text>",
        ja = "usage: remind.sh <いつ> <本文>"
    )
    val mBadWhen = t("macro.mBadWhen",
        en = "cannot read the time (try: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
        ja = "いつ？ が分かりません (例: 30m / 18:30 / 土曜日の夜九時 / 明日 18:30 / 3日後 09:00 / 毎日 07:00):"
    )
    val mBadTime = t("macro.mBadTime", en = "write the time as HH:MM:", ja = "時刻は HH:MM で書いてください:")
    val mBadRange = t("macro.mBadRange",
        en =         "time out of range (00:00-23:59):",
        ja =         "時刻の範囲が違います (00:00〜23:59):"
    )
    val mNoTime = t("macro.mNoTime",
        en =         "no time given (e.g. daily 07:00):",
        ja =         "時刻が書かれていません (例: 毎日 07:00):"
    )
    val mBadDow = t("macro.mBadDow", en = "unknown weekday:", ja = "曜日が分かりません:")
    val mPastTime = t("macro.mPastTime",
        en =         "that time has already passed:",
        ja =         "その時刻はもう過ぎています:"
    )
    val cDays = t("macro.cDays",
        en = """    # Day-based wording. With no time, keep **the current time of day** (never invent a default).
    # It can also arrive as a single word, so the glued form is accepted too.""",
        ja = """    # 日付で書く言い方。時刻を省いたら**今と同じ時刻**にする (既定時刻を勝手に決めない)。
    # 「明日の18:30」のように 1 語で来ることもあるので、くっついた形も受ける。"""
    )
    val cDayEpoch = t("macro.cDayEpoch",
        en = """# Turn "HH:MM, N days from now" into epoch seconds. ⚠ busybox has no date -d "tomorrow",
# so today's midnight is derived first and the days and time added on top.
# ⚠ A value with a leading zero ("08") is read as octal by ${d}(()), so strip it.
# ⚠ A day is added as 86400s, so it can be an hour off on a DST switch day.""",
        ja = """# 「N 日後の HH:MM」を epoch 秒にする。⚠ date -d "tomorrow" は busybox に無いので、
# 今日の 0 時を出してから日数と時刻を足す。
# ⚠ 先頭 0 の付いた値 ("08") を ${d}(()) に渡すと 8 進数と解釈されるので必ず落とす。
# ⚠ 1 日 = 86400 秒として足すので、夏時間のある地域では切り替え日に 1 時間ずれる。"""
    )
    val cFmtAt = t("macro.cFmtAt",
        en = """# The label shown in the list. ⚠ Keeping "tomorrow" would read wrong once the date rolls over.""",
        ja = """# 一覧に出す日時。⚠ 「明日」のまま覚えると日付が変わった後にズレて見えるので、実日付にする。"""
    )
    val mNoBody = t("macro.mNoBody", en = "say what to remind you about", ja = "リマインドの本文を書いてください")
    val mNoAlarm = t("macro.mNoAlarm", en = "could not schedule it", ja = "予約できませんでした")
    val mNone = t("macro.mNone", en = "nothing scheduled", ja = "予定はありません")
    val mFired = t("macro.mFired", en = " (fired)", ja = " (通知済)")
    val mRemoved = t("macro.mRemoved", en = "removed:", ja = "消しました:")
    val mNoSuchNum = t("macro.mNoSuchNum",
        en =         "no such entry (check remind.sh list):",
        ja =         "その番号はありません (remind.sh list で確認):"
    )
    val mUsageDel = t("macro.mUsageDel",
        en = "usage: remind.sh del <n|all>",
        ja = "usage: remind.sh del <番号|all>"
    )
    val bDone = t("macro.bDone", en = "Done", ja = "完了")
    // ⚠ 通知のボタンは 1 語で書く (空白があると引数が割れる)。
    val bDelete = t("macro.bDelete", en = "Delete", ja = "消す")
    val mAskDel = t("macro.mAskDel", en = "Remove which one?", ja = "どれを消す？")
    val mAskDelH = t("macro.mAskDelH", en = "number / all", ja = "番号 / all")
    val mDeletedTitle = t("macro.mDeletedTitle", en = "🗑 Removed", ja = "🗑 消しました")
    val cPeek = t("macro.cPeek",
        en = """# Show the list in a notification (from the "list" tile). **Keep the numbers** — they are what
# you point at to remove one. ⚠ Listing without a way to remove was the complaint; hence the button.""",
        ja = """# 一覧を通知で見せる (タイル「予定」から)。**番号を残す** — 消すときに指すものだから。
# ⚠ 一覧を出したのに消す手段が無く、set したら消せないと言われた (要望) ので「消す」を付ける。"""
    )
    val cAskDel = t("macro.cAskDel",
        en = """# The "delete" button: ask for a number and hand it to [cmd_del] (the same path as the CLI).""",
        ja = """# 「消す」ボタン。番号を聞いて [cmd_del] へ渡すだけ (端末の remind.sh del と同じ経路)。"""
    )
    val bS1 = t("macro.bS1", en = "+10min", ja = "10分後")
    val bS2 = t("macro.bS2", en = "+1h", ja = "1時間後")
    val mAgain = t("macro.mAgain", en = "- again in", ja = "にもう一度:")
    val mTitle = t("macro.mTitle", en = "⏰ Reminders", ja = "⏰ リマインド")
    val mAsk1 = t("macro.mAsk1", en = "Remind you about what?", ja = "何をリマインド？")
    val mAsk1H = t("macro.mAsk1H", en = "e.g. take pills", ja = "例: 薬を飲む")
    val mAsk2 = t("macro.mAsk2", en = "When?", ja = "いつ？")
    val mAsk2H = t("macro.mAsk2H",
        en = "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
        ja = "30m / 土曜日の夜九時 / 朝七時 / 明日 18:30 / 毎日 07:00"
    )
    val mAskAgain = t("macro.mAskAgain", en = "please enter it again", ja = "もう一度入力してください")
    val mAskGiveUp = t("macro.mAskGiveUp",
        en = "Could not read it ${ASK_TRIES} times. You can also add it from the terminal: remind.sh 30m take pills",
        ja = "${ASK_TRIES} 回とも読めませんでした。端末からも登録できます: remind.sh 30m 薬を飲む"
    )
    val mOkTitle = t("macro.mOkTitle", en = "⏰ Reminder set", ja = "⏰ 登録しました")
    val mNgTitle = t("macro.mNgTitle", en = "⚠ Not set", ja = "⚠ 登録できませんでした")
    val cHooks = t("macro.cHooks",
        en = """  # One hook for "a reminder fired", one for "a notification button was tapped". Just these
  # two, no matter how many reminders you add. Neither depends on the detection switches.""",
        ja = """  # 予定が鳴ったのを拾う 1 本と、通知ボタンの返事を拾う 1 本。**この 2 本だけ**で、
  # 予定を何件足しても増えない。どちらも「検知」の ON/OFF に関係なく働く。"""
    )
    val cTiles = t("macro.cTiles",
        en = """  # Only fill empty slots and slots already ours (never overwrite someone else's).
  # A macro name with arguments works from 0.8.275 on (before that, write sh + full path).""",
        ja = """  # 空いている枠と、すでに自分が使っている枠だけに置く (他人の割り当ては触らない)。
  # 引数付きのマクロ名は 0.8.275 からそのまま書ける (それより前は sh + フルパスで書くこと)。"""
    )
    val mSetupHooks = t("macro.mSetupHooks", en = "hooks registered:", ja = "受け口を登録しました:")
    val mSetupTiles = t("macro.mSetupTiles", en = "tiles:", ja = "タイル:")
    val mPathHint = t("macro.mPathHint",
        en = "Note: this tab cannot run remind.sh by name yet. Open a new tab, or run " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH here.",
        ja = "⚠ このタブでは remind.sh を名前で打てません。新しいタブを開くか、" +
            "このタブで export PATH=${d}HOME/.z2term/macros:${d}PATH を打ってください。"
    )
    val cPathHint = t("macro.cPathHint",
        en = """  # ⚠ The macro directory has only been on PATH since 0.8.287, so **tabs opened before that**
  # still carry the old one. Check whether the name resolves and say what to do if not
  # (otherwise `command not found` has no visible reason).""",
        ja = """  # ⚠ マクロ置き場が PATH に入るのは 0.8.287 から。**それ以前に開いたタブ**は古い PATH の
  # ままなので、名前で打てるか確かめて、駄目なら開き直しを案内する (黙っていると
  # `command not found` の理由が分からない)。"""
    )
    val mPlace = t("macro.mPlace",
        en = "Note: you still have to place the tiles yourself, from the quick-settings pencil/edit screen.",
        ja = "⚠ タイルはご自身でクイック設定パネルの鉛筆(編集)から並べてください。"
    )
    val lRemind = t("macro.lRemind", en = "remind", ja = "リマインド")
    val lList = t("macro.lList", en = "list", ja = "予定")
    val cSelf = t("macro.cSelf",
        en = "# Only react to our own notifications (our ids always start with r)",
        ja = "# 自分が出した通知だけ相手にする (id は必ず r で始まる)"
    )
    val cKeepRepeat = t("macro.cKeepRepeat",
        en = "      # Do not delete a repeating one here: it should fire again tomorrow.",
        ja = "      # 繰り返しはここで消さない (明日もまた鳴ってほしいので)。単発だけ片付ける。"
    )
    val cAsk = t("macro.cAsk",
        en = """# The path used from the tile / a notification. ⚠ **Always answer with a notification**:
# nobody is looking at a terminal here, so failing to stderr reads as "I tapped it and
# nothing happened" (it did — the reason only reached the tile's run.log).
#   Unreadable -> say why and ask again, up to $ASK_TRIES times (the previous answer is kept)
#   Scheduled  -> show the plan and the text in a notification""",
        ja = """# タイル/通知から聞く経路。⚠ **結果は必ず通知で返す** — ここは画面を見ていない前提の
# 入口なので、エラーを標準エラーへ出して終わると「押したのに何も起きない」になる
# (実際そうなっていた。理由はタイルの run.log にしか残らなかった)。
#   読めなかったら → 何が駄目かを付けて $ASK_TRIES 回まで聞き直す (前の入力は返信欄に残す)
#   登録できたら   → 予定と本文を通知で見せる"""
    )
    val cSnoozeCancel = t("macro.cSnoozeCancel",
        en =         "drop the snooze alarm if it was snoozed before being done",
        ja =         "スヌーズ中に完了を押したときの予約を残さない"
    )

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
  dom=; mon=; ymd=; yy=; mm2=; dd2=; nextdow=
  w1=${d}1; w2=${d}2; w3=${d}3

$cEvery
  case ${d}w1 in
    毎|every) w1=${d}(expand_every "${d}w2") ;;
  esac

  case ${d}w1 in
    # Spell out multibyte prefixes: byte-oriented sh/locale patterns cannot match [日月…].
    日曜*|月曜*|火曜*|水曜*|木曜*|金曜*|土曜*|日|月|火|水|木|金|土|sun|mon|tue|wed|thu|fri|sat)
                      KIND=once; USED=1
                      case ${d}w1 in
                        *曜日*) downame=${d}{w1%%曜日*}; hhmm=${d}{w1#*曜日} ;;
                        *曜*) downame=${d}{w1%%曜*}; hhmm=${d}{w1#*曜} ;;
                        *) downame=${d}w1 ;;
                      esac
                      nextdow=${d}(dow_of "${d}downame") || { WHY="$mBadDow ${d}w1"; return 1; }
                      hhmm=${d}{hhmm#の}
                      if [ -z "${d}hhmm" ] && is_hhmm "${d}w2"; then hhmm=${d}w2; USED=2; fi ;;
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
    *) if is_hhmm "${d}w1"; then KIND=once; USED=1; hhmm=${d}w1
       else WHY="$mBadWhen ${d}w1"; return 1; fi ;;
  esac

  if [ -n "${d}nextdow" ]; then
    [ -z "${d}hhmm" ] || check_hhmm "${d}hhmm" || return 1
    now=${d}(date +%s)
    [ -n "${d}hhmm" ] || hhmm=${d}(date -d "@${d}now" +%H:%M)
    today=${d}(date -d "@${d}now" +%w)
    days=${d}(( (nextdow - today + 7) % 7 ))
    tgt=${d}(weekday_epoch "${d}now" "${d}days" "${d}hhmm") || { WHY="$mBadTime ${d}hhmm"; return 1; }
    if [ "${d}tgt" -le "${d}now" ]; then
      tgt=${d}(weekday_epoch "${d}now" "${d}((days+7))" "${d}hhmm") || { WHY="$mBadTime ${d}hhmm"; return 1; }
    fi
    sec=${d}((tgt-now)); PLAN=${d}(fmt_at "${d}tgt"); SPEC="in ${d}{sec}s"
    return 0
  fi

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
  # ⚠ 時分秒は **1 つの `now` から** 導く。以前は now と時/分/秒を別々の `date` で取っており、
  #   CI などで呼び出し間に秒境界をまたぐと算出時刻が数百ms〜数秒ずれ、**18:30 が 18:29 に
  #   丸められて**予約されていた (ローカルは呼び出しが同一秒に収まって出なかった)。
  now=${d}(date +%s)
  ch=${d}(date -d "@${d}now" +%H); cm=${d}(date -d "@${d}now" +%M); cs=${d}(date -d "@${d}now" +%S)
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
  if is_hhmm "${d}1"; then echo "$eDaily"; return; fi
  case ${d}1 in
    [0-9]:[0-9][0-9]|[0-9][0-9]:[0-9][0-9]) echo "$eDaily" ;;
    [0-9]*/[0-9]*)                          echo "$eYearly" ;;
    [0-9]*)                                 echo "$eMonthly" ;;
    *)                                      echo "$eWeekly" ;;
  esac
}

# Convert supported clock words only; never interpret reminder text as shell source.
clock_hhmm() {
  printf '%s\n' "${d}1" | awk -v shape="${d}2" '
    function number(s, parts, count) {
      if (s ~ /^[0-9][0-9]?${d}/) return s + 0
      if (s !~ /^[1-9]?十[1-9]?${d}/) return -1
      count = split(s, parts, "十")
      return (parts[1] == "" ? 1 : parts[1] + 0) * 10 + (parts[2] + 0)
    }
    {
      if (NR != 1) exit 1
      s = ${d}0; period = ""; gsub(/：/, ":", s)
      if (sub(/^午前(の)?/, "", s)) period = "am"
      else if (sub(/^午後(の)?/, "", s)) period = "pm"
      else if (sub(/^朝(の)?/, "", s)) period = "morning"
      else if (sub(/^夜(の)?/, "", s)) period = "night"
      gsub(/０|〇|零/, "0", s); gsub(/１|一/, "1", s); gsub(/２|二/, "2", s)
      gsub(/３|三/, "3", s); gsub(/４|四/, "4", s); gsub(/５|五/, "5", s)
      gsub(/６|六/, "6", s); gsub(/７|七/, "7", s); gsub(/８|八/, "8", s); gsub(/９|九/, "9", s)
      if (s ~ /^[0-9][0-9]?:[0-9][0-9]${d}/) {
        split(s, parts, ":"); hour = parts[1] + 0; minute = parts[2] + 0
      } else {
        if (split(s, parts, "時") != 2) exit 1
        hour = number(parts[1]); tail = parts[2]
        if (tail == "") minute = 0
        else if (tail == "半") minute = 30
        else if (sub(/分${d}/, "", tail)) minute = number(tail)
        else exit 1
        if (hour < 0 || minute < 0) exit 1
      }
      if (shape == "shape") exit 0
      if (period == "morning" && hour > 11) exit 1
      # Night 12 is ambiguous about the date; ask for an explicit 00:00 instead.
      if (period == "night" && hour == 12) exit 1
      if ((period == "am" || period == "pm") && hour > 12) exit 1
      if (period == "am" && hour == 12) hour = 0
      if (period == "pm" && hour < 12) hour += 12
      if (period == "night" && hour >= 1 && hour <= 11) hour += 12
      printf "%02d:%02d\n", hour, minute
    }'
}

# Clock-shaped arguments are consumed even when their numeric range is invalid.
is_hhmm() { clock_hhmm "${d}1" shape >/dev/null; }

check_hhmm() {
  [ -n "${d}1" ] || { WHY="$mNoTime"; return 1; }
  hhmm=${d}(clock_hhmm "${d}1") || { WHY="$mBadTime ${d}1"; return 1; }
  hh=${d}{hhmm%%:*}; mm=${d}{hhmm##*:}
  hh=${d}{hh#0}; [ -n "${d}hh" ] || hh=0
  mm=${d}{mm#0}; [ -n "${d}mm" ] || mm=0
  { [ "${d}hh" -le 23 ] && [ "${d}mm" -le 59 ]; } || { WHY="$mBadRange ${d}1"; return 1; }
  return 0
}

# Resolve the target calendar day in local time, including timezone offset changes.
weekday_epoch() {
  stamp=${d}1; delta=${d}2; clock=${d}3
  cy=${d}(date -d "@${d}stamp" +%Y); cm0=${d}(date -d "@${d}stamp" +%m); cd=${d}(date -d "@${d}stamp" +%d)
  civil=${d}(days_from_civil "${d}cy" "${d}(strip0 "${d}cm0")" "${d}(strip0 "${d}cd")")
  calendar=${d}(date -u -d "@${d}(((civil + delta)*86400))" +%Y-%m-%d) || return 1
  date -d "${d}calendar ${d}clock:00" +%s
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
# ⚠ 今日の年月日も **1 つの `now` から** 取る (別々の date だと午前 0 時直前に日付がずれる)。
days_between() {
  n0=${d}(date +%s)
  ty=${d}(strip0 "${d}(date -d "@${d}n0" +%Y)")
  tm=${d}(strip0 "${d}(date -d "@${d}n0" +%m)")
  td=${d}(strip0 "${d}(date -d "@${d}n0" +%d)")
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
    "${d}1" "${d}n" "${d}id" "${d}f" || return ${d}?
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
    z2-notify -h -n remind-list "$mTitle" "$mNone"
    return
  fi
  z2-notify -h -n remind-list -b $bDelete "$mTitle" "${d}out"
}

$cAskDel
ask_delete() {
  out=${d}(each row | tr '\t' ' ')
  [ -n "${d}out" ] || { z2-notify -h -n remind-list "$mTitle" "$mNone"; return; }
  # ⚠ ボタンを押すと元の通知は閉じるので、番号が見えるように一覧を出し直してから聞く。
  z2-notify -h -n remind-list "$mTitle" "${d}out"
  n=${d}(z2-ask -H "$mAskDelH" "$mAskDel") || return
  [ -n "${d}n" ] || return
  msg=${d}(cmd_del "${d}n" 2>&1) || { z2-notify -h -n remind-ng "$mNgTitle" "${d}msg"; return; }
  z2-notify -h -n remind-ok "$mDeletedTitle" "${d}msg"
}

del_one() {
  kind=${d}(cut -f1 "${d}3"); wid=${d}(cut -f3 "${d}3"); body=${d}(cut -f4- "${d}3")
  # A repeating item can also have a snooze alarm. Cancel both before removing its data.
  z2-alarm cancel "r${d}2" >/dev/null || return 1
  if [ "${d}kind" = repeat ] && [ "${d}wid" != - ]; then
    z2-when remove "${d}wid" >/dev/null || return 1
  fi
  rm -f "${d}3" || return 1
  echo "$mRemoved ${d}body"
}

TARGET=
del_if_match() {
  [ "${d}1" = "${d}TARGET" ] || [ "${d}2" = "${d}TARGET" ] || return 0
  del_one "${d}@" || return ${d}?
  HIT=1
}

cmd_del() {
  [ -n "${d}1" ] || die "$mUsageDel"
  if [ "${d}1" = all ]; then each del_one; return; fi
  TARGET=${d}1; HIT=0
  each del_if_match || return ${d}?
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
  [ "${d}ok" = 1 ] || { z2-notify -h -n remind-ng "$mNgTitle" "$mAskGiveUp"; exit 1; }

  set -- ${d}w
  out=${d}(cmd_add "${d}@" "${d}body" 2>&1) || { z2-notify -h -n remind-ng "$mNgTitle" "${d}out"; exit 1; }
  z2-notify -h -n remind-ok "$mOkTitle" "${d}(echo "${d}out" | tr '\t' ' ')"
}

${reminderViewFunctions(d, t)}

ensure_hooks() {
$cHooks
  z2-when list 2>/dev/null | grep -q "${d}SELF fire" ||
    z2-when 'event:alarm' run "sh ${d}SELF fire \"${d}Z2_WHEN_EVENT_NAME\"" >/dev/null || return 1
  z2-when list 2>/dev/null | grep -q "${d}SELF reply" ||
    z2-when 'event:notify_action' run "sh ${d}SELF reply \"${d}Z2_WHEN_EVENT_NAME\" \"${d}Z2_WHEN_ACTION\"" >/dev/null
}

cmd_setup() {
  ensure_hooks || return 1

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
  view)        cmd_view ;;
  view-add)    shift; cmd_view_add "${d}@" ;;
  view-delete) shift; cmd_view_delete "${d}@" ;;
  peek)        cmd_peek ;;
  add)         shift; cmd_add "${d}@" ;;
  del|rm)      shift; cmd_del "${d}@" ;;
  fire)        shift; cmd_fire "${d}@" ;;
  reply)       shift; cmd_reply "${d}@" ;;
  ask)         cmd_ask ;;
  setup)       cmd_setup ;;
  # 先頭のコメントを説明として出す。⚠ **空行では止めない** (NF で判定する) —
  # 見出しの塊を空行で区切ってあるので、止めると冒頭の 3 行しか出ない (0.8.286 まで実際そうだった)。
  -h|--help|help) awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 && NF { exit }' "${d}0" ;;
  *)           cmd_add "${d}@" ;;
esac
"""
}

/**
 * QR サンプルの本体。
 *
 * **アプリ側に QR 機能を作り直さないための見本**でもある。0.8.219 で状態ウィジェットに
 * SSH 接続 QR を載せ、0.8.220 に「やはり要らない」の判断で自前エンコーダ
 * (`QrEncoder` / `ReedSolomon`) ごと撤去した経緯がある。⚠ **アプリ側へ復活させない** —
 * 欲しいのは「いま手元にあるものを打ち直さずに別の端末へ渡す」ことであって、それは
 * distro の `qrencode` と画像表示 (Kitty graphics) の組み合わせで足りる
 * (同梱物ゼロ・F-Droid 適合。QR は壊れていても「それらしい模様」が出て目視で検証できない
 * ので、**実績のある実装に任せる**方が結果も確か)。
 *
 * 設計上の要点:
 *  - **前提が欠けたら、このタブでの入れ方を出して止まる**。`qrencode` はどの distro にも
 *    あるが既定では入っていない。⚠ パッケージ名は distro ごとに違う (Alpine だけ
 *    `libqrencode-tools`) ので、`command -v` で見えたパッケージマネージャに合わせて出す。
 *  - **既定は絵、`-t` で文字**。⚠ ブロック文字は端末のフォント次第で行間に隙間が出て、
 *    目で読めてもカメラが読み取れないことがある。カメラに読ませるなら絵か PNG。
 *    逆に画像を出せない端末 (ssh で入った先など) では絵が意味不明な文字列として流れるので、
 *    そこは `-t`。**どちらが正しいかは相手の端末次第**なので両方残す。
 *  - **長い入力は行の区切りで分ける**。QR 1 枚の容量は決まっているので 900 バイトで切り、
 *    `[1/3]` と番号を振る。⚠ 行の途中では切らない (日本語が混じっても壊れない)。
 *  - **縦横比は仮定するしかない**。端末は「1 文字の大きさ」を教えてくれないので 1:2 と
 *    決め打ちし、合わない環境向けに `Z2_QR_ASPECT` を残す。
 */
private fun qrBody(d: String, t: CliText): String {
    // ⚠ 先頭の改行を落とす。`#!/bin/sh` と説明の間に**空行を作らない**ためで、
    // 空行が入ると `usage()` の awk がそこで止まり、説明が冒頭 2 行しか出ない。
    val head = (t("macro.qrBody.head",
        en = """
#
# Usage:
#   qr.sh "https://example.com"       encode a string and draw it here
#   qr.sh -f notes.txt                encode the contents of a file
#   z2-clip get | qr.sh               encode what is on the clipboard
#   qr.sh -o ~/qr.png "text"          save a PNG (nothing is drawn)
#   qr.sh -t "text"                   print blocks instead of an image
#   qr.sh -s 24 "text"                pick the size (in columns; default fits the width)
#
# z2-run: qr.sh "https://example.com"   (after installing, the name alone works. Runs once)
#
# # Requirements (without these it will not run, or will look different)
#
#   1) The encoding is done by the app ... inside a tab of z2term there is nothing
#      to install (z2-qr encode). If qrencode is already there it is used instead.
#      Where the app is out of reach (over ssh), install it once per tab (distro):
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      With neither, the install command is printed and the script stops.
#
#   2) The image (default) only shows inside a tab of this app ... it is drawn with
#      Kitty graphics. Over ssh, or on any terminal that cannot show images, you get
#      a stream of gibberish instead. Use -t there.
#
#   3) -t (blocks) depends on the terminal font ... fonts that leave gaps between
#      rows stay readable to the eye but not to a camera. To scan it with a camera,
#      prefer the default image or the -o PNG.
#
#   4) The aspect ratio is assumed ... see "Aspect ratio" below. Squashed? Z2_QR_ASPECT.
#
# Options:
#   -f FILE   read the input from a file (else the arguments, else stdin)
#   -o PNG    save a PNG instead of drawing. Multiple codes become
#             qr-1.png / qr-2.png ...
#   -t        print blocks. For terminals that cannot show images, and over ssh
#   -s N      size in columns when drawing. Default is up to 34, capped by the width
#   -h        this help
#
# About long input:
#   One code holds a fixed amount (2953 bytes of alphanumerics at most; a few hundred
#   if it still has to be scannable). Input over 900 bytes is split at line breaks
#   into several codes, numbered [1/3] and printed in order.
#   WARNING a single line too long to fit in one code cannot be encoded at all.
#
# Aspect ratio:
#   Terminals do not report their cell size, so a 1:2 character ratio is assumed to
#   make the code square. If it looks squashed, adjust it with an environment variable:
#     Z2_QR_ASPECT=0.45 qr.sh "text"     (smaller = taller. Default 0.5)
""",
        ja = """
#
# 使い方:
#   qr.sh "https://example.com"       文字列を QR にしてこの端末に出す
#   qr.sh -f notes.txt                ファイルの中身を QR にする
#   z2-clip get | qr.sh               いまのクリップボードを QR にする
#   qr.sh -o ~/qr.png "text"          PNG に保存する (画面には出さない)
#   qr.sh -t "text"                   絵ではなく文字 (ブロック) で出す
#   qr.sh -s 24 "text"                大きさを変える (画面の桁数。既定は画面幅なり)
#
# z2-run: qr.sh "https://example.com"   (入れた後は名前だけで打てる。常駐させない使い切り)
#
# ■ 前提条件 (これが揃っていないと動かない / 見え方が変わる)
#
#   1) 符号化はアプリがやる … z2term のタブの中なら入れるものはない (z2-qr encode)。
#      qrencode が入っていればそちらを使う。ssh で入った先などアプリに手が届かない
#      ところでは、タブ (distro) ごとに 1 回入れる:
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      どちらも無いときだけ、その場で導入コマンドを出して止まる。
#
#   2) 絵で出す (既定) のはこのアプリのタブの中だけ … 画像は Kitty graphics で
#      描いている。ssh で入った先の別の端末や、画像を出せない端末では絵が出ない
#      (代わりに意味不明な文字列が流れる)。そこでは -t を付けて文字で出す。
#
#   3) -t (文字) は端末のフォント次第 … ブロック文字の行間に隙間が出るフォント
#      だと、画面上は読めてもカメラが読み取れないことがある。カメラで読ませる
#      なら既定の絵、もしくは -o の PNG のほうが確実。
#
#   4) 縦横比を仮定している … 後述の「表示の縦横比」。潰れて見えたら Z2_QR_ASPECT。
#
# オプション:
#   -f FILE   入力をファイルから読む (省略時は引数、引数も無ければ標準入力)
#   -o PNG    PNG に保存する。画面には出さない。複数枚になるときは
#             qr-1.png / qr-2.png … と連番になる
#   -t        文字 (ブロック) で出す。画像を出せない端末や SSH 先の端末向け
#   -s N      画面に出す大きさ (桁数)。既定は画面幅に収まる最大 34 桁
#   -h        この説明
#
# 長い入力について:
#   QR は 1 枚に入る量が決まっている (英数字で 2953 バイトが上限。実際に
#   カメラで読める大きさとなると数百バイト)。900 バイトを超える入力は
#   行の区切りで自動的に複数枚に分け、[1/3] と番号を付けて順に出す。
#   ⚠ 1 行が長すぎて 1 枚に入らないときは、その行だけ QR にできない。
#
# 表示の縦横比:
#   端末が「1 文字ぶんの大きさ」を教えてくれないため、文字の縦横比を 1:2 と
#   仮定して正方形に出している。潰れて見えるときは環境変数で微調整する:
#     Z2_QR_ASPECT=0.45 qr.sh "text"     (小さいほど縦長になる。既定 0.5)
"""
    )).trimStart('\n')
    val cMaxBytes = t("macro.cMaxBytes",
        en = "max bytes per code (split beyond this)",
        ja = "1 枚に入れる上限 (これを超えたら分ける)"
    )
    val cPngModule = t("macro.cPngModule",
        en = "dots per module in a saved PNG",
        ja = "保存する PNG の 1 モジュールあたりのドット数"
    )
    val cTargetPx = t("macro.cTargetPx", en = "target pixel width when drawing", ja = "画面に出すときの狙いのドット幅")
    val cMargin = t("macro.cMargin",
        en = "quiet zone around the code (modules; needed to scan)",
        ja = "QR の周りの余白 (モジュール数。読み取りに必要)"
    )
    val cUsageFn = t("macro.cUsageFn",
        en = "Print the leading comment block as the help text (no fixed line count).\n" +
            "# Blank lines do not stop it (NF decides), so blank-separated sections stay intact.",
        ja = "先頭のコメントブロックをそのまま説明として出す (行数を固定しない)。\n" +
            "# 空行では止めない (NF で判定する) — 説明を空行で区切っても途中で切れないように。"
    )
    // ⭐ 0.8.630: 符号化する道具はアプリにもある (z2-qr encode = ZXing)。端末に qrencode を
    // 入れさせるのは「読むだけ・作るだけ」に対して重すぎるので、あるものを使う順にする。
    // ⚠ qrencode が先 — 既に入れている人の見た目 (訂正レベル・大きさ) を変えないため。
    val cNeedEncoder = t("macro.cNeedEncoder",
        en = "Requirement 1: something to encode with. qrencode if it is here, else the app.",
        ja = "前提条件 1: QR を作る道具。qrencode があればそれ、無ければアプリ (z2-qr) を使う。"
    )
    val mMissing = t("macro.mMissing",
        en = "qr.sh: nothing to encode with here - no qrencode, and the app is out of reach",
        ja = "qr.sh: QR を作る道具が無い — qrencode も無く、アプリ (z2-qr) にも手が届かない"
    )
    val mInstallOnce = t("macro.mInstallOnce",
        en = "  Install it once in this tab:",
        ja = "  このタブで 1 回だけ入れてください:"
    )
    // パッケージマネージャが見つからなかったときの控え (distro ごとに名前が違うので並べる)。
    val mAnyPm = t("macro.mAnyPm",
        en = "    Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode",
        ja = "    Arch: pacman -S qrencode / Ubuntu・Kali: apt install qrencode"
    )
    val cMakeFns = t("macro.cMakeFns",
        en = "Make the PNG. qrencode picks the module size from a probe; the app takes a target.",
        ja = "PNG を作る。qrencode は試し焼きから 1 升の画素を決め、アプリには目安の画素を渡す。"
    )
    val cMakeBlocks = t("macro.cMakeBlocks",
        en = "Draw it with block characters. Both paths emit their own colours, so any theme works.",
        ja = "文字 (ブロック) で描く。どちらも明暗を自分で出すので、端末のテーマに関係なく読める。"
    )
    val mNoTmp = t("macro.qrBody.mNoTmp", en = "cannot create a work directory", ja = "作業場所を作れない")
    val mUnreadable = t("macro.qrBody.mUnreadable", en = "cannot read:", ja = "読めない:")
    val mEmpty = t("macro.qrBody.mEmpty", en = "empty input", ja = "入力が空")
    val mSizeNum = t("macro.mSizeNum", en = "-s takes a number", ja = "-s は数字で")
    val mUsageHint = t("macro.qrBody.mUsageHint", en = "see qr.sh -h", ja = "使い方は qr.sh -h")
    val mPieceFail = t("macro.mPieceFail",
        en = "qr.sh: cannot build code %d: %s",
        ja = "qr.sh: %d 枚目を作れない: %s"
    )
    val cCollect = t("macro.qrBody.cCollect", en = "---- collect the input ----", ja = "---- 入力を集める ----")
    val cSplit = t("macro.cSplit",
        en = "---- split at line breaks when too long ----",
        ja = "---- 長ければ行の区切りで分ける ----"
    )
    val cSplit2 = t("macro.cSplit2",
        en = "Move to the next piece just before the running total passes MAX_BYTES. Lines are\n# never cut in the middle, so multi-byte text survives.",
        ja = "累積バイト数が MAX_BYTES を超える手前で次のピースへ送る。行の途中では切らない\n# ので、日本語が混じっていても壊れない。"
    )
    val cTrim = t("macro.cTrim",
        en = "Drop the trailing newline (never encode a newline the input did not have).",
        ja = "末尾の改行を落とす (元の入力に無い改行を QR に混ぜないため)。"
    )
    val cSize = t("macro.cSize",
        en = "---- pick the display size from the terminal width ----",
        ja = "---- 端末の幅から表示サイズを決める ----"
    )
    val cPngWidth = t("macro.cPngWidth",
        en = "Return the pixel width of a PNG (bytes 16-19, the IHDR).",
        ja = "PNG の横ドット数を返す (IHDR の 16〜19 バイト目)。"
    )
    val cModulePx = t("macro.cModulePx",
        en = "Work back from the target pixel width to the dots per module.",
        ja = "1 モジュールを何ドットで描くかを、狙いのドット幅から逆算する。"
    )
    val cInline = t("macro.cInline",
        en = "Draw a PNG in place with the Kitty graphics protocol.\n# c/r pin down how many cells it takes, then print that many newlines to move the cursor below it.",
        ja = "Kitty graphics protocol で PNG をその場に描く。\n# c/r を指定して占有セル数を確定させ、そのぶん改行してカーソルを絵の下へ運ぶ。"
    )
    val cEmit = t("macro.cEmit", en = "---- emit ----", ja = "---- 出す ----")
    val cAnsi = t("macro.cAnsi",
        en = "ANSIUTF8 emits its own colours, so light/dark comes out right whatever the theme is.",
        ja = "ANSIUTF8 は色を付けて出すので、端末の配色に関係なく明暗が正しく出る。"
    )

    return """$head
set -u

MAX_BYTES=900          # $cMaxBytes
PNG_MODULE_PX=8        # $cPngModule
TARGET_PX=600          # $cTargetPx
MARGIN=2               # $cMargin

die() { printf '%s\n' "qr.sh: ${d}*" >&2; exit 1; }

# $cUsageFn
usage() {
    awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 && NF { exit }' "${d}0"
    exit 0
}

infile=""
outpng=""
astext=0
size=""

while getopts "f:o:ts:h" opt; do
    case "${d}opt" in
        f) infile=${d}OPTARG ;;
        o) outpng=${d}OPTARG ;;
        t) astext=1 ;;
        s) size=${d}OPTARG ;;
        h) usage ;;
        *) die "$mUsageHint" ;;
    esac
done
shift ${d}((OPTIND - 1))

# $cNeedEncoder
have_qrencode=0
command -v qrencode >/dev/null 2>&1 && have_qrencode=1
have_app=0
command -v z2-qr >/dev/null 2>&1 && have_app=1
if [ "${d}have_qrencode" = 0 ] && [ "${d}have_app" = 0 ]; then
    printf '$mMissing\n' >&2
    printf '$mInstallOnce\n' >&2
    if   command -v pacman >/dev/null 2>&1; then printf '    pacman -S qrencode\n' >&2
    elif command -v apt    >/dev/null 2>&1; then printf '    apt install qrencode\n' >&2
    elif command -v apk    >/dev/null 2>&1; then printf '    apk add libqrencode-tools\n' >&2
    elif command -v dnf    >/dev/null 2>&1; then printf '    dnf install qrencode\n' >&2
    else
        printf '$mAnyPm\n' >&2
        printf '    Alpine: apk add libqrencode-tools\n' >&2
    fi
    exit 1
fi

TMP=${d}(mktemp -d) || die "$mNoTmp"
trap 'rm -rf "${d}TMP"' EXIT INT TERM

# $cCollect
src=${d}TMP/src
if [ -n "${d}infile" ]; then
    [ -r "${d}infile" ] || die "$mUnreadable ${d}infile"
    cat -- "${d}infile" > "${d}src"
elif [ ${d}# -gt 0 ]; then
    printf '%s' "${d}*" > "${d}src"
else
    cat > "${d}src"
fi
[ -s "${d}src" ] || die "$mEmpty"

# $cSplit
# $cSplit2
LC_ALL=C awk -v max="${d}MAX_BYTES" -v dir="${d}TMP" '
    BEGIN { n = 1; used = 0; out = dir "/piece-1" }
    {
        len = length(${d}0) + 1
        if (used > 0 && used + len > max) {
            close(out); n++; used = 0; out = dir "/piece-" n
        }
        printf "%s\n", ${d}0 > out
        used += len
    }
    END { print n }
' "${d}src" > "${d}TMP/count"
pieces=${d}(cat "${d}TMP/count")

# $cTrim
i=1
while [ "${d}i" -le "${d}pieces" ]; do
    p=${d}TMP/piece-${d}i
    LC_ALL=C awk '{ if (NR > 1) printf "\n"; printf "%s", ${d}0 }' "${d}p" > "${d}p.trim"
    mv "${d}p.trim" "${d}p"
    i=${d}((i + 1))
done

# $cSize
cols=${d}(tput cols 2>/dev/null) || cols=""
case "${d}cols" in ''|*[!0-9]*) cols=80 ;; esac
if [ -n "${d}size" ]; then
    case "${d}size" in *[!0-9]*|'') die "$mSizeNum" ;; esac
    show_cols=${d}size
else
    show_cols=34
    [ "${d}show_cols" -gt ${d}((cols - 2)) ] && show_cols=${d}((cols - 2))
fi
[ "${d}show_cols" -lt 8 ] && show_cols=8

aspect=${d}{Z2_QR_ASPECT:-0.5}
show_rows=${d}(awk -v c="${d}show_cols" -v a="${d}aspect" 'BEGIN { r = int(c * a + 0.5); if (r < 4) r = 4; print r }')

# $cPngWidth
png_width() {
    od -An -tu1 -j16 -N4 "${d}1" | awk '{ print ${d}1 * 16777216 + ${d}2 * 65536 + ${d}3 * 256 + ${d}4 }'
}

# $cModulePx
module_px_for() {
    qrencode -s 1 -m "${d}MARGIN" -o "${d}TMP/probe.png" -r "${d}1" 2>/dev/null || return 1
    w=${d}(png_width "${d}TMP/probe.png")
    [ -n "${d}w" ] && [ "${d}w" -gt 0 ] || return 1
    awk -v t="${d}TARGET_PX" -v w="${d}w" 'BEGIN { s = int(t / w); if (s < 2) s = 2; if (s > 20) s = 20; print s }'
}

# $cMakeFns
make_png() {
    if [ "${d}have_qrencode" = 1 ]; then
        s=${d}(module_px_for "${d}1") || s=${d}PNG_MODULE_PX
        qrencode -s "${d}s" -m "${d}MARGIN" -o "${d}2" -r "${d}1" 2>"${d}TMP/err"
    else
        z2-qr encode -p "${d}TARGET_PX" -m "${d}MARGIN" -o "${d}2" < "${d}1" >/dev/null 2>"${d}TMP/err"
    fi
}

# $cMakeBlocks
make_blocks() {
    if [ "${d}have_qrencode" = 1 ]; then
        # $cAnsi
        qrencode -t ANSIUTF8 -m "${d}MARGIN" -r "${d}1" 2>"${d}TMP/err"
    else
        z2-qr encode -t -m "${d}MARGIN" < "${d}1" 2>"${d}TMP/err"
    fi
}

# $cInline
show_inline() {
    base64 < "${d}1" | tr -d '\n' | fold -w 4096 > "${d}TMP/chunks"
    n=${d}(awk 'END { print NR }' "${d}TMP/chunks")
    i=0
    while IFS= read -r chunk || [ -n "${d}chunk" ]; do
        i=${d}((i + 1))
        if [ "${d}i" -lt "${d}n" ]; then m=1; else m=0; fi
        if [ "${d}i" -eq 1 ]; then
            printf '\033_Ga=T,f=100,C=1,c=%s,r=%s,q=2,m=%s;%s\033\\' \
                "${d}show_cols" "${d}show_rows" "${d}m" "${d}chunk"
        else
            printf '\033_Gm=%s;%s\033\\' "${d}m" "${d}chunk"
        fi
    done < "${d}TMP/chunks"
    # C=1 を送っているのでカーソルは行方向に動かない。絵の高さぶん送って下へ出す
    # (z2-img と同じ出し方。片方を直すときは両方を揃えること)。
    printf '\r'
    i=0
    while [ "${d}i" -le "${d}show_rows" ]; do printf '\n'; i=${d}((i + 1)); done
}

# $cEmit
i=1
failed=0
while [ "${d}i" -le "${d}pieces" ]; do
    p=${d}TMP/piece-${d}i

    if [ "${d}pieces" -gt 1 ]; then
        printf '[%d/%d]\n' "${d}i" "${d}pieces"
    fi

    if [ -n "${d}outpng" ]; then
        if [ "${d}pieces" -gt 1 ]; then
            base=${d}{outpng%.png}
            dest=${d}base-${d}i.png
        else
            dest=${d}outpng
        fi
        if make_png "${d}p" "${d}dest"; then
            printf '%s\n' "${d}dest"
        else
            printf '$mPieceFail\n' "${d}i" "${d}(cat "${d}TMP/err")" >&2
            failed=1
        fi
    elif [ "${d}astext" -eq 1 ]; then
        make_blocks "${d}p" || {
            printf '$mPieceFail\n' "${d}i" "${d}(cat "${d}TMP/err")" >&2
            failed=1
        }
    else
        if make_png "${d}p" "${d}TMP/out-${d}i.png"; then
            show_inline "${d}TMP/out-${d}i.png"
        else
            printf '$mPieceFail\n' "${d}i" "${d}(cat "${d}TMP/err")" >&2
            failed=1
        fi
    fi

    i=${d}((i + 1))
done

exit "${d}failed"
"""
}

/**
 * Markdown を読むサンプルの本体。
 *
 * **アプリ側に「文書ビューア」を作らないための見本**でもある (rss.sh / remind.sh と同じ立ち位置)。
 * 必要な部品はすでに全部ある — 折り返しと色は端末が、リンクは OSC 8 が、絵は kitty graphics
 * (`z2-img`) が、頁として読むのは `z2-view` が引き受ける。ここが足していないのは
 * **Markdown を解く 1 本の awk** だけで、用途限定の画面は 1 枚も増えない。
 *
 * 設計上の要点:
 *  - **追加インストールを要らなくする**。読むだけの道具に `pacman -S` を要求すると、
 *    タブ (distro) を作り直すたびに入れ直しになる。`sh` と `awk` だけで書く
 *    (qr.sh が `qrencode` を、rss.sh が `python3` を要るのとは事情が違う —
 *    あちらは符号化と XML 解析という「自分で書くと必ず壊れる」処理が中身)。
 *  - **端末と読み物画面で同じ解析を使う** (`mode=ansi|html`)。2 本に分けると、
 *    表の桁がずれる・引用が抜けるといった違いが**片方だけ**に出て気付けない。
 *  - **見た目の桁は自分で数える**。`LC_ALL=C` でバイト列として扱い、幅の判定は
 *    アプリの `EastAsianWidth` と同じ範囲にそろえる。awk 任せ (`length()`) だと、
 *    gawk は文字数・busybox はバイト数を返すので、同じ表が端末ごとに崩れる。
 *  - **折り返しの残り桁は測り直す**。引き算で出すと、行末ちょうどに空白が来たとき
 *    -1 になって次の行が 1 桁はみ出す (実際に踏んだ)。
 *  - **awk はファイルに書いて `-f` で渡す**。引数で渡すと gawk が 4096 バイト境界に
 *    またがった多バイト文字でプログラムを打ち切る (これも実際に踏んだ)。
 *  - **斜体は下線で出す**。端末が SGR 3 を描かないので、`*強調*` が素の字と区別できない。
 *    man と同じ逃げ方をする。⚠ 端末が斜体を描くようになったらここを戻すこと。
 */
private fun mdBody(d: String, t: CliText): String {
    // ⚠ 先頭の改行を落とす。`#!/bin/sh` と説明の間に**空行を作らない**ため (qr.sh と同じ)。
    val head = (t("macro.mdBody.head",
        en = """
#
# Usage:
#   md.sh README.md                render it here, in this terminal
#   md.sh -v README.md             open it inside the app (z2-view)
#   cat notes.md | md.sh           read from standard input
#   md.sh -o page.html README.md   write the HTML and stop (nothing opens)
#
# z2-run: md.sh README.md   (after installing, the name alone works. Runs once)
#
# Options:
#   -v        open it inside the app (z2-view)
#   -o FILE   write the HTML there. Nothing is shown here
#   -w N      wrap at N columns (default: the width of the screen, up to 100)
#   -p        no colour and no decoration (plain text)
#   -t TITLE  title for the reading screen (default: the first heading, else the file name)
#   -h        this help
#
# A long document, a screenful at a time: md.sh README.md | less -R   (-R keeps the colours)
#
# Nothing to install: sh and awk are enough.
# Headings, paragraphs, lists (nested, numbered, [ ]/[x]), quotes, code, tables,
# rules, links, pictures and front matter (---) are handled. WARNING setext headings
# (=== / ---) and reference links ([text][1]) come out as they are written.
#
# What to expect:
#   - Pictures appear in place only inside a tab of this app (drawn with kitty
#     graphics through z2-img). Over ssh you get the caption and the path instead.
#   - Links can be tapped (OSC 8). A terminal without it shows them as text.
#   - This terminal does not draw italics, so *emphasis* is underlined (as man does).
#   - A table too wide for the screen is broken into "heading: value" lines.
#   - -v embeds pictures that are on this device into the page (1.5 MB each at most).
#     The reading screen fetches nothing from the network, so http(s) pictures
#     stay as their caption.
""",
        ja = """
#
# 使い方:
#   md.sh README.md                この端末に整形して出す
#   md.sh -v README.md             アプリの読み物画面で開く (z2-view)
#   cat notes.md | md.sh           標準入力から読む
#   md.sh -o page.html README.md   HTML に書き出すだけ (開かない)
#
# z2-run: md.sh README.md   (入れた後は名前だけで打てる。常駐させない使い切り)
#
# オプション:
#   -v        アプリの読み物画面で開く (z2-view)
#   -o FILE   HTML を書き出す。画面には出さない
#   -w N      折り返す桁数 (既定: 画面の幅。最大 100)
#   -p        色も飾りも付けない (ただの文字)
#   -t 題名   読み物画面の題名 (既定: 最初の見出し、無ければファイル名)
#   -h        この説明
#
# 長い文書を少しずつ読む: md.sh README.md | less -R   (-R で色が通る)
#
# 追加で入れるものはありません。sh と awk だけで動きます。
# 見出し・段落・箇条書き (入れ子・番号・[ ]/[x])・引用・コード・表・区切り線・
# リンク・画像・頭書き (---) に対応します。⚠ 見出しの下線書き (=== / ---) と
# 参照リンク ([文字][1]) は、書いたままの字で出ます。
#
# 見え方について:
#   - 絵が本文の位置に出るのはこのアプリのタブの中だけです (kitty graphics で
#     z2-img が描きます)。ssh で入った先では説明とパスだけになります。
#   - リンクは押せます (OSC 8)。対応しない端末では文字のままです。
#   - この端末は斜体を描かないので、*強調* は下線で出します (man と同じ)。
#   - 画面の幅に入らない表は「見出し: 中身」の並びに崩します。
#   - -v は端末にある絵を頁へ埋め込みます (1 枚 1.5MB まで)。読み物画面は外へ
#     取りに行かないので、http(s) の絵は説明だけになります。
"""
    )).trimStart('\n')

    val cCollect = t("macro.mdBody.cCollect",
        en = "# Gather the input (several files become one read, in the order given).",
        ja = "# 入力をまとめる (複数のファイルは続けて 1 つの読み物にする)。"
    )
    val cTitle = t("macro.cTitle",
        en = "# Title: -t, else the first # heading, else the file name",
        ja = "# 題名: -t → 最初の # 見出し → ファイル名"
    )
    val cWidth = t("macro.cWidth",
        en = "# The width of the screen",
        ja = "# 画面の幅"
    )
    val cColorEnv = t("macro.cColorEnv",
        en = "# A way out when the colours should (or should not) go down a pipe, e.g. into a pager",
        ja = "# パイプの先 (ページャなど) へ色を送りたいとき / 送りたくないときの逃げ道"
    )
    val cImgWhere = t("macro.cImgWhere",
        en = "# A picture can only appear in place inside a tab (kitty graphics) with z2-img present.",
        ja = "# 絵を本文の位置に出せるのは、タブの中 (kitty graphics) で z2-img があるときだけ。"
    )
    val cAmbi = t("macro.cAmbi",
        en = "# How wide to count ambiguous characters (rules, arrows, circled digits). Use\n" +
            "# Z2_MD_AMBIWIDTH=2 if Settings has them as full width. Default 1, as the app does.",
        ja = "# 曖昧幅 (罫線・矢印・丸数字) をいくつと数えるか。⚙設定の「曖昧幅」を全角にしている\n" +
            "# 人は Z2_MD_AMBIWIDTH=2。既定はアプリの既定と同じ 1。"
    )
    val cAwkFile = t("macro.cAwkFile",
        en = "# WARNING the awk program is written to a FILE and handed over with -f. Passed as an\n" +
            "#   argument instead, gawk cuts the program where a multibyte character (like the\n" +
            "#   Japanese in these notes) straddles its 4096-byte read boundary, and dies with\n" +
            "#   \"unexpected newline or end of string\".",
        ja = "# ⚠ awk の本体は**ファイルに書いて -f で渡す**。引数として渡すと、gawk が\n" +
            "#   4096 バイトごとに読む境界に多バイト文字 (この註釈のような日本語) がまたがったとき\n" +
            "#   そこでプログラムが切れたことにされ、「unexpected newline or end of string」で落ちる。"
    )
    val cInlineImg = t("macro.cInlineImg",
        en = "# Embed the pictures into the page for the reading screen, as data: URIs.\n" +
            "# WARNING z2-view fetches nothing from the network (JavaScript and loads are off), so\n" +
            "#   a picture that is not embedded simply does not appear. 4 MB for the whole page.",
        ja = "# 読み物画面用の HTML に、絵を data: URI として埋め込む。\n" +
            "# ⚠ z2-view は外の絵を取りに行かない (JavaScript も通信も切ってある) ので、\n" +
            "#   埋め込まないと絵は出ない。頁全体で 4MB まで。"
    )
    val cImgLoop = t("macro.cImgLoop",
        en = "# Only a document that has pictures is read line by line (to call z2-img in place)",
        ja = "# 絵のある文書だけ 1 行ずつ読む (絵の位置で z2-img を呼ぶため)"
    )

    val mUsageHint = t("macro.mdBody.mUsageHint",
        en = "see md.sh -h", ja = "使い方は md.sh -h"
    )
    val mNoTmp = t("macro.mdBody.mNoTmp",
        en = "cannot make a temporary directory", ja = "一時ディレクトリを作れません"
    )
    val mUnreadable = t("macro.mdBody.mUnreadable",
        en = "unreadable file:", ja = "読めないファイル:"
    )
    val mEmpty = t("macro.mdBody.mEmpty",
        en = "nothing to read", ja = "中身がありません"
    )
    val mWidthNum = t("macro.mWidthNum",
        en = "-w takes a number", ja = "-w は数字で"
    )
    val mNoDir = t("macro.mNoDir",
        en = "cannot make the folder to put it in", ja = "置き場を作れません"
    )
    val mConvFail = t("macro.mConvFail",
        en = "the conversion failed", ja = "変換に失敗しました"
    )
    val mNoView = t("macro.mNoView",
        en = "no z2-view here (the app needs updating). The HTML is at:",
        ja = "z2-view がありません (アプリの更新が要ります)。HTML は次にあります:"
    )
    val mImgLabel = t("macro.mImgLabel",
        en = "[picture]", ja = "[画像]"
    )

    return """$head
set -u

usage() {
    awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 && NF { exit }' "${d}0"
    exit 0
}

die() { printf '%s\n' "md.sh: ${d}*" >&2; exit 1; }

viewmode=0
outfile=""
width=""
plain=0
title=""

while getopts "vo:w:pt:h" opt; do
    case "${d}opt" in
        v) viewmode=1 ;;
        o) outfile=${d}OPTARG ;;
        w) width=${d}OPTARG ;;
        p) plain=1 ;;
        t) title=${d}OPTARG ;;
        h) usage ;;
        *) die "$mUsageHint" ;;
    esac
done
shift ${d}((OPTIND - 1))

TMP=${d}(mktemp -d) || die "$mNoTmp"
trap 'rm -rf "${d}TMP"' EXIT INT TERM

TAB=${d}(printf '\t')
IMGMARK=${d}(printf '\001')

$cCollect
src=${d}TMP/src
: > "${d}src"
srcdir=.
if [ ${d}# -gt 0 ]; then
    first=${d}1
    srcdir=${d}(dirname -- "${d}first")
    for f in "${d}@"; do
        [ -r "${d}f" ] || die "$mUnreadable ${d}f"
        cat -- "${d}f" >> "${d}src"
        printf '\n' >> "${d}src"
    done
else
    first=""
    cat > "${d}src"
fi
[ -s "${d}src" ] || die "$mEmpty"

$cTitle
if [ -z "${d}title" ]; then
    title=${d}(LC_ALL=C awk '/^#[ \t]+/ { sub(/^#[ \t]+/, ""); sub(/[ \t]+#*[ \t]*${d}/, ""); print; exit }' "${d}src")
fi
if [ -z "${d}title" ]; then
    if [ -n "${d}first" ]; then title=${d}(basename -- "${d}first"); else title="Markdown"; fi
fi

$cWidth
if [ -z "${d}width" ]; then
    width=${d}(tput cols 2>/dev/null) || width=""
    case "${d}width" in ''|*[!0-9]*) width=80 ;; esac
    [ "${d}width" -gt 100 ] && width=100
fi
case "${d}width" in *[!0-9]*|'') die "$mWidthNum" ;; esac
[ "${d}width" -lt 20 ] && width=20

tty=0
[ -t 1 ] && tty=1
color=1
[ "${d}plain" = 1 ] && color=0
[ "${d}tty" = 1 ] || color=0
$cColorEnv
case "${d}{Z2_MD_COLOR:-}" in 1) color=1 ;; 0) color=0 ;; esac

$cImgWhere
imgtok=0
if [ "${d}tty" = 1 ] && command -v z2-img >/dev/null 2>&1; then imgtok=1; fi

$cAmbi
ambi=${d}{Z2_MD_AMBIWIDTH:-1}
case "${d}ambi" in 1|2) ;; *) ambi=1 ;; esac

$cAwkFile
cat > "${d}TMP/md.awk" <<"Z2MD_AWK"
# Markdown を ANSI (端末) か HTML (読み物画面) にする。
#
# ⭐ LC_ALL=C で走らせる前提。文字列をバイト列として扱い、見た目の桁は自分で数える
#    (awk によって length() が文字数だったりバイト数だったりするため、そろえないと
#    表の桁が環境ごとにずれる)。
# ⭐ 幅の判定はアプリの EastAsianWidth と同じ範囲にしてある。ここがずれると、
#    画面の折り返しと食い違って表が崩れる。

# ---------- 文字とバイト ----------

# 位置 i から始まる 1 文字のバイト数
function clen(s, i,   b) {
    b = ORD[substr(s, i, 1)]
    if (b < 192) return 1
    if (b < 224) return 2
    if (b < 240) return 3
    return 4
}

# 位置 i の文字のコードポイント
function cpat(s, i,   b, nb, cp, k) {
    b = ORD[substr(s, i, 1)]
    nb = clen(s, i)
    if (nb == 1) return b
    if (nb == 2) cp = b - 192
    else if (nb == 3) cp = b - 224
    else cp = b - 240
    for (k = 1; k < nb; k++) cp = cp * 64 + (ORD[substr(s, i + k, 1)] % 64)
    return cp
}

# 見た目の桁数 (2 桁になる文字はアプリと同じ範囲)
function cwidth(cp) {
    if (cp < 128) return 1
    if (cp >= 4352 && cp <= 4447) return 2
    if (cp >= 11904 && cp <= 12350) return 2
    if (cp >= 12353 && cp <= 13311) return 2
    if (cp >= 13312 && cp <= 19903) return 2
    if (cp >= 19968 && cp <= 40959) return 2
    if (cp >= 40960 && cp <= 42191) return 2
    if (cp >= 44032 && cp <= 55203) return 2
    if (cp >= 63744 && cp <= 64255) return 2
    if (cp >= 65072 && cp <= 65135) return 2
    if (cp >= 65280 && cp <= 65376) return 2
    if (cp >= 65504 && cp <= 65510) return 2
    if (cp >= 127744 && cp <= 128591) return 2
    if (cp >= 128640 && cp <= 128767) return 2
    if (cp >= 129280 && cp <= 129535) return 2
    if (cp >= 131072 && cp <= 196605) return 2
    if (cp >= 196608 && cp <= 262141) return 2
    if (isambi(cp)) return ambi
    return 1
}

# 曖昧幅 (CJK では全角に描かれることがあるもの)
function isambi(cp) {
    if (cp >= 8208 && cp <= 8286) return 1
    if (cp >= 8448 && cp <= 10175) return 1
    if (cp >= 65024 && cp <= 65039) return 1
    return 0
}

# i にあるエスケープ列の次の位置
function skipesc(s, i,   ln) {
    ln = length(s)
    if (substr(s, i + 1, 1) == "[") {
        i += 2
        while (i <= ln && index("0123456789;:?", substr(s, i, 1)) > 0) i++
        return i + 1
    }
    if (substr(s, i + 1, 1) == "]") {
        i += 2
        while (i <= ln) {
            if (substr(s, i, 1) == ESC && substr(s, i + 1, 1) == BSL) return i + 2
            i++
        }
        return ln + 1
    }
    return i + 2
}

# エスケープ列を飛ばして数える見た目の桁数
function vwidth(s,   i, ln, w) {
    ln = length(s); i = 1; w = 0
    while (i <= ln) {
        if (substr(s, i, 1) == ESC) { i = skipesc(s, i); continue }
        w += cwidth(cpat(s, i))
        i += clen(s, i)
    }
    return w
}

function rep(ch, cnt,   s, i) {
    s = ""
    for (i = 0; i < cnt; i++) s = s ch
    return s
}

function sp(cnt) { return rep(" ", cnt) }

# ---------- 出力 ----------

function emit(s) {
    if (s == "" && QPFX == "") { print ""; nout++; return }
    print QPFX s
    nout++
}

function blank() {
    if (nout > 0 && !lastblank) { print ""; nout++ }
    lastblank = 1
}

function esc(s) {
    gsub(/&/, "\\&amp;", s); gsub(/</, "\\&lt;", s); gsub(/>/, "\\&gt;", s)
    gsub(/"/, "\\&quot;", s)
    return s
}

function txt(s) { return html ? esc(s) : s }

# ---------- 行内の記法 ----------

function findclose(s, i, mark,   ln, m) {
    ln = length(s); m = length(mark)
    while (i <= ln) {
        if (substr(s, i, 1) == "\\") { i += 2; continue }
        if (substr(s, i, m) == mark) return i
        i++
    }
    return 0
}

function findbracket(s, i,   ln, lvl, c) {
    ln = length(s); lvl = 1
    while (i <= ln) {
        c = substr(s, i, 1)
        if (c == "\\") { i += 2; continue }
        if (c == "[") lvl++
        else if (c == "]") { lvl--; if (lvl == 0) return i }
        i++
    }
    return 0
}

function linkout(url, label) {
    if (html) {
        # 端末の外から来た文字がそのまま載るので、http(s) 以外はリンクにしない
        if (url ~ /^https?:\/\//) return "<a href=\"" esc(url) "\">" label "</a>"
        return label
    }
    if (!color) return (label == url) ? url : label " <" url ">"
    if (url ~ /^(https?:\/\/|mailto:)/)
        return ESC "]8;;" url ESC BSL L1 U1 label U0 L0 ESC "]8;;" ESC BSL
    return L1 U1 label U0 L0
}

function dim(s) {
    if (html) return "<span class=\"fm\">" s "</span>"
    return color ? D1 s D0 : s
}

function inl(s,   out, ln, i, c, c2, j, k, url, label, run, body) {
    out = ""
    ln = length(s)
    i = 1
    while (i <= ln) {
        c = substr(s, i, 1)
        c2 = substr(s, i, 2)

        if (c == "\\" && i < ln) { out = out txt(substr(s, i + 1, 1)); i += 2; continue }

        if (c == "`") {
            run = 1
            while (substr(s, i + run, 1) == "`") run++
            j = index(substr(s, i + run), substr(s, i, run))
            if (j > 0) {
                body = substr(s, i + run, j - 1)
                sub(/^ /, "", body); sub(/ ${d}/, "", body)
                out = out (html ? "<code>" esc(body) "</code>" : (color ? C1 body C0 : body))
                i = i + run + j - 1 + run
                continue
            }
        }

        if (c2 == "![") {
            j = findbracket(s, i + 2)
            if (j > 0 && substr(s, j + 1, 1) == "(") {
                k = findclose(s, j + 2, ")")
                if (k > 0) {
                    label = substr(s, i + 2, j - i - 2)
                    url = substr(s, j + 2, k - j - 2)
                    sub(/[ \t].*${d}/, "", url)
                    out = out dim(IMGLABEL " " txt(label == "" ? url : label))
                    i = k + 1
                    continue
                }
            }
        }

        if (c == "[") {
            j = findbracket(s, i + 1)
            if (j > 0 && substr(s, j + 1, 1) == "(") {
                k = findclose(s, j + 2, ")")
                if (k > 0) {
                    label = substr(s, i + 1, j - i - 1)
                    url = substr(s, j + 2, k - j - 2)
                    sub(/[ \t]+["(].*${d}/, "", url)
                    out = out linkout(url, inl(label))
                    i = k + 1
                    continue
                }
            }
        }

        if (c == "<") {
            j = index(substr(s, i), ">")
            if (j > 0) {
                url = substr(s, i + 1, j - 2)
                if (url ~ /^(https?:\/\/|mailto:)[^ ]+${d}/) {
                    out = out linkout(url, txt(url))
                    i = i + j
                    continue
                }
            }
        }

        if (c2 == "**" || c2 == "__") {
            j = findclose(s, i + 2, c2)
            if (j > i + 2) {
                body = inl(substr(s, i + 2, j - i - 2))
                out = out (html ? "<strong>" body "</strong>" : (color ? B1 body B0 : body))
                i = j + 2
                continue
            }
        }

        if (c2 == "~~") {
            j = findclose(s, i + 2, "~~")
            if (j > i + 2) {
                body = inl(substr(s, i + 2, j - i - 2))
                out = out (html ? "<del>" body "</del>" : (color ? K1 body K0 : body))
                i = j + 2
                continue
            }
        }

        if (c == "*" || (c == "_" && (i == 1 || substr(s, i - 1, 1) ~ /[ \t([]/))) {
            j = findclose(s, i + 1, c)
            if (j > i + 1 && substr(s, i + 1, 1) != " ") {
                body = inl(substr(s, i + 1, j - i - 1))
                out = out (html ? "<em>" body "</em>" : (color ? I1 body I0 : body))
                i = j + 1
                continue
            }
        }

        # 素の URL もリンクにする (行頭か、空白・括弧の直後から始まるものだけ)
        if (c == "h" && (i == 1 || substr(s, i - 1, 1) ~ /[ \t(<]/)) {
            if (match(substr(s, i), /^https?:\/\/[^ \t<>()]+/)) {
                url = substr(s, i, RLENGTH)
                sub(/[.,;:]${d}/, "", url)
                out = out linkout(url, txt(url))
                i += length(url)
                continue
            }
        }

        out = out txt(c)
        i++
    }
    return out
}

# ---------- 折り返し ----------

# 見た目の桁で折り返す。first は 1 行目の前置き、cont は 2 行目以降の前置き。
function wrap(s, first, cont,   ln, i, j, col, out, lim, brk, brkout, brkw, cp, w, tok, prevw, done) {
    if (html) { emit(first s); return }
    # 引用の前置き (emit が足す) のぶんも引く
    lim = width - vwidth(QPFX) - vwidth(first)
    if (lim < 8) lim = 8
    ln = length(s)
    i = 1; col = 0; out = ""; brk = 0; prevw = 0; done = 0
    while (i <= ln) {
        if (substr(s, i, 1) == ESC) {
            j = skipesc(s, i)
            out = out substr(s, i, j - i)
            i = j
            continue
        }
        cp = cpat(s, i)
        w = cwidth(cp)
        tok = substr(s, i, clen(s, i))

        if (cp == 32 && col > 0) { brk = 1; brkout = out; brkw = col }
        else if (prevw == 2 && w == 2 && !nostart(cp)) { brk = 2; brkout = out; brkw = col }

        if (col + w > lim && col > 0) {
            if (brk == 1) {
                emit(first brkout)
                out = substr(out, length(brkout) + 2)
            } else if (brk == 2) {
                emit(first brkout)
                out = substr(out, length(brkout) + 1)
            } else {
                emit(first out)
                out = ""
            }
            # ⚠ 残りの桁は引き算で出さない。折り返しの機会が「いま見ている文字の
            #   直前」だったとき (行末ちょうどに空白が来た場合) 引き算では -1 になり、
            #   次の行が 1 桁はみ出す。短い残りを測り直すほうが安い。
            col = vwidth(out)
            done = 1
            first = cont
            lim = width - vwidth(QPFX) - vwidth(cont)
            if (lim < 8) lim = 8
            brk = 0
            if (out == "" && cp == 32) { i += clen(s, i); prevw = 0; continue }
        }
        out = out tok
        col += w
        prevw = w
        i += clen(s, i)
    }
    if (out != "" || !done) emit(first out)
    lastblank = 0
}

# 行頭に置かない文字 (最低限の禁則)
function nostart(cp) {
    if (cp == 12290 || cp == 12289) return 1                                  # 。 、
    if (cp == 65289 || cp == 12301 || cp == 12303 || cp == 12309) return 1    # ） 」 』 〕
    if (cp == 65281 || cp == 65311 || cp == 65292 || cp == 65294) return 1    # ！ ？ ， ．
    if (cp == 12539 || cp == 65306 || cp == 65307) return 1                   # ・ ： ；
    return 0
}

# ---------- ブロックの見分け ----------

function fencemark(s) { sub(/^[ \t]+/, "", s); return substr(s, 1, 3) }

function isblank(i) { return (L[i] ~ /^[ \t]*${d}/) }
function isfence(s) { return (s ~ /^[ \t]*(```|~~~)/) }
function isitem(s)  { return (s ~ /^[ \t]*([-*+]|[0-9]+[.)])[ \t]+/) }

# 区切り線 (--- / *** / ___)。
# ⚠ 正規表現の {3,} は使わない。awk によっては回数指定を解釈せず、
#   区切り線が段落として出てしまう。印の数は数えて判断する。
function ishr(s,   marks) {
    if (s !~ /^[ \t]*[-*_][-*_ \t]*${d}/) return 0
    marks = s
    gsub(/[ \t]/, "", marks)
    if (length(marks) < 3) return 0
    if (marks ~ /^-+${d}/ || marks ~ /^\*+${d}/ || marks ~ /^_+${d}/) return 1
    return 0
}

function istablerow(s) { return (s ~ /\|/ && s !~ /^[ \t]*\|?[ \t]*${d}/) }

function isdelim(s) {
    if (s !~ /\|/) return 0
    return (s ~ /^[ \t]*\|?[ \t]*:?-+:?[ \t]*(\|[ \t]*:?-+:?[ \t]*)*\|?[ \t]*${d}/)
}

function isimgline(s) { return (s ~ /^[ \t]*!\[[^]]*\][ \t]*\([^)]+\)[ \t]*${d}/) }

# ---------- ブロックごとの組み立て ----------

function heading(i,   s, lvl, body) {
    s = L[i]
    sub(/^[ \t]+/, "", s)
    lvl = 0
    while (substr(s, lvl + 1, 1) == "#") lvl++
    body = substr(s, lvl + 1)
    if (lvl > 6) lvl = 6
    sub(/^[ \t]+/, "", body)
    sub(/[ \t]+#+[ \t]*${d}/, "", body)
    blank()
    if (html) { emit("<h" lvl ">" inl(body) "</h" lvl ">"); lastblank = 0; return i + 1 }
    if (lvl <= 2 && color) {
        wrap(B1 inl(body) B0, "", "  ")
        emit(D1 rep(lvl == 1 ? HH : HL, width - vwidth(QPFX)) D0)
    } else {
        wrap((color ? D1 rep("#", lvl) " " D0 B1 inl(body) B0 : rep("#", lvl) " " inl(body)),
             "", sp(lvl + 1))
    }
    lastblank = 0
    return i + 1
}

function fence(i,   mark, lang, s, body) {
    s = L[i]
    sub(/^[ \t]+/, "", s)
    mark = substr(s, 1, 3)
    lang = substr(s, 4)
    sub(/^[ \t]+/, "", lang); sub(/[ \t`~]+${d}/, "", lang)
    blank()
    if (html) emit("<pre><code>")
    else if (lang != "" && color) emit(D1 lang D0)
    i++
    while (i <= n && L[i] !~ ("^[ \t]*" mark)) {
        body = L[i]
        if (html) emit(esc(body))
        else emit((color ? C1 : "") "  " body (color ? C0 : ""))
        i++
    }
    if (html) emit("</code></pre>")
    lastblank = 0
    return i + 1
}

function indentcode(i,   body) {
    blank()
    if (html) emit("<pre><code>")
    while (i <= n && (L[i] ~ /^(    |\t)/ || (isblank(i) && i + 1 <= n && L[i + 1] ~ /^(    |\t)/))) {
        body = L[i]
        sub(/^(    |\t)/, "", body)
        if (html) emit(esc(body))
        else emit((color ? C1 : "") "  " body (color ? C0 : ""))
        i++
    }
    if (html) emit("</code></pre>")
    lastblank = 0
    return i
}

function imgline(i,   s, url, label, j) {
    s = L[i]
    sub(/^[ \t]+/, "", s); sub(/[ \t]+${d}/, "", s)
    j = index(s, "](")
    label = substr(s, 3, j - 3)
    url = substr(s, j + 2, length(s) - j - 2)
    sub(/[ \t].*${d}/, "", url)
    blank()
    if (html) {
        emit(IMGTOKEN url IMGTOKEN2 label IMGTOKEN)
    } else if (imgtok && url !~ /^https?:\/\//) {
        if (label != "") emit(dim(IMGLABEL " " label))
        print IMGMARK url "\t" label
        nout++
    } else {
        wrap(dim(IMGLABEL " " (label == "" ? url : label " (" url ")")), "", "  ")
    }
    lastblank = 0
    return i + 1
}

function listblock(i,   s, ind, marker, body, ordered) {
    blank()
    top = 0
    delete INDENTS
    while (i <= n) {
        if (isblank(i)) {
            if (i + 1 <= n && isitem(L[i + 1]) && sametype(L[i + 1])) { i++; continue }
            if (i + 1 <= n && !isitem(L[i + 1]) && L[i + 1] ~ /^(    |\t|  )[^ \t]/) { i++; continue }
            break
        }
        if (QD[i] != QCUR) break
        if (!isitem(L[i])) {
            if (L[i] ~ /^[ \t]+[^ \t]/ && PEND != "" && !isfence(L[i])) {
                body = L[i]
                sub(/^[ \t]+/, "", body)
                PEND = PEND joiner(PEND, body) body
                i++
                continue
            }
            break
        }
        flushitem()
        s = L[i]
        match(s, /^[ \t]*/)
        ind = RLENGTH
        sub(/^[ \t]*/, "", s)
        ordered = (s ~ /^[0-9]/)
        match(s, /^([-*+]|[0-9]+[.)])[ \t]+/)
        marker = substr(s, 1, RLENGTH)
        body = substr(s, RLENGTH + 1)
        while (top > 0 && ind < INDENTS[top]) { closelevel(); top-- }
        if (top == 0 || ind > INDENTS[top]) {
            top++
            INDENTS[top] = ind
            openlevel(ordered)
        }
        sub(/[ \t]+${d}/, "", marker)
        PEND = body
        PENDLVL = top
        PENDORD = ordered
        PENDMARK = marker
        i++
    }
    flushitem()
    while (top > 0) { closelevel(); top-- }
    lastblank = 0
    return i
}

# 空行をまたいだ次の項目が、いまと同じ種類 (箇条書き / 番号付き) か
function sametype(s,   ind, ordered, k) {
    match(s, /^[ \t]*/)
    ind = RLENGTH
    sub(/^[ \t]*/, "", s)
    ordered = (s ~ /^[0-9]/)
    k = top
    while (k > 0 && ind < INDENTS[k]) k--
    if (k == 0) return 1
    return (LTYPE[k] == (ordered ? "ol" : "ul"))
}

function openlevel(ordered) {
    LTYPE[top] = ordered ? "ol" : "ul"
    LIOPEN[top] = 0
    if (html) emit("<" LTYPE[top] ">")
}

# 入れ子のリストは親の <li> の**中**へ入れる (兄弟として並べると字下げが 1 段足りない)。
function closelevel() {
    if (!html) return
    if (LIOPEN[top]) { emit("</li>"); LIOPEN[top] = 0 }
    emit("</" LTYPE[top] ">")
}

# 行をつなぐときの区切り (和文どうしは空白を入れない)
function joiner(a, b,   c1, c2) {
    if (a == "" || b == "") return ""
    c1 = cpat(a, lastcharpos(a))
    c2 = cpat(b, 1)
    return (c1 > 127 && c2 > 127) ? "" : " "
}

function lastcharpos(s,   i, p) {
    i = 1; p = 1
    while (i <= length(s)) { p = i; i += clen(s, i) }
    return p
}

function flushitem(   body, first, cont, ind, mark, done) {
    if (PEND == "") return
    body = PEND
    PEND = ""
    if (html) {
        if (LIOPEN[PENDLVL]) emit("</li>")
        if (body ~ /^\[[ xX]\][ \t]/) {
            done = (body ~ /^\[[xX]\]/)
            sub(/^\[[ xX]\][ \t]+/, "", body)
            emit("<li>" (done ? "[x] " : "[ ] ") inl(body))
        } else {
            emit("<li>" inl(body))
        }
        LIOPEN[PENDLVL] = 1
        return
    }
    ind = sp((PENDLVL - 1) * 2)
    if (body ~ /^\[[ xX]\][ \t]/) {
        done = (body ~ /^\[[xX]\]/)
        sub(/^\[[ xX]\][ \t]+/, "", body)
        mark = done ? (color ? B1 "[x]" B0 : "[x]") : "[ ]"
        mark = mark " "
    } else if (PENDORD) {
        mark = PENDMARK " "
    } else {
        mark = (PENDLVL % 2 == 1 ? BULLET1 : BULLET2) " "
        if (color) mark = D1 mark D0
    }
    first = ind mark
    cont = ind sp(vwidth(mark))
    wrap(inl(body), first, cont)
}

function table(i,   ncols, r, c, j, line, parts, cnt, wmax, total, out, sep, cell) {
    blank()
    r = 0; ncols = 0
    delete TB
    delete RAW
    delete ALIGN
    delete COLW
    while (i <= n && istablerow(L[i]) && QD[i] == QCUR) {
        line = L[i]
        sub(/^[ \t]*\|/, "", line)
        sub(/\|[ \t]*${d}/, "", line)
        cnt = split(line, parts, "|")
        if (isdelim(L[i])) {
            for (c = 1; c <= cnt; c++) {
                j = parts[c]
                gsub(/[ \t]/, "", j)
                if (j ~ /^:.*:${d}/) ALIGN[c] = "c"
                else if (j ~ /:${d}/) ALIGN[c] = "r"
                else ALIGN[c] = "l"
            }
            i++
            continue
        }
        r++
        for (c = 1; c <= cnt; c++) {
            j = parts[c]
            sub(/^[ \t]+/, "", j); sub(/[ \t]+${d}/, "", j)
            gsub(/\\\|/, "|", j)
            RAW[r, c] = j
            TB[r, c] = inl(j)
        }
        if (cnt > ncols) ncols = cnt
        i++
    }
    if (html) {
        emit("<table>")
        for (j = 1; j <= r; j++) {
            emit(j == 1 ? "<thead><tr>" : "<tr>")
            for (c = 1; c <= ncols; c++) {
                cell = ((j SUBSEP c) in TB) ? TB[j, c] : ""
                emit(j == 1 ? "<th>" cell "</th>" : "<td>" cell "</td>")
            }
            emit(j == 1 ? "</tr></thead><tbody>" : "</tr>")
        }
        emit("</tbody></table>")
        lastblank = 0
        return i
    }
    total = 0
    for (c = 1; c <= ncols; c++) {
        wmax = 0
        for (j = 1; j <= r; j++) {
            if (!((j SUBSEP c) in TB)) continue
            if (vwidth(TB[j, c]) > wmax) wmax = vwidth(TB[j, c])
        }
        COLW[c] = wmax
        total += wmax + 3
    }
    total -= 3
    if (total > width - vwidth(QPFX)) {
        # 幅に入らない表は「見出し: 中身」の並びにする (携帯の幅で読めるように)
        for (j = 2; j <= r; j++) {
            for (c = 1; c <= ncols; c++) {
                if (!((j SUBSEP c) in TB) || RAW[j, c] == "") continue
                wrap(((1 SUBSEP c) in TB ? dim(RAW[1, c] ": ") : "") TB[j, c], "", "  ")
            }
            if (j < r) blank()
        }
        lastblank = 0
        return i
    }
    for (j = 1; j <= r; j++) {
        out = ""
        for (c = 1; c <= ncols; c++) {
            cell = ((j SUBSEP c) in TB) ? TB[j, c] : ""
            if (j == 1 && color) cell = B1 cell B0
            out = out pad(cell, COLW[c], ALIGN[c])
            if (c < ncols) out = out (color ? D1 " " VBAR " " D0 : " | ")
        }
        emit(out)
        if (j == 1) {
            sep = ""
            for (c = 1; c <= ncols; c++) {
                sep = sep rep(HL, COLW[c])
                if (c < ncols) sep = sep HL CROSS HL
            }
            emit(color ? D1 sep D0 : sep)
        }
    }
    lastblank = 0
    return i
}

function pad(s, w, a,   d, l) {
    d = w - vwidth(s)
    if (d <= 0) return s
    if (a == "r") return sp(d) s
    if (a == "c") { l = int(d / 2); return sp(l) s sp(d - l) }
    return s sp(d)
}

function para(i,   body) {
    body = ""
    while (i <= n && !isblank(i) && QD[i] == QCUR && !isfence(L[i]) && !ishr(L[i]) \
           && L[i] !~ /^[ \t]*#+([ \t]|${d})/ && !isitem(L[i]) && !isimgline(L[i])) {
        if (istablerow(L[i]) && i + 1 <= n && isdelim(L[i + 1])) break
        if (body == "") body = L[i]
        else body = body joiner(body, L[i]) L[i]
        i++
    }
    sub(/^[ \t]+/, "", body)
    if (body == "") return i + 1
    blank()
    if (html) emit("<p>" inl(body) "</p>")
    else wrap(inl(body), "", "")
    lastblank = 0
    return i
}

function hr() {
    blank()
    if (html) emit("<hr>")
    else emit((color ? D1 : "") rep(HL, width - vwidth(QPFX)) (color ? D0 : ""))
    lastblank = 0
}

# 生の HTML (<details> など)。読み物画面はそのまま通し、端末では札を外して
# 中の文字だけ薄く出す (<summary><b>…</b></summary> がそのまま流れると読めない)。
function rawhtml(i,   line) {
    blank()
    while (i <= n && !isblank(i)) {
        if (html) { emit(L[i]); i++; continue }
        line = L[i]
        gsub(/<[^>]*>/, "", line)
        sub(/^[ \t]+/, "", line); sub(/[ \t]+${d}/, "", line)
        if (line != "") wrap(dim(inl(line)), "", "  ")
        i++
    }
    lastblank = 0
    return i
}

function block(i,   s) {
    if (isblank(i)) return i + 1
    setquote(QD[i])
    s = L[i]
    if (isfence(s)) return fence(i)
    if (s ~ /^[ \t]*#+([ \t]|${d})/) return heading(i)
    if (ishr(s)) { hr(); return i + 1 }
    if (isitem(s)) return listblock(i)
    if (isimgline(s)) return imgline(i)
    if (istablerow(s) && i + 1 <= n && isdelim(L[i + 1])) return table(i)
    if (s ~ /^(    |\t)/ && QD[i] == 0) return indentcode(i)
    if (s ~ /^[ \t]*<[a-zA-Z\/!]/) return rawhtml(i)
    return para(i)
}

# 引用の深さに応じた前置き (端末) / <blockquote> の開け閉め (読み物画面) を決める
function setquote(d,   k) {
    QCUR = d
    QPFX = ""
    if (html) {
        while (HQ > d) { emit("</blockquote>"); HQ-- }
        while (HQ < d) { emit("<blockquote>"); HQ++ }
        return
    }
    if (d <= 0) return
    for (k = 0; k < d; k++) QPFX = QPFX (color ? D1 VBAR D0 " " : "| ")
}

# ---------- 走らせる ----------

BEGIN {
    ESC = sprintf("%c", 27)
    BSL = sprintf("%c", 92)
    for (i = 0; i < 256; i++) ORD[sprintf("%c", i)] = i
    HL     = sprintf("%c%c%c", 226, 148, 128)   # ─
    HH     = sprintf("%c%c%c", 226, 148, 129)   # ━
    VBAR   = sprintf("%c%c%c", 226, 148, 130)   # │
    CROSS  = sprintf("%c%c%c", 226, 148, 188)   # ┼
    BULLET1 = sprintf("%c%c%c", 226, 128, 162)  # •
    BULLET2 = sprintf("%c%c%c", 226, 151, 166)  # ◦
    html = (mode == "html")
    if (ambi < 1) ambi = 1
    if (!html && width < 20) width = 20
    if (color) {
        B1 = ESC "[1m"; B0 = ESC "[22m"
        # ⚠ 斜体 (SGR 3) はこの端末では描かれない。*強調* が普通の字と区別できなく
        #   なるので、man と同じように下線も一緒に付ける (斜体を描く端末では両方出る)。
        I1 = ESC "[3m" ESC "[4m"; I0 = ESC "[24m" ESC "[23m"
        U1 = ESC "[4m"; U0 = ESC "[24m"
        K1 = ESC "[9m"; K0 = ESC "[29m"
        D1 = ESC "[2m"; D0 = ESC "[22m"
        C1 = ESC "[36m"; C0 = ESC "[39m"
        L1 = ESC "[34m"; L0 = ESC "[39m"
    }
    nout = 0; lastblank = 1; top = 0; PEND = ""; QCUR = 0; QPFX = ""; HQ = 0
    infence = 0; fenceq = 0; fencere = ""
}

# 先に引用の > を剥がして深さだけ覚える。
# ⚠ ただし ``` の中は剥がさない。コードには行頭の > (リダイレクト・>>> の入力例) が
#   ふつうに出てくるので、一緒に剥がすとコードが書き換わってしまう。
{
    line = ${d}0
    if (infence) {
        d = 0
        while (d < fenceq && line ~ /^[ \t]*>/) { sub(/^[ \t]*>[ ]?/, "", line); d++ }
        L[NR] = line
        QD[NR] = fenceq
        if (line ~ fencere) infence = 0
    } else {
        d = 0
        while (line ~ /^[ \t]*>/) { sub(/^[ \t]*>[ ]?/, "", line); d++ }
        L[NR] = line
        QD[NR] = d
        if (line ~ /^[ \t]*(```|~~~)/) {
            fenceq = d
            fencere = "^[ \t]*" fencemark(line)
            infence = 1
        }
    }
}

END {
    n = NR
    if (html) {
        print "<!doctype html><html><head><meta charset=\"utf-8\">"
        print "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        print "<title>" esc(title) "</title>"
        print "<style>" CSS "</style></head><body><main>"
    }
    i = 1
    # フロントマター (--- で囲まれた頭書き) は薄く出す
    if (n >= 2 && L[1] ~ /^---[ \t]*${d}/) {
        j = 2
        while (j <= n && L[j] !~ /^(---|\.\.\.)[ \t]*${d}/) j++
        if (j <= n) {
            for (k = 2; k < j; k++) emit(html ? "<p class=\"fm\">" esc(L[k]) "</p>" : dim(L[k]))
            lastblank = 0
            i = j + 1
        }
    }
    while (i <= n) i = block(i)
    if (html) {
        while (HQ > 0) { print "</blockquote>"; HQ-- }
        print "</main></body></html>"
    }
}
Z2MD_AWK

CSS='
:root{color-scheme:light dark}
body{margin:0;background:var(--z2-bg,#fff);color:var(--z2-fg,#111);
font:16px/1.75 -apple-system,"Noto Sans JP","Noto Sans",sans-serif}
main{padding:14px 16px 40px;max-width:42em;margin:0 auto}
h1,h2,h3,h4,h5,h6{line-height:1.35;margin:1.8em 0 .5em;font-weight:600}
h1{font-size:20px;margin-top:.3em;padding-bottom:.3em;border-bottom:2px solid var(--z2-line,#ddd)}
h2{font-size:17px;padding-bottom:.25em;border-bottom:1px solid var(--z2-line,#ddd)}
h3{font-size:15px}
h4,h5,h6{font-size:14px;color:var(--z2-dim,#666)}
p{margin:.85em 0}
a{color:var(--z2-accent,#0a7d3f)}
code{font-family:ui-monospace,monospace;font-size:.88em;background:var(--z2-bg2,#f1f1f1);padding:1px 4px}
pre{background:var(--z2-bg2,#f1f1f1);padding:10px 12px;overflow-x:auto;
border-left:2px solid var(--z2-line,#ddd)}
pre code{background:none;padding:0;font-size:12.5px;line-height:1.55}
blockquote{margin:.9em 0;padding:.1em 0 .1em 12px;border-left:3px solid var(--z2-line,#ddd);
color:var(--z2-dim,#666)}
ul,ol{margin:.7em 0;padding-left:1.4em}
li{margin:.3em 0}
table{border-collapse:collapse;margin:1em 0;font-size:14px;display:block;overflow-x:auto}
th,td{border:1px solid var(--z2-line,#ddd);padding:5px 8px;text-align:left;vertical-align:top}
th{background:var(--z2-bg2,#f1f1f1);font-weight:600;white-space:nowrap}
hr{border:0;border-top:1px solid var(--z2-line,#ddd);margin:1.6em 0}
img{max-width:100%;height:auto;margin:.6em 0}
.fm{font-size:12px;color:var(--z2-dim,#666)}
'

$cInlineImg
inline_images() {
    in=${d}1
    out=${d}2
    if ! grep -q "${d}IMGTOK" "${d}in" 2>/dev/null; then
        cp "${d}in" "${d}out"
        return
    fi
    : > "${d}TMP/map"
    LC_ALL=C awk -v tok="${d}IMGTOK" -v tok2="${d}IMGTOK2" '
        {
            s = ${d}0
            while ((i = index(s, tok)) > 0) {
                s = substr(s, i + length(tok))
                j = index(s, tok)
                if (j == 0) break
                body = substr(s, 1, j - 1)
                s = substr(s, j + length(tok))
                k = index(body, tok2)
                print (k > 0) ? substr(body, 1, k - 1) : body
            }
        }
    ' "${d}in" | sort -u | while IFS= read -r url; do
        [ -n "${d}url" ] || continue
        real=${d}url
        case "${d}real" in /*) ;; *) real=${d}srcdir/${d}real ;; esac
        [ -r "${d}real" ] || continue
        case "${d}real" in
            *.png|*.PNG) mime=image/png ;;
            *.jpg|*.JPG|*.jpeg|*.JPEG) mime=image/jpeg ;;
            *.gif|*.GIF) mime=image/gif ;;
            *.webp|*.WEBP) mime=image/webp ;;
            *.svg|*.SVG) mime=image/svg+xml ;;
            *) continue ;;
        esac
        sz=${d}(wc -c < "${d}real" 2>/dev/null) || continue
        [ "${d}sz" -le 1500000 ] || continue
        command -v base64 >/dev/null 2>&1 || continue
        printf '%s\t%s\n' "${d}url" "data:${d}mime;base64,${d}(base64 < "${d}real" | tr -d '\n')" >> "${d}TMP/map"
    done
    LC_ALL=C awk -v mapfile="${d}TMP/map" -v tok="${d}IMGTOK" -v tok2="${d}IMGTOK2" -v lbl="${d}IMGLABEL" '
        BEGIN {
            while ((getline ln < mapfile) > 0) {
                p = index(ln, "\t")
                if (p > 0) MAP[substr(ln, 1, p - 1)] = substr(ln, p + 1)
            }
        }
        {
            out = ""
            s = ${d}0
            while ((i = index(s, tok)) > 0) {
                out = out substr(s, 1, i - 1)
                s = substr(s, i + length(tok))
                j = index(s, tok)
                if (j == 0) { out = out s; s = ""; break }
                body = substr(s, 1, j - 1)
                s = substr(s, j + length(tok))
                k = index(body, tok2)
                url = (k > 0) ? substr(body, 1, k - 1) : body
                alt = (k > 0) ? substr(body, k + length(tok2)) : ""
                if (url in MAP) {
                    out = out "<img src=\"" MAP[url] "\" alt=\"" alt "\">"
                } else {
                    out = out "<p class=\"fm\">" lbl " " (alt == "" ? url : alt " (" url ")") "</p>"
                }
            }
            print out s
        }
    ' "${d}in" > "${d}out"
}

IMGTOK="@@Z2IMG@@"
IMGTOK2="@@ALT@@"
IMGLABEL="$mImgLabel"

if [ "${d}viewmode" = 1 ] || [ -n "${d}outfile" ]; then
    page=${d}{outfile:-${d}HOME/.z2term/md/page.html}
    mkdir -p "${d}(dirname -- "${d}page")" || die "$mNoDir"
    LC_ALL=C awk -v mode=html -v color=0 -v width=0 -v ambi="${d}ambi" \
        -v imgtok=0 -v title="${d}title" -v CSS="${d}CSS" -v IMGLABEL="${d}IMGLABEL" \
        -v IMGTOKEN="${d}IMGTOK" -v IMGTOKEN2="${d}IMGTOK2" -v IMGMARK="${d}IMGMARK" \
        -f "${d}TMP/md.awk" "${d}src" > "${d}TMP/page.html" || die "$mConvFail"
    inline_images "${d}TMP/page.html" "${d}page"
    if [ "${d}viewmode" = 1 ]; then
        command -v z2-view >/dev/null 2>&1 || {
            printf '%s\n' "md.sh: $mNoView" >&2
            printf '%s\n' "${d}page"
            exit 1
        }
        z2-view "${d}page" "${d}title"
    else
        printf '%s\n' "${d}page"
    fi
    exit 0
fi

render() {
    LC_ALL=C awk -v mode=ansi -v color="${d}color" -v width="${d}width" -v ambi="${d}ambi" \
        -v imgtok="${d}imgtok" -v title="${d}title" -v CSS="" -v IMGLABEL="${d}IMGLABEL" \
        -v IMGTOKEN="${d}IMGTOK" -v IMGTOKEN2="${d}IMGTOK2" -v IMGMARK="${d}IMGMARK" \
        -f "${d}TMP/md.awk" "${d}src"
}

if [ "${d}imgtok" = 1 ] && grep -q '!\[' "${d}src" 2>/dev/null; then
    $cImgLoop
    render | while IFS= read -r line; do
        case "${d}line" in
            "${d}IMGMARK"*)
                rest=${d}{line#"${d}IMGMARK"}
                p=${d}{rest%%"${d}TAB"*}
                case "${d}p" in /*) ;; *) p=${d}srcdir/${d}p ;; esac
                if [ -r "${d}p" ]; then
                    z2-img "${d}p" 2>/dev/null || printf '%s %s\n' "${d}IMGLABEL" "${d}p"
                else
                    printf '%s %s\n' "${d}IMGLABEL" "${d}p"
                fi
                ;;
            *) printf '%s\n' "${d}line" ;;
        esac
    done
else
    render
fi
"""
}
