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
        |# キー: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset volume volume_max
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

    return linkedMapOf(
        "z2api" to dispatcher,
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
