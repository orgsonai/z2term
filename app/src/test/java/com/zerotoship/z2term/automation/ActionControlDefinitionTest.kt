package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionControlDefinitionTest {
    private val screen = ActionDefinition.Screen(100, 200, 0)
    private fun parse(body: String) = ActionDefinition.parse("version=2\nscreen=current\n$body", screen)
    @Test fun scopesTargetsAndChecksAllBranches() {
        val definition = parse("target org.example.outer\nrepeat 2\n target org.example.inner\n tap px 1 2\nend\nif charging\n launch org.example.yes\n tap px 3 4\nelse\n tap px 5 6\nend\ntap px 7 8")
        val strokes = ActionDefinition.allSteps(definition.steps).filterIsInstance<ActionDefinition.Step.Stroke>()
        assertEquals(listOf("org.example.inner", "org.example.yes", "org.example.outer", "org.example.outer"), strokes.map { it.target })
        assertEquals(definition, ActionDefinition.parse(definition.text))
        assertThrows(IllegalArgumentException::class.java) { parse("if charging\nwait 1\nelse\ntap px 100 0\nend") }
        assertThrows(IllegalArgumentException::class.java) { parse("if charging\nlaunch org.example.inner\nelse\ntap px 1 2\nend") }
    }
    @Test fun requiresV2BalancedNonemptyBoundedBlocksAndSafeNames() {
        assertThrows(IllegalArgumentException::class.java) { ActionDefinition.parse("version=1\ncall helper") }
        for (body in listOf("end", "else", "repeat 1\nwait 0", "repeat 0\nwait 0\nend", "repeat 10001\nwait 0\nend",
            "repeat forever\nend", "if wifi\nelse\nwait 0\nend", "if wifi\nwait 0\nelse\nwait 0\nelse\nwait 0\nend",
            "call ../other", "if unknown\nwait 0\nend", "repeat 1\n".repeat(9) + "wait 0\n" + "end\n".repeat(9),
            "repeat 1\n" + "wait 0\n".repeat(64) + "end")) {
            assertThrows(body, IllegalArgumentException::class.java) { parse(body) }
        }
        assertEquals(2, (parse("repeat 2\nwait 0\nend").steps.single() as ActionDefinition.Step.Repeat).count)
        assertNull((parse("repeat forever\nwait 0\nend").steps.single() as ActionDefinition.Step.Repeat).count)
    }
    @Test fun conditionsAreStrictAndMissingStateDoesNotSelectElse() {
        assertTrue(ActionCondition.matches("charging,level>20,!wifi", mapOf("charging" to "true", "level" to "80", "wifi" to "false")))
        assertFalse(ActionCondition.matches("foreground=org.example.app", mapOf("foreground" to "org.example.other")))
        for (condition in listOf("", "wifi,", ",wifi", "!missing", "level>NaN", "level", "wifi>1", "foreground=bad", "ssid=")) {
            assertThrows(condition, IllegalArgumentException::class.java) { ActionCondition.validate(condition) }
        }
        assertThrows(IllegalStateException::class.java) { ActionCondition.matches("!foreground=org.example.app", emptyMap()) }
        assertThrows(IllegalStateException::class.java) { ActionCondition.matches("level<20", mapOf("level" to "-1")) }
        assertThrows(IllegalStateException::class.java) { ActionCondition.matches("!ssid=Home", mapOf("ssid" to "")) }
    }
    @Test fun editorTemplatesKeepCommentsAndBlockRowsCannotBeMovedIndividually() {
        val source = "# keep\r\nversion=1\r\nwait 1\r\n"
        val document = ActionEditorDocument(ActionEditorDocument(source).appendControl(true))
        assertTrue(document.text.startsWith("# keep\r\nversion=2\r\n"))
        assertTrue(document.hasBlocks)
        ActionDefinition.parse(document.text)
        assertThrows(IllegalArgumentException::class.java) { document.move(document.rows.first().line, 1) }
        assertThrows(IllegalArgumentException::class.java) { document.remove(document.rows.first().line) }
        val scoped = ActionEditorDocument("version=2\ntarget org.example.outer\nrepeat 2\ntarget org.example.inner\nwait 1\nend\nwait 2")
        assertEquals("org.example.outer", scoped.targetBefore(scoped.rows.last().line))
    }
}
