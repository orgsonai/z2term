package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction as ScrollAction
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.terminal.input.TerminalInputView
import kotlin.math.abs

/** Ask a vertical scrollable view directly, without injecting touches or paging horizontally. */
@Suppress("DEPRECATION")
internal class AndroidNodeScroll(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    var running = false
        private set
    private var next: Runnable? = null
    private var finished: ((String?) -> Unit)? = null
    private var stoppedAt: Long? = null
    private var singleShot = false
    private var node: AccessibilityNodeInfo? = null
    private var nodeWindow = -1
    private val visibleArea = Rect()
    private val selectedBounds = Rect()
    private var pointX = 0
    private var pointY = 0
    private var speed = 0f
    private var granular = false
    private var sentAt = 0L
    private var observedDistancePx = 0f
    private var previousScrollY: Int? = null

    fun recentlyRunning(): Boolean = running || stoppedAt?.let {
        SystemClock.uptimeMillis() - it < 500
    } == true

    fun stop(error: String? = null, completed: Boolean = false) {
        if (running) stoppedAt = SystemClock.uptimeMillis()
        generation++
        running = false
        next?.let(main::removeCallbacks)
        next = null
        node?.recycle()
        node = null
        val callback = finished
        finished = null
        val result = error ?: if (singleShot && !completed) service.getString(R.string.edge_scroll_failed) else null
        singleShot = false
        callback?.invoke(result)
    }

    fun outsideTouch(event: android.view.MotionEvent) {
        val synthetic = event.deviceId == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD &&
            event.getToolType(0) == android.view.MotionEvent.TOOL_TYPE_UNKNOWN
        if (!synthetic) stop()
    }

    /** Keep the actual distance of page-sized actions, including views that scroll less than a page. */
    fun onScrolled(event: AccessibilityEvent) {
        if (!running || granular || singleShot || event.windowId != nodeWindow) return
        val source = event.source ?: return
        val matches = try { source == node } finally { source.recycle() }
        if (!matches) return
        val delta = if (event.scrollDeltaY != -1) abs(event.scrollDeltaY.toFloat())
            else previousScrollY?.let { abs(event.scrollY.toFloat() - it) } ?: 0f
        previousScrollY = event.scrollY.takeIf { it >= 0 }
        if (delta <= 0) return
        observedDistancePx += delta
        val period = EdgeNodeScrollPolicy.periodMs(speed,
            observedDistancePx / service.resources.displayMetrics.density, false)
        // Do not start another page while the preceding animation is still reporting movement.
        val deadline = maxOf(sentAt + period, SystemClock.uptimeMillis() + 100L)
        next?.let { main.removeCallbacks(it); main.postAtTime(it, deadline) }
    }

    /** False means no suitable vertical view; auto may then use its explicit swipe fallback. */
    fun start(speedDp: Float, windowId: Int, area: Rect, xPercent: Float, yPercent: Float,
        once: Boolean, stillTarget: () -> Boolean, done: (String?) -> Unit, terminalOnly: Boolean = false): Boolean {
        require(speedDp.isFinite() && speedDp != 0f && !area.isEmpty)
        stop()
        speed = speedDp
        visibleArea.set(area)
        pointX = (area.left + area.width() * xPercent.coerceIn(0f, 100f) / 100f).toInt()
        pointY = (area.top + area.height() * yPercent.coerceIn(0f, 100f) / 100f).toInt()
        val found = find(windowId) ?: return false
        // General scroll actions may start their own fixed-duration animations, even with an
        // amount flag. The terminal implements immediate fractional movement under our control.
        if (terminalOnly && (!supportsAmount(found) || found.className?.toString() != TerminalInputView::class.java.name)) {
            found.recycle(); return false
        }
        node = found
        nodeWindow = windowId
        found.getBoundsInScreen(selectedBounds)
        running = true
        singleShot = once
        stoppedAt = null
        finished = done
        sentAt = 0L
        previousScrollY = null
        val token = generation
        val density = service.resources.displayMetrics.density
        val tick = object : Runnable {
            override fun run() {
                if (!running || token != generation) return
                if (!runCatching(stillTarget).getOrDefault(false)) { stop(); return }
                var target = node ?: return
                if (!runCatching { target.refresh() }.getOrDefault(false)) {
                    // Preserve the point and region; never retarget a stale list to another pane/pager.
                    val again = find(nodeWindow, selectedBounds) ?: run { stop(completed = true); return }
                    target.recycle(); node = again; target = again
                }
                val action = action(target) ?: run { stop(completed = true); return }
                val box = Rect().also { target.getBoundsInScreen(it) }
                if (!box.intersect(visibleArea) || box.isEmpty) { stop(); return }
                granular = supportsAmount(target)
                val heightDp = box.height() / density
                val period = EdgeNodeScrollPolicy.periodMs(speed, heightDp, granular)
                val now = SystemClock.uptimeMillis()
                val elapsed = if (sentAt == 0L) period else (now - sentAt).coerceIn(1L, 200L)
                val fraction = if (once) 0.25f else (abs(speed) * elapsed / 1000f / heightDp).coerceAtMost(1f)
                val arguments = if (granular) Bundle().apply {
                    putFloat(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, fraction)
                    if (Build.VERSION.SDK_INT >= 35)
                        putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, fraction)
                    putInt(TerminalInputView.ACCESSIBILITY_SCROLL_X, pointX)
                    putInt(TerminalInputView.ACCESSIBILITY_SCROLL_Y, pointY)
                } else null
                sentAt = now
                observedDistancePx = 0f
                val accepted = runCatching { target.performAction(action, arguments) }.getOrDefault(false)
                if (!accepted) { stop(); return }
                if (once) { stop(completed = true); return }
                next?.let { main.postAtTime(it, maxOf(now + period, SystemClock.uptimeMillis() + 1L)) }
            }
        }
        next = tick
        main.post(tick)
        return true
    }

    private fun supportsAmount(target: AccessibilityNodeInfo): Boolean =
        (Build.VERSION.SDK_INT >= 35 && target.isGranularScrollingSupported) ||
            AccessibilityNodeInfoCompat.wrap(target).isGranularScrollingSupported

    private fun action(target: AccessibilityNodeInfo, speedDp: Float = speed): Int? {
        val ids = target.actionList.map { it.id }.toSet()
        val actions = EdgeNodeScrollPolicy.Actions(ScrollAction.ACTION_SCROLL_UP.id in ids, ScrollAction.ACTION_SCROLL_DOWN.id in ids,
            ScrollAction.ACTION_SCROLL_LEFT.id in ids, ScrollAction.ACTION_SCROLL_RIGHT.id in ids,
            listOf(ScrollAction.ACTION_PAGE_UP, ScrollAction.ACTION_PAGE_DOWN,
                ScrollAction.ACTION_PAGE_LEFT, ScrollAction.ACTION_PAGE_RIGHT).any { it.id in ids },
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD in ids, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in ids)
        val collection = target.collectionInfo
        return when (EdgeNodeScrollPolicy.action(actions, target.className?.toString().orEmpty(),
            collection?.rowCount, collection?.columnCount, speedDp)) {
            EdgeNodeScrollPolicy.Action.UP -> ScrollAction.ACTION_SCROLL_UP.id
            EdgeNodeScrollPolicy.Action.DOWN -> ScrollAction.ACTION_SCROLL_DOWN.id
            EdgeNodeScrollPolicy.Action.BACKWARD -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            EdgeNodeScrollPolicy.Action.FORWARD -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            null -> null
        }
    }

    private fun find(windowId: Int, previousBounds: Rect? = null): AccessibilityNodeInfo? {
        val windows = service.windows
        val root = try { windows.firstOrNull { it.id == windowId }?.root }
            catch (_: Exception) { null } finally { windows.forEach { it.recycle() } }
        if (root == null) return null
        val queue = arrayListOf(root)
        var inner: AccessibilityNodeInfo? = null
        var innerArea = Long.MAX_VALUE
        var widest: AccessibilityNodeInfo? = null
        var widestArea = 0L
        var index = 0
        val started = SystemClock.uptimeMillis()
        try {
            while (index < queue.size && queue.size <= 1024 && SystemClock.uptimeMillis() - started <= 400) {
                val current = queue[index++]
                if (current.isVisibleToUser && current.isEnabled &&
                    (action(current) != null || action(current, -speed) != null)) {
                    val box = Rect().also { current.getBoundsInScreen(it) }
                    if ((previousBounds == null || box == previousBounds) && box.intersect(visibleArea)) {
                        val size = box.width().toLong() * box.height()
                        if (size > 0) {
                            if (box.contains(pointX, pointY) && size <= innerArea) { inner = current; innerArea = size }
                            if (size > widestArea) { widest = current; widestArea = size }
                        }
                    }
                }
                repeat(current.childCount) { child ->
                    if (queue.size < 1024) current.getChild(child)?.let { queue += it }
                }
            }
        } catch (_: Exception) { /* The tree may be rebuilt during traversal. */ }
        val chosen = inner ?: widest
        queue.forEach { if (it !== chosen) it.recycle() }
        return chosen
    }
}
