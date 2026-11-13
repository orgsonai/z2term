package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionExecutionTest {
    private class Clock {
        data class Task(val time: Long, val order: Int, val run: () -> Unit, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        private var time = 0L
        private var order = 0
        fun schedule(delay: Long, block: () -> Unit): () -> Unit {
            val task = Task(time + delay, order++, block)
            tasks += task
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
        fun now() = time
    }
    private fun definition() = ActionDefinition.parse("version=1\ntimeout=1\nwait 10\nwait 20")
    @Test fun completionNotInvocationAdvancesAndDuplicateCallbacksAreIgnored() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val callbacks = mutableListOf<(String?) -> Unit>(); val results = mutableListOf<ActionExecution.Result>()
        engine.start(definition(), { _, done -> callbacks += done; null }, { _, _ -> }, results::add)
        clock.advance(80)
        assertEquals(1, callbacks.size)
        callbacks[0](null); callbacks[0](null)
        clock.advance(80)
        assertEquals(2, callbacks.size)
        callbacks[0]("late failure"); callbacks[1](null)
        clock.advance(80)
        assertEquals("completed", results.single().state)
        assertEquals(2, results.single().step)
        assertFalse(engine.running)
    }
    @Test fun pauseCancelsTheStepHoldsTheDeadlineAndResumeSendsTheStepAgain() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule, clock::now)
        val callbacks = mutableListOf<(String?) -> Unit>(); val results = mutableListOf<ActionExecution.Result>()
        var cancelled = 0; val indexes = mutableListOf<Int>()
        engine.start(definition(), { _, done -> callbacks += done; { cancelled++ } }, { index, _ -> indexes += index }, results::add)
        clock.advance(80)
        assertTrue(engine.pause()); assertFalse(engine.pause())
        assertEquals(1, cancelled)
        callbacks[0](null)
        // Longer than timeout=1 (s): a paused run does not time out and sends nothing.
        clock.advance(5000)
        assertTrue(engine.running); assertTrue(results.isEmpty()); assertEquals(1, callbacks.size)
        assertTrue(engine.resume()); assertFalse(engine.resume())
        clock.advance(5020)
        assertEquals(2, callbacks.size)
        assertEquals(listOf(1, 1), indexes)
        callbacks[1](null); clock.advance(5040)
        callbacks[2](null); clock.advance(5060)
        assertEquals("completed", results.single().state)
        assertEquals(2, results.single().step)
    }
    @Test fun resumedRunStillTimesOutWithTheTimeLeftBeforeThePause() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule, clock::now)
        val results = mutableListOf<ActionExecution.Result>()
        engine.start(definition(), { _, _ -> null }, { _, _ -> }, results::add)
        clock.advance(400); engine.pause()
        clock.advance(10_000); engine.resume()
        clock.advance(10_599)
        assertTrue(results.isEmpty())
        clock.advance(10_600)
        assertEquals("timeout", results.single().state)
    }
    @Test fun stopCancelsCurrentStepAndInvalidatesItsLateCallbackAcrossRuns() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        var oldCallback: ((String?) -> Unit)? = null
        var cancelled = 0; var nextStarted = 0
        val results = mutableListOf<ActionExecution.Result>()
        engine.start(definition(), { _, done -> oldCallback = done; { cancelled++ } }, { _, _ -> }, results::add)
        clock.advance(80); engine.stop()
        engine.start(definition(), { _, _ -> nextStarted++; null }, { _, _ -> }, results::add)
        oldCallback!!(null); clock.advance(160)
        assertEquals(1, nextStarted); assertEquals(1, cancelled)
        assertEquals("cancelled", results.single().state)
        engine.stop()
    }
    @Test fun deadlineCancelsAndFailureNeverStartsSuccessor() {
        for (timeout in listOf(true, false)) {
            val clock = Clock(); val engine = ActionExecution(clock::schedule)
            var started = 0; var cancelled = 0
            val results = mutableListOf<ActionExecution.Result>()
            engine.start(definition(), { _, done -> started++; if (!timeout) done("rejected"); { cancelled++ } },
                { _, _ -> }, results::add)
            clock.advance(1001)
            assertEquals(1, started)
            assertEquals(if (timeout) "timeout" else "failed", results.single().state)
            assertEquals(if (timeout) 1 else 0, cancelled)
        }
    }
    @Test fun busyRunIsRejectedAndReentrantStopStillCancelsReturnedWork() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        var cancelled = false
        val results = mutableListOf<ActionExecution.Result>()
        engine.start(definition(), { _, _ -> engine.stop(); { cancelled = true } }, { _, _ -> }, results::add)
        try { engine.start(definition(), { _, _ -> null }, { _, _ -> }, {}); fail("Concurrent run accepted") }
        catch (_: IllegalStateException) { }
        clock.advance(80)
        assertTrue(cancelled)
        assertEquals("cancelled", results.single().state)
    }
}
