package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 存在/補助動詞「ある・いる」の漢字表層ペナルティ ([KkcConverter] の AUX_KANA_PENALTY) の検証。
 * 平仮名で打った「ありません」等が漢字 (在り/有り/居る) に過剰変換されないことを確認する。
 */
class AuxKanaPenaltyTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadKkc() {
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

    @Test
    fun arimasenStaysHiragana() {
        // もんだいありません → 在り/有り に化けず平仮名のまま。
        val got = KkcConverter.convert("もんだいありません")
        assertEquals("問題ありません", got)
    }

    @Test
    fun existenceVerbDoesNotBecomeKanji() {
        val got = KkcConverter.convert("ありません") ?: ""
        assertFalse("在/有 へ過剰変換: $got", got.contains('在') || got.contains('有'))
    }

    @Test
    fun kudasaiStaysHiragana() {
        // 〜してください の補助動詞 ください は平仮名のまま (下さい にしない)。
        val got = KkcConverter.convert("してください") ?: ""
        assertFalse("下さい へ過剰変換: $got", got.contains('下'))
    }

    @Test
    fun compoundsWithSameKanjiAreNotBroken() {
        // 読みで絞っているので、同じ漢字を含む別語 (別読み) は壊さない。
        assertEquals("有名", KkcConverter.convert("ゆうめい"))
        assertEquals("仕事", KkcConverter.convert("しごと"))
        assertEquals("時間", KkcConverter.convert("じかん"))
    }
}
