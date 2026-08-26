package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLayoutEditingTest {

    @Test fun updateKey_changesOnlyTheSelectedNestedKey() {
        val layout = splitLayout()
        val path = KeyCellPath(0, 0, listOf(1))
        val changed = layout.updateKey(path) { it.copy(label = "right!") }

        assertEquals("left", changed.keyAt(KeyCellPath(0, 0, listOf(0)))?.label)
        assertEquals("right!", changed.keyAt(path)?.label)
        assertEquals("tail", changed.keyAt(KeyCellPath(0, 1))?.label)
    }

    @Test fun invalidPath_isAlwaysANoOp() {
        val layout = splitLayout()
        assertSame(layout, layout.updateKey(KeyCellPath(9, 0)) { it.copy(label = "x") })
        assertSame(layout, layout.removeKeyCell(KeyCellPath(0, 9)))
        assertSame(layout, layout.moveSlot(KeyCellPath(0, 0), -1))
    }

    @Test fun split_canReachDepthTwoButNotThree() {
        val base = oneKeyLayout()
        val once = base.splitKey(KeyCellPath(0, 0), SplitDir.HORIZONTAL)
        val twice = once.splitKey(KeyCellPath(0, 0, listOf(0)), SplitDir.VERTICAL)
        val tooDeepPath = KeyCellPath(0, 0, listOf(0, 0))
        val three = twice.splitKey(tooDeepPath, SplitDir.HORIZONTAL)

        assertNotEquals(base, once)
        assertNotEquals(once, twice)
        assertSame(twice, three)
        assertTrue(twice.validate().isEmpty())
        assertEquals(3, twice.keyPaths().size)
    }

    @Test fun removingOneOfTwoParts_collapsesToTheOther() {
        val layout = splitLayout()
        val changed = layout.removeKeyCell(KeyCellPath(0, 0, listOf(0)))
        assertEquals("right", changed.keyAt(KeyCellPath(0, 0))?.label)
        assertTrue(changed.validate().isEmpty())
    }

    @Test fun collapseParent_keepsTheSelectedSide() {
        val changed = splitLayout().collapseParentTo(KeyCellPath(0, 0, listOf(1)))
        assertEquals("right", changed.keyAt(KeyCellPath(0, 0))?.label)
    }

    @Test fun structureAndWidthOperations_preserveValidLayout() {
        val base = splitLayout()
        val widened = base.updateSlotWidth(KeyCellPath(0, 1), KeyWidth.Fixed(2f))
        assertEquals(KeyWidth.Fixed(2f), widened.rows[0].slots[1].width)

        val moved = widened.moveSlot(KeyCellPath(0, 1), -1)
        assertEquals("tail", moved.keyAt(KeyCellPath(0, 0))?.label)

        val withKey = moved.appendKey(0)
        assertEquals(3, withKey.rows[0].slots.size)
        val withRow = withKey.insertRowAfter(0)
        assertEquals(2, withRow.rows.size)
        assertTrue(withRow.validate().isEmpty())
        assertEquals(1, withRow.removeRow(0).rows.size)
    }

    @Test fun bulkOperations_changeEverySelectedKeyAndSharedWidthOnce() {
        val layout = splitLayout()
        val left = KeyCellPath(0, 0, listOf(0))
        val right = KeyCellPath(0, 0, listOf(1))
        val tail = KeyCellPath(0, 1)

        val changed = layout
            .updateKeys(setOf(left, tail)) { it.copy(repeatable = true) }
            .updateSlotWidths(setOf(left, right, tail), KeyWidth.Fixed(1.6f))

        assertTrue(changed.keyAt(left)!!.repeatable)
        assertTrue(changed.keyAt(tail)!!.repeatable)
        assertEquals(false, changed.keyAt(right)!!.repeatable)
        assertEquals(listOf(KeyWidth.Fixed(1.6f), KeyWidth.Fixed(1.6f)), changed.rows[0].slots.map { it.width })
    }

    @Test fun lastSlotAndLastRow_cannotBeRemoved() {
        val one = oneKeyLayout()
        assertSame(one, one.removeKeyCell(KeyCellPath(0, 0)))
        assertSame(one, one.removeRow(0))
    }

    private fun oneKeyLayout() = KeyLayout(
        id = "mine",
        name = "Mine",
        rows = listOf(KeyRow(listOf(KeySlot.of(KeyDef.text("a"))))),
    )

    private fun splitLayout() = KeyLayout(
        id = "split",
        name = "Split",
        rows = listOf(
            KeyRow(
                listOf(
                    KeySlot(
                        SlotContent.Split(
                            SplitDir.HORIZONTAL,
                            listOf(
                                SlotPart(SlotContent.Single(KeyDef.text("left"))),
                                SlotPart(SlotContent.Single(KeyDef.text("right"))),
                            ),
                        ),
                    ),
                    KeySlot.of(KeyDef.text("tail")),
                ),
            ),
        ),
    )
}
