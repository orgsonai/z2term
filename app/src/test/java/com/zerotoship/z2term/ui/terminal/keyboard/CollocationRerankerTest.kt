package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

/**
 * [CollocationReranker] のメカニズム検証。実アセット (`kkc_colloc.bloom`) を使い、
 * 「水＋飲」が共起・「瑞＋飲」が非共起であることを利用して、Viterbi で上位だった
 * `瑞を飲む` が `水を飲む` に逆転することを確かめる。
 */
class CollocationRerankerTest {

    private fun cand(surface: String, cost: Int) =
        KkcConverter.Candidate(surface, emptyList(), cost)

    private fun loadFilter(): ExistenceFilter {
        for (base in listOf(".", "app", "../app")) {
            val f = File(base, "src/main/assets/kkc_colloc.bloom")
            if (f.exists()) return f.inputStream().use { ExistenceFilter.load(it) }
        }
        error("kkc_colloc.bloom not found")
    }

    @Test
    fun cooccurringCandidatePromotedToTop() {
        val filter = loadFilter()
        val r = CollocationReranker({ filter }, bonus = 2500)
        // Viterbi 順では稀語 瑞 が上 (コスト低)。共起ボーナスで 水 が逆転するはず。
        val input = listOf(cand("瑞を飲む", 1000), cand("水を飲む", 1100))
        val out = r.rerank("みずをのむ", input, KkcConverter.KkcContext.EMPTY)
        assertEquals("水を飲む", out.first().surface)
    }

    @Test
    fun noCooccurrenceKeepsViterbiOrder() {
        val filter = loadFilter()
        val r = CollocationReranker({ filter }, bonus = 2500)
        // 単一内容語のみ (公園/高遠 とも後続内容語が無い) → ペアが作れず順序不変。
        val input = listOf(cand("高遠で", 1000), cand("公園で", 1100))
        val out = r.rerank("こうえんで", input, KkcConverter.KkcContext.EMPTY)
        assertEquals("高遠で", out.first().surface)
    }

    @Test
    fun nullFilterReturnsInputUnchanged() {
        val r = CollocationReranker({ null }, bonus = 2500)
        val input = listOf(cand("瑞を飲む", 1000), cand("水を飲む", 1100))
        val out = r.rerank("みずをのむ", input, KkcConverter.KkcContext.EMPTY)
        assertSame(input, out)
    }

    @Test
    fun singleCandidateUnchanged() {
        val filter = loadFilter()
        val r = CollocationReranker({ filter }, bonus = 2500)
        val input = listOf(cand("水を飲む", 1100))
        val out = r.rerank("みずをのむ", input, KkcConverter.KkcContext.EMPTY)
        assertSame(input, out)
    }
}
