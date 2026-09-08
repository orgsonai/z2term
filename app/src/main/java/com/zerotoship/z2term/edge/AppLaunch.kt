package com.zerotoship.z2term.edge

import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import com.zerotoship.z2term.R

/** Public Android launch requests only. The OS owns the final window mode and existing tasks. */
object AppLaunch {
    val modes = listOf("full", "freeform", "split", "ask")
    fun choose(context: Context, includeAsk: Boolean = true, selected: (String) -> Unit) {
        val choices = if (includeAsk) modes else modes.dropLast(1)
        val labels = listOf(R.string.edge_window_full, R.string.edge_window_freeform,
            R.string.edge_window_split, R.string.edge_window_ask)
        AlertDialog.Builder(context).setTitle(R.string.edge_window_mode)
            .setItems(choices.indices.map { context.getString(labels[it]) }.toTypedArray()) { _, index -> selected(choices[index]) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    fun launch(context: Context, target: Intent, mode: String) {
        require(mode in modes) { "window: full|freeform|split|ask" }
        if (mode == "ask") {
            context.startActivity(Intent(context, LaunchModeActivity::class.java)
                .putExtra("target", target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val intent = Intent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic()
        when (mode) {
            "freeform" -> {
                val supported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
                    runCatching { Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1 }.getOrDefault(false)
                check(supported) { context.getString(R.string.edge_freeform_unavailable) }
                val wm = context.getSystemService(WindowManager::class.java)
                @Suppress("DEPRECATION")
                val screen = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds else {
                    val metrics = android.util.DisplayMetrics(); wm.defaultDisplay.getMetrics(metrics)
                    Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
                }
                options.setLaunchBounds(Rect(screen.left + screen.width() / 10, screen.top + screen.height() / 10,
                    screen.right - screen.width() / 10, screen.bottom - screen.height() / 10))
            }
            "split" -> intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            "full" -> {
                intent.flags = intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT.inv()
                options.setLaunchBounds(null)
            }
        }
        context.startActivity(intent, options.toBundle())
    }
}
