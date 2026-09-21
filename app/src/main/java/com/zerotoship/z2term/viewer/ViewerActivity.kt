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
        pane = ViewerPane(this, key).also { root.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }
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
        internal fun open(context: Context, key: String) {
            context.startActivity(Intent(context, ViewerActivity::class.java).putExtra("page", key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }
}
