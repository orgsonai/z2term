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
import android.view.accessibility.AccessibilityEvent
import org.json.JSONObject

/** Global actions only: no screen contents, keystrokes or UI hierarchy are requested. */
class AndroidActions : AccessibilityService() {
    override fun onServiceConnected() { active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: Intent?): Boolean {
        if (active === this) active = null
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
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
            check(service.performGlobalAction(action)) { "Android rejected action: $name" }
            return ""
        }

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
