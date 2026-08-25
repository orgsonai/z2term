package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 頻度優先 (0.8.398) の検証。
 *
 * 「よく使う語ほど前に出す」ための unigram ボーナス ([KkcConverter.unigramBonus]) が、
 * **単語 1 つの変換だけでなく文の中でも**効くことを実 lex/matrix で確認する。
 *
 * 背景: [ImeHistoryStore.learnedBlock] は読み完全一致の塊にしか効かず、しかも 2 文字以上の
 * 読み限定だったため、**長文の途中に出てくる頻用語 (とくに 1 文字の漢字) は何度使っても
 * 順位が変わらなかった** (利用者指摘)。
 */
class UnigramLearningTest {
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

    /** `(読み, 表層)` にだけボーナスを与えて body を走らせる。他テストへ漏らさないよう必ず戻す。 */
    private inline fun withUnigram(reading: String, surface: String, bonus: Int, body: () -> Unit) {
        val prev = KkcConverter.unigramBonus
        KkcConverter.unigramBonus = { r, s -> if (r == reading && s == surface) bonus else 0 }
        try {
            body()
        } finally {
            KkcConverter.unigramBonus = prev
        }
    }

    /** ボーナス値と count の対応 (ImeHistoryStore の UNIGRAM_*)。 */
    private fun bonusFor(count: Int): Int = 1200 + 800 * (count.coerceAtMost(8) - 1)

    @Test
    fun without_learning_sentence_is_unchanged() {
        val prev = KkcConverter.unigramBonus
        KkcConverter.unigramBonus = null
        try {
            // 未学習では辞書コストのまま。⚠ ここが「今日」になっていたら、頻度優先ではなく
            // 辞書側が変わっている (下のテストが意味を失う)。
            assertEquals("教は雨が降る日だ", top("きょうはあめがふるひだ"))
            assertEquals("噺を聞くときはメモを取る", top("はなしをきくときはめもをとる"))
        } finally {
            KkcConverter.unigramBonus = prev
        }
    }

    @Test
    fun one_commit_lifts_word_inside_sentence() {
        // ⭐ 要望の本体: 単語 1 つの変換ではなく**文の中**で順位が上がること。
        // 「きょう→今日」は 1 回選べば次から文中でも勝つ (count=1)。
        withUnigram("きょう", "今日", bonusFor(1)) {
            assertEquals("今日は雨が降る日だ", top("きょうはあめがふるひだ"))
        }
    }

    @Test
    fun repeated_commits_beat_dictionary_cost() {
        // 辞書コストの差が大きい語は 1 回では足りず、使い込むと勝つ (count=3 相当)。
        withUnigram("はなし", "話", bonusFor(1)) {
            assertEquals("噺を聞くときはメモを取る", top("はなしをきくときはめもをとる"))
        }
        withUnigram("はなし", "話", bonusFor(3)) {
            assertEquals("話を聞くときはメモを取る", top("はなしをきくときはめもをとる"))
        }
    }

    @Test
    fun single_kanji_lifts_inside_sentence() {
        // ⭐ 1 文字の漢字も文中で上がる。これが上がらないのが「何度使っても前に来ない」の
        // 主因だった (学習側は isLearnableWord で 1 文字漢字を覚えるようにしてある)。
        withUnigram("とき", "時", bonusFor(3)) {
            assertEquals("噺を聞く時はメモを取る", top("はなしをきくときはめもをとる"))
        }
    }

    @Test
    fun kana_preferred_word_needs_many_commits() {
        // 平仮名で書くのが普通な語 (KANA_PREFERRED_PENALTY=4000) は **1 回では覆らない**。
        // 何度も自分で選んだときだけ既定を上書きできる (count=8 相当)。
        withUnigram("もの", "物", bonusFor(1)) {
            assertEquals("この問題は難しいものだ", top("このもんだいはむずかしいものだ"))
        }
        withUnigram("もの", "物", bonusFor(8)) {
            assertEquals("この問題は難しい物だ", top("このもんだいはむずかしいものだ"))
        }
    }

    @Test
    fun bonus_never_exceeds_block_learning() {
        // 塊の繋ぎ止め (BLOCK_* 最大 8500) より強くしない。逆転すると覚えた分割が崩れる。
        assertTrue(bonusFor(8) + 500 < 8500)
    }

    private fun top(reading: String): String? =
        KkcConverter.nbest(reading, 1).firstOrNull()?.surface

    @Test
    fun isLearnableWord_keeps_kanji_drops_kana() {
        // 1 文字でも漢字・カタカナは覚える (よく使う漢字が上がらない主因だった)。
        assertTrue(ImeHistoryStore.isLearnableWord("時"))
        assertTrue(ImeHistoryStore.isLearnableWord("々"))
        assertTrue(ImeHistoryStore.isLearnableWord("ト"))
        // 単打のひらがな・記号・英数字は覚えない (助詞で履歴が埋まるのを防ぐ)。
        assertFalse(ImeHistoryStore.isLearnableWord("の"))
        assertFalse(ImeHistoryStore.isLearnableWord("、"))
        assertFalse(ImeHistoryStore.isLearnableWord("a"))
        assertFalse(ImeHistoryStore.isLearnableWord(""))
        // 2 文字以上は従来どおり無条件。
        assertTrue(ImeHistoryStore.isLearnableWord("かな"))
        assertTrue(ImeHistoryStore.isLearnableWord("聴く"))
    }
}
