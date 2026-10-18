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

    @Test fun colorNamesAreCoarseButStable() {
        fun name(hex: String) = UiBridgePolicy.colorName(hex.removePrefix("#").toInt(16))
        assertEquals("red", name("#e03020"))
        assertEquals("pink", name("#f070c0"))
        assertEquals("pink", name("#f09090"))
        assertEquals("orange", name("#f08020"))
        assertEquals("brown", name("#804010"))
        assertEquals("yellow", name("#f0e040"))
        assertEquals("green", name("#30c040"))
        assertEquals("cyan", name("#20c0d0"))
        assertEquals("blue", name("#2060e0"))
        assertEquals("purple", name("#9030d0"))
        assertEquals("white", name("#f4f4f4"))
        assertEquals("gray", name("#808080"))
        assertEquals("black", name("#101010"))
        assertTrue(listOf("#e03020", "#808080").map(::name).all { it in UiBridgePolicy.COLOR_NAMES })
        assertEquals("#0a0b0c", UiBridgePolicy.hex(0xff0a0b0c.toInt()))
    }

    @Test fun sameIsStricterThanSettled() {
        val before = IntArray(100) { 120 }
        // 全体のわずかな揺れ (平均 1) でも、1 点だけの大きな変化 (数字の書き換えなど) は「同じ」にしない。
        val oneDigit = before.copyOf().also { it[42] = 200 }
        assertTrue(UiBridgePolicy.settled(before, oneDigit, width = 10))
        assertFalse(UiBridgePolicy.same(before, oneDigit))
        assertTrue(UiBridgePolicy.same(before, IntArray(100) { 120 + 8 }))
        assertFalse(UiBridgePolicy.same(before, IntArray(99) { 120 }))
    }

    @Test fun settledCatchesALocalAnimation() {
        // 48 x 20 の画面のうち 8 x 8 (面積 7%) だけが大きく変わる。全体の平均差は 3 未満でも、まだ動いている。
        val width = 48
        val still = IntArray(width * 20) { 120 }
        val local = still.copyOf().also { a -> for (y in 4 until 12) for (x in 8 until 16) a[y * width + x] = 160 }
        assertTrue((0 until still.size).sumOf { kotlin.math.abs(still[it] - local[it]) }.toDouble() / still.size < UiBridgePolicy.SETTLE_THRESHOLD)
        assertFalse(UiBridgePolicy.settled(still, local, width))
        // 区画を見ない判定 (loose) なら、1.0.41 までと同じく止まったとみる。
        assertTrue(UiBridgePolicy.settled(still, local, width, blocks = false))
        // まばらなきらめき (1 区画に 1 点、差 20) は無視する。
        val sparkle = still.copyOf().also { a -> for (y in 0 until 20 step 4) for (x in 0 until width step 4) a[y * width + x] = 140 }
        assertTrue(UiBridgePolicy.settled(still, sparkle, width))
    }

    @Test fun untilConditions() {
        fun until(vararg w: String) = UiBridgePolicy.Until.parse(w.toList())
        val dark = intArrayOf(0x202020, 0x302010)
        val bright = intArrayOf(0xe03020, 0x30c040)
        val brighter = until("brighter", "60")!!
        assertEquals(2, brighter.second)
        assertFalse(brighter.first.met(dark, dark))
        assertTrue(brighter.first.met(bright, dark))
        assertTrue(until("darker", "60")!!.first.met(dark, bright))
        // 色の名前がどこか 1 点でも変われば changed。
        assertFalse(until("changed")!!.first.met(dark, dark))
        assertTrue(until("changed")!!.first.met(intArrayOf(0x202020, 0x30c040), dark))
        assertTrue(until("green")!!.first.met(intArrayOf(0x30c040, 0x20b030), dark))
        assertFalse(until("green")!!.first.met(bright, dark))
        assertNull(until("brighter", "300"))
        assertNull(until("shiny"))
    }

    @Test fun spreadSplitsWordsJoinedIntoOneArgument() {
        assertEquals(listOf("540,1200", "600,1300", "--step", "60"), UiBridgePolicy.spread(listOf("540,1200 600,1300 ", "--step", "60")))
        assertEquals(listOf("--until", "brighter", "125"), UiBridgePolicy.spread(listOf("--until", "brighter 125")))
        assertEquals(listOf("a", "b"), UiBridgePolicy.spread(listOf("  a\tb", "")))
    }

    @Test fun cellsBecomePointsOnTheGrid() {
        val grid = listOf("--cells", "100,1516", "177,177", "6x5")
        assertEquals(listOf("454,1693", "454,1516", "985,2224", "--step", "60"),
            UiBridgePolicy.cells("drag", grid + listOf("c3r2", "c3r1", "c6r5", "--step", "60")))
        // tap / long は座標の target (@X,Y) にする。格子を 1 語にまとめても読む。
        assertEquals(listOf("@277,2224", "--shot"),
            UiBridgePolicy.cells("tap", listOf("--cells", "100,1516 177,177 6x5", "c2r5", "--shot")))
        // --cells が無ければ触らない。
        assertEquals(listOf("@540,1200"), UiBridgePolicy.cells("tap", listOf("@540,1200")))
        assertEquals("bad_args", refusal { UiBridgePolicy.cells("drag", grid + listOf("c7r1", "c1r1")) })
        assertEquals("bad_args", refusal { UiBridgePolicy.cells("type", grid + listOf("c1r1")) })
        assertEquals("bad_args", refusal { UiBridgePolicy.cells("tap", listOf("--cells", "100,1516", "177,177")) })
    }

    @Test fun medianIgnoresAHighlight() {
        // 赤い 8 点に白い光沢が 1 点。平均なら薄まるが、中央値は赤のまま。
        val red = 0xe03020
        val pixels = IntArray(9) { if (it == 4) 0xffffff else red }
        assertEquals(red, UiBridgePolicy.median(pixels))
        assertEquals(0, UiBridgePolicy.median(IntArray(0)))
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
        // 始点で止める分は、始点を重ねて再現する (50 ms を 40 ms 刻みなら 2 区間)。
        assertEquals("drag px 40 1,2 1,2 1,2 3,4", step("drag", "1,2", "3,4", "--hold", "50", "--step", "40").line)
        // 座標の長押しは指を置いた長さのまま。z2-action の長押しの範囲 (500..3000) を外れる長さは保存を断る。
        assertEquals("long-press px 5 6 600", step("long", "@5,6").line)
        assertEquals("long-press px 5 6 1500", step("long", "@5,6", "--hold", "1500").line)
        assertEquals("long --hold 5000", step("long", "@5,6", "--hold", "5000").unsupported)
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
