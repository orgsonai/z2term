package com.zerotoship.z2term.automation

/**
 * One run on a single event loop. Calls and loops share cancellation and the root deadline.
 *
 * [pause] cancels the step in flight and holds every deadline; [resume] sends that step again from its
 * start (1.0.96). A touch already delivered to Android cannot be taken back, so a step cut off midway is
 * repeated whole rather than continued.
 */
internal class ActionExecution(private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    /** A time limit that stops counting while the run is paused. */
    private inner class Deadline(private var remaining: Long, private val expire: () -> Unit) {
        private var startedAt = 0L
        private var cancel: (() -> Unit)? = null
        fun start() { if (cancel == null) { startedAt = now(); cancel = schedule(remaining.coerceAtLeast(0)) { cancel = null; expire() } } }
        fun hold() { cancel?.let { it(); cancel = null; remaining -= now() - startedAt } }
        fun stop() { cancel?.invoke(); cancel = null }
    }
    data class Result(val state: String, val step: Int, val error: String? = null)
    private data class Frame(val steps: List<ActionDefinition.Step>, val macro: String, val prefix: String = "",
        val repeats: Int? = 1, val loop: Boolean = false, val call: Boolean = false,
        var index: Int = 0, var iteration: Int = 1, var timeout: Deadline? = null)
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
    /** Every enclosing repeat, outermost first: (current pass, count or null for endless). */
    var loops: List<Pair<Int, Int?>> = emptyList()
        private set
    /** Position of the running step inside its own block (1-based) and that block's length. */
    var blockStep = 0
        private set
    var blockSize = 0
        private set
    var paused = false
        private set
    private var generation = 0
    /** Bumped whenever the step in flight is abandoned (pause), so its late completion is ignored. */
    private var attempt = 0
    private var stepInFlight = false
    private var cancelStep: (() -> Unit)? = null
    private var deadline: Deadline? = null
    private var cancelNext: (() -> Unit)? = null
    private var proceed: (() -> Unit)? = null
    private var completed: ((Result) -> Unit)? = null
    fun stop(reason: String = "Stopped") = finish("cancelled", reason)
    /** Holds the run where it is. Returns false when there is nothing to pause. */
    fun pause(): Boolean {
        if (!running || paused) return false
        paused = true
        cancelNext?.invoke(); cancelNext = null
        deadline?.hold(); frames.forEach { it.timeout?.hold() }
        if (stepInFlight) {
            // Send this step again on resume: step back the block position and the instruction count.
            stepInFlight = false
            attempt++
            frames.lastOrNull()?.let { it.index-- }
            step--
            val cancel = cancelStep; cancelStep = null
            runCatching { cancel?.invoke() }
        }
        return true
    }
    fun resume(): Boolean {
        if (!running || !paused) return false
        paused = false
        deadline?.start(); frames.forEach { it.timeout?.start() }
        val next = proceed ?: return true
        cancelNext = schedule(CONTROL_DELAY_MS) { next() }
        return true
    }
    private fun finish(state: String, error: String? = null) {
        if (!running) return
        running = false; paused = false; stepInFlight = false; proceed = null
        generation++
        val cancel = cancelStep; cancelStep = null
        deadline?.stop(); deadline = null
        cancelNext?.invoke(); cancelNext = null
        frames.forEach { it.timeout?.stop() }; frames.clear()
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
        loops = emptyList(); blockStep = 0; blockSize = 0
        frames += Frame(definition.steps, name)
        val token = ++generation
        deadline = Deadline(definition.timeoutMs) { if (token == generation) finish("timeout", "Macro timed out") }.also { it.start() }
        fun push(frame: Frame) {
            check(frames.size < ActionProgram.MAX_FRAME_DEPTH) { "Execution nesting limit reached" }
            frames += frame
        }
        fun advance() {
            if (!running || paused || token != generation) return
            cancelNext = null
            while (frames.isNotEmpty() && frames.last().index == frames.last().steps.size) {
                val frame = frames.last()
                if (frame.loop && (frame.repeats == null || frame.iteration < frame.repeats)) {
                    frame.index = 0; frame.iteration++
                    cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    return
                }
                frames.removeAt(frames.lastIndex).timeout?.stop()
            }
            if (frames.isEmpty()) { finish("completed"); return }
            val frame = frames.last()
            val action = frame.steps[frame.index++]
            macro = frame.macro; path = frame.prefix + frame.index
            iteration = frames.lastOrNull { it.loop }?.iteration ?: 1
            callDepth = 1 + frames.count { it.call }
            loops = frames.filter { it.loop }.map { it.iteration to it.repeats }
            blockStep = frame.index; blockSize = frame.steps.size
            if (step >= MAX_EXECUTED_STEPS) { finish("failed", "Execution exceeds $MAX_EXECUTED_STEPS instructions"); return }
            val index = ++step
            val tried = attempt
            var delivered = false
            val next: (String?) -> Unit = next@{ error ->
                if (delivered || !running || token != generation || index != step || tried != attempt) return@next
                delivered = true; cancelStep = null; stepInFlight = false
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
                        child.timeout = Deadline(called.timeoutMs) {
                            if (token == generation) finish("timeout", "Called macro timed out: " + action.name)
                        }.also { it.start() }
                        cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    }
                    else -> {
                        stepInFlight = true
                        val cancel = execute(action, next)
                        if (!delivered && running && token == generation && index == step && tried == attempt) cancelStep = cancel
                        else if (!delivered) runCatching { cancel?.invoke() }
                    }
                }
            } catch (e: Exception) { next(e.message ?: "Action failed") }
        }
        proceed = { advance() }
        cancelNext = schedule(80) { advance() }
    }
    companion object {
        const val MAX_EXECUTED_STEPS = 10000
        const val CONTROL_DELAY_MS = 20L
    }
}
