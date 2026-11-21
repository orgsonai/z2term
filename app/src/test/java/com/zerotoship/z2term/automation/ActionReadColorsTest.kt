package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

/** `read-colors 名前 X,Y ...` (1.0.200): 1 回の撮影で複数の点を読み、変数に入れる手順の読み取り。 */
class ActionReadColorsTest {
    @Test fun parsesNameAndPoints() {
        val steps = ActionDefinition.parse("version=2\nscreen=1080x2400@0\nread-colors board 100,1516 277,1516 985,2224").steps
        assertEquals(ActionDefinition.Step.ReadColors(ActionDefinition.CURRENT_TARGET, "board",
            listOf(100 to 1516, 277 to 1516, 985 to 2224)), steps.single())
    }

    @Test fun keepsTheTargetBeforeIt() {
        val steps = ActionDefinition.parse("version=2\nscreen=1080x2400@0\ntarget com.android.chrome\nread-colors c 1,2").steps
        assertEquals("com.android.chrome", (steps.last() as ActionDefinition.Step.ReadColors).target)
    }

    @Test fun refusesWhatItCannotRead() {
        val head = "version=2\nscreen=1080x2400@0\n"
        val max = ActionDefinition.MAX_READ_COLOR_POINTS
        for (body in listOf("read-colors board", "read-colors 1x 1,2", "read-colors board 1,2,3", "read-colors board 10%,20",
            "read-colors board -1,2", "read-colors board 1080,10", "read-colors board 10,2400",
            "read-colors board " + List(max + 1) { "1,1" }.joinToString(" "))) {
            assertThrows(body, IllegalArgumentException::class.java) { ActionDefinition.parse(head + body) }
        }
        // 32 点は通る (6x5 の盤面を 1 回で読める)。
        ActionDefinition.parse(head + "read-colors board " + List(max) { "1,1" }.joinToString(" "))
        // 点は px なので画面の寸法が要る。version=1 では使えない。
        assertThrows(IllegalArgumentException::class.java) { ActionDefinition.parse("version=2\nread-colors board 1,2") }
        assertThrows(IllegalArgumentException::class.java) { ActionDefinition.parse("version=1\nscreen=1080x2400@0\nread-colors board 1,2") }
    }

    @Test fun theValueFitsInAVariable() {
        // 1 点 RRGGBB (6 文字) と区切りの空白。上限の点数でも変数の長さに収まること。
        val value = List(ActionDefinition.MAX_READ_COLOR_POINTS) { "ffffff" }.joinToString(" ")
        assertTrue(value.length <= ActionDefinition.MAX_VARIABLE_CHARS)
    }
}
