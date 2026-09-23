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

    @Test fun arrowsStayInTheRowAndThePickerOrDragChangesRows() {
        val start = listOf(listOf("a", "b", "c"), listOf("n"))
        assertEquals(listOf(listOf("b", "a", "c"), listOf("n")), EdgeRows.shift(start, "a", 1))
        assertFalse(EdgeRows.canShift(start, "a", -1))
        assertFalse(EdgeRows.canShift(start, "n", 1))
        // Moving to another row takes one step, wherever it lands.
        assertEquals(listOf(listOf("a", "b"), listOf("n", "c")), EdgeRows.insert(start, "c", 1, 9))
        assertEquals(listOf(listOf("b", "c"), listOf("a", "n")), EdgeRows.insert(start, "a", 1, 0))
        // Reordering within a row counts positions among the other items.
        assertEquals(listOf(listOf("b", "c", "a"), listOf("n")), EdgeRows.insert(start, "a", 0, 2))
        // A gap between rows makes a new row there; an emptied row disappears.
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("n")), EdgeRows.newRow(start, "c", 1))
        assertEquals(listOf(listOf("n"), listOf("a", "b", "c")), EdgeRows.newRow(start, "n", 0))
        assertEquals(setOf("a", "b"), EdgeRows.companions(start, "c"))
        assertEquals(emptySet<String>(), EdgeRows.companions(start, "n"))
    }

    @Test fun widthsBecomeProportionsThatAddUpToTheRow() {
        assertEquals(listOf(50f, 50f), EdgeRows.weights(listOf(null, null), 360f))
        assertEquals(listOf(20f, 40f, 40f), EdgeRows.weights(listOf("20%", null, ""), 360f))
        // dp is a share of the panel width; when the given widths fill the row, the rest take the average.
        assertEquals(listOf(50f, 50f, 50f), EdgeRows.weights(listOf("180", "50%", null), 360f))
        assertEquals(listOf(25, 25, 50), EdgeRows.percents(listOf(1f, 1f, 2f)))
        assertEquals(100, EdgeRows.percents(listOf(1f, 1f, 1f)).sum())
    }

    @Test fun iconsFollowTheRowHeightAndNeverOverflowTheCell() {
        // No size given and room to spare: the panel's icon size and padding stay.
        assertNull(EdgeRows.iconFit(40, 72f, null, labelled = false))
        // A narrow cell shrinks the icon; a tall row enlarges it, still within the cell width.
        assertEquals(26, EdgeRows.iconFit(40, 30f, null, labelled = false))
        assertEquals(60, EdgeRows.iconFit(40, 120f, 64f, labelled = false))
        assertEquals(46, EdgeRows.iconFit(40, 50f, 64f, labelled = false))
        assertEquals(20, EdgeRows.iconFit(40, 200f, 24f, labelled = true))
        assertEquals(12, EdgeRows.iconFit(40, 8f, null, labelled = false))
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
