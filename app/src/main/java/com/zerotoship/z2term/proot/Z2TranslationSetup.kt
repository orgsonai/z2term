package com.zerotoship.z2term.proot

/** Prints manual setup only. Read OS metadata as data, never as shell commands. */
internal fun z2TranslationSetup(lang: String): String {
    val t = CliText(lang)
    val d = '$'
    val setup = t(en = "Open a terminal in the same local Linux environment, run the command below yourself, then retry translation.",
        ja = "同じローカルLinux環境のターミナルで、次のコマンドをユーザー自身で実行してください。導入後、翻訳を再実行してください。",
        "zh-CN" to "请在同一本地 Linux 环境的终端中自行执行以下命令，安装后重试翻译。",
        "zh-TW" to "請在同一本機 Linux 環境的終端中自行執行以下命令，安裝後重試翻譯。",
        "es" to "Abre una terminal en el mismo entorno Linux local, ejecuta el comando y vuelve a traducir.",
        "ko" to "같은 로컬 Linux 환경의 터미널에서 다음 명령을 직접 실행한 후 번역을 다시 시도하세요.")
    val alpineRepo = t(en = "If the package is not found, enable the community repository for the same Alpine release in /etc/apk/repositories.",
        ja = "パッケージが見つからない場合は、/etc/apk/repositories で同じAlpine版の community リポジトリを有効にしてください。",
        "zh-CN" to "若找不到软件包，请在 /etc/apk/repositories 中启用相同 Alpine 版本的 community 仓库。",
        "zh-TW" to "若找不到套件，請在 /etc/apk/repositories 中啟用相同 Alpine 版本的 community 軟體庫。",
        "es" to "Si falta el paquete, activa community para la misma versión de Alpine en /etc/apk/repositories.",
        "ko" to "패키지가 없으면 /etc/apk/repositories에서 같은 Alpine 버전의 community 저장소를 활성화하세요.")
    val ubuntuRepo = t(en = "If the package is not found on Ubuntu, enable its universe repository and retry.",
        ja = "Ubuntuでパッケージが見つからない場合は、universe リポジトリを有効にして再実行してください。",
        "zh-CN" to "若 Ubuntu 中找不到软件包，请启用 universe 仓库后重试。",
        "zh-TW" to "若 Ubuntu 中找不到套件，請啟用 universe 軟體庫後重試。",
        "es" to "Si falta el paquete en Ubuntu, activa el repositorio universe y reintenta.",
        "ko" to "Ubuntu에서 패키지가 없으면 universe 저장소를 활성화한 후 다시 시도하세요.")
    val unknown = t(en = "Distribution not recognized. Choose the command for your local Linux environment:",
        ja = "ディストリを判別できませんでした。使用中のローカルLinux環境に合うコマンドを選んでください。",
        "zh-CN" to "无法识别发行版。请选择适合当前本地 Linux 环境的命令。",
        "zh-TW" to "無法辨識發行版。請選擇適合目前本機 Linux 環境的命令。",
        "es" to "No se reconoce la distribución. Elige el comando para tu entorno Linux local:",
        "ko" to "배포판을 확인할 수 없습니다. 사용 중인 로컬 Linux 환경에 맞는 명령을 선택하세요.")

    return """
        |translation_setup() (
        |    distro=
        |    related=
        |    for release_file in "${d}{1:-/etc/os-release}" "${d}{2:-/usr/lib/os-release}"; do
        |        [ -r "${d}release_file" ] || continue
        |        while IFS='=' read -r key value || [ -n "${d}key" ]; do
        |            case "${d}value" in
        |                \"*\") value=${d}{value#\"}; value=${d}{value%\"} ;;
        |                \'*\') value=${d}{value#\'}; value=${d}{value%\'} ;;
        |            esac
        |            case "${d}key" in ID) distro=${d}value ;; ID_LIKE) related=${d}value ;; esac
        |        done < "${d}release_file"
        |        break
        |    done
        |    family=
        |    case "${d}distro" in
        |        alpine) family=alpine ;;
        |        arch|archarm) family=arch ;;
        |        ubuntu|debian|kali) family=debian ;;
        |    esac
        |    if [ -z "${d}family" ]; then
        |        case " ${d}related " in
        |            *" alpine "*) family=alpine ;;
        |            *" arch "*|*" archarm "*) family=arch ;;
        |            *" debian "*|*" ubuntu "*) family=debian ;;
        |        esac
        |    fi
        |    printf '%s\n' '$setup'
        |    case "${d}family" in
        |        arch) printf '%s\n' 'Arch Linux / Arch Linux ARM:' '  pacman -S --needed translate-shell' ;;
        |        alpine) printf '%s\n' 'Alpine Linux:' '  apk update && apk add translate-shell' '$alpineRepo' ;;
        |        debian)
        |            printf '%s\n' 'Ubuntu / Kali / Debian:' '  apt-get update && apt-get install translate-shell'
        |            case " ${d}distro ${d}related " in *" ubuntu "*) printf '%s\n' '$ubuntuRepo' ;; esac
        |            ;;
        |        *)
        |            printf '%s\n' '$unknown' \
        |                'Arch Linux / Arch Linux ARM: pacman -S --needed translate-shell' \
        |                'Alpine Linux: apk update && apk add translate-shell' \
        |                'Ubuntu / Kali / Debian: apt-get update && apt-get install translate-shell'
        |            ;;
        |    esac
        |)
    """.trimMargin()
}
