package com.zerotoship.z2term.edge

/** Callback sequencing independent of Android. Cancellation invalidates late or duplicate completions. */
internal class EdgeActionSequence {
    private var generation = 0
    private var step = 0
    private var cancelStep: (() -> Unit)? = null
    var running = false
        private set

    fun cancel() {
        generation++
        running = false
        val cancel = cancelStep
        cancelStep = null
        cancel?.invoke()
    }

    fun start(actions: List<EdgeActions.Action>,
        execute: (EdgeActions.Action, (String?) -> Unit) -> (() -> Unit)?,
        failed: (String) -> Unit) {
        EdgeActions.validate(actions)
        cancel()
        running = true
        val token = generation
        step = 0
        fun advance(index: Int) {
            if (token != generation || !running) return
            if (index == actions.size) { running = false; cancelStep = null; return }
            val current = ++step
            var delivered = false
            val done: (String?) -> Unit = done@{ error ->
                if (delivered || token != generation || !running || step != current) return@done
                delivered = true
                cancelStep = null
                if (error != null) { cancel(); failed(error) } else advance(index + 1)
            }
            try {
                val cancel = execute(actions[index], done)
                if (!delivered && token == generation && running && step == current) cancelStep = cancel
            } catch (e: Exception) { done(e.message ?: "Action failed") }
        }
        advance(0)
    }
}
