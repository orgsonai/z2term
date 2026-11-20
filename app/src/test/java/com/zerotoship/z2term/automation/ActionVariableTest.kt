package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

/** `set` / `add` and `var` conditions (1.0.192). */
class ActionVariableTest {
    @Test fun parsesSetAddAndVarTerms() {
        val steps = ActionDefinition.parse("version=2\nset name 残り 3 回   # 値は行の残り\nadd count -2\nif var count>3,!var name=x\n  wait 1\nend").steps
        assertEquals(ActionDefinition.Step.Set("name", "残り 3 回"), steps[0])
        assertEquals(ActionDefinition.Step.Set("count", "-2", add = true), steps[1])
        val terms = ActionCondition.parse("var count>3,!var name=x").terms
        assertEquals(ActionCondition.Term.Var(false, "count", ">", "3"), terms[0])
        assertEquals(ActionCondition.Term.Var(true, "name", "=", "x"), terms[1])
        for (body in listOf("set count", "set 1x 3", "add count", "add count 1.5", "add count x", "add count 1 2",
            "if var count>x\nwait 1\nend", "if var count\nwait 1\nend")) {
            assertThrows(body, IllegalArgumentException::class.java) { ActionDefinition.parse("version=2\n$body") }
        }
        assertThrows(IllegalArgumentException::class.java) { ActionDefinition.parse("version=1\nset a 1") }
    }

    @Test fun comparesNumbersAndText() {
        fun matches(spec: String, vars: Map<String, String>): Boolean? {
            var answer: Boolean? = null
            ActionCondition.evaluate(ActionCondition.parse(spec), { emptyMap() }, { _, _ -> error("no probe") },
                variables = { vars }) { matched, _ -> answer = matched }
            return answer
        }
        assertEquals(true, matches("var count=0", emptyMap()))       // 入れていない変数は 0
        assertEquals(true, matches("var count>3", mapOf("count" to "4")))
        assertEquals(false, matches("var count<3", mapOf("count" to "4")))
        assertEquals(true, matches("var count=4.0", mapOf("count" to "4")))
        assertEquals(true, matches("var name=OK", mapOf("name" to "OK")))
        assertEquals(false, matches("var name=ok", mapOf("name" to "OK")))
        assertEquals(null, matches("var name>1", mapOf("name" to "OK")))  // 数でないものを比べたら失敗
        assertThrows(IllegalStateException::class.java) { ActionCondition.matches("var a=1", emptyMap()) }
    }

    @Test fun countsAcrossLoopsAndCallsAndStartsEmpty() {
        val tasks = mutableListOf<() -> Unit>()
        val engine = ActionExecution({ _, block -> tasks += block; val cancel: () -> Unit = {}; cancel })
        val results = mutableListOf<ActionExecution.Result>()
        val bump = ActionDefinition.parse("version=2\ntimeout=0\nadd done 1")
        val root = ActionDefinition.parse("version=2\ntimeout=0\nrepeat until var done>2\n  call bump\nend\nset name 終わり")
        fun run() {
            engine.start(root, { _, done -> done(null); null }, { _, _ -> }, { results += it }, resolve = { bump },
                condition = { spec ->
                    val term = ActionCondition.parse(spec).terms.single() as ActionCondition.Term.Var
                    ActionCondition.variableMatches(term, engine.variables)
                })
            while (tasks.isNotEmpty()) tasks.removeAt(0)()
        }
        run()
        assertEquals("completed", results.last().state)
        assertEquals(mapOf("done" to "3", "name" to "終わり"), engine.variables.toMap())
        run()
        assertEquals("completed", results.last().state)
        assertEquals("3", engine.variables["done"])
    }

    @Test fun addingToTextFailsAndTryCatchesIt() {
        val tasks = mutableListOf<() -> Unit>()
        val engine = ActionExecution({ _, block -> tasks += block; val cancel: () -> Unit = {}; cancel })
        var result: ActionExecution.Result? = null
        engine.start(ActionDefinition.parse("version=2\ntimeout=0\nset a x\ntry\n  add a 1\nelse\n  set a 0\nend\nadd a 1"),
            { _, done -> done(null); null }, { _, _ -> }, { result = it })
        while (tasks.isNotEmpty()) tasks.removeAt(0)()
        assertEquals("completed", result?.state)
        assertEquals("1", engine.variables["a"])
    }
}
