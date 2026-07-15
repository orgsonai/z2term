package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertFalse
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 1 文字平仮名読みに記号表層が最優先で付く IPADIC の当て字 ([KkcConverter] の
 * SYMBOL_READING_PENALTY) の検証。「と」で ＆、「に」で ２ …が最上位候補にならないことを確認する。
 */
class SymbolReadingPenaltyTest {

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
    fun toDoesNotBecomeAmpersand() {
        // 「と」を打っただけで最優先候補が ＆ にならない (以前は ＆ 3177 < と 5381 で ＆ が最上位)。
        val got = KkcConverter.convert("と") ?: ""
        assertFalse("と → ＆ に化けた: $got", got.contains('＆'))
    }

    @Test
    fun singleKanaDoesNotBecomeSymbol() {
        // に→２ / ご→５ / ざ→ｔｈｅ の当て字も最上位に出ない。
        assertFalse(KkcConverter.convert("に")?.contains('２') ?: false)
        assertFalse(KkcConverter.convert("ご")?.contains('５') ?: false)
    }
}
