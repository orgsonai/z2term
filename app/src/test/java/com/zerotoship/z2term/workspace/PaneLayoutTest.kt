package com.zerotoship.z2term.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaneLayoutTest {
    private fun leaf(id: String) = PaneNode.Leaf(id)
    private val column = flatLayout(listOf("a", "b", "c"), horizontal = false, weights = emptyList())!!

    @Test
    fun dockingOnASideMixesDirections() {
        // c goes to the right of a: the top row becomes a | c, b stays below.
        val docked = column.dock("c", "a", DockSide.RIGHT)!!
        val split = docked as PaneNode.Split
        assertEquals(false, split.horizontal)
        assertEquals(PaneNode.Split(true, listOf(leaf("a"), leaf("c")), listOf(0.5f, 0.5f)), split.children[0])
        assertEquals(leaf("b"), split.children[1])
        assertEquals(1f, split.weights.sum(), 1e-5f)
        assertEquals(listOf("a", "c", "b"), docked.ids())
    }

    @Test
    fun dockingAlongTheSameDirectionJoinsTheRowAndHalvesTheTarget() {
        val row = flatLayout(listOf("a", "b"), horizontal = true, weights = emptyList())!!
        val docked = row.plus("c").dock("c", "a", DockSide.LEFT) as PaneNode.Split
        assertEquals(true, docked.horizontal)
        assertEquals(listOf("c", "a", "b"), docked.ids())
        assertEquals(docked.weights[0], docked.weights[1], 1e-5f)
    }

    @Test
    fun centreSwapsAndSelfOrMissingDoNothing() {
        assertEquals(listOf("c", "b", "a"), column.dock("a", "c", DockSide.CENTER)!!.ids())
        assertNull(column.dock("a", "a", DockSide.LEFT))
        assertNull(column.dock("x", "a", DockSide.LEFT))
    }

    @Test
    fun removingCollapsesSplitsLeftWithOneChild() {
        val docked = column.dock("c", "a", DockSide.RIGHT)!!
        // Without c the top row has only a, so a sits directly in the column again.
        val removed = docked.without("c") as PaneNode.Split
        assertEquals(listOf(leaf("a"), leaf("b")), removed.children)
        assertEquals(1f, removed.weights.sum(), 1e-5f)
        assertNull(leaf("a").without("a"))
    }

    @Test
    fun resizingANestedSplitOnlyChangesThatSplit() {
        val docked = column.dock("c", "a", DockSide.RIGHT)!!
        val resized = docked.resized(listOf(0), 0, 0.1f) as PaneNode.Split
        val row = resized.children[0] as PaneNode.Split
        assertEquals(0.6f, row.weights[0], 1e-5f)
        assertEquals((docked as PaneNode.Split).weights, resized.weights)
        assertNull(docked.resized(listOf(5), 0, 0.1f))
    }

    @Test
    fun groupsKeepTheMixedLayoutThroughAddRemoveAndReorder() {
        var st = TabGroups()
        val (created, id) = st.create { "g$it" }
        st = listOf("a", "b", "c").fold(created) { acc, t -> acc.add(id, t, 8)!! }
        st = st.dock(id, "c", "a", DockSide.RIGHT)
        assertEquals(listOf("a", "c", "b"), st.group(id)!!.members)
        // A new tab becomes one more row at the end; the a | c row stays.
        st = st.add(id, "d", 8)!!
        assertEquals(listOf("a", "c", "b", "d"), st.group(id)!!.members)
        assertEquals(st.group(id)!!.members, st.group(id)!!.tree()!!.ids())
        st = st.removeMember("c")
        assertEquals(listOf("a", "b", "d"), st.group(id)!!.tree()!!.ids())
        st = st.prune(setOf("a", "d"))
        assertEquals(listOf("a", "d"), st.group(id)!!.tree()!!.ids())
        // Choosing a direction lays it out as one row or column again.
        assertNull(st.setHorizontal(id, true).group(id)!!.layout)
    }

    @Test
    fun sideIsTheNearestEdgeAndTheMiddleSwaps() {
        assertEquals(DockSide.LEFT, dockSideAt(10f, 50f, 0f, 0f, 100f, 100f))
        assertEquals(DockSide.BOTTOM, dockSideAt(50f, 95f, 0f, 0f, 100f, 100f))
        assertEquals(DockSide.CENTER, dockSideAt(50f, 50f, 0f, 0f, 100f, 100f))
        assertNull(dockSideAt(150f, 50f, 0f, 0f, 100f, 100f))
    }

    private fun PaneNode.plus(id: String) = appended(id, true)
}
