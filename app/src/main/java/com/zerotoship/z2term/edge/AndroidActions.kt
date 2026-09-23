package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.net.Uri
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityEvent
import org.json.JSONObject
import kotlinx.coroutines.*
import androidx.core.net.toUri

/** Global/coordinate actions use window metadata; explicit UI requests inspect non-editable nodes. */
class AndroidActions : AccessibilityService() {
    private val autoScroll by lazy { AndroidAutoScroll(this) }
    private val nodeScroll by lazy { AndroidNodeScroll(this) }
    private val coordinateStroke by lazy { AndroidStroke(this) }
    private val windowPackages = linkedMapOf<Int, String>()
    private data class ScrollTarget(val id: Int, val bounds: Rect)
    private var scrollTarget: ScrollTarget? = null

    /**
     * スクロールの相手にする窓。
     *
     * ⚠ **いま触っている窓 (`isActive`) を先に見る** (0.8.627)。入力欄を持たない窓や自由な大きさの窓
     * (フリーフォーム) は**入力フォーカスを持たないことがあり**、`isFocused` だけで選ぶと「前面に
     * 出ているのにスクロールできない」になる (利用者の指摘:「フリーフォームウィンドウとかだと
     * 上手くスクロールもしません」)。⚠ **自分の窓は相手にしない** — パネルを触った拍子に
     * 自分自身へスクロールを送っても何も起きないうえ、相手の取り違えに気付けない。
     */
    @Suppress("DEPRECATION")
    private fun focusedTarget(): ScrollTarget? {
        val currentWindows = windows
        return try {
            val applications = currentWindows.filter {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    (Build.VERSION.SDK_INT < 30 || it.displayId == android.view.Display.DEFAULT_DISPLAY) &&
                    windowPackages[it.id] != packageName
            }
            val target = applications.firstOrNull { it.isActive }
                ?: applications.firstOrNull { it.isFocused }
                ?: return null
            val bounds = Rect().also { target.getBoundsInScreen(it) }
            // The application's window may extend behind the IME (adjustNothing/edge-to-edge).
            // Exclude the entire area below an intersecting IME, including floating keyboards.
            currentWindows.filter {
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                    (Build.VERSION.SDK_INT < 30 || it.displayId == android.view.Display.DEFAULT_DISPLAY)
            }.forEach { ime ->
                val keyboard = Rect().also { ime.getBoundsInScreen(it) }
                bounds.bottom = ScrollArea(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    .aboveKeyboard(ScrollArea(keyboard.left, keyboard.top, keyboard.right, keyboard.bottom)).bottom
            }
            com.zerotoship.z2term.core.TerminalScrollViewport.current()?.let { viewport ->
                if (!bounds.intersect(viewport)) return null
            }
            if (bounds.isEmpty) return null
            ScrollTarget(target.id, bounds)
        } finally { currentWindows.forEach { it.recycle() } }
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        active = this
        EdgeRuntime.refreshHandleContrast()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.windowId >= 0) {
            event.packageName?.toString()?.let { windowPackages[event.windowId] = it }
            while (windowPackages.size > 64) windowPackages.remove(windowPackages.keys.first())
        }
        if (autoScroll.running && (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            if (runCatching { focusedTarget() }.getOrNull() != scrollTarget) { autoScroll.stop(); nodeScroll.stop() }
        }
    }
    override fun onInterrupt() {
        autoScroll.stop()
        nodeScroll.stop()
        com.zerotoship.z2term.automation.ActionRuntime.stop(reason = "Accessibility interrupted")
        com.zerotoship.z2term.automation.ActionCoordinatePicker.cancel()
        RegionShot.cancel()
    }
    override fun onUnbind(intent: Intent?): Boolean {
        autoScroll.stop()
        nodeScroll.stop()
        if (active === this) {
            com.zerotoship.z2term.automation.ActionCoordinatePicker.cancel()
            RegionShot.cancel()
            active = null
            EdgeRuntime.refreshHandleContrast()
        }
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        autoScroll.stop()
        nodeScroll.stop()
        if (active === this) {
            com.zerotoship.z2term.automation.ActionCoordinatePicker.cancel()
            RegionShot.cancel()
            active = null
            EdgeRuntime.refreshHandleContrast()
        }
        super.onDestroy()
    }

    companion object {
        @Volatile private var active: AndroidActions? = null
        private val actions = linkedMapOf(
            "back" to GLOBAL_ACTION_BACK, "home" to GLOBAL_ACTION_HOME,
            "recents" to GLOBAL_ACTION_RECENTS, "shade" to GLOBAL_ACTION_NOTIFICATIONS,
            "quicksettings" to GLOBAL_ACTION_QUICK_SETTINGS,
            "screenshot" to GLOBAL_ACTION_TAKE_SCREENSHOT,
            "split" to GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN,
        )

        fun command(context: Context, args: List<String>): String {
            require(args.size == 1) { "z2-key: back|home|recents|shade|quicksettings|screenshot|split|status|permission|app-info" }
            val name = args[0]
            if (name == "status") return JSONObject()
                .put("enabled", enabled(context))
                .put("connected", active != null)
                .put("actions", org.json.JSONArray(available())).toString()
            if (name == "permission") {
                val fallback = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= 30) {
                    val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                        .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, AndroidActions::class.java))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(details)
                    } catch (_: android.content.ActivityNotFoundException) {
                        context.startActivity(fallback)
                    } catch (_: SecurityException) {
                        context.startActivity(fallback)
                    }
                } else context.startActivity(fallback)
                return context.getString(com.zerotoship.z2term.R.string.edge_accessibility_help)
            }
            if (name == "app-info") {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return ""
            }
            val action = actions[name] ?: throw IllegalArgumentException("Unknown action: $name")
            if (name == "home" && active == null) {
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return ""
            }
            val service = active ?: throw IllegalStateException(context.getString(com.zerotoship.z2term.R.string.edge_accessibility_help))
            if (Build.VERSION.SDK_INT >= 30) {
                require(name == "split" || service.systemActions.any { it.id == action }) { "Android does not offer this action: $name" }
            } else require(name != "screenshot") { "Screenshot requires Android 11" }
            EdgeRuntime.onMain { service.autoScroll.stop(); service.nodeScroll.stop() }
            check(service.performGlobalAction(action)) { "Android rejected action: $name" }
            return ""
        }

        /**
         * スクロールを始める。
         *
         * [how] は `auto` / `node` / `swipe` (空欄は `auto`)。⚠ **既定でスワイプを注入しない**
         * (0.8.627) — 注入したスワイプはアプリから見て指と区別が付かず、払う操作に機能が
         * 割り当たっている画面では**その機能が動いてしまう** ([AndroidNodeScroll] の解説)。
         * `auto` は部品へ頼み、スクロールできる部品が無いときだけスワイプへ落とす。
         */
        fun startAutoScroll(speedDp: Float, bounds: android.graphics.Rect, xPercent: Float = 50f,
            yPercent: Float = 50f, once: Boolean = false, requirePreviousTarget: Boolean = false,
            how: String = "auto", done: (String?) -> Unit) {
            val service = active ?: error("Enable z2term Android actions in Accessibility settings")
            check(!service.coordinateStroke.inFlight) { "Wait for the previous coordinate gesture to finish" }
            val target = service.focusedTarget()
                ?: error(service.getString(com.zerotoship.z2term.R.string.edge_scroll_no_window))
            check(!requirePreviousTarget || target == service.scrollTarget) {
                service.getString(com.zerotoship.z2term.R.string.edge_scroll_no_window)
            }
            val area = Rect(target.bounds)
            check(area.intersect(bounds)) {
                service.getString(com.zerotoship.z2term.R.string.edge_scroll_no_window)
            }
            val stillTarget = {
                service.focusedTarget() == target &&
                    service.getSystemService(android.os.PowerManager::class.java).isInteractive &&
                    !service.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
            }
            service.scrollTarget = target
            if (how != "swipe") {
                service.autoScroll.stop()
                if (service.nodeScroll.start(speedDp, target.id, area, xPercent, yPercent, once, stillTarget, done)) return
                check(how != "node") { service.getString(com.zerotoship.z2term.R.string.edge_scroll_no_scrollable) }
            }
            service.nodeScroll.stop()
            // ⚠ スワイプは Android の MOVE 取り込み間隔ぶんの長さが要る ([EdgeScrollTiming])。
            //   部品へ頼む側にこの制限は無いので、確かめるのはここへ入ってから。
            check(area.height() >= 96 * service.resources.displayMetrics.density) {
                service.getString(com.zerotoship.z2term.R.string.edge_scroll_no_window)
            }
            service.autoScroll.start(speedDp, area, xPercent, yPercent, once, stillTarget, done)
        }

        fun stopAutoScroll(): Boolean {
            val scrolling = active?.autoScroll?.recentlyRunning() == true ||
                active?.nodeScroll?.recentlyRunning() == true
            active?.autoScroll?.stop()
            active?.nodeScroll?.stop()
            return scrolling
        }

        fun recentlyAutoScrolling() = active?.autoScroll?.recentlyRunning() == true ||
            active?.nodeScroll?.recentlyRunning() == true

        fun outsideTouch(event: android.view.MotionEvent) {
            active?.autoScroll?.outsideTouch(event)
            active?.nodeScroll?.outsideTouch(event)
        }

        fun connected() = active != null

        internal fun contrastService(): AndroidActions? = active

        /** Uses package metadata from window events without retrieving UI nodes. */
        internal fun focusedPackage(): String? {
            val service = active ?: return null
            val target = service.focusedTarget() ?: return null
            return service.windowPackages[target.id]
        }

        internal fun targetMatches(packageName: String): Boolean {
            val service = active ?: return false
            val target = service.focusedTarget() ?: return false
            return service.windowPackages[target.id] == packageName
        }

        private fun uiTarget(service: AndroidActions, targetPackage: String): AndroidUiElements.Window {
            check(active === service) { "Accessibility disconnected" }
            check(service.getSystemService(android.os.PowerManager::class.java).isInteractive &&
                !service.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) { "Screen is off or locked" }
            val target = service.focusedTarget() ?: error("No focused application window")
            check(service.windowPackages[target.id] == targetPackage) { "Target app is not focused" }
            return AndroidUiElements.Window(target.id, Rect(target.bounds))
        }

        internal fun queryUi(step: com.zerotoship.z2term.automation.ActionDefinition.Step.Ui,
            done: (Boolean, String?) -> Unit): () -> Unit {
            val service = active ?: error("Accessibility disconnected")
            val job = CoroutineScope(Dispatchers.Main.immediate).launch {
                try { done(AndroidUiElements.query(service, step) { uiTarget(service, step.target) }, null) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    if (step.operation == "wait-ui" && e is AndroidUiElements.Changed) done(false, null)
                    else done(false, e.message ?: "UI lookup failed")
                }
            }
            return { job.cancel() }
        }

        internal suspend fun inspectUi(targetPackage: String): List<AndroidUiElements.Entry> {
            com.zerotoship.z2term.automation.ActionDefinition.packageName(targetPackage)
            val service = active ?: error("Accessibility disconnected")
            return AndroidUiElements.inspect(service, targetPackage) { uiTarget(service, targetPackage) }
        }

        internal fun pickUiElements(request: String, target: String) {
            val service = active ?: error("Enable z2term Android actions in Accessibility settings")
            com.zerotoship.z2term.automation.ActionCoordinatePicker.start(service, request, swipe = false, target = target)
        }

        internal fun pickCoordinates(request: String, swipe: Boolean, fingers: Int = 1, freehand: Boolean = false) {
            val service = active ?: error("Enable z2term Android actions in Accessibility settings")
            com.zerotoship.z2term.automation.ActionCoordinatePicker.start(service, request, swipe, fingers = fingers, freehand = freehand)
        }

        internal fun recordTouches(request: String, live: Boolean) {
            val service = active ?: error("Enable z2term Android actions in Accessibility settings")
            com.zerotoship.z2term.automation.ActionCoordinatePicker.start(service, request, swipe = true,
                recording = true, live = live)
        }

        internal fun gesturesInFlight(): Boolean = active?.let { it.coordinateStroke.inFlight || it.autoScroll.inFlight } == true

        internal fun stroke(step: com.zerotoship.z2term.automation.ActionDefinition.Step.Stroke,
            screen: com.zerotoship.z2term.automation.ActionDefinition.Screen, done: (String?) -> Unit): () -> Unit {
            val service = active ?: error("Enable z2term Android actions in Accessibility settings")
            check(!service.autoScroll.running && !service.autoScroll.inFlight) { "Wait for scrolling to finish" }
            val target = service.focusedTarget() ?: error("No focused application window")
            check(service.windowPackages[target.id] == step.target) { "Target app is not focused; switch to ${step.target}" }
            val points = step.tracks(screen).flatten()
            check(points.all { target.bounds.contains(it.x.toInt(), it.y.toInt()) }) {
                "Coordinates must be inside the visible target window, above the keyboard"
            }
            return service.coordinateStroke.dispatch(step, screen, stillTarget = {
                val current = service.focusedTarget()
                current != null && current.id == target.id && current.bounds == target.bounds &&
                    service.windowPackages[current.id] == step.target &&
                    com.zerotoship.z2term.automation.ActionRuntime.screen(service) == screen
            }, done = done)
        }

        fun enabled(context: Context): Boolean = context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                val service = it.resolveInfo.serviceInfo
                ComponentName(service.packageName, service.name) == ComponentName(context, AndroidActions::class.java)
            }

        private fun available(): List<String> {
            val service = active ?: return listOf("home")
            return if (Build.VERSION.SDK_INT >= 30) {
                val ids = service.systemActions.map { it.id }.toSet()
                actions.filterValues { it in ids }.keys.toList()
            } else actions.keys.filter { it != "screenshot" }
        }
    }
}
