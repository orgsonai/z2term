package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ローマ字面の打鍵 ([RomajiTyping]) と配列 ([romajiKeyLayout])。
 *
 * 打ちかけを変換中の文字列から読み直す作りなので、「文字列がどう育つか」をそのまま確かめる。
 */
class RomajiTypingTest {

    /** [keys] を順に打ったあとの、変換中の文字列。 */
    private fun typed(keys: String, start: String = ""): String {
        var text = start
        for (ch in keys) {
            val edit = RomajiTyping.type(text, ch)
            text = text.dropLast(edit.delete) + edit.insert
        }
        return text
    }

    @Test
    fun lettersGrowIntoKanaWhileThePendingPartStaysVisible() {
        assertEquals("あ", typed("a"))
        assertEquals("k", typed("k"))
        assertEquals("け", typed("ke"))
        assertEquals("ky", typed("ky"))
        assertEquals("きょう", typed("kyou"))
        assertEquals("にほんご", typed("nihongo"))
    }

    @Test
    fun doubledConsonantAndNnFollowTheUsualRules() {
        assertEquals("がっこう", typed("gakkou"))
        assertEquals("かんじ", typed("kanji"))
        assertEquals("かんい", typed("kanni"))
        assertEquals("っ", typed("ltu"))
    }

    @Test
    fun punctuationKeysCloseAPendingN() {
        assertEquals("かん、", typed("kan、"))
        assertEquals("らーめん。", typed("ra-menn。"))
    }

    @Test
    fun lettersThatNeverBecameKanaAreNotMixedIntoTheNextSyllable() {
        // `q` は育たないので打った字のまま残り、続く `na` は「な」になる。
        assertEquals("qな", typed("qna"))
    }

    @Test
    fun typingContinuesAfterWhatWasAlreadyComposed() {
        assertEquals("かき", typed("ki", start = "か"))
        // ⌫ で打ちかけを 1 字戻したあとも、残りから続けられる。
        assertEquals("きゃ", typed("ya", start = "k"))
    }

    @Test
    fun flushTurnsADanglingNIntoKanaAndLeavesTheRestAlone() {
        val edit = RomajiTyping.flush("かn")
        assertEquals(RomajiTyping.Edit(delete = 1, insert = "ん"), edit)
        assertNull(RomajiTyping.flush("かky"))
        assertNull(RomajiTyping.flush("かな"))
    }

    @Test
    fun digitsAloneNeedNoComposition() {
        val digit = RomajiTyping.type("", '1')
        assertFalse(digit.hasKana)
        assertFalse(digit.hasPending)
        val consonant = RomajiTyping.type("", 'k')
        assertTrue(consonant.hasPending)
        assertTrue(RomajiTyping.type("", 'a').hasKana)
    }

    @Test
    fun layoutDropsTheModifierColumnSoLetterRowsHoldTenKeys() {
        val layout = romajiKeyLayout(hasFaceKey = true)
        assertEquals(KeyboardFace.ROMAJI.id, layout.faceId)
        assertEquals(5, layout.rows.size)
        val labels = layout.allKeys().map { it.label }
        listOf("TAB", "⇧", "CTRL", "ALT").forEach { assertFalse(it, it in labels) }
        listOf("ESC", "⌫", "⏎", "、", "。", "ー").forEach { assertTrue(it, it in labels) }
        // 字の段は 10 個ぶん (英字面は左端の列があるので 11 個ぶん強)。
        assertEquals(listOf(10, 10, 10), layout.rows.subList(1, 4).map { it.slots.size })
        // 記号面でも左端の列は戻らない。
        val symbolLabels = romajiKeyLayout(hasFaceKey = true, symbols = true).allKeys().map { it.label }
        listOf("TAB", "⇧", "CTRL", "ALT").forEach { assertFalse(it, it in symbolLabels) }
    }

    @Test
    fun romajiFaceIsListedButOffUntilTheUserTurnsItOn() {
        val entries = KeyboardFaceConfig.allEntries(null, emptyList())
        assertTrue(KeyboardFaceEntry.BUILTIN_ROMAJI_ID in entries.map { it.id })
        val enabled = KeyboardFaceConfig.enabledIds(
            enabledValue = null,
            entries = entries,
            legacyNumberEnabled = true,
            legacyActiveLayoutId = "",
            legacyKanaAvailable = true,
        )
        assertFalse(KeyboardFaceEntry.BUILTIN_ROMAJI_ID in enabled)
    }
}
