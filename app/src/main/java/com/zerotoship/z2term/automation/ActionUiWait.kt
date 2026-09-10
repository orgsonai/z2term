package com.zerotoship.z2term.automation

/** Bounded asynchronous lookup. A cancelled or late lookup never starts a successor. */
internal class ActionUiWait(private val schedule: (Long, () -> Unit) -> (() -> Unit)) {
    fun start(timeoutMs: Long, probe: ((Boolean, String?) -> Unit) -> (() -> Unit),
        done: (String?) -> Unit): () -> Unit {
        require(timeoutMs in 1..30000)
        var active = true
        var generation = 0
        var cancelQuery: (() -> Unit)? = null
        var cancelNext: (() -> Unit)? = null
        var cancelTimeout: (() -> Unit)? = null
        fun finish(error: String?, notify: Boolean = true) {
            if (!active) return
            active = false; generation++
            cancelNext?.invoke(); cancelTimeout?.invoke()
            val cancel = cancelQuery; cancelQuery = null
            runCatching { cancel?.invoke() }
            if (notify) done(error)
        }
        fun query() {
            if (!active) return
            cancelNext = null
            val token = ++generation
            var delivered = false
            try {
                val cancel = probe { found, error ->
                    if (active && !delivered && token == generation) {
                        delivered = true; cancelQuery = null
                        if (error != null || found) finish(error)
                        else cancelNext = schedule(250) { query() }
                    }
                }
                if (!delivered && active && token == generation) cancelQuery = cancel
                else if (!delivered) cancel()
            } catch (e: Exception) { finish(e.message ?: "UI lookup failed") }
        }
        cancelTimeout = schedule(timeoutMs) { finish("UI element wait timed out") }
        cancelNext = schedule(0) { query() }
        return { finish(null, notify = false) }
    }
}
