package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 「先頭ブロックの非ブロック化」(タスク B) の検証。
 *
 * 長文入力中の自動分割 (as-you-type) では、先頭ブロックをブロック化せず生かな全体を扱う:
 *  - [ComposingState.isAutoSplit] が立つ (2 文節以上に割れたとき)。
 *  - このとき生確定 ([ComposingState.commitRaw]) は先頭ブロックだけでなく**生かな全体**を 1 度に送る。
 *  - 変換キー ([ComposingState.convert]) を押すと自動分割 → 手動セグメント変換へ移行し、
 *    従来どおり先頭ブロックだけを確定する。
 *
 * 「びるど」は kkc_lex に無く辞書上 2 文節以上へ割れる ([BlockLearningTest] 参照) ので分割の素材に使う。
 */
class AutoSplitHeadDisplayTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun load() {
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

    private fun typeBiludo(onCommit: (String) -> Unit): ComposingState {
        val c = ComposingState(onCommit)
        for (ch in "びるど") c.emitKana(ch)
        return c
    }

    @Test
    fun long_input_enters_auto_split_with_raw_head() {
        val c = typeBiludo {}
        assertTrue("2 文節以上なら自動分割に入る", c.isAutoSplit)
        assertTrue("自動分割は内部的にはスプリット中", c.isSplitMode)
        // 先頭ブロックは辞書上 全体より短い (= ブロック化されている) が、表示は生かな全体を使う。
        assertTrue("先頭ブロックは全体より短い", c.splitHead.length < c.text.length)
        assertEquals("びるど", c.text)
    }

    @Test
    fun commit_raw_during_auto_split_commits_whole_kana() {
        val committed = ArrayList<String>()
        val c = typeBiludo { committed.add(it) }
        assertTrue(c.isAutoSplit)
        c.commitRaw()
        assertEquals("生確定は先頭ブロックでなく生かな全体を一括で送る", listOf("びるど"), committed)
        assertFalse("全体確定後は composing が空になる", c.isActive)
    }

    @Test
    fun convert_switches_auto_split_to_manual_segment() {
        val committed = ArrayList<String>()
        val c = typeBiludo { committed.add(it) }
        c.convert()
        assertFalse("変換キーで自動分割は解除される", c.isAutoSplit)
        assertTrue("手動セグメント変換モードへ移行", c.isSplitMode)
        val head = c.splitHead
        assertTrue("手動分割の先頭ブロックは全体より短い", head.length < c.text.length)
        c.commitRaw()
        assertEquals("手動分割では先頭ブロックだけ確定する", listOf(head), committed)
        assertTrue("残りがあるので composing は継続", c.isActive)
    }
}
