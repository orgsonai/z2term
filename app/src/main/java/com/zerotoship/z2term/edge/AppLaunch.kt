package com.zerotoship.z2term.edge

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.zerotoship.z2term.MainActivity
import android.provider.Settings
import android.view.WindowManager
import com.zerotoship.z2term.R

/** The OS owns the final window mode and existing tasks. */
object AppLaunch {
    private var pendingSplit: Pair<Intent, Long>? = null
    private val main = Handler(Looper.getMainLooper())

    /** A service has no source task. Keep the terminal open as the adjacent pane. */
    fun resumePendingSplit(activity: Activity) {
        if (!activity.hasWindowFocus()) return
        val request = pendingSplit ?: return
        pendingSplit = null
        if (SystemClock.elapsedRealtime() - request.second > 10_000L) return
        runCatching {
            check(Build.VERSION.SDK_INT >= 32 || activity.isInMultiWindowMode) {
                activity.getString(R.string.edge_split_unavailable)
            }
            activity.startActivity(Intent(request.first).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT))
        }.onFailure { Toast.makeText(activity, it.message, Toast.LENGTH_LONG).show() }
    }

    val modes = listOf("full", "freeform", "split", "ask")
    fun choose(context: Context, includeAsk: Boolean = true, selected: (String) -> Unit) {
        val choices = if (includeAsk) modes else modes.dropLast(1)
        val labels = listOf(R.string.edge_window_full, R.string.edge_window_freeform,
            R.string.edge_window_split, R.string.edge_window_ask)
        AlertDialog.Builder(context).setTitle(R.string.edge_window_mode)
            .setItems(choices.indices.map { context.getString(labels[it]) }.toTypedArray()) { _, index -> selected(choices[index]) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    data class FreeformOptions(val reuseTask: Boolean = false, val osBounds: Boolean = false, val boundsOnly: Boolean = false) {
        // Standard launches leave the existing task and remembered bounds to Android.
        // The legacy switches remain accepted for saved commands. Only bounds-only opts into sizing.
        val requestsBounds: Boolean get() = boundsOnly
        fun validate(mode: String) {
            require(this == FreeformOptions() || mode == "freeform") { "Freeform options require --window freeform" }
            require(!(osBounds && boundsOnly)) { "--os-bounds and --bounds-only cannot be combined" }
        }
    }

    internal fun activityFlags(flags: Int, mode: String): Int {
        val removed = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
            if (mode == "freeform") Intent.FLAG_ACTIVITY_MULTIPLE_TASK else 0
        return (flags and removed.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun launch(context: Context, target: Intent, mode: String, freeform: FreeformOptions = FreeformOptions()) {
        freeform.validate(mode)
        require(mode in modes) { "window: full|freeform|split|ask" }
        if (mode == "ask") {
            context.startActivity(Intent(context, LaunchModeActivity::class.java)
                .putExtra("target", target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (mode == "split") {
            val request = Intent(target)
            main.post {
                pendingSplit = request to SystemClock.elapsedRealtime()
                runCatching {
                    context.startActivity(Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure {
                    pendingSplit = null
                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        val intent = Intent(target).apply { flags = activityFlags(target.flags, mode) }
        val options = ActivityOptions.makeBasic()
        when (mode) {
            "freeform" -> {
                val supported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
                    runCatching { Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1 }.getOrDefault(false)
                check(supported) { context.getString(R.string.edge_freeform_unavailable) }
                if (freeform.requestsBounds) {
                    val wm = context.getSystemService(WindowManager::class.java)
                    @Suppress("DEPRECATION")
                    val screen = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds else {
                        val metrics = android.util.DisplayMetrics(); wm.defaultDisplay.getMetrics(metrics)
                        Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
                    }
                    options.setLaunchBounds(Rect(screen.left + screen.width() / 10, screen.top + screen.height() / 10,
                        screen.right - screen.width() / 10, screen.bottom - screen.height() / 10))
                }
            }
            "full" -> {
                options.setLaunchBounds(null)
            }
        }
        val bundle = options.toBundle()
        if (mode == "freeform" && !freeform.boundsOnly) {
            // AOSP ActivityOptions key. The bounds-only option lets callers compare the public request.
            // This is not a public SDK contract; device implementations may reject the request.
            bundle.putInt("android.activity.windowingMode", 5 /* WINDOWING_MODE_FREEFORM */)
        }
        context.startActivity(intent, bundle)
    }
}
