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
        AlertDialog.Builder(this).setTitle(R.string.edge_window_mode)
            .setItems(arrayOf(getString(R.string.edge_window_full), getString(R.string.edge_window_freeform),
                getString(R.string.edge_window_split))) { _, index ->
                runCatching { AppLaunch.launch(this, target, AppLaunch.modes[index]) }
                    .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                finish()
            }.setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
    }
}
