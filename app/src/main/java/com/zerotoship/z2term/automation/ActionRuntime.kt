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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        var target: String? = null, var service: ActionService? = null, var watchdog: Runnable? = null,
        /** Started from the macro chooser: show the stop bar even when its setting is off. */
        val bar: Boolean = false) {
        val definition get() = program.root
        /** 人らしい操作の強さ (0 はなし)。最初のマクロの `human=` で始まり、停止の帯の ～ で入り切りできる。 */
        var humanLevel: Int = program.root.humanLevel
        /** ～ で切ったあと入れ直すときの強さ。 */
        var humanRestore: Int = program.root.humanLevel.takeIf { it > 0 } ?: ActionHumanize.DEFAULT_LEVEL
        val human get() = humanLevel > 0
        /** 揺らすもの (`jitter=`、1.0.203)。最初のマクロの見出しで決まる (呼び先の見出しは見ない。強さと同じ)。 */
        val jitter: ActionHumanize.Parts = program.root.jitter
        val random = kotlin.random.Random(System.nanoTime())
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
    /** 後片付けの命令の上限 (秒)。サーバーを止める程度を想定し、詰まっても残り続けないようにする。 */
    private const val CLEANUP_TIMEOUT_S = 60L
    private const val SHADE_REASON = "Notification shade opened"
    /** How long a launch step waits for the launched app to come to the front. */
    private const val LAUNCH_WAIT_MS = 10_000L
    private const val COLOR_RADIUS = 4
    /** `tap ui` の押す長さ (座標の tap と同じ)。 */
    private const val TAP_MS = 80L
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
    /**
     * Shows the bar that picks a macro and runs it (`z2-action pick`, 1.0.102, issue #2). One edge item or
     * tile then covers every macro. The run it starts keeps the bar as its stop bar, and the bar closes when
     * that run ends.
     */
    fun pick(context: Context): String = EdgeRuntime.onMain {
        check(current == null) { "An action macro is already running; use z2-action status or stop" }
        val host = AndroidActions.overlayHost() ?: error("Enable z2term Android actions using z2-key permission")
        // ルーチン (呼び出し専用、1.0.203) は選択肢に出さない。
        val names = store(context).runnableNames()
        check(names.isNotEmpty()) { context.getString(com.zerotoship.z2term.R.string.action_chooser_empty) }
        showPicker(context.applicationContext, host, names, null)
        ""
    }
    /** 選んで実行する帯。～ の初めの状態は、選んだマクロの見出しの `human=` (読めなければ切り)。 */
    private fun showPicker(app: Context, host: android.accessibilityservice.AccessibilityService, names: List<String>, first: String?) {
        ActionRunOverlay.showPicker(host, names, first, human = { name -> runCatching { store(app).read(name).humanLevel > 0 }.getOrDefault(false) }) { name, human ->
            try { start(app, name, waitForForeground = true, bar = true, human = human); true } catch (e: Exception) {
                android.widget.Toast.makeText(app, e.message, android.widget.Toast.LENGTH_LONG).show()
                false
            }
        }
    }
    fun start(context: Context, name: String, background: (() -> Unit)? = null, waitForForeground: Boolean = false,
        bar: Boolean = false, args: List<String> = emptyList(), human: Boolean? = null): String {
        val program = store(context).snapshot(name, args)
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
                awaitBackground = waitForForeground || (program.usesCurrentTarget && background != null), allowOwnForeground = waitForForeground,
                bar = bar)
            // 選ぶ帯の ～ (1.0.196): 入れるならマクロの強さ (書いていなければ既定)、切るなら 0。この実行だけ。
            human?.let { run.humanLevel = if (it) run.humanRestore else 0 }
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
            toggle = { runCatching { if (run.engine.paused) resume(run.id) else pause(run.id) } }, force = run.bar,
            human = {
                if (current === run) {
                    if (run.human) { run.humanRestore = run.humanLevel; run.humanLevel = 0 } else run.humanLevel = run.humanRestore
                }
                run.human
            }, humanOn = run.human)
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
            ActionRunOverlay.update(run.engine.loops, run.engine.blockStep, run.engine.blockSize, run.engine.calls)
            run.service?.showProgress(ActionRunOverlay.describe(run.context, run.engine.loops, run.engine.blockStep, run.engine.blockSize,
                run.engine.calls))
            log(run, "step", when (step) {
                is ActionDefinition.Step.Repeat -> "repeat"
                is ActionDefinition.Step.Branch -> "if"
                is ActionDefinition.Step.Call -> "call"
                is ActionDefinition.Step.Watch -> "watch"
                is ActionDefinition.Step.Break -> "break"
                is ActionDefinition.Step.Try -> "try"
                is ActionDefinition.Step.Set -> if (step.add) "add" else "set"
                is ActionDefinition.Step.ReadColors -> "read-colors"
                is ActionDefinition.Step.Input -> if (step.text == null) "enter" else "type"
                is ActionDefinition.Step.UiTap -> "tap-ui"
                is ActionDefinition.Step.RunOutput -> "run-output"
                is ActionDefinition.Step.Ui -> step.operation
                is ActionDefinition.Step.WaitFor -> if (step.until) "wait-until" else "wait-while"
                is ActionDefinition.Step.Wait -> "wait"
                is ActionDefinition.Step.Launch -> "launch"
                is ActionDefinition.Step.Key -> "key"
                is ActionDefinition.Step.Command -> "command"
                is ActionDefinition.Step.Stroke -> "stroke"
                is ActionDefinition.Step.Scroll -> "scroll"
            })
        }, { result -> finish(run, result) },
            resolve = { run.program.definitions.getValue(it) },
            name = run.name,
            branch = { _, matched -> log(run, "branch", matched.toString()) },
            test = { spec, target, answer -> test(run, spec, target, answer) },
            expand = { step, produced -> expand(run, step, produced) },
            // 割り込みは手順の 1 つとして記録する (履歴の一覧では手順と同じく畳まれる)。
            watched = { log(run, "step", "watch " + it.name) },
            catchable = ::catchable,
            caught = { error -> log(run, "caught", error) })
    }
    /**
     * `try` で受け止めない失敗 (1.0.190)。画面が消えた・回転した・ユーザー補助が切れた・AI 操作の一時停止で
     * z2term 自身を操作できない、はどの手順へ進んでも同じく失敗するので、別の手順へ逃がさずに止める。
     */
    private fun catchable(error: String) = error !in setOf("Screen is off or locked", "Accessibility disconnected",
        "Screen size/orientation changed") && !error.startsWith("AI operation is paused")
    /**
     * `run-output`: シェルの標準出力を手順として読む。今の画面寸法と、その行の対象アプリを引き継いで検査する。
     * 出力は 64 KiB まで (EdgeRunner の上限)。終了コードが 0 以外なら失敗 (出力は使わない)。
     */
    private fun expand(run: Run, step: ActionDefinition.Step.RunOutput,
        produced: (List<ActionDefinition.Step>?, String?) -> Unit): () -> Unit {
        checkDevice(run)
        val values = run.engine.args
        val started = run.runner.run("macro:${run.id}", shell(run, step.text), run.definition.timeoutMs / 1000, positional = values) { result ->
            if (result.error != null) produced(null, result.error)
            else {
                val steps = try { ActionDefinition.fragment(result.output, run.screen, step.target, values) }
                    catch (e: Exception) { produced(null, e.message ?: "run-output produced invalid steps"); return@run }
                produced(steps, null)
            }
        }
        check(started) { "Cannot start shell command" }
        return { run.runner.cancelAll() }
    }
    /**
     * 条件式を 1 回確かめる (if / wait-until / wait-while)。端末の状態はその場で読み、画面の要素・色と
     * シェルは答えを待つ。画面を読む項は手順と同じ対象アプリを見て、対象が前面に無ければ「無い」と答える。
     * ⚠ 画面の読み取りに z2-ui のアプリごとの許可は使わない。操作は元から z2-action で許可なしにできるため、
     * 読み取りだけ縛っても守りにならない (HANDBOOK「AI app control」の注意を参照)。
     */
    private fun test(run: Run, spec: String, target: String, answer: (Boolean?, String?) -> Unit): () -> Unit {
        checkDevice(run)
        return ActionCondition.evaluate(ActionCondition.parse(spec), state = {
            val state = com.zerotoship.z2term.service.Z2ApiBridge.stateSnapshot(run.context).toMutableMap()
            AndroidActions.focusedPackage()?.let { state["foreground"] = it }
            state
        }, probe = { term, done -> probe(run, term, target, done) }, done = answer, variables = { run.engine.variables })
    }
    private fun probe(run: Run, term: ActionCondition.Term, target: String, done: (Boolean?, String?) -> Unit): () -> Unit {
        val noWork: () -> Unit = {}
        val resolved = if (target == ActionDefinition.CURRENT_TARGET) AndroidActions.focusedPackage() else target
        fun shown() = resolved != null && AndroidActions.targetMatches(resolved)
        return when (term) {
            is ActionCondition.Term.State, is ActionCondition.Term.Var -> error("State and variable terms are evaluated directly")
            is ActionCondition.Term.Ui -> {
                if (!shown()) { done(false, null); return noWork }
                AndroidActions.queryUi(ActionDefinition.Step.Ui(resolved!!, "if-ui", term.selector)) { found, error -> done(found, error) }
            }
            is ActionCondition.Term.Color -> {
                if (android.os.Build.VERSION.SDK_INT < 30) { done(null, "Colour conditions require Android 11 or later"); return noWork }
                if (!shown()) { done(false, null); return noWork }
                if (ActionRunOverlay.covers(term.x, term.y)) {
                    done(null, "The colour point ${term.x},${term.y} is under the stop bar; move the bar away from it"); return noWork
                }
                color(term, done)
            }
            is ActionCondition.Term.Shell -> {
                val started = run.runner.run("macro:${run.id}", shell(run, term.text), run.definition.timeoutMs / 1000,
                    positional = run.engine.args) { result ->
                    if (result.exit != null) done(result.exit == 0, null) else done(null, result.error ?: "Shell condition failed")
                }
                if (!started) { done(null, "Cannot start shell command"); return noWork }
                ({ run.runner.cancelAll() })
            }
        }
    }
    /** シェルの文の前に、今の変数を `Z2_VAR_名前` として書き出す (1.0.192)。 */
    private fun shell(run: Run, text: String): String = run.engine.variables.entries.joinToString("") { (name, value) ->
        "export Z2_VAR_$name=" + com.zerotoship.z2term.service.HeadlessRun.shSingleQuote(value) + "; "
    } + text
    /** `read-colors`: 全部の点を 1 枚の撮影から読む (1.0.200)。読み方は [color] と同じ。 */
    @androidx.annotation.RequiresApi(30)
    private fun readColors(step: ActionDefinition.Step.ReadColors, store: (String) -> Unit, done: (String?) -> Unit): () -> Unit {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch {
            val error = try {
                val sample = com.zerotoship.z2term.edge.AndroidUiLook.sample(
                    com.zerotoship.z2term.edge.AndroidUiLook.Points(step.points, COLOR_RADIUS), gate = {}, stableMs = 0)
                store(sample.rgb.joinToString(" ") { "%06x".format(it and 0xFFFFFF) })
                null
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                e.message ?: "Cannot read the screen colour"
            }
            done(error)
        }
        return { job.cancel() }
    }
    /** 1 点の色を 1 回読む。点は [COLOR_RADIUS] px 四方の中央値 (z2-ui colors と同じ読み方)。 */
    @androidx.annotation.RequiresApi(30)
    private fun color(term: ActionCondition.Term.Color, done: (Boolean?, String?) -> Unit): () -> Unit {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                val sample = com.zerotoship.z2term.edge.AndroidUiLook.sample(
                    com.zerotoship.z2term.edge.AndroidUiLook.Points(listOf(term.x to term.y), COLOR_RADIUS), gate = {}, stableMs = 0)
                done(term.matches(sample.rgb[0]), null)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                done(null, e.message ?: "Cannot read the screen colour")
            }
        }
        return { job.cancel() }
    }
    /**
     * While AI operation (z2-ui) is paused or stopped, a macro may not operate z2term's own screen (1.0.103).
     * The AI has a shell, so it could otherwise save a macro that presses Resume (or turns AI operation on)
     * in z2term's settings and lift the user's pause itself. Macros on other apps run as usual; the
     * notification shade is already out of reach because it pauses the macro.
     */
    private fun guardOwnScreen(run: Run) {
        if (!UiBridge.holdsOwnScreen(run.context)) return
        check(AndroidActions.focusedPackage() != run.context.packageName) {
            "AI operation is paused or stopped; until it is resumed or turned on again in z2term, macros cannot operate z2term itself"
        }
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
        if (step is ActionDefinition.Step.Stroke || step is ActionDefinition.Step.Scroll || step is ActionDefinition.Step.Key ||
            step is ActionDefinition.Step.Input || step is ActionDefinition.Step.UiTap ||
            (step is ActionDefinition.Step.Ui && step.operation != "wait-ui")) guardOwnScreen(run)
        fun done(error: String?) { run.target = null; completed(error) }
        fun target(requested: String): String = if (requested == ActionDefinition.CURRENT_TARGET)
            AndroidActions.focusedPackage() ?: error(run.context.getString(com.zerotoship.z2term.R.string.action_run_no_foreground))
            else requested
        when (step) {
            is ActionDefinition.Step.Repeat, is ActionDefinition.Step.Branch, is ActionDefinition.Step.Call,
            is ActionDefinition.Step.Watch, is ActionDefinition.Step.RunOutput, is ActionDefinition.Step.Break,
            is ActionDefinition.Step.Try, is ActionDefinition.Step.Set ->
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
            is ActionDefinition.Step.Wait -> return schedule(if (run.human) ActionHumanize.waitMs(step.ms, run.random, run.humanLevel, run.jitter) else step.ms) { done(null) }
            is ActionDefinition.Step.WaitFor -> {
                val label = if (step.until) "wait-until" else "wait-while"
                return ActionUiWait(::schedule).start(step.ms, { callback ->
                    // 成立 (wait-until) / 不成立 (wait-while) になったら次へ。
                    test(run, step.condition, step.target) { matched, error ->
                        callback(matched == step.until, error ?: if (matched == null) "Condition failed" else null)
                    }
                }, ::done, "$label timed out after ${step.ms} ms")
            }
            is ActionDefinition.Step.Launch -> {
                val intent = run.context.packageManager.getLaunchIntentForPackage(step.packageName) ?: error("No launchable app: ${step.packageName}")
                run.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                // ⚠ startActivity returns before the app is in front. A cold start can take seconds (Settings took
                // 1.7 s), and a following step on the current app then took the screen behind it as its target and
                // failed with "Target application changed" once the app arrived (1.0.111). Finish the step only when
                // the launched app is in front.
                return ActionUiWait(::schedule).start(LAUNCH_WAIT_MS, { callback ->
                    callback(AndroidActions.focusedPackage() == step.packageName, null)
                    val noWork: () -> Unit = {}
                    noWork
                }, ::done, run.context.getString(com.zerotoship.z2term.R.string.action_launch_timeout, step.packageName,
                    (LAUNCH_WAIT_MS / 1000).toInt()))
            }
            is ActionDefinition.Step.Key -> { AndroidActions.command(run.context, listOf(step.name)); done(null) }
            is ActionDefinition.Step.ReadColors -> {
                // ⚠ check(...) では lint が版の確認と読まない (NewApi で release が止まった)。色の条件と同じく if で返す。
                if (android.os.Build.VERSION.SDK_INT < 30) { done("read-colors requires Android 11 or later"); return {} }
                val resolved = target(step.target)
                run.target = resolved
                checkDevice(run)
                check(AndroidActions.targetMatches(resolved)) { "read-colors: $resolved is not in front" }
                step.points.firstOrNull { (x, y) -> ActionRunOverlay.covers(x, y) }?.let { (x, y) ->
                    error("The colour point $x,$y is under the stop bar; move the bar away from it")
                }
                val variables = run.engine.variables
                check(step.name in variables || variables.size < ActionDefinition.MAX_VARIABLES) {
                    "At most ${ActionDefinition.MAX_VARIABLES} variables"
                }
                return readColors(step, { variables[step.name] = it }, ::done)
            }
            is ActionDefinition.Step.Command -> {
                check(run.runner.run("macro:${run.id}", shell(run, step.text), run.definition.timeoutMs / 1000,
                    positional = run.engine.args) { done(it.error) }) { "Cannot start shell command" }
                return { run.runner.cancelAll() }
            }
            is ActionDefinition.Step.Stroke -> {
                val resolved = step.copy(target = target(step.target))
                run.target = resolved.target
                return send(run, resolved, ::done)
            }
            is ActionDefinition.Step.UiTap -> {
                // 要素を探して、その真ん中を指でタップする (人らしい操作・軌跡も座標の tap と同じ)。
                val resolved = target(step.target)
                run.target = resolved
                checkDevice(run)
                var inner: (() -> Unit)? = null
                val lookup = AndroidActions.locateUi(resolved, step.selector) { box, error ->
                    if (current !== run) return@locateUi
                    if (error != null || box == null) { done(error ?: "UI element not found: ${step.selector}"); return@locateUi }
                    inner = try {
                        send(run, ActionDefinition.Step.Stroke(resolved, "px", box.exactCenterX(), box.exactCenterY(),
                            box.exactCenterX(), box.exactCenterY(), TAP_MS), ::done)
                    } catch (e: Exception) { done(e.message ?: "Action failed"); null }
                }
                return { lookup(); inner?.invoke() }
            }
            is ActionDefinition.Step.Input -> {
                val field = target(step.target)
                run.target = field
                checkDevice(run)
                val text = step.text
                val typed = if (run.human && text != null) ActionHumanize.prefixes(text) else emptyList()
                if (typed.size < 2 || typed.size > ActionHumanize.MAX_TYPED_ONE_BY_ONE) {
                    AndroidActions.input(field, text)
                    done(null)
                } else {
                    // 人らしい打鍵 (1.0.193): 1 文字ずつ長くして入れ、文字の間を少しずつ空ける。
                    // 一時停止で取り消され、再開すると最初から入れ直す (置き換えなので二重にならない)。
                    var index = 0
                    var cancel: (() -> Unit)? = null
                    fun next() {
                        cancel = null
                        if (current !== run) return
                        try { checkDevice(run); AndroidActions.input(field, typed[index]) }
                        catch (e: Exception) { done(e.message ?: "Typing failed"); return }
                        index++
                        if (index == typed.size) { done(null); return }
                        val last = typed[index - 1].codePointBefore(typed[index - 1].length)
                        cancel = schedule(ActionHumanize.keyMs(last, run.random, run.humanLevel, run.jitter)) { next() }
                    }
                    next()
                    return { cancel?.invoke(); cancel = null }
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
    /** 座標の指を送る。停止の帯をよけ、人らしい操作 (human=) と軌跡の表示 (設定) を掛ける。 */
    private fun send(run: Run, stroke: ActionDefinition.Step.Stroke, done: (String?) -> Unit): (() -> Unit)? {
        // Only where a finger lands decides which window receives it; avoid the stop bar along the whole path.
        val points = stroke.tracks(run.screen).flatten()
        return ActionRunOverlay.clear({ box -> points.any { box.contains(it.x.toInt(), it.y.toInt()) } }, ::schedule, done) { finished ->
            check(current === run) { "Stopped" }
            AndroidActions.stroke(stroke, run.screen, finished,
                human = if (run.human) { s, area -> ActionHumanize.stroke(s, run.screen, area, run.random, run.humanLevel, run.jitter) } else null,
                trail = ActionRunOverlay.trailEnabled(run.context))
        }
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
        // 終わったあとも帯を残す設定 (1.0.196、既定は閉じる) なら、帯を「選んで実行」に替えてこのマクロを選んでおく。
        val keep = ActionRunOverlay.owner == ActionRunOverlay.Owner.MACRO && ActionRunOverlay.keepEnabled(run.context)
        ActionRunOverlay.dismiss(ActionRunOverlay.Owner.MACRO)
        if (keep) runCatching {
            val host = AndroidActions.overlayHost() ?: return@runCatching
            store(run.context).runnableNames().takeIf { it.isNotEmpty() }?.let { showPicker(run.context, host, it, run.name) }
        }
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
        if (result.state != "completed") run.definition.cleanup?.let { cleanup(run, result, it) }
    }
    /**
     * 後片付け (`cleanup=`、1.0.205)。止めた・失敗した・時間切れのとき、最初のマクロの見出しの命令をシェルで流す。
     * 画面が消えていても走る。マクロの変数 (`Z2_VAR_…`) と呼び出しの値 (`$1`…) に加えて、
     * `Z2_ACTION_NAME` / `Z2_ACTION_STATE` (cancelled / failed / timeout) / `Z2_ACTION_ERROR` を渡す。
     * 結果は履歴に `cleanup` として残す。⚠ 実行の枠 ([current]) は使わないので、次のマクロはすぐ始められる。
     */
    private fun cleanup(run: Run, result: ActionExecution.Result, command: String) {
        fun quote(value: String) = com.zerotoship.z2term.service.HeadlessRun.shSingleQuote(value)
        val script = "export Z2_ACTION_NAME=" + quote(run.name) + "; export Z2_ACTION_STATE=" + quote(result.state) +
            "; export Z2_ACTION_ERROR=" + quote(result.error.orEmpty()) + "; " + shell(run, command)
        val started = runCatching {
            EdgeRunner(run.context).run("cleanup:${run.id}", script, CLEANUP_TIMEOUT_S, positional = run.definition.args) { done ->
                runCatching { log(run, "cleanup", done.error ?: "exit 0") }
            }
        }.getOrDefault(false)
        if (!started) runCatching { log(run, "cleanup", "Cannot start shell command") }
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
