package com.zerotoship.z2term.proot

/** Sound for an explicitly requested command, independent of the GUI runtime and user configuration. */
fun z2AudioScript(lang: String): String {
    val d = '$'
    val t = CliText(lang)
    val description = t(
        en = "Play sound from a local Linux command on Android, without opening a GUI.\ninstall: install PulseAudio in the current Linux OS (downloads packages).\nrun COMMAND [ARG...]: connect audio, run the command, then stop its audio server.\nRun a shell to enable sound for several commands. No configuration files are overwritten.\nRequires a local terminal tab and a PulseAudio-compatible player. SSH is not supported.",
        ja = "GUIを開かず、ローカルLinuxコマンドの音をAndroidで鳴らします。\ninstall: 現在のLinux OSへPulseAudioを導入します（パッケージを取得）。\nrun コマンド [引数...]: 音声を接続して実行し、終了時に専用の音声サーバーを停止。\nシェルを指定すれば、その中の複数コマンドで音を使えます。既存の設定ファイルは上書きしません。\nローカル端末タブとPulseAudio対応プレイヤーが必要です。SSHには対応しません。",
        "zh-CN" to "无需打开GUI，即可在Android播放本地Linux命令的声音。\ninstall: 在当前Linux系统安装PulseAudio（下载软件包）。\nrun 命令 [参数...]: 连接音频并执行命令，结束时停止专用音频服务器。\n指定Shell可为其中的多个命令启用声音。不会覆盖现有配置文件。\n需要本地终端标签页及支持PulseAudio的播放器。不支持SSH。",
        "zh-TW" to "無需開啟GUI，即可在Android播放本機Linux命令的聲音。\ninstall: 在目前Linux系統安裝PulseAudio（下載套件）。\nrun 命令 [參數...]: 連接音訊並執行命令，結束時停止專用音訊伺服器。\n指定Shell可為其中多個命令啟用聲音。不會覆寫現有設定檔。\n需要本機終端分頁及支援PulseAudio的播放器。不支援SSH。",
        "es" to "Reproduce en Android el sonido de un comando Linux local sin abrir el escritorio.\ninstall: instala PulseAudio en el sistema Linux actual (descarga paquetes).\nrun COMANDO [ARG...]: conecta el audio, ejecuta el comando y detiene su servidor al terminar.\nEjecuta un shell para usar sonido en varios comandos. No sobrescribe archivos de configuración.\nRequiere una pestaña local y un reproductor compatible con PulseAudio. No admite SSH.",
        "ko" to "GUI를 열지 않고 로컬 Linux 명령의 소리를 Android에서 재생합니다.\ninstall: 현재 Linux OS에 PulseAudio를 설치합니다(패키지 다운로드).\nrun 명령 [인수...]: 오디오를 연결하고 명령을 실행한 뒤 전용 서버를 종료합니다.\n셸을 지정하면 여러 명령에서 소리를 사용할 수 있습니다. 기존 설정 파일은 덮어쓰지 않습니다.\n로컬 터미널 탭과 PulseAudio 호환 플레이어가 필요합니다. SSH는 지원하지 않습니다."
    )
    val missing = t(
        en = "Audio tools are missing. Run: z2-audio install",
        ja = "音声用ツールがありません。z2-audio install を実行してください。",
        "zh-CN" to "缺少音频工具。请运行：z2-audio install",
        "zh-TW" to "缺少音訊工具。請執行：z2-audio install",
        "es" to "Faltan herramientas de audio. Ejecuta: z2-audio install",
        "ko" to "오디오 도구가 없습니다. z2-audio install을 실행하세요."
    )
    val failed = t(
        en = "Audio connection failed; the command was not started.",
        ja = "音声を接続できなかったため、指定コマンドは起動していません。",
        "zh-CN" to "音频连接失败，未启动指定命令。",
        "zh-TW" to "音訊連接失敗，未啟動指定命令。",
        "es" to "Falló la conexión de audio; no se inició el comando.",
        "ko" to "오디오 연결에 실패하여 지정한 명령을 시작하지 않았습니다."
    )
    val header = description.lines().joinToString("\n") { "# $it" }
    return "#!/bin/sh\n$header\n" + """
        |# z2-audio install
        |# z2-audio run COMMAND [ARG...]
        |# z2-audio -h | --help | help
        |case "${d}{1:-}" in
        |  ''|-h|--help|help)
        |    awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 && NF { exit }' "${d}0"; exit 0 ;;
        |  install)
        |    [ "${d}#" -eq 1 ] || exit 2
        |    if command -v apk >/dev/null 2>&1; then
        |      exec apk add --no-cache pulseaudio pulseaudio-utils
        |    elif command -v apt-get >/dev/null 2>&1; then
        |      apt-get update && apt-get install -y pulseaudio pulseaudio-utils
        |      exit "${d}?"
        |    elif command -v pacman >/dev/null 2>&1; then
        |      exec pacman -S --needed pulseaudio
        |    fi
        |    echo 'z2-audio: install pulseaudio and pactl with your package manager' >&2; exit 1 ;;
        |  run) shift ;;
        |  *) echo 'z2-audio: --help' >&2; exit 2 ;;
        |esac
        |[ "${d}#" -gt 0 ] || { echo 'z2-audio run COMMAND [ARG...]' >&2; exit 2; }
        |for tool in pulseaudio pactl setsid; do
        |  command -v "${d}tool" >/dev/null 2>&1 || { echo '$missing' >&2; exit 1; }
        |done
        |[ -n "${d}{Z2_SESSION_ID:-}" ] && [ -n "${d}{Z2_DISTRO_ID:-}" ] || {
        |  echo 'z2-audio: run in a local z2term Linux terminal tab' >&2; exit 1;
        |}
        |lease=''; audio_dir=''; pulse_pid=''
        |cleanup() {
        |  result=${d}?
        |  trap - 0 HUP INT TERM
        |  if [ -n "${d}pulse_pid" ]; then
        |    kill "${d}pulse_pid" 2>/dev/null || true
        |    wait "${d}pulse_pid" 2>/dev/null || true
        |  fi
        |  [ -z "${d}lease" ] || z2api 1 audio close "${d}Z2_SESSION_ID" "${d}Z2_DISTRO_ID" "${d}lease" >/dev/null 2>&1
        |  [ -z "${d}audio_dir" ] || rm -rf -- "${d}audio_dir"
        |  exit "${d}result"
        |}
        |trap cleanup 0
        |trap 'exit 129' HUP
        |trap 'exit 130' INT
        |trap 'exit 143' TERM
        |reply=${d}(z2api 1 audio open "${d}Z2_SESSION_ID" "${d}Z2_DISTRO_ID") || exit "${d}?"
        |lease=${d}{reply%% *}
        |audio_port=${d}{reply#* }
        |case "${d}lease" in ''|*[!a-f0-9-]*) exit 1 ;; esac
        |case "${d}audio_port" in ''|*[!0-9]*) exit 1 ;; esac
        |audio_dir=${d}(mktemp -d /tmp/z2-audio.XXXXXXXX) || exit 1
        |chmod 700 "${d}audio_dir" || exit 1
        |export PULSE_RUNTIME_PATH="${d}audio_dir"
        |export PULSE_STATE_PATH="${d}audio_dir"
        |export PULSE_CONFIG_PATH="${d}audio_dir"
        |export PULSE_CONFIG="${d}audio_dir/daemon.conf"
        |export PULSE_CLIENTCONFIG="${d}audio_dir/client.conf"
        |export PULSE_COOKIE="${d}audio_dir/cookie"
        |export PULSE_SERVER="unix:${d}audio_dir/native"
        |export PULSE_SINK=z2terminal
        |export SDL_AUDIODRIVER=pulseaudio
        |printf 'enable-shm = no\nflat-volumes = no\n' > "${d}PULSE_CONFIG" || exit 1
        |printf 'autospawn = no\nenable-shm = no\n' > "${d}PULSE_CLIENTCONFIG" || exit 1
        |# Do not daemonize: self re-exec through /proc/self/exe fails under the ptrace engine.
        |setsid pulseaudio -n --daemonize=no --use-pid-file=no --exit-idle-time=10 \
        |  --load="module-native-protocol-unix socket=${d}audio_dir/native" \
        |  --load="module-null-sink sink_name=z2terminal rate=48000 channels=2" \
        |  --load="module-simple-protocol-tcp record=true playback=false source=z2terminal.monitor format=s16le rate=48000 channels=2 listen=127.0.0.1 port=${d}audio_port" \
        |  --log-target="file:${d}audio_dir/pulse.log" </dev/null >"${d}audio_dir/startup.log" 2>&1 &
        |pulse_pid=${d}!
        |ready=0
        |attempt=0
        |while [ "${d}attempt" -lt 50 ] && kill -0 "${d}pulse_pid" 2>/dev/null; do
        |  if pactl info >/dev/null 2>&1; then
        |    state=${d}(z2api 1 audio ready "${d}Z2_SESSION_ID" "${d}Z2_DISTRO_ID" "${d}lease") || break
        |    if [ "${d}state" = ready ]; then ready=1; break; fi
        |  fi
        |  attempt=${d}((attempt + 1))
        |  sleep 0.1
        |done
        |if [ "${d}ready" -ne 1 ]; then
        |  echo '$failed' >&2
        |  tail -n 20 "${d}audio_dir/startup.log" "${d}audio_dir/pulse.log" >&2 2>/dev/null
        |  exit 1
        |fi
        |# Keep the command in the foreground with its original arguments and terminal stdin.
        |"${d}@"
        |exit "${d}?"
    """.trimMargin() + "\n"
}
