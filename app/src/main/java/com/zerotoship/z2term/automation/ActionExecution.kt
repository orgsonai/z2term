package com.zerotoship.z2term.automation

/** One run on a single event loop. Late callbacks cannot advance another step. */
internal class ActionExecution(private val schedule: (Long, () -> Unit) -> (() -> Unit)) {
    data class Result(val state: String, val step: Int, val error: String? = null)
    var running = false
        private set
    var step = 0
        private set
    private var generation = 0
    private var cancelStep: (() -> Unit)? = null
    private var cancelDeadline: (() -> Unit)? = null
    private var cancelNext: (() -> Unit)? = null
    private var completed: ((Result) -> Unit)? = null
    fun stop(reason: String = "Stopped") = finish("cancelled", reason)
    private fun finish(state: String, error: String? = null) {
        if (!running) return
        running = false
        generation++
        val cancel = cancelStep; cancelStep = null
        cancelDeadline?.invoke(); cancelDeadline = null
        cancelNext?.invoke(); cancelNext = null
        val done = completed; completed = null
        runCatching { cancel?.invoke() }
        done?.invoke(Result(state, step, error))
    }
    fun start(definition: ActionDefinition, execute: (ActionDefinition.Step, (String?) -> Unit) -> (() -> Unit)?,
        progress: (Int, ActionDefinition.Step) -> Unit, done: (Result) -> Unit) {
        check(!running) { "An action macro is already running" }
        running = true; step = 0; completed = done
        val token = ++generation
        cancelDeadline = schedule(definition.timeoutMs) { if (token == generation) finish("timeout", "Macro timed out") }
        fun advance() {
            if (!running || token != generation) return
            cancelNext = null
            if (step == definition.steps.size) { finish("completed"); return }
            val action = definition.steps[step++]
            val index = step
            var delivered = false
            val next: (String?) -> Unit = next@{ error ->
                if (delivered || !running || token != generation || index != step) return@next
                delivered = true
                cancelStep = null
                if (error != null) finish("failed", error)
                else cancelNext = schedule(0) { advance() }
            }
            try {
                progress(index, action)
                val cancel = execute(action, next)
                if (!delivered && running && token == generation && index == step) cancelStep = cancel
                else if (!delivered) runCatching { cancel?.invoke() }
            } catch (e: Exception) { next(e.message ?: "Action failed") }
        }
        cancelNext = schedule(80) { advance() }
    }
}
