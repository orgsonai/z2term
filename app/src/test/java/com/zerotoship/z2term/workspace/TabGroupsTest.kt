package com.zerotoship.z2term.workspace

import org.junit.Assert.*
import org.junit.Test

class TabGroupsTest {
    private val name = { n: Int -> "Group $n" }
    private fun groupOf(vararg ids: String, max: Int = 4): Pair<TabGroups, String> {
        val (created, id) = TabGroups().create(name)
        return ids.fold(created) { st, s -> st.add(id, s, max)!! } to id
    }

    @Test fun singleMemberGroupIsAPlainTabAndTwoAreStacked() {
        val (one, id) = groupOf("a")
        assertNull(one.stackedFor("a"))
        assertEquals(listOf(TabUnit.Solo("a"), TabUnit.Solo("b")), one.units(listOf("a", "b")))
        val two = one.add(id, "b", 4)!!
        assertEquals(listOf("a", "b"), two.stackedFor("b")!!.members)
        assertEquals(1, two.units(listOf("a", "b")).size)
    }

    @Test fun aTabBelongsToOneGroupAndAFullGroupRefuses() {
        val (st, first) = groupOf("a", "b", max = 2)
        assertNull(st.add(first, "c", 2))
        val (st2, second) = st.create(name)
        val moved = st2.add(second, "b", 2)!!
        assertEquals(listOf("a"), moved.group(first)!!.members)
        assertEquals(listOf("b"), moved.group(second)!!.members)
    }

    @Test fun groupAppearsWhereItsFirstMemberIsAndKeepsMembersTogether() {
        val (st, id) = groupOf("d", "b")
        val order = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "d", "b", "c"), st.ordered(order))
        val units = st.units(order)
        assertEquals("a", (units[0] as TabUnit.Solo).id)
        assertEquals(id, (units[1] as TabUnit.Group).group.id)
    }

    @Test fun movingKeepsEachPaneShare() {
        val (st, id) = groupOf("a", "b", "c")
        val resized = st.resize(id, 0, 0.1f)
        val moved = resized.move(id, 0, 2)
        assertEquals(listOf("b", "c", "a"), moved.group(id)!!.members)
        assertEquals(resized.group(id)!!.weightOf("a"), moved.group(id)!!.weightOf("a"), 1e-6f)
    }

    @Test fun dividerTradesShareOnlyBetweenNeighboursAndNeverHidesAPane() {
        val (st, id) = groupOf("a", "b", "c", "d")
        val g = st.resize(id, 1, 10f).group(id)!!
        assertEquals(0.25f, g.weightOf("a"), 1e-6f)
        assertEquals(0.25f, g.weightOf("d"), 1e-6f)
        assertEquals(0.1f, g.weightOf("c"), 1e-6f)
        assertEquals(1f, g.weights.sum(), 1e-5f)
        assertEquals(st, st.resize(id, 1, Float.NaN))
        assertEquals(st, st.resize(id, 3, 0.1f))
    }

    @Test fun maximizeAppliesOnlyToTheMaximizedTab() {
        val (st, id) = groupOf("a", "b")
        val g = st.maximize(id, "a").group(id)!!
        assertEquals(listOf("a"), g.panes("a"))
        assertEquals(listOf("a", "b"), g.panes("b"))
        assertEquals(listOf("a", "b"), st.maximize(id, "a").maximize(id, null).group(id)!!.panes("a"))
        assertNull(st.maximize(id, "zz").group(id)!!.maximized)
    }

    @Test fun closingTabsShrinksGroupsAndDropsEmptyOnesUnlessEditing() {
        val (st, id) = groupOf("a", "b", "c")
        val withFocus = st.focus("b").maximize(id, "b")
        val pruned = withFocus.prune(setOf("a", "c"))
        assertEquals(listOf("a", "c"), pruned.group(id)!!.members)
        assertNull(pruned.group(id)!!.maximized)
        assertEquals("a", pruned.group(id)!!.entry())
        assertTrue(st.prune(emptySet()).groups.isEmpty())
        assertEquals(1, st.prune(emptySet(), keepEmpty = true).groups.size)
        assertSame(st, st.prune(setOf("a", "b", "c")))
    }

    @Test fun selectingTheGroupReturnsToTheLastFocusedTab() {
        val (st, id) = groupOf("a", "b")
        assertEquals("a", st.group(id)!!.entry())
        assertEquals("b", st.focus("b").group(id)!!.entry())
    }

    @Test fun renameIgnoresBlankNames() {
        val (st, id) = groupOf("a")
        assertEquals("Group 1", st.rename(id, "   ").group(id)!!.name)
        assertEquals("Work", st.rename(id, " Work ").group(id)!!.name)
    }

    @Test fun limitFollowsTheScreenWithTheKeyboardOpen() {
        // 915dp phone, 320dp keyboard, 13sp: (915 - 320 - 150) / (42 + 4 * 13 * 1.2) = 4.26
        assertEquals(4, groupMaxTabsFor(915f, 320f, 13f, 1f, 2, 8))
        // A large tablet reaches the cap; a tiny screen keeps the floor.
        assertEquals(8, groupMaxTabsFor(2000f, 320f, 13f, 1f, 2, 8))
        assertEquals(2, groupMaxTabsFor(400f, 320f, 13f, 1f, 2, 8))
    }

    @Test fun middleEllipsisKeepsBothEnds() {
        assertEquals("short", middleEllipsis("short", 12))
        assertEquals("abcde…uvwxyz", middleEllipsis("abcdefghijklmnopqrstuvwxyz", 12))
    }

    // The rule agreed for broadcast: ON and typing in a ticked tab → every ticked tab;
    // typing in an unticked tab, or OFF → that tab alone.
    @Test fun broadcastGoesToTickedTabsOnlyWhenTypingInATickedTab() {
        val (st, id) = groupOf("a", "b", "c")
        val ticked = st.checkOnly(id, listOf("a", "c"))
        val all = { _: String -> true }
        assertEquals(listOf("a", "c"), broadcastTargets(ticked, on = true, from = "c", eligible = all))
        assertEquals(listOf("b"), broadcastTargets(ticked, on = true, from = "b", eligible = all))
        assertEquals(listOf("a"), broadcastTargets(ticked, on = false, from = "a", eligible = all))
        // A tab outside any stacked group never fans out.
        assertEquals(listOf("z"), broadcastTargets(ticked, on = true, from = "z", eligible = all))
    }

    @Test fun broadcastSkipsTabsThatCannotReceiveButKeepsTheFocusedOne() {
        val (st, id) = groupOf("a", "b", "c")
        val ticked = st.checkOnly(id, listOf("a", "b", "c"))
        assertEquals(listOf("a", "c"), broadcastTargets(ticked, on = true, from = "a") { it != "b" })
        assertEquals(listOf("b"), broadcastTargets(ticked, on = true, from = "b") { it == "zz" })
    }

    @Test fun ticksFollowMembership() {
        val (st, id) = groupOf("a", "b", "c")
        val ticked = st.checkOnly(id, listOf("a", "b", "x"))
        assertEquals(setOf("a", "b"), ticked.group(id)!!.checked)
        assertEquals(setOf("a"), ticked.removeMember("b").group(id)!!.checked)
        assertEquals(setOf("b"), ticked.prune(setOf("b", "c")).group(id)!!.checked)
        assertEquals(setOf("a", "b", "c"), ticked.setChecked("c", true).group(id)!!.checked)
        assertEquals(setOf("b"), ticked.setChecked("a", false).group(id)!!.checked)
        // A tab joining later is not ticked by itself.
        val (st2, other) = ticked.create(name)
        assertEquals(setOf("a", "b"), st2.add(id, "d", 4)!!.group(id)!!.checked)
        assertTrue(st2.add(other, "a", 4)!!.group(other)!!.checked.isEmpty())
    }

    @Test fun tabNameLengthFollowsTheWidthAndTheDefaultKeepsTwelve() {
        assertEquals(12, tabNameChars(84f))
        assertEquals(6, tabNameChars(48f))
        assertEquals(28, tabNameChars(200f))
        assertEquals(3, tabNameChars(1f))
        assertEquals(3, tabNameChars(Float.NaN))
    }
}
