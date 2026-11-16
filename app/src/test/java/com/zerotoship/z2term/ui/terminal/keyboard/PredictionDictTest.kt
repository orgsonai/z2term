package com.zerotoship.z2term.ui.terminal.keyboard

import kotlinx.coroutines.runBlocking
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

    /**
     * 学習した語が多い読みでも、よく使われる語が補完の枠に入る (1.0.138)。
     * 1.0.137 の実機では「かい」の枠 3 件を学習 (回線 / 改善… / 改善) が全部取り、開発 が出なかった。
     */
    @Test
    fun learnedWordsDoNotFillAllCompletionSlots() {
        withHistory(
            listOf(
                "かいせん" to "回線", "かいぜん" to "改善", "かいけつ" to "解決",
                "かいぜんできないでしょうか？" to "改善出来ないでしょうか？",
            )
        ) {
            val c = KanaKanjiConverter.convertFlexible("かい")
            val head = c.take(4) // 1 番目の変換 + 補完 3 件 (読み 2 文字)
            assertTrue("かい の補完に 開発 が無い: ${c.take(8)}", "開発" in head)
            val learned = head.count { it in setOf("回線", "改善", "解決") }
            assertEquals("学習した語が補完の枠を 1 件より多く取った: ${c.take(8)}", 1, learned)
            // 残りの学習も消えずに後ろへ回る。
            for (w in listOf("回線", "改善", "解決")) assertTrue("$w が候補から消えた: $c", w in c)
        }
    }

    @Test
    fun learnedSentencesAreNotOfferedAsCompletions() {
        withHistory(listOf("かいぜんできないでしょうか？" to "改善出来ないでしょうか？")) {
            val c = KanaKanjiConverter.convertFlexible("かい")
            assertTrue("文まるごとが補完に出た: $c", "改善出来ないでしょうか？" !in c)
        }
    }

    @Test
    fun sentenceLikeRule() {
        assertTrue(KanaKanjiConverter.isSentenceLike("かいぜんできないでしょうか？", "改善出来ないでしょうか？"))
        assertTrue(KanaKanjiConverter.isSentenceLike("はい、", "はい、"))
        assertTrue(KanaKanjiConverter.isSentenceLike("きょうはいいてんきです", "今日はいい天気です"))
        assertTrue(!KanaKanjiConverter.isSentenceLike("おねがいします", "お願いします"))
        assertTrue(!KanaKanjiConverter.isSentenceLike("かいせん", "回線"))
    }

    /** [entries] を学習履歴に入れた状態で [body] を実行し、後片付けする (ExactWordCandidateTest と同じ作法)。 */
    private fun withHistory(entries: List<Pair<String, String>>, body: () -> Unit) {
        val loadedField = ImeHistoryStore::class.java.getDeclaredField("loaded")
            .apply { isAccessible = true }
        val wasLoaded = loadedField.getBoolean(ImeHistoryStore)
        loadedField.setBoolean(ImeHistoryStore, true)
        try {
            runBlocking { ImeHistoryStore.clearAll() }
            for ((r, w) in entries) ImeHistoryStore.record(r, w)
            // record は IO コルーチンなので反映を待つ。
            val deadline = System.currentTimeMillis() + 5_000
            while (ImeHistoryStore.approximateCount() < entries.size &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(20)
            }
            assertEquals("履歴が入りきっていない", entries.size, ImeHistoryStore.approximateCount())
            body()
        } finally {
            runBlocking { ImeHistoryStore.clearAll() }
            loadedField.setBoolean(ImeHistoryStore, wasLoaded)
        }
    }

    @Test
    fun slotsGrowWithTypedLength() {
        assertEquals(2, KanaKanjiConverter.completionSlots(1))
        assertEquals(3, KanaKanjiConverter.completionSlots(2))
        assertEquals(4, KanaKanjiConverter.completionSlots(3))
        assertEquals(4, KanaKanjiConverter.completionSlots(9))
    }
}
