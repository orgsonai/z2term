package com.zerotoship.z2term.edge

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class EdgeRowsTest {
    private fun item(id: String, type: String = "run", row: Int? = null) =
        EdgeStore.Item(id, mapOf("type" to type) + (row?.let { mapOf("row" to it.toString()) } ?: emptyMap()))
    private fun ids(rows: List<List<EdgeStore.Item>>) = rows.map { line -> line.map { it.id } }

    @Test fun legacyFlowsBecomeTheRowsTheyAlreadyDrew() {
        val items = listOf(item("a"), item("b"), item("c"), item("n", "note"))
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c"), listOf("n")), ids(EdgeRows.derived(items, "vertical", null, 4)))
        assertEquals(listOf(listOf("a", "b", "c", "n")), ids(EdgeRows.derived(items, "horizontal", null, 4)))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "n")), ids(EdgeRows.derived(items, "grid", null, 2)))
        // flow unset with a grid tab: only apps form the grid, the rest stacks below.
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("n")), ids(EdgeRows.derived(items, "", "grid", 2)))
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c"), listOf("n")), ids(EdgeRows.derived(items, "free", null, 2)))
    }

    @Test fun savedRowsFollowTheirNumbersAndUnnumberedItemsGoLast() {
        val items = listOf(item("n", "note", 2), item("a", row = 1), item("x"), item("b", row = 1))
        assertTrue(EdgeRows.arranged(items))
        assertEquals(listOf(listOf("a", "b"), listOf("n"), listOf("x")), ids(EdgeRows.saved(items)))
        // An arranged tab ignores the shared flow of its parent.
        assertEquals(ids(EdgeRows.saved(items)), ids(EdgeRows.of(items, "horizontal", null, 4)))
    }

    @Test fun arrowsSplitAndJoinRowsSoEveryArrangementIsReachable() {
        val start = listOf(listOf("a", "b", "c"), listOf("n"))
        // Apps in one row with a note below: step the last app out into a row of its own…
        val split = EdgeRows.move(start, "c", 1)
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("n")), split)
        // …then into the note's row, and back up again.
        assertEquals(listOf(listOf("a", "b"), listOf("c", "n")), EdgeRows.move(split, "c", 1))
        assertEquals(start, EdgeRows.move(split, "c", -1))
        assertEquals(listOf(listOf("b", "a", "c"), listOf("n")), EdgeRows.move(start, "a", 1))
        assertEquals(listOf(listOf("a"), listOf("b", "c"), listOf("n")), EdgeRows.move(start, "a", -1))
        // Alone at either end, there is nowhere further to go.
        assertFalse(EdgeRows.canMove(start, "n", 1))
        assertFalse(EdgeRows.canMove(listOf(listOf("a"), listOf("b")), "a", -1))
        assertTrue(EdgeRows.canMove(start, "n", -1))
    }

    @Test fun droppingJoinsTheTargetRow() {
        val start = listOf(listOf("a", "b"), listOf("n"))
        assertEquals(listOf(listOf("b"), listOf("n", "a")), EdgeRows.drop(start, "a", "n", after = true))
        assertEquals(listOf(listOf("a", "b", "n")), EdgeRows.drop(start, "n", "b", after = true))
        assertEquals(start, EdgeRows.drop(start, "a", "a", after = false))
    }

    @Test fun newAppsJoinALastRowOfAppsOrStartANewOne() {
        assertNull(EdgeRows.rowForNewApp(listOf(item("a"))))
        assertEquals(1, EdgeRows.rowForNewApp(listOf(item("a", row = 1), item("b", row = 1))))
        assertEquals(3, EdgeRows.rowForNewApp(listOf(item("a", row = 1), item("n", "note", 2))))
    }

    @Test fun arrangementIsSavedPerItemAndValidated() {
        val dir = Files.createTempDirectory("edge-rows-test").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", mapOf("flow" to "horizontal"))
            listOf("a", "b").forEach { store.setItem("main:$it", mapOf("run" to "echo $it")) }
            store.setItem("main:n", mapOf("type" to "note"))
            store.arrange("main", listOf(listOf("b", "a"), listOf("n")))
            val items = store.panel("main").items
            assertEquals(listOf("b", "a", "n"), items.map { it.id })
            assertEquals(listOf("1", "1", "2"), items.map { it.fields["row"] })
            assertEquals("echo a", store.item("main:a").command)
            assertThrows(IllegalArgumentException::class.java) { store.arrange("main", listOf(listOf("a", "b"))) }
            assertThrows(IllegalArgumentException::class.java) { store.arrange("main", listOf(listOf("a", "b", "n", "a"))) }
            for (value in listOf("0", "65", "x")) {
                assertThrows(IllegalArgumentException::class.java) { EdgeStore.validateItem(mapOf("row" to value)) }
            }
        } finally { dir.deleteRecursively() }
    }
}
