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
        var index: Int = 0, var iteration: Int = 1, var timeout: Deadline? = null,
        /** `repeat … until` (1.0.188): checked before each pass ([checked]); [exit] ends the loop. */
        val until: String? = null, val untilTarget: String = ActionDefinition.CURRENT_TARGET,
        var checked: Boolean = false, var exit: Boolean = false,
        /** Non-null for the body of `try` (1.0.190): the steps that run when something inside fails. */
        val recover: List<ActionDefinition.Step>? = null,
        /** Values of the macro this frame belongs to (1.0.191); blocks inherit them from their macro. */
        val args: List<String> = emptyList())
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
    /** `set` / `add` variables of this run (1.0.192), shared with called macros; empty at every start. */
    val variables = linkedMapOf<String, String>()
    /**
     * 呼び出しの道筋 (1.0.206): いま手順を進めているマクロまで、外側から順に呼んだマクロの名前。最初のマクロは
     * 含めない (帯と通知はそれを別に出している)。repeat などのブロックは同じ名前が続くので 1 つにまとめる。
     */
    val calls: List<String> get() = frames.map { it.macro }
        .fold(mutableListOf<String>()) { list, name -> if (list.lastOrNull() != name) list += name; list }.drop(1)
    /** Values of the macro whose step is running (shell steps receive them as `$1`…). */
    val args: List<String> get() = frames.lastOrNull()?.args.orEmpty()
    private var generation = 0
    /** Bumped whenever the step in flight is abandoned (pause), so its late completion is ignored. */
    private var attempt = 0
    private var stepInFlight = false
    /** The condition of `repeat … until` is being checked (not an instruction, so pause does not step back). */
    private var untilInFlight = false
    private var limitSteps = true
    private var cancelStep: (() -> Unit)? = null
    private var deadline: Deadline? = null
    private var cancelNext: (() -> Unit)? = null
    private var proceed: (() -> Unit)? = null
    private var completed: ((Result) -> Unit)? = null
    /** The step being sent (null between steps). Decides whether a watch may cut it short. */
    private var inFlight: ActionDefinition.Step? = null
    /** A `watch` stays registered until the block it was written in ([owner]) ends. */
    private class Watching(val owner: Frame, val step: ActionDefinition.Step.Watch)
    private val watches = mutableListOf<Watching>()
    /** The handler frame a watch started; no watch fires again until it ends. */
    private var handling: Frame? = null
    /** A watch that fired and waits for the next step boundary (or for the step it cuts short). */
    private var pendingWatch: ActionDefinition.Step.Watch? = null
    private var cancelPoll: (() -> Unit)? = null
    private var pollTest: (() -> Unit)? = null
    private var pollRound = 0
    private var startPolling: (() -> Unit)? = null
    private fun stopPolling() {
        pollRound++
        cancelPoll?.invoke(); cancelPoll = null
        val test = pollTest; pollTest = null
        runCatching { test?.invoke() }
    }
    /**
     * Steps that only wait can be cut short by a watch and are sent again from their start afterwards,
     * as after a pause. A gesture, a launch or a shell command finishes first.
     */
    private fun interruptible(step: ActionDefinition.Step?) = step is ActionDefinition.Step.Wait ||
        step is ActionDefinition.Step.WaitFor || step is ActionDefinition.Step.Branch ||
        (step is ActionDefinition.Step.Ui && step.operation == "wait-ui")
    /** Abandons the step in flight so it is sent again from its start (pause and watches). */
    private fun rewind() {
        stepInFlight = false; inFlight = null
        attempt++
        if (untilInFlight) {
            // 繰り返しの条件は、再開したらもう一度確かめる (手順の数には入っていない)。
            untilInFlight = false
            frames.lastOrNull()?.checked = false
            val cancel = cancelStep; cancelStep = null
            runCatching { cancel?.invoke() }
            return
        }
        frames.lastOrNull()?.let { it.index-- }
        step--
        val cancel = cancelStep; cancelStep = null
        runCatching { cancel?.invoke() }
    }
    fun stop(reason: String = "Stopped") = finish("cancelled", reason)
    /** Holds the run where it is. Returns false when there is nothing to pause. */
    fun pause(): Boolean {
        if (!running || paused) return false
        paused = true
        cancelNext?.invoke(); cancelNext = null
        deadline?.hold(); frames.forEach { it.timeout?.hold() }
        // Send this step again on resume: step back the block position and the instruction count.
        if (stepInFlight) rewind()
        stopPolling()
        return true
    }
    fun resume(): Boolean {
        if (!running || !paused) return false
        paused = false
        deadline?.start(); frames.forEach { it.timeout?.start() }
        startPolling?.invoke()
        val next = proceed ?: return true
        cancelNext = schedule(CONTROL_DELAY_MS) { next() }
        return true
    }
    private fun finish(state: String, error: String? = null) {
        if (!running) return
        running = false; paused = false; stepInFlight = false; untilInFlight = false; proceed = null; inFlight = null
        generation++
        stopPolling(); startPolling = null
        watches.clear(); handling = null; pendingWatch = null
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
        name: String = "", branch: (String, Boolean) -> Unit = { _, _ -> },
        test: ((String, String, (Boolean?, String?) -> Unit) -> (() -> Unit)?)? = null,
        expand: (ActionDefinition.Step.RunOutput, (List<ActionDefinition.Step>?, String?) -> Unit) -> (() -> Unit)? =
            { _, _ -> error("run-output is unavailable") },
        watched: (ActionDefinition.Step.Watch) -> Unit = {},
        /** Whether a failure may be caught by `try` (false for a locked screen and the like). */
        catchable: (String) -> Boolean = { true },
        /** A failure a `try` caught, before its `else` runs. */
        caught: (String) -> Unit = {}) {
        check(!running) { "An action macro is already running" }
        running = true; step = 0; completed = done
        variables.clear()
        limitSteps = !definition.unlimited
        macro = name; path = ""; iteration = 1; callDepth = 1
        loops = emptyList(); blockStep = 0; blockSize = 0
        frames += Frame(definition.steps, name, args = definition.args)
        val token = ++generation
        deadline = if (definition.unlimited) null else
            Deadline(definition.timeoutMs) { if (token == generation) finish("timeout", "Macro timed out") }.also { it.start() }
        fun push(frame: Frame) {
            check(frames.size < ActionProgram.MAX_FRAME_DEPTH) { "Execution nesting limit reached" }
            frames += frame
        }
        /** Removes the top frame with everything that belongs to it. */
        fun pop(): Frame {
            val removed = frames.removeAt(frames.lastIndex)
            removed.timeout?.stop()
            watches.removeAll { it.owner === removed }
            if (removed === handling) handling = null
            return removed
        }
        /**
         * A step failed. The innermost `try` (also across calls) takes it: every frame above it goes, and its
         * `else` steps run in its place. Without one, or for a failure [catchable] refuses, the run fails.
         */
        fun fail(error: String) {
            if (!running || token != generation) return
            val at = frames.indexOfLast { it.recover != null }
            if (at < 0 || !catchable(error)) { finish("failed", error); return }
            val owner = frames[at]
            while (frames.size > at) pop()
            caught(error)
            if (!running || token != generation) return
            try {
                val recover = owner.recover.orEmpty()
                if (recover.isNotEmpty()) push(Frame(recover, owner.macro, owner.prefix.removeSuffix("try.") + "else.", args = owner.args))
            } catch (e: Exception) { finish("failed", e.message ?: error); return }
            // advance() is declared below; [proceed] points at it before the first step runs.
            cancelNext = schedule(CONTROL_DELAY_MS) { proceed?.invoke() }
        }
        fun advance() {
            if (!running || paused || token != generation) return
            cancelNext = null
            pendingWatch?.let { fired ->
                pendingWatch = null
                try {
                    // 割り込みで呼ぶ。終わったら、割り込まれた所 (待ちの手順なら最初から) へ戻る。
                    check(frames.none { it.macro == fired.name }) { "Recursive macro call: " + fired.name }
                    check(1 + frames.count { it.call } < ActionProgram.MAX_CALL_DEPTH) { "Macro call depth exceeded" }
                    val called = resolve(fired.name)
                    val child = Frame(called.steps, fired.name, call = true, args = called.args)
                    push(child)
                    handling = child
                    child.timeout = if (called.unlimited) null else Deadline(called.timeoutMs) {
                        if (token == generation) finish("timeout", "Called macro timed out: " + fired.name)
                    }.also { it.start() }
                    watched(fired)
                } catch (e: Exception) { fail(e.message ?: "watch failed"); return }
                if (!running || token != generation) return
            }
            while (frames.isNotEmpty() && frames.last().index == frames.last().steps.size) {
                val frame = frames.last()
                if (frame.loop && !frame.exit && (frame.repeats == null || frame.iteration < frame.repeats)) {
                    frame.index = 0; frame.iteration++; frame.checked = false
                    cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    return
                }
                pop()
            }
            if (frames.isEmpty()) { finish("completed"); return }
            val frame = frames.last()
            // `repeat … until`: 各回の始めに条件を確かめ、成立していれば抜ける。答えを待つ間は送信中と同じ扱い。
            val until = frame.until
            if (frame.loop && until != null && frame.index == 0 && !frame.checked) {
                frame.checked = true
                fun leave(matched: Boolean) {
                    if (matched) { frame.exit = true; frame.index = frame.steps.size }
                    cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                }
                if (test == null) {
                    try { leave(condition(until)) } catch (e: Exception) { fail(e.message ?: "Condition failed") }
                    return
                }
                val tried = attempt
                stepInFlight = true; untilInFlight = true
                var answered = false
                val cancel = try {
                    test(until, frame.untilTarget) answer@{ matched, error ->
                        if (answered || !running || token != generation || tried != attempt) return@answer
                        answered = true; cancelStep = null; stepInFlight = false; untilInFlight = false
                        if (error != null || matched == null) { fail(error ?: "Condition failed"); return@answer }
                        leave(matched)
                    }
                } catch (e: Exception) { fail(e.message ?: "Condition failed"); return }
                if (!answered && running && token == generation && tried == attempt) cancelStep = cancel
                else if (!answered) runCatching { cancel?.invoke() }
                return
            }
            val action = frame.steps[frame.index++]
            macro = frame.macro; path = frame.prefix + frame.index
            iteration = frames.lastOrNull { it.loop }?.iteration ?: 1
            callDepth = 1 + frames.count { it.call }
            loops = frames.filter { it.loop }.map { it.iteration to it.repeats }
            blockStep = frame.index; blockSize = frame.steps.size
            if (limitSteps && step >= MAX_EXECUTED_STEPS) { finish("failed", "Execution exceeds $MAX_EXECUTED_STEPS instructions"); return }
            val index = ++step
            val tried = attempt
            var delivered = false
            val next: (String?) -> Unit = next@{ error ->
                if (delivered || !running || token != generation || index != step || tried != attempt) return@next
                delivered = true; cancelStep = null; stepInFlight = false; inFlight = null
                if (error != null) fail(error)
                else cancelNext = schedule(0) { advance() }
            }
            try {
                progress(index, action)
                if (!running || token != generation) return
                when (action) {
                    is ActionDefinition.Step.Repeat -> {
                        push(Frame(action.body, macro, "$path.", action.count, loop = true,
                            until = action.until, untilTarget = action.target, args = frame.args))
                        cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    }
                    is ActionDefinition.Step.Branch -> {
                        val here = path
                        fun decide(matched: Boolean) {
                            if (!running || token != generation) return
                            branch(action.condition, matched)
                            if (!running || token != generation) return
                            val branchPath = "$here." + (if (matched) "yes." else "no.")
                            push(Frame(if (matched) action.yes else action.no, macro, branchPath, args = frame.args))
                            cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                        }
                        if (test == null) decide(condition(action.condition))
                        else {
                            // 画面やシェルを見る条件は答えを待つ。待つ間は手順と同じく「送っている途中」で、
                            // 一時停止すると取り消し、再開で条件を最初から見直す。
                            stepInFlight = true; inFlight = action
                            var answered = false
                            val cancel = test(action.condition, action.target) answer@{ matched, error ->
                                if (answered || !running || token != generation || index != step || tried != attempt) return@answer
                                answered = true; cancelStep = null; stepInFlight = false; inFlight = null
                                if (error != null || matched == null) { fail(error ?: "Condition failed"); return@answer }
                                try { decide(matched) } catch (e: Exception) { fail(e.message ?: "Action failed") }
                            }
                            if (!answered && running && token == generation && index == step && tried == attempt) cancelStep = cancel
                            else if (!answered) runCatching { cancel?.invoke() }
                        }
                    }
                    is ActionDefinition.Step.Call -> {
                        check(frames.none { it.macro == action.name }) { "Recursive macro call: " + action.name }
                        check(callDepth < ActionProgram.MAX_CALL_DEPTH) { "Macro call depth exceeded" }
                        val called = resolve(action.key)
                        val child = Frame(called.steps, action.name, call = true, args = called.args)
                        push(child)
                        child.timeout = if (called.unlimited) null else Deadline(called.timeoutMs) {
                            if (token == generation) finish("timeout", "Called macro timed out: " + action.name)
                        }.also { it.start() }
                        cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    }
                    is ActionDefinition.Step.Set -> {
                        val value = if (!action.add) action.value else {
                            val have = variables[action.name] ?: "0"
                            val number = checkNotNull(have.toLongOrNull()) { "Variable ${action.name} is not a whole number: $have" }
                            (number + action.value.toLong()).toString()
                        }
                        check(action.name in variables || variables.size < ActionDefinition.MAX_VARIABLES) {
                            "At most ${ActionDefinition.MAX_VARIABLES} variables"
                        }
                        variables[action.name] = value
                        cancelNext = schedule(0) { advance() }
                    }
                    is ActionDefinition.Step.Try -> {
                        push(Frame(action.body, macro, "$path.try.", recover = action.recover, args = frame.args))
                        cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    }
                    is ActionDefinition.Step.Break -> {
                        // いちばん内側の繰り返しを、途中の分岐のブロックごと抜ける (解析で repeat の中だけに限っている)。
                        while (frames.isNotEmpty()) { if (pop().loop) break }
                        cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    }
                    is ActionDefinition.Step.Watch -> {
                        checkNotNull(test) { "watch requires a condition evaluator" }
                        if (watches.none { it.owner === frame && it.step === action }) watches += Watching(frame, action)
                        startPolling?.invoke()
                        cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                    }
                    is ActionDefinition.Step.RunOutput -> {
                        // 出力を手順として読み、その場の続きとして実行する (呼び出しではないので対象や制限時間はそのまま)。
                        val here = path
                        stepInFlight = true; inFlight = action
                        var answered = false
                        val cancel = expand(action) produced@{ steps, error ->
                            if (answered || !running || token != generation || index != step || tried != attempt) return@produced
                            answered = true; cancelStep = null; stepInFlight = false; inFlight = null
                            if (error != null || steps == null) { fail(error ?: "run-output failed"); return@produced }
                            try {
                                if (steps.isNotEmpty()) push(Frame(steps, macro, "$here.", args = frame.args))
                                cancelNext = schedule(CONTROL_DELAY_MS) { advance() }
                            } catch (e: Exception) { fail(e.message ?: "run-output failed") }
                        }
                        if (!answered && running && token == generation && index == step && tried == attempt) cancelStep = cancel
                        else if (!answered) runCatching { cancel?.invoke() }
                    }
                    else -> {
                        stepInFlight = true; inFlight = action
                        val cancel = execute(action, next)
                        if (!delivered && running && token == generation && index == step && tried == attempt) cancelStep = cancel
                        else if (!delivered) runCatching { cancel?.invoke() }
                    }
                }
            } catch (e: Exception) { next(e.message ?: "Action failed") }
        }
        /** A watch fired: run its macro at the next boundary, cutting a waiting step short. */
        fun fire(watch: ActionDefinition.Step.Watch) {
            pendingWatch = watch
            if (stepInFlight) {
                if (!interruptible(inFlight)) return  // its completion schedules advance(), which takes the watch
                rewind()
            }
            cancelNext?.invoke()
            cancelNext = schedule(0) { advance() }
        }
        fun poll() {
            cancelPoll = null
            val evaluate = test ?: return
            if (!running || token != generation || watches.isEmpty()) return
            if (paused || handling != null || pendingWatch != null) {
                cancelPoll = schedule(WATCH_INTERVAL_MS) { poll() }; return
            }
            val round = ++pollRound
            val list = watches.toList()
            fun check(position: Int) {
                if (!running || token != generation || round != pollRound) return
                if (position == list.size) { cancelPoll = schedule(WATCH_INTERVAL_MS) { poll() }; return }
                val watch = list[position]
                if (watches.none { it === watch }) { check(position + 1); return }
                var answered = false
                val stop = try {
                    evaluate(watch.step.condition, watch.step.target) answer@{ matched, error ->
                        if (answered || !running || token != generation || round != pollRound) return@answer
                        answered = true; pollTest = null
                        when {
                            error != null || matched == null -> finish("failed", error ?: "Condition failed")
                            matched -> { fire(watch.step); cancelPoll = schedule(WATCH_INTERVAL_MS) { poll() } }
                            else -> check(position + 1)
                        }
                    }
                } catch (e: Exception) { finish("failed", e.message ?: "Condition failed"); return }
                if (!answered && running && round == pollRound) pollTest = stop
                else if (!answered) runCatching { stop?.invoke() }
            }
            check(0)
        }
        startPolling = {
            if (running && token == generation && !paused && cancelPoll == null && pollTest == null && watches.isNotEmpty())
                cancelPoll = schedule(WATCH_INTERVAL_MS) { poll() }
        }
        proceed = { advance() }
        cancelNext = schedule(80) { advance() }
    }
    companion object {
        const val MAX_EXECUTED_STEPS = 10000
        const val CONTROL_DELAY_MS = 20L
        /** How often active watches are checked. Colours cannot be read faster than about 1/3 s anyway. */
        const val WATCH_INTERVAL_MS = 300L
    }
}
