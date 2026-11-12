package com.zerotoship.z2term.edge

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.zerotoship.z2term.R

/** A transient choice for each launch; it stores no preferences. */
class LaunchModeActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getParcelableExtra<Intent>("target") ?: run { finish(); return }
        // An app in another profile opens through LauncherApps, which has no split pane.
        val user = intent.getParcelableExtra<android.os.UserHandle>("profile")
        val choices = AppLaunch.modes.dropLast(1).filter { user == null || it != "split" }
        val labels = mapOf("full" to R.string.edge_window_full, "freeform" to R.string.edge_window_freeform,
            "split" to R.string.edge_window_split)
        AlertDialog.Builder(this).setTitle(R.string.edge_window_mode)
            .setItems(choices.map { getString(labels.getValue(it)) }.toTypedArray()) { _, index ->
                runCatching { AppLaunch.launch(this, target, choices[index], user = user) }
                    .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                finish()
            }.setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
    }
}
