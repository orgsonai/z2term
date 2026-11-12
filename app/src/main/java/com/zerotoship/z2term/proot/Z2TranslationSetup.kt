package com.zerotoship.z2term.proot

/** Prints manual setup only. Read OS metadata as data, never as shell commands. */
internal fun z2TranslationSetup(lang: String): String {
    val t = CliText(lang)
    val d = '$'
    val setup = t("translationSetup.setup", en = "Open a terminal in the same local Linux environment, run the command below yourself, then retry translation.",
        ja = "同じローカルLinux環境のターミナルで、次のコマンドをユーザー自身で実行してください。導入後、翻訳を再実行してください。")
    val alpineRepo = t("translationSetup.alpineRepo", en = "If the package is not found, enable the community repository for the same Alpine release in /etc/apk/repositories.",
        ja = "パッケージが見つからない場合は、/etc/apk/repositories で同じAlpine版の community リポジトリを有効にしてください。")
    val ubuntuRepo = t("translationSetup.ubuntuRepo", en = "If the package is not found on Ubuntu, enable its universe repository and retry.",
        ja = "Ubuntuでパッケージが見つからない場合は、universe リポジトリを有効にして再実行してください。")
    val unknown = t("translationSetup.unknown", en = "Distribution not recognized. Choose the command for your local Linux environment:",
        ja = "ディストリを判別できませんでした。使用中のローカルLinux環境に合うコマンドを選んでください。")

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
