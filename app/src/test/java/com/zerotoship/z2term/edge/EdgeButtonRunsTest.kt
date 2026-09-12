package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeButtonRunsTest {
    private val item = EdgeStore.Item("timer", mapOf("run" to "sleep 2", "button-state" to "on", "button-source" to "process"))
    private val run = EdgeButtonRuns.Run("main", item, 0)

    @Test fun completedOrStoppedRunsDoNotSurviveReopeningOrRestart() {
        val runs = EdgeButtonRuns()
        assertFalse(runs.running("main", item))
        runs.start("first", run)
        assertTrue(runs.running("main", item))
        runs.finish("first") // Completion is also used for failures and timeouts.
        assertFalse(runs.running("main", item))
        runs.start("second", run)
        runs.cancel("main", item.id)
        assertFalse(runs.running("main", item))
        assertNull(runs.get("second"))
        runs.start("third", run)
        runs.clear()
        assertFalse(runs.running("main", item))
    }

    @Test fun lateCompletionAndApiCallsCannotAffectARenewedRun() {
        val runs = EdgeButtonRuns()
        runs.start("old", run)
        runs.start("new", run)
        assertNull(runs.get("old"))
        runs.finish("old")
        assertTrue(runs.running("main", item))
        assertEquals(run, runs.get("new"))
        assertFalse(runs.running("main", item.copy(fields = item.fields + ("run" to "sleep 3"))))
        assertFalse(runs.running("other", item))
    }
}
