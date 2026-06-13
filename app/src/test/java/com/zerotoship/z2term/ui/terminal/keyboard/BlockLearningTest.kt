package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 動的ブロック分割 (頻用の塊を 1 ブロックへ繋ぎ止める学習) の検証。
 *
 * 「びるど」は kkc_lex に無く、辞書上は びる(ビル)+ど(ド) に分割される。ユーザーが
 * 何度も「ビルド」と確定した塊は [KkcConverter.learnedBlock] のボーナスで 1 ブロックへ
 * まとまるべき。本テストは実 lex/matrix を使い「頻度 (=ボーナス) で分割が解消する」ことと
 * 「未学習では分割のまま」を確認する。ボーナス値は [ImeHistoryStore] の
 * BLOCK_BASE_BONUS(3000)+BLOCK_COUNT_STEP(1500)*(count-1)+recency(0..1000) に対応。
 */
class BlockLearningTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun load() {
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

    private inline fun withLearned(reading: String, surface: String, bonus: Int, body: () -> Unit) {
        val prev = KkcConverter.learnedBlock
        KkcConverter.learnedBlock = { r -> if (r == reading) surface to bonus else null }
        try {
            body()
        } finally {
            KkcConverter.learnedBlock = prev
        }
    }

    @Test
    fun baseline_splits_biludo() {
        val prev = KkcConverter.learnedBlock
        KkcConverter.learnedBlock = null
        try {
            val b = KkcConverter.bunsetsu("びるど")
            println("baseline bunsetsu(びるど) = $b")
            assertTrue("baseline should split into >=2 blocks", b.size >= 2)
        } finally {
            KkcConverter.learnedBlock = prev
        }
    }

    @Test
    fun frequency_merges_biludo() {
        // 閾値把握用のスイープ (count=1..4 相当 + recency 上振れ)。
        for (bonus in listOf(0, 3000, 4500, 6000, 7500, 8500)) {
            withLearned("びるど", "ビルド", bonus) {
                val b = KkcConverter.bunsetsu("びるど")
                println("bonus=$bonus -> size=${b.size} $b")
            }
        }
        // 高頻度 (count>=4 相当 = 8500) では必ず 1 ブロック「ビルド」へ繋ぎ止まる。
        withLearned("びるど", "ビルド", 8500) {
            val b = KkcConverter.bunsetsu("びるど")
            assertEquals("high-frequency block must merge to one bunsetsu", 1, b.size)
            assertEquals("ビルド", b[0].second)
        }
        // 未学習 (bonus=0) は辞書どおり分割のまま。
        withLearned("びるど", "ビルド", 0) {
            assertTrue(KkcConverter.bunsetsu("びるど").size >= 2)
        }
    }
}
