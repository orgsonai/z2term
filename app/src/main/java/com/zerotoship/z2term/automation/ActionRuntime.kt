package com.zerotoship.z2term.automation

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
internal object ActionRuntime {
    private val main = Handler(Looper.getMainLooper())
    private data class Run(val id: String, val name: String, val program: ActionProgram, val context: Context,
        val screen: ActionDefinition.Screen, val engine: ActionExecution, val runner: EdgeRunner,
        val listeners: MutableList<(JSONObject) -> Unit> = mutableListOf(), var step: Int = 0,
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
        run.target?.let { check(AndroidActions.targetMatches(it)) { "Target application changed" } }
    }
    private fun execute(run: Run, step: ActionDefinition.Step, completed: (String?) -> Unit): (() -> Unit)? {
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
                        if (run.target == null && !AndroidActions.targetMatches(resolved.target)) {
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
                return AndroidActions.stroke(resolved, run.screen, ::done)
            }
            is ActionDefinition.Step.Scroll -> {
                run.target = target(step.target)
                checkDevice(run)
                var ending = false
                var cancelTimer: (() -> Unit)? = null
                var cancelDrain: (() -> Unit)? = null
                AndroidActions.startAutoScroll(step.speed, Rect(0, 0, run.screen.width, run.screen.height), step.x, step.y) { error ->
                    if (!ending) { ending = true; cancelTimer?.invoke(); done(error ?: "Scrolling was interrupted") }
                }
                if (!ending) cancelTimer = schedule(step.ms) {
                    ending = true
                    AndroidActions.stopAutoScroll()
                    // The final stroke still owns the input stream until its completion callback.
                    fun drain() {
                        if (current !== run) return
                        if (AndroidActions.gesturesInFlight()) cancelDrain = schedule(20) { drain() } else done(null)
                    }
                    drain()
                }
                return { ending = true; cancelTimer?.invoke(); cancelDrain?.invoke(); AndroidActions.stopAutoScroll(); Unit }
            }
        }
        return null
    }
    fun stop(id: String? = null, reason: String = "Stopped"): JSONObject = EdgeRuntime.onMain {
        val run = current
        if (run != null && (id == null || id == run.id)) {
            if (run.engine.running) run.engine.stop(reason)
            else finish(run, ActionExecution.Result("cancelled", run.step, reason))
        }
        if (id != null) status(id) else status()
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
        run.runner.cancelAll()
        AndroidActions.stopAutoScroll()
        val response = json(run, result.state).put("step", result.step).put("error", result.error ?: JSONObject.NULL)
        results[run.id] = response
        while (results.size > 32) results.remove(results.keys.first())
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
        if (run != null && (id == null || run.id == id)) json(run, if (run.engine.running) "running" else "queued")
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
