package com.zerotoship.z2term.proot

/**
 * `z2-*` CLI が**端末に出す文言**（先頭のヘルプコメント・usage・メッセージ）の日英。
 *
 * **なぜ分けたか**: アプリの画面は `values/` と `values-ja/` で日英そろっているのに、
 * 端末側の CLI だけが日本語ベタ書きで、英語話者には使えない状態だった（GitHub 直配布なので
 * README を読むのは英語話者の方が多い）。`z2help` は 1 本だけ `lang` で出し分けていたので、
 * その方式を全体へ広げる。
 *
 * **ロジックは 1 つのまま**にするのが要点。スクリプト全体を 2 セット持つと、片方だけ直して
 * 挙動がズレる（そして端末でしか気付けない）。ここで持つのは**文言だけ**で、
 * [z2ApiScripts] 側は言語に関係なく同じ制御フローを組み立てる。
 *
 * ヘルプは行頭 `#` ・末尾改行つきの**完成形**で持つ（`trimMargin` の外で連結するため、
 * マージン `|` の剥がし漏れが起きない）。[d] はシェルの `$`。
 */
internal class Z2ApiMsg(private val en: Boolean, private val d: String) {

    // --- z2-notify ---

    val notifyHelp: String = if (en) """
        |# z2-notify [-h] [-n NAME] [-b LABEL]... "title" "text"  /  z2-notify [-h] "text"
        |#   -h / --high / --banner : show it as a banner (heads-up) at the top of the screen
        |#   -b <label>             : add a reply button (up to 3). Pressing one appends
        |#                            notify_action to ~/.z2term/events.jsonl
        |#                            ({"event":"notify_action","name":NAME,"action":LABEL})
        |#   -n <name>              : an identifier for this notification (to tell replies apart)
    """.trimMargin() else """
        |# z2-notify [-h] [-n 名前] [-b ラベル]... "タイトル" "本文"  /  z2-notify [-h] "本文"
        |#   -h / --high / --banner : 画面上部にバナー(ヘッドアップ)表示する
        |#   -b <ラベル>            : 返事のボタンを付ける (最大 3 つ)。押すと
        |#                            ~/.z2term/events.jsonl に notify_action が 1 行増える
        |#                            ({"event":"notify_action","name":名前,"action":ラベル})
        |#   -n <名前>              : その通知の識別名 (どの問いかけへの返事か区別する用)
    """.trimMargin()

    val notifyUsage: String =
        if (en) "usage: z2-notify [-h] [-n name] [-b label]... <title> [text]"
        else "usage: z2-notify [-h] [-n 名前] [-b ラベル]... <タイトル> [本文]"

    // --- 単機能のもの (ヘルプ 1〜2 行) ---

    val clipHelp: String = if (en) """
        |# z2-clip get        … print the clipboard to stdout
        |# z2-clip set [text] … put text (or stdin when omitted) on the clipboard
    """.trimMargin() else """
        |# z2-clip get        … クリップボードを標準出力へ
        |# z2-clip set [text] … text (無ければ標準入力) をクリップボードへ
    """.trimMargin()

    val batteryHelp: String =
        if (en) "# Print level / charging state as JSON ({\"level\":N,\"charging\":bool})."
        else "# 残量/充電状態を JSON ({\"level\":N,\"charging\":bool}) で出力。"

    val vibrateHelp: String =
        if (en) "# z2-vibrate [ms]  (default 200ms)"
        else "# z2-vibrate [ms]  (既定 200ms)"

    val sayHelp: String =
        if (en) "# z2-say <text>       … speak with the device TTS (reads stdin when no argument)"
        else "# z2-say <text>       … 端末標準の TTS で読み上げ (引数無しなら標準入力を読む)"

    val torchHelp: String =
        if (en) "# z2-torch on|off|toggle  (default toggle). Prints the resulting state (on/off)."
        else "# z2-torch on|off|toggle  (既定 toggle)。結果の点灯状態 (on/off) を出力。"

    val mediaHelp: String =
        if (en) "# z2-media play|pause|playpause|next|previous|stop  (default playpause)"
        else "# z2-media play|pause|playpause|next|previous|stop  (既定 playpause)"

    val volumeHelp: String =
        if (en) "# z2-volume up|down|mute|unmute|N|N%   Media volume. Prints the resulting current/max."
        else "# z2-volume up|down|mute|unmute|N|N%   メディア音量を操作。結果の current/max を出力。"

    val sensorHelp: String =
        if (en) "# z2-sensor light|accel|proximity  (default light). Reads one sample and returns JSON."
        else "# z2-sensor light|accel|proximity  (既定 light)。センサーを 1 回読んで JSON で返す。"

    val intentHelp: String = if (en) """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Fire any Android Intent (startActivity by default). A leading non-flag argument is the ACTION.
    """.trimMargin() else """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# 任意の Android Intent を発火 (既定は startActivity)。先頭の非フラグ引数は ACTION。
    """.trimMargin()

    val stateHelp: String = if (en) """
        |# z2-state            … the device's current state, all of it, as JSON
        |# z2-state <key>      … just that value, raw (drops straight into a test)
        |# keys: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# e.g. [ "${d}(z2-state charging)" = "true" ] && echo charging
    """.trimMargin() else """
        |# z2-state            … 今の端末の状態をまとめて JSON で返す
        |# z2-state <キー>     … その値だけを生で返す (条件式にそのまま書ける)
        |# キー: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(℃)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# 例: [ "${d}(z2-state charging)" = "true" ] && echo 充電中
    """.trimMargin()

    // --- z2-ask ---

    val askHelp: String = if (en) """
        |# z2-ask [-t SEC] [-H HINT] [-d DEFAULT] <question>
        |#   Ask the person a question and print their answer on stdout.
        |#   The question arrives as a notification with a **reply field**, so it can be
        |#   answered from the shade without opening the app (a macro running in the
        |#   background can ask too).
        |#   -t <sec>   how long to wait for an answer (default 300 = 5 min)
        |#   -H <hint>  label shown in the reply field
        |#   -d <text>  suggested answer (shown in the notification)
        |# Dismissing the notification without answering, or running out of time, exits
        |# non-zero and prints nothing — so you can write "or give up":
        |#   name=${d}(z2-ask "Branch name?") || exit 1
        |# Compare with z2-notify -b <label>, which only offers the choices you prepared.
    """.trimMargin() else """
        |# z2-ask [-t 秒] [-H ヒント] [-d 既定] <質問>
        |#   人に質問して、答えを標準出力へ返す。
        |#   質問は**返信欄つきの通知**で届くので、アプリを開かずシェードのまま答えられる
        |#   (裏で走っているマクロからも聞ける)。
        |#   -t <秒>     答えを待つ時間 (既定 300 = 5 分)
        |#   -H <ヒント> 返信欄に出す見出し
        |#   -d <文字列> 答えの候補 (通知に出す)
        |# 答えずに通知を消したとき・時間切れのときは**非ゼロ終了**で何も出さないので、
        |# 「答えなければ諦める」がそのまま書ける:
        |#   name=${d}(z2-ask "ブランチ名は?") || exit 1
        |# 用意した選択肢から選ばせるだけなら z2-notify -b <ラベル> の方が向いている。
    """.trimMargin()

    val askUsage: String =
        if (en) "usage: z2-ask [-t sec] [-H hint] [-d default] <question>"
        else "usage: z2-ask [-t 秒] [-H ヒント] [-d 既定] <質問>"

    // --- z2-screen ---

    val screenHelp: String = if (en) """
        |# z2-screen keepon <N|Ns|Nm|Nh> … stop the screen from turning off by itself, for that long
        |# z2-screen keepon off          … put it back now, without waiting for the deadline
        |# z2-screen status              … current state as JSON
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# This changes the OS-wide "screen timeout" setting, so it holds even when the app is
        |# in the background. It is NOT the toolbar's screen lock, which only lasts while the
        |# app is on screen — that one is left untouched.
        |# The original value is saved and always written back at the deadline (even if the app
        |# is killed or the device reboots). Max 24h in one go.
        |# Needs "modify system settings" (Settings > screen timeout > allow).
        |# e.g. z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin() else """
        |# z2-screen keepon <N|Ns|Nm|Nh> … その時間だけ、画面が自分で消えないようにする
        |# z2-screen keepon off          … 期限を待たずに今すぐ元へ戻す
        |# z2-screen status              … 今の状態を JSON で
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# これは OS 全体の「画面消灯までの時間」を変えるので、アプリを畳んでいても効く。
        |# ツールバーの🔅 (アプリを開いている間だけ消えない) とは別物で、そちらは触らない。
        |# 元の値は保存され、期限が来たら必ず書き戻す (アプリが落ちても・再起動しても)。
        |# 一度に掛けられるのは 24h まで。
        |# 「システム設定の変更」の許可が要ります (設定 › 画面の自動消灯 › 許可)。
        |# 例: z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    val screenUsage: String =
        if (en) "usage: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"
        else "usage: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    // --- z2-tile ---

    val tileHelp: String = if (en) """
        |# z2-tile set <1-4> <macro.sh | command...> [--off <command...>] [-l <label>]
        |#                             … put something on quick-settings tile 1-4
        |# z2-tile list                … all four slots as TSV (slot / label / command; '-' = empty)
        |# z2-tile clear <1-4|all>     … empty a slot
        |# What you assign is either **the file name of a macro** in ~/.z2term/macros/ or
        |# **a command** to run as typed — whichever it is, is worked out from the name.
        |# Tap the tile to run it, tap again to stop (same deal as the widget's buttons).
        |# The tile looks "on" while it runs (the colour is the OS accent, not ours).
        |# With --off you get **two commands**: tapping alternates between them and the tile stays
        |# "on"-looking while it is on. Use it where turning off is its own command (z2-torch on/off).
        |# ⚠ That on/off is only what the app remembers — running z2-torch off in the terminal
        |# instead leaves the tile showing "on". (z2-screen keepon is the exception: the app
        |# holds that state for real, so its tile follows the terminal.)
        |# The command runs with Z2_TILE=<slot> (and Z2_TILE_MACRO for a macro) in the environment.
        |# ⚠ You still have to place the tile yourself, from the pencil/edit screen of the quick
        |# settings panel — Android does not let an app put its own tiles there. There are exactly
        |# 4 slots: the number is fixed in the manifest and cannot grow at runtime.
        |# e.g. z2-tile set 1 backup.sh
        |#      z2-tile set 2 'z2-screen keepon 1h' -l "no sleep"
        |#      z2-tile set 3 z2-torch on --off z2-torch off -l torch
    """.trimMargin() else """
        |# z2-tile set <1-4> <マクロ.sh | コマンド...> [--off <コマンド...>] [-l <表示名>]
        |#                             … クイック設定タイル 1〜4 に割り当てる
        |# z2-tile list                … 4 枠すべてを TSV で (枠 / 表示名 / コマンド。'-' は空き)
        |# z2-tile clear <1-4|all>     … 割り当てを消す
        |# 割り当てるのは ~/.z2term/macros/ にある**マクロのファイル名**か、そのまま走らせる
        |# **コマンド**のどちらでもよい (名前を見て自動で判別します)。
        |# タイルを押すと実行、もう一度押すと停止 (ウィジェットのボタンと同じ約束)。
        |# 実行中はタイルが ON の見た目になります (色は OS のもの)。
        |# --off を付けると**入 / 切の 2 コマンド**になり、押すたびに交互に走ります
        |# (入の間タイルは ON の見た目)。切るのが別コマンドのもの (z2-torch on / off) 向けです。
        |# ⚠ この ON / 切は**アプリが覚えているだけ**です。端末から直接 z2-torch off を打つと
        |# タイルは入のままになります (z2-screen keepon だけは例外で、アプリが実態を
        |# 持っているので端末から切ってもタイルが揃います)。
        |# 実行時、環境変数 Z2_TILE に枠番号 (マクロなら Z2_TILE_MACRO も) が入ります。
        |# ⚠ タイルを**並べるのはご自身**で、クイック設定パネルの鉛筆(編集)から追加します
        |# — アプリが勝手に置くことは Android が禁じています。枠はちょうど 4 つで、
        |# manifest で決め打ちのため実行中に増やせません。
        |# 例: z2-tile set 1 backup.sh
        |#     z2-tile set 2 'z2-screen keepon 1h' -l 消灯しない
        |#     z2-tile set 3 z2-torch on --off z2-torch off -l ライト
    """.trimMargin()

    val tileUsage: String =
        if (en) "usage: z2-tile set <1-4> <macro.sh|command...> [--off <command...>] [-l label] | " +
            "list | clear <1-4|all>"
        else "usage: z2-tile set <1-4> <マクロ.sh|コマンド...> [--off <コマンド...>] [-l 表示名] | " +
            "list | clear <1-4|all>"

    // --- z2-noti ---

    val notiHelp: String = if (en) """
        |# z2-noti list  … the notifications currently on screen, as TSV
        |#                 (key / package / app name / title / body)
        |# Reading only. There is deliberately no way to press or dismiss a notification:
        |# that would also press other apps' pay and send buttons.
        |# Needs notification access (Settings > resident servers & automation).
        |# See also: z2-when notify:otp / notify:pkg=<part> / notify:contains=<part>
    """.trimMargin() else """
        |# z2-noti list  … いま出ている通知を TSV で表示
        |#                 (key / パッケージ / アプリ名 / タイトル / 本文)
        |# 読むだけです。通知のボタンを「押す」「消す」は意図的に用意していません
        |# (他アプリの決済・送信ボタンまで押せてしまうため)。
        |# 通知アクセスの許可が要ります (設定 › 常駐サーバー・自動化 › 通知検知)。
        |# 併せて: z2-when notify:otp / notify:pkg=<部分> / notify:contains=<部分>
    """.trimMargin()

    val notiUsage: String =
        if (en) "usage: z2-noti list" else "usage: z2-noti list"

    // --- z2-alarm ---

    val alarmHelp: String = if (en) """
        |# z2-alarm at HH:MM [name]     … once at the next HH:MM (tomorrow if already past)
        |# z2-alarm daily HH:MM [name]  … every day at HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [name] … once, N seconds/minutes/hours from now
        |# z2-alarm list                … list what is scheduled (JSON)
        |# z2-alarm cancel <id|name|all> … cancel
        |# When it fires, a line {"event":"alarm","name":…} is appended to ~/.z2term/events.jsonl.
        |# It wakes the device even in Doze, but power saving can delay it by a few minutes.
    """.trimMargin() else """
        |# z2-alarm at HH:MM [名前]     … 次の HH:MM に 1 回 (今日を過ぎていれば明日)
        |# z2-alarm daily HH:MM [名前]  … 毎日 HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [名前] … N 秒/分/時間後に 1 回
        |# z2-alarm list                … 予約一覧 (JSON)
        |# z2-alarm cancel <id|名前|all> … 取り消し
        |# 発火すると ~/.z2term/events.jsonl に {"event":"alarm","name":…} が 1 行増える。
        |# Doze 中でも起きるが、省電力のため発火が数分ずれることがある。
    """.trimMargin()

    val alarmNoDate: String =
        if (en) "z2-alarm: no usable date command" else "z2-alarm: date が使えません"

    // --- z2-session ---

    val sessionHelp: String = if (en) """
        |# z2-session list                     … list tabs (index / id / kind / mark / name, TSV)
        |#   marks: * = the tab on screen / ! = something is running / ? = not started yet / - = other
        |# z2-session new [name]               … open one terminal tab (returns index and id)
        |# z2-session send <tab> <text>...     … type text into that tab (does not run it)
        |# z2-session send <tab> <text> --enter … type it, then run it
        |# z2-session capture [tab] [--all]    … take that tab's screen (--all includes scrollback)
        |# z2-session close <tab>              … close that tab (never the last one)
        |#
        |# <tab> can be the index from list, an id, or a tab name. '.' or omitted = the tab on screen.
        |# e.g. n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin() else """
        |# z2-session list                     … タブ一覧 (番号 / id / 種別 / 印 / 名前 の TSV)
        |#   印: * = 表示中のタブ / ! = 何か動作中 / ? = まだ起動していない / - = それ以外
        |# z2-session new [名前]               … 端末タブを 1 枚開く (番号と id を返す)
        |# z2-session send <先> <文字列>...    … そのタブに文字を入れる (実行はしない)
        |# z2-session send <先> <文字列> --enter … 入れてから実行する
        |# z2-session capture [先] [--all]     … そのタブの画面を取り出す (--all は遡れる分も)
        |# z2-session close <先>               … そのタブを閉じる (最後の 1 枚は閉じない)
        |#
        |# <先> は list の番号 / id / タブ名 のどれでもよい。'.' か省略で今表示しているタブ。
        |# 例: n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    val sessionUsage: String =
        if (en) "usage: z2-session list | new [name] | send <tab> <text>... [--enter] | capture [tab] [--all] | close <tab>"
        else "usage: z2-session list | new [名前] | send <先> <文字列>... [--enter] | capture [先] [--all] | close <先>"

    // --- z2-when ---

    val whenHelp: String = if (en) """
        |# z2-when <trigger> run <cmd...>        … register a rule
        |#   triggers: charge:start | charge:stop  (needs detection ON)
        |#            battery:below=N | battery:above=N  (needs detection ON)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='min hour dom month dow'  (dow 0-7 / 0,7=Sunday. Quote it: it has spaces)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<name>  (needs detection ON)
        |#            net:online | net:offline  … a usable connection appeared / went away
        |#            net:wifi | net:mobile | net:ethernet  … the link in use switched to that
        |#                                        (needs detection ON; counts mobile data, not just Wi-Fi)
        |#            share:any | share:text | share:file | share:contains=<part> | share:ext=<ext>
        |#                                        … something was shared to z2term from another app
        |#            boot                        … the device finished starting up (works with detection OFF)
        |#            sms:any | sms:from=<substr> | sms:contains=<substr> | sms:otp  (needs RECEIVE_SMS)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (detection ON)
        |#            file:new=<dir>[,ext=<ext>]  … a new file landed in that folder (needs detection ON)
        |#            notify:any | notify:otp | notify:pkg=<part> | notify:title=<part> | notify:contains=<part>
        |#                                        … a notification arrived (needs notification access)
        |#            event:<name> | event:<prefix>* | event:*  … any device event, by name
        |#              (z2-when events lists them; same names as in events.jsonl)
        |# Filters (any trigger, right after it — before run):
        |#   if=<cond>[,<cond>...]   … only when the device is in that state (AND; ! negates)
        |#                             keys are the ones z2-state prints: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             e.g. if=wifi,!screen / if=ssid=Home / if=level<30
        |#   cooldown=30m            … do not run again within that time (10s / 30m / 2h)
        |#   between=22:00-07:00     … only inside that window (wraps past midnight)
        |#   days=mon-fri            … only on those days (names or cron numbers 0-7, 0/7 = Sunday)
        |# Skipped runs are recorded too — z2-when fired shows skip:if / skip:between / skip:days
        |# / skip:cooldown, so a rule that never runs can be explained. The ▶ button in the app
        |# ignores every filter (it is there to try the rule out).
        |# z2-when events                        … list the names usable with event:
        |# z2-when pause / resume                … stop / resume automatic runs (rules are kept)
        |# z2-when fired [n]                     … recent fires (time / id / trigger / run|paused)
        |# z2-when list                          … registered rules (id / on|off / trigger / -> / cmd, TSV)
        |# z2-when remove <id|all>  (rm works)   … delete
        |# z2-when on <id> / off <id>            … enable / disable
        |# z2-when log <id>                      … that rule's run log (tail)
        |# On fire the command runs on the selected distro with Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) in the environment.
        |# For net: you get Z2_WHEN_NET (the link now) and Z2_WHEN_NET_PREV (the one before).
        |# For share: you get Z2_WHEN_SHARE (the text, or the paths of the files taken in)
        |# and Z2_WHEN_SHARE_KIND (text|file). The share is still put on the input line as before.
        |# For file: you get Z2_WHEN_FILE (full path) and Z2_WHEN_DIR.
        |# For notify: you get Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT (and Z2_WHEN_OTP for notify:otp).
        |# For event: you also get Z2_WHEN_EVENT (the event name); alarm / notify_action add
        |# Z2_WHEN_EVENT_NAME (the identifier you armed it with) and Z2_WHEN_ACTION (button pressed).
        |# The same rule will not fire twice within 10 seconds (events like screen_on come often).
        |# e.g. z2-when charge:start run ~/.z2term/macros/backup.sh
        |#      z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#      z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#      z2-when 'event:ringer_*' run 'z2-toast "ringer: ${d}Z2_WHEN_EVENT"'
        |#      z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#      z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#      z2-when boot run 'sshd --lan'
        |#      z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
    """.trimMargin() else """
        |# z2-when <トリガー> run <コマンド...>   … ルールを登録
        |#   トリガー: charge:start | charge:stop  (検知 ON が前提)
        |#            battery:below=N | battery:above=N  (検知 ON が前提)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='分 時 日 月 曜日'  (曜日 0-7 / 0,7=日曜。空白を含むので要クォート)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<名前>  (検知 ON が前提)
        |#            net:online | net:offline  … 通信できる回線ができた / 無くなった
        |#            net:wifi | net:mobile | net:ethernet  … 使う回線がそれへ切り替わった
        |#                                        (検知 ON が前提。Wi-Fi だけでなくモバイル回線も見る)
        |#            share:any | share:text | share:file | share:contains=<部分> | share:ext=<拡張子>
        |#                                        … 他アプリの共有シートから z2term へ送られたとき
        |#            boot                        … 端末の起動が終わったとき (検知 OFF でも動く)
        |#            sms:any | sms:from=<部分> | sms:contains=<部分> | sms:otp  (RECEIVE_SMS 許可が前提)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (検知 ON が前提)
        |#            file:new=<フォルダ>[,ext=<拡張子>]  … そのフォルダに新しいファイルが来たとき (検知 ON が前提)
        |#            notify:any | notify:otp | notify:pkg=<部分> | notify:title=<部分> | notify:contains=<部分>
        |#                                        … 通知が届いたとき (通知アクセスの許可が前提)
        |#            event:<名前> | event:<接頭辞>* | event:*  … 端末イベントを名前で拾う
        |#              (名前は z2-when events で一覧。events.jsonl に出るものと同じ)
        |# 絞り込み (どのトリガーでも使える。トリガーの直後・run より前に置く):
        |#   if=<条件>[,<条件>...]   … 端末がその状態のときだけ実行 (カンマは AND。頭の ! で否定)
        |#                             使えるキーは z2-state が出すもの: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             例: if=wifi,!screen / if=ssid=Home / if=level<30
        |#   cooldown=30m            … 前回の実行からこの時間は再実行しない (10s / 30m / 2h)
        |#   between=22:00-07:00     … この時間帯だけ実行 (日跨ぎ可)
        |#   days=mon-fri            … この曜日だけ実行 (曜日名か cron と同じ数字 0-7 / 0,7=日曜)
        |# 弾いたことも記録する — z2-when fired に skip:if / skip:between / skip:days / skip:cooldown
        |# として出るので、「動かない理由」が分かる。アプリ画面の ▶ は絞り込みを無視する
        |# (試すためのボタンなので)。
        |# z2-when events                        … event: で使えるイベント名の一覧
        |# z2-when pause / resume                … 自動実行を一時停止 / 再開 (ルールは消えない)
        |# z2-when fired [n]                     … 直近の発火 (時刻 / id / トリガー / run|paused)
        |# z2-when list                          … 登録一覧 (id / on|off / トリガー / -> / コマンド の TSV)
        |# z2-when remove <id|all>  (rm でも可)  … 削除
        |# z2-when on <id> / off <id>            … 有効 / 無効
        |# z2-when log <id>                      … そのルールの実行ログ (末尾)
        |# 発火時、コマンドは選択中の distro で実行され、環境変数 Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) が入る。
        |# net: のときは Z2_WHEN_NET (今の回線) と Z2_WHEN_NET_PREV (直前の回線) が入る。
        |# share: のときは Z2_WHEN_SHARE (テキストそのもの、またはファイルの取り込み先パス) と
        |# Z2_WHEN_SHARE_KIND (text|file) が入る。共有されたものは今までどおり入力行にも入る。
        |# file: のときは Z2_WHEN_FILE (フルパス) と Z2_WHEN_DIR が入る。
        |# notify: のときは Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT (notify:otp なら Z2_WHEN_OTP も)。
        |# event: のときは Z2_WHEN_EVENT (イベント名) が入る。alarm / notify_action では
        |# Z2_WHEN_EVENT_NAME (仕掛けたときの識別名) と Z2_WHEN_ACTION (押したボタン) も入る。
        |# 同じルールは 10 秒以内に続けて発火しない (screen_on のような数の多いイベント対策)。
        |# 例: z2-when charge:start run ~/.z2term/macros/backup.sh
        |#     z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#     z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#     z2-when 'event:ringer_*' run 'z2-toast "マナーモード: ${d}Z2_WHEN_EVENT"'
        |#     z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#     z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#     z2-when boot run 'sshd --lan'
        |#     z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
    """.trimMargin()

    val whenPaused: String =
        if (en) "Automatic runs paused (z2-when resume to start again)"
        else "自動実行を一時停止しました (z2-when resume で再開)"

    val whenResumed: String =
        if (en) "Automatic runs resumed" else "自動実行を再開しました"

    val whenNoFires: String =
        if (en) "(nothing has fired yet)" else "(まだ発火していません)"

    val whenPausedNote: String =
        if (en) "# paused (z2-when resume to start again)"
        else "# 一時停止中 (z2-when resume で再開)"

    val whenNoLog: String =
        if (en) "(no log yet)" else "(ログはまだありません)"

    val whenWriteFailed: String =
        if (en) "z2-when: could not write the rule" else "z2-when: 書き込みに失敗しました"

    /** `if=` に知らないキーを書いたとき。**キー名は呼び元がこの後ろに足す**。 */
    val whenUnknownIfKey: String =
        if (en) "z2-when: unknown if= key (z2-state lists what you can use):"
        else "z2-when: if= に書けない条件です (使えるものは z2-state が出す項目):"

    /** 知らない種別のトリガー (`:` の手前) を書いたとき。**種別は呼び元がこの後ろに足す**。 */
    val whenUnknownTrigger: String =
        if (en) "z2-when: unknown trigger (z2-when with no arguments lists them):"
        else "z2-when: そんなきっかけはありません (一覧は引数なしの z2-when で出ます):"

    /** 種別は合っているが引数の書き方が違うとき。**トリガー全体は呼び元がこの後ろに足す**。 */
    val whenBadTriggerSpec: String =
        if (en) "z2-when: that trigger does not take this argument:"
        else "z2-when: そのきっかけにその書き方はできません:"

    val whenPausedWarn: String =
        if (en) "note: automatic runs are paused (z2-when resume to start again)"
        else "注意: 自動実行は一時停止中です (z2-when resume で再開)"

    /**
     * `z2-when events` が出すイベント名の一覧。**名前 (1 列目) は言語を問わず同じ**で、
     * 説明と注記だけを訳す（名前はルールに書く識別子なので訳してはいけない）。
     */
    val whenEventList: String = if (en) """
        |screen_on              screen turned on          [detection ON]
        |screen_off             screen turned off         [detection ON]
        |unlocked               device was unlocked       [detection ON]
        |power_connected        charging started          [detection ON]
        |power_disconnected     charging stopped          [detection ON]
        |battery_low            battery got low           [detection ON]
        |battery_okay           battery recovered         [detection ON]
        |battery_level          level crossed a 10% mark  [detection ON]
        |wifi_connected         joined Wi-Fi              [detection ON]
        |wifi_disconnected      left Wi-Fi                [detection ON]
        |net_online             a link that works appeared [detection ON]
        |net_offline            no link that works        [detection ON]
        |net_wifi               the link in use is Wi-Fi  [detection ON]
        |net_mobile             the link in use is mobile [detection ON]
        |headset_plugged        wired earphones in        [detection ON]
        |headset_unplugged      wired earphones out       [detection ON]
        |bt_audio_connected     Bluetooth audio connected [detection ON]
        |bt_audio_disconnected  Bluetooth audio gone      [detection ON]
        |airplane_on            airplane mode on          [detection ON]
        |airplane_off           airplane mode off         [detection ON]
        |ringer_normal          ringer set to normal      [detection ON]
        |ringer_vibrate         ringer set to vibrate     [detection ON]
        |ringer_silent          ringer set to silent      [detection ON]
        |boot                   the device started up     [always]
        |alarm                  a z2-alarm fired          [always]
        |notify_action          a notification button     [always]
        |unlock_failed          failed to unlock          [always, needs setup]
        |unlock_succeeded       unlocked after a failure  [always, needs setup]
    """.trimMargin() else """
        |screen_on              画面が点いた            [検知 ON]
        |screen_off             画面が消えた            [検知 ON]
        |unlocked               ロックを解除した        [検知 ON]
        |power_connected        充電を開始した          [検知 ON]
        |power_disconnected     充電を止めた            [検知 ON]
        |battery_low            電池が少なくなった      [検知 ON]
        |battery_okay           電池が回復した          [検知 ON]
        |battery_level          残量が 10% 刻みを跨いだ [検知 ON]
        |wifi_connected         Wi-Fi に繋がった        [検知 ON]
        |wifi_disconnected      Wi-Fi が切れた          [検知 ON]
        |net_online             通信できる回線ができた  [検知 ON]
        |net_offline            通信できる回線が無い    [検知 ON]
        |net_wifi               使う回線が Wi-Fi になった [検知 ON]
        |net_mobile             使う回線がモバイルになった [検知 ON]
        |headset_plugged        有線イヤホンを挿した    [検知 ON]
        |headset_unplugged      有線イヤホンを抜いた    [検知 ON]
        |bt_audio_connected     Bluetooth 音声が繋がった [検知 ON]
        |bt_audio_disconnected  Bluetooth 音声が切れた   [検知 ON]
        |airplane_on            機内モードにした        [検知 ON]
        |airplane_off           機内モードを解除した    [検知 ON]
        |ringer_normal          着信音ありにした        [検知 ON]
        |ringer_vibrate         バイブにした            [検知 ON]
        |ringer_silent          マナーにした            [検知 ON]
        |boot                   端末が起動した          [常に]
        |alarm                  z2-alarm が鳴った       [常に]
        |notify_action          通知のボタンを押した    [常に]
        |unlock_failed          ロック解除に失敗した    [常に・要設定]
        |unlock_succeeded       失敗のあと解除できた    [常に・要設定]
    """.trimMargin()

    /** `z2-when events` の一覧の前に置くコメント（どちらの段が検知 ON を要るか）。 */
    val whenEventListNote: String = if (en) """
        |    # Names you can put in event:<name>. Same order as they appear in events.jsonl.
        |    # The upper group needs detection ON (Settings > resident servers & automation),
        |    # the lower group is armed by you, so it works with detection OFF.
    """.trimMargin() else """
        |    # event:<名前> に書ける名前。events.jsonl に出るものと同じ並び。
        |    # 上段は検知 ON が前提 (設定 › 常駐サーバー・自動化 › システムイベント検知)、
        |    # 下段は自分で仕掛けるものなので検知 OFF でも動く。
    """.trimMargin()
}
