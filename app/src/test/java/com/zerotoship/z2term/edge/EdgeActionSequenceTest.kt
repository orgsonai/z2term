package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeActionSequenceTest {
    private val actions = listOf(EdgeActions.Action(EdgeActions.Type.BACK), EdgeActions.Action(EdgeActions.Type.HOME))

    @Test fun waitsForCompletionAndIgnoresDuplicateCallbacks() {
        val sequence = EdgeActionSequence()
        val calls = mutableListOf<EdgeActions.Type>()
        val callbacks = mutableListOf<(String?) -> Unit>()
        sequence.start(actions, { action, done -> calls.add(action.type); callbacks.add(done); null }, { fail(it) })
        assertEquals(listOf(EdgeActions.Type.BACK), calls)
        callbacks[0](null)
        callbacks[0](null)
        assertEquals(listOf(EdgeActions.Type.BACK, EdgeActions.Type.HOME), calls)
        assertTrue(sequence.running)
        callbacks[1](null)
        assertFalse(sequence.running)
    }

    @Test fun cancellationStopsCurrentWorkAndInvalidatesLateCompletion() {
        val sequence = EdgeActionSequence()
        var done: ((String?) -> Unit)? = null
        var calls = 0
        var cancelled = 0
        sequence.start(actions, { _, callback ->
            calls++; done = callback
            val cancel: () -> Unit = { cancelled++ }
            cancel
        }, { fail(it) })
        sequence.cancel()
        done!!(null)
        assertEquals(1, cancelled)
        assertEquals(1, calls)
        assertFalse(sequence.running)
    }

    @Test fun failureStopsRemainingActionsAndNewRunRejectsOldCallbacks() {
        val sequence = EdgeActionSequence()
        val callbacks = mutableListOf<(String?) -> Unit>()
        val errors = mutableListOf<String>()
        val run: (EdgeActions.Action, (String?) -> Unit) -> (() -> Unit)? = { _, done -> callbacks.add(done); null }
        sequence.start(actions, run, { errors.add(it) })
        val old = callbacks[0]
        sequence.start(actions, run, { errors.add(it) })
        old(null)
        assertEquals(2, callbacks.size)
        callbacks[1]("rejected")
        assertEquals(listOf("rejected"), errors)
        assertFalse(sequence.running)
        assertEquals(2, callbacks.size)
    }

    @Test fun synchronousCompletionDoesNotReplaceTheNextActionsCancelHook() {
        val sequence = EdgeActionSequence()
        var cancelled = false
        sequence.start(actions, { action, done ->
            if (action.type == EdgeActions.Type.BACK) { done(null); null }
            else { val cancel: () -> Unit = { cancelled = true }; cancel }
        }, { fail(it) })
        sequence.cancel()
        assertTrue(cancelled)
    }
}
