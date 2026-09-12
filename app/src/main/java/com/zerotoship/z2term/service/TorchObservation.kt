package com.zerotoship.z2term.service

/** Callback state; an issued command never writes a guessed value here. */
internal class TorchObservation {
    private val lock = Object()
    private var value: Boolean? = null
    fun current(): Boolean? = synchronized(lock) { value }
    fun update(on: Boolean?) = synchronized(lock) { value = on; lock.notifyAll() }

    fun await(expected: Boolean? = null, timeoutMillis: Long = 1500): Boolean = synchronized(lock) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (value == null || expected != null && value != expected) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) break
            lock.wait((remaining / 1_000_000).coerceAtLeast(1))
        }
        value ?: throw IllegalStateException("Torch state is not available yet; retry shortly")
    }
}
