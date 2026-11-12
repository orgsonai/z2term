package com.zerotoship.z2term.proot

import com.zerotoship.z2term.settings.AppLanguages

/**
 * **端末に出る文言**（`z2-*` CLI のヘルプ・usage・エラー、同梱スクリプトの表示、
 * サンプルマクロのコメント）を言語ごとに選ぶための小さな入れ物。
 *
 * アプリの**画面**は `res/values-<言語>/` が面倒を見るが、端末に出る文言は rootfs へ
 * 書き出すシェルスクリプトの中身なので res では持てない。ここが res の代わり。
 *
 * ## 使い方
 *
 * ```kotlin
 * val t = CliText(lang)
 * val usage = t("toast.usage", en = "usage: z2-toast <message>", ja = "usage: z2-toast <メッセージ>")
 * ```
 *
 * ⭐ **英語と日本語だけをここ (使う場所) に書く。** それ以外の言語の訳は
 * `proot/lang/Cli<言語>.kt` に **1 言語 = 1 ファイル**で置き、先頭の名前 (`"toast.usage"`) で引く
 * ([CliLanguage])。⚠ 1.0.83 までは 6 言語の訳を呼び出しのたびに並べていて、言語を足すほど
 * 文言を使う側のファイルが訳で埋まり、処理が読めなくなっていた（`Z2MacroScript.kt` は 6,384 行）。
 *
 * ⚠ **名前は全体で重ならないこと。** `<ファイルの短い名前>.<変数名>` の形で付けている
 * (`doctor.head` / `macro.rssBody.head`)。重なりは `scripts/i18n-status.sh --check` が落とす。
 * ⚠ **文言を書き換えても名前は変えない** — 変えると全言語の訳が外れて英語に戻る。
 * 逆に**意味が変わったなら名前を変える**（古い訳が新しい意味の文言に出続けるのを防ぐ）。
 *
 * ⚠ **全部の文言を埋めなくてよい。** 訳の無い言語は英語 ([AppLanguages.FALLBACK]) が出る。
 * ⛔ **「英語ではない = 日本語」と書かないこと** — 3 言語目に日本語が出る。
 * 0.8.421 まで `z2-macro` と `pacman-keyring` が実際にその形だった。
 * 埋まり具合は `scripts/i18n-status.sh` が数える。
 *
 * ⚠ **ロジックは 1 つのまま**にするのが要点（[Z2ApiMsg] のヘッダと同じ思想）。
 * ここで持ち替えるのは**文言だけ**で、スクリプトの制御フローは言語に関係なく 1 本。
 * スクリプト全体を言語の数だけ持つと、片方だけ直して挙動がズレる（そして端末でしか気付けない）。
 *
 * ⚠ **ここは名簿 ([AppLanguages]) で受け取る値を絞らない。** 渡された言語コードをそのまま見て、
 * 訳が無ければ英語へ落とすだけ。「その言語をアプリが選べるか」は
 * [com.zerotoship.z2term.settings.LocaleHelper] の仕事で、二重に判断すると
 * 「名簿に足したのに文言が出ない」「文言を足したのに名簿に無い」の切り分けができなくなる。
 *
 * @param lang 呼び出し側が持っている言語コード (`ja` / `en` / `zh-CN` …)。
 * @param other その言語の訳 (英語・日本語では null)。テストで差し替えるための口。
 */
internal class CliText(
    val lang: String,
    private val other: CliLanguage? = CliLanguages.of(lang),
) {

    /** 日本語かどうか。⚠ 「日本語以外 = 英語」ではないので、文言の選択に使わないこと。 */
    val isJa: Boolean get() = lang == "ja"

    /**
     * 名前 [key] の文言を選ぶ。
     *
     * @param key 訳を引く名前。言語ファイルの `texts` に同じ名前で訳を置く。
     * @param en 英語。**訳の無い言語ではこれが出る**ので、必ず埋める。
     * @param ja 日本語。
     */
    operator fun invoke(key: String, en: String, ja: String): String =
        pick(en, ja) { it.texts[key] }

    /**
     * 複数行をまとめて選ぶ。行ごとに [invoke] を書くと、どの行がどの言語か追えなくなるため。
     * 訳は言語ファイルの `lineSets` に置く。
     *
     * ```kotlin
     * t.lines("macro.starter",
     *     en = listOf("# starter macro", "# runs once and exits"),
     *     ja = listOf("# 入門用マクロ", "# 起きたときに 1 回走って終わる"),
     * ).forEach { appendLine(it) }
     * ```
     *
     * ⚠ **言語ごとに行数が違ってよい。** 訳すと 1 行に収まらないことがあるので、
     * 行数を揃える決まりは作らない（揃えようとすると訳が不自然になる）。
     */
    fun lines(key: String, en: List<String>, ja: List<String>): List<String> =
        pick(en, ja) { it.lineSets[key] }

    /**
     * 文字列でないもの（桁数など）を言語で選ぶ。訳は言語ファイルの `values` に置く。
     * ⚠ **文言には [invoke] を使うこと** — ここを文言に使うと `scripts/i18n-status.sh` の
     * 数え方が変わる (`values` は文言の数に入れない)。
     * ⚠ 言語ファイルに違う型の値を置くと英語へ落ちる（落ちて止まらないように型で見る）。
     */
    inline fun <reified T> of(key: String, en: T, ja: T): T =
        pickValue(en, ja, key) as? T ?: if (lang == "ja") ja else en

    @PublishedApi
    internal fun pickValue(en: Any?, ja: Any?, key: String): Any? =
        pick(en, ja) { it.values[key] }

    /**
     * ⚠ 落とし先は必ず [en]。ここを `ja` にすると、訳の無い言語に日本語が出る。
     */
    private fun <T> pick(en: T, ja: T, translated: (CliLanguage) -> T?): T =
        if (lang == "ja") ja else other?.let(translated) ?: en
}
