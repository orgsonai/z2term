package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionBlockDocumentTest {
    @Test fun movingAndDuplicatingWholeBlocksPreservesCommentsQuotesAndCrLf() {
        val prefix = "# header\r\nversion=2\r\nwait 1\r\n"
        val block = "# block note\r\nrepeat 2\r\n  command printf '%s' '\$HOME # literal'\r\nend\r\n"
        val source = prefix + block + "wait 3\r\n"
        val document = ActionBlockDocument(source)
        val item = document.root.items[1]
        val moved = document.move(item.line, 1)
        assertEquals(prefix + "wait 3\r\n" + block, moved)
        val duplicate = document.duplicate(item.line)
        assertEquals(prefix + block + block + "wait 3\r\n", duplicate)
        assertEquals(4, ActionDefinition.parse(duplicate).steps.size)
        assertEquals(prefix + "wait 3\r\n", document.remove(item.line))
    }
    @Test fun movingAcrossBranchesAdjustsOffsetsAndKeepsEachEndPaired() {
        val source = "version=2\nif charging\n  wait 1\n  wait 2\nelse\n  wait 3\nend\nwait 4\n"
        val document = ActionBlockDocument(source)
        val branch = document.root.items.first()
        val moved = document.moveTo(branch.body!!.items.first().line, branch.otherwise!!.start)
        val parsed = ActionDefinition.parse(moved).steps.first() as ActionDefinition.Step.Branch
        assertEquals(listOf(ActionDefinition.Step.Wait(2)), parsed.yes)
        assertEquals(listOf(ActionDefinition.Step.Wait(3), ActionDefinition.Step.Wait(1)), parsed.no)
        val next = ActionBlockDocument(moved)
        val elseItem = next.root.items.first().otherwise!!.items.last()
        val returned = ActionBlockDocument(next.moveTo(elseItem.line, next.root.start))
        assertEquals("wait 1", returned.text.lines()[returned.root.items.last().line].trim())
        ActionDefinition.parse(returned.text)
        assertThrows(IllegalStateException::class.java) { document.moveTo(branch.line, branch.body!!.start) }
    }
    @Test fun optionalElseAndEmptyDraftBodiesCanBeEditedWithoutBreakingStructure() {
        val source = "version=2\nif charging\n  wait 1\nend\n"
        val document = ActionBlockDocument(source)
        val withElse = document.addElse(document.root.items.single().line)
        val branch = ActionBlockDocument(withElse).root.items.single()
        assertNotNull(branch.otherwise)
        assertEquals(source, ActionBlockDocument(withElse).removeElse(branch.line))
        val empty = ActionBlockDocument(document.remove(document.root.items.single().body!!.items.single().line))
        val repaired = empty.insert(empty.root.items.single().body!!.start, "repeat 2\n  wait 3\nend")
        val parsed = ActionDefinition.parse(repaired).steps.single() as ActionDefinition.Step.Branch
        assertTrue(parsed.yes.single() is ActionDefinition.Step.Repeat)
        val changed = ActionEditorDocument(repaired).replace(ActionEditorDocument(repaired).rows.first { it.text.trim() == "wait 3" }.line, "wait 4")
        assertTrue(changed.contains("    wait 4\n"))
    }
    @Test fun branchDestinationsHaveDistinctPathsAndTargetsDoNotShiftExecutionNumbers() {
        val source = "version=2\ntarget org.example.app\nif charging\nrepeat 1\nwait 0\nend\nelse\nrepeat 1\nwait 0\nend\nend"
        val document = ActionBlockDocument(source)
        val branch = document.root.items.last()
        assertEquals("1", branch.path)
        assertEquals("1.yes.1", branch.body!!.items.single().path)
        assertEquals("1.no.1", branch.otherwise!!.items.single().path)
        assertEquals(document.groups.size, document.groups.map { it.path to it.branch }.distinct().size)
    }
    @Test fun rejectsBrokenDelimitersAndKeepsHeadersSeparateWhenAddingFirstStep() {
        for (source in listOf("version=2\nend", "version=2\nif charging\nwait 1", "version=2\nrepeat 2\nelse\nwait 1\nend")) {
            assertThrows(IllegalArgumentException::class.java) { ActionBlockDocument(source) }
        }
        val empty = ActionBlockDocument("version=1\ntimeout=30")
        assertEquals("version=1\ntimeout=30\nwait 1", empty.insert(empty.root.start, "wait 1"))
        val document = ActionBlockDocument("version=2\nrepeat 1\n  wait 0\nend\n")
        assertEquals(1, document.destinations(document.root.items.single().line).size)
    }
}
