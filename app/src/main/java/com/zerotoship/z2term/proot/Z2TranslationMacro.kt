package com.zerotoship.z2term.proot

/** Our wrapper only: the external translator is installed separately by the user. */
fun z2TranslationMacro(lang: String): String {
    val t = CliText(lang)
    val d = '$'
    val description = t("translationMacro.description", en = "Translate text using a separately installed command.", ja = "別途導入したコマンドで文章を翻訳します。")
    val missing = t("translationMacro.missing", en = "The trans command is not installed in this local environment. Install your translation CLI yourself, then retry. Nothing is downloaded automatically.",
        ja = "このローカル環境には trans コマンドがありません。翻訳CLIをユーザー自身で導入してから再実行してください。自動ダウンロードは行いません。")
    val network = t("translationMacro.network", en = "Translation text is sent to the external service used by your CLI. Its terms and network availability apply.",
        ja = "翻訳文はCLIが利用する外部サービスへ送信されます。接続先の利用条件・通信状況に従います。")
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
