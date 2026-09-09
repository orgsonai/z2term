package com.zerotoship.z2term.edge

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Keeps the overlay attached across page changes and handles back before dismissing it. */
internal class EdgePanelWindow(context: Context) : FrameLayout(context) {
    var back: () -> Unit = {}
    var contentAlignment: android.view.View.OnLayoutChangeListener? = null
    private var backHidesIme = false
    private var backDispatcher: OnBackInvokedDispatcher? = null
    private val backCallback = if (Build.VERSION.SDK_INT >= 33)
        OnBackInvokedCallback { if (!hideKeyboard()) back() } else null

    init {
        isFocusableInTouchMode = true
        if (Build.VERSION.SDK_INT >= 30) setOnApplyWindowInsetsListener { _, insets ->
            // System bars are excluded by WindowManager; IME insets are relative to that frame.
            setPadding(0, 0, 0, insets.getInsets(WindowInsets.Type.ime()).bottom)
            insets
        }
    }

    private fun imeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    fun hideKeyboard(): Boolean {
        if (!imeVisible()) return false
        if (Build.VERSION.SDK_INT >= 30) windowInsetsController?.hide(WindowInsets.Type.ime())
        else context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
        return true
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) backHidesIme = imeVisible()
            if (backHidesIme) {
                if (event.action == KeyEvent.ACTION_UP) {
                    backHidesIme = false
                    if (!event.isCanceled) hideKeyboard()
                }
                return true
            }
        }
        return super.dispatchKeyEventPreIme(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) backHidesIme = imeVisible()
        if (event.action == KeyEvent.ACTION_UP) {
            if (!event.isCanceled) {
                if (backHidesIme) hideKeyboard() else if (!hideKeyboard()) back()
            }
            backHidesIme = false
        }
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= 33) {
            backDispatcher = findOnBackInvokedDispatcher()
            // The IME retains its higher priority for gesture navigation.
            backDispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback!!)
        }
        requestApplyInsets()
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 33) backDispatcher?.unregisterOnBackInvokedCallback(backCallback!!)
        backDispatcher = null
        super.onDetachedFromWindow()
    }
}
