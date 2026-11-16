package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 「ふつうの語があるのに、変わった書き方のほうが安い」エントリの抑制
 * ([KkcConverter] の MIXED_SCRIPT_PENALTY) の検証。
 *
 * 元の症状: 文の中で かける→×・する→スる・かく→斯ク・あがる→アガる・かえって→カエって が
 * 1 位に出ていた (IPADIC がこれらの表層を、ふつうの漢字・かなの語より安く持つため)。
 */
class OddSurfacePenaltyTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadKkc() {
            // 他のテストが学習を配線していても、ここは辞書だけの素の順位を見る。
            KkcConverter.learnedBlock = null
            KkcConverter.userDictBlock = null
            KkcConverter.unigramBonus = null
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

    private fun top(reading: String): String =
        KkcConverter.nbest(reading, 1).firstOrNull()?.surface ?: ""

    @Test
    fun symbolDoesNotBeatRealWord() {
        assertEquals("電話を掛ける", top("でんわをかける"))      // 以前は 電話を×
        assertEquals("あっと驚く", top("あっとおどろく"))        // 以前は ＠驚く
        assertEquals("安堵した", top("あんどした"))              // 以前は ＆した
        assertEquals("一万円", top("いちまんえん"))              // 以前は １万円
    }

    @Test
    fun katakanaMixDoesNotBeatRealWord() {
        assertEquals("上がる", top("あがる"))                    // 以前は アガる
        assertEquals("受ける", top("うける"))                    // 以前は ウケる
        assertEquals("偉い", top("えらい"))                      // 以前は エラい
        assertEquals("帰ってきた", top("かえってきた"))          // 以前は カエってきた
        assertEquals("選択をする", top("せんたくをする"))        // 以前は 選択をスる
    }

    @Test
    fun oldKatakanaOkuriganaDoesNotBeatRealWord() {
        assertTrue("斯ク が 1 位に残っている", '斯' !in top("てがみをかく"))
    }

    @Test
    fun wordsWithoutAnOrdinarySpellingAreKept() {
        // ふつうの語が無い読みは沈めない。
        assertEquals("ダサい", top("ださい"))
        assertEquals("サボる", top("さぼる"))
    }

    @Test
    fun penalizedSurfaceStaysSelectable() {
        // 沈めるだけで消さない。欲しいときは候補一覧から選べる。
        assertTrue("× が候補から消えた", "×" in KkcConverter.wordsFor("かける", limit = 100))
    }
}
