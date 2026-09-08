package com.zerotoship.z2term.edge

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.zerotoship.z2term.R
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The panel entry writes a definition; the CLI entry returns the selected package. */
class AppPickerActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val expire = Runnable { finish() }
    private var selected = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = intent.getStringExtra("request")
        if (request != null && !pending.containsKey(request)) { finish(); return }
        val entries = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString() }
        AlertDialog.Builder(this).setTitle(R.string.edge_pick_app)
            .setItems(entries.map { "${it.loadLabel(packageManager)}\n${it.activityInfo.packageName}" }.toTypedArray()) { _, index ->
                val entry = entries[index]
                val pkg = entry.activityInfo.packageName
                runCatching {
                    val panel = intent.getStringExtra("panel")
                    if (panel != null) {
                        val store = EdgeRuntime.store(this)
                        val id = "app_" + UUID.randomUUID().toString().replace("-", "")
                        store.setItem("$panel:$id", mapOf("type" to "run", "run" to "z2-intent -p $pkg",
                            "label" to entry.loadLabel(packageManager).toString().replace('\n', ' ').replace('\r', ' '),
                            "icon" to "@app:$pkg"))
                        EdgeRuntime.reload(this)
                        if (store.enabled()) EdgeRuntime.open(panel)
                    }
                    request?.let { pending[it]?.complete(pkg) }
                    selected = true
                }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                finish()
            }.setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
        val deadline = intent.getLongExtra("deadline", 0L).takeIf { it > 0 }
            ?: (android.os.SystemClock.elapsedRealtime() + 120_000).also { intent.putExtra("deadline", it) }
        main.postDelayed(expire, (deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
    }

    override fun onDestroy() {
        main.removeCallbacks(expire)
        if (!isChangingConfigurations && !selected) intent.getStringExtra("request")?.let {
            pending[it]?.completeExceptionally(IllegalStateException("App selection cancelled"))
        }
        super.onDestroy()
    }

    companion object {
        private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()
        fun pick(context: Context): String {
            check(Looper.myLooper() != Looper.getMainLooper()) { "App selection must wait off the main thread" }
            val id = UUID.randomUUID().toString()
            val result = CompletableFuture<String>()
            pending[id] = result
            try {
                context.startActivity(Intent(context, AppPickerActivity::class.java).putExtra("request", id)
                    .putExtra("deadline", android.os.SystemClock.elapsedRealtime() + 120_000)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return result.get(120, TimeUnit.SECONDS)
            } finally { pending.remove(id) }
        }
    }
}
