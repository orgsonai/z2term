package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

/** `try … else … end` (1.0.190). */
class ActionTryTest {
    private class Clock {
        private data class Task(val time: Long, val order: Int, val run: () -> Unit, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        var time = 0L
            private set
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

    @Test fun parsesTryWithAndWithoutElse() {
        val steps = ActionDefinition.parse("version=2\ntry   # 失敗に備える\n  wait 1\n  wait 2\nelse\n  wait 3\nend\ntry\n  wait 4\nend").steps
        assertEquals(ActionDefinition.Step.Try(listOf(ActionDefinition.Step.Wait(1), ActionDefinition.Step.Wait(2)),
            listOf(ActionDefinition.Step.Wait(3))), steps[0])
        assertEquals(ActionDefinition.Step.Try(listOf(ActionDefinition.Step.Wait(4)), emptyList()), steps[1])
        for (body in listOf("try\nend", "try\nwait 1\nelse\nend", "try 3\nwait 1\nend", "try\nwait 1\nelse\nwait 1\nelse\nwait 1\nend",
            "repeat 2\nwait 1\nelse\nwait 1\nend")) {
            assertThrows(body, IllegalArgumentException::class.java) { ActionDefinition.parse("version=2\n$body") }
        }
        assertThrows(IllegalArgumentException::class.java) { ActionDefinition.parse("version=1\ntry\nwait 1\nend") }
        assertTrue("try" in ActionDefinition.FLOW_WORDS)
    }

    /** Runs [source]; a `wait` of 13 ms fails. Returns the waits that were sent and the result. */
    private fun run(source: String, resolve: (String) -> ActionDefinition = { error("no $it") },
        catchable: (String) -> Boolean = { true }, failure: String = "boom"): Triple<List<Long>, ActionExecution.Result, List<String>> {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val sent = mutableListOf<Long>()
        val caught = mutableListOf<String>()
        var result: ActionExecution.Result? = null
        engine.start(ActionDefinition.parse(source), { step, done ->
            val ms = (step as ActionDefinition.Step.Wait).ms
            sent += ms
            clock.schedule(ms) { done(if (ms == 13L) failure else null) }
        }, { _, _ -> }, { result = it }, resolve = resolve, name = "root", catchable = catchable, caught = { caught += it })
        clock.advance(100_000)
        return Triple(sent, result!!, caught)
    }

    @Test fun aFailureSkipsTheRestAndRunsElseThenContinues() {
        val (sent, result, caught) = run("version=2\ntry\n  wait 1\n  wait 13\n  wait 2\nelse\n  wait 3\nend\nwait 4")
        assertEquals(listOf(1L, 13L, 3L, 4L), sent)
        assertEquals("completed", result.state)
        assertEquals(listOf("boom"), caught)
    }

    @Test fun withoutElseTheFailureIsDroppedAndWithoutFailureElseIsSkipped() {
        assertEquals(listOf(13L, 4L), run("version=2\ntry\n  wait 13\n  wait 2\nend\nwait 4").first)
        assertEquals(listOf(1L, 4L), run("version=2\ntry\n  wait 1\nelse\n  wait 3\nend\nwait 4").first)
    }

    @Test fun theInnermostTryCatchesAndAFailureInElseGoesOutward() {
        val (sent, result, caught) = run("version=2\ntry\n  try\n    wait 13\n  else\n    wait 13\n  end\n  wait 2\nelse\n  wait 3\nend")
        assertEquals(listOf(13L, 13L, 3L), sent)
        assertEquals("completed", result.state)
        assertEquals(2, caught.size)
        val (_, failed, _) = run("version=2\ntry\n  wait 13\nelse\n  wait 13\nend\nwait 4")
        assertEquals("failed", failed.state)
    }

    @Test fun failuresInsideLoopsAndCalledMacrosAreCaught() {
        val child = ActionDefinition.parse("version=1\nwait 1\nwait 13\nwait 2")
        val (sent, result, _) = run("version=2\nrepeat 2\n  try\n    call child\n  else\n    wait 3\n  end\nend\nwait 4", resolve = { child })
        assertEquals(listOf(1L, 13L, 3L, 1L, 13L, 3L, 4L), sent)
        assertEquals("completed", result.state)
    }

    @Test fun refusedFailuresAndUncaughtOnesEndTheRun() {
        val (sent, result, caught) = run("version=2\ntry\n  wait 13\nelse\n  wait 3\nend", catchable = { it != "Screen is off or locked" },
            failure = "Screen is off or locked")
        assertEquals(listOf(13L), sent)
        assertEquals("failed", result.state)
        assertTrue(caught.isEmpty())
        assertEquals("failed", run("version=2\nwait 13\nwait 4").second.state)
    }

    @Test fun stopAndTimeoutAreNotCaught() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        var result: ActionExecution.Result? = null
        engine.start(ActionDefinition.parse("version=2\ntimeout=1\ntry\n  wait 5000\nelse\n  wait 3\nend"), { step, done ->
            clock.schedule((step as ActionDefinition.Step.Wait).ms) { done(null) }
        }, { _, _ -> }, { result = it })
        clock.advance(10_000)
        assertEquals("timeout", result!!.state)
    }

    @Test fun blockEditorKnowsTryAndItsFailureBranch() {
        val document = ActionBlockDocument("version=2\ntry\n  wait 1\nend\n")
        val item = document.root.items.single()
        assertEquals("try", item.body!!.branch)
        val added = document.addElse(item.line)
        assertEquals("version=2\ntry\n  wait 1\nelse\n  wait 800\nend\n", added)
        val branch = ActionBlockDocument(added).root.items.single().otherwise!!
        assertEquals("else", branch.branch)
        assertEquals("1.else.1", branch.items.single().path)
        ActionDefinition.parse(added)
    }
}
