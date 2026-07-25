package com.zerotoship.z2term.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ライブ tail ウィジェット (D2) の行の切り出し
 * ([TailReader.lastLines] = 末尾 / [TailReader.firstLines] = 先頭) の検証。
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

    // --- head 側 (0.8.240) ---

    @Test fun takesFirstLines() {
        val text = "a\nb\nc\nd\ne\n"
        assertEquals(listOf("a", "b", "c"), TailReader.firstLines(text, 3))
    }

    @Test fun headFewerLinesThanAskedIsFine() {
        assertEquals(listOf("a", "b"), TailReader.firstLines("a\nb\n", 10))
    }

    @Test fun headTrailingNewlineIsNotAnEmptyLine() {
        assertEquals(listOf("a", "b"), TailReader.firstLines("a\nb\n", 5))
    }

    @Test fun headBlankLineInsideIsKept() {
        assertEquals(listOf("a", "", "b"), TailReader.firstLines("a\n\nb\n", 3))
    }

    @Test fun headCrlfIsHandled() {
        assertEquals(listOf("a", "b"), TailReader.firstLines("a\r\nb\r\n", 5))
    }

    @Test fun truncatedTailDropsTheLastLine() {
        // 先頭から 16KB だけ読んだときは、最終行が半端 (文字の途中で切れている) なので捨てる。
        assertEquals(listOf("a", "b"), TailReader.firstLines("a\nb\nc-cut", 5, truncatedTail = true))
    }

    @Test fun truncatedTailKeepsTheOnlyLine() {
        // 1 行しか無いなら捨てると何も出せなくなるので残す ([truncatedHead] と対称)。
        assertEquals(listOf("only"), TailReader.firstLines("only", 3, truncatedTail = true))
    }

    @Test fun headEmptyInputGivesNothing() {
        assertEquals(emptyList<String>(), TailReader.firstLines("", 5))
        assertEquals(emptyList<String>(), TailReader.firstLines("\n", 5))
        assertEquals(emptyList<String>(), TailReader.firstLines("a\n", 0))
    }

    @Test fun headAndTailAgreeOnAShortFile() {
        // 窓に収まる短いファイルなら、どちら側から数えても同じ 3 行になる。
        val text = "a\nb\nc\n"
        assertEquals(TailReader.lastLines(text, 3), TailReader.firstLines(text, 3))
    }
}
