package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 打ちかけの補完 ([PredictionDict] と [KanaKanjiConverter.convertFlexible] の並び) の検証。
 *
 * ⚠ 補完の表はこのクラスだけが読む。ほかのテストは表なしの候補の並びを固定しているので、
 * 終わったら空に戻す。
 */
class PredictionDictTest {

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
            locate("src/main/assets/kkc_predict.tsv").bufferedReader(Charsets.UTF_8)
                .use { PredictionDict.loadFrom(it) }
        }

        @AfterClass
        @JvmStatic
        fun unload() = PredictionDict.clear()

        private fun locate(rel: String): File {
            for (b in listOf(".", "app", "../app")) {
                val f = File(b, rel)
                if (f.exists()) return f
            }
            error("asset not found: $rel (cwd=${File(".").absolutePath})")
        }
    }

    @Test
    fun commonWordsComeFirst() {
        assertEquals("開発", PredictionDict.predict("かい").first())
        assertEquals("仕事", PredictionDict.predict("しご").first())
        assertEquals("問題", PredictionDict.predict("もん").first())
        assertEquals("新しい", PredictionDict.predict("あた").first())
        assertEquals("食べる", PredictionDict.predict("たべ").first())
    }

    @Test
    fun namesDoNotCrowdOutOrdinaryWords() {
        // 地名・人名は回数を 1/10 にして並べてある。そのままだと あり → 有馬 が先頭に来る。
        val ari = PredictionDict.predict("あり", limit = 3)
        assertTrue("あり の上位 3 件に ありがとう が無い: $ari", "ありがとう" in ari)
        assertTrue("あり の上位 3 件に人名・地名が入っている: $ari", "有馬" !in ari && "有吉" !in ari)
    }

    @Test
    fun onlyLongerReadingsAreReturned() {
        for ((reading, _) in PredictionDict.predictWithReading("しごと", limit = 20)) {
            assertTrue("読みが接頭辞より長くない: $reading", reading.startsWith("しごと") && reading.length > 3)
        }
    }

    @Test
    fun singleKanaPrefixGetsNothing() {
        // 1 文字では絞れない (候補の列を無関係な語で埋めるだけになる)。
        assertTrue(PredictionDict.predict("あ").isEmpty())
    }

    @Test
    fun conversionComesFirstThenCompletions() {
        // 1 番目は打った読みの変換、補完はその後ろの 3 件まで (読み 2 文字)。
        val c = KanaKanjiConverter.convertFlexible("しご")
        val at = c.indexOf("仕事")
        assertTrue("仕事 が 2〜4 番目に無い (位置 $at): ${c.take(6)}", at in 1..3)
    }

    @Test
    fun completionCarriesItsRealReading() {
        // 補完を確定したとき、打った「しご」ではなく「しごと」で学習させる。
        assertEquals("しごと", KanaKanjiConverter.predictionReadingMap("しご")["仕事"])
    }

    @Test
    fun slotsGrowWithTypedLength() {
        assertEquals(2, KanaKanjiConverter.completionSlots(1))
        assertEquals(3, KanaKanjiConverter.completionSlots(2))
        assertEquals(4, KanaKanjiConverter.completionSlots(3))
        assertEquals(4, KanaKanjiConverter.completionSlots(9))
    }
}
