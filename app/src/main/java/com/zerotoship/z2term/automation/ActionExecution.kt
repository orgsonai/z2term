package com.zerotoship.z2term.automation

/** One run on a single event loop. Calls and loops share cancellation and the root deadline. */
internal class ActionExecution(private val schedule: (Long, () -> Unit) -> (() -> Unit)) {
    data class Result(val state: String, val step: Int, val error: String? = null)
    private data class Frame(val steps: List<ActionDefinition.Step>, val macro: String, val prefix: String = "",
        val repeats: Int? = 1, val loop: Boolean = false, val call: Boolean = false,
        var index: Int = 0, var iteration: Int = 1, var cancelTimeout: (() -> Unit)? = null)
    private val frames = mutableListOf<Frame>()
    var running = false
        private set
    var step = 0
        private set
    var macro = ""
        private set
    var path = ""
        private set
    var iteration = 1
        private set
    var callDepth = 1
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
        frames.forEach { it.cancelTimeout?.invoke() }; frames.clear()
        val done = completed; completed = null
        runCatching { cancel?.invoke() }
        done?.invoke(Result(state, step, error))
    }
    fun start(definition: ActionDefinition, execute: (ActionDefinition.Step, (String?) -> Unit) -> (() -> Unit)?,
        progress: (Int, ActionDefinition.Step) -> Unit, done: (Result) -> Unit,
        resolve: (String) -> ActionDefinition = { error("Unresolved macro: $it") },
        condition: (String) -> Boolean = { error("Condition evaluator unavailable") },
        name: String = "", branch: (String, Boolean) -> Unit = { _, _ -> }) {
        check(!running) { "An action macro is already running" }
        running = true; step = 0; completed = done
        macro = name; path = ""; iteration = 1; callDepth = 1
        frames += Frame(definition.steps, name)
        val token = ++generation
        cancelDeadline = schedule(definition.timeoutMs) { if (token == generation) finish("timeout", "Macro timed out") }
        fun push(frame: Frame) {
            check(frames.size < ActionProgram.MAX_FRAME_DEPTH) { "Execution nesting limit reached" }
            frames += frame
        }
        fun advance() {
            if (!running || token != generation) return
            cancelNext = null
            while (frames.isNotEmpty() && frames.last().index == frames.last().steps.size) {
                val frame = frames.last()
                if (frame.loop && (frame.repeats == null || frame.iteration < frame.repeats)) {
                    frame.index = 0; frame.iteration++
                    cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    return
                }
                frames.removeAt(frames.lastIndex).cancelTimeout?.invoke()
            }
            if (frames.isEmpty()) { finish("completed"); return }
            val frame = frames.last()
            val action = frame.steps[frame.index++]
            macro = frame.macro; path = frame.prefix + frame.index
            iteration = frames.lastOrNull { it.loop }?.iteration ?: 1
            callDepth = 1 + frames.count { it.call }
            if (step >= MAX_EXECUTED_STEPS) { finish("failed", "Execution exceeds $MAX_EXECUTED_STEPS instructions"); return }
            val index = ++step
            var delivered = false
            val next: (String?) -> Unit = next@{ error ->
                if (delivered || !running || token != generation || index != step) return@next
                delivered = true; cancelStep = null
                if (error != null) finish("failed", error)
                else cancelNext = schedule(0) { advance() }
            }
            try {
                progress(index, action)
                if (!running || token != generation) return
                when (action) {
                    is ActionDefinition.Step.Repeat -> {
                        push(Frame(action.body, macro, "$path.", action.count, loop = true))
                        cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    }
                    is ActionDefinition.Step.Branch -> {
                        val matched = condition(action.condition)
                        if (!running || token != generation) return
                        branch(action.condition, matched)
                        if (!running || token != generation) return
                        val branchPath = "$path." + (if (matched) "yes." else "no.")
                        push(Frame(if (matched) action.yes else action.no, macro, branchPath))
                        cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    }
                    is ActionDefinition.Step.Call -> {
                        check(frames.none { it.macro == action.name }) { "Recursive macro call: " + action.name }
                        check(callDepth < ActionProgram.MAX_CALL_DEPTH) { "Macro call depth exceeded" }
                        val called = resolve(action.name)
                        val child = Frame(called.steps, action.name, call = true)
                        push(child)
                        child.cancelTimeout = schedule(called.timeoutMs) {
                            if (token == generation) finish("timeout", "Called macro timed out: " + action.name)
                        }
                        cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    }
                    else -> {
                        val cancel = execute(action, next)
                        if (!delivered && running && token == generation && index == step) cancelStep = cancel
                        else if (!delivered) runCatching { cancel?.invoke() }
                    }
                }
            } catch (e: Exception) { next(e.message ?: "Action failed") }
        }
        cancelNext = schedule(80) { advance() }
    }
    companion object {
        const val MAX_EXECUTED_STEPS = 10000
        const val CONTROL_DELAY_MS = 20L
    }
}
