package com.zerotoship.z2term.automation

import com.zerotoship.z2term.automation.UiBridgePolicy.Level
import com.zerotoship.z2term.automation.UiBridgePolicy.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UiBridgePolicyTest {
    private fun refusal(block: () -> Unit): String = try { block(); fail("no refusal"); "" } catch (e: Refusal) { e.code }

    @Test fun targetsAreNumbersSelectorsOrPoints() {
        assertEquals(Target.Ref(2), UiBridgePolicy.target("#2"))
        assertEquals(Target.Point(540, 1200), UiBridgePolicy.target("@540,1200"))
        assertEquals(ActionSelector("text", "次へ"), (UiBridgePolicy.target("text=次へ") as Target.Find).selector)
        assertEquals(ActionSelector("desc", "その他 の操作"), (UiBridgePolicy.target("desc=その他 の操作") as Target.Find).selector)
        for (bad in listOf("#0", "#x", "@1", "@-1,2", "@1,2,3", "label=次へ", "id=next"))
            assertEquals(bad, "bad_args", refusal { UiBridgePolicy.target(bad) })
    }

    @Test fun protectedAppsStayOffWhateverTheList() {
        val own = setOf("com.zerotoship.z2term", "com.android.settings")
        val levels = mapOf("com.android.settings" to Level.AUTO, "*" to Level.AUTO)
        assertEquals(Level.OFF, UiBridgePolicy.level("com.android.settings", own, levels))
        assertEquals(Level.OFF, UiBridgePolicy.level("com.zerotoship.z2term", own, levels))
        assertEquals(Level.AUTO, UiBridgePolicy.level("org.example.app", own, levels))
    }

    @Test fun unlistedAppsAreReadOnlyByDefault() {
        assertEquals(Level.LOOK, UiBridgePolicy.level("org.example.app", emptySet(), emptyMap()))
        assertEquals(Level.ASK, UiBridgePolicy.level("org.example.app", emptySet(), mapOf("org.example.app" to Level.ASK)))
        assertEquals(Level.OFF, UiBridgePolicy.level("org.example.other", emptySet(), mapOf("*" to Level.OFF)))
    }

    @Test fun onlyWideningNeedsApproval() {
        assertTrue(UiBridgePolicy.widens(Level.LOOK, Level.ASK))
        assertTrue(UiBridgePolicy.widens(Level.OFF, Level.AUTO))
        assertFalse(UiBridgePolicy.widens(Level.AUTO, Level.ASK))
        assertFalse(UiBridgePolicy.widens(Level.ASK, Level.ASK))
        assertEquals("bad_args", refusal { Level.parse("all") })
    }

    @Test fun grantMinutesKeepTheHiddenRange() {
        assertEquals(1, UiBridgePolicy.grantMinutes(0))
        assertEquals(60, UiBridgePolicy.grantMinutes(60))
        assertEquals(180, UiBridgePolicy.grantMinutes(180))
        assertEquals(1440, UiBridgePolicy.grantMinutes(5000))
    }

    @Test fun waitTimesAreBounded() {
        assertEquals(5000L, UiBridgePolicy.waitMs(null))
        assertEquals(30_000L, UiBridgePolicy.waitMs("30000"))
        for (bad in listOf("0", "30001", "5s")) assertEquals(bad, "bad_args", refusal { UiBridgePolicy.waitMs(bad) })
    }

    @Test fun rolesFoldIntoASmallVocabulary() {
        fun role(cls: String?, editable: Boolean = false, password: Boolean = false, checkable: Boolean = false,
            clickable: Boolean = false, scrollable: Boolean = false) =
            UiBridgePolicy.role(cls, editable, password, checkable, clickable, scrollable)
        assertEquals("password", role("android.widget.EditText", editable = true, password = true))
        assertEquals("input", role("android.widget.EditText", editable = true))
        assertEquals("switch", role("android.widget.Switch", checkable = true, clickable = true))
        assertEquals("check", role("android.widget.CheckBox", checkable = true, clickable = true))
        assertEquals("list", role("androidx.recyclerview.widget.RecyclerView", scrollable = true))
        assertEquals("button", role("android.widget.ImageButton"))
        assertEquals("button", role("android.view.ViewGroup", clickable = true))
        assertEquals("image", role("android.widget.ImageView"))
        assertEquals("text", role("android.widget.TextView"))
        assertEquals("other", role(null))
    }

    @Test fun treeHangsElementsOnTheNearestListedAncestor() {
        // 0 = 根の直下の一覧、その中の 2 行 (間の入れ物は出していない)、別の枝のボタン
        val paths = listOf(listOf(0), listOf(1), listOf(0, 0, 3), listOf(0, 1, 0, 2))
        assertEquals(listOf(-1, -1, 0, 0), UiBridgePolicy.parents(paths).toList())
        assertEquals(listOf(-1, 0), UiBridgePolicy.parents(listOf(emptyList(), listOf(4))).toList())
    }

    @Test fun replayPrefersIdsThenTextThenCoordinates() {
        val named = mapOf(1 to UiBridgeReplay.Named("org.example.app:id/next", "次へ", null, 10, 20),
            2 to UiBridgeReplay.Named(null, "送信", null, 30, 40),
            3 to UiBridgeReplay.Named(null, "とても長い文…", null, 50, 60))
        fun step(verb: String, vararg args: String) = UiBridgeReplay.step(verb, args.toList()) { named[it] }
        assertEquals("click id=org.example.app:id/next", step("tap", "#1").line)
        assertEquals("long-click text=送信", step("long", "#2").line)
        // 切り詰めた文字は元の表示と一致しないので座標にする。
        assertEquals("tap px 50 60", step("tap", "#3").line)
        assertTrue(step("tap", "#3").coordinates)
        assertEquals("click desc=戻る", step("tap", "desc=戻る").line)
        assertEquals("launch org.example.app", step("open", "org.example.app").line)
        assertEquals("open URI", step("open", "https://example.org").unsupported)
        assertEquals("type", step("type", "#1", "abc").unsupported)
        assertEquals("wait-ui 5000 text=完了", step("wait", "text=完了").line)
        assertEquals("swipe px 1 2 3 4 300", step("swipe", "1", "2", "3", "4").line)
        assertEquals("drag px 80 10,20 30,40 50,60", step("drag", "10,20", "30,40", "--step", "80", "@50,60").line)
        assertEquals("drag px 100 1,2 3,4", step("drag", "1,2", "3,4").line)
        assertNull(step("look").line)
        assertNull(step("look").unsupported)
        assertEquals("tap #9", step("tap", "#9").unsupported)
    }

    @Test fun definitionWaitsTheRealGapsUpToTwoSeconds() {
        val text = UiBridgeReplay.definition(listOf(
            UiBridgeReplay.Step("launch org.example.app", null) to 1000L,
            UiBridgeReplay.Step("click text=次へ", null) to 1100L,
            UiBridgeReplay.Step("tap px 5 6", null, coordinates = true) to 9000L))
        assertEquals("version=2\ntimeout=32\nscreen=current\nlaunch org.example.app\nclick text=次へ\nwait 2000\ntap px 5 6\n", text)
        assertEquals("too_long", refusal { UiBridgeReplay.definition(List(65) { UiBridgeReplay.Step("key back", null) to 0L }) })
    }

    @Test fun settledIgnoresSmallFlickerButNotRealChanges() {
        val still = IntArray(100) { 120 }
        assertTrue(UiBridgePolicy.settled(still, still.copyOf()))
        // きらめき程度 (全体で平均 2 の差) は止まったとみる。
        assertTrue(UiBridgePolicy.settled(still, IntArray(100) { if (it % 10 == 0) 140 else 120 }))
        // ダイアログが開いた (4 分の 1 が大きく変わった) は動いている。
        assertFalse(UiBridgePolicy.settled(still, IntArray(100) { if (it < 25) 240 else 120 }))
        assertFalse(UiBridgePolicy.settled(IntArray(0), IntArray(0)))
        assertFalse(UiBridgePolicy.settled(still, IntArray(50) { 120 }))
    }

    @Test fun textIsCleanedAndCut() {
        assertNull(UiBridgePolicy.clean("  "))
        assertEquals("a b", UiBridgePolicy.clean("a\nb"))
        assertEquals("abc…", UiBridgePolicy.clean("abcdef", limit = 3))
    }
}
