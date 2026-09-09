package com.zerotoship.z2term.core

import android.graphics.Rect
import android.view.View
import java.lang.ref.WeakReference

/** Main-thread geometry only. The built-in keyboard is inside the app window, but outside this area. */
internal object TerminalScrollViewport {
    private data class Entry(val owner: Any, val sessionId: String, val view: WeakReference<View>, val bounds: Rect)
    private var entry: Entry? = null

    fun update(owner: Any, sessionId: String, view: View, bounds: Rect) {
        entry = Entry(owner, sessionId, WeakReference(view), Rect(bounds))
    }

    fun remove(owner: Any) {
        if (entry?.owner === owner) entry = null
    }

    fun current(): Rect? {
        val current = entry ?: return null
        val view = current.view.get() ?: return null
        if (!view.isShown || !view.hasWindowFocus() || SessionManager.active()?.id != current.sessionId) return null
        return Rect(current.bounds)
    }
}
