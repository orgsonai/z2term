package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.zerotoship.z2term.automation.ActionDefinition
import com.zerotoship.z2term.automation.ActionSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/** Explicit macro/inspection requests only. Nothing is read from accessibility event sources. */
@Suppress("DEPRECATION")
internal object AndroidUiElements {
    private val access = Mutex()
    private suspend fun <T> readTree(block: suspend () -> T): T = access.withLock {
        withContext(Dispatchers.IO) { block() }
    }
    class Changed(message: String) : IllegalStateException(message)
    data class Window(val id: Int, val bounds: Rect)
    data class Entry(val id: String?, val text: String?, val description: String?) {
        fun selectors(): List<ActionSelector> = listOfNotNull(
            id?.let { runCatching { ActionSelector.parse("id=$it") }.getOrNull() },
            text?.let { runCatching { ActionSelector.parse("text=$it") }.getOrNull() },
            description?.let { runCatching { ActionSelector.parse("desc=$it") }.getOrNull() })
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also { node.getBoundsInScreen(it) }
    private fun eligible(node: AccessibilityNodeInfo, target: String, window: Window): Boolean =
        node.windowId == window.id && node.packageName?.toString() == target &&
            !node.isPassword && !node.isEditable && node.isVisibleToUser && Rect.intersects(bounds(node), window.bounds)
    private fun matches(node: AccessibilityNodeInfo, selector: ActionSelector): Boolean = selector.matches(
        if (selector.key == "id") node.viewIdResourceName else null,
        if (selector.key == "text") node.text?.toString() else null,
        if (selector.key == "desc") node.contentDescription?.toString() else null)
    private suspend fun nodes(service: AccessibilityService, target: String, window: Window): List<AccessibilityNodeInfo> {
        val started = SystemClock.uptimeMillis()
        val retained = mutableListOf<AccessibilityNodeInfo>()
        try {
            val windows = service.windows
            val root = try {
                windows.firstOrNull { it.id == window.id && it.isFocused &&
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION }?.root
            } finally { windows.forEach { it.recycle() } }
            if (root == null) return emptyList()
            retained += root
            check(root.packageName?.toString() == target) { "Target window changed" }
            var index = 0
            while (index < retained.size) {
                coroutineContext.ensureActive()
                check(SystemClock.uptimeMillis() - started <= 1500) { "UI tree search exceeded its time budget" }
                val node = retained[index++]
                // Do not inspect editable/password subtrees or embedded content owned by another app.
                if (node.isPassword || node.isEditable || node.packageName?.toString() != target) continue
                val children = node.childCount
                check(children <= 1024 - retained.size) { "UI tree exceeds 1024 nodes" }
                repeat(children) { child -> retained += (node.getChild(child) ?: throw Changed("UI tree changed during lookup")) }
            }
            return retained
        } catch (e: Throwable) { retained.forEach { it.recycle() }; throw e }
    }
    suspend fun query(service: AccessibilityService, step: ActionDefinition.Step.Ui, guard: () -> Window): Boolean =
        readTree {
            val window = withContext(Dispatchers.Main) { guard() }
            val retained = nodes(service, step.target, window)
            val parents = mutableListOf<AccessibilityNodeInfo>()
            try {
                val found = retained.filter { eligible(it, step.target, window) && matches(it, step.selector) }
                check(found.size <= 1) { "More than one UI element matches; use a unique text, description or resource ID" }
                val selected = found.singleOrNull() ?: return@readTree false
                if (!selected.refresh() || !eligible(selected, step.target, window) || !matches(selected, step.selector))
                    throw Changed("UI element changed during lookup")
                if (step.operation == "wait-ui") {
                    withContext(Dispatchers.Main) { if (guard().id != window.id) throw Changed("Target window changed") }
                    return@readTree true
                }
                val action = if (step.operation == "click") AccessibilityNodeInfo.ACTION_CLICK
                    else AccessibilityNodeInfo.ACTION_LONG_CLICK
                var recipient = selected
                var depth = 0
                while (recipient.actionList.none { it.id == action }) {
                    check(depth++ < 8) { "No actionable UI element within eight ancestors" }
                    recipient = (recipient.parent ?: error("UI element does not offer the requested action")).also { parents += it }
                    check(eligible(recipient, step.target, window)) { "UI action ancestor is outside the target window" }
                }
                check(recipient.refresh() && eligible(recipient, step.target, window) && recipient.isEnabled &&
                    recipient.actionList.any { it.id == action }) { "UI action is no longer available" }
                check(matches(selected, step.selector)) { "UI element changed before the action" }
                coroutineContext.ensureActive()
                withContext(Dispatchers.Main) {
                    coroutineContext.ensureActive()
                    if (guard().id != window.id) throw Changed("Target window changed")
                    check(recipient.performAction(action)) { "Android rejected the UI action" }
                    true
                }
            } finally {
                parents.forEach { it.recycle() }
                retained.forEach { it.recycle() }
            }
        }
    suspend fun inspect(service: AccessibilityService, target: String, guard: () -> Window): List<Entry> =
        readTree {
            val window = withContext(Dispatchers.Main) { guard() }
            val retained = nodes(service, target, window)
            try {
                fun value(raw: CharSequence?): String? = raw?.toString()?.takeIf {
                    it.isNotBlank() && it.length <= 256 && it.none { character -> character.isISOControl() }
                }
                val entries = retained.filter { eligible(it, target, window) }.map {
                    Entry(value(it.viewIdResourceName), value(it.text), value(it.contentDescription))
                }.filter { it.selectors().isNotEmpty() }.distinct()
                check(entries.size <= 256) { "UI inspection exceeds 256 entries" }
                withContext(Dispatchers.Main) { if (guard().id != window.id) throw Changed("Target window changed") }
                entries
            } finally { retained.forEach { it.recycle() } }
        }
}
