package com.zerotoship.z2term.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 内蔵キーボードのバイト列 → テキスト欄の操作 ([ImeKeyTranslator]) の検証。
 *
 * ここが崩れると「打った文字が入らない」「⌫ が効かない」という、入力メソッドとして
 * 一番基本的なところが壊れる。⚠ 端末向けの制御コードがそのまま入力欄へ流れると、
 * 見えない 1 文字が紛れ込んで**保存してから気付く**種類の事故になる。
 */
class ImeKeyTranslatorTest {

    private fun tr(vararg bytes: Int) = ImeKeyTranslator.translate(bytes.map { it.toByte() }.toByteArray())

    @Test
    fun plainTextGoesThroughAsOneInsert() {
        assertEquals(
            listOf(ImeKeyAction.Insert("abc")),
            ImeKeyTranslator.translate("abc".toByteArray())
        )
    }

    /** かなや絵文字は複数バイト。バイト単位で見ると途中を制御コードと読み違える。 */
    @Test
    fun multiByteCharactersSurvive() {
        assertEquals(
            listOf(ImeKeyAction.Insert("あ漢🙂")),
            ImeKeyTranslator.translate("あ漢🙂".toByteArray())
        )
    }

    @Test
    fun backspaceAndEnterAndTab() {
        assertEquals(listOf(ImeKeyAction.DeleteBack), tr(0x7F))
        assertEquals(listOf(ImeKeyAction.DeleteBack), tr(0x08))
        assertEquals(listOf(ImeKeyAction.Newline), tr(0x0D))
        assertEquals(listOf(ImeKeyAction.Tab), tr(0x09))
    }

    /** ⌫ の左右フリック。端末で使い慣れた指の動きを入力欄でも同じ意味にする。 */
    @Test
    fun backspaceFlicksBecomeWordAndLineDeletes() {
        assertEquals(listOf(ImeKeyAction.DeleteWordBack), tr(0x17))       // Ctrl+W
        assertEquals(listOf(ImeKeyAction.DeleteToLineStart), tr(0x15))    // Ctrl+U
    }

    /** ESC も Ctrl+文字 もテキスト欄では捨てる (見えない文字を混ぜない)。 */
    @Test
    fun terminalOnlyControlCodesAreDropped() {
        assertEquals(emptyList<ImeKeyAction>(), tr(0x1B))                 // ESC キー
        assertEquals(emptyList<ImeKeyAction>(), tr(0x01))                 // Ctrl+A
    }

    /** ALT は ESC 前置で来る。前置を捨てるので、続く文字だけが入る。 */
    @Test
    fun altPrefixLeavesJustTheCharacter() {
        assertEquals(
            listOf(ImeKeyAction.Insert("a")),
            ImeKeyTranslator.translate(byteArrayOf(0x1B) + "a".toByteArray())
        )
    }

    @Test
    fun mixedSequenceKeepsOrderAndGroupsText() {
        assertEquals(
            listOf(
                ImeKeyAction.Insert("ab"),
                ImeKeyAction.DeleteBack,
                ImeKeyAction.Insert("cd"),
                ImeKeyAction.Newline
            ),
            ImeKeyTranslator.translate("ab".toByteArray() + byteArrayOf(0x7F) + "cd".toByteArray() + byteArrayOf(0x0D))
        )
    }

    /** Ctrl+W の数え方は readline の unix-word-rubout と同じ (末尾の空白ごと 1 単語)。 */
    @Test
    fun wordBackCountsTrailingSpaceWithTheWord() {
        assertEquals(3, ImeKeyTranslator.wordBackLength("git add"))
        assertEquals(5, ImeKeyTranslator.wordBackLength("git add  "))
        assertEquals(0, ImeKeyTranslator.wordBackLength(""))
        assertEquals(3, ImeKeyTranslator.wordBackLength("   "))
    }

    /** Ctrl+U は行頭まで。改行は残す (前の行まで消さない)。 */
    @Test
    fun lineStartStopsAtTheNewline() {
        assertEquals(5, ImeKeyTranslator.toLineStartLength("hello"))
        assertEquals(5, ImeKeyTranslator.toLineStartLength("abc\nhello"))
        assertEquals(2, ImeKeyTranslator.toLineStartLength("one\nab"))
        assertEquals(0, ImeKeyTranslator.toLineStartLength("one\n"))
    }
}
