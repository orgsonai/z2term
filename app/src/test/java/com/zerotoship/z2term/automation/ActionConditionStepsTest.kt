package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionConditionStepsTest {
    private val screen = ActionDefinition.Screen(1080, 2400, 0)
    private fun parse(body: String, header: String = "version=2\nscreen=current\n") = ActionDefinition.parse(header + body, screen)

    @Test fun waitUntilAndWhileTakeMillisecondsFirstAndKeepTheirTarget() {
        val steps = parse("target org.example.app\nwait-until 5000 ui text=OK|ui text=Done\nwait-while 0 color 10,20=white\n" +
            "if ui desc=Menu\n  wait 1\nend").steps
        assertEquals(ActionDefinition.Step.WaitFor("org.example.app", "ui text=OK|ui text=Done", true, 5000), steps[0])
        assertEquals(ActionDefinition.Step.WaitFor("org.example.app", "color 10,20=white", false, 0), steps[1])
        assertEquals("org.example.app", (steps[2] as ActionDefinition.Step.Branch).target)
        assertEquals(ActionDefinition.CURRENT_TARGET, (parse("if wifi\n  wait 1\nend").steps.single() as ActionDefinition.Step.Branch).target)
    }

    @Test fun rejectsBadWaitsAndColourPointsWithoutOrOutsideTheScreen() {
        for (body in listOf("wait-until ui text=OK", "wait-until 5000", "wait-until 300001 wifi", "wait-while -1 wifi",
            "wait-until 10 color 1080,0=red", "wait-until 10 color 0,2400=red")) {
            assertThrows(body, IllegalArgumentException::class.java) { parse(body) }
        }
        assertThrows(IllegalArgumentException::class.java) { parse("wait-until 10 wifi", "version=1\n") }
        assertThrows(IllegalArgumentException::class.java) { parse("if color 1,1=red\n  wait 1\nend", "version=2\n") }
        parse("wait-until 300000 color 1079,2399=red")
    }

    @Test fun aShellTermKeepsItsHashButEarlierCommentsStillWork() {
        val definition = parse("if wifi, command echo '#1' # shell\n  wait 1\nend\nwait-until 10 wifi # 待つ")
        assertEquals("wifi, command echo '#1' # shell", (definition.steps[0] as ActionDefinition.Step.Branch).condition)
        assertEquals("wifi", (definition.steps[1] as ActionDefinition.Step.WaitFor).condition)
        assertNull(ActionDefinition.commentStart("wait-until 0 command echo # x"))
        assertEquals(8, ActionDefinition.commentStart("if wifi # x, command echo"))
    }

    @Test fun screenConditionsOnTheCurrentAppWaitForTheForegroundAtGuiStart() {
        fun program(body: String) = ActionProgram.load("root") { parse(body, "version=2\nscreen=current\n") }
        assertTrue(program("wait-until 10 ui text=OK").usesCurrentTarget)
        assertTrue(program("if !color 1,1=red\n  wait 1\nend").usesCurrentTarget)
        assertFalse(program("if wifi\n  wait 1\nend").usesCurrentTarget)
        assertFalse(program("target org.example.app\nwait-until 10 ui text=OK").usesCurrentTarget)
    }

    private class Clock {
        private data class Task(val time: Long, val order: Int, val run: () -> Unit, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        private var time = 0L
        private var order = 0
        fun schedule(delay: Long, block: () -> Unit): () -> Unit {
            val task = Task(time + delay, order++, block); tasks += task
            return { task.cancelled = true }
        }
        fun advance(to: Long) {
            while (true) {
                val task = tasks.filter { !it.cancelled && it.time <= to }
                    .minWithOrNull(compareBy<Task> { it.time }.thenBy { it.order }) ?: break
                tasks.remove(task); time = task.time; task.run()
            }
            time = to
        }
    }

    @Test fun branchWaitsForAnAsynchronousAnswerAndPauseAsksAgain() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val asked = mutableListOf<Pair<String, String>>()
        val answers = mutableListOf<(Boolean?, String?) -> Unit>()
        var cancelled = 0
        val ran = mutableListOf<Long>()
        val results = mutableListOf<ActionExecution.Result>()
        engine.start(ActionDefinition.parse("version=2\ntarget org.example.app\nif ui text=OK\n  wait 1\nelse\n  wait 2\nend"),
            { step, done -> ran += (step as ActionDefinition.Step.Wait).ms; done(null); null }, { _, _ -> }, results::add,
            test = { spec, target, answer -> asked += spec to target; answers += answer; { cancelled++ } })
        clock.advance(100)
        assertEquals(listOf("ui text=OK" to "org.example.app"), asked)
        assertTrue(ran.isEmpty())
        assertTrue(engine.pause())
        assertEquals(1, cancelled)
        answers[0](true, null)                 // 一時停止で捨てた問い合わせの遅い答えは無視する
        assertTrue(engine.resume())
        clock.advance(200)
        assertEquals(2, asked.size)            // 再開で条件を最初から見直す
        answers[1](false, null)
        clock.advance(300)
        assertEquals(listOf(2L), ran)
        assertEquals("completed", results.single().state)
    }

    @Test fun aConditionErrorFailsTheRun() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val results = mutableListOf<ActionExecution.Result>()
        engine.start(ActionDefinition.parse("version=2\nif command false\n  wait 1\nend"), { _, done -> done(null); null },
            { _, _ -> }, results::add, test = { _, _, answer -> answer(null, "Cannot start shell command"); null })
        clock.advance(100)
        assertEquals(ActionExecution.Result("failed", 1, "Cannot start shell command"), results.single())
    }
}
