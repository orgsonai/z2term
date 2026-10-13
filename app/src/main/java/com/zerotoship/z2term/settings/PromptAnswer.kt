package com.zerotoship.z2term.settings

/**
 * 画面なしで走るコマンドが**端末へ聞いてきたとき**の返事 1 組 (1.0.12)。
 *
 * 出力の最後の行が [prompt] で終わったら [text] と Enter を送る。`[y/N]` の確認にも、
 * パスワードにも同じ仕組みで答える。
 *
 * [secret] は**入力を隠す返事** (パスワード)。ルールファイルには [prompt] だけを書き、
 * 中身はアプリの中に暗号化して置く ([com.zerotoship.z2term.service.WhenSecrets])。
 * 送るのも、端末が打った文字を表示しない状態のときだけ — 表示する状態の端末へ送ると、
 * 返事がそのまま実行ログに残る。
 */
data class PromptAnswer(
    val prompt: String,
    val text: String = "",
    val secret: Boolean = false,
) {
    companion object {
        /** ルールファイルと CLI で、聞かれる文言と返事を分ける印 (`answer=続けますか=>y`)。 */
        const val SEPARATOR = "=>"

        /** `answer=` の値を読む。印が無ければ null。文言が空のものも null (何にでも答えてしまう)。 */
        fun parse(value: String): PromptAnswer? {
            val at = value.lastIndexOf(SEPARATOR)
            if (at < 0) return null
            val prompt = value.substring(0, at).trim()
            if (prompt.isEmpty()) return null
            return PromptAnswer(prompt, value.substring(at + SEPARATOR.length))
        }
    }
}
