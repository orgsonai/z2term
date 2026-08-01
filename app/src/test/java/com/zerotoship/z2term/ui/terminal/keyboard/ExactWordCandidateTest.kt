package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 「読みに完全一致する 1 語」が候補一覧に必ず並ぶことの回帰テスト。
 *
 * N-best は文としての経路を上位 k 本しか返さないため、同じ読みの 1 語候補が順位争いに埋もれて
 * **一度も候補に出ない**ことがあった (とく → 説く/解く/溶く が出せず、代わりに稀な単漢字が
 * 枠を埋めていた)。辞書に在る語が選べないのは変換の穴なので、
 * [KkcConverter.wordsFor] 経由で候補へ入ることを固定する。
 */
class ExactWordCandidateTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun load() {
            if (!KkcConverter.loaded) {
                locate("src/main/assets/kkc_matrix.bin").inputStream().use { ms ->
                    locate("src/main/assets/kkc_lex.tsv").bufferedReader(Charsets.UTF_8).use { lr ->
                        KkcConverter.loadFromStreams(ms, lr)
                    }
                }
            }
            if (!KanaKanjiConverter.loaded) {
                locate("src/main/assets/z2dict.txt").bufferedReader(Charsets.UTF_8)
                    .use { KanaKanjiConverter.loadFrom(it) }
            }
        }

        private fun locate(rel: String): File {
            for (b in listOf(".", "app", "../app")) {
                val f = File(b, rel)
                if (f.exists()) return f
            }
            error("asset not found: $rel (cwd=${File(".").absolutePath})")
        }
    }

    @Test
    fun wordsForReturnsHomophonesInCostOrder() {
        val words = KkcConverter.wordsFor("とく")
        for (expected in listOf("説く", "解く", "溶く", "得", "特")) {
            assertTrue("wordsFor(とく) に $expected が無い: $words", expected in words)
        }
    }

    @Test
    fun candidatesContainVerbFormsForTwoMoraReading() {
        // 「とく」を打って「説く」が選べる (旧実装では候補一覧に一度も現れなかった)。
        val toku = KanaKanjiConverter.convertFlexible("とく")
        assertTrue("とく の候補に 説く が無い: $toku", "説く" in toku)
        assertTrue("とく の候補に 解く が無い: $toku", "解く" in toku)
        // 同じ理由で埋もれていた他の 2 モーラ動詞も一覧に出る。
        val kiku = KanaKanjiConverter.convertFlexible("きく")
        for (expected in listOf("聞く", "効く", "聴く")) {
            assertTrue("きく の候補に $expected が無い: $kiku", expected in kiku)
        }
        val miru = KanaKanjiConverter.convertFlexible("みる")
        for (expected in listOf("見る", "診る", "観る")) {
            assertTrue("みる の候補に $expected が無い: $miru", expected in miru)
        }
    }

    @Test
    fun sentenceReadingIsUnaffected() {
        // 文の読みは lex に完全一致しないので、追加した経路は何も足さない (従来どおり)。
        assertTrue(KkcConverter.wordsFor("きょうのてんきは").isEmpty())
        val sentence = KanaKanjiConverter.convertFlexible("きょうのてんきは")
        assertTrue("文の先頭候補が変わった: $sentence", sentence.firstOrNull() == "今日の天気は")
    }
}
