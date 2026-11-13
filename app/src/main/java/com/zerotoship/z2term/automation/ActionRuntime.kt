package com.zerotoship.z2term.automation

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.content.ContextCompat
import com.zerotoship.z2term.edge.AndroidActions
import com.zerotoship.z2term.edge.EdgeRunner
import com.zerotoship.z2term.edge.EdgeRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Shared by CLI, shell macros, tiles, triggers and panels. State belongs to the main thread. */
// Holds only the application context and windows it adds itself; every one is removed and
// cleared when it closes, so nothing outlives the overlay it belongs to.
@SuppressLint("StaticFieldLeak")
internal object ActionRuntime {
    private val main = Handler(Looper.getMainLooper())
    private data class Run(val id: String, val name: String, val program: ActionProgram, val context: Context,
        val screen: ActionDefinition.Screen, val engine: ActionExecution, val runner: EdgeRunner,
        val listeners: MutableList<(JSONObject) -> Unit> = mutableListOf(),
        /** `z2-action resume` callers waiting for the pending resume to settle (running or still paused). */
        val resumeWaiters: MutableList<(JSONObject) -> Unit> = mutableListOf(), var step: Int = 0,
        val history: MutableList<String> = mutableListOf(), var historyLoaded: Boolean = false,
        var cancelHistoryFlush: (() -> Unit)? = null,
        val awaitBackground: Boolean = false, val allowOwnForeground: Boolean = false, var cancelStart: (() -> Unit)? = null,
        var target: String? = null, var service: ActionService? = null, var watchdog: Runnable? = null) {
        val definition get() = program.root
    }
    private var current: Run? = null
    private var retiringService: ActionService? = null
    private val results = linkedMapOf<String, JSONObject>()
    val running get() = current != null
    /** For the notification: whether the current run is paused, and whether a resume is waiting for its screen. */
    val paused get() = current?.engine?.paused == true
    val resuming get() = current?.let { it.engine.paused && it.cancelStart != null } == true
    /** A gesture cancelled by a real touch fails before the shade that touch opens takes focus. */
    private const val SHADE_GRACE_MS = 400L
    private const val SHADE_REASON = "Notification shade opened"
    fun store(context: Context) = ActionStore(File(context.filesDir, "shared_home/.z2term/actions"))

    @Suppress("DEPRECATION")
    fun screen(context: Context): ActionDefinition.Screen {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            ?: error("Primary display is unavailable")
        val metrics = DisplayMetrics().also { display.getRealMetrics(it) }
        return ActionDefinition.Screen(metrics.widthPixels, metrics.heightPixels, display.rotation)
    }
    private fun schedule(ms: Long, block: () -> Unit): () -> Unit {
        val task = Runnable { block() }
        main.postDelayed(task, ms)
        return { main.removeCallbacks(task) }
    }
    fun start(context: Context, name: String, background: (() -> Unit)? = null, waitForForeground: Boolean = false): String {
        val program = store(context).snapshot(name)
        return EdgeRuntime.onMain {
            check(!ActionCoordinatePicker.active) { context.getString(com.zerotoship.z2term.R.string.action_pick_busy) }
            check(current == null) { "An action macro is already running; use z2-action status or stop" }
            check(!UiBridge.busy) { "z2-ui is sending an operation; try again after it finishes" }
            check(retiringService == null) { "Wait for the previous execution service to stop" }
            check(AndroidActions.connected()) { "Enable z2term Android actions using z2-key permission" }
            check(!AndroidActions.gesturesInFlight()) { "Wait for the previous gesture to finish" }
            val geometry = screen(context)
            program.definitions.forEach { (macro, definition) ->
                require(definition.screen == null || definition.screen == geometry) {
                    "Saved screen size/orientation differs in $macro; edit and save it again"
                }
            }
            val app = context.applicationContext
            val run = Run(UUID.randomUUID().toString(), name, program, app, geometry, ActionExecution(::schedule), EdgeRunner(app),
                awaitBackground = waitForForeground || (program.usesCurrentTarget && background != null), allowOwnForeground = waitForForeground)
            checkDevice(run)
            current = run
            try {
                EdgeRuntime.suspendForActions(true)
                log(run, "queued")
                val watchdog = object : Runnable {
                    override fun run() {
                        if (current !== run) return
                        try { checkDevice(run) } catch (e: Exception) { stop(run.id, e.message ?: "Device changed"); return }
                        if (run.engine.running && !run.engine.paused && AndroidActions.systemUiFocused()) pause(run, SHADE_REASON)
                        main.postDelayed(this, 100)
                    }
                }
                run.watchdog = watchdog
                main.post(watchdog)
                ContextCompat.startForegroundService(app, Intent(app, ActionService::class.java).putExtra("run_id", run.id))
                if (run.awaitBackground) background?.invoke()
                schedule(5000) { if (current === run && run.service == null) finish(run, ActionExecution.Result("failed", 0, "Service did not start")) }
            } catch (e: Exception) {
                finish(run, ActionExecution.Result("failed", 0, e.message ?: "Cannot start macro"))
                throw e
            }
            run.id
        }
    }
    fun ready(id: String?, service: ActionService): Boolean {
        val run = current?.takeIf { it.id == id } ?: return false
        if (run.service != null) return true
        run.service = service
        service.showRun(run.name)
        ActionRunOverlay.show(AndroidActions.overlayHost(), run.name, stop = { runCatching { stop(run.id) } },
            toggle = { runCatching { if (run.engine.paused) resume(run.id) else pause(run.id) } })
        if (run.awaitBackground) {
            run.cancelStart = ActionForegroundWait(::schedule).start(if (run.allowOwnForeground) "" else run.context.packageName, {
                checkDevice(run)
                AndroidActions.focusedPackage()
            }, run.context.getString(com.zerotoship.z2term.R.string.action_run_background_timeout)) { error ->
                run.cancelStart = null
                if (current === run) {
                    if (error != null) finish(run, ActionExecution.Result("failed", 0, error))
                    else try { begin(run) } catch (e: Exception) {
                        finish(run, ActionExecution.Result("failed", 0, e.message ?: "Cannot start macro"))
                    }
                }
            }
        } else begin(run)
        return true
    }
    private fun begin(run: Run) {
        if (current !== run) return
        run.engine.start(run.definition, { step, done -> execute(run, step, done) }, { index, step ->
            run.step = index
            ActionRunOverlay.update(run.engine.loops, run.engine.blockStep, run.engine.blockSize)
            run.service?.showProgress(ActionRunOverlay.describe(run.context, run.engine.loops, run.engine.blockStep, run.engine.blockSize))
            log(run, "step", when (step) {
                is ActionDefinition.Step.Repeat -> "repeat"
                is ActionDefinition.Step.Branch -> "if"
                is ActionDefinition.Step.Call -> "call"
                is ActionDefinition.Step.Ui -> step.operation
                is ActionDefinition.Step.Wait -> "wait"
                is ActionDefinition.Step.Launch -> "launch"
                is ActionDefinition.Step.Key -> "key"
                is ActionDefinition.Step.Command -> "command"
                is ActionDefinition.Step.Stroke -> "stroke"
                is ActionDefinition.Step.Scroll -> "scroll"
            })
        }, { result -> finish(run, result) },
            resolve = { run.program.definitions.getValue(it) },
            condition = { spec ->
                checkDevice(run)
                val state = com.zerotoship.z2term.service.Z2ApiBridge.stateSnapshot(run.context).toMutableMap()
                AndroidActions.focusedPackage()?.let { state["foreground"] = it }
                ActionCondition.matches(spec, state)
            },
            name = run.name,
            branch = { _, matched -> log(run, "branch", matched.toString()) })
    }
    private fun checkDevice(run: Run) {
        check(run.context.getSystemService(PowerManager::class.java).isInteractive &&
            !run.context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Screen is off or locked" }
        check(AndroidActions.connected()) { "Accessibility disconnected" }
        check(screen(run.context) == run.screen) { "Screen size/orientation changed" }
        // ⚠ 「別アプリだと分かっている」ときだけ止める。前面の窓のパッケージ名が**まだ分からない**間
        // (null) は切り替わりと扱わない: タップで開いた同じアプリの次の画面は、フォーカスを得てから
        // 窓の状態変化イベントが届くまで名前が引けない。ここは実行中 100ms ごとの見張りからも
        // 呼ばれるので、その隙間を「切り替わった」と読むと click → wait-ui が必ず止まる (0.9.32)。
        // 操作そのもの (タップ・要素の操作) は送る直前に対象の一致を別に確かめている。
        run.target?.let { target ->
            val focused = AndroidActions.focusedPackage()
            check(focused == null || focused == target) { "Target application changed" }
        }
    }
    /**
     * The notification shade pauses the run instead of failing it (1.0.96). Pulling the shade is a real touch,
     * so it first cancels a gesture in flight or hides the foreground app; a failure is reported only when
     * the shade has not come up [SHADE_GRACE_MS] later.
     */
    private fun execute(run: Run, step: ActionDefinition.Step, completed: (String?) -> Unit): (() -> Unit)? {
        if (AndroidActions.systemUiFocused()) { main.post { if (current === run) pause(run, SHADE_REASON) }; return null }
        val guarded: (String?) -> Unit = { error ->
            if (error == null) completed(null)
            else schedule(SHADE_GRACE_MS) {
                if (current === run) { if (AndroidActions.systemUiFocused()) pause(run, SHADE_REASON) else completed(error) }
            }
        }
        return try { executeStep(run, step, guarded) } catch (e: Exception) { guarded(e.message ?: "Action failed"); null }
    }
    private fun executeStep(run: Run, step: ActionDefinition.Step, completed: (String?) -> Unit): (() -> Unit)? {
        checkDevice(run)
        fun done(error: String?) { run.target = null; completed(error) }
        fun target(requested: String): String = if (requested == ActionDefinition.CURRENT_TARGET)
            AndroidActions.focusedPackage() ?: error(run.context.getString(com.zerotoship.z2term.R.string.action_run_no_foreground))
            else requested
        when (step) {
            is ActionDefinition.Step.Repeat, is ActionDefinition.Step.Branch, is ActionDefinition.Step.Call ->
                error("Control flow must be handled by the execution engine")
            is ActionDefinition.Step.Ui -> {
                val resolved = step.copy(target = target(step.target))
                if (step.operation == "wait-ui") return ActionUiWait(::schedule).start(step.timeoutMs,
                    { callback ->
                        // Launch returns before its window is focused. Wait for that first arrival too.
                        // The same gap exists after a tap opens the next screen of the same app; checkDevice
                        // only stops the run when the focused window is known to belong to another app.
                        if (!AndroidActions.targetMatches(resolved.target)) {
                            checkDevice(run)
                            val noWork: () -> Unit = {}
                            callback(false, null)
                            noWork
                        } else {
                            run.target = resolved.target
                            checkDevice(run)
                            AndroidActions.queryUi(resolved, callback)
                        }
                    }, ::done)
                run.target = resolved.target
                checkDevice(run)
                return AndroidActions.queryUi(resolved) { found, error -> done(error ?: if (found) null else "UI element not found") }
            }
            is ActionDefinition.Step.Wait -> return schedule(step.ms) { done(null) }
            is ActionDefinition.Step.Launch -> {
                val intent = run.context.packageManager.getLaunchIntentForPackage(step.packageName) ?: error("No launchable app: ${step.packageName}")
                run.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); done(null)
            }
            is ActionDefinition.Step.Key -> { AndroidActions.command(run.context, listOf(step.name)); done(null) }
            is ActionDefinition.Step.Command -> {
                check(run.runner.run("macro:${run.id}", step.text, run.definition.timeoutMs / 1000) { done(it.error) }) { "Cannot start shell command" }
                return { run.runner.cancelAll() }
            }
            is ActionDefinition.Step.Stroke -> {
                val resolved = step.copy(target = target(step.target))
                run.target = resolved.target
                // Only where a finger lands decides which window receives it; avoid the stop bar along the whole path.
                val points = resolved.tracks(run.screen).flatten()
                return ActionRunOverlay.clear({ box -> points.any { box.contains(it.x.toInt(), it.y.toInt()) } }, ::schedule, ::done) { finished ->
                    check(current === run) { "Stopped" }
                    AndroidActions.stroke(resolved, run.screen, finished)
                }
            }
            is ActionDefinition.Step.Scroll -> {
                run.target = target(step.target)
                checkDevice(run)
                return scroll(run, step, ::done)
            }
        }
        return null
    }
    private fun scroll(run: Run, step: ActionDefinition.Step.Scroll, done: (String?) -> Unit): () -> Unit {
        var ending = false
        var cancelTimer: (() -> Unit)? = null
        var cancelDrain: (() -> Unit)? = null
        var cancelOverlay: (() -> Unit)? = null
        // Set while the stop bar is out of the way; calling it brings the bar back shortly after.
        var overlayFinished: ((String?) -> Unit)? = null
        fun end(error: String?) { overlayFinished?.invoke(null); overlayFinished = null; done(error) }
        val band = ActionUi.dp(run.context, 48)
        // The bar steps aside only when a finger is really placed and its line crosses the bar: scrolling
        // the view directly places no finger, and the line covers 60% of the window height, not all of it.
        AndroidActions.startAutoScroll(step.speed, Rect(0, 0, run.screen.width, run.screen.height), step.x, step.y,
            fingers = { track, go ->
                cancelOverlay = ActionRunOverlay.clear({ box ->
                    track.left - band < box.right && box.left < track.right + band &&
                        track.top < box.bottom && box.top < track.bottom
                }, ::schedule, {}) { finished ->
                    overlayFinished = finished
                    if (!ending && current === run) go()
                    null
                }
            }) { error ->
            if (!ending) { ending = true; cancelTimer?.invoke(); end(error ?: "Scrolling was interrupted") }
        }
        if (!ending) cancelTimer = schedule(step.ms) {
            ending = true
            AndroidActions.stopAutoScroll()
            // The final stroke still owns the input stream until its completion callback.
            fun drain() {
                if (current !== run) return
                if (AndroidActions.gesturesInFlight()) cancelDrain = schedule(20) { drain() } else end(null)
            }
            drain()
        }
        return {
            ending = true; cancelTimer?.invoke(); cancelDrain?.invoke(); cancelOverlay?.invoke()
            AndroidActions.stopAutoScroll(); overlayFinished?.invoke(null); overlayFinished = null
        }
    }
    fun stop(id: String? = null, reason: String = "Stopped"): JSONObject = EdgeRuntime.onMain {
        val run = current
        if (run != null && (id == null || id == run.id)) {
            if (run.engine.running) run.engine.stop(reason)
            else finish(run, ActionExecution.Result("cancelled", run.step, reason))
        }
        if (id != null) status(id) else status()
    }
    /** Holds the run where it is; [resume] sends the interrupted step again from its start. */
    fun pause(id: String? = null): JSONObject = EdgeRuntime.onMain {
        current?.takeIf { id == null || it.id == id }?.let { pause(it, "Paused") }
        if (id != null) status(id) else status()
    }
    private fun pause(run: Run, reason: String) {
        // Before the first step there is nothing to hold; the start still waits for its screen.
        if (!run.engine.running || !run.engine.pause()) return
        run.target = null
        AndroidActions.stopAutoScroll()
        log(run, "paused", reason)
        refresh(run)
    }
    /**
     * Continues once a screen other than z2term has stayed in front for half a second, as at the start.
     * [background] moves z2term away first when resuming from its own screen. If no screen settles within
     * five seconds the run stays paused.
     */
    fun resume(id: String? = null, background: (() -> Unit)? = null): JSONObject = EdgeRuntime.onMain {
        resumeNow(id, background, null)
    }
    /**
     * Same as [resume], but [settled] receives the status once the outcome is known: `running` after
     * the run continued, `paused` when no screen settled, or the final state if the run ended meanwhile.
     * A run that is not paused answers at once. Used by `z2-action resume` so a script reads the result.
     */
    fun resume(id: String?, settled: (JSONObject) -> Unit) = EdgeRuntime.onMain {
        val pending = current?.takeIf { (id == null || it.id == id) && it.engine.paused && it.cancelStart != null }
        if (pending != null) {
            // Another resume is already waiting for its screen; share its outcome instead of a second wait.
            require(pending.resumeWaiters.size < 16) { "Too many waiters" }
            pending.resumeWaiters += settled
            return@onMain
        }
        // The screen may already be there, so the wait can settle before resumeNow returns: answer once.
        var answered = false
        val answer: (JSONObject) -> Unit = { if (!answered) { answered = true; settled(it) } }
        val status = resumeNow(id, null, answer)
        if (current?.resumeWaiters?.contains(answer) != true) answer(status)
    }
    private fun settleResume(run: Run) {
        val status = status(run.id)
        run.resumeWaiters.toList().forEach { waiter -> runCatching { waiter(status) } }
        run.resumeWaiters.clear()
    }
    private fun resumeNow(id: String?, background: (() -> Unit)?, settled: ((JSONObject) -> Unit)?): JSONObject {
        val run = current?.takeIf { (id == null || it.id == id) && it.engine.paused && it.cancelStart == null }
        if (run != null) {
            settled?.let { run.resumeWaiters += it }
            AndroidActions.dismissShade()
            run.cancelStart = ActionForegroundWait(::schedule).start(if (run.allowOwnForeground) "" else run.context.packageName, {
                checkDevice(run)
                if (AndroidActions.systemUiFocused()) null else AndroidActions.focusedPackage()
            }, run.context.getString(com.zerotoship.z2term.R.string.action_resume_no_screen)) { error ->
                run.cancelStart = null
                if (current === run && run.engine.paused) {
                    if (error != null) {
                        log(run, "paused", error)
                        android.widget.Toast.makeText(run.context, error, android.widget.Toast.LENGTH_LONG).show()
                    } else { log(run, "resumed"); run.engine.resume() }
                    refresh(run)
                }
                if (current === run) settleResume(run)
            }
            refresh(run)
            background?.let { runCatching(it) }
        }
        return if (id != null) status(id) else status()
    }
    /** Brings the stop bar and the notification in line with the run's state. */
    private fun refresh(run: Run) {
        ActionRunOverlay.setPaused(run.engine.paused, run.cancelStart != null)
        runCatching { run.service?.showRun(run.name) }
    }
    fun serviceDestroyed(service: ActionService) {
        if (retiringService === service) retiringService = null
        current?.takeIf { it.service === service }?.let { stop(it.id, "Execution service stopped") }
        if (retiringService === service) retiringService = null
    }
    private fun finish(run: Run, result: ActionExecution.Result) {
        if (current !== run) return
        current = null
        run.cancelStart?.invoke(); run.cancelStart = null
        run.watchdog?.let(main::removeCallbacks)
        ActionRunOverlay.dismiss()
        run.runner.cancelAll()
        AndroidActions.stopAutoScroll()
        val response = json(run, result.state).put("step", result.step).put("error", result.error ?: JSONObject.NULL)
        results[run.id] = response
        while (results.size > 32) results.remove(results.keys.first())
        // A stop during the resume wait cancels that wait without a callback; answer its callers here.
        run.resumeWaiters.toList().forEach { waiter -> runCatching { waiter(response) } }
        run.resumeWaiters.clear()
        runCatching { log(run, result.state, result.error) }
        retiringService = run.service
        runCatching { run.service?.stopSelf() }
        runCatching { EdgeRuntime.suspendForActions(false) }
        run.listeners.toList().forEach { listener -> runCatching { listener(response) } }
        run.listeners.clear()
    }
    fun waitFor(id: String, completed: (JSONObject) -> Unit) = EdgeRuntime.onMain {
        results[id]?.let { completed(it); return@onMain }
        val run = current?.takeIf { it.id == id } ?: error("No retained run: $id")
        require(run.listeners.size < 16) { "Too many waiters" }
        run.listeners += completed
    }
    fun status(id: String? = null): JSONObject = EdgeRuntime.onMain {
        val run = current
        if (run != null && (id == null || run.id == id)) json(run, when {
            run.engine.paused -> "paused"; run.engine.running -> "running"; else -> "queued" })
        else if (id != null) results[id] ?: error("No retained run: $id")
        else results.values.lastOrNull() ?: JSONObject().put("state", "idle")
    }
    private fun json(run: Run, state: String) = JSONObject().put("id", run.id).put("name", run.name)
        .put("state", state).put("step", run.step)
        .put("total", if (run.program.hasControlFlow) JSONObject.NULL else run.definition.steps.size)
        .put("action_macro", run.engine.macro.ifBlank { run.name }).put("path", run.engine.path)
        .put("iteration", run.engine.iteration).put("call_depth", run.engine.callDepth)
    private fun historyFile(context: Context) = File(context.filesDir, "shared_home/.z2term/actions/.history.jsonl")
    private fun readHistory(context: Context): List<String> {
        val file = historyFile(context)
        return if (file.isFile && file.length() <= 262144) file.readLines().takeLast(256) else emptyList()
    }
    private fun flushHistory(run: Run) {
        run.cancelHistoryFlush?.invoke(); run.cancelHistoryFlush = null
        val file = historyFile(run.context)
        file.parentFile?.mkdirs()
        file.writeText(run.history.joinToString("\n", postfix = "\n"))
    }
    private fun log(run: Run, state: String, detail: String? = null) {
        if (!run.historyLoaded) {
            run.history += readHistory(run.context)
            run.historyLoaded = true
        }
        val event = json(run, state).put("time", System.currentTimeMillis()).put("detail", detail?.take(500) ?: JSONObject.NULL)
        run.history += event.toString()
        while (run.history.size > 256) run.history.removeAt(0)
        // Loops can emit many immediate steps. Keep progress live, but batch disk snapshots.
        if (state !in setOf("step", "branch")) flushHistory(run)
        else if (run.cancelHistoryFlush == null) run.cancelHistoryFlush = schedule(250) {
            run.cancelHistoryFlush = null
            try { flushHistory(run) }
            catch (e: Exception) { stop(run.id, e.message ?: "Cannot save action history") }
        }
    }
    fun history(context: Context): String = EdgeRuntime.onMain {
        val lines = current?.takeIf { it.historyLoaded }?.history ?: readHistory(context)
        JSONArray().apply { lines.forEach { put(JSONObject(it)) } }.toString()
    }
}
