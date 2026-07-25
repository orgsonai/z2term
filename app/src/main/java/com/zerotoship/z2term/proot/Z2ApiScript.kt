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
fun z2ApiScripts(lang: String = "ja"): Map<String, String> {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    // 端末に出る文言だけを言語で切り替える (ロジックは 1 つのまま。[Z2ApiMsg] のヘッダ参照)。
    val m = Z2ApiMsg(en = lang == "en", d = d)

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

    val notify = "#!/bin/sh\n" + m.notifyHelp + "\n" + """
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
        |  echo "${m.notifyUsage}" >&2; exit 1
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

    val clip = "#!/bin/sh\n" + m.clipHelp + "\n" + """
        |case "${d}1" in
        |  get) exec /usr/local/bin/z2api 1 clip-get ;;
        |  set)
        |    shift
        |    if [ ${d}# -ge 1 ]; then text="${d}*"; else text="${d}(cat)"; fi
        |    exec /usr/local/bin/z2api 0 clip-set "${d}text" ;;
        |  *) echo "usage: z2-clip get | z2-clip set [text]" >&2; exit 1 ;;
        |esac
    """.trimMargin() + "\n"

    val battery = "#!/bin/sh\n" + m.batteryHelp + "\n" + """
        |exec /usr/local/bin/z2api 1 battery
    """.trimMargin() + "\n"

    val vibrate = "#!/bin/sh\n" + m.vibrateHelp + "\n" + """
        |exec /usr/local/bin/z2api 0 vibrate "${d}{1:-200}"
    """.trimMargin() + "\n"

    val say = "#!/bin/sh\n" + m.sayHelp + "\n" + """
        |if [ ${d}# -ge 1 ]; then text="${d}*"; else text="${d}(cat)"; fi
        |[ -n "${d}text" ] || { echo "usage: z2-say <text>" >&2; exit 1; }
        |exec /usr/local/bin/z2api 0 say "${d}text"
    """.trimMargin() + "\n"

    val torch = "#!/bin/sh\n" + m.torchHelp + "\n" + """
        |exec /usr/local/bin/z2api 1 torch "${d}{1:-toggle}"
    """.trimMargin() + "\n"

    val media = "#!/bin/sh\n" + m.mediaHelp + "\n" + """
        |exec /usr/local/bin/z2api 0 media "${d}{1:-playpause}"
    """.trimMargin() + "\n"

    val volume = "#!/bin/sh\n" + m.volumeHelp + "\n" + """
        |[ ${d}# -ge 1 ] || { echo "usage: z2-volume up|down|mute|unmute|N|N%" >&2; exit 1; }
        |exec /usr/local/bin/z2api 1 volume "${d}1"
    """.trimMargin() + "\n"

    val intent = "#!/bin/sh\n" + m.intentHelp + "\n" + """
        |[ ${d}# -ge 1 ] || { echo "usage: z2-intent [-a ACTION] [-d URI] [-p PKG] [-n PKG/CLS] ..." >&2; exit 1; }
        |exec /usr/local/bin/z2api 0 intent "${d}@"
    """.trimMargin() + "\n"

    val sensor = "#!/bin/sh\n" + m.sensorHelp + "\n" + """
        |exec /usr/local/bin/z2api 1 sensor "${d}{1:-light}"
    """.trimMargin() + "\n"

    val state = "#!/bin/sh\n" + m.stateHelp + "\n" + """
        |exec /usr/local/bin/z2api 1 state "${d}1"
    """.trimMargin() + "\n"

    val alarm = "#!/bin/sh\n" + m.alarmHelp + "\n" + """
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
        |    now=${d}(date +%s) || { echo "${m.alarmNoDate}" >&2; exit 1; }
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
    val session = "#!/bin/sh\n" + m.sessionHelp + "\n" + """
        |usage() {
        |  echo "${m.sessionUsage}" >&2
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
    val zwhen = "#!/bin/sh\n" + m.whenHelp + "\n" + """
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
        |    echo "${m.whenPaused}"
        |    ;;
        |  resume)
        |    rm -f "${d}DIR/.paused" 2>/dev/null
        |    echo "${m.whenResumed}"
        |    ;;
        |  fired)
        |    n="${d}2"; [ -n "${d}n" ] || n=20
        |    if [ -s "${d}DIR/.fired" ]; then
        |      tail -n "${d}n" "${d}DIR/.fired"
        |    else
        |      echo "${m.whenNoFires}"
        |    fi
        |    ;;
        |  list)
        |    if [ -e "${d}DIR/.paused" ]; then echo "${m.whenPausedNote}"; fi
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
        |    cat "${d}DIR/${d}2.log" 2>/dev/null || echo "${m.whenNoLog}"
        |    ;;
        |  events)
    """.trimMargin() + "\n" + m.whenEventListNote + "\n" + """
        |    cat <<'EOS'
    """.trimMargin() + "\n" + m.whenEventList + "\n" + """
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
        |    mv "${d}tmp" "${d}DIR/${d}id.rule" || { echo "${m.whenWriteFailed}" >&2; exit 1; }
        |    echo "${d}id"
        |    if [ -e "${d}DIR/.paused" ]; then
        |      echo "${m.whenPausedWarn}" >&2
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
