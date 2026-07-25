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
        |#            sms:any | sms:from=<substr> | sms:contains=<substr> | sms:otp  (needs RECEIVE_SMS)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (detection ON)
        |#            event:<name> | event:<prefix>* | event:*  … any device event, by name
        |#              (z2-when events lists them; same names as in events.jsonl)
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
        |# For event: you also get Z2_WHEN_EVENT (the event name); alarm / notify_action add
        |# Z2_WHEN_EVENT_NAME (the identifier you armed it with) and Z2_WHEN_ACTION (button pressed).
        |# The same rule will not fire twice within 10 seconds (events like screen_on come often).
        |# e.g. z2-when charge:start run ~/.z2term/macros/backup.sh
        |#      z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#      z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#      z2-when 'event:ringer_*' run 'z2-toast "ringer: ${d}Z2_WHEN_EVENT"'
    """.trimMargin() else """
        |# z2-when <トリガー> run <コマンド...>   … ルールを登録
        |#   トリガー: charge:start | charge:stop  (検知 ON が前提)
        |#            battery:below=N | battery:above=N  (検知 ON が前提)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='分 時 日 月 曜日'  (曜日 0-7 / 0,7=日曜。空白を含むので要クォート)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<名前>  (検知 ON が前提)
        |#            sms:any | sms:from=<部分> | sms:contains=<部分> | sms:otp  (RECEIVE_SMS 許可が前提)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (検知 ON が前提)
        |#            event:<名前> | event:<接頭辞>* | event:*  … 端末イベントを名前で拾う
        |#              (名前は z2-when events で一覧。events.jsonl に出るものと同じ)
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
        |# event: のときは Z2_WHEN_EVENT (イベント名) が入る。alarm / notify_action では
        |# Z2_WHEN_EVENT_NAME (仕掛けたときの識別名) と Z2_WHEN_ACTION (押したボタン) も入る。
        |# 同じルールは 10 秒以内に続けて発火しない (screen_on のような数の多いイベント対策)。
        |# 例: z2-when charge:start run ~/.z2term/macros/backup.sh
        |#     z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#     z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#     z2-when 'event:ringer_*' run 'z2-toast "マナーモード: ${d}Z2_WHEN_EVENT"'
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
        |headset_plugged        wired earphones in        [detection ON]
        |headset_unplugged      wired earphones out       [detection ON]
        |bt_audio_connected     Bluetooth audio connected [detection ON]
        |bt_audio_disconnected  Bluetooth audio gone      [detection ON]
        |airplane_on            airplane mode on          [detection ON]
        |airplane_off           airplane mode off         [detection ON]
        |ringer_normal          ringer set to normal      [detection ON]
        |ringer_vibrate         ringer set to vibrate     [detection ON]
        |ringer_silent          ringer set to silent      [detection ON]
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
        |headset_plugged        有線イヤホンを挿した    [検知 ON]
        |headset_unplugged      有線イヤホンを抜いた    [検知 ON]
        |bt_audio_connected     Bluetooth 音声が繋がった [検知 ON]
        |bt_audio_disconnected  Bluetooth 音声が切れた   [検知 ON]
        |airplane_on            機内モードにした        [検知 ON]
        |airplane_off           機内モードを解除した    [検知 ON]
        |ringer_normal          着信音ありにした        [検知 ON]
        |ringer_vibrate         バイブにした            [検知 ON]
        |ringer_silent          マナーにした            [検知 ON]
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
