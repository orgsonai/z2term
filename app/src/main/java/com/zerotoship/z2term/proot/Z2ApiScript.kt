package com.zerotoship.z2term.proot

/**
 * Android API ブリッジの rootfs 側ヘルパー (`z2-*` CLI 群)。
 *
 * 端末 (PRoot/Linux) から `z2-notify "done"` のように叩くと、共有ディレクトリ
 * `/storage/app/z2api/req/` にリクエストファイルを書き、アプリ側の
 * [com.zerotoship.z2term.service.Z2ApiBridge] (FileObserver) が Android 機能を代行する。
 *
 * 中核は `z2api` ディスパッチャ 1 本で、各 `z2-xxx` はその薄いラッパー。プロトコル詳細は
 * [com.zerotoship.z2term.service.Z2ApiBridge] のヘッダ参照。
 *
 * [ProotLauncher.ensureZ2ApiScripts] が launch 毎に `/usr/local/bin` へ書き出す
 * (PATH 上で直接叩ける)。内容は常に最新に上書きされる。
 */
fun z2ApiScripts(): Map<String, String> {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)

    val dispatcher = """
        |#!/bin/sh
        |# z2term Android API ブリッジ・ディスパッチャ (内部用)。
        |# usage: z2api <need_resp 0|1> <cmd> [args...]
        |DIR=/storage/app/z2api
        |mkdir -p "${d}DIR/req" "${d}DIR/resp" 2>/dev/null || true
        |need_resp="${d}1"; shift
        |cmd="${d}1"; shift
        |rnd=${d}(awk 'BEGIN{srand(); printf "%d", rand()*1000000}' 2>/dev/null)
        |id="${d}${d}-${d}(date +%s 2>/dev/null)-${d}rnd"
        |tmp="${d}DIR/req/.${d}id.tmp"
        |{
        |  echo "CMD ${d}cmd"
        |  for a in "${d}@"; do
        |    printf 'A %s\n' "${d}(printf '%s' "${d}a" | base64 | tr -d '\n')"
        |  done
        |  [ "${d}need_resp" = "1" ] && echo "R 1"
        |} > "${d}tmp" 2>/dev/null
        |mv "${d}tmp" "${d}DIR/req/${d}id.req" 2>/dev/null || { echo "z2api: cannot write request (storage perm?)" >&2; exit 1; }
        |[ "${d}need_resp" = "1" ] || exit 0
        |resp="${d}DIR/resp/${d}id.resp"
        |i=0
        |while [ ! -e "${d}resp" ] && [ ${d}i -lt 50 ]; do sleep 0.1; i=${d}((i+1)); done
        |if [ ! -e "${d}resp" ]; then echo "z2api: timeout waiting for app" >&2; exit 1; fi
        |line=${d}(head -n1 "${d}resp")
        |status=${d}{line%% *}
        |payload=${d}{line#* }
        |rm -f "${d}resp" 2>/dev/null
        |dec=${d}(printf '%s' "${d}payload" | base64 -d 2>/dev/null)
        |if [ "${d}status" = "OK" ]; then
        |  printf '%s\n' "${d}dec"
        |  exit 0
        |else
        |  printf '%s\n' "${d}dec" >&2
        |  exit 1
        |fi
    """.trimMargin() + "\n"

    val notify = """
        |#!/bin/sh
        |# z2-notify [-h] [-n 名前] [-b ラベル]... "タイトル" "本文"  /  z2-notify [-h] "本文"
        |#   -h / --high / --banner : 画面上部にバナー(ヘッドアップ)表示する
        |#   -b <ラベル>            : 返事のボタンを付ける (最大 3 つ)。押すと
        |#                            ~/.z2term/events.jsonl に notify_action が 1 行増える
        |#                            ({"event":"notify_action","name":名前,"action":ラベル})
        |#   -n <名前>              : その通知の識別名 (どの問いかけへの返事か区別する用)
        |high=""
        |name=""
        |b1=""; b2=""; b3=""
        |while [ ${d}# -gt 0 ]; do
        |  case "${d}1" in
        |    -h|--high|--banner) high="high"; shift ;;
        |    -n|--name) name="${d}2"; shift 2 || exit 1 ;;
        |    -b|--button)
        |      # 先着 3 つを採用し、4 つ目以降は黙って無視する (Android が 3 つしか出さないため)。
        |      if   [ -z "${d}b1" ]; then b1="${d}2"
        |      elif [ -z "${d}b2" ]; then b2="${d}2"
        |      elif [ -z "${d}b3" ]; then b3="${d}2"
        |      fi
        |      shift 2 || exit 1 ;;
        |    --) shift; break ;;
        |    *) break ;;
        |  esac
        |done
        |if [ ${d}# -ge 2 ]; then
        |  exec /usr/local/bin/z2api 0 notify "${d}1" "${d}2" "${d}high" "${d}name" "${d}b1" "${d}b2" "${d}b3"
        |elif [ ${d}# -eq 1 ]; then
        |  exec /usr/local/bin/z2api 0 notify "${d}1" "" "${d}high" "${d}name" "${d}b1" "${d}b2" "${d}b3"
        |else
        |  echo "usage: z2-notify [-h] [-n name] [-b label]... <title> [text]" >&2; exit 1
        |fi
    """.trimMargin() + "\n"

    val toast = """
        |#!/bin/sh
        |[ ${d}# -ge 1 ] || { echo "usage: z2-toast <message>" >&2; exit 1; }
        |exec /usr/local/bin/z2api 0 toast "${d}*"
    """.trimMargin() + "\n"

    val share = """
        |#!/bin/sh
        |[ ${d}# -ge 1 ] || { echo "usage: z2-share <text>" >&2; exit 1; }
        |exec /usr/local/bin/z2api 0 share "${d}*"
    """.trimMargin() + "\n"

    val open = """
        |#!/bin/sh
        |[ ${d}# -ge 1 ] || { echo "usage: z2-open <url|path>" >&2; exit 1; }
        |exec /usr/local/bin/z2api 0 open "${d}1"
    """.trimMargin() + "\n"

    val clip = """
        |#!/bin/sh
        |# z2-clip get        … クリップボードを標準出力へ
        |# z2-clip set [text] … text (無ければ標準入力) をクリップボードへ
        |case "${d}1" in
        |  get) exec /usr/local/bin/z2api 1 clip-get ;;
        |  set)
        |    shift
        |    if [ ${d}# -ge 1 ]; then text="${d}*"; else text="${d}(cat)"; fi
        |    exec /usr/local/bin/z2api 0 clip-set "${d}text" ;;
        |  *) echo "usage: z2-clip get | z2-clip set [text]" >&2; exit 1 ;;
        |esac
    """.trimMargin() + "\n"

    val battery = """
        |#!/bin/sh
        |# 残量/充電状態を JSON ({"level":N,"charging":bool}) で出力。
        |exec /usr/local/bin/z2api 1 battery
    """.trimMargin() + "\n"

    val vibrate = """
        |#!/bin/sh
        |# z2-vibrate [ms]  (既定 200ms)
        |exec /usr/local/bin/z2api 0 vibrate "${d}{1:-200}"
    """.trimMargin() + "\n"

    val say = """
        |#!/bin/sh
        |# z2-say <text>       … 端末標準の TTS で読み上げ (引数無しなら標準入力を読む)
        |if [ ${d}# -ge 1 ]; then text="${d}*"; else text="${d}(cat)"; fi
        |[ -n "${d}text" ] || { echo "usage: z2-say <text>" >&2; exit 1; }
        |exec /usr/local/bin/z2api 0 say "${d}text"
    """.trimMargin() + "\n"

    val torch = """
        |#!/bin/sh
        |# z2-torch on|off|toggle  (既定 toggle)。結果の点灯状態 (on/off) を出力。
        |exec /usr/local/bin/z2api 1 torch "${d}{1:-toggle}"
    """.trimMargin() + "\n"

    val media = """
        |#!/bin/sh
        |# z2-media play|pause|playpause|next|previous|stop  (既定 playpause)
        |exec /usr/local/bin/z2api 0 media "${d}{1:-playpause}"
    """.trimMargin() + "\n"

    val volume = """
        |#!/bin/sh
        |# z2-volume up|down|mute|unmute|N|N%   メディア音量を操作。結果の current/max を出力。
        |[ ${d}# -ge 1 ] || { echo "usage: z2-volume up|down|mute|unmute|N|N%" >&2; exit 1; }
        |exec /usr/local/bin/z2api 1 volume "${d}1"
    """.trimMargin() + "\n"

    val intent = """
        |#!/bin/sh
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# 任意の Android Intent を発火 (既定は startActivity)。先頭の非フラグ引数は ACTION。
        |[ ${d}# -ge 1 ] || { echo "usage: z2-intent [-a ACTION] [-d URI] [-p PKG] [-n PKG/CLS] ..." >&2; exit 1; }
        |exec /usr/local/bin/z2api 0 intent "${d}@"
    """.trimMargin() + "\n"

    val sensor = """
        |#!/bin/sh
        |# z2-sensor light|accel|proximity  (既定 light)。センサーを 1 回読んで JSON で返す。
        |exec /usr/local/bin/z2api 1 sensor "${d}{1:-light}"
    """.trimMargin() + "\n"

    val state = """
        |#!/bin/sh
        |# z2-state            … 今の端末の状態をまとめて JSON で返す
        |# z2-state <キー>     … その値だけを生で返す (条件式にそのまま書ける)
        |# キー: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(℃)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# 例: [ "${d}(z2-state charging)" = "true" ] && echo 充電中
        |exec /usr/local/bin/z2api 1 state "${d}1"
    """.trimMargin() + "\n"

    val alarm = """
        |#!/bin/sh
        |# z2-alarm at HH:MM [名前]     … 次の HH:MM に 1 回 (今日を過ぎていれば明日)
        |# z2-alarm daily HH:MM [名前]  … 毎日 HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [名前] … N 秒/分/時間後に 1 回
        |# z2-alarm list                … 予約一覧 (JSON)
        |# z2-alarm cancel <id|名前|all> … 取り消し
        |# 発火すると ~/.z2term/events.jsonl に {"event":"alarm","name":…} が 1 行増える。
        |# Doze 中でも起きるが、省電力のため発火が数分ずれることがある。
        |usage() {
        |  echo "usage: z2-alarm at HH:MM [name] | daily HH:MM [name] | in <N[s|m|h]> [name] | list | cancel <id|name|all>" >&2
        |  exit 1
        |}
        |# "HH:MM" を hour/minute に割る。数値化はアプリ側 (10進で解釈) に任せるので、
        |# ここでは形が HH:MM かどうかだけ見る (sh の $(()) は先頭 0 を 8 進と誤解する実装があるため触らない)。
        |split_hm() {
        |  case "${d}1" in
        |    [0-9]*:[0-9]*) ;;
        |    *) usage ;;
        |  esac
        |  hour=${d}{1%%:*}; minute=${d}{1##*:}
        |}
        |case "${d}1" in
        |  at)
        |    [ ${d}# -ge 2 ] || usage
        |    split_hm "${d}2"
        |    exec /usr/local/bin/z2api 1 alarm once "${d}hour" "${d}minute" "${d}3" ;;
        |  daily)
        |    [ ${d}# -ge 2 ] || usage
        |    split_hm "${d}2"
        |    exec /usr/local/bin/z2api 1 alarm daily "${d}hour" "${d}minute" "${d}3" ;;
        |  in)
        |    [ ${d}# -ge 2 ] || usage
        |    spec="${d}2"
        |    num=${d}{spec%[smh]}
        |    # 先頭 0 を落とす ("05" を 8 進数と解釈する $(()) 実装があるため)
        |    num=${d}(printf '%s' "${d}num" | sed 's/^0*//')
        |    [ -n "${d}num" ] || num=0
        |    case "${d}spec" in
        |      *h) secs=${d}((num*3600)) ;;
        |      *m) secs=${d}((num*60)) ;;
        |      *s|*[0-9]) secs=${d}num ;;
        |      *) usage ;;
        |    esac
        |    [ "${d}secs" -gt 0 ] 2>/dev/null || usage
        |    now=${d}(date +%s) || { echo "z2-alarm: date が使えません" >&2; exit 1; }
        |    exec /usr/local/bin/z2api 1 alarm at "${d}((  (now+secs)*1000  ))" "${d}3" ;;
        |  list)   exec /usr/local/bin/z2api 1 alarm list ;;
        |  cancel)
        |    [ ${d}# -ge 2 ] || usage
        |    exec /usr/local/bin/z2api 1 alarm cancel "${d}2" ;;
        |  *) usage ;;
        |esac
    """.trimMargin() + "\n"

    // アプリ自身のタブを操る (A1)。他の z2-* が「Android を叩く」のに対し、これだけは内側を触る。
    // send は既定で「入れるだけ・実行しない」。実行させたいときだけ --enter を明示する
    // (共有の受け取りと同じ約束で、他のタブが勝手に走り出す状態を作らない)。
    val session = """
        |#!/bin/sh
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
        |usage() {
        |  echo "usage: z2-session list | new [名前] | send <先> <文字列>... [--enter] | capture [先] [--all] | close <先>" >&2
        |  exit 1
        |}
        |[ ${d}# -ge 1 ] || usage
        |sub="${d}1"; shift
        |case "${d}sub" in
        |  list)    exec /usr/local/bin/z2api 1 session list ;;
        |  new)     exec /usr/local/bin/z2api 1 session new "${d}1" ;;
        |  send)
        |    [ ${d}# -ge 2 ] || usage
        |    exec /usr/local/bin/z2api 1 session send "${d}@" ;;
        |  capture) exec /usr/local/bin/z2api 1 session capture "${d}@" ;;
        |  close)
        |    [ ${d}# -ge 1 ] || usage
        |    exec /usr/local/bin/z2api 1 session close "${d}1" ;;
        |  *) usage ;;
        |esac
    """.trimMargin() + "\n"

    // 自動化ハブ (A6)。トリガー (充電/電池/時刻) を宣言すると、アプリ側 (WhenManager) が監視して
    // 発火時に run のコマンドを実行する。ルールは ~/.z2term/when/<id>.rule のテキスト (git 同期が効く)。
    // CLI はファイルを直接読み書きし、変更後に z2api when-reload で時刻トリガーを貼り直させる。
    val zwhen = """
        |#!/bin/sh
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
        |DIR="${d}HOME/.z2term/when"
        |mkdir -p "${d}DIR" 2>/dev/null
        |reload() { /usr/local/bin/z2api 0 when-reload >/dev/null 2>&1 || true; }
        |usage() {
        |  echo "usage: z2-when <trigger> run <cmd...> | list | events | pause | resume | fired [n] | remove <id|all> | on <id> | off <id> | log <id>" >&2
        |  exit 1
        |}
        |[ ${d}# -ge 1 ] || usage
        |case "${d}1" in
        |  pause)
        |    printf 'paused\n' > "${d}DIR/.paused"
        |    echo "自動実行を一時停止しました (z2-when resume で再開)"
        |    ;;
        |  resume)
        |    rm -f "${d}DIR/.paused" 2>/dev/null
        |    echo "自動実行を再開しました"
        |    ;;
        |  fired)
        |    n="${d}2"; [ -n "${d}n" ] || n=20
        |    if [ -s "${d}DIR/.fired" ]; then
        |      tail -n "${d}n" "${d}DIR/.fired"
        |    else
        |      echo "(まだ発火していません)"
        |    fi
        |    ;;
        |  list)
        |    if [ -e "${d}DIR/.paused" ]; then echo "# 一時停止中 (z2-when resume で再開)"; fi
        |    for f in "${d}DIR"/*.rule; do
        |      [ -e "${d}f" ] || continue
        |      id=${d}(basename "${d}f" .rule)
        |      t=${d}(sed -n 's/^trigger=//p' "${d}f")
        |      r=${d}(sed -n 's/^run=//p' "${d}f")
        |      e=${d}(sed -n 's/^enabled=//p' "${d}f")
        |      if [ "${d}e" = "0" ]; then st=off; else st=on; fi
        |      printf '%s\t%s\t%s\t->\t%s\n' "${d}id" "${d}st" "${d}t" "${d}r"
        |    done
        |    ;;
        |  remove|rm)
        |    [ ${d}# -ge 2 ] || usage
        |    if [ "${d}2" = "all" ]; then
        |      rm -f "${d}DIR"/*.rule "${d}DIR"/*.log 2>/dev/null
        |    else
        |      rm -f "${d}DIR/${d}2.rule" "${d}DIR/${d}2.log" 2>/dev/null
        |    fi
        |    reload
        |    ;;
        |  on|off)
        |    [ ${d}# -ge 2 ] || usage
        |    f="${d}DIR/${d}2.rule"
        |    [ -f "${d}f" ] || { echo "z2-when: no such rule: ${d}2" >&2; exit 1; }
        |    if [ "${d}1" = "off" ]; then val=0; else val=1; fi
        |    if ! sed -i "s/^enabled=.*/enabled=${d}val/" "${d}f" 2>/dev/null; then
        |      # sed -i が無い環境 (一部 busybox) のフォールバック。
        |      tmp="${d}f.tmp"; sed "s/^enabled=.*/enabled=${d}val/" "${d}f" > "${d}tmp" && mv "${d}tmp" "${d}f"
        |    fi
        |    reload
        |    ;;
        |  log)
        |    [ ${d}# -ge 2 ] || usage
        |    cat "${d}DIR/${d}2.log" 2>/dev/null || echo "(ログはまだありません)"
        |    ;;
        |  events)
        |    # event:<名前> に書ける名前。events.jsonl に出るものと同じ並び。
        |    # 上段は検知 ON が前提 (設定 › 常駐サーバー・自動化 › システムイベント検知)、
        |    # 下段は自分で仕掛けるものなので検知 OFF でも動く。
        |    cat <<'EOS'
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
        |EOS
        |    ;;
        |  *)
        |    trig="${d}1"; shift
        |    [ "${d}1" = "run" ] && shift
        |    { [ -n "${d}trig" ] && [ ${d}# -ge 1 ]; } || usage
        |    cmd="${d}*"
        |    # id は w<epoch><pid>。awk の srand() は「秒」で seed されるため同一秒では同じ乱数になり、
        |    # 続けて登録したルールが同じ id で上書きし合っていた。pid は 1 プロセス 1 値なので同一秒でも
        |    # 衝突しない。pid 再利用に備えて既存ファイルがあれば連番を足す (二重の防御)。
        |    base="w${d}(date +%s 2>/dev/null)${d}${d}"
        |    id="${d}base"; n=0
        |    while [ -e "${d}DIR/${d}id.rule" ]; do n=${d}((n+1)); id="${d}base-${d}n"; done
        |    tmp="${d}DIR/.${d}id.tmp"
        |    { printf 'trigger=%s\n' "${d}trig"; printf 'run=%s\n' "${d}cmd"; printf 'enabled=1\n'; } > "${d}tmp"
        |    mv "${d}tmp" "${d}DIR/${d}id.rule" || { echo "z2-when: 書き込みに失敗しました" >&2; exit 1; }
        |    echo "${d}id"
        |    if [ -e "${d}DIR/.paused" ]; then
        |      echo "注意: 自動実行は一時停止中です (z2-when resume で再開)" >&2
        |    fi
        |    reload
        |    ;;
        |esac
    """.trimMargin() + "\n"

    return linkedMapOf(
        "z2api" to dispatcher,
        "z2-session" to session,
        "z2-when" to zwhen,
        "z2-notify" to notify,
        "z2-toast" to toast,
        "z2-share" to share,
        "z2-open" to open,
        "z2-clip" to clip,
        "z2-battery" to battery,
        "z2-vibrate" to vibrate,
        "z2-say" to say,
        "z2-torch" to torch,
        "z2-media" to media,
        "z2-volume" to volume,
        "z2-intent" to intent,
        "z2-sensor" to sensor,
        "z2-state" to state,
        "z2-alarm" to alarm,
    )
}
