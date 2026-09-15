package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionForegroundWaitTest {
    private class Clock {
        data class Task(val time: Long, val order: Int, val block: () -> Unit, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        private var now = 0L
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
    @Test fun editorAndUnknownFocusDoNotStartActionsAndAppChangesResetSettling() {
        val clock = Clock(); val results = mutableListOf<String?>()
        var focused: String? = "org.example.editor"
        ActionForegroundWait(clock::schedule).start("org.example.editor", { focused }, "No foreground", results::add)
        clock.advance(1000)
        assertTrue(results.isEmpty())
        focused = null; clock.advance(1250)
        focused = "org.example.first"; clock.advance(1500)
        focused = "org.example.second"; clock.advance(2000)
        assertTrue(results.isEmpty())
        clock.advance(2250)
        assertEquals(listOf<String?>(null), results)
        focused = "org.example.third"; clock.advance(10000)
        assertEquals(1, results.size)
    }
    @Test fun timeoutAndCancellationNeverStartActionsOnALateForegroundChange() {
        for (stop in listOf(true, false)) {
            val clock = Clock(); val results = mutableListOf<String?>()
            var focused: String? = null
            val cancel = ActionForegroundWait(clock::schedule).start("org.example.editor", { focused }, "No foreground", results::add)
            clock.advance(250)
            if (stop) cancel()
            clock.advance(5000)
            focused = "org.example.other"; clock.advance(10000)
            assertEquals(if (stop) emptyList<String?>() else listOf("No foreground"), results)
        }
    }
    @Test fun deviceCheckFailureStopsWaiting() {
        val clock = Clock(); val results = mutableListOf<String?>()
        ActionForegroundWait(clock::schedule).start("org.example.editor", { error("Screen locked") }, "No foreground", results::add)
        clock.advance(10000)
        assertEquals(listOf("Screen locked"), results)
    }
}
