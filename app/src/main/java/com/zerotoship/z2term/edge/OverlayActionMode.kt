package com.zerotoship.z2term.edge

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.core.view.isEmpty
import androidx.core.view.size
import androidx.core.view.get

/**
 * The text selection toolbar (cut / copy / paste / select all) for a window added directly
 * through WindowManager. Such a window has no DecorView, so the platform returns no action
 * mode and a selection shows its handles without any commands. The host draws the commands
 * itself, above the selection, while the text view's own callback performs each one.
 */
internal class OverlayActionMode(
    private val host: FrameLayout,
    private val origin: View,
    private val callback: ActionMode.Callback,
) : ActionMode() {
    // PopupMenu is the public way to obtain the platform Menu the callback expects to fill.
    private val items: Menu = PopupMenu(host.context, origin).menu
    private val bar = LinearLayout(host.context)
    private val toolbar = HorizontalScrollView(host.context).apply {
        isHorizontalScrollBarEnabled = false
        isClickable = true // A tap between commands must not reach the panel behind.
        background = EdgeSettingsUi.frame(host.context)
        elevation = EdgeSettingsUi.dp(host.context, 2).toFloat()
        addView(bar)
    }
    private var title: CharSequence? = null
    private var subtitle: CharSequence? = null
    private var custom: View? = null
    private var finished = false
    private val reveal = Runnable { toolbar.visibility = View.VISIBLE; place() }
    private val follow = ViewTreeObserver.OnPreDrawListener { place(); true }

    fun start(): Boolean {
        if (!callback.onCreateActionMode(this, items)) return false
        type = TYPE_FLOATING
        callback.onPrepareActionMode(this, items)
        fill()
        host.addView(toolbar, FrameLayout.LayoutParams(-2, -2, ScreenGravity.TOP_LEFT))
        host.viewTreeObserver.addOnPreDrawListener(follow)
        return true
    }

    private fun fill() {
        bar.removeAllViews()
        for (i in 0 until items.size) {
            val item = items[i]
            if (!item.isVisible) continue
            val label = item.title?.toString()?.takeIf { it.isNotBlank() } ?: continue
            bar.addView(EdgeSettingsUi.button(host.context, label, EdgeSettingsUi.Kind.QUIET) {
                if (!finished) callback.onActionItemClicked(this, item)
            }.apply { isFocusable = false; isEnabled = item.isEnabled })
        }
        toolbar.visibility = if (bar.isEmpty()) View.GONE else toolbar.visibility
    }

    /** Above the selection when it fits, otherwise below it; always inside the window. */
    private fun place() {
        if (finished || !origin.isAttachedToWindow || toolbar.width == 0) return
        val content = Rect()
        if (callback is Callback2) callback.onGetContentRect(this, origin, content)
        else content.set(0, 0, origin.width, origin.height)
        val at = IntArray(2).also { origin.getLocationInWindow(it) }
        val base = IntArray(2).also { host.getLocationInWindow(it) }
        content.offset(at[0] - base[0], at[1] - base[1])
        val gap = EdgeSettingsUi.dp(host.context, 8)
        val limit = host.height - host.paddingBottom
        val x = (content.centerX() - toolbar.width / 2).coerceIn(0, (host.width - toolbar.width).coerceAtLeast(0))
        val y = (content.top - gap - toolbar.height).takeIf { it >= 0 }
            ?: (content.bottom + gap).coerceAtMost((limit - toolbar.height).coerceAtLeast(0))
        if (toolbar.translationX != x.toFloat()) toolbar.translationX = x.toFloat()
        if (toolbar.translationY != y.toFloat()) toolbar.translationY = y.toFloat()
    }

    override fun invalidate() {
        if (finished) return
        callback.onPrepareActionMode(this, items)
        fill()
    }

    override fun invalidateContentRect() = place()

    override fun hide(duration: Long) {
        toolbar.removeCallbacks(reveal)
        if (duration == 0L) { reveal.run(); return }
        toolbar.visibility = View.INVISIBLE
        val wait = if (duration == DEFAULT_HIDE_DURATION.toLong()) ViewConfiguration.getDefaultActionModeHideDuration()
            else duration.coerceAtMost(3000L)
        toolbar.postDelayed(reveal, wait)
    }

    override fun finish() {
        if (finished) return
        finished = true
        toolbar.removeCallbacks(reveal)
        host.viewTreeObserver.removeOnPreDrawListener(follow)
        host.removeView(toolbar)
        callback.onDestroyActionMode(this)
    }

    override fun setTitle(title: CharSequence?) { this.title = title }
    override fun setTitle(resId: Int) { title = host.context.getString(resId) }
    override fun setSubtitle(subtitle: CharSequence?) { this.subtitle = subtitle }
    override fun setSubtitle(resId: Int) { subtitle = host.context.getString(resId) }
    override fun setCustomView(view: View?) { custom = view }
    override fun getMenu(): Menu = items
    override fun getTitle(): CharSequence? = title
    override fun getSubtitle(): CharSequence? = subtitle
    override fun getCustomView(): View? = custom
    override fun getMenuInflater(): MenuInflater = MenuInflater(host.context)
}
