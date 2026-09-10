package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionProgramTest {
    private fun definition(body: String) = ActionDefinition.parse("version=2\n$body")
    @Test fun freezesDefinitionsAndRejectsMissingOrCyclicCallsEvenInUnselectedBranches() {
        val saved = mutableMapOf("root" to definition("call child\ncall child"), "child" to definition("wait 1"))
        val reads = mutableListOf<String>()
        val program = ActionProgram.load("root") { reads += it; saved.getValue(it) }
        saved["child"] = definition("wait 2")
        assertEquals(listOf("root", "child"), reads)
        assertEquals(listOf(ActionDefinition.Step.Wait(1)), program.definitions.getValue("child").steps)
        saved["child"] = definition("if wifi\ncall root\nend")
        assertThrows(IllegalArgumentException::class.java) { ActionProgram.load("root", saved::getValue) }
        saved.remove("child")
        assertThrows(NoSuchElementException::class.java) { ActionProgram.load("root", saved::getValue) }
    }
    @Test fun sharedCallGraphsAreReadOnceAndCheckedOnEveryDepth() {
        val saved = (0..7).associate { index ->
            "m$index" to definition(if (index == 7) "wait 0" else ("call m" + (index + 1) + "\n").repeat(32))
        }.toMutableMap()
        var reads = 0
        ActionProgram.load("m0") { reads++; saved.getValue(it) }
        assertEquals(8, reads)
        saved["extra"] = definition("call m0")
        assertThrows(IllegalArgumentException::class.java) { ActionProgram.load("extra", saved::getValue) }
        // A short path visits a shared node first; its later, deeper use must still fail.
        saved["root"] = definition("call m1\ncall extra")
        assertThrows(IllegalArgumentException::class.java) { ActionProgram.load("root", saved::getValue) }
    }
    @Test fun combinedBlockAndCallDepthIsBounded() {
        val open = "repeat 1\n".repeat(8)
        val close = "\n" + "end\n".repeat(8)
        val saved = mapOf("root" to definition(open + "call child" + close),
            "child" to definition(open + "wait 0" + close))
        assertThrows(IllegalArgumentException::class.java) { ActionProgram.load("root", saved::getValue) }
    }
}
