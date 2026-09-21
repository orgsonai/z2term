package com.zerotoship.z2term.security

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.zerotoship.z2term.settings.AppSettings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.security.AppLock
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.ui.settings.PillButton

internal abstract class ToolActivity : ComponentActivity() {
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
            AppLock.State.LOCKED -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                PillButton(label = stringResource(R.string.lock_prompt_title), accent = true) {
                    AppLock.authenticate(this@ToolActivity, getString(R.string.lock_prompt_title),
                        getString(R.string.lock_prompt_subtitle)) {
                        if (it) AppLock.unlock()
                    }
                }
            }
        }
    }
}
