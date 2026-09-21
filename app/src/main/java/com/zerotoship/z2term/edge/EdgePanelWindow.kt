package com.zerotoship.z2term.edge

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
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
    var outside: () -> Unit = {}
    var contentAlignment: android.view.View.OnLayoutChangeListener? = null
    var swipeArea: View? = null
    var tabStrip: View? = null
    var horizontalTabSwipe = true
    var changeTab: (Boolean) -> Unit = {}
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeCandidate = false
    private var swiping = false
    private var swipeOnResult = false
    private var swipeHorizontal = true
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val swipeDistance = maxOf(touchSlop, 32 * resources.displayMetrics.density)

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Nested scroll views claim the stream at their smaller touch slop. Keep observing
        // until the direction is known. Results retain vertical scrolling but allow horizontal tabs.
        if (disallowIntercept && swipeCandidate) return
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.rawX
                swipeStartY = event.rawY
                swiping = false
                swipeOnResult = swipeArea?.let { resultAt(it, event) } == true
                swipeHorizontal = swipeOnResult || horizontalTabSwipe
                swipeCandidate = tabStrip?.let { contains(it, event) } != true &&
                    swipeArea?.let { contains(it, event) && !editingAt(it, event) } == true
            }
            MotionEvent.ACTION_POINTER_DOWN -> swipeCandidate = false
            MotionEvent.ACTION_MOVE -> if (swipeCandidate) {
                val dx = event.rawX - swipeStartX
                val dy = event.rawY - swipeStartY
                val along = kotlin.math.abs(if (swipeHorizontal) dx else dy)
                val across = kotlin.math.abs(if (swipeHorizontal) dy else dx)
                // Once the result starts a vertical scroll, do not steal a later sideways move.
                val crossDistance = if (swipeOnResult) touchSlop else swipeDistance
                if (event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout() ||
                    across > crossDistance && across >= along) swipeCandidate = false
                else if (along > swipeDistance && along > across * 1.5f) {
                    swiping = true
                    return true // Android cancels the child's tap before delivering the remaining gesture here.
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> swipeCandidate = false
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) { outside(); return true }
        if (!swiping) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) swipeCandidate = false
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            val delta = if (swipeHorizontal) event.rawX - swipeStartX else event.rawY - swipeStartY
            val navigate = event.actionMasked == MotionEvent.ACTION_UP && swipeCandidate &&
                kotlin.math.abs(delta) > swipeDistance
            swiping = false
            swipeCandidate = false
            if (navigate) changeTab(delta < 0)
        }
        return true
    }

    private fun contains(view: View, event: MotionEvent): Boolean {
        val bounds = android.graphics.Rect()
        if (!view.getGlobalVisibleRect(bounds)) return false
        val origin = IntArray(2)
        view.rootView.getLocationOnScreen(origin)
        bounds.offset(origin[0], origin[1])
        return bounds.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun editingAt(view: View, event: MotionEvent): Boolean {
        if (!contains(view, event)) return false
        if (view is android.widget.EditText || view is android.widget.SeekBar || view is android.widget.Spinner)
            return true
        if (view is ViewGroup) for (i in view.childCount - 1 downTo 0) {
            if (editingAt(view.getChildAt(i), event)) return true
        }
        return false
    }

    private fun resultAt(view: View, event: MotionEvent): Boolean {
        if (!contains(view, event)) return false
        // HTML pages use the same directional lock as text output: vertical reading, horizontal tabs.
        if (view is EdgeResultScrollView || view is android.webkit.WebView) return true
        if (view is ViewGroup) for (i in view.childCount - 1 downTo 0) {
            if (resultAt(view.getChildAt(i), event)) return true
        }
        return false
    }
    private var wasImeVisible = false
    private var backHidesIme = false
    private var backDispatcher: OnBackInvokedDispatcher? = null
    private val backCallback = if (Build.VERSION.SDK_INT >= 33)
        OnBackInvokedCallback { if (!hideKeyboard()) back() } else null

    init {
        isFocusableInTouchMode = true
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            // System bars are excluded by WindowManager; IME insets are relative to that frame.
            val visible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (wasImeVisible && !visible) releaseInputFocus()
            wasImeVisible = visible
            if (Build.VERSION.SDK_INT >= 30) {
                val bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                setPadding(0, 0, 0, bottom)
            }
            insets
        }
    }

    private fun imeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun releaseInputFocus() {
        if (findFocus() is android.widget.EditText) {
            findFocus()?.clearFocus()
            requestFocus()
        }
    }

    /** First dismiss typing; a later outside tap/back can close the panel. */
    fun finishInput(): Boolean {
        val editing = findFocus() is android.widget.EditText
        val hidden = hideKeyboard()
        releaseInputFocus()
        return editing || hidden
    }

    fun hideKeyboard(): Boolean {
        if (!imeVisible()) return false
        releaseInputFocus()
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
