package com.zerotoship.z2term.core

import android.graphics.Rect
import android.view.View
import java.lang.ref.WeakReference

/** Main-thread geometry and window identity. The built-in keyboard is outside this area. */
internal object TerminalScrollViewport {
    private data class Entry(val owner: Any, val sessionId: String, val view: WeakReference<View>, val bounds: Rect)
    private var entry: Entry? = null

    fun update(owner: Any, sessionId: String, view: View, bounds: Rect) {
        entry = Entry(owner, sessionId, WeakReference(view), Rect(bounds))
    }

    fun remove(owner: Any) {
        if (entry?.owner === owner) entry = null
    }

    fun current(windowId: Int): Rect? {
        val current = entry ?: return null
        val view = current.view.get() ?: return null
        if (!view.isShown || !view.hasWindowFocus() || SessionManager.active()?.id != current.sessionId) return null
        // View's accessibility window-ID getter is hidden; use the public node metadata instead.
        val node = view.createAccessibilityNodeInfo() ?: return null
        @Suppress("DEPRECATION")
        val sameWindow = try { node.windowId == windowId } finally { node.recycle() }
        if (!sameWindow) return null
        return Rect(current.bounds)
    }
}
