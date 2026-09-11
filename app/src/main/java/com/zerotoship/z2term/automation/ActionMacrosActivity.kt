package com.zerotoship.z2term.automation

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.zerotoship.z2term.security.AppLock
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.LocaleHelper
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Standalone entry point; the tools tab embeds the same manager directly. */
class ActionMacrosActivity : ComponentActivity() {
    private lateinit var editor: ActionMacrosEditor

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleHelper.applyLocale(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        editor = ActionMacrosEditor(this, savedInstanceState, onClose = { finish() })
        setContentView(editor.view)
        ViewCompat.setOnApplyWindowInsetsListener(editor.view) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        ViewCompat.requestApplyInsets(editor.view)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { editor.leave() }
        })
        lifecycleScope.launch {
            AppSettings(applicationContext).flow.collect { AppLock.applyPolicy(it.appLockEnabled, it.appLockGraceSec) }
        }
        editor.start()
    }

    override fun onStart() { super.onStart(); AppLock.onEnterForeground() }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLock.onLeaveForeground()
    }
    override fun onResume() {
        super.onResume()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    override fun onPause() {
        if (AppLock.isEnabledNow()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putAll(editor.saveState())
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { editor.dispose(); super.onDestroy() }
}
