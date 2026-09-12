package com.zerotoship.z2term.service

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class TorchObservationTest {
    @Test fun externalChangesReplaceTheStateUsedByTheNextToggle() {
        val state = TorchObservation()
        assertNull(state.current())
        state.update(false)
        state.update(true) // Another application enabled the flashlight.
        assertFalse(!state.await(timeoutMillis = 0)) // Next toggle turns it off.
        state.update(false)
        assertTrue(!state.await(timeoutMillis = 0))
        state.update(null)
        assertNull(state.current())
    }

    @Test fun completionWaitsForTheCallbackAndDoesNotInventSuccess() {
        val state = TorchObservation()
        state.update(false)
        val waiting = FutureTask { state.await(true, 2000) }
        Thread(waiting).start()
        state.update(true)
        assertTrue(waiting.get(3, TimeUnit.SECONDS))
        state.update(false)
        assertFalse(state.await(true, 0)) // An external OFF wins over the requested ON.
    }

    @Test(expected = IllegalStateException::class)
    fun unknownInitialStateCannotBeTreatedAsOff() {
        TorchObservation().await(timeoutMillis = 0)
    }
}
