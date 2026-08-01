package com.zerotoship.z2term.ui.terminal.keyboard

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    /**
     * ⚠ **学習が育っている端末**でも同音語が出ること。
     *
     * 候補の枠 (limit=16) を学習履歴 (完全一致 4) + 予測変換 (前方一致 6) + 文まるごと変換 (6) が
     * **先着で取り合う**作りだと、使い込むほど上の段で枠が尽き、辞書が持っている語が候補へ
     * 一度も出てこなくなる。0.8.297 で「読み完全一致の 1 語」を足しても実機で「とく → 説く」が
     * 出なかったのはこれで、履歴が空の環境 (他のケース) では出るため見えなかった。
     */
    @Test
    fun exactWordsSurviveEvenWhenHistoryFillsTheBudget() {
        // 枠を上限まで食う履歴: 完全一致 4 件 + 前方一致 6 件。⚠ 完全一致側は文まるごと変換の
        // 上位と重ならない表層にする (重なると重複が落ちて枠が空き、条件を再現できない)。
        // ⚠ 1 文字の語は学習されない (MIN_WORD_LEN=2) ので、実機同様すべて 2 文字以上にする。
        withHistory(
            listOf(
                "とく" to "トク", "とく" to "得々", "とく" to "特区", "とく" to "篤く",
                "とくに" to "特に", "とくべつ" to "特別", "とくちょう" to "特徴",
                "とくい" to "得意", "とくてい" to "特定", "とくてん" to "得点",
            )
        ) {
            val cands = KanaKanjiConverter.convertFlexible("とく")
            println("とく (枠が満杯の履歴) = " + cands.mapIndexed { i, c -> "${i + 1}:$c" })
            // 学習した語は今までどおり先頭 (予測変換の順位は動かさない)。
            assertTrue("学習した語が先頭に来ていない: $cands", cands.take(4).contains("トク"))
            for (expected in listOf("説く", "解く", "溶く")) {
                assertTrue("学習が枠を埋めると $expected が消える: $cands", expected in cands)
            }
        }
    }

    /** [entries] を学習履歴に入れた状態で [body] を実行し、後片付けする。 */
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
    fun sentenceReadingIsUnaffected() {
        // 文の読みは lex に完全一致しないので、追加した経路は何も足さない (従来どおり)。
        assertTrue(KkcConverter.wordsFor("きょうのてんきは").isEmpty())
        val sentence = KanaKanjiConverter.convertFlexible("きょうのてんきは")
        assertTrue("文の先頭候補が変わった: $sentence", sentence.firstOrNull() == "今日の天気は")
    }
}
