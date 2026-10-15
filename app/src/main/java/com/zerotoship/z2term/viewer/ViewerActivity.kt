package com.zerotoship.z2term.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.zerotoship.z2term.R
import com.zerotoship.z2term.security.AppLock
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.ui.theme.AppColors
import kotlinx.coroutines.launch

/** Standalone host for the same local reading surface used by edge panels. */
class ViewerActivity : ComponentActivity() {
    private var pane: ViewerPane? = null
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleHelper.applyLocale(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        CustomThemeStore.ensureLoaded(applicationContext)
        val key = intent.getStringExtra("page").orEmpty()
        val page = runCatching { ViewerStore.read(this, key) }.getOrNull()
        if (page == null) {
            Toast.makeText(this, R.string.viewer_missing, Toast.LENGTH_LONG).show(); finish(); return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(AppColors.bgPrimary.toArgb())
        }
        root.addView(TextView(this).apply {
            text = page.title; textSize = 15f; maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(AppColors.textSecondary.toArgb()); setBackgroundColor(AppColors.bgSecondary.toArgb())
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }, LinearLayout.LayoutParams(-1, -2))
        // A full screen for reading: Refresh and Find are offered unless `z2-view --no-refresh / --no-find`; panels keep Find opt-in.
        // Pages opened from a terminal are new each time, so this screen keeps one text size for all of them.
        // `z2-view --text-size` picks the size for this page only; a text pinch still updates the remembered one.
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val zoom = intent.getIntExtra(EXTRA_TEXT_ZOOM, 0).takeIf { it > 0 } ?: prefs.getInt(TEXT_ZOOM, ViewerOptions.TEXT_ZOOM)
        pane = ViewerPane(this, key, options = ViewerOptions(
                showRefresh = intent.getBooleanExtra(EXTRA_SHOW_REFRESH, true),
                showFind = intent.getBooleanExtra(EXTRA_SHOW_FIND, true),
                textZoom = zoom.coerceIn(ViewerOptions.MIN_TEXT_ZOOM, ViewerOptions.MAX_TEXT_ZOOM),
                pagePinch = intent.getBooleanExtra(EXTRA_PAGE_PINCH, false)),
            onTextZoom = { prefs.edit().putInt(TEXT_ZOOM, it).apply() }).also { root.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        ViewCompat.requestApplyInsets(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pane?.hasDraft != true) finish()
                else android.app.AlertDialog.Builder(this@ViewerActivity)
                    .setTitle(R.string.edge_discard_title).setMessage(R.string.edge_discard_message)
                    .setPositiveButton(R.string.edge_discard) { _, _ -> finish() }
                    .setNegativeButton(R.string.edge_keep_editing, null).show()
            }
        })
        lifecycleScope.launch {
            AppSettings(applicationContext).flow.collect { AppLock.applyPolicy(it.appLockEnabled, it.appLockGraceSec) }
        }
    }
    override fun onStart() { super.onStart(); AppLock.onEnterForeground() }
    override fun onStop() {
        if (!isChangingConfigurations) AppLock.onLeaveForeground()
        super.onStop()
    }
    override fun onDestroy() { pane?.dispose(); pane = null; super.onDestroy() }
    companion object {
        private const val PREFS = "viewer"
        private const val TEXT_ZOOM = "text-zoom"
        private const val EXTRA_PAGE_PINCH = "page-pinch"
        private const val EXTRA_TEXT_ZOOM = "text-size"
        private const val EXTRA_SHOW_REFRESH = "show-refresh"
        private const val EXTRA_SHOW_FIND = "show-find"
        /** textZoom 0 = the remembered size. */
        internal fun open(context: Context, key: String, pagePinch: Boolean = false, textZoom: Int = 0,
                          showRefresh: Boolean = true, showFind: Boolean = true) {
            context.startActivity(Intent(context, ViewerActivity::class.java).putExtra("page", key)
                .putExtra(EXTRA_PAGE_PINCH, pagePinch).putExtra(EXTRA_TEXT_ZOOM, textZoom)
                .putExtra(EXTRA_SHOW_REFRESH, showRefresh).putExtra(EXTRA_SHOW_FIND, showFind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }
}
