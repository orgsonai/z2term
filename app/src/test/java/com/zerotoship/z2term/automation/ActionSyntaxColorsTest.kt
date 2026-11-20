package com.zerotoship.z2term.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionSyntaxColorsTest {
    @Test fun flowWordsAtLineStartAndCommentsAreTheOnlyColoredRanges() {
        val text = "# 説明\nrepeat 2 # 回数\n  wait 800\n  call ending\nend\ncommand echo if # x\nwait 1 #\nclick text=#5\n"
        val expected = listOf(
            Triple(0, 4, true),          // # 説明
            Triple(5, 11, false),        // repeat
            Triple(14, 18, true),        // # 回数
            Triple(32, 36, false),       // call (ending は色なし)
            Triple(44, 47, false),       // end
            // command の行は全体が本文。if も # も色を付けない。
            Triple(75, 76, true),        // wait 1 の後の #
        )
        assertEquals(expected, ActionSyntaxColors.ranges(text))
    }
}
