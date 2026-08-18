package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 常用動詞の活用形が候補に出ることの回帰テスト。
 *
 * 元辞書 (SKK 送り仮名なし) は動詞の終止形も活用形もほぼ持たないので、変換できる活用形は
 * [KanaKanjiConverter] 内の常用語テーブルに**その動詞が載っているかどうか**で決まる。
 * 表から 1 語落ちると、その動詞の活用形が丸ごと変換不能になる — 「開ける/閉める/閉まる/閉じる」が
 * 揃っているのに「開く」だけ無く、**あかない → 開かない が出なかった**のがそれ (利用者の指摘)。
 * 対になる自動詞/他動詞が両方あることを固定する。
 */
class VerbConjugationCandidateTest {

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

    private fun assertHas(reading: String, expected: String) {
        val got = KanaKanjiConverter.convert(reading)
        assertTrue("$reading の候補に $expected が無い: $got", expected in got)
    }

    @Test
    fun openIntransitiveConjugates() {
        // 報告そのもの: 「あかない」で「開かない」が出ない。
        assertHas("あかない", "開かない")
        assertHas("あく", "開く")
        assertHas("あいて", "開いて")
        assertHas("あきます", "開きます")
        assertHas("あかなかった", "開かなかった")
    }

    @Test
    fun openHiraakuConjugates() {
        // 同じ「開く」のもう一方の読み。元辞書は `ひらく /啓/` しか持たない。
        assertHas("ひらく", "開く")
        assertHas("ひらかない", "開かない")
        assertHas("ひらいて", "開いて")
    }

    @Test
    fun theOppositesAreStillThere() {
        // 対になる語が消えていないこと (片側だけ直して逆を落とす事故を防ぐ)。
        assertHas("あける", "開ける")
        assertHas("しまらない", "閉まらない")
        assertHas("とじない", "閉じない")
    }
}
