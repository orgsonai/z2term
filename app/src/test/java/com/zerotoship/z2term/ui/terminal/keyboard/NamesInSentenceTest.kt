package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 文の中の地名・人名 (`kkc_names.tsv`) の検証。アプリと同じ構成 (頻度補正 + 地名・人名 +
 * 上位 8 候補を共起で並べ替え) で 1 位を見る。
 *
 * ⚠ このクラスは補正つきで辞書を読み、並べ替えも差し替える。ほかのテストは補正なしの順位を
 * 固定しているので、終わったら補正なしで読み直し、並べ替えも元に戻す。
 */
class NamesInSentenceTest {

    companion object {
        private var savedReranker: KkcConverter.KkcReranker = KkcConverter.IdentityReranker

        @BeforeClass
        @JvmStatic
        fun loadLikeTheApp() {
            KkcConverter.learnedBlock = null
            KkcConverter.userDictBlock = null
            KkcConverter.unigramBonus = null
            savedReranker = KkcConverter.reranker
            val filter = locate("src/main/assets/kkc_colloc.bloom").inputStream().use { ExistenceFilter.load(it) }
            KkcConverter.reranker = CollocationReranker({ filter })
            load(withExtras = true)
        }

        @AfterClass
        @JvmStatic
        fun restore() {
            KkcConverter.reranker = savedReranker
            load(withExtras = false)
        }

        private fun load(withExtras: Boolean) {
            val freq = if (withExtras) locate("src/main/assets/kkc_freq.tsv").bufferedReader(Charsets.UTF_8) else null
            val names = if (withExtras) locate("src/main/assets/kkc_names.tsv").bufferedReader(Charsets.UTF_8) else null
            try {
                locate("src/main/assets/kkc_matrix.bin").inputStream().use { ms ->
                    locate("src/main/assets/kkc_lex.tsv").bufferedReader(Charsets.UTF_8).use { lr ->
                        KkcConverter.loadFromStreams(ms, lr, freq, names)
                    }
                }
            } finally {
                freq?.close()
                names?.close()
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

    /** アプリの候補 1 位 (convertFlexible の文まるごと変換と同じ: 上位 8 候補 → 並べ替え)。 */
    private fun top(reading: String): String =
        KkcConverter.nbest(reading, 8).firstOrNull()?.surface ?: ""

    @Test
    fun placeNamesConvertInsideSentences() {
        assertEquals("東京へ行きます", top("とうきょうへいきます"))      // 以前は 当供へ行きます
        assertEquals("大阪出身です", top("おおさかしゅっしんです"))      // 多さ化出身です
        assertEquals("北海道へ旅行", top("ほっかいどうへりょこう"))      // ホッ会同へ旅行
        assertEquals("富士山に登る", top("ふじさんにのぼる"))            // 不持参に上る
        assertEquals("京都に行く", top("きょうとにいく"))                // 教徒に行く
        assertEquals("名古屋で乗り換える", top("なごやでのりかえる"))    // 名子屋で乗り換える
    }

    @Test
    fun personalNamesConvertInsideSentences() {
        assertEquals("山田太郎です", top("やまだたろうです"))            // 山田他弄です
        assertEquals("田中さんに電話する", top("たなかさんにでんわする"))  // 他中三二電話する
    }

    @Test
    fun ordinaryWordsAreNotTurnedIntoNames() {
        assertEquals("砂糖を入れる", top("さとうをいれる"))              // 佐藤 にならない
        assertEquals("棚から落ちる", top("たなからおちる"))              // 田中 にならない
        assertEquals("林の中を歩く", top("はやしのなかをあるく"))        // 中野 にならない
        assertEquals("山だけが見える", top("やまだけがみえる"))          // 山田 にならない
    }

    @Test
    fun rerankDoesNotKanjifyKanaWords() {
        assertEquals("待ってください", top("まってください"))            // 以前は 待って下さい
        assertEquals("よろしくお願いします", top("よろしくおねがいします"))  // 宜しくお願いします
        assertEquals("時間がないので急ぎます", top("じかんがないのでいそぎます"))  // 時間が無いので急ぎます
        assertEquals("走っていく", top("はしっていく"))                  // 走って行く
        assertEquals("書いてください", top("かいてください"))            // 書いて下さい
    }

    @Test
    fun namesFileHasLexShape() {
        val bad = locate("src/main/assets/kkc_names.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotEmpty() && it[0] != '#' }
            .filter { line -> line.split('\t').let { p -> p.size != 5 || p.drop(2).any { it.toIntOrNull() == null } } }
        assertTrue("形の違う行がある: ${bad.take(3)}", bad.isEmpty())
    }
}
