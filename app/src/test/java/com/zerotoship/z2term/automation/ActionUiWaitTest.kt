package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionUiWaitTest {
    private class Clock {
        data class Task(val time: Long, val order: Int, val block: () -> Unit, var cancelled: Boolean = false)
        val tasks = mutableListOf<Task>()
        var now = 0L
        private var order = 0
        fun schedule(delay: Long, block: () -> Unit): () -> Unit {
            val task = Task(now + delay, order++, block); tasks += task
            return { task.cancelled = true }
        }
        fun advance(to: Long) {
            var guard = 0
            while (true) {
                check(guard++ < 1000)
                val task = tasks.filter { !it.cancelled && it.time <= to }
                    .minWithOrNull(compareBy<Task> { it.time }.thenBy { it.order }) ?: break
                tasks.remove(task); now = task.time; task.block()
            }
            now = to
        }
    }
    @Test fun onlyMissingResultsAreRetriedAndSuccessFinishesExactlyOnce() {
        val clock = Clock(); val results = mutableListOf<String?>()
        var count = 0
        ActionUiWait(clock::schedule).start(1000, { done ->
            val cancel: () -> Unit = {}
            count++; done(count == 3, null); done(true, "duplicate")
            cancel
        }, results::add)
        clock.advance(2000)
        assertEquals(3, count)
        assertEquals(listOf<String?>(null), results)
    }
    @Test fun timeoutCancelsAStalledLookupAndIgnoresItsLateResult() {
        val clock = Clock(); val results = mutableListOf<String?>()
        var late: ((Boolean, String?) -> Unit)? = null; var cancellations = 0
        ActionUiWait(clock::schedule).start(500, { done -> late = done; { cancellations++ } }, results::add)
        clock.advance(500)
        assertEquals(1, cancellations)
        assertTrue(results.single().orEmpty().contains("timed out"))
        late!!(true, null); clock.advance(2000)
        assertEquals(1, results.size)
    }
    @Test fun stopIsSilentAndLookupErrorsAreNeverRetried() {
        for (stop in listOf(true, false)) {
            val clock = Clock(); val results = mutableListOf<String?>(); var count = 0
            var callback: ((Boolean, String?) -> Unit)? = null
            val cancel = ActionUiWait(clock::schedule).start(1000, { done -> count++; callback = done; {} }, results::add)
            clock.advance(0)
            if (stop) cancel() else callback!!(false, "Target changed")
            callback!!(true, null); clock.advance(2000)
            assertEquals(1, count)
            assertEquals(if (stop) emptyList<String?>() else listOf("Target changed"), results)
        }
    }
}
