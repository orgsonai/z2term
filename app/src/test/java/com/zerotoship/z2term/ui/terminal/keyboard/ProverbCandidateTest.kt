package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * ことわざ ([PROVERB_ENTRIES]) が辞書から引けることの回帰テスト (1.0.28)。
 *
 * 同梱辞書 (SKK 由来) は文の形のことわざを持たないので、内蔵テーブルを辞書へ混ぜている。
 * 読みを全部打てば変換で、途中まで打てば予測で出ること、既存の読みの候補を押し下げないことを固定する。
 */
class ProverbCandidateTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun load() {
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
    fun fullReadingConvertsToProverb() {
        assertTrue(KanaKanjiConverter.convert("ねこにこばん").contains("猫に小判"))
        assertTrue(KanaKanjiConverter.convert("あつものにこりてなますをふく").contains("羹に懲りて膾を吹く"))
        // 表記が 2 つあるものは両方出る。
        val kuchi = KanaKanjiConverter.convert("くちはわざわいのもと")
        assertTrue(kuchi.containsAll(listOf("口は災いの元", "口は禍の元")))
    }

    @Test
    fun partialReadingPredictsProverb() {
        assertTrue(KanaKanjiConverter.predict("いしのうえに").contains("石の上にも三年"))
    }

    @Test
    fun everyEntryIsReachable() {
        for ((reading, text) in PROVERB_ENTRIES) {
            val got = KanaKanjiConverter.convert(reading)
            for (w in text.split('/')) assertTrue("$reading → $w が引けない: $got", w in got)
        }
    }

    @Test
    fun existingCandidatesStayFirst() {
        // 辞書が既に持つ読みでは、元の並びを変えない (同じ表記は重ねない)。
        assertEquals(listOf("後の祭り", "後の祭"), KanaKanjiConverter.convert("あとのまつり"))
        assertEquals(listOf("背水の陣", "背水乃陣"), KanaKanjiConverter.convert("はいすいのじん"))
    }
}
