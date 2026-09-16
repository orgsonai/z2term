package com.zerotoship.z2term.proot

/** Our wrapper only: the external translator is installed separately by the user. */
fun z2TranslationMacro(lang: String): String {
    val t = CliText(lang)
    val d = '$'
    val description = t(en = "Translate text using a separately installed command.", ja = "別途導入したコマンドで文章を翻訳します。",
        "zh-CN" to "使用另行安装的命令翻译文本。", "zh-TW" to "使用另行安裝的命令翻譯文字。",
        "es" to "Traduce texto con un comando instalado por separado.", "ko" to "별도로 설치한 명령으로 텍스트를 번역합니다.")
    val missing = t(en = "The trans command is not installed in this local environment. Install your translation CLI yourself, then retry. Nothing is downloaded automatically.",
        ja = "このローカル環境には trans コマンドがありません。翻訳CLIをユーザー自身で導入してから再実行してください。自動ダウンロードは行いません。",
        "zh-CN" to "此本地环境未安装 trans 命令。请自行安装翻译 CLI 后重试。不会自动下载。",
        "zh-TW" to "此本機環境未安裝 trans 命令。請自行安裝翻譯 CLI 後重試。不會自動下載。",
        "es" to "Falta el comando trans en este entorno local. Instala la CLI de traducción y reintenta. No hay descarga automática.",
        "ko" to "이 로컬 환경에 trans 명령이 없습니다. 번역 CLI를 직접 설치한 뒤 다시 실행하세요. 자동 다운로드는 하지 않습니다.")
    val network = t(en = "Translation text is sent to the external service used by your CLI. Its terms and network availability apply.",
        ja = "翻訳文はCLIが利用する外部サービスへ送信されます。接続先の利用条件・通信状況に従います。",
        "zh-CN" to "翻译文本会发送至 CLI 使用的外部服务，受其条款和网络状态影响。",
        "zh-TW" to "翻譯文字會傳送至 CLI 使用的外部服務，受其條款與網路狀態影響。",
        "es" to "El texto se envía al servicio externo de la CLI. Se aplican sus condiciones y disponibilidad de red.",
        "ko" to "텍스트는 CLI의 외부 서비스로 전송됩니다. 해당 이용 조건과 네트워크 상태가 적용됩니다.")
    val setup = z2TranslationSetup(lang)
    return """
        |#!/bin/sh
        |# translate.sh — $description
        |# Usage: sh ~/.z2term/macros/translate.sh [--] "text" [target=ja] [source=auto]
        |# Example: sh ~/.z2term/macros/translate.sh "Hello" ja auto
        |# $network
        |# $missing
        |# Source: auto or a language code. Target: a language code (ja, en, zh-CN, ...).
        |$setup
        |if [ "${d}#" -eq 0 ] || { [ "${d}#" -eq 1 ] && [ "${d}1" = --help ]; }; then
        |    while IFS= read -r line; do case "${d}line" in '#!'*) ;; '# '*) printf '%s\n' "${d}{line#\# }" ;; *) break ;; esac; done < "${d}0"
        |    exit 0
        |fi
        |[ "${d}{1:-}" != -- ] || shift
        |[ "${d}#" -ge 1 ] && [ "${d}#" -le 3 ] && [ -n "${d}1" ] || { printf '%s\n' 'Usage: translate.sh [--] "text" [target=ja] [source=auto]' >&2; exit 2; }
        |text=${d}1
        |# The external CLI interprets leading file/web URLs even after --; refuse those instead of reading files.
        |case "${d}text" in file://*|http://*|https://*) printf '%s\n' 'Enter plain text, not a file or web URL' >&2; exit 2 ;; esac
        |target=${d}{2:-ja}
        |source=${d}{3:-auto}
        |for code in "${d}target" "${d}source"; do
        |    case "${d}code" in ''|[!A-Za-z]*|*[!A-Za-z0-9-]*) printf '%s\n' 'Invalid language code' >&2; exit 2 ;; esac
        |done
        |[ "${d}target" != auto ] || { printf '%s\n' 'Choose a target language (for example: ja or en)' >&2; exit 2; }
        |command -v trans >/dev/null 2>&1 || { printf '%s\n' '$missing' >&2; translation_setup >&2; exit 127; }
        |# Keep text out of option parsing, shell evaluation, startup scripts, pagers and audio.
        |unset SOURCE_LANG TARGET_LANG
        |set -- -no-init -brief -no-ansi -no-bidi -no-play -no-browser -no-pager -t "${d}target"
        |[ "${d}source" = auto ] || set -- "${d}@" -s "${d}source"
        |exec trans "${d}@" -- "${d}text"
        |
    """.trimMargin()
}
