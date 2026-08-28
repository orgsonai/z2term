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
    // 言語ごとの文言を選ぶ道具。3 言語目は t(en = …, ja = …) の後ろへ変わり値を足す ([CliText])。
    val t = CliText(lang)
    val d = "${'$'}"

    // --- 1. 入門: 出来事に反応する (待ち受けは z2-when がやる) ---
    val watchBasic = buildString {
        appendLine("#!/bin/sh")
        t.lines(
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
            ),
            "zh-CN" to listOf(
                "# watch-basic.sh — 入门用的宏。对设备上发生的事情做出反应。",
                "# 等待由应用一侧 (z2-when) 负责，所以它不常驻: 事情发生时跑一次就结束。",
                "# 准备: ⚙设置 → 打开“系统事件检测”",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (耳机再登记一条 'event:headset_*')",
            ),
            "zh-TW" to listOf(
                "# watch-basic.sh — 入門用的巨集。對裝置上發生的事情做出反應。",
                "# 等待由應用程式一側 (z2-when) 負責，所以它不常駐: 事情發生時跑一次就結束。",
                "# 準備: ⚙設定 → 開啟“系統事件偵測”",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (耳機再登記一條 'event:headset_*')",
            ),
        ).forEach { appendLine(it) }
        appendLine()
        t.lines(
            en = listOf(
            "# What happened arrives in Z2_WHEN_EVENT (z2-when events lists the names).",
                "# To try it by hand:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            ja = listOf(
            "# 何が起きたかは Z2_WHEN_EVENT に入る (使える名前は z2-when events で一覧できる)。",
                "# 手で試すときは:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "zh-CN" to listOf(
                "# 发生了什么会放进 Z2_WHEN_EVENT (可用的名字用 z2-when events 列出)。",
                "# 想手动试的话:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "zh-TW" to listOf(
                "# 發生了什麼會放進 Z2_WHEN_EVENT (可用的名字用 z2-when events 列出)。",
                "# 想手動試的話:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
        ).forEach { appendLine(it) }
        appendLine("event=${d}{Z2_WHEN_EVENT:-}")
        t.lines(
            en = listOf(
            "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT is empty. To try by hand: Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            ja = listOf(
            "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT が空です。手で試すなら Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "zh-CN" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT 是空的。想手动试就用 Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "zh-TW" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT 是空的。想手動試就用 Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
        ).forEach { appendLine(it) }
        appendLine()
        appendLine("case \"${d}event\" in")
        t.lines(
            en = listOf(
            "  power_connected)    z2-toast \"Charging started\" ;;",
                "  power_disconnected) z2-toast \"Charging stopped\" ;;",
            ),
            ja = listOf(
            "  power_connected)    z2-toast \"充電を開始しました\" ;;",
                "  power_disconnected) z2-toast \"充電をやめました\" ;;",
            ),
            "zh-CN" to listOf(
                "  power_connected)    z2-toast \"开始充电了\" ;;",
                "  power_disconnected) z2-toast \"停止充电了\" ;;",
            ),
            "zh-TW" to listOf(
                "  power_connected)    z2-toast \"開始充電了\" ;;",
                "  power_disconnected) z2-toast \"停止充電了\" ;;",
            ),
        ).forEach { appendLine(it) }
        appendLine("  headset_plugged)    z2-media play ;;")
        appendLine("  headset_unplugged)  z2-media pause ;;")
        appendLine("esac")
    }

    // --- 2. z2-state を使う: 状況を見てから動く (z2-when が起こす「使い切り」の形) ---
    val batteryAlert = buildString {
        appendLine("#!/bin/sh")
        t.lines(
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
            ),
            "zh-CN" to listOf(
                "# battery-alert.sh — 电量低了就通知，但要看当时的情况分开处理。",
                "# 用 z2-state 看“屏幕是否亮着”: 亮着就用吐司，熄着就用通知。",
                "# 准备: ⚙设置 → 打开“系统事件检测”",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# 电量由 z2-when 传过来。为了手动试也能跑，再用 z2-state 兜一下底。",
            ),
            "zh-TW" to listOf(
                "# battery-alert.sh — 電量低了就通知，但要看當時的情況分開處理。",
                "# 用 z2-state 看“螢幕是否亮著”: 亮著就用快顯訊息，熄著就用通知。",
                "# 準備: ⚙設定 → 開啟“系統事件偵測”",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# 電量由 z2-when 傳過來。為了手動試也能跑，再用 z2-state 兜一下底。",
            ),
        ).forEach { appendLine(it) }
        appendLine("level=${d}{Z2_WHEN_LEVEL:-${d}(z2-state level)}")
        appendLine()
        t.lines(
            en = listOf(
            "# Say nothing while charging (it is not actually draining)",
            ),
            ja = listOf(
            "# 充電中なら知らせない (勝手に減っているわけではないので)",
            ),
            "zh-CN" to listOf(
                "# 正在充电时就不通知 (并不是电量自己在掉)",
            ),
            "zh-TW" to listOf(
                "# 正在充電時就不通知 (並不是電量自己在掉)",
            ),
        ).forEach { appendLine(it) }
        appendLine("[ \"${d}(z2-state charging)\" = \"true\" ] && exit 0")
        appendLine()
        appendLine("if [ \"${d}(z2-state screen)\" = \"on\" ]; then")
        t.lines(
            en = listOf(
            "  z2-toast \"Battery ${d}{level}%\"",
                "else",
                "  z2-notify -h \"Low battery\" \"${d}{level}% left\"",
            ),
            ja = listOf(
            "  z2-toast \"電池 ${d}{level}%\"",
                "else",
                "  z2-notify -h \"電池注意\" \"残り ${d}{level}% です\"",
            ),
            "zh-CN" to listOf(
                "  z2-toast \"电量 ${d}{level}%\"",
                "else",
                "  z2-notify -h \"电量偏低\" \"还剩 ${d}{level}%\"",
            ),
            "zh-TW" to listOf(
                "  z2-toast \"電量 ${d}{level}%\"",
                "else",
                "  z2-notify -h \"電量偏低\" \"還剩 ${d}{level}%\"",
            ),
        ).forEach { appendLine(it) }
        appendLine("fi")
    }

    // --- 3. 時刻で動く (z2-when time: が OS のアラームで起こす) ---
    val dailyReport = buildString {
        appendLine("#!/bin/sh")
        t.lines(
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
            ),
            "zh-CN" to listOf(
                "# daily-report.sh — 每天早上定时播报电量和连接状况。",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# 时刻由 OS 的闹钟唤醒，所以 Doze 中也会响 (为了省电可能晚几分钟)。",
                "# 不依赖检测开关的开关状态。",
            ),
            "zh-TW" to listOf(
                "# daily-report.sh — 每天早上定時播報電量和連線狀況。",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# 時刻由 OS 的鬧鐘喚醒，所以 Doze 中也會響 (為了省電可能晚幾分鐘)。",
                "# 不依賴偵測開關的開關狀態。",
            ),
        ).forEach { appendLine(it) }
        appendLine()
        appendLine("level=${d}(z2-state level)")
        t.lines(
            en = listOf(
            "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=mobile; fi",
                "z2-say \"Good morning. Battery ${d}{level} percent, network ${d}{net}\"",
            ),
            ja = listOf(
            "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=モバイル; fi",
                "z2-say \"おはようございます。電池は ${d}{level} パーセント、接続は ${d}{net} です\"",
            ),
            "zh-CN" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=移动数据; fi",
                "z2-say \"早上好。电量 ${d}{level} 个百分点，网络是 ${d}{net}\"",
            ),
            "zh-TW" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=行動數據; fi",
                "z2-say \"早上好。電量 ${d}{level} 個百分點，網路是 ${d}{net}\"",
            ),
        ).forEach { appendLine(it) }
    }

    // --- 4. 実用: 通知内のワンタイムコードを自動コピー (MACRO-GUIDE 5-6 と同じ内容) ---
    val otpClip = buildString {
        appendLine("#!/bin/sh")
        t.lines(
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
            ),
            "zh-CN" to listOf(
                "# otp-clip.sh — 把通知里的一次性验证码自动放进剪贴板，",
                "# 过 TTL 秒后，如果剪贴板里还是那个值就自动清掉。",
                "# 准备: ⚙设置 → 打开“通知检测” ＋ 授予系统的“通知使用权”",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ 有时会把敏感通知里的验证码遮蔽掉。想要可靠就用短信版",
                "#    (otp-sms.sh) — 短信正文不会被遮蔽。",
            ),
            "zh-TW" to listOf(
                "# otp-clip.sh — 把通知裡的一次性驗證碼自動放進剪貼簿，",
                "# 過 TTL 秒後，如果剪貼簿裡還是那個值就自動清掉。",
                "# 準備: ⚙設定 → 開啟“通知偵測” ＋ 授予系統的“通知使用權”",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ 有時會把敏感通知裡的驗證碼遮蔽掉。想要可靠就用簡訊版",
                "#    (otp-sms.sh) — 簡訊正文不會被遮蔽。",
            ),
        ).forEach { appendLine(it) }
        append(otpWhenBody(d, t))
    }

    // --- 5. 実用: SMS 内のワンタイムコードを自動コピー (通知でなく SMS を直読み = 伏せ字を迂回) ---
    val otpSms = buildString {
        appendLine("#!/bin/sh")
        t.lines(
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
            ),
            "zh-CN" to listOf(
                "# otp-sms.sh — 把收到的短信里的一次性验证码自动放进剪贴板，",
                "# 过 TTL 秒后，如果剪贴板里还是那个值就自动清掉。",
                "# 这是 otp-clip.sh 的短信版。和通知不同，短信正文永远不会被敏感通知保护",
                "# (Android 15+) 遮蔽，锁屏状态下也照样能取到。",
                "# 准备: ⚙设置 → 打开“短信检测” ＋ 授予系统的接收短信权限",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "zh-TW" to listOf(
                "# otp-sms.sh — 把收到的簡訊裡的一次性驗證碼自動放進剪貼簿，",
                "# 過 TTL 秒後，如果剪貼簿裡還是那個值就自動清掉。",
                "# 這是 otp-clip.sh 的簡訊版。和通知不同，簡訊正文永遠不會被敏感通知保護",
                "# (Android 15+) 遮蔽，鎖屏狀態下也照樣能取到。",
                "# 準備: ⚙設定 → 開啟“簡訊偵測” ＋ 授予系統的接收簡訊權限",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
        ).forEach { appendLine(it) }
        append(otpWhenBody(d, t))
    }

    // --- 6. 実用: 電話帳に無い番号からの着信を控える (通知の種別で拾う = 権限を増やさない) ---
    val unknownCall = buildString {
        appendLine("#!/bin/sh")
        t.lines(
            en = listOf(
            "# unknown-call.sh — when a number that is not in your contacts calls, show it in a notification.",
                "# Its \"Copy\" button puts the number on the clipboard (why it waits for a press: see below).",
                "# Setup: Settings -> \"Notification detection\" ON + grant OS notification access",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# To catch missed calls too, register a second rule (same script, different category):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            ja = listOf(
            "# unknown-call.sh — 電話帳に無い番号から電話が来たら、その番号を通知に出す。",
                "# 通知の「コピー」ボタンで番号がクリップボードへ入る (押すまで待つ理由は下に)。",
                "# 準備: ⚙設定 →「通知検知」ON ＋ OS の「通知アクセス」許可",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# 不在着信も控えたいなら、種別違いでもう 1 本登録する (中身は同じでよい):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "zh-CN" to listOf(
                "# unknown-call.sh — 通讯录里没有的号码打来电话时，把号码显示在通知里。",
                "# 通知上的“复制”按钮会把号码放进剪贴板 (为什么要等人按，见下面)。",
                "# 准备: ⚙设置 → 打开“通知检测” ＋ 授予系统的“通知使用权”",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# 也想记未接来电的话，用不同的类别再登记一条 (脚本还是这个):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "zh-TW" to listOf(
                "# unknown-call.sh — 通訊錄裡沒有的號碼打來電話時，把號碼顯示在通知裡。",
                "# 通知上的“複製”按鈕會把號碼放進剪貼簿 (為什麼要等人按，見下面)。",
                "# 準備: ⚙設定 → 開啟“通知偵測” ＋ 授予系統的“通知使用權”",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# 也想記未接來電的話，用不同的類別再登記一條 (指令碼還是這個):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
        ).forEach { appendLine(it) }
        append(unknownCallBody(d, t))
    }

    // --- 7. 実用: フィード購読 (時刻トリガーで 1 回だけ走る「使い切り」の形) ---
    val rss = buildString {
        appendLine("#!/bin/sh")
        t.lines(
            en = listOf(
            "# rss.sh — poll feeds and keep only what is new, as a notification and as text.",
                "# Unlike the other samples this one does **not** stay resident: it is the",
                "# 'run once from a time trigger and exit' shape.",
            ),
            ja = listOf(
            "# rss.sh — フィードを見に行って、新着だけを通知とテキストに残す。",
                "# 他のサンプルと違い**常駐しない**。時刻トリガーで 1 回走って終わる形の見本でもある。",
            ),
            "zh-CN" to listOf(
                "# rss.sh — 去看订阅源，只留下新的内容，做成通知和文本。",
                "# 和其他示例不同，这个**不常驻**: 它是“由时间触发跑一次就结束”的形态。",
            ),
            "zh-TW" to listOf(
                "# rss.sh — 去看訂閱源，只留下新的內容，做成通知和文字。",
                "# 和其他示例不同，這個**不常駐**: 它是“由時間觸發跑一次就結束”的形態。",
            ),
        ).forEach { appendLine(it) }
        append(rssBody(d, t))
    }

    // --- 8. 実用: 集めた記事を 1 本ずつ開く (状態ウィジェットのボタンから叩く用) ---
    val rssOpen = buildString {
        appendLine("#!/bin/sh")
        t.lines(
            en = listOf(
            "# rss-open.sh — open what rss.sh collected, newest first, one article per tap.",
                "# It shows up as a macro button on the status widget, so each tap opens the next one.",
            ),
            ja = listOf(
            "# rss-open.sh — rss.sh が集めた記事を、新しいものから 1 本ずつブラウザで開く。",
                "# 状態ウィジェットのマクロボタンに出るので、タップするたびに次の 1 本が開く。",
            ),
            "zh-CN" to listOf(
                "# rss-open.sh — 把 rss.sh 收集到的文章从新到旧，每点一次打开一篇。",
                "# 它会出现在状态小组件的宏按钮上，所以每点一下就打开下一篇。",
            ),
            "zh-TW" to listOf(
                "# rss-open.sh — 把 rss.sh 收集到的文章從新到舊，每點一次開啟一篇。",
                "# 它會出現在狀態小工具的巨集按鈕上，所以每點一下就開啟下一篇。",
            ),
        ).forEach { appendLine(it) }
        append(rssOpenBody(d, t))
    }

    // --- 9. 実用: 通知でリマインド (単発 = z2-alarm / 繰り返し = z2-when time: の使い分けの見本) ---
    val remind = buildString {
        appendLine("#!/bin/sh")
        t.lines(
            en = listOf(
            "# remind.sh — remind you with a notification: one-shot (in 30m, at 18:30) or",
                "# repeating (daily / weekdays / weekly). Fires with the app closed (an OS alarm",
                "# wakes it; no detection switch needed). Snooze from the notification buttons.",
            ),
            ja = listOf(
            "# remind.sh — 通知でリマインドする。単発 (30分後・18:30) と繰り返し (毎日・平日・毎週)。",
                "# アプリを閉じていても鳴る (OS のアラームで起こされるため。「検知」ON も不要)。",
                "# 通知のボタンからスヌーズできる。**アプリ側に予定機能を作らないための見本**でもある。",
            ),
            "zh-CN" to listOf(
                "# remind.sh — 用通知来提醒: 一次性 (30 分钟后、18:30) 或者",
                "# 重复 (每天 / 工作日 / 每周)。关着应用也会响 (由 OS 的闹钟唤醒，",
                "# 也不需要打开检测开关)。可以从通知上的按钮小睡。",
            ),
            "zh-TW" to listOf(
                "# remind.sh — 用通知來提醒: 一次性 (30 分鐘後、18:30) 或者",
                "# 重複 (每天 / 工作日 / 每週)。關著應用程式也會響 (由 OS 的鬧鐘喚醒，",
                "# 也不需要開啟偵測開關)。可以從通知上的按鈕小睡。",
            ),
        ).forEach { appendLine(it) }
        append(remindBody(d, t))
    }

    // --- 10. 実用: QR にして渡す (端末に絵を出す / PNG に保存する・使い切り) ---
    val qr = buildString {
        appendLine("#!/bin/sh")
        t.lines(
            en = listOf(
            "# qr.sh — turn text or a file into a QR code: drawn here, or saved as a PNG.",
                "# Hands something to another device without retyping it. Runs once, no residency.",
            ),
            ja = listOf(
            "# qr.sh — テキストやファイルを QR にして、この端末に絵で出す / PNG に保存する。",
                "# 別の端末やカメラへ「打ち直さずに渡す」ための道具。常駐しない使い切りのマクロ。",
            ),
            "zh-CN" to listOf(
                "# qr.sh — 把文本或文件变成二维码: 在这里画出来，或者存成 PNG。",
                "# 不用重新敲一遍就能交给另一台设备。跑一次就结束，不常驻。",
            ),
            "zh-TW" to listOf(
                "# qr.sh — 把文字或檔案變成二維條碼: 在這裡畫出來，或者存成 PNG。",
                "# 不用重新敲一遍就能交給另一台裝置。跑一次就結束，不常駐。",
            ),
        ).forEach { appendLine(it) }
        append(qrBody(d, t))
    }

    return linkedMapOf(
        "watch-basic.sh" to watchBasic,
        "battery-alert.sh" to batteryAlert,
        "daily-report.sh" to dailyReport,
        "otp-clip.sh" to otpClip,
        "otp-sms.sh" to otpSms,
        "unknown-call.sh" to unknownCall,
        "remind.sh" to remind,
        "rss.sh" to rss,
        "rss-open.sh" to rssOpen,
        "qr.sh" to qr,
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
    val head = t(
        en = """
#
# Setup:
#   1) One feed URL per line in:  ~/.z2term/rss/feeds.txt
#   2) Poll every 30 minutes:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) Optional - one feed or word per line that must not get buried:  ~/.z2term/rss/important.txt
#      Anything matching gets a notification of its own, so a busy feed cannot push it out.
#      Write part of a URL or part of a title (e.g. example.org).
#   4) Optional - let the notification's button open that very article:
#        z2-when event:notify_action run 'case "${d}Z2_WHEN_EVENT_NAME" in rss:*) z2-open "${d}{Z2_WHEN_EVENT_NAME#rss:}" ;; esac'
#   5) Optional - widget: point a live tail at ~/.z2term/rss/latest.txt in "start (head)" mode.
#      Each line carries its URL, so tapping a line opens that article.
#
# Needs: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
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
#   4) 通知の「開く」でその記事をブラウザへ (任意):
#        z2-when event:notify_action run 'case "${d}Z2_WHEN_EVENT_NAME" in rss:*) z2-open "${d}{Z2_WHEN_EVENT_NAME#rss:}" ;; esac'
#   5) ウィジェット (任意): ライブ tail で ~/.z2term/rss/latest.txt を「先頭 (head)」表示。
#      行に URL が入っているので、タップするとその記事が開く。
#
# 必要: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
# 電池: 取りに行くほど食う。30 分より短くしないこと。
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
        "zh-CN" to """
#
# 准备:
#   1) 在这里一行写一个订阅源 URL:  ~/.z2term/rss/feeds.txt
#   2) 每 30 分钟看一次:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) 可选 - 一行一个、不想被埋掉的订阅源或词:  ~/.z2term/rss/important.txt
#      命中的文章会单独发一条通知，这样流量大的源就压不掉它。
#      写 URL 的一部分或标题的一部分都行 (例: example.org)。
#   4) 可选 - 让通知上的按钮直接打开那一篇:
#        z2-when event:notify_action run 'case "${d}Z2_WHEN_EVENT_NAME" in rss:*) z2-open "${d}{Z2_WHEN_EVENT_NAME#rss:}" ;; esac'
#   5) 可选 - 小组件: 用实时 tail 以“开头 (head)”模式看 ~/.z2term/rss/latest.txt。
#      每行都带着自己的 URL，所以点一行就能打开那篇文章。
#
# 需要: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
# 电池: 取得越勤耗得越多。不要短于 30 分钟。
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
        "zh-TW" to """
#
# 準備:
#   1) 在這裡一行寫一個訂閱源 URL:  ~/.z2term/rss/feeds.txt
#   2) 每 30 分鐘看一次:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) 可選 - 一行一個、不想被埋掉的訂閱源或詞:  ~/.z2term/rss/important.txt
#      命中的文章會單獨發一條通知，這樣流量大的源就壓不掉它。
#      寫 URL 的一部分或標題的一部分都行 (例: example.org)。
#   4) 可選 - 讓通知上的按鈕直接開啟那一篇:
#        z2-when event:notify_action run 'case "${d}Z2_WHEN_EVENT_NAME" in rss:*) z2-open "${d}{Z2_WHEN_EVENT_NAME#rss:}" ;; esac'
#   5) 可選 - 小工具: 用即時 tail 以“開頭 (head)”模式看 ~/.z2term/rss/latest.txt。
#      每行都帶著自己的 URL，所以點一行就能開啟那篇文章。
#
# 需要: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
# 電池: 取得越勤耗得越多。不要短於 30 分鐘。
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
"""
    )

    val cKeep = t(en = "max lines kept in seen/latest", ja = "seen/latest に残す行数の上限", "zh-CN" to "seen/latest 里保留的行数上限", "zh-TW" to "seen/latest 裡保留的行數上限")
    val cNoPy = t(
        en = "python3 is required (e.g. apk add python3)",
        ja = "python3 が要ります (apk add python3 など)",
        "zh-CN" to "需要 python3 (例如 apk add python3)",
        "zh-TW" to "需要 python3 (例如 apk add python3)"
    )
    val cWriteFeeds = t(
        en = "Write one feed URL per line in:",
        ja = "フィードの URL を 1 行に 1 本書いてください:",
        "zh-CN" to "请在这里一行写一个订阅源 URL:",
        "zh-TW" to "請在這裡一行寫一個訂閱源 URL:"
    )
    val cFeedsHint = t(
        en = "# One feed URL per line (lines starting with # are ignored)",
        ja = "# 1 行に 1 本、フィードの URL を書く (# で始まる行は無視)",
        "zh-CN" to "# 一行写一个订阅源 URL (以 # 开头的行会被忽略)",
        "zh-TW" to "# 一行寫一個訂閱源 URL (以 # 開頭的行會被忽略)"
    )
    val cSkipFail = t(
        en = "silently skip a feed that fails to fetch or parse (the rest continue)",
        ja = "取得や解析に失敗した 1 本は黙って飛ばす (他のフィードは続ける)",
        "zh-CN" to "取得或解析失败的那一条就默默跳过 (其他订阅源继续)",
        "zh-TW" to "取得或解析失敗的那一條就默默跳過 (其他訂閱源繼續)"
    )
    val cDiff = t(
        en = "# Subtract what we have seen to leave only what is new (same trick as z2scan's baseline diff).\n# Feed dates and ordering are not trusted; neither is reliable.",
        ja = "# 既読を引いて新着だけにする (z2scan のベースライン差分と同じやり方)。\n# フィードの日付や並び順は当てにしない — どちらも当てにならない。",
        "zh-CN" to "# 减去已读的部分，只留下新的 (和 z2scan 的基准差分是同一个套路)。\n# 不相信订阅源的日期和排列顺序 — 这两样都靠不住。",
        "zh-TW" to "# 減去已讀的部分，只留下新的 (和 z2scan 的基準差分是同一個套路)。\n# 不相信訂閱源的日期和排列順序 — 這兩樣都靠不住。"
    )
    val cPrepend = t(
        en = "# Stack newest-first so the widget's \"start (head)\" mode reads correctly.",
        ja = "# 新着が上に来るように積む (ウィジェットの「先頭 (head)」表示でそのまま読める)。",
        "zh-CN" to "# 新的堆在上面，这样小组件的“开头 (head)”模式读起来才顺。",
        "zh-TW" to "# 新的堆在上面，這樣小工具的“開頭 (head)”模式讀起來才順。"
    )
    val cNotify = t(en = "%s new", ja = "新着 %s 件", "zh-CN" to "新增 %s 条", "zh-TW" to "新增 %s 條")
    val cOpen = t(en = "Open", ja = "開く", "zh-CN" to "打开", "zh-TW" to "開啟")
    val cListNone = t(
        en = "Nothing collected yet — run it once with no arguments first.",
        ja = "まだ何も集めていません。まず引数なしで実行してください。",
        "zh-CN" to "还什么都没收集到 — 请先不带参数运行一次。",
        "zh-TW" to "還什麼都沒收集到 — 請先不帶參數執行一次。"
    )
    val cListHint = t(
        en = "List them with: rss.sh list [count]",
        ja = "一覧: rss.sh list [件数]",
        "zh-CN" to "列出来的方法: rss.sh list [条数]",
        "zh-TW" to "列出來的方法: rss.sh list [條數]"
    )
    val cHitMax = t(
        en = "max number of per-article notifications in one run (the rest go to the summary)",
        ja = "1 回に出す個別通知の上限 (超えた分はまとめ通知へ回す)",
        "zh-CN" to "一次运行里单独通知的条数上限 (超出的并到汇总通知里)",
        "zh-TW" to "一次執行裡單獨通知的條數上限 (超出的並到匯總通知裡)"
    )
    val cImportantTemplate = t.lines(
        en = listOf(
            "# One feed or word per line that must not get buried (lines starting with # are ignored).",
                "# Part of a URL or part of a title works. Each match gets its own notification.",
                "# e.g.", "#example.org",
            ),
        ja = listOf(
            "# 見逃したくないフィード / 語を 1 行 1 本 (# で始まる行は無視)。",
                "# URL の一部でも題名の一部でもよい。当たった記事は 1 本ずつ別の通知になる。",
                "# 例:", "#example.org",
            ),
        "zh-CN" to listOf(
                "# 一行一个、不想被埋掉的订阅源或词 (以 # 开头的行会被忽略)。",
                "# 写 URL 的一部分或标题的一部分都行。命中的文章会各自单独发一条通知。",
                "# 例:", "#example.org",
            ),
        "zh-TW" to listOf(
                "# 一行一個、不想被埋掉的訂閱源或詞 (以 # 開頭的行會被忽略)。",
                "# 寫 URL 的一部分或標題的一部分都行。命中的文章會各自單獨發一條通知。",
                "# 例:", "#example.org",
            )
    )
    val cSplitDoc = t(
        en = "# Split off what must not be missed. The summary body only carries 3 lines, so a busy feed\n" +
            "# updating at the same time pushes the one that mattered out. Matches get **their own\n" +
            "# notification** (the app hands out a separate id per notification, so nothing is replaced).",
        ja = "# 見逃したくないものを切り分ける。まとめ通知は本文に 3 件しか載らないので、流量の多い\n" +
            "# フィードが同時に更新されると重要な 1 本が押し出される。当たった記事は**通知を分ける**\n" +
            "# (通知 ID はアプリ側で 1 件ずつ別に振られるので、分ければ上書きも省略もされない)。",
        "zh-CN" to "# 把不能错过的挑出来。汇总通知的正文只放得下 3 条，流量大的\n" +
            "# 订阅源要是同时更新，重要的那一条就被挤出去了。命中的文章**单独发通知**\n" +
            "# (通知 ID 由应用一条一条分开发放，所以分开之后既不会被覆盖也不会被省略)。",
        "zh-TW" to "# 把不能錯過的挑出來。匯總通知的正文只放得下 3 條，流量大的\n" +
            "# 訂閱源要是同時更新，重要的那一條就被擠出去了。命中的文章**單獨發通知**\n" +
            "# (通知 ID 由應用程式一條一條分開發放，所以分開之後既不會被覆寫也不會被省略)。"
    )
    val cNameDoc = t(
        en = "# Put the URL in the notification's name: pressing the button hands it back as {name} in\n" +
            "# notify_action, so the right article opens even with several notifications on screen (setup 4).",
        ja = "# 通知の名前に URL を入れておく。ボタンを押すと notify_action の {name} でそのまま返るので、\n" +
            "# どの通知の「開く」なのかを取り違えずに開ける (準備 4 のルール)。",
        "zh-CN" to "# 把 URL 放进通知的名字里。按下按钮时它会原样作为 notify_action 的 {name} 回来，\n" +
            "# 所以即使屏幕上有好几条通知，也不会搞错是哪一条的“打开”(准备 4 的规则)。",
        "zh-TW" to "# 把 URL 放進通知的名字裡。按下按鈕時它會原樣作為 notify_action 的 {name} 回來，\n" +
            "# 所以即使螢幕上有好幾條通知，也不會搞錯是哪一條的“開啟”(準備 4 的規則)。"
    )
    val cListDoc = t(
        en = "# Print what was collected as a readable list (no colour — it should look the same as the file/widget).\n" +
            "# latest.txt itself stays one-article-per-line raw data (the widget tail and rss-open.sh read it).",
        ja = "# 集めた記事を読みやすく並べて出す。色は使わない (ウィジェットやファイル表示と見え方を揃える)。\n" +
            "# 端末のときは OSC 8 で**題名そのものをリンク**にし、URL の行を並べない — 長い URL は\n" +
            "# 折り返して題名と混ざり、一覧として読めなくなるため。端末以外 (パイプ・リダイレクト) では\n" +
            "# エスケープが邪魔なので素のテキストに落とし、URL も見えるようにする。\n" +
            "# latest.txt は 1 行 1 記事の素データのままにしておく (ウィジェットの tail と rss-open.sh が読む)。",
        "zh-CN" to "# 把收集到的文章排得好读一些输出。不用颜色 — 要和文件、小组件里看到的一致。\n" +
            "# latest.txt 本身保持一行一篇的原始数据 (小组件的 tail 和 rss-open.sh 会读它)。",
        "zh-TW" to "# 把收集到的文章排得好讀一些輸出。不用顏色 — 要和檔案、小工具裡看到的一致。\n" +
            "# latest.txt 本身保持一行一篇的原始資料 (小工具的 tail 和 rss-open.sh 會讀它)。"
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
KEEP=500                                  # $cKeep
HITMAX=5                                  # $cHitMax

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
  z2-notify -h -n "rss:${d}u" -b "$cOpen" "${d}{t:-${d}u}" "${d}host"
done < "${d}HITS"

rn=${d}(grep -c . "${d}REST" 2>/dev/null)
if [ "${d}{rn:-0}" -gt 0 ]; then
  z2-notify -h -n "rss:${d}(head -1 "${d}REST" | cut -f1)" -b "$cOpen" \
    "${d}(printf '$cNotify' "${d}rn")" "${d}(cut -f2 "${d}REST" | head -3)"
fi
rm -f "${d}HITS" "${d}REST"

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
private fun rssOpenBody(d: String, t: CliText): String {
    val head = t(
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
""",
        "zh-CN" to """
#
# 准备: 先把收集的那一侧装好
#   z2-macro install rss
# 小组件: 在状态小组件的设置里，把“rss-open”分配给一个按钮。
#
# z2-run: 把 rss-open 分配给状态小组件的按钮 (想在终端里试就用 sh ~/.z2term/macros/rss-open.sh)
""",
        "zh-TW" to """
#
# 準備: 先把收集的那一側裝好
#   z2-macro install rss
# 小工具: 在狀態小工具的設定裡，把“rss-open”分配給一個按鈕。
#
# z2-run: 把 rss-open 分配給狀態小工具的按鈕 (想在終端機裡試就用 sh ~/.z2term/macros/rss-open.sh)
"""
    )
    val cNone = t(en = "No articles yet", ja = "まだ記事がありません", "zh-CN" to "还没有文章", "zh-TW" to "還沒有文章")
    val cAllRead = t(en = "Nothing new to open", ja = "新しい記事はありません", "zh-CN" to "没有新的可以打开了", "zh-TW" to "沒有新的可以開啟了")
    val cPick = t(
        en = "# latest.txt lines are \"title  URL\". Take the first URL that has not been opened yet.",
        ja = "# latest.txt は「タイトル  URL」。まだ開いていない先頭の URL を 1 本だけ取る。",
        "zh-CN" to "# latest.txt 的每行是“标题  URL”。取出还没打开过的第一个 URL。",
        "zh-TW" to "# latest.txt 的每行是“標題  URL”。取出還沒開啟過的第一個 URL。"
    )
    val cCap = t(
        en = "cap the opened list so it cannot grow forever",
        ja = "開いた記録が増え続けないよう上限をかける",
        "zh-CN" to "给已打开的记录设上限，免得一直涨下去",
        "zh-TW" to "給已開啟的記錄設上限，免得一直漲下去"
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
    val copied = t(en = "Copied code: ${d}{code}", ja = "コードをコピー: ${d}{code}", "zh-CN" to "已复制验证码: ${d}{code}", "zh-TW" to "已複製驗證碼: ${d}{code}")
    val cleared = t(en = "Cleared the copied code", ja = "コピーしたコードをクリアしました", "zh-CN" to "已清除复制的验证码", "zh-TW" to "已清除複製的驗證碼")
    val otpTitle = t(en = "One-time code", ja = "認証コード", "zh-CN" to "一次性验证码", "zh-TW" to "一次性驗證碼")
    val cTtl = t(
        en = "seconds before the copy is cleared (only applies when it went in directly)",
        ja = "コピーから何秒でクリアするか (直に入れられたときだけ効く)",
        "zh-CN" to "复制之后过多少秒清除 (只有直接放进去时才生效)",
        "zh-TW" to "複製之後過多少秒清除 (只有直接放進去時才生效)"
    )
    val cCode = t(
        en = "# The app already extracted the code. Do nothing when it could not.",
        ja = "# コードの抽出はアプリ側が済ませてある。取れなかったときは何もしない。",
        "zh-CN" to "# 验证码的提取应用一侧已经做好了。取不到的时候什么都不做。",
        "zh-TW" to "# 驗證碼的提取應用程式一側已經做好了。取不到的時候什麼都不做。"
    )
    val cTry = t(
        en = "# Try to put it in directly. This only lands while you are **looking at the app**:\n" +
            "# Android 10+ lets only the app in front write to the clipboard, so a run in the\n" +
            "# background is dropped silently. Read it back to see whether it landed.",
        ja = "# まず直に入れてみる。入るのは**この画面を見ているとき**だけ — Android 10+ は前面の\n" +
            "# アプリしかクリップボードに書けないので、裏で走ったぶんは黙って捨てられる。\n" +
            "# 入ったかどうかは読み返して確かめる (書けなくてもエラーにはならないため)。",
        "zh-CN" to "# 先试着直接放进去。只有**你正在看着这个应用**的时候才放得进去 —\n" +
            "# Android 10+ 只让前台应用写剪贴板，在后台跑的那次会被默默丢掉。\n" +
            "# 放没放进去，读回来看一眼就知道 (写不进去也不会报错)。",
        "zh-TW" to "# 先試著直接放進去。只有**你正在看著這個應用程式**的時候才放得進去 —\n" +
            "# Android 10+ 只讓前景應用程式寫剪貼簿，在背景跑的那次會被默默丟掉。\n" +
            "# 放沒放進去，讀回來看一眼就知道 (寫不進去也不會報錯)。"
    )
    val cFallback = t(
        en = "  # It did not land = you are not looking at the app. Hand it over through the\n" +
            "  # notification's \"Copy\" button instead (pressing it brings the app to the front).\n" +
            "  # ⚠ What goes in that way is not cleared by TTL below: clearing also needs the front.",
        ja = "  # 入らなかった = 画面を見ていない。通知の「コピー」ボタンで渡す (押した瞬間だけ前面に出る)。\n" +
            "  # ⚠ この道で入れたぶんは下の TTL では消えない — 消すのにも前面にいることが要るため。",
        "zh-CN" to "  # 没放进去 = 你没在看这个应用。改用通知上的“复制”按钮交给你\n" +
            "  # (按下的那一瞬间应用会到前台)。\n" +
            "  # ⚠ 走这条路放进去的，下面的 TTL 清不掉 — 因为清除同样需要在前台。",
        "zh-TW" to "  # 沒放進去 = 你沒在看這個應用程式。改用通知上的“複製”按鈕交給你\n" +
            "  # (按下的那一瞬間應用程式會到前景)。\n" +
            "  # ⚠ 走這條路放進去的，下面的 TTL 清不掉 — 因為清除同樣需要在前景。"
    )
    val cClear = t(
        en = "# After TTL, clear the clipboard only if it still holds the code we copied\n" +
            "# (anything copied since then is left alone).",
        ja = "# TTL 秒後、クリップボードがコピー時の値のままなら空にする。\n" +
            "# その間に別のものをコピーしていたら、そちらは消さずに残す。",
        "zh-CN" to "# 过了 TTL 秒，只有剪贴板里还是我们复制的那个验证码时才清空\n" +
            "# (这期间复制过别的东西的话，那些不动)。",
        "zh-TW" to "# 過了 TTL 秒，只有剪貼簿裡還是我們複製的那個驗證碼時才清空\n" +
            "# (這期間複製過別的東西的話，那些不動)。"
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
 * 「電話帳に無い番号からの着信を控える」サンプルの本体。
 *
 * **アプリ側に電話まわりの権限を持たせないための見本**でもある。発信者が電話帳にいるかを
 * 直に調べるには連絡先 (`READ_CONTACTS`) と通話履歴 (`READ_CALL_LOG`。Android 9+ は
 * 着信番号を得るのに必須) が要り、後者は既定の電話アプリ以外ほぼ配布できない。
 *
 * 代わりに**電話アプリが出す通知の表示**を見る。電話アプリは電話帳にある相手なら名前を、
 * 無ければ番号そのものを出すので、「表示が番号の形か」を見れば同じ答えが出る。必要なのは
 * 既にある通知アクセスだけで、権限は 1 つも増えない。
 */
private fun unknownCallBody(d: String, t: CliText): String {
    val cHow = t(
        en = """
# ■ How "not in contacts" is decided
#   A phone app shows the **name** for someone in your contacts and the **bare number** for
#   someone who is not. So if the notification shows a number, that caller is not in contacts.
#   ⚠ This shape exists to keep z2term free of contacts (READ_CONTACTS) and call-log
#      (READ_CALL_LOG) permissions: notification access alone gives the same answer.
""",
        ja = """
# ■ 「電話帳に無い」をどう見分けているか
#   電話アプリは、電話帳にある相手なら**名前**を、無い相手なら**番号そのもの**を通知に出す。
#   だから通知の表示が番号の形なら、その相手は電話帳に載っていない。
#   ⚠ この形にしているのは、z2term に連絡先 (READ_CONTACTS) も通話履歴 (READ_CALL_LOG) も
#      持たせないため。通知アクセスだけで同じ答えが出るので、権限は 1 つも増えない。
""",
        "zh-CN" to """
# ■ 「不在通讯录里」是怎么判断的
#   电话应用对通讯录里的人显示**名字**，对不在通讯录里的人显示**号码本身**。
#   所以只要通知上显示的是号码，那位来电者就不在通讯录里。
#   ⚠ 之所以做成这个样子，是为了让 z2term 既不要通讯录 (READ_CONTACTS) 也不要通话记录
#      (READ_CALL_LOG) 权限: 只靠通知使用权就能得到同样的答案。
""",
        "zh-TW" to """
# ■ 「不在通訊錄裡」是怎麼判斷的
#   電話應用程式對通訊錄裡的人顯示**名字**，對不在通訊錄裡的人顯示**號碼本身**。
#   所以只要通知上顯示的是號碼，那位來電者就不在通訊錄裡。
#   ⚠ 之所以做成這個樣子，是為了讓 z2term 既不要通訊錄 (READ_CONTACTS) 也不要通話記錄
#      (READ_CALL_LOG) 權限: 只靠通知使用權就能得到同樣的答案。
"""
    )
    val cIsNum = t(
        en = """
# Is it a bare number? Strip every character a phone number may use (digits + - ( ) space);
# if **nothing is left** and 7-15 digits remain, treat it as a number.
#   -> Letters mixed in = a name = someone in your contacts, so do nothing.
#   -> "Unknown"/"Private number" also fall out here (loosen the test below if you want those).
# ⚠ Do not write this as case [!...]: the ) inside the pattern is read as the case separator.
""",
        ja = """
# 「番号そのもの」か? 電話番号で使う文字 (数字 + - ( ) 空白) を全部消して**何も残らず**、
# かつ数字が 7〜15 桁あれば番号とみなす。
#   → かな・漢字・英字が混ざる = 名前 = 電話帳にある相手なので、何もしない。
#   → 「非通知」「不明な発信者」も数字が足りずここで外れる (拾いたいなら下の判定を緩める)。
# ⚠ case の [!...] で書かないこと — パターン中の ) が case の区切りに読まれて構文エラーになる。
""",
        "zh-CN" to """
# 是不是「号码本身」? 把电话号码会用到的字符 (数字 + - ( ) 空格) 全部去掉，如果**什么都不剩**，
# 并且数字有 7〜15 位，就当成号码。
#   -> 混着字母 = 名字 = 通讯录里的人，什么都不做。
#   -> 「未知」「隐藏号码」也会因为数字不够而在这里被排除 (想收就把下面的判断放宽)。
# ⚠ 不要写成 case [!...]: 模式里的 ) 会被当成 case 的分隔符。
""",
        "zh-TW" to """
# 是不是「號碼本身」? 把電話號碼會用到的字元 (數字 + - ( ) 空格) 全部去掉，如果**什麼都不剩**，
# 並且數字有 7〜15 位，就當成號碼。
#   -> 混著字母 = 名字 = 通訊錄裡的人，什麼都不做。
#   -> 「未知」「隱藏號碼」也會因為數字不夠而在這裡被排除 (想收就把下面的判斷放寬)。
# ⚠ 不要寫成 case [!...]: 模式裡的 ) 會被當成 case 的分隔符。
"""
    )
    val cScan = t(
        en = "# The caller is usually the title, but some phone apps put it in the text. Check both.",
        ja = "# 発信者は題名に出るのが普通だが、電話アプリによっては本文側に出る。両方を見る。",
        "zh-CN" to "# 来电者一般在标题里，但有的电话应用会放在正文里。两边都看。",
        "zh-TW" to "# 來電者一般在標題裡，但有的電話應用程式會放在正文裡。兩邊都看。"
    )
    val cSkip = t(
        en = "# A name was shown = in contacts. Do nothing.",
        ja = "# 名前が出ていた = 電話帳にある相手。何もしない。",
        "zh-CN" to "# 显示的是名字 = 通讯录里的人。什么都不做。",
        "zh-TW" to "# 顯示的是名字 = 通訊錄裡的人。什麼都不做。"
    )
    val cWhat = t(
        en = "# The category tells which one fired (same value as the notify:category= you registered).",
        ja = "# 着信中と不在着信のどちらで動いたかは種別で分かる (登録した notify:category= と同じ値)。",
        "zh-CN" to "# 是哪一种触发的，看类别就知道 (和登记时的 notify:category= 是同一个值)。",
        "zh-TW" to "# 是哪一種觸發的，看類別就知道 (和登記時的 notify:category= 是同一個值)。"
    )
    val cCopy = t(
        en = """
# Hand the number over through the notification's "Copy" button (-c). Calling z2-clip set here
# would not land: Android 10+ only lets the app in front write to the clipboard, and during a
# call that is the phone app. Pressing the button brings z2term to the front for that instant.
""",
        ja = """
# 番号は**通知の「コピー」ボタン**で渡す (-c)。ここで z2-clip set を呼んでも入らない —
# Android 10+ は前面のアプリしかクリップボードに書けず、着信中に前面にいるのは電話アプリ
# だから。ボタンを押した瞬間だけ z2term が前面に出るので、そのときに確実に入る。
""",
        "zh-CN" to """
# 号码通过**通知上的「复制」按钮**交给你 (-c)。在这里调用 z2-clip set 是放不进去的 —
# Android 10+ 只让前台应用写剪贴板，而来电时在前台的是电话应用。
# 按下按钮的那一瞬间 z2term 会到前台，所以那时一定放得进去。
""",
        "zh-TW" to """
# 號碼透過**通知上的「複製」按鈕**交給你 (-c)。在這裡呼叫 z2-clip set 是放不進去的 —
# Android 10+ 只讓前景應用程式寫剪貼簿，而來電時在前景的是電話應用程式。
# 按下按鈕的那一瞬間 z2term 會到前景，所以那時一定放得進去。
"""
    )
    val missed = t(en = "Missed call", ja = "不在着信", "zh-CN" to "未接来电", "zh-TW" to "未接來電")
    val incoming = t(en = "Incoming call", ja = "着信", "zh-CN" to "来电", "zh-TW" to "來電")
    val title = t(en = "number not in contacts", ja = "電話帳に無い番号", "zh-CN" to "不在通讯录里的号码", "zh-TW" to "不在通訊錄裡的號碼")
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
    // 言語ごとの文言を選ぶ道具。3 言語目は t(en = …, ja = …) の後ろへ変わり値を足す ([CliText])。
    val t = CliText(lang)
    val d = "${'$'}"
    val src = "/usr/local/share/z2term/macros"

    val usage = t.lines(
        en = listOf(
            "usage: z2-macro <subcommand>",
                "  list                list bundled samples (marked new / same / differs)",
                "  install <name|all>  copy into ~/.z2term/macros/ (never overwrites; -f to force)",
                "  diff <name>         what differs between your copy and the bundled one (yours on the left)",
                "  show <name>         print the script",
                "  run <name>          run it here (Ctrl-C to stop)",
                "  dir                 print where macros live",
            ),
        ja = listOf(
            "usage: z2-macro <サブコマンド>",
                "  list                同梱サンプルの一覧 (未導入 / 同じ / 差分あり つき)",
                "  install <名前|all>  ~/.z2term/macros/ へコピー (既存は上書きしない。-f で上書き)",
                "  diff <名前>         端末のコピーと同梱版の違いを見る (左が自分の側)",
                "  show <名前>         中身を表示",
                "  run <名前>          その場で実行 (Ctrl-C で止める)",
                "  dir                 マクロの置き場所を表示",
            ),
        "zh-CN" to listOf(
                "usage: z2-macro <子命令>",
                "  list                列出随附的示例 (带 未安装 / 相同 / 有差异 标记)",
                "  install <名字|all>  复制到 ~/.z2term/macros/ (不覆盖已有的。-f 强制覆盖)",
                "  diff <名字>         看设备上的副本和随附版有什么不同 (左边是你自己的)",
                "  show <名字>         显示脚本内容",
                "  run <名字>          就地运行 (Ctrl-C 停止)",
                "  dir                 显示宏放在哪里",
            ),
        "zh-TW" to listOf(
                "usage: z2-macro <子指令>",
                "  list                列出隨附的示例 (帶 未安裝 / 相同 / 有差異 標記)",
                "  install <名字|all>  複製到 ~/.z2term/macros/ (不覆寫已有的。-f 強制覆寫)",
                "  diff <名字>         看裝置上的副本和隨附版有什麼不同 (左邊是你自己的)",
                "  show <名字>         顯示指令碼內容",
                "  run <名字>          就地執行 (Ctrl-C 停止)",
                "  dir                 顯示巨集放在哪裡",
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

    val stCols = t.of(en = 7, ja = 8, "zh-CN" to 7, "zh-TW" to 7)
    val stNew = padVisual(t(en = "new", ja = "未導入", "zh-CN" to "未安装", "zh-TW" to "未安裝"), stCols)
    val stSame = padVisual(t(en = "same", ja = "同じ", "zh-CN" to "相同", "zh-TW" to "相同"), stCols)
    val stDiff = padVisual(t(en = "differs", ja = "差分あり", "zh-CN" to "有差异", "zh-TW" to "有差異"), stCols)

    val msgInstalled = t(en = "installed:", ja = "導入しました:", "zh-CN" to "已安装:", "zh-TW" to "已安裝:")
    // 「既にある」を**同じ / 違う**で言い分ける (0.8.332)。一律「既にあります」だと、同梱版が
    // 直っていても気付けず、古いコピーを使い続けることになる (remind.sh で実際に起きた)。
    val msgSame = t(en = "the same thing is already installed:", ja = "同じ内容がすでに入っています:", "zh-CN" to "同样的内容已经装过了:", "zh-TW" to "同樣的內容已經裝過了:")
    val msgOutdated = t(en = "yours differs from the bundled one:", ja = "同梱版と中身が違います:", "zh-CN" to "和随附版的内容不一样:", "zh-TW" to "和隨附版的內容不一樣:")
    // ⚠ 「同梱版が新しい」と断定しない。端末側の方が進んでいることが実際にある
    //    (アプリへ取り込んでいない拡張)。**先に diff** を見せてから -f を出す順にする。
    val msgHowUpdate = t(
        en = "look first: z2-macro diff %s   /   replace with the bundled one: z2-macro install -f %s (your own edits go too)",
        ja = "まず違いを見る: z2-macro diff %s   /   同梱版で置き換える: z2-macro install -f %s (自分で書き換えた分は消えます)",
        "zh-CN" to "先看看差别: z2-macro diff %s   /   用随附版替换: z2-macro install -f %s (你自己改的部分会没了)",
        "zh-TW" to "先看看差別: z2-macro diff %s   /   用隨附版替換: z2-macro install -f %s (你自己改的部分會沒了)"
    )
    val msgNotInstalled = t(
        en = "not installed yet (run z2-macro install first):",
        ja = "まだ導入していません (先に z2-macro install):",
        "zh-CN" to "还没有安装 (请先 z2-macro install):",
        "zh-TW" to "還沒有安裝 (請先 z2-macro install):"
    )
    val msgNoDiffTool = t(
        en = "no diff here. You can still read the bundled one with z2-macro show.",
        ja = "この環境に diff がありません。中身は z2-macro show で見られます。",
        "zh-CN" to "这个环境里没有 diff。内容可以用 z2-macro show 查看。",
        "zh-TW" to "這個環境裡沒有 diff。內容可以用 z2-macro show 查看。"
    )
    val msgSameAsBundled = t(en = "identical to the bundled one.", ja = "同梱版と同じです。", "zh-CN" to "和随附版相同。", "zh-TW" to "和隨附版相同。")
    val msgNotFound = t(en = "no such sample:", ja = "そんなサンプルはありません:", "zh-CN" to "没有这个示例:", "zh-TW" to "沒有這個示例:")
    val msgHintResident = t(
        en = "To keep it running, register this under Settings -> Resident servers:",
        ja = "常駐させるには ⚙設定 → 常駐サーバー に次を登録してください:",
        "zh-CN" to "要让它常驻，请在 ⚙设置 → 常驻服务 里登记下面这条:",
        "zh-TW" to "要讓它常駐，請在 ⚙設定 → 常駐服務 裡登記下面這條:"
    )
    // 常駐させないサンプル (時刻トリガーで 1 回走るもの・ウィジェットのボタンから叩くもの) がある。
    // そういうスクリプトは先頭に `# z2-run: <動かし方>` を書いておき、install はそれを出す。
    // ⚠ 一律に「常駐サーバーに登録」と案内すると、**使い切りのスクリプトを常駐させてしまう**
    //   (終了するたび supervisor が再起動するので、フィード取得なら延々と取りに行く)。
    val msgHintRun = t(en = "How to run it:", ja = "動かし方:", "zh-CN" to "怎么运行:", "zh-TW" to "怎麼執行:")

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
private const val ASK_TRIES = 3

private fun remindBody(d: String, t: CliText): String {
    val head = t(
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
""",
        "zh-CN" to """
#
# 准备 (只做一次):
#   sh ~/.z2term/macros/remind.sh setup   登记两个接口和两个磁贴
#
# 语法:
#   remind.sh <什么时候> <正文>      添加一条提醒
#   remind.sh list                   列出来 (开头是编号)
#   remind.sh del <编号|all>         取消 (rm 也可以)
#   remind.sh peek                   用通知看一览 (带「删除」按钮)
#   remind.sh ask                    在通知的回复框里问 (给磁贴用)
#   remind.sh setup                  登记接口和磁贴 (最开始做一次)
#   remind.sh help                   这段说明
#
# <什么时候> — 一次性:
#   30m / 90s / 2h                   从现在算起
#   18:30                            下一个 18:30 (过了就是明天)
#   tomorrow 18:30 / 3d 07:00        按天 (3d = 3 天后)
#   07/30 19:00                      月/日 (过了就是明年)
#   2030 07/30 19:00                 带年份
#   203007301900 / 07301900          只写数字 (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ 不写时刻的话就保持**当前的时刻** (3d / 07/30 …)
#
# <什么时候> — 重复:
#   daily 07:00 / weekday 09:00      每天 / 周一到周五
#   weekly mon 09:00                 那个星期几
#   monthly 15 09:00                 那个日子
#   yearly 07/30 19:00               那个月日
#   every 19:00 / every wed 19:00    只写 every 也行 (由后面那个词决定)
#   every 15 19:00 / every 07/30 19:00  -> 每天 / 每周 / 每月 / 每年
#
# 例:
#   remind.sh 30m 吃药
#   remind.sh tomorrow 18:30 倒垃圾
#   remind.sh monthly 25 10:00 房租
#   remind.sh list        ->  1  ⏰ 07/30 18:30  倒垃圾
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (之后只要 remind.sh 30m ... 就行)
""",
        "zh-TW" to """
#
# 準備 (只做一次):
#   sh ~/.z2term/macros/remind.sh setup   登記兩個介面和兩個圖塊
#
# 語法:
#   remind.sh <什麼時候> <正文>      新增一條提醒
#   remind.sh list                   列出來 (開頭是編號)
#   remind.sh del <編號|all>         取消 (rm 也可以)
#   remind.sh peek                   用通知看一覽 (帶「刪除」按鈕)
#   remind.sh ask                    在通知的回覆框裡問 (給圖塊用)
#   remind.sh setup                  登記介面和圖塊 (最開始做一次)
#   remind.sh help                   這段說明
#
# <什麼時候> — 一次性:
#   30m / 90s / 2h                   從現在算起
#   18:30                            下一個 18:30 (過了就是明天)
#   tomorrow 18:30 / 3d 07:00        按天 (3d = 3 天後)
#   07/30 19:00                      月/日 (過了就是明年)
#   2030 07/30 19:00                 帶年份
#   203007301900 / 07301900          只寫數字 (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ 不寫時刻的話就保持**當前的時刻** (3d / 07/30 …)
#
# <什麼時候> — 重複:
#   daily 07:00 / weekday 09:00      每天 / 週一到週五
#   weekly mon 09:00                 那個星期幾
#   monthly 15 09:00                 那個日子
#   yearly 07/30 19:00               那個月日
#   every 19:00 / every wed 19:00    只寫 every 也行 (由後面那個詞決定)
#   every 15 19:00 / every 07/30 19:00  -> 每天 / 每週 / 每月 / 每年
#
# 例:
#   remind.sh 30m 吃藥
#   remind.sh tomorrow 18:30 倒垃圾
#   remind.sh monthly 25 10:00 房租
#   remind.sh list        ->  1  ⏰ 07/30 18:30  倒垃圾
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (之後只要 remind.sh 30m ... 就行)
"""
    )
    val cStore = t(
        en = """# One reminder = one line in ${d}DIR/<id>.txt, tab separated: kind / label / when-id / text.
#   kind: once (still to fire) / fired (already fired) / repeat (recurring)
#   when-id: only for repeat (so delete can call z2-when remove). '-' otherwise.
# The text goes last so it can hold anything but a TAB.""",
        ja = """# 予定 1 件 = ${d}DIR/<id>.txt の 1 行。TAB 区切りで  種別 / 予定の表記 / when の id / 本文。
#   種別: once (これから鳴る単発) / fired (鳴った単発) / repeat (繰り返し)
#   when の id: repeat のときだけ入る (削除で z2-when remove するため)。それ以外は '-'。
# 本文を末尾に置くのは、TAB 以外の文字をそのまま持たせるため。""",
        "zh-CN" to """# 一条提醒 = ${d}DIR/<id>.txt 里的一行，用制表符分隔: 种类 / 显示的写法 / when 的 id / 正文。
#   种类: once (还没响的一次性) / fired (已经响过的一次性) / repeat (重复)
#   when 的 id: 只有 repeat 才有 (删除时要调 z2-when remove)。其余是 '-'。
# 正文放在最后，是为了让它能装下除 TAB 以外的任何字符。""",
        "zh-TW" to """# 一條提醒 = ${d}DIR/<id>.txt 裡的一行，用定位字元分隔: 種類 / 顯示的寫法 / when 的 id / 正文。
#   種類: once (還沒響的一次性) / fired (已經響過的一次性) / repeat (重複)
#   when 的 id: 只有 repeat 才有 (刪除時要調 z2-when remove)。其餘是 '-'。
# 正文放在最後，是為了讓它能裝下除 TAB 以外的任何字元。"""
    )
    val cParse = t(
        en = """# Read 1-3 words and decide:
#   KIND ... once|repeat   PLAN ... label to show   SPEC ... what z2-alarm/z2-when takes   USED ... words used""",
        ja = """# 引数 (1〜3 語) を読んで下記を決める。
#   KIND … once|repeat   PLAN … 一覧に出す表記   SPEC … z2-alarm/z2-when へ渡す形   USED … 使った語数""",
        "zh-CN" to """# 读 1〜3 个词，决定下面这些。
#   KIND … once|repeat   PLAN … 列表里显示的写法   SPEC … 交给 z2-alarm/z2-when 的形式   USED … 用掉了几个词""",
        "zh-TW" to """# 讀 1〜3 個詞，決定下面這些。
#   KIND … once|repeat   PLAN … 列表裡顯示的寫法   SPEC … 交給 z2-alarm/z2-when 的形式   USED … 用掉了幾個詞"""
    )
    val pDaily = t(en = "daily ", ja = "毎日 ", "zh-CN" to "每天 ", "zh-TW" to "每天 ")
    val pWeekday = t(en = "weekdays ", ja = "平日 ", "zh-CN" to "工作日 ", "zh-TW" to "工作日 ")
    val pWeekly = t(en = "weekly ", ja = "毎週", "zh-CN" to "每周", "zh-TW" to "每週")
    val pMonthly = t(en = "monthly ", ja = "毎月 ", "zh-CN" to "每月 ", "zh-TW" to "每月 ")
    val pYearly = t(en = "yearly ", ja = "毎年 ", "zh-CN" to "每年 ", "zh-TW" to "每年 ")
    val mDay = t(en = "", ja = "日", "zh-CN" to "日", "zh-TW" to "日")
    // 「毎 …」の展開先。⚠ **その言語で case が拾える語**であること (日本語面なら「毎日」等)。
    val eDaily = t(en = "daily", ja = "毎日", "zh-CN" to "每天", "zh-TW" to "每天")
    val eWeekly = t(en = "weekly", ja = "毎週", "zh-CN" to "每周", "zh-TW" to "每週")
    val eMonthly = t(en = "monthly", ja = "毎月", "zh-CN" to "每月", "zh-TW" to "每月")
    val eYearly = t(en = "yearly", ja = "毎年", "zh-CN" to "每年", "zh-TW" to "每年")
    val mBadDate = t(
        en =         "no such date (e.g. 07/30):",
        ja =         "その日付はありません (例: 07/30):",
        "zh-CN" to "没有这个日期 (例: 07/30):",
        "zh-TW" to "沒有這個日期 (例: 07/30):"
    )
    val cEvery = t(
        en = """  # The short "every ..." form. **The shape of the next word** picks daily/weekly/monthly/yearly:
  #   every 19:00 -> daily / every wed 19:00 -> weekly / every 15 19:00 -> monthly /
  #   every 07/30 19:00 -> yearly
  # ⚠ The word count does not change (one word swapped for another), so USED still holds.""",
        ja = """  # 「毎 …」の簡易指定 (要望)。**次の語の形**で 毎日 / 毎週 / 毎月 / 毎年 を決める。
  #   毎 19:00 → 毎日 / 毎 水 19:00 → 毎週 / 毎 15 19:00 → 毎月 / 毎 07/30 19:00 → 毎年
  # ⚠ 語数は変わらない (「毎」1 語が「毎日」等に置き換わるだけ) ので USED はそのままでよい。""",
        "zh-CN" to """  # 「every …」的简写。**后面那个词的形状**决定是每天/每周/每月/每年:
  #   every 19:00 -> 每天 / every wed 19:00 -> 每周 / every 15 19:00 -> 每月 /
  #   every 07/30 19:00 -> 每年
  # ⚠ 词的个数不变 (只是一个词换成了另一个词)，所以 USED 保持原样即可。""",
        "zh-TW" to """  # 「every …」的簡寫。**後面那個詞的形狀**決定是每天/每週/每月/每年:
  #   every 19:00 -> 每天 / every wed 19:00 -> 每週 / every 15 19:00 -> 每月 /
  #   every 07/30 19:00 -> 每年
  # ⚠ 詞的個數不變 (只是一個詞換成了另一個詞)，所以 USED 保持原樣即可。"""
    )
    val cExpand = t(
        en = "# Decide which recurrence \"every\" meant, from the word that follows it.",
        ja = "# 「毎」の次に来た語から、どの繰り返しかを決める。",
        "zh-CN" to "# 从 every 后面来的那个词，决定它指的是哪一种重复。",
        "zh-TW" to "# 從 every 後面來的那個詞，決定它指的是哪一種重複。"
    )
    val cDigits = t(
        en = """    # All-digit forms: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ Check 12 digits before 8 (the 8-digit pattern also matches a 12-digit string).""",
        ja = """    # 数字だけで書く形 (要望): 202607301900 = 年月日時分 / 07301900 = 月日時分。
    # ⚠ 12 桁 → 8 桁の順に見る (8 桁のパターンは 12 桁にも当たってしまうため)。""",
        "zh-CN" to """    # 只写数字的形式: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM。
    # ⚠ 要先看 12 位再看 8 位 (8 位的模式对 12 位的串也会命中)。""",
        "zh-TW" to """    # 只寫數字的形式: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM。
    # ⚠ 要先看 12 位再看 8 位 (8 位的模式對 12 位的串也會命中)。"""
    )
    val cYmd = t(
        en = """  # Written as a date. ⚠ z2-alarm cannot take a date, so this also folds into a delay.""",
        ja = """  # 年月日で書かれたもの。⚠ z2-alarm は日付を渡せないので、ここでも秒差へ寄せる。""",
        "zh-CN" to """  # 用年月日写的。⚠ z2-alarm 不能接收日期，所以这里也折算成时间差。""",
        "zh-TW" to """  # 用年月日寫的。⚠ z2-alarm 不能接收日期，所以這裡也折算成時間差。"""
    )
    val cCivil = t(
        en = """# Civil date -> days since 1970-01-01 (leap years included; Howard Hinnant's days_from_civil).
# ⚠ date -d "2026-07-30" is not dependable on busybox, so the count is done here.
# ⚠ Only the day difference is used and the time is left to [day_epoch], which cancels out the zone.""",
        ja = """# 年月日 → 1970-01-01 からの通算日 (うるう年込み・Howard Hinnant の days_from_civil)。
# ⚠ date -d "2026-07-30" は busybox で当てにならないので自前で数える。
# ⚠ 日数の差だけを使い、時刻は [day_epoch] に任せる (こうするとタイムゾーンが相殺される)。""",
        "zh-CN" to """# 年月日 → 从 1970-01-01 起的天数 (含闰年・Howard Hinnant 的 days_from_civil)。
# ⚠ date -d "2026-07-30" 在 busybox 上靠不住，所以自己数。
# ⚠ 只用天数之差，时刻交给 [day_epoch]，这样时区就相互抵消了。""",
        "zh-TW" to """# 年月日 → 從 1970-01-01 起的天數 (含閏年・Howard Hinnant 的 days_from_civil)。
# ⚠ date -d "2026-07-30" 在 busybox 上靠不住，所以自己數。
# ⚠ 只用天數之差，時刻交給 [day_epoch]，這樣時區就相互抵消了。"""
    )
    val cCron = t(
        en = """# Build a cron expression (min hour dom month dow) from HH:MM and a weekday field.
# Do NOT use expr to strip a leading zero: it exits 1 when the result is 0, so the right-hand
# side of `expr "${d}m" + 0 || echo "${d}m"` also runs and the value ends up two lines long.""",
        ja = """# HH:MM と曜日欄から cron 式を作る (分 時 日 月 曜日)。
# ⚠ 先頭 0 を落とすのに expr を使わないこと — 結果が 0 のとき終了コードが 1 になり、
#   `expr "${d}m" + 0 || echo "${d}m"` の右側まで走って値が 2 行に化ける (実際に踏んだ)。""",
        "zh-CN" to """# 从 HH:MM 和星期字段做出 cron 表达式 (分 时 日 月 星期)。
# ⚠ 不要用 expr 去掉开头的 0 — 结果为 0 时退出码是 1，
#   `expr "${d}m" + 0 || echo "${d}m"` 右边也会跑，值就变成两行了。""",
        "zh-TW" to """# 從 HH:MM 和星期字段做出 cron 表達式 (分 時 日 月 星期)。
# ⚠ 不要用 expr 去掉開頭的 0 — 結果為 0 時退出碼是 1，
#   `expr "${d}m" + 0 || echo "${d}m"` 右邊也會跑，值就變成兩行了。"""
    )
    val afterLabel = t(
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
}""",
        "zh-CN" to """# "30m" → "30分钟后 (14:35)"。把钟点也显示出来，是为了登记完能马上核对。
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num; unit=秒 ;; m) sec=${d}((num*60)); unit=分钟 ;; h) sec=${d}((num*3600)); unit=小时 ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "${d}num${d}unit后 (${d}at)" || echo "${d}num${d}unit后"
}""",
        "zh-TW" to """# "30m" → "30分鐘後 (14:35)"。把鐘點也顯示出來，是為了登記完能馬上核對。
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num; unit=秒 ;; m) sec=${d}((num*60)); unit=分鐘 ;; h) sec=${d}((num*3600)); unit=小時 ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "${d}num${d}unit後 (${d}at)" || echo "${d}num${d}unit後"
}"""
    )
    val mUsageAdd = t(
        en = "usage: remind.sh <when> <text>",
        ja = "usage: remind.sh <いつ> <本文>",
        "zh-CN" to "usage: remind.sh <什么时候> <正文>",
        "zh-TW" to "usage: remind.sh <什麼時候> <正文>"
    )
    val mBadWhen = t(
        en = "cannot read the time (try: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
        ja = "いつ？ が分かりません (例: 30m / 18:30 / 明日 18:30 / 3日後 09:00 / 毎日 07:00):",
        "zh-CN" to "看不懂时间 (例: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
        "zh-TW" to "看不懂時間 (例: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):"
    )
    val mBadTime = t(en = "write the time as HH:MM:", ja = "時刻は HH:MM で書いてください:", "zh-CN" to "时刻请写成 HH:MM:", "zh-TW" to "時刻請寫成 HH:MM:")
    val mBadRange = t(
        en =         "time out of range (00:00-23:59):",
        ja =         "時刻の範囲が違います (00:00〜23:59):",
        "zh-CN" to "时刻超出范围 (00:00〜23:59):",
        "zh-TW" to "時刻超出範圍 (00:00〜23:59):"
    )
    val mNoTime = t(
        en =         "no time given (e.g. daily 07:00):",
        ja =         "時刻が書かれていません (例: 毎日 07:00):",
        "zh-CN" to "没有写时刻 (例: daily 07:00):",
        "zh-TW" to "沒有寫時刻 (例: daily 07:00):"
    )
    val mBadDow = t(en = "unknown weekday:", ja = "曜日が分かりません:", "zh-CN" to "看不懂是星期几:", "zh-TW" to "看不懂是星期幾:")
    val mPastTime = t(
        en =         "that time has already passed:",
        ja =         "その時刻はもう過ぎています:",
        "zh-CN" to "那个时刻已经过去了:",
        "zh-TW" to "那個時刻已經過去了:"
    )
    val cDays = t(
        en = """    # Day-based wording. With no time, keep **the current time of day** (never invent a default).
    # It can also arrive as a single word, so the glued form is accepted too.""",
        ja = """    # 日付で書く言い方。時刻を省いたら**今と同じ時刻**にする (既定時刻を勝手に決めない)。
    # 「明日の18:30」のように 1 語で来ることもあるので、くっついた形も受ける。""",
        "zh-CN" to """    # 按日期写的说法。不写时刻就保持**当前的时刻** (不擅自定一个默认时刻)。
    # 也可能整个连成一个词送过来，所以粘在一起的写法也接受。""",
        "zh-TW" to """    # 按日期寫的說法。不寫時刻就保持**當前的時刻** (不擅自定一個預設時刻)。
    # 也可能整個連成一個詞送過來，所以黏在一起的寫法也接受。"""
    )
    val cDayEpoch = t(
        en = """# Turn "HH:MM, N days from now" into epoch seconds. ⚠ busybox has no date -d "tomorrow",
# so today's midnight is derived first and the days and time added on top.
# ⚠ A value with a leading zero ("08") is read as octal by ${d}(()), so strip it.
# ⚠ A day is added as 86400s, so it can be an hour off on a DST switch day.""",
        ja = """# 「N 日後の HH:MM」を epoch 秒にする。⚠ date -d "tomorrow" は busybox に無いので、
# 今日の 0 時を出してから日数と時刻を足す。
# ⚠ 先頭 0 の付いた値 ("08") を ${d}(()) に渡すと 8 進数と解釈されるので必ず落とす。
# ⚠ 1 日 = 86400 秒として足すので、夏時間のある地域では切り替え日に 1 時間ずれる。""",
        "zh-CN" to """# 把「N 天后的 HH:MM」换算成 epoch 秒。⚠ busybox 没有 date -d \"tomorrow\"，
# 所以先求出今天的 0 点，再把天数和时刻加上去。
# ⚠ 带前导 0 的值 (\"08\") 交给 ${d}(()) 会被当成八进制，一定要去掉。
# ⚠ 一天按 86400 秒相加，所以在有夏令时的地区，切换那天会差一小时。""",
        "zh-TW" to """# 把「N 天後的 HH:MM」換算成 epoch 秒。⚠ busybox 沒有 date -d \"tomorrow\"，
# 所以先求出今天的 0 點，再把天數和時刻加上去。
# ⚠ 帶前導 0 的值 (\"08\") 交給 ${d}(()) 會被當成八進位，一定要去掉。
# ⚠ 一天按 86400 秒相加，所以在有日光節約時間的地區，切換那天會差一小時。"""
    )
    val cFmtAt = t(
        en = """# The label shown in the list. ⚠ Keeping "tomorrow" would read wrong once the date rolls over.""",
        ja = """# 一覧に出す日時。⚠ 「明日」のまま覚えると日付が変わった後にズレて見えるので、実日付にする。""",
        "zh-CN" to """# 列表里显示的日期时间。⚠ 要是照原样记成「明天」，日期一变就看错了，所以存实际日期。""",
        "zh-TW" to """# 列表裡顯示的日期時間。⚠ 要是照原樣記成「明天」，日期一變就看錯了，所以存實際日期。"""
    )
    val mNoBody = t(en = "say what to remind you about", ja = "リマインドの本文を書いてください", "zh-CN" to "请写上要提醒什么", "zh-TW" to "請寫上要提醒什麼")
    val mNoAlarm = t(en = "could not schedule it", ja = "予約できませんでした", "zh-CN" to "没能预约成功", "zh-TW" to "沒能預約成功")
    val mNone = t(en = "nothing scheduled", ja = "予定はありません", "zh-CN" to "没有预定", "zh-TW" to "沒有預定")
    val mFired = t(en = " (fired)", ja = " (通知済)", "zh-CN" to " (已通知)", "zh-TW" to " (已通知)")
    val mRemoved = t(en = "removed:", ja = "消しました:", "zh-CN" to "已删除:", "zh-TW" to "已刪除:")
    val mNoSuchNum = t(
        en =         "no such entry (check remind.sh list):",
        ja =         "その番号はありません (remind.sh list で確認):",
        "zh-CN" to "没有这个编号 (用 remind.sh list 确认):",
        "zh-TW" to "沒有這個編號 (用 remind.sh list 確認):"
    )
    val mUsageDel = t(
        en = "usage: remind.sh del <n|all>",
        ja = "usage: remind.sh del <番号|all>",
        "zh-CN" to "usage: remind.sh del <编号|all>",
        "zh-TW" to "usage: remind.sh del <編號|all>"
    )
    val bDone = t(en = "Done", ja = "完了", "zh-CN" to "完成", "zh-TW" to "完成")
    // ⚠ 通知のボタンは 1 語で書く (空白があると引数が割れる)。
    val bDelete = t(en = "Delete", ja = "消す", "zh-CN" to "删除", "zh-TW" to "刪除")
    val mAskDel = t(en = "Remove which one?", ja = "どれを消す？", "zh-CN" to "要删哪一条？", "zh-TW" to "要刪哪一條？")
    val mAskDelH = t(en = "number / all", ja = "番号 / all", "zh-CN" to "编号 / all", "zh-TW" to "編號 / all")
    val mDeletedTitle = t(en = "🗑 Removed", ja = "🗑 消しました", "zh-CN" to "🗑 已删除", "zh-TW" to "🗑 已刪除")
    val cPeek = t(
        en = """# Show the list in a notification (from the "list" tile). **Keep the numbers** — they are what
# you point at to remove one. ⚠ Listing without a way to remove was the complaint; hence the button.""",
        ja = """# 一覧を通知で見せる (タイル「予定」から)。**番号を残す** — 消すときに指すものだから。
# ⚠ 一覧を出したのに消す手段が無く、set したら消せないと言われた (要望) ので「消す」を付ける。""",
        "zh-CN" to """# 用通知显示一览 (从「一览」磁贴)。**保留编号** — 删除的时候要靠它来指。
# ⚠ 之前被反映过「列出来了却没法删」，所以加了这个按钮。""",
        "zh-TW" to """# 用通知顯示一覽 (從「一覽」圖塊)。**保留編號** — 刪除的時候要靠它來指。
# ⚠ 之前被反映過「列出來了卻沒法刪」，所以加了這個按鈕。"""
    )
    val cAskDel = t(
        en = """# The "delete" button: ask for a number and hand it to [cmd_del] (the same path as the CLI).""",
        ja = """# 「消す」ボタン。番号を聞いて [cmd_del] へ渡すだけ (端末の remind.sh del と同じ経路)。""",
        "zh-CN" to """# 「删除」按钮。只是问一个编号再交给 [cmd_del] (和终端里的 remind.sh del 同一条路)。""",
        "zh-TW" to """# 「刪除」按鈕。只是問一個編號再交給 [cmd_del] (和終端機裡的 remind.sh del 同一條路)。"""
    )
    val bS1 = t(en = "+10min", ja = "10分後", "zh-CN" to "10分钟后", "zh-TW" to "10分鐘後")
    val bS2 = t(en = "+1h", ja = "1時間後", "zh-CN" to "1小时后", "zh-TW" to "1小時後")
    val mAgain = t(en = "- again in", ja = "にもう一度:", "zh-CN" to "再提醒一次:", "zh-TW" to "再提醒一次:")
    val mTitle = t(en = "⏰ Reminders", ja = "⏰ リマインド", "zh-CN" to "⏰ 提醒", "zh-TW" to "⏰ 提醒")
    val mAsk1 = t(en = "Remind you about what?", ja = "何をリマインド？", "zh-CN" to "要提醒什么？", "zh-TW" to "要提醒什麼？")
    val mAsk1H = t(en = "e.g. take pills", ja = "例: 薬を飲む", "zh-CN" to "例: 吃药", "zh-TW" to "例: 吃藥")
    val mAsk2 = t(en = "When?", ja = "いつ？", "zh-CN" to "什么时候？", "zh-TW" to "什麼時候？")
    val mAsk2H = t(
        en = "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
        ja = "30m / 18:30 / 明日 18:30 / 3日後 / 毎日 07:00",
        "zh-CN" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
        "zh-TW" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00"
    )
    val mAskAgain = t(en = "please enter it again", ja = "もう一度入力してください", "zh-CN" to "请再输入一次", "zh-TW" to "請再輸入一次")
    val mAskGiveUp = t(
        en = "Could not read it ${ASK_TRIES} times. You can also add it from the terminal: remind.sh 30m take pills",
        ja = "${ASK_TRIES} 回とも読めませんでした。端末からも登録できます: remind.sh 30m 薬を飲む",
        "zh-CN" to "${ASK_TRIES} 次都没读懂。也可以从终端登记: remind.sh 30m 吃药",
        "zh-TW" to "${ASK_TRIES} 次都沒讀懂。也可以從終端機登記: remind.sh 30m 吃藥"
    )
    val mOkTitle = t(en = "⏰ Reminder set", ja = "⏰ 登録しました", "zh-CN" to "⏰ 已登记", "zh-TW" to "⏰ 已登記")
    val mNgTitle = t(en = "⚠ Not set", ja = "⚠ 登録できませんでした", "zh-CN" to "⚠ 没能登记", "zh-TW" to "⚠ 沒能登記")
    val cHooks = t(
        en = """  # One hook for "a reminder fired", one for "a notification button was tapped". Just these
  # two, no matter how many reminders you add. Neither depends on the detection switches.""",
        ja = """  # 予定が鳴ったのを拾う 1 本と、通知ボタンの返事を拾う 1 本。**この 2 本だけ**で、
  # 予定を何件足しても増えない。どちらも「検知」の ON/OFF に関係なく働く。""",
        "zh-CN" to """  # 一条用来接「提醒响了」，一条用来接「按了通知上的按钮」。**就这两条**，
  # 不管你加多少条提醒都不会变多。两条都和检测开关的开关状态无关。""",
        "zh-TW" to """  # 一條用來接「提醒響了」，一條用來接「按了通知上的按鈕」。**就這兩條**，
  # 不管你加多少條提醒都不會變多。兩條都和偵測開關的開關狀態無關。"""
    )
    val cTiles = t(
        en = """  # Only fill empty slots and slots already ours (never overwrite someone else's).
  # A macro name with arguments works from 0.8.275 on (before that, write sh + full path).""",
        ja = """  # 空いている枠と、すでに自分が使っている枠だけに置く (他人の割り当ては触らない)。
  # 引数付きのマクロ名は 0.8.275 からそのまま書ける (それより前は sh + フルパスで書くこと)。""",
        "zh-CN" to """  # 只往空着的位和已经属于自己的位上放 (绝不覆盖别人的分配)。
  # 带参数的宏名从 0.8.275 起可以直接写 (在那之前要写 sh + 完整路径)。""",
        "zh-TW" to """  # 只往空著的位和已經屬於自己的位上放 (絕不覆寫別人的分配)。
  # 帶參數的巨集名從 0.8.275 起可以直接寫 (在那之前要寫 sh + 完整路徑)。"""
    )
    val mSetupHooks = t(en = "hooks registered:", ja = "受け口を登録しました:", "zh-CN" to "已登记接口:", "zh-TW" to "已登記介面:")
    val mSetupTiles = t(en = "tiles:", ja = "タイル:", "zh-CN" to "磁贴:", "zh-TW" to "圖塊:")
    val mPathHint = t(
        en = "Note: this tab cannot run remind.sh by name yet. Open a new tab, or run " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH here.",
        ja = "⚠ このタブでは remind.sh を名前で打てません。新しいタブを開くか、" +
            "このタブで export PATH=${d}HOME/.z2term/macros:${d}PATH を打ってください。",
        "zh-CN" to "⚠ 这个标签页还不能用名字直接敲 remind.sh。请开一个新标签页，" +
            "或者在这个标签页里敲 export PATH=${d}HOME/.z2term/macros:${d}PATH。",
        "zh-TW" to "⚠ 這個分頁還不能用名字直接敲 remind.sh。請開一個新分頁，" +
            "或者在這個分頁裡敲 export PATH=${d}HOME/.z2term/macros:${d}PATH。"
    )
    val cPathHint = t(
        en = """  # ⚠ The macro directory has only been on PATH since 0.8.287, so **tabs opened before that**
  # still carry the old one. Check whether the name resolves and say what to do if not
  # (otherwise `command not found` has no visible reason).""",
        ja = """  # ⚠ マクロ置き場が PATH に入るのは 0.8.287 から。**それ以前に開いたタブ**は古い PATH の
  # ままなので、名前で打てるか確かめて、駄目なら開き直しを案内する (黙っていると
  # `command not found` の理由が分からない)。""",
        "zh-CN" to """  # ⚠ 宏的目录进入 PATH 是从 0.8.287 开始的。**在那之前打开的标签页**
  # 还带着旧的 PATH，所以先确认名字能不能解析，不行就提示怎么办 (不说的话，
  # `command not found` 的原因根本看不出来)。""",
        "zh-TW" to """  # ⚠ 巨集的目錄進入 PATH 是從 0.8.287 開始的。**在那之前開啟的分頁**
  # 還帶著舊的 PATH，所以先確認名字能不能解析，不行就提示怎麼辦 (不說的話，
  # `command not found` 的原因根本看不出來)。"""
    )
    val mPlace = t(
        en = "Note: you still have to place the tiles yourself, from the quick-settings pencil/edit screen.",
        ja = "⚠ タイルはご自身でクイック設定パネルの鉛筆(編集)から並べてください。",
        "zh-CN" to "⚠ 磁贴请你自己从快捷设置面板的铅笔(编辑)里摆上去。",
        "zh-TW" to "⚠ 圖塊請你自己從快速設定面板的鉛筆(編輯)裡擺上去。"
    )
    val lRemind = t(en = "remind", ja = "リマインド", "zh-CN" to "提醒", "zh-TW" to "提醒")
    val lList = t(en = "list", ja = "予定", "zh-CN" to "一览", "zh-TW" to "一覽")
    val cSelf = t(
        en = "# Only react to our own notifications (our ids always start with r)",
        ja = "# 自分が出した通知だけ相手にする (id は必ず r で始まる)",
        "zh-CN" to "# 只搭理自己发出的通知 (id 一定以 r 开头)",
        "zh-TW" to "# 只搭理自己發出的通知 (id 一定以 r 開頭)"
    )
    val cKeepRepeat = t(
        en = "      # Do not delete a repeating one here: it should fire again tomorrow.",
        ja = "      # 繰り返しはここで消さない (明日もまた鳴ってほしいので)。単発だけ片付ける。",
        "zh-CN" to "      # 重复的不在这里删 (明天还要再响)。只收拾一次性的。",
        "zh-TW" to "      # 重複的不在這裡刪 (明天還要再響)。只收拾一次性的。"
    )
    val cAsk = t(
        en = """# The path used from the tile / a notification. ⚠ **Always answer with a notification**:
# nobody is looking at a terminal here, so failing to stderr reads as "I tapped it and
# nothing happened" (it did — the reason only reached the tile's run.log).
#   Unreadable -> say why and ask again, up to $ASK_TRIES times (the previous answer is kept)
#   Scheduled  -> show the plan and the text in a notification""",
        ja = """# タイル/通知から聞く経路。⚠ **結果は必ず通知で返す** — ここは画面を見ていない前提の
# 入口なので、エラーを標準エラーへ出して終わると「押したのに何も起きない」になる
# (実際そうなっていた。理由はタイルの run.log にしか残らなかった)。
#   読めなかったら → 何が駄目かを付けて $ASK_TRIES 回まで聞き直す (前の入力は返信欄に残す)
#   登録できたら   → 予定と本文を通知で見せる""",
        "zh-CN" to """# 从磁贴/通知过来的这条路。⚠ **结果一定要用通知回复** —
# 这是个默认没人在看屏幕的入口，把错误丢到标准错误就结束的话，就成了「按了却什么都没发生」
# (实际就是这样，原因只留在磁贴的 run.log 里)。
#   读不懂    -> 说清哪里不对，最多再问 $ASK_TRIES 次 (上次输入的内容会留在回复框里)
#   登记成功  -> 用通知把预定和正文显示出来""",
        "zh-TW" to """# 從圖塊/通知過來的這條路。⚠ **結果一定要用通知回覆** —
# 這是個預設沒人在看螢幕的入口，把錯誤丟到標準錯誤就結束的話，就成了「按了卻什麼都沒發生」
# (實際就是這樣，原因只留在圖塊的 run.log 裡)。
#   讀不懂    -> 說清哪裡不對，最多再問 $ASK_TRIES 次 (上次輸入的內容會留在回覆框裡)
#   登記成功  -> 用通知把預定和正文顯示出來"""
    )
    val cSnoozeCancel = t(
        en =         "drop the snooze alarm if it was snoozed before being done",
        ja =         "スヌーズ中に完了を押したときの予約を残さない",
        "zh-CN" to "小睡期间按了完成时，不要把小睡的预约留下",
        "zh-TW" to "小睡期間按了完成時，不要把小睡的預約留下"
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
  [ "${d}ok" = 1 ] || { z2-notify -h -n remind-ng "$mNgTitle" "$mAskGiveUp"; exit 1; }

  set -- ${d}w
  out=${d}(cmd_add "${d}@" "${d}body" 2>&1) || { z2-notify -h -n remind-ng "$mNgTitle" "${d}out"; exit 1; }
  z2-notify -h -n remind-ok "$mOkTitle" "${d}(echo "${d}out" | tr '\t' ' ')"
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
    val head = (t(
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
#   1) qrencode must be installed ... it does the encoding. Once per tab (distro):
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      If it is missing, the install command is printed and the script stops.
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
#   1) qrencode が入っていること … QR を作る本体。タブ (distro) ごとに 1 回入れる
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      入っていなければ、その場で導入コマンドを出して止まる。
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
""",
        "zh-CN" to """
#
# 用法:
#   qr.sh \"https://example.com\"       把字符串编成二维码，在这里画出来
#   qr.sh -f notes.txt                把文件的内容编成二维码
#   z2-clip get | qr.sh               把当前剪贴板的内容编成二维码
#   qr.sh -o ~/qr.png \"text\"          存成 PNG (画面上不显示)
#   qr.sh -t \"text\"                   用方块字符输出，而不是图片
#   qr.sh -s 24 \"text\"                指定大小 (以列数计。默认按画面宽度)
#
# z2-run: qr.sh \"https://example.com\"   (装好之后只写名字就行。跑一次就结束)
#
# # 前提条件 (不满足就跑不起来，或者看到的样子会不一样)
#
#   1) 必须装了 qrencode … 编码由它来做。每个标签页 (发行版) 装一次:
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      没有的话会当场打出安装命令并停下。
#
#   2) 图片 (默认) 只有在这个应用的标签页里才看得到 … 它是用 Kitty graphics 画的。
#      通过 ssh 连到别的机器，或者在显示不了图片的终端上，看到的会是一堆乱码。
#      那种场合请加 -t。
#
#   3) -t (方块) 取决于终端字体 … 行与行之间会留缝的字体，眼睛看得清，摄像头却读不出来。
#      要让摄像头扫的话，还是用默认的图片或者 -o 存 PNG 更稳。
#
#   4) 纵横比是假定出来的 … 见下面的「纵横比」。看着被压扁了就用 Z2_QR_ASPECT 调。
#
# 选项:
#   -f FILE   从文件读取输入 (省略则用参数，没有参数就读标准输入)
#   -o PNG    存成 PNG 而不是画出来。多张时会变成
#             qr-1.png / qr-2.png … 这样的连号
#   -t        用方块字符输出。给显示不了图片的终端和 ssh 用
#   -s N      画出来时的大小 (列数)。默认最多 34 列，并受画面宽度限制
#   -h        这段说明
#
# 关于长输入:
#   一张二维码能装的量是固定的 (字母数字最多 2953 字节; 要真能扫得动的话就是几百字节)。
#   超过 900 字节的输入会在换行处切分成好几张，编上 [1/3] 这样的号按顺序输出。
#   ⚠ 单独一行长到一张都装不下时，那一行根本编不出来。
#
# 纵横比:
#   终端不会告诉别人一个字符格有多大，所以这里假定字符的纵横比是 1:2 来把码画成正方形。
#   看着被压扁的话，用环境变量微调:
#     Z2_QR_ASPECT=0.45 qr.sh \"text\"     (越小越竖长。默认 0.5)
""",
        "zh-TW" to """
#
# 用法:
#   qr.sh \"https://example.com\"       把字串編成二維條碼，在這裡畫出來
#   qr.sh -f notes.txt                把檔案的內容編成二維條碼
#   z2-clip get | qr.sh               把當前剪貼簿的內容編成二維條碼
#   qr.sh -o ~/qr.png \"text\"          存成 PNG (畫面上不顯示)
#   qr.sh -t \"text\"                   用方塊字元輸出，而不是圖片
#   qr.sh -s 24 \"text\"                指定大小 (以列數計。預設按畫面寬度)
#
# z2-run: qr.sh \"https://example.com\"   (裝好之後只寫名字就行。跑一次就結束)
#
# # 前提條件 (不滿足就跑不起來，或者看到的樣子會不一樣)
#
#   1) 必須裝了 qrencode … 編碼由它來做。每個分頁 (發行版) 裝一次:
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      沒有的話會當場打出安裝指令並停下。
#
#   2) 圖片 (預設) 只有在這個應用程式的分頁裡才看得到 … 它是用 Kitty graphics 畫的。
#      透過 ssh 連到別的機器，或者在顯示不了圖片的終端機上，看到的會是一堆亂碼。
#      那種場合請加 -t。
#
#   3) -t (方塊) 取決於終端機字型 … 行與行之間會留縫的字型，眼睛看得清，攝像頭卻讀不出來。
#      要讓攝像頭掃的話，還是用預設的圖片或者 -o 存 PNG 更穩。
#
#   4) 縱橫比是假定出來的 … 見下面的「縱橫比」。看著被壓扁了就用 Z2_QR_ASPECT 調。
#
# 選項:
#   -f FILE   從檔案讀取輸入 (省略則用參數，沒有參數就讀標準輸入)
#   -o PNG    存成 PNG 而不是畫出來。多張時會變成
#             qr-1.png / qr-2.png … 這樣的連號
#   -t        用方塊字元輸出。給顯示不了圖片的終端機和 ssh 用
#   -s N      畫出來時的大小 (列數)。預設最多 34 列，並受畫面寬度限制
#   -h        這段說明
#
# 關於長輸入:
#   一張二維條碼能裝的量是固定的 (字母數字最多 2953 位元組; 要真能掃得動的話就是幾百位元組)。
#   超過 900 位元組的輸入會在換行處切分成好幾張，編上 [1/3] 這樣的號按順序輸出。
#   ⚠ 單獨一行長到一張都裝不下時，那一行根本編不出來。
#
# 縱橫比:
#   終端機不會告訴別人一個字元格有多大，所以這裡假定字元的縱橫比是 1:2 來把碼畫成正方形。
#   看著被壓扁的話，用環境變數微調:
#     Z2_QR_ASPECT=0.45 qr.sh \"text\"     (越小越豎長。預設 0.5)
"""
    )).trimStart('\n')
    val cMaxBytes = t(
        en = "max bytes per code (split beyond this)",
        ja = "1 枚に入れる上限 (これを超えたら分ける)",
        "zh-CN" to "一张里装的字节上限 (超过就分开)",
        "zh-TW" to "一張裡裝的位元組上限 (超過就分開)"
    )
    val cPngModule = t(
        en = "dots per module in a saved PNG",
        ja = "保存する PNG の 1 モジュールあたりのドット数",
        "zh-CN" to "保存的 PNG 里每个模块占几个点",
        "zh-TW" to "儲存的 PNG 裡每個模組佔幾個點"
    )
    val cTargetPx = t(en = "target pixel width when drawing", ja = "画面に出すときの狙いのドット幅", "zh-CN" to "画出来时想要的像素宽度", "zh-TW" to "畫出來時想要的像素寬度")
    val cMargin = t(
        en = "quiet zone around the code (modules; needed to scan)",
        ja = "QR の周りの余白 (モジュール数。読み取りに必要)",
        "zh-CN" to "二维码周围的静区 (模块数。扫描时需要)",
        "zh-TW" to "二維條碼周圍的靜區 (模組數。掃描時需要)"
    )
    val cUsageFn = t(
        en = "Print the leading comment block as the help text (no fixed line count).\n" +
            "# Blank lines do not stop it (NF decides), so blank-separated sections stay intact.",
        ja = "先頭のコメントブロックをそのまま説明として出す (行数を固定しない)。\n" +
            "# 空行では止めない (NF で判定する) — 説明を空行で区切っても途中で切れないように。",
        "zh-CN" to "把开头的注释块原样当作说明输出 (不固定行数)。\n" +
            "# 空行不会让它停下 (由 NF 判断)，所以用空行分段的说明也不会被截断。",
        "zh-TW" to "把開頭的註解塊原樣當作說明輸出 (不固定行數)。\n" +
            "# 空行不會讓它停下 (由 NF 判斷)，所以用空行分段的說明也不會被截斷。"
    )
    val cNeedEncoder = t(
        en = "Requirement 1: qrencode. If it is missing, print how to install it here and stop.",
        ja = "前提条件 1: qrencode。無ければ、このタブでの入れ方を出して止まる。",
        "zh-CN" to "前提条件 1: qrencode。没有的话，打出在这个标签页里的安装方法并停下。",
        "zh-TW" to "前提條件 1: qrencode。沒有的話，打出在這個分頁裡的安裝方法並停下。"
    )
    val mMissing = t(
        en = "qr.sh: missing requirement - qrencode is not installed",
        ja = "qr.sh: 前提条件が足りない — qrencode が入っていない",
        "zh-CN" to "qr.sh: 前提条件不满足 — 没有安装 qrencode",
        "zh-TW" to "qr.sh: 前提條件不滿足 — 沒有安裝 qrencode"
    )
    val mInstallOnce = t(
        en = "  Install it once in this tab:",
        ja = "  このタブで 1 回だけ入れてください:",
        "zh-CN" to "  请在这个标签页里装一次:",
        "zh-TW" to "  請在這個分頁裡裝一次:"
    )
    // パッケージマネージャが見つからなかったときの控え (distro ごとに名前が違うので並べる)。
    val mAnyPm = t(
        en = "    Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode",
        ja = "    Arch: pacman -S qrencode / Ubuntu・Kali: apt install qrencode",
        "zh-CN" to "    Arch: pacman -S qrencode / Ubuntu、Kali: apt install qrencode",
        "zh-TW" to "    Arch: pacman -S qrencode / Ubuntu、Kali: apt install qrencode"
    )
    val mNoTmp = t(en = "cannot create a work directory", ja = "作業場所を作れない", "zh-CN" to "建不了工作目录", "zh-TW" to "建不了工作目錄")
    val mUnreadable = t(en = "cannot read:", ja = "読めない:", "zh-CN" to "读不了:", "zh-TW" to "讀不了:")
    val mEmpty = t(en = "empty input", ja = "入力が空", "zh-CN" to "输入是空的", "zh-TW" to "輸入是空的")
    val mSizeNum = t(en = "-s takes a number", ja = "-s は数字で", "zh-CN" to "-s 要跟数字", "zh-TW" to "-s 要跟數字")
    val mUsageHint = t(en = "see qr.sh -h", ja = "使い方は qr.sh -h", "zh-CN" to "用法见 qr.sh -h", "zh-TW" to "用法見 qr.sh -h")
    val mPieceFail = t(
        en = "qr.sh: cannot build code %d: %s",
        ja = "qr.sh: %d 枚目を作れない: %s",
        "zh-CN" to "qr.sh: 做不出第 %d 张: %s",
        "zh-TW" to "qr.sh: 做不出第 %d 張: %s"
    )
    val cCollect = t(en = "---- collect the input ----", ja = "---- 入力を集める ----", "zh-CN" to "---- 收集输入 ----", "zh-TW" to "---- 收集輸入 ----")
    val cSplit = t(
        en = "---- split at line breaks when too long ----",
        ja = "---- 長ければ行の区切りで分ける ----",
        "zh-CN" to "---- 太长就在换行处切分 ----",
        "zh-TW" to "---- 太長就在換行處切分 ----"
    )
    val cSplit2 = t(
        en = "Move to the next piece just before the running total passes MAX_BYTES. Lines are\n# never cut in the middle, so multi-byte text survives.",
        ja = "累積バイト数が MAX_BYTES を超える手前で次のピースへ送る。行の途中では切らない\n# ので、日本語が混じっていても壊れない。",
        "zh-CN" to "在累计字节数超过 MAX_BYTES 之前就换到下一片。绝不在行的中间切，\n# 所以多字节的文字也不会被弄坏。",
        "zh-TW" to "在累計位元組數超過 MAX_BYTES 之前就換到下一片。絕不在行的中間切，\n# 所以多位元組的文字也不會被弄壞。"
    )
    val cTrim = t(
        en = "Drop the trailing newline (never encode a newline the input did not have).",
        ja = "末尾の改行を落とす (元の入力に無い改行を QR に混ぜないため)。",
        "zh-CN" to "去掉末尾的换行 (不把原输入里没有的换行编进二维码)。",
        "zh-TW" to "去掉末尾的換行 (不把原輸入裡沒有的換行編進二維條碼)。"
    )
    val cSize = t(
        en = "---- pick the display size from the terminal width ----",
        ja = "---- 端末の幅から表示サイズを決める ----",
        "zh-CN" to "---- 根据终端宽度决定显示大小 ----",
        "zh-TW" to "---- 根據終端機寬度決定顯示大小 ----"
    )
    val cPngWidth = t(
        en = "Return the pixel width of a PNG (bytes 16-19, the IHDR).",
        ja = "PNG の横ドット数を返す (IHDR の 16〜19 バイト目)。",
        "zh-CN" to "返回 PNG 的像素宽度 (IHDR 的第 16〜19 字节)。",
        "zh-TW" to "返回 PNG 的像素寬度 (IHDR 的第 16〜19 位元組)。"
    )
    val cModulePx = t(
        en = "Work back from the target pixel width to the dots per module.",
        ja = "1 モジュールを何ドットで描くかを、狙いのドット幅から逆算する。",
        "zh-CN" to "从想要的像素宽度倒推出每个模块画几个点。",
        "zh-TW" to "從想要的像素寬度倒推出每個模組畫幾個點。"
    )
    val cInline = t(
        en = "Draw a PNG in place with the Kitty graphics protocol.\n# c/r pin down how many cells it takes, then print that many newlines to move the cursor below it.",
        ja = "Kitty graphics protocol で PNG をその場に描く。\n# c/r を指定して占有セル数を確定させ、そのぶん改行してカーソルを絵の下へ運ぶ。",
        "zh-CN" to "用 Kitty graphics protocol 就地画出 PNG。\n# 指定 c/r 把占用的格数定下来，再输出相应的换行，把光标送到图的下方。",
        "zh-TW" to "用 Kitty graphics protocol 就地畫出 PNG。\n# 指定 c/r 把佔用的格數定下來，再輸出相應的換行，把游標送到圖的下方。"
    )
    val cEmit = t(en = "---- emit ----", ja = "---- 出す ----", "zh-CN" to "---- 输出 ----", "zh-TW" to "---- 輸出 ----")
    val cAnsi = t(
        en = "ANSIUTF8 emits its own colours, so light/dark comes out right whatever the theme is.",
        ja = "ANSIUTF8 は色を付けて出すので、端末の配色に関係なく明暗が正しく出る。",
        "zh-CN" to "ANSIUTF8 会自己带上颜色，所以不管终端配色如何，明暗都能正确显示。",
        "zh-TW" to "ANSIUTF8 會自己帶上顏色，所以不管終端機配色如何，明暗都能正確顯示。"
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
if ! command -v qrencode >/dev/null 2>&1; then
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

# $cInline
show_inline() {
    base64 < "${d}1" | tr -d '\n' | fold -w 4096 > "${d}TMP/chunks"
    n=${d}(awk 'END { print NR }' "${d}TMP/chunks")
    i=0
    while IFS= read -r chunk || [ -n "${d}chunk" ]; do
        i=${d}((i + 1))
        if [ "${d}i" -lt "${d}n" ]; then m=1; else m=0; fi
        if [ "${d}i" -eq 1 ]; then
            printf '\033_Ga=T,f=100,c=%s,r=%s,q=2,m=%s;%s\033\\' \
                "${d}show_cols" "${d}show_rows" "${d}m" "${d}chunk"
        else
            printf '\033_Gm=%s;%s\033\\' "${d}m" "${d}chunk"
        fi
    done < "${d}TMP/chunks"
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
        if qrencode -s "${d}PNG_MODULE_PX" -m "${d}MARGIN" -o "${d}dest" -r "${d}p" 2>"${d}TMP/err"; then
            printf '%s\n' "${d}dest"
        else
            printf '$mPieceFail\n' "${d}i" "${d}(cat "${d}TMP/err")" >&2
            failed=1
        fi
    elif [ "${d}astext" -eq 1 ]; then
        # $cAnsi
        qrencode -t ANSIUTF8 -m "${d}MARGIN" -r "${d}p" 2>"${d}TMP/err" || {
            printf '$mPieceFail\n' "${d}i" "${d}(cat "${d}TMP/err")" >&2
            failed=1
        }
    else
        s=${d}(module_px_for "${d}p") || s=${d}PNG_MODULE_PX
        if qrencode -s "${d}s" -m "${d}MARGIN" -o "${d}TMP/out-${d}i.png" -r "${d}p" 2>"${d}TMP/err"; then
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
