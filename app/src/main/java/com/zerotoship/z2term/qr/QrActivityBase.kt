package com.zerotoship.z2term.qr

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.zerotoship.z2term.settings.AppSettings
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.zerotoship.z2term.R
import com.zerotoship.z2term.security.AppLock
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper

internal abstract class QrActivityBase : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleHelper.applyLocale(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CustomThemeStore.ensureLoaded(applicationContext)
        lifecycleScope.launch {
            AppSettings(applicationContext).flow.collect { AppLock.applyPolicy(it.appLockEnabled, it.appLockGraceSec) }
        }
    }
    override fun onStart() { super.onStart(); AppLock.onEnterForeground() }
    override fun onStop() {
        if (!isChangingConfigurations) AppLock.onLeaveForeground()
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    override fun onPause() {
        if (AppLock.isEnabledNow()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onPause()
    }

    @Composable protected fun Unlocked(content: @Composable () -> Unit) {
        val state by AppLock.state.collectAsState()
        when (state) {
            AppLock.State.UNKNOWN -> Unit
            AppLock.State.UNLOCKED -> content()
            AppLock.State.LOCKED -> TextButton(onClick = {
                AppLock.authenticate(this, getString(R.string.lock_prompt_title), getString(R.string.lock_prompt_subtitle)) {
                    if (it) AppLock.unlock()
                }
            }) { Text(stringResource(R.string.lock_prompt_title)) }
        }
    }
}
