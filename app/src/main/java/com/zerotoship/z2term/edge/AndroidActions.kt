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

/** Global actions and scroll gestures. Reads window bounds/focus, never nodes or typed text. */
class AndroidActions : AccessibilityService() {
    private val autoScroll by lazy { AndroidAutoScroll(this) }
    private data class ScrollTarget(val id: Int, val bounds: Rect)
    private var scrollTarget: ScrollTarget? = null

    @Suppress("DEPRECATION")
    private fun focusedTarget(): ScrollTarget? {
        val currentWindows = windows
        return try {
            val target = currentWindows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused &&
                    (Build.VERSION.SDK_INT < 30 || it.displayId == android.view.Display.DEFAULT_DISPLAY)
            } ?: return null
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

    override fun onServiceConnected() { active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (autoScroll.running && (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            if (runCatching { focusedTarget() }.getOrNull() != scrollTarget) autoScroll.stop()
        }
    }
    override fun onInterrupt() { autoScroll.stop() }
    override fun onUnbind(intent: Intent?): Boolean {
        autoScroll.stop()
        if (active === this) active = null
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        autoScroll.stop()
        if (active === this) active = null
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
                    try { context.startActivity(details) }
                    catch (_: android.content.ActivityNotFoundException) { context.startActivity(fallback) }
                    catch (_: SecurityException) { context.startActivity(fallback) }
                } else context.startActivity(fallback)
                return context.getString(com.zerotoship.z2term.R.string.edge_accessibility_help)
            }
            if (name == "app-info") {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
            EdgeRuntime.onMain { service.autoScroll.stop() }
            check(service.performGlobalAction(action)) { "Android rejected action: $name" }
            return ""
        }

        fun startAutoScroll(speedDp: Float, bounds: android.graphics.Rect, xPercent: Float = 50f,
            yPercent: Float = 50f, once: Boolean = false, requirePreviousTarget: Boolean = false, done: (String?) -> Unit) {
            val service = active ?: error("Enable z2term Android actions in Accessibility settings")
            val target = service.focusedTarget()
                ?: error(service.getString(com.zerotoship.z2term.R.string.edge_scroll_no_window))
            check(!requirePreviousTarget || target == service.scrollTarget) {
                service.getString(com.zerotoship.z2term.R.string.edge_scroll_no_window)
            }
            val area = Rect(target.bounds)
            check(area.intersect(bounds) && area.height() >= 96 * service.resources.displayMetrics.density) {
                service.getString(com.zerotoship.z2term.R.string.edge_scroll_no_window)
            }
            service.scrollTarget = target
            service.autoScroll.start(speedDp, area, xPercent, yPercent, once, {
                service.focusedTarget() == target &&
                    service.getSystemService(android.os.PowerManager::class.java).isInteractive &&
                    !service.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
            }, done)
        }

        fun stopAutoScroll(): Boolean {
            val scrolling = active?.autoScroll?.recentlyRunning() == true
            active?.autoScroll?.stop()
            return scrolling
        }

        fun recentlyAutoScrolling() = active?.autoScroll?.recentlyRunning() == true

        fun outsideTouch(event: android.view.MotionEvent) { active?.autoScroll?.outsideTouch(event) }

        fun connected() = active != null

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
