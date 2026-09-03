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
        for (bonus in listOf(3000, 4500, 6000, 7500, 8500)) {
            withLearned("びるど", "ビルド", bonus) {
                val b = KkcConverter.bunsetsu("びるど")
                println("bonus=$bonus -> size=${b.size} $b")
            }
        }
        // ⭐ **1 度確定すれば効く** (count=1 相当 = 3000)。以前は合成ノードのコスト基準が
        //   UNK_COST だったため、ボーナスが上限でも辞書分割に勝てない読みが残っていた。
        withLearned("びるど", "ビルド", 3000) {
            val b = KkcConverter.bunsetsu("びるど")
            assertEquals("一度確定した塊は 1 ブロックへ繋ぎ止まる", 1, b.size)
            assertEquals("ビルド", b[0].second)
        }
        // 高頻度 (count>=4 相当 = 8500) でも当然 1 ブロックのまま。
        withLearned("びるど", "ビルド", 8500) {
            val b = KkcConverter.bunsetsu("びるど")
            assertEquals("high-frequency block must merge to one bunsetsu", 1, b.size)
            assertEquals("ビルド", b[0].second)
        }
        // 未学習は辞書どおり分割のまま (未学習 = learnedBlock が null。これが実装の契約で、
        // 「学習はあるが下げ幅 0」という状態は [ImeHistoryStore.learnedBlock] からは返らない)。
        val prev = KkcConverter.learnedBlock
        KkcConverter.learnedBlock = null
        try {
            assertTrue(KkcConverter.bunsetsu("びるど").size >= 2)
        } finally {
            KkcConverter.learnedBlock = prev
        }
    }

    /**
     * ⚠ **学習した塊が「別の語の途中」へ割り込まないこと** の回帰テスト。
     *
     * 学習ブロックはラティスへ合成ノードとして入るが、そのノードは接続文脈を持たない
     * (lc=rc=0 の BOS/EOS 近似) ため、**コストを下げすぎるとどこへでも安く繋がる**。
     * その状態で塊を 1 つ覚えると、その読みを先頭に含む別語 (辞書が 1 語で正しく変換できて
     * いたもの) が軒並み崩れる。[KkcConverter] のコスト下限はこれを防ぐために置いてある。
     *
     * ここでは「4 文字の平仮名の塊を最大頻度で学習しても、その塊を先頭に含む別語の変換が
     * 変わらない」ことを確認する。
     */
    @Test
    fun learned_block_does_not_cut_into_other_words() {
        val block = "わかると"
        // 学習が効くべきケース: 塊 + 別の文節。
        withLearned(block, block, 8500) {
            assertEquals(
                "学習した塊は後続が変わっても使い回される",
                "わかると便利です",
                KkcConverter.nbest("わかるとべんりです", 1).firstOrNull()?.surface,
            )
        }
        // 壊してはいけないケース: 塊の読みを先頭に含むが、辞書が 1 語で変換できる別語。
        for ((reading, expected) in listOf(
            "わかるところ" to "分かるところ",
            "わかるとき" to "分かるとき",
            "わかるとしても" to "分かるとしても",
        )) {
            withLearned(block, block, 8500) {
                assertEquals(
                    "学習した塊が別語の途中へ割り込んではいけない: $reading",
                    expected,
                    KkcConverter.nbest(reading, 1).firstOrNull()?.surface,
                )
            }
        }
    }
}
