package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionControlExecutionTest {
    private class Clock {
        data class Task(val time: Long, val order: Int, val run: () -> Unit, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        private var time = 0L
        private var order = 0
        fun schedule(delay: Long, block: () -> Unit): () -> Unit {
            val task = Task(time + delay, order++, block); tasks += task
            return { task.cancelled = true }
        }
        fun advance(to: Long) {
            var dispatched = 0
            while (true) {
                check(dispatched++ < 100000) { "Scheduler spun without yielding" }
                val task = tasks.filter { !it.cancelled && it.time <= to }
                    .minWithOrNull(compareBy<Task> { it.time }.thenBy { it.order }) ?: break
                tasks.remove(task); time = task.time; task.run()
            }
            time = to
        }
    }
    private fun definition(body: String, timeout: Int = 30) = ActionDefinition.parse("version=2\ntimeout=$timeout\n$body")

    @Test fun loopsEvaluateConditionsAgainAndRunOnlyTheSelectedBranch() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val values = mutableListOf<Long>(); val decisions = mutableListOf<Boolean>()
        var charging = true
        val results = mutableListOf<ActionExecution.Result>()
        engine.start(definition("repeat 2\nif charging\nwait 1\nelse\nwait 2\nend\nend\nwait 9"),
            { step, done -> values += (step as ActionDefinition.Step.Wait).ms; charging = false; done(null); null },
            { _, _ -> }, results::add, condition = { charging }, name = "root", branch = { _, value -> decisions += value })
        clock.advance(1000)
        assertEquals(listOf(1L, 2L, 9L), values)
        assertEquals(listOf(true, false), decisions)
        assertEquals("completed", results.single().state)
        assertEquals(6, results.single().step)
    }

    @Test fun branchPathsDistinguishElseAndExcludeTargetDirectives() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val paths = mutableListOf<String>()
        val results = mutableListOf<ActionExecution.Result>()
        engine.start(definition("target org.example.app\nif charging\nwait 1\nelse\nwait 2\nend"),
            { _, done -> done(null); null }, { _, _ -> paths += engine.path }, results::add,
            condition = { false }, name = "root")
        clock.advance(1000)
        assertEquals(listOf("1", "1.no.1"), paths)
        assertEquals("completed", results.single().state)
    }

    @Test fun foreverYieldsAndStillHonorsTheRootDeadline() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val results = mutableListOf<ActionExecution.Result>()
        var instructions = 0
        engine.start(definition("repeat forever\nwait 0\nend", 1), { _, done -> done(null); null },
            { _, _ -> instructions++ }, results::add)
        clock.advance(1000)
        assertEquals("timeout", results.single().state)
        assertTrue(instructions in 2..100)
    }

    @Test fun stopCancelsTheChildAndLateCompletionCannotAdvanceAnotherRun() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val results = mutableListOf<ActionExecution.Result>()
        var late: ((String?) -> Unit)? = null; var cancelled = 0; var started = 0
        engine.start(definition("call child\nwait 9"), { _, done -> late = done; { cancelled++ } },
            { _, _ -> }, results::add, resolve = { definition("repeat forever\nwait 0\nend") }, name = "root")
        clock.advance(120)
        assertEquals("child", engine.macro)
        assertEquals(2, engine.callDepth)
        engine.stop()
        engine.start(definition("wait 7"), { _, _ -> started++; null }, { _, _ -> }, results::add)
        late!!(null); clock.advance(200)
        assertEquals(1, started); assertEquals(1, cancelled)
        assertEquals("cancelled", results.single().state)
        engine.stop()
    }

    @Test fun childDeadlineStopsRootAndCompletedChildTimersAreRemoved() {
        for (completeChild in listOf(false, true)) {
            val clock = Clock(); val engine = ActionExecution(clock::schedule)
            val results = mutableListOf<ActionExecution.Result>(); var cancelled = 0
            engine.start(definition("call child\nwait 9", 5),
                { step, done -> if ((step as ActionDefinition.Step.Wait).ms == 1L && completeChild) {
                    done(null); null
                } else { { cancelled++ } } },
                { _, _ -> }, results::add, resolve = { definition("wait 1", 1) }, name = "root")
            clock.advance(1500)
            if (completeChild) {
                assertTrue(engine.running); assertTrue(results.isEmpty()); engine.stop()
            } else {
                assertEquals("timeout", results.single().state)
                assertTrue(results.single().error.orEmpty().contains("child"))
            }
            assertEquals(1, cancelled)
        }
    }

    @Test fun progressCancellationAndUnknownConditionsNeverDispatchActions() {
        for (stopFromProgress in listOf(true, false)) {
            val clock = Clock(); val engine = ActionExecution(clock::schedule)
            val results = mutableListOf<ActionExecution.Result>(); var dispatched = 0
            engine.start(definition("if wifi\nwait 1\nelse\nwait 2\nend"),
                { _, _ -> dispatched++; null }, { _, _ -> if (stopFromProgress) engine.stop() }, results::add,
                condition = { error("State unavailable") })
            clock.advance(500)
            assertEquals(0, dispatched)
            assertEquals(if (stopFromProgress) "cancelled" else "failed", results.single().state)
        }
    }

    @Test fun expandedWorkIsLimitedWithoutMaterializingTheLoop() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val results = mutableListOf<ActionExecution.Result>()
        engine.start(definition("repeat 10000\nwait 0\nend", 300), { _, done -> done(null); null }, { _, _ -> }, results::add)
        clock.advance(300000)
        assertEquals("failed", results.single().state)
        assertEquals(ActionExecution.MAX_EXECUTED_STEPS, results.single().step)
    }
}
