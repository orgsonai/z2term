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
    fun singleKanaPrefixGetsCommonWords() {
        assertTrue(PredictionDict.predict("あ").isNotEmpty())
        // 候補の列を埋めないよう、1 文字のときの補完は 2 件まで。
        assertEquals(2, KanaKanjiConverter.completionSlots(1))
    }

    /** 活用した形も補完に出る (1.0.166)。文章に少ない丁寧な言い方は、活用の規則で作ってある。 */
    @Test
    fun conjugatedFormsAreOffered() {
        val words = PredictionDict.predictWithReading("わかりま", limit = 20)
        assertTrue("わかりま の補完に わかりました が無い: $words", words.any { it.first == "わかりました" })
        assertTrue(PredictionDict.predictWithReading("かんがえま", limit = 20).any { it.second == "考えました" })
    }

    @Test
    fun conversionComesFirstThenCompletions() {
        // 1 番目は打った読みの変換、補完はその後ろの 4 件まで (読み 2 文字)。
        val c = KanaKanjiConverter.convertFlexible("しご")
        val at = c.indexOf("仕事")
        assertTrue("仕事 が 2〜5 番目に無い (位置 $at): ${c.take(6)}", at in 1..4)
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
            val head = c.take(5) // 1 番目の変換 + 補完 4 件 (読み 2 文字)
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

    /** 変換キーを押した後 (allowPrediction=false) は、読みにぴったり合う語だけになる (1.0.139)。 */
    @Test
    fun conversionModeShowsOnlyExactWords() {
        val predicting = KanaKanjiConverter.convertFlexible("かい")
        assertTrue("予測で 開発 が出ていない: ${predicting.take(8)}", "開発" in predicting)
        val converting = KanaKanjiConverter.convertFlexible("かい", allowPrediction = false)
        assertTrue("変換で 開発 (続きの長い語) が出た: $converting", "開発" !in converting)
        assertTrue("変換で 会 が無い: $converting", "会" in converting)
    }

    /** 次の言葉の予測は、直前の語の後に続けて確定した語を、よく続けた順に返す (1.0.139)。 */
    @Test
    fun nextWordsFollowWhatWasTypedAfter() {
        withHistory(emptyList()) {
            ImeHistoryStore.recordBigram("よろしく", "お願いします")
            ImeHistoryStore.recordBigram("よろしく", "ね")
            ImeHistoryStore.recordBigram("よろしく", "お願いします")
            // recordBigram は IO コルーチンなので反映を待つ。
            val deadline = System.currentTimeMillis() + 5_000
            while (ImeHistoryStore.nextWords("よろしく").size < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            Thread.sleep(100) // 3 件目 (お願いします の 2 回目) の反映
            assertEquals(listOf("お願いします", "ね"), ImeHistoryStore.nextWords("よろしく"))
            assertTrue(ImeHistoryStore.nextWords("ありがとう").isEmpty())
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

    /** 定型文は補完に出るが、よく使われる語の枠は取らない (1.0.165)。 */
    @Test
    fun setPhrasesAreOfferedAfterOrdinaryCompletions() {
        val c = KanaKanjiConverter.convertFlexible("よろ")
        assertTrue("よろ で定型文が語の枠を取った: ${c.take(6)}", "よろしくお願いします" !in c.take(5))
        val longer = KanaKanjiConverter.convertFlexible("よろしくお")
        assertTrue("よろしくお の補完に定型文が無い: ${longer.take(6)}", "よろしくお願いします" in longer.take(5))
        assertEquals(
            "よろしくおねがいします",
            KanaKanjiConverter.predictionReadingMap("よろ")["よろしくお願いします"],
        )
        // 1 文字では出さない (補完と同じ)。読みぴったりの語は補完ではない。
        assertTrue(SetPhrases.predictWithReading("よ").isEmpty())
        assertTrue(SetPhrases.predictWithReading("こんにちは").isEmpty())
    }

    /** 設定「予測変換」が OFF (既定) なら、表の補完も定型文も出ない (1.0.140)。 */
    @Test
    fun predictionOffShowsNoTableCompletions() {
        val off = KanaKanjiConverter.convertFlexible("よろ", predictive = false)
        assertTrue("予測変換 OFF で定型文が出た: $off", "よろしくお願いします" !in off)
        val offKai = KanaKanjiConverter.convertFlexible("かい", predictive = false)
        assertTrue("予測変換 OFF で 1 番目が変換になっていない: ${offKai.take(4)}", offKai.isNotEmpty())
        assertTrue(
            "予測変換 OFF で表の語の読みを控えた",
            KanaKanjiConverter.predictionReadingMap("よろ", predictive = false)["よろしくお願いします"] == null,
        )
    }

    @Test
    fun slotsGrowWithTypedLength() {
        assertEquals(2, KanaKanjiConverter.completionSlots(1))
        assertEquals(4, KanaKanjiConverter.completionSlots(2))
        assertEquals(5, KanaKanjiConverter.completionSlots(3))
        assertEquals(5, KanaKanjiConverter.completionSlots(9))
    }
}
