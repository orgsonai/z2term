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
 * val usage = t(en = "usage: z2-toast <message>", ja = "usage: z2-toast <メッセージ>")
 * ```
 *
 * ⭐ **3 言語目はここに足す。** 名前つき引数の後ろへ `"<コード>" to "…"` を並べるだけ:
 *
 * ```kotlin
 * t(
 *     en = "usage: z2-toast <message>",
 *     ja = "usage: z2-toast <メッセージ>",
 *     "zh-CN" to "用法: z2-toast <消息>",
 * )
 * ```
 *
 * ⚠ **全部の文言を埋めなくてよい。** 挙げていない言語は英語 ([AppLanguages.FALLBACK]) が出る。
 * ⛔ **「英語ではない = 日本語」と書かないこと** — 3 言語目に日本語が出る。
 * 0.8.421 まで `z2-macro` と `pacman-keyring` が実際にその形だった。
 * 埋まり具合は `scripts/i18n-status.sh` が数える。
 *
 * ⚠ **ロジックは 1 つのまま**にするのが要点（[Z2ApiMsg] のヘッダと同じ思想）。
 * ここで持ち替えるのは**文言だけ**で、スクリプトの制御フローは言語に関係なく 1 本。
 * スクリプト全体を言語の数だけ持つと、片方だけ直して挙動がズレる（そして端末でしか気付けない）。
 *
 * ⚠ **ここは名簿 ([AppLanguages]) で受け取る値を絞らない。** 渡された言語コードをそのまま見て、
 * 変わり値が無ければ英語へ落とすだけ。「その言語をアプリが選べるか」は
 * [com.zerotoship.z2term.settings.LocaleHelper] の仕事で、二重に判断すると
 * 「名簿に足したのに文言が出ない」「文言を足したのに名簿に無い」の切り分けができなくなる。
 *
 * @param lang 呼び出し側が持っている言語コード (`ja` / `en` / `zh-CN` …)。
 */
internal class CliText(val lang: String) {

    /** 日本語かどうか。⚠ 「日本語以外 = 英語」ではないので、文言の選択に使わないこと。 */
    val isJa: Boolean get() = lang == "ja"

    /**
     * 言語ごとの文言から 1 つ選ぶ。
     *
     * @param en 英語。**変わり値の無い言語ではこれが出る**ので、必ず埋める。
     * @param ja 日本語。
     * @param more それ以外の言語 (`"zh-CN" to "…"`)。挙げなければ英語に落ちる。
     */
    operator fun invoke(en: String, ja: String, vararg more: Pair<String, String>): String =
        pick(en, ja, more)

    /**
     * 複数行をまとめて選ぶ。行ごとに [invoke] を書くと、どの行がどの言語か追えなくなるため。
     *
     * ```kotlin
     * t.lines(
     *     en = listOf("# starter macro", "# runs once and exits"),
     *     ja = listOf("# 入門用マクロ", "# 起きたときに 1 回走って終わる"),
     * ).forEach { appendLine(it) }
     * ```
     *
     * ⚠ **言語ごとに行数が違ってよい。** 訳すと 1 行に収まらないことがあるので、
     * 行数を揃える決まりは作らない（揃えようとすると訳が不自然になる）。
     */
    fun lines(
        en: List<String>,
        ja: List<String>,
        vararg more: Pair<String, List<String>>
    ): List<String> = pick(en, ja, more)

    /**
     * 文字列でないもの（桁数など）を言語で選ぶ。
     * ⚠ **文言には [invoke] を使うこと** — ここを文言に使うと `scripts/i18n-status.sh` の
     * 数え上げから漏れる。
     */
    fun <T> of(en: T, ja: T, vararg more: Pair<String, T>): T = pick(en, ja, more)

    /**
     * ⚠ 落とし先は必ず [en]。ここを `ja` にすると、訳の無い言語に日本語が出る。
     * 変わり値 ([more]) を先に見るので、`en`/`ja` を [more] で上書きすることもできる
     * （地域差を入れたくなったときの逃げ道）。
     */
    private fun <T> pick(en: T, ja: T, more: Array<out Pair<String, T>>): T =
        more.firstOrNull { it.first == lang }?.second
            ?: if (lang == "ja") ja else en
}
