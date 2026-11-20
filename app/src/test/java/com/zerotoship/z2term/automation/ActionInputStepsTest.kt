package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionInputStepsTest {
    private fun parse(body: String, version: Int = 2) = ActionDefinition.parse("version=$version\n$body").steps

    @Test fun typeEnterAndTapUiParse() {
        val steps = parse("target org.example.app\ntap ui text=検索\ntype 東京 # 駅 の天気\ntype\nenter\ntap ui desc~=メニュー")
        assertEquals(ActionDefinition.Step.UiTap("org.example.app", ActionSelector("text", "検索")), steps[0])
        assertEquals(ActionDefinition.Step.Input("org.example.app", "東京 # 駅 の天気"), steps[1])   // # も文字として入れる
        assertEquals(ActionDefinition.Step.Input("org.example.app", ""), steps[2])                  // 空は消す
        assertEquals(ActionDefinition.Step.Input("org.example.app", null), steps[3])                // 確定キー
        assertEquals(ActionSelector("desc", "メニュー", true), (steps[4] as ActionDefinition.Step.UiTap).selector)
        // 座標の tap は今までどおり (画面情報が要る)
        assertTrue(ActionDefinition.parse("version=2\nscreen=1080x2400@0\ntap px 1 2").steps.single() is ActionDefinition.Step.Stroke)
    }

    @Test fun rejectsWrongForms() {
        for (body in listOf("enter now", "tap ui", "tap ui name=x", "type " + "あ".repeat(ActionDefinition.MAX_TYPE_CHARS + 1))) {
            assertThrows(body, IllegalArgumentException::class.java) { parse(body) }
        }
        for (body in listOf("type a", "enter", "tap ui text=OK")) {
            assertThrows(body, IllegalArgumentException::class.java) { parse(body, version = 1) }
        }
    }

    @Test fun inputAndTapUiOnTheForegroundWaitForItAtGuiStart() {
        fun program(body: String) = ActionProgram.load("root") { ActionDefinition.parse("version=2\n$body") }
        assertTrue(program("type a").usesCurrentTarget)
        assertTrue(program("tap ui text=OK").usesCurrentTarget)
        assertFalse(program("target org.example.app\ntype a").usesCurrentTarget)
    }

    @Test fun colouringTreatsTypedTextLikeShell() {
        assertNull(ActionDefinition.commentStart("type a # b"))
        assertEquals(listOf<Triple<Int, Int, Boolean>>(), ActionSyntaxColors.ranges("type a # b"))
    }
}
