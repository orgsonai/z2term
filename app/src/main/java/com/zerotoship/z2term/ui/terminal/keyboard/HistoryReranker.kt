package com.zerotoship.z2term.ui.terminal.keyboard

/**
 * ユーザ確定 bigram (UserHistoryPredictor 相当) で N-best 候補を並べ替えるリランカー。
 *
 * 「直前確定語 ([KkcConverter.KkcContext.prevSurface]) → 当候補の表層」が学習済み bigram に
 * あれば、その候補のコストからボーナスを引いて昇格させる。Phase 1 の N-best ([KkcConverter.nbest])
 * 段に差し込む。
 *
 * [ImeHistoryStore] に直接依存せず [bigramBonus] を関数注入で受けるため、Android Context 無しの
 * JVM ユニットテストで挙動を検証できる。実機では `ImeHistoryStore::bigramBonus` を渡す。
 */
class HistoryReranker(
    private val bigramBonus: (prev: String, next: String) -> Int,
) : KkcConverter.KkcReranker {

    override fun rerank(
        reading: String,
        candidates: List<KkcConverter.Candidate>,
        context: KkcConverter.KkcContext,
    ): List<KkcConverter.Candidate> {
        val prev = context.prevSurface
        if (prev.isNullOrEmpty() || candidates.size < 2) return candidates
        // コスト − bigram ボーナス で安定ソート (sortedBy は安定なので同点は元順 = Viterbi 順を保つ)。
        return candidates.sortedBy { it.cost - bigramBonus(prev, it.surface) }
    }
}
