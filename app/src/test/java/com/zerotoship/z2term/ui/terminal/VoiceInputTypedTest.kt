package com.zerotoship.z2term.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🎤 で打ち込む前の整え ([VoiceInput.typed]) を固定する。
 *
 * 「エル エス」が `LS` で入り、`ls` として動かなかった (実機)。崩れると声でコマンドが打てなくなる。
 */
class VoiceInputTypedTest {

    /** 全部大文字の語だけ小文字にする。 */
    @Test
    fun lowersAllCapsWords() {
        assertEquals("ls", VoiceInput.typed("LS"))
        assertEquals("git status", VoiceInput.typed("GIT STATUS"))
        assertEquals("mp3", VoiceInput.typed("MP3"))
        assertEquals("readme.md", VoiceInput.typed("README.md"))
    }

    /** 大小が混ざった語・日本語・数字だけの語はそのまま。 */
    @Test
    fun keepsMixedCaseAndOtherText() {
        assertEquals("Git", VoiceInput.typed("Git"))
        assertEquals("ファイルをlsして", VoiceInput.typed("ファイルをLSして"))
        assertEquals("ポート 8080", VoiceInput.typed("ポート 8080"))
    }

    /** 全角の大文字は半角の小文字にする (全角のままでもコマンドとして動かない)。 */
    @Test
    fun widensFullWidthAllCaps() {
        assertEquals("ls", VoiceInput.typed("ＬＳ"))
    }
}
