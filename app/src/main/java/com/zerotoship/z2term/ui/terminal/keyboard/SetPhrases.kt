package com.zerotoship.z2term.ui.terminal.keyboard

/**
 * 定型文 (1.0.140)。あいさつ・返事のように、決まった形でよく打つ言い回し。
 *
 * よく使われる語の表 ([PredictionDict]) は文章から集めたので、話し言葉のあいさつがほぼ入っていない。
 * そこで自作の短い一覧を持たせる (外部の一覧は使っていない)。予測変換が ON のときだけ使う:
 *  - 読みを打っている途中の補完 (よろ → よろしくお願いします)。
 *  - 何も打っていないときの候補バー ([ComposingState] の確定後・Enter の後・開いた直後・⌫ の後)。
 */
internal object SetPhrases {

    /** 何も打っていないときの候補の最後に置く記号 (1.0.168)。語より前に出すと語が隠れる。 */
    val symbols: List<String> = listOf("、", "。", "？", "！", "…", "ー", "・")

    /** (読み, 表記)。並びは候補バーに出す順。 */
    val all: List<Pair<String, String>> = listOf(
        "ありがとうございます" to "ありがとうございます",
        "よろしくおねがいします" to "よろしくお願いします",
        "おつかれさまです" to "お疲れ様です",
        "おねがいします" to "お願いします",
        "おはようございます" to "おはようございます",
        "すみません" to "すみません",
        "りょうかいです" to "了解です",
        "しょうちしました" to "承知しました",
        "だいじょうぶです" to "大丈夫です",
        "こんにちは" to "こんにちは",
        "こんばんは" to "こんばんは",
        "おやすみなさい" to "おやすみなさい",
    )

    private const val MIN_PREFIX = 2

    /** 句読点や記号だけでできた候補か。「ー」は文字扱いなので [symbols] でも見る。 */
    fun isSymbol(word: String): Boolean = word in symbols || word.none { it.isLetterOrDigit() }

    /** 何も打っていないときの候補: よく確定している語 → 定型文 → 記号。 */
    fun idleCandidates(frequentWords: List<String>): List<String> =
        (frequentWords + all.map { it.second } + symbols).distinct()

    /**
     * 確定直後に出す次の言葉: 学習した語 → 続きやすい語 → 記号。
     * 句読点を確定した回数が多くても語より前には出さない。記号しか無いときは空を返す
     * (何も打っていないときの候補に任せる)。
     */
    fun orderNextWords(learned: List<String>, table: List<String>, limit: Int): List<String> {
        val (marks, words) = (learned + table).distinct().partition { isSymbol(it) }
        return if (words.isEmpty()) emptyList() else (words + marks).take(limit)
    }

    /** 打った読み [prefix] で始まり、それより長い定型文。2 文字から。 */
    fun predictWithReading(prefix: String): List<Pair<String, String>> {
        if (prefix.length < MIN_PREFIX) return emptyList()
        return all.filter { (r, _) -> r.length > prefix.length && r.startsWith(prefix) }
    }
}
