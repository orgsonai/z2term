package com.zerotoship.z2term.proot

/** Sound for an explicitly requested command, independent of the GUI runtime and user configuration. */
fun z2AudioScript(lang: String): String {
    val d = '$'
    val t = CliText(lang)
    val description = t("audio.description",
        en = "Play sound from a local Linux command on Android, without opening a GUI.\ninstall: install PulseAudio in the current Linux OS (downloads packages).\nrun COMMAND [ARG...]: connect audio, run the command, then stop its audio server.\nRun a shell to enable sound for several commands. No configuration files are overwritten.\nRequires a local terminal tab and a PulseAudio-compatible player. SSH is not supported.",
        ja = "GUIを開かず、ローカルLinuxコマンドの音をAndroidで鳴らします。\ninstall: 現在のLinux OSへPulseAudioを導入します（パッケージを取得）。\nrun コマンド [引数...]: 音声を接続して実行し、終了時に専用の音声サーバーを停止。\nシェルを指定すれば、その中の複数コマンドで音を使えます。既存の設定ファイルは上書きしません。\nローカル端末タブとPulseAudio対応プレイヤーが必要です。SSHには対応しません。"
    )
    val missing = t("audio.missing",
        en = "Audio tools are missing. Run: z2-audio install",
        ja = "音声用ツールがありません。z2-audio install を実行してください。"
    )
    val failed = t("audio.failed",
        en = "Audio connection failed; the command was not started.",
        ja = "音声を接続できなかったため、指定コマンドは起動していません。"
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
