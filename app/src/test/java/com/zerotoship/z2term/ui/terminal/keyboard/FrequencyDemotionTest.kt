package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 頻度補正 (`kkc_freq.tsv`) の検証。同じ読み・同じ品詞の語のうち、実際の文章で少ないほうが
 * 後ろへ回り、長文で「ふつう使わない語が先に出る」のが減ることを確かめる。
 *
 * ⚠ このクラスだけ補正つきで辞書を読む。ほかのテストは補正なしの順位を固定しているので、
 * 終わったら補正なしで読み直して元に戻す。
 */
class FrequencyDemotionTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadWithFrequency() {
            KkcConverter.learnedBlock = null
            KkcConverter.userDictBlock = null
            KkcConverter.unigramBonus = null
            load(withFrequency = true)
        }

        @AfterClass
        @JvmStatic
        fun restore() = load(withFrequency = false)

        private fun load(withFrequency: Boolean) {
            val freq = if (withFrequency) locate("src/main/assets/kkc_freq.tsv").bufferedReader(Charsets.UTF_8) else null
            try {
                locate("src/main/assets/kkc_matrix.bin").inputStream().use { ms ->
                    locate("src/main/assets/kkc_lex.tsv").bufferedReader(Charsets.UTF_8).use { lr ->
                        KkcConverter.loadFromStreams(ms, lr, freq)
                    }
                }
            } finally {
                freq?.close()
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
    fun commonWordBeatsRareHomophone() {
        assertEquals("水を飲む", top("みずをのむ"))              // 補正なしでは 瑞を飲む
        assertEquals("掃除をする", top("そうじをする"))          // 相似をする
        assertEquals("橋を渡る", top("はしをわたる"))            // 箸を渡る
        assertEquals("山田先生は親切です", top("やまだせんせいはしんせつです"))   // 深切です
        assertEquals("書いてください", top("かいてください"))    // 欠いてください
        assertEquals("文字が読めない", top("もじがよめない"))    // 讀めない
    }

    @Test
    fun kanjiBeatsKanaSpellingOfTheSameNoun() {
        assertEquals("高い山", top("たかいやま"))                // 高いヤマ
        assertEquals("部屋の中", top("へやのなか"))              // 部屋のなか
        assertEquals("人が多い", top("ひとがおおい"))            // ひとが多い
    }

    @Test
    fun wordsOfAnotherPartOfSpeechAreNotDisturbed() {
        // 安くする補正を入れると、ここが 後身 / 細菌 に化ける (試して確かめた)。
        assertEquals("更新を確認する", top("こうしんをかくにんする"))
        assertEquals("最近忙しい", top("さいきんいそがしい"))
        assertEquals("お願いします", top("おねがいします"))
    }

    @Test
    fun dataOnlyRaisesCosts() {
        val bad = locate("src/main/assets/kkc_freq.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotEmpty() && it[0] != '#' }
            .filter { line -> line.split('\t').let { it.size != 4 || (it[3].toIntOrNull() ?: 0) <= 0 } }
        assertTrue("足すコストが正でない行がある: ${bad.take(3)}", bad.isEmpty())
    }
}
