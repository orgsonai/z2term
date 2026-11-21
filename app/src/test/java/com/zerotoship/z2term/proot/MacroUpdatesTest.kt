package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 同梱サンプルの更新のお知らせ (1.0.198・[MacroUpdates])。知らせるのは**書き換えていないコピーで、
 * 中身 (言語を除く) が新しくなったもの**だけ。固定したもの・書き換えたものは出さない。版が同じなら
 * 2 回目は出さない。
 */
class MacroUpdatesTest {

    private fun home(): File = File.createTempFile("macro-updates", "").apply { delete(); mkdirs() }

    @Test
    fun announcesOnlyUneditedCopiesWithNewContent() {
        val root = home()
        val macros = File(root, ".z2term/macros").apply { mkdirs() }
        try {
            val v1 = mapOf("a.sh" to "a1", "b.sh" to "b1", "c.sh" to "c1", "d.sh" to "d1")
            val v1Ids = mapOf("a.sh" to "A1", "b.sh" to "B1", "c.sh" to "C1", "d.sh" to "D1")
            // 1.0.197 までに入れたコピー (元が無い)。a・c・d は書き換えていない、b は書き換えた。
            File(macros, "a.sh").writeText("a1")
            File(macros, "b.sh").writeText("b1 mine")
            File(macros, "c.sh").writeText("c1")
            File(macros, "d.sh").writeText("d1")
            File(macros, ".hold").writeText("c   # 自分用に残す\n")

            // 新しい版: a・b・c の中身が変わり、d は言語だけ変わった (名札が同じ)。
            val v2 = mapOf("a.sh" to "a2", "b.sh" to "b2", "c.sh" to "c2", "d.sh" to "d1 (fr)")
            val v2Ids = mapOf("a.sh" to "A2", "b.sh" to "B2", "c.sh" to "C2", "d.sh" to "D1")
            MacroUpdates.adoptLegacy(macros, v2, v2Ids, before = v1, beforeIds = v1Ids)
            assertTrue("書き換えたコピーに元を作った", !File(macros, ".bundled/b.sh").exists())

            assertEquals(listOf("a.sh"), MacroUpdates.namesToAnnounce(macros, 900, v2, v2Ids))
            // 同じ版の 2 回目の起動では出さない。
            assertEquals(emptyList<String>(), MacroUpdates.namesToAnnounce(macros, 900, v2, v2Ids))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun noMacrosNoNotice() {
        val root = home()
        try {
            val macros = File(root, ".z2term/macros")
            assertEquals(emptyList<String>(), MacroUpdates.namesToAnnounce(macros, 900, mapOf("a.sh" to "a"), mapOf("a.sh" to "A")))
            assertTrue("マクロを入れていないのに置き場を作った", !macros.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun heldNamesAcceptBothFormsAndNotes() {
        assertEquals(setOf("voice", "remind"), MacroUpdates.heldNames("voice.sh\n  remind # 自分で直した\n\n# メモ\n"))
    }
}
