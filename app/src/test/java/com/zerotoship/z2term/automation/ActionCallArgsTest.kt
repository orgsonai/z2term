package com.zerotoship.z2term.automation

import com.zerotoship.z2term.edge.EdgeCommandScript
import org.junit.Assert.*
import org.junit.Test

/** `call 名前 値…` and `$1`… in the called macro (1.0.191). */
class ActionCallArgsTest {
    private val screen = ActionDefinition.Screen(1080, 2400, 0)
    private val tapAt = "version=2\nscreen=1080x2400@0\ntarget org.example.app\ntap px $1 $2   # 値の座標\nclick text=$3\ncommand echo \"$1\" $#\n"

    @Test fun callsCarryValues() {
        val steps = ActionDefinition.parse("version=2\ncall tap-at 540 1200 OK\ncall plain").steps
        assertEquals(ActionDefinition.Step.Call("tap-at", listOf("540", "1200", "OK")), steps[0])
        assertEquals(ActionDefinition.Step.Call("plain"), steps[1])
        assertNotEquals(steps[0].let { (it as ActionDefinition.Step.Call).key }, "tap-at")
        assertThrows(IllegalArgumentException::class.java) {
            ActionDefinition.parse("version=2\ncall x " + (1..10).joinToString(" "))
        }
    }

    @Test fun templatesSaveAndBindWithRealValues() {
        val saved = ActionDefinition.parse(tapAt, screen)
        assertTrue(saved.templated)
        val bound = saved.bind(listOf("540", "1200", "OK"))
        val tap = bound.steps[0] as ActionDefinition.Step.Stroke
        assertEquals(540f, tap.x1); assertEquals(1200f, tap.y1)
        assertEquals("text=OK", (bound.steps[1] as ActionDefinition.Step.Ui).selector.toString())
        // シェルの行は置き換えない (シェル自身の $1 として渡す)。
        assertEquals(ActionDefinition.Step.Command("echo \"$1\" $#"), bound.steps[2])
        assertEquals(listOf("540", "1200", "OK"), bound.args)
        assertThrows(IllegalArgumentException::class.java) { saved.bind(listOf("540")) }
        assertThrows(IllegalArgumentException::class.java) { saved.bind(listOf("99999", "1", "OK")) }
    }

    @Test fun aLineThatOnlyWorksWithItsValueStillSaves() {
        val saved = ActionDefinition.parse("version=2\nscreen=current\nlaunch $1\nlong-press percent 50 50 $2", screen)
        assertTrue(saved.templated)
        val bound = saved.bind(listOf("org.example.app", "700"))
        assertEquals(ActionDefinition.Step.Launch("org.example.app"), bound.steps[0])
        assertEquals(700L, (bound.steps[1] as ActionDefinition.Step.Stroke).ms)
        assertThrows(IllegalArgumentException::class.java) { saved.bind(listOf("bad name", "700")) }
    }

    @Test fun plainDefinitionsKeepTheirValuesForTheShell() {
        val plain = ActionDefinition.parse("version=1\ncommand echo $1")
        assertFalse(plain.templated)
        assertEquals(listOf("a"), plain.bind(listOf("a")).args)
    }

    @Test fun programsFreezeEachCallWithItsValues() {
        val sources = mapOf("root" to "version=2\ncall tap-at 10 20 A\ncall tap-at 30 40 B\ncall tap-at 10 20 A",
            "tap-at" to tapAt)
        val reads = mutableListOf<String>()
        val program = ActionProgram.load("root") { reads += it; ActionDefinition.parse(sources.getValue(it), screen) }
        assertEquals(listOf("root", "tap-at"), reads)
        assertEquals(3, program.definitions.size)
        val first = program.definitions.getValue(ActionDefinition.callKey("tap-at", listOf("10", "20", "A")))
        assertEquals(10f, (first.steps[0] as ActionDefinition.Step.Stroke).x1)
        // 値の足りない呼び出しは実行前に断る。
        val missing = mapOf("root" to "version=2\ncall tap-at 1", "tap-at" to tapAt)
        assertThrows(IllegalArgumentException::class.java) {
            ActionProgram.load("root") { ActionDefinition.parse(missing.getValue(it), screen) }
        }
        // 最初のマクロにも値を渡せる。
        val root = ActionProgram.load("tap-at", listOf("5", "6", "C")) { ActionDefinition.parse(sources.getValue(it), screen) }
        assertEquals(5f, (root.root.steps[0] as ActionDefinition.Step.Stroke).x1)
        assertThrows(IllegalArgumentException::class.java) {
            ActionProgram.load("tap-at") { ActionDefinition.parse(sources.getValue(it), screen) }
        }
    }

    @Test fun valuesPassThroughToNestedCallsAndRunOutput() {
        val sources = mapOf("root" to "version=2\ncall outer 7", "outer" to "version=2\ncall inner $1 $1", "inner" to "version=2\nwait $2")
        val program = ActionProgram.load("root") { ActionDefinition.parse(sources.getValue(it)) }
        assertEquals(listOf(ActionDefinition.Step.Wait(7)),
            program.definitions.getValue(ActionDefinition.callKey("inner", listOf("7", "7"))).steps)
        assertEquals(listOf(ActionDefinition.Step.Wait(9)),
            ActionDefinition.fragment("wait $1", null, ActionDefinition.CURRENT_TARGET, listOf("9")))
        assertThrows(IllegalArgumentException::class.java) {
            ActionDefinition.fragment("wait $1", null, ActionDefinition.CURRENT_TARGET)
        }
    }

    @Test fun theEngineExposesTheValuesOfTheRunningMacro() {
        val tasks = mutableListOf<() -> Unit>()
        val engine = ActionExecution({ _, block -> tasks += block; val cancel: () -> Unit = {}; cancel })
        val seen = mutableListOf<List<String>>()
        var result: ActionExecution.Result? = null
        val child = ActionDefinition.parse("version=1\ntimeout=0\nwait 1").bind(listOf("x", "y"))
        engine.start(ActionDefinition.parse("version=2\ntimeout=0\nwait 2\nrepeat 1\n  call child x y\nend"), { _, done ->
            seen += engine.args; done(null); null
        }, { _, _ -> }, { result = it }, resolve = { child })
        while (tasks.isNotEmpty()) tasks.removeAt(0)()
        assertEquals("completed", result?.state)
        assertEquals(listOf(emptyList(), listOf("x", "y")), seen)
    }

    @Test fun shellStepsReceiveValuesAsPositionalParameters() {
        val script = EdgeCommandScript.create("t1", "echo \"$1\"", null, null, positional = listOf("a b", "c"))
        assertTrue(script.contains("z2-edge 'a b' 'c'"))
    }
}
