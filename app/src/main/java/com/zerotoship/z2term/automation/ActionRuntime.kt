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
    private data class Run(val id: String, val name: String, val definition: ActionDefinition, val context: Context,
        val screen: ActionDefinition.Screen, val engine: ActionExecution, val runner: EdgeRunner,
        val listeners: MutableList<(JSONObject) -> Unit> = mutableListOf(), var step: Int = 0,
        var target: String? = null, var service: ActionService? = null, var watchdog: Runnable? = null)
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
    fun start(context: Context, name: String): String {
        val definition = store(context).read(name)
        return EdgeRuntime.onMain {
            check(current == null) { "An action macro is already running; use z2-action status or stop" }
            check(retiringService == null) { "Wait for the previous execution service to stop" }
            check(AndroidActions.connected()) { "Enable z2term Android actions using z2-key permission" }
            check(!AndroidActions.gesturesInFlight()) { "Wait for the previous gesture to finish" }
            val geometry = screen(context)
            require(definition.screen == null || definition.screen == geometry) { "Saved screen size/orientation differs; edit and save the macro again" }
            val app = context.applicationContext
            val run = Run(UUID.randomUUID().toString(), name, definition, app, geometry, ActionExecution(::schedule), EdgeRunner(app))
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
        run.engine.start(run.definition, { step, done -> execute(run, step, done) }, { index, step ->
            run.step = index
            log(run, "step", when (step) {
                is ActionDefinition.Step.Wait -> "wait"
                is ActionDefinition.Step.Launch -> "launch"
                is ActionDefinition.Step.Key -> "key"
                is ActionDefinition.Step.Command -> "command"
                is ActionDefinition.Step.Stroke -> "stroke"
                is ActionDefinition.Step.Scroll -> "scroll"
            })
        }, { result -> finish(run, result) })
        return true
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
        when (step) {
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
                run.target = step.target
                return AndroidActions.stroke(step, run.screen, ::done)
            }
            is ActionDefinition.Step.Scroll -> {
                run.target = step.target
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
        if (run != null && (id == null || run.id == id)) json(run, if (run.service == null) "queued" else "running")
        else if (id != null) results[id] ?: error("No retained run: $id")
        else results.values.lastOrNull() ?: JSONObject().put("state", "idle")
    }
    private fun json(run: Run, state: String) = JSONObject().put("id", run.id).put("name", run.name)
        .put("state", state).put("step", run.step).put("total", run.definition.steps.size)
    private fun log(run: Run, state: String, detail: String? = null) {
        val file = File(run.context.filesDir, "shared_home/.z2term/actions/.history.jsonl")
        file.parentFile?.mkdirs()
        val event = json(run, state).put("time", System.currentTimeMillis()).put("detail", detail?.take(500) ?: JSONObject.NULL)
        val kept = if (file.isFile && file.length() <= 262144) file.readLines().takeLast(255) else emptyList()
        file.writeText((kept + event.toString()).joinToString("\n", postfix = "\n"))
    }
    fun history(context: Context): String = EdgeRuntime.onMain {
        val file = File(context.filesDir, "shared_home/.z2term/actions/.history.jsonl")
        val lines = if (file.isFile && file.length() <= 262144) file.readLines().takeLast(256) else emptyList()
        JSONArray().apply { lines.forEach { put(JSONObject(it)) } }.toString()
    }
}
