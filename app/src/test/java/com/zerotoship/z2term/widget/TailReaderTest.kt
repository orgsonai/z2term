package com.zerotoship.z2term.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ライブ tail ウィジェット (D2) の末尾行の切り出し ([TailReader.lastLines]) の検証。
 *
 * ここが崩れると、ホーム画面に**古い行が出続ける / 化けた行が出る**という、
 * 端末側では気付きにくい形の不具合になる。
 */
class TailReaderTest {

    @Test fun takesLastLines() {
        val text = "a\nb\nc\nd\ne\n"
        assertEquals(listOf("c", "d", "e"), TailReader.lastLines(text, 3))
    }

    @Test fun fewerLinesThanAskedIsFine() {
        assertEquals(listOf("a", "b"), TailReader.lastLines("a\nb\n", 10))
    }

    @Test fun trailingNewlineIsNotAnEmptyLine() {
        // 末尾の改行は「最後の行が終わった」印。空行として数えると 1 行ぶん損をする。
        assertEquals(listOf("b"), TailReader.lastLines("a\nb\n", 1))
    }

    @Test fun blankLineInsideIsKept() {
        assertEquals(listOf("a", "", "b"), TailReader.lastLines("a\n\nb\n", 3))
    }

    @Test fun crlfIsHandled() {
        assertEquals(listOf("a", "b"), TailReader.lastLines("a\r\nb\r\n", 5))
    }

    @Test fun truncatedHeadDropsTheFirstLine() {
        // 途中のバイトから読み始めたときは、先頭行が半端 (文字の途中で切れている) なので捨てる。
        assertEquals(listOf("b", "c"), TailReader.lastLines("alf\nb\nc\n", 3, truncatedHead = true))
    }

    @Test fun truncatedHeadKeepsTheOnlyLine() {
        // 1 行しか無いなら捨てると何も出せなくなるので残す。
        assertEquals(listOf("only"), TailReader.lastLines("only", 3, truncatedHead = true))
    }

    @Test fun emptyInputGivesNothing() {
        assertEquals(emptyList<String>(), TailReader.lastLines("", 5))
        assertEquals(emptyList<String>(), TailReader.lastLines("\n", 5))
        assertEquals(emptyList<String>(), TailReader.lastLines("a\n", 0))
    }
}
