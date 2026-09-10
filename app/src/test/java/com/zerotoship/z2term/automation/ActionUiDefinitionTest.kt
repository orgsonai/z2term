package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionUiDefinitionTest {
    @Test fun exactSelectorsRetainSpacesAndEqualsWithoutShellQuoting() {
        val selector = ActionSelector.parse("text=  Continue = next  ")
        assertEquals("Continue = next", selector.value)
        assertTrue(selector.matches(null, " Continue = next ", null))
        assertFalse(selector.matches(null, "continue = next", null))
        assertFalse(selector.matches(null, "Continue", "Continue = next"))
        assertTrue(ActionSelector.parse("desc=Open menu").matches(null, null, "Open menu"))
        assertTrue(ActionSelector.parse("id=org.example.app:id/open").matches("org.example.app:id/open", null, null))
        for (source in listOf("text=", "unknown=value", "id=open", "id=../app", "text=line\nbreak", "desc=" + "x".repeat(257))) {
            assertThrows(source, IllegalArgumentException::class.java) { ActionSelector.parse(source) }
        }
    }
    @Test fun uiStepsRequireV2AndTargetButDoNotRequireGeometry() {
        val source = "version=2\nlaunch org.example.app\nwait-ui 5000 text=Ready now\nclick id=org.example.app:id/next\nlong-click desc=More options\n"
        val parsed = ActionDefinition.parse(source)
        assertNull(parsed.screen)
        val ui = parsed.steps.filterIsInstance<ActionDefinition.Step.Ui>()
        assertEquals(listOf("wait-ui", "click", "long-click"), ui.map { it.operation })
        assertEquals("Ready now", ui.first().selector.value)
        assertEquals(5000L, ui.first().timeoutMs)
        assertTrue(ui.all { it.target == "org.example.app" })
        assertEquals(parsed, ActionDefinition.parse(parsed.text))
        for (invalid in listOf(source.replace("version=2", "version=1"), "version=2\nclick text=Next",
            source.replace("5000", "0"), source.replace("5000", "30001"), source.replace("5000", "bad"))) {
            assertThrows(IllegalArgumentException::class.java) { ActionDefinition.parse(invalid) }
        }
    }
    @Test fun uiTargetsFollowLexicalBranchesAndScreenCanBeRemovedInTheEditor() {
        val parsed = ActionDefinition.parse("version=2\ntarget org.example.outer\nif charging\nlaunch org.example.inner\nclick text=One\nelse\nclick text=Two\nend\nclick text=Three")
        assertEquals(listOf("org.example.inner", "org.example.outer", "org.example.outer"),
            ActionDefinition.allSteps(parsed.steps).filterIsInstance<ActionDefinition.Step.Ui>().map { it.target })
        val source = "# retain\r\nversion=2\r\nscreen=current\r\ntarget org.example.app\r\nclick text=Next\r\n"
        val edited = ActionEditorDocument(source).withoutScreen()
        assertEquals(source.replace("screen=current\r\n", ""), edited)
        assertNull(ActionDefinition.parse(edited).screen)
        assertNull(ActionEditorDocument(ActionEditorDocument.EMPTY).header("screen"))
    }
}
