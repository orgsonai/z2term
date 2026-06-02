package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Phase 2: ユーザ確定 bigram リランク ([HistoryReranker]) の検証。
 *
 * [ImeHistoryStore] は Android Context (filesDir) に依存し JVM テストに乗らないため、bigram
 * ボーナスを関数注入したリランカーを直接検証する。最後に N-best 経路への組込みも確認する。
 */
class HistoryRerankerTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadKkc() {
            if (KkcConverter.loaded) return
            val matrix = locate("src/main/assets/kkc_matrix.bin")
            val lex = locate("src/main/assets/kkc_lex.tsv")
            matrix.inputStream().use { ms ->
                lex.bufferedReader(Charsets.UTF_8).use { lr ->
                    KkcConverter.loadFromStreams(ms, lr)
                }
            }
        }

        private fun locate(rel: String): File {
            for (base in listOf(".", "app", "../app")) {
                val f = File(base, rel)
                if (f.exists()) return f
            }
            error("asset not found: $rel (cwd=${File(".").absolutePath})")
        }
    }

    /** 学習済み bigram (前語→当語) の候補がコストボーナスでトップに昇格する。 */
    @Test
    fun learnedBigramPromotesCandidate() {
        val reranker = HistoryReranker { prev, next ->
            if (prev == "わたしは" && next == "林檎を食べる") 2000 else 0
        }
        val cands = listOf(
            KkcConverter.Candidate("リンゴを食べる", emptyList(), 1000),
            KkcConverter.Candidate("林檎を食べる", emptyList(), 1500),
        )
        // 前語コンテキストありなら 1500-2000 = -500 < 1000 で昇格。
        val withCtx = reranker.rerank("x", cands, KkcConverter.KkcContext("わたしは"))
        assertEquals("林檎を食べる", withCtx.first().surface)
    }

    /** 前語コンテキストが無い (null) 場合は Viterbi コスト順のまま。 */
    @Test
    fun noContextKeepsViterbiOrder() {
        val reranker = HistoryReranker { _, _ -> 9999 }
        val cands = listOf(
            KkcConverter.Candidate("A", emptyList(), 1000),
            KkcConverter.Candidate("B", emptyList(), 1500),
        )
        val out = reranker.rerank("x", cands, KkcConverter.KkcContext(null))
        assertEquals(listOf("A", "B"), out.map { it.surface })
    }

    /** N-best 経路 (KkcConverter.nbest) に組込んだとき、下位候補が context で 1 位へ入れ替わる。 */
    @Test
    fun rerankerReordersNbestEndToEnd() {
        val reading = "はしをわたる"
        val base = KkcConverter.nbest(reading, 5)
        assumeTrue("需要 >=2 候補", base.size >= 2)
        val target = base[1].surface
        assertTrue("1 位と 2 位は別表層のはず", target != base[0].surface)

        val saved = KkcConverter.reranker
        try {
            KkcConverter.reranker = HistoryReranker { _, next -> if (next == target) 100_000 else 0 }
            val reranked = KkcConverter.nbest(reading, 5, KkcConverter.KkcContext("だれかが"))
            assertEquals(target, reranked.first().surface)
            // context が無ければ元の 1 位のまま。
            val plain = KkcConverter.nbest(reading, 5, KkcConverter.KkcContext(null))
            assertEquals(base[0].surface, plain.first().surface)
        } finally {
            KkcConverter.reranker = saved
        }
    }
}
