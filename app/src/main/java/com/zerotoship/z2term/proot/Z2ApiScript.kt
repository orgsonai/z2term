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
        |# 待ち時間は 0.1 秒 x この回数 (既定 5 秒)。z2-ask のように**人が答えるまで**待つものだけが
        |# Z2API_WAIT を伸ばす。ここを一律に長くすると、アプリが止まっているときの誤動作
        |# (応答が来ない) にどのコマンドも延々と付き合うことになる。
        |wait_n="${d}{Z2API_WAIT:-50}"
        |i=0
        |while [ ! -e "${d}resp" ] && [ ${d}i -lt "${d}wait_n" ]; do sleep 0.1; i=${d}((i+1)); done
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

    // z2-ask: 通知の返信欄で人に聞いて、答えを標準出力へ返す。応答待ちだけが長い (人が入力する)
    // ので、この 1 本だけ Z2API_WAIT を伸ばす。既定 5 分は「通知に気付いて答えるまで」の実感値で、
    // -t で変えられる。答えずに通知を消したらブリッジが ERR を返す = 非ゼロ終了なので、
    // `ans=$(z2-ask ...) || 諦める` が書ける (待ちっぱなしで固まらない)。
    val ask = "#!/bin/sh\n" + m.askHelp + "\n" + """
        |secs=300
        |hint=""
        |preset=""
        |while [ ${d}# -gt 0 ]; do
        |  case "${d}1" in
        |    -t|--timeout) secs="${d}2"; shift 2 || exit 1 ;;
        |    -H|--hint)    hint="${d}2"; shift 2 || exit 1 ;;
        |    -d|--default) preset="${d}2"; shift 2 || exit 1 ;;
        |    --) shift; break ;;
        |    *) break ;;
        |  esac
        |done
        |[ ${d}# -ge 1 ] || { echo "${m.askUsage}" >&2; exit 1; }
        |case "${d}secs" in *[!0-9]*|"") echo "${m.askUsage}" >&2; exit 1 ;; esac
        |[ "${d}secs" -gt 0 ] || { echo "${m.askUsage}" >&2; exit 1; }
        |Z2API_WAIT=${d}((secs*10)) exec /usr/local/bin/z2api 1 ask "${d}*" "${d}hint" "${d}preset"
    """.trimMargin() + "\n"

    val noti = "#!/bin/sh\n" + m.notiHelp + "\n" + """
        |case "${d}{1:-list}" in
        |  list) exec /usr/local/bin/z2api 1 noti list ;;
        |  *) echo "${m.notiUsage}" >&2; exit 1 ;;
        |esac
    """.trimMargin() + "\n"

    val state = "#!/bin/sh\n" + m.stateHelp + "\n" + """
        |exec /usr/local/bin/z2api 1 state "${d}1"
    """.trimMargin() + "\n"

    // OS の自動画面消灯を期限つきで止める。⚠ ツールバーの 🔅 (アプリを開いている間だけ) とは別物。
    // 相対時間 (1h / 30m / 90s) の秒への変換だけここで行い、あとはアプリ側 (ScreenTimeout) が持つ
    // — z2-alarm in と同じ分担。時間を必須にしているのは、期限の無い「消灯しない」を作らないため。
    val screen = "#!/bin/sh\n" + m.screenHelp + "\n" + """
        |usage() {
        |  echo "${m.screenUsage}" >&2
        |  exit 1
        |}
        |case "${d}{1:-status}" in
        |  status) exec /usr/local/bin/z2api 1 screen status ;;
        |  keepon)
        |    [ ${d}# -ge 2 ] || usage
        |    case "${d}2" in
        |      off|0) exec /usr/local/bin/z2api 1 screen off ;;
        |    esac
        |    spec="${d}2"
        |    num=${d}{spec%[smh]}
        |    # 先頭 0 を落とす ("05" を 8 進数と解釈する ${d}(()) 実装があるため。z2-alarm in と同じ)
        |    num=${d}(printf '%s' "${d}num" | sed 's/^0*//')
        |    [ -n "${d}num" ] || num=0
        |    case "${d}spec" in
        |      *h) secs=${d}((num*3600)) ;;
        |      *m) secs=${d}((num*60)) ;;
        |      *s|*[0-9]) secs=${d}num ;;
        |      *) usage ;;
        |    esac
        |    [ "${d}secs" -gt 0 ] 2>/dev/null || usage
        |    exec /usr/local/bin/z2api 1 screen keepon "${d}secs" ;;
        |  off) exec /usr/local/bin/z2api 1 screen off ;;
        |  *) usage ;;
        |esac
    """.trimMargin() + "\n"

    // クイック設定タイル (4 枠固定)。割り当てるのは「マクロのファイル名」か「そのまま走らせる
    // コマンド」で、どちらかはアプリ側 (TileStore.scriptFor) が名前で判別する — 打つ側に
    // 「これはマクロかコマンドか」を選ばせない。-l/--label は先に読み切ってから残りをコマンドにする。
    val tile = "#!/bin/sh\n" + m.tileHelp + "\n" + """
        |usage() {
        |  echo "${m.tileUsage}" >&2
        |  exit 1
        |}
        |sub="${d}{1:-list}"
        |case "${d}sub" in
        |  list) exec /usr/local/bin/z2api 1 tile list ;;
        |  clear)
        |    [ ${d}# -ge 2 ] || usage
        |    exec /usr/local/bin/z2api 1 tile clear "${d}2" ;;
        |  set)
        |    shift
        |    [ ${d}# -ge 2 ] || usage
        |    n="${d}1"; shift
        |    # -l/--label は**どこに書かれていても**拾う。表示名はコマンドの後ろに足したくなるので
        |    # (`z2-tile set 2 'z2-screen keepon 1h' -l 消灯しない`)、頭だけ見る作りにしない。
        |    # --off から後ろは「切るときのコマンド」。⚠ 引数を 2 つ並べるだけの形にはできない
        |    # (`z2-tile set 1 ls -la` が「入=ls / 切=-la」に化ける)。区切りを明示させる。
        |    label=""; cmd=""; off=""; seen_off=0
        |    while [ ${d}# -gt 0 ]; do
        |      case "${d}1" in
        |        -l|--label) label="${d}2"; shift 2 2>/dev/null || usage ;;
        |        --off) seen_off=1; shift ;;
        |        *)
        |          if [ "${d}seen_off" = 1 ]; then
        |            if [ -z "${d}off" ]; then off="${d}1"; else off="${d}off ${d}1"; fi
        |          else
        |            if [ -z "${d}cmd" ]; then cmd="${d}1"; else cmd="${d}cmd ${d}1"; fi
        |          fi
        |          shift ;;
        |      esac
        |    done
        |    [ -n "${d}cmd" ] || usage
        |    # --off と書いたのに中身が無いのは打ち間違い。空で通すと「押しても切れないトグル」になる。
        |    [ "${d}seen_off" = 0 ] || [ -n "${d}off" ] || usage
        |    # ⚠ マクロ名らしいのに置き場に無いものは**ここで弾く** (z2-when の綴り検査と同じ思想)。
        |    # 通してしまうとコマンド扱いで PATH から探され、見つからず「押しても無反応」になる。
        |    # 失敗は tile/run.log にしか出ないので、外からは正しい割り当てと区別が付かない。
        |    check_macro() {
        |      first="${d}{1%% *}"
        |      case "${d}first" in
        |        */*) return 0 ;;                     # パス指定は素直にコマンドとして扱う
        |        *.sh)
        |          [ -f "${d}HOME/.z2term/macros/${d}first" ] ||
        |            { echo "${m.tileNoSuchMacro} ${d}first" >&2; exit 1; } ;;
        |      esac
        |    }
        |    check_macro "${d}cmd"
        |    [ -z "${d}off" ] || check_macro "${d}off"
        |    exec /usr/local/bin/z2api 1 tile set "${d}n" "${d}cmd" "${d}label" "${d}off" ;;
        |  *) usage ;;
        |esac
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
        |  echo "usage: z2-when <trigger> [if=..] [cooldown=..] [between=..] [days=..] run <cmd...>" >&2
        |  echo "       z2-when list | events | pause | resume | fired [n] | remove <id|all> | on <id> | off <id> | log <id>" >&2
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
        |      # 絞り込みが付いていれば末尾に足す (付いていないルールの見え方は今までどおり)。
        |      w=""
        |      for k in if cooldown between days; do
        |        v=${d}(sed -n "s/^${d}k=//p" "${d}f")
        |        [ -n "${d}v" ] && w="${d}w ${d}k=${d}v"
        |      done
        |      if [ -n "${d}w" ]; then
        |        printf '%s\t%s\t%s\t->\t%s\t[%s]\n' "${d}id" "${d}st" "${d}t" "${d}r" "${d}{w# }"
        |      else
        |        printf '%s\t%s\t%s\t->\t%s\n' "${d}id" "${d}st" "${d}t" "${d}r"
        |      fi
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
        |    # 絞り込み (0.8.263) はトリガーの直後に置く。run の後ろは**全部コマンド**という
        |    # 今までの読み方を変えないため (コマンド側に if= が現れても誤解しない)。
        |    zif=""; zcool=""; zbetw=""; zdays=""
        |    while [ ${d}# -ge 1 ]; do
        |      case "${d}1" in
        |        if=*)       zif="${d}{1#if=}"; shift ;;
        |        cooldown=*) zcool="${d}{1#cooldown=}"; shift ;;
        |        between=*)  zbetw="${d}{1#between=}"; shift ;;
        |        days=*)     zdays="${d}{1#days=}"; shift ;;
        |        run)        shift; break ;;
        |        *)          break ;;
        |      esac
        |    done
        |    { [ -n "${d}trig" ] && [ ${d}# -ge 1 ]; } || usage
        |    # トリガーを登録時に検査する (if= のキーと同じ理由・0.8.265)。綴りが 1 文字違っても
        |    # 登録は成功して**黙って一度も発火しない**ので、書いた瞬間に止めないと原因に辿り着けない。
        |    # 種別と引数の一覧は WhenRule の KDoc / whenHelp と揃えること。
        |    tkind="${d}{trig%%:*}"
        |    tspec=""
        |    case "${d}trig" in *:*) tspec="${d}{trig#*:}" ;; esac
        |    badspec=0
        |    case "${d}tkind" in
        |      boot)    [ -z "${d}tspec" ] || badspec=1 ;;
        |      charge)  case "${d}tspec" in start|stop) ;; *) badspec=1 ;; esac ;;
        |      battery) case "${d}tspec" in below=?*|above=?*) ;; *) badspec=1 ;; esac ;;
        |      time)    case "${d}tspec" in daily=?*|at=?*|every=?*|cron=?*) ;; *) badspec=1 ;; esac ;;
        |      wifi)    case "${d}tspec" in connect|disconnect|ssid=?*) ;; *) badspec=1 ;; esac ;;
        |      net)     case "${d}tspec" in online|offline|wifi|mobile|ethernet) ;; *) badspec=1 ;; esac ;;
        |      sms)     case "${d}tspec" in any|otp|from=?*|contains=?*) ;; *) badspec=1 ;; esac ;;
        |      notify)  case "${d}tspec" in any|otp|pkg=?*|title=?*|contains=?*|category=?*) ;; *) badspec=1 ;; esac ;;
        |      file)    case "${d}tspec" in new=?*) ;; *) badspec=1 ;; esac ;;
        |      share)   case "${d}tspec" in any|text|file|contains=?*|ext=?*) ;; *) badspec=1 ;; esac ;;
        |      # `>` `<` は case のパターンではリダイレクトに読まれうるのでクォートする。
        |      sensor)  case "${d}tspec" in shake|"light>"?*|"light<"?*|proximity=near|proximity=far) ;; *) badspec=1 ;; esac ;;
        |      # event: の名前は増え続ける (z2-when events が正本) ので、ここでは空でないことだけ見る。
        |      event)   [ -n "${d}tspec" ] || badspec=1 ;;
        |      *) echo "${m.whenUnknownTrigger} ${d}tkind" >&2; exit 1 ;;
        |    esac
        |    [ "${d}badspec" = 0 ] || { echo "${m.whenBadTriggerSpec} ${d}trig" >&2; exit 1; }
        |    # if= のキーを登録時に検査する (実行時に黙って不成立になるより、書いた瞬間に気付ける)。
        |    # 一覧は WhenGuard.KNOWN_KEYS と揃えること。
        |    if [ -n "${d}zif" ]; then
        |      for t in ${d}(echo "${d}zif" | tr ',' ' '); do
        |        k=${d}(echo "${d}{t#!}" | sed 's/[<>=].*//')
        |        case " screen locked idle charging plug level wifi ssid ringer airplane headset bt_audio temp volume volume_max " in
        |          *" ${d}k "*) ;;
        |          *) echo "${m.whenUnknownIfKey} ${d}k" >&2; exit 1 ;;
        |        esac
        |      done
        |    fi
        |    # ルールファイルは 1 行 1 項目。改行入りのまま書くと 2 行目以降が別の項目として
        |    # 読まれ、**途中で切れたコマンド**が黙って登録される (折り返して貼り付けると起きる)。
        |    # 弾くのではなく空白へ直して通し、直したことだけ伝える。
        |    cmdraw="${d}*"
        |    cmd=${d}(printf '%s' "${d}cmdraw" | tr '\n\r' '  ')
        |    [ "${d}cmd" = "${d}cmdraw" ] || echo "${m.whenRunJoined}" >&2
        |    # id は w<epoch><pid>。awk の srand() は「秒」で seed されるため同一秒では同じ乱数になり、
        |    # 続けて登録したルールが同じ id で上書きし合っていた。pid は 1 プロセス 1 値なので同一秒でも
        |    # 衝突しない。pid 再利用に備えて既存ファイルがあれば連番を足す (二重の防御)。
        |    base="w${d}(date +%s 2>/dev/null)${d}${d}"
        |    id="${d}base"; n=0
        |    while [ -e "${d}DIR/${d}id.rule" ]; do n=${d}((n+1)); id="${d}base-${d}n"; done
        |    tmp="${d}DIR/.${d}id.tmp"
        |    {
        |      printf 'trigger=%s\n' "${d}trig"; printf 'run=%s\n' "${d}cmd"; printf 'enabled=1\n'
        |      [ -n "${d}zif" ] && printf 'if=%s\n' "${d}zif"
        |      [ -n "${d}zcool" ] && printf 'cooldown=%s\n' "${d}zcool"
        |      [ -n "${d}zbetw" ] && printf 'between=%s\n' "${d}zbetw"
        |      [ -n "${d}zdays" ] && printf 'days=%s\n' "${d}zdays"
        |      true
        |    } > "${d}tmp"
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
        "z2-screen" to screen,
        "z2-tile" to tile,
        "z2-noti" to noti,
        "z2-ask" to ask,
        "z2-alarm" to alarm,
    )
}
