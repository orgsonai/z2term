package com.zerotoship.z2term.ui.terminal.keyboard

/**
 * 共起 (コロケーション) で N-best 候補を並べ替えるリランカー (mozc の `CollocationRewriter` 相当)。
 * Phase 4 / ギャップ G3。
 *
 * Viterbi はクラス bigram (品詞接続) しか持たないため、`みずをのむ` で `瑞を飲む` (瑞 6052 < 水 7385)
 * のように稀語が常用語に勝つ。本リランカーは候補表層を内容語核の列に分解し、隣接する核ペアが
 * Wikipedia 由来の共起集合 ([ExistenceFilter]) にあれば、その候補のコストからボーナスを引いて昇格させる。
 * 「水＋飲」は共起するが「瑞＋飲」は共起しないので、水を飲む が選ばれる。
 *
 * 候補表層のトークン化は [scripts/build-collocation.sh] (Python 抽出側) と**同一規則**:
 *   - 内容語核 = 漢字/カタカナ/々/ー の連続
 *   - glue     = ひらがなの連続。1〜[MAX_GLUE] 文字までを「同一局所」とみなしペアにする
 *   - それ以外 = 連鎖を断つ
 * 学習・推論で同じ分解にすることで集合の引き当てが一致する。
 *
 * [filterProvider] で [ExistenceFilter] を遅延取得するため、Kkc の共起アセット読込との順序に
 * 依存しない。JVM テストでは固定の filter を返すプロバイダを渡す。
 */
class CollocationReranker(
    private val filterProvider: () -> ExistenceFilter?,
    private val bonus: Int = DEFAULT_BONUS,
) : KkcConverter.KkcReranker {

    override fun rerank(
        reading: String,
        candidates: List<KkcConverter.Candidate>,
        context: KkcConverter.KkcContext,
    ): List<KkcConverter.Candidate> {
        if (candidates.size < 2) return candidates
        val filter = filterProvider() ?: return candidates
        // 共起ボーナスをコストに反映した新候補を作り、安定ソート (同点は元順 = Viterbi 順を保つ)。
        // コストへ畳み込むことで後段リランカー (HistoryReranker) と素直に合成できる。
        return candidates
            .map { c ->
                val m = matchedPairs(c.surface, filter)
                if (m == 0) c else KkcConverter.Candidate(c.surface, c.segments, c.cost - m * bonus)
            }
            .sortedBy { it.cost }
    }

    /** 候補表層を内容語核の列に分解し、共起集合にある隣接ペア数を数える。 */
    private fun matchedPairs(surface: String, filter: ExistenceFilter): Int {
        var matched = 0
        var prev: String? = null
        var glue = 0
        val core = StringBuilder()
        fun flush() {
            if (core.isEmpty()) return
            val cur = core.toString()
            core.setLength(0)
            val p = prev
            if (p != null && glue in 1..MAX_GLUE && filter.mayContain("$p\t$cur")) matched++
            prev = cur
            glue = 0
        }
        for (ch in surface) {
            when {
                isContent(ch) -> core.append(ch)
                isHira(ch) -> {
                    flush()
                    if (prev != null) {
                        glue++
                        if (glue > MAX_GLUE) { prev = null; glue = 0 }
                    }
                }
                else -> { flush(); prev = null; glue = 0 }
            }
        }
        // 末尾核は次語が無いのでペアにしない (flush しても prev に置くだけ)。
        return matched
    }

    private fun isContent(c: Char): Boolean {
        val o = c.code
        return o in 0x4E00..0x9FFF || o in 0x3400..0x4DBF ||
            o in 0x30A1..0x30FA || o == 0x30FC || o == 0x3005
    }

    private fun isHira(c: Char): Boolean = c.code in 0x3041..0x3096 || c.code == 0x3094

    companion object {
        /** 共起ペア 1 件あたりのコストボーナス。Phase 0 評価で調整。 */
        const val DEFAULT_BONUS = 2500
        const val MAX_GLUE = 4
    }
}

/**
 * 複数のリランカーを順に適用する合成リランカー。前段の並べ替え/コスト調整結果を次段に渡す。
 * Phase 4 では `[CollocationReranker] → [HistoryReranker]` の順で、ユーザ学習 (明示的選択) を
 * 共起より優先させる。
 */
class CompositeReranker(
    private val rerankers: List<KkcConverter.KkcReranker>,
) : KkcConverter.KkcReranker {
    override fun rerank(
        reading: String,
        candidates: List<KkcConverter.Candidate>,
        context: KkcConverter.KkcContext,
    ): List<KkcConverter.Candidate> {
        var cur = candidates
        for (r in rerankers) cur = r.rerank(reading, cur, context)
        return cur
    }
}
