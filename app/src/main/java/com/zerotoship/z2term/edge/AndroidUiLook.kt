package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.zerotoship.z2term.automation.Refusal
import com.zerotoship.z2term.automation.UiBridgePolicy
import com.zerotoship.z2term.automation.UiBridgePolicy.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * `z2-ui` の「見る・押す・入れる」。呼ぶのは [com.zerotoship.z2term.automation.UiBridge] だけで、
 * 可否の判定はそちらで済ませてから来る ([gate] は読み取りの途中で前面が変わったときの再判定用)。
 *
 * [AndroidUiElements] (z2-action) との違い:
 *  - 入力欄は読む (中身・ヒント)。**パスワード欄は種類と場所だけ**で、中身には触れない。
 *  - 位置・種類・できる操作を返し、番号 (`#n`) で指せる。番号は直前の [look] の中だけで通じ、
 *    操作の直前に同じ要素かを照らし合わせる (違えば `stale` で押さない)。
 *  - 押す操作の再送や、座標への自動の置き換えはしない (z2-action と同じ)。
 */
@Suppress("DEPRECATION")
internal object AndroidUiLook {
    private const val MAX_NODES = 3000
    private const val MAX_ELEMENTS = 512
    private const val BUDGET_MS = 1500L

    class Window(val service: AndroidActions, val id: Int, val bounds: Rect, val pkg: String)

    class Element(val path: List<Int>, val role: String, val text: String?, val desc: String?, val id: String?,
        val hint: String?, val value: String?, val checked: Boolean?, val enabled: Boolean, val box: Rect,
        val can: List<String>) {
        /** 番号の取り違えを見分ける印。入力欄の中身は打てば変わるので含めない。 */
        fun same(other: Element) = role == other.role && text == other.text && desc == other.desc &&
            id == other.id && box == other.box

        fun json(n: Int): JSONObject = JSONObject().put("n", n).put("role", role).apply {
            text?.let { put("text", it) }
            desc?.let { put("desc", it) }
            id?.let { put("id", it) }
            hint?.let { put("hint", it) }
            value?.let { put("value", it) }
            checked?.let { put("checked", it) }
            if (!enabled) put("enabled", false)
            put("box", JSONArray(listOf(box.left, box.top, box.width(), box.height())))
            put("can", JSONArray(can))
        }

        fun fingerprint() = "$role|$text|$desc|$id|$value|$checked|$enabled|${box.flattenToString()}"
    }

    class Snapshot(val screen: String, val windowId: Int, val pkg: String, val elements: List<Element>) {
        val fingerprint = fingerprint(windowId, elements)
    }

    private val counter = AtomicInteger()
    @Volatile private var last: Snapshot? = null

    /** 前面の窓。読めなければ null。メインスレッドで窓の情報だけを見る。 */
    suspend fun window(): Window? {
        val fg = withContext(Dispatchers.Main) { AndroidActions.foreground() } ?: return null
        val pkg = fg.packageName ?: withContext(Dispatchers.IO) {
            runCatching { root(fg.service, fg.windowId)?.let { r -> r.packageName?.toString().also { r.recycle() } } }.getOrNull()
        } ?: return null
        return Window(fg.service, fg.windowId, fg.bounds, pkg)
    }

    private fun root(service: AccessibilityService, windowId: Int): AccessibilityNodeInfo? {
        val windows = service.windows
        return try { windows.firstOrNull { it.id == windowId }?.root } finally { windows.forEach { it.recycle() } }
    }

    private class Visit(val node: AccessibilityNodeInfo, val path: List<Int>, val tapFrom: Int?, val longFrom: Int?)

    private fun offers(node: AccessibilityNodeInfo, action: Int) = node.actionList.any { it.id == action }

    private fun next(node: AccessibilityNodeInfo, path: List<Int>, parent: Visit?): Visit {
        fun distance(action: Int, inherited: Int?) =
            if (offers(node, action)) 0 else inherited?.plus(1)?.takeIf { it <= 8 }
        return Visit(node, path, distance(AccessibilityNodeInfo.ACTION_CLICK, parent?.tapFrom),
            distance(AccessibilityNodeInfo.ACTION_LONG_CLICK, parent?.longFrom))
    }

    /** 前面の窓の木を幅優先でたどる。別のアプリの埋め込み内容とパスワード欄の中には入らない。 */
    private suspend fun collect(window: Window): List<Visit> = withContext(Dispatchers.IO) {
        val started = SystemClock.uptimeMillis()
        val root = root(window.service, window.id) ?: throw Refusal("no_window", "The foreground window has no readable content")
        val visits = mutableListOf(next(root, emptyList(), null))
        try {
            if (root.packageName?.toString() != window.pkg) throw Refusal("changed", "The foreground app changed; look again")
            var index = 0
            while (index < visits.size) {
                val visit = visits[index++]
                if (SystemClock.uptimeMillis() - started > BUDGET_MS) throw Refusal("too_large", "The screen took too long to read")
                val node = visit.node
                if (node.isPassword || node.packageName?.toString() != window.pkg) continue
                repeat(node.childCount) { i ->
                    if (visits.size >= MAX_NODES) throw Refusal("too_large", "The screen has more than $MAX_NODES nodes")
                    node.getChild(i)?.let { visits += next(it, visit.path + i, visit) }
                }
            }
            visits
        } catch (e: Throwable) { release(visits); throw e }
    }

    private fun release(visits: List<Visit>) = visits.forEach { it.node.recycle() }

    private fun element(visit: Visit, window: Window, all: Boolean): Element? {
        val node = visit.node
        if (!node.isVisibleToUser || node.packageName?.toString() != window.pkg) return null
        val box = Rect().also { node.getBoundsInScreen(it) }
        if (!box.intersect(window.bounds)) return null
        val password = node.isPassword
        val editable = node.isEditable && !password
        val text = if (password || editable) null else UiBridgePolicy.clean(node.text)
        val desc = UiBridgePolicy.clean(node.contentDescription)
        val id = node.viewIdResourceName?.takeIf { it.length <= 256 }
        val hint = if (editable) UiBridgePolicy.clean(node.hintText) else null
        // ヒントを表示しているとき text にはヒントが入っている。中身としては空。
        val value = if (editable) (if (node.isShowingHintText) "" else UiBridgePolicy.clean(node.text, 4096).orEmpty()) else null
        val can = buildList {
            if (visit.tapFrom != null) add("tap")
            if (visit.longFrom != null) add("long")
            if (editable) { add("type"); add("enter") }
            if (node.isScrollable) add("scroll")
        }
        // 意味の無い入れ物は出さない (AI に渡す量を減らす)。押せる要素は ID があれば残す。
        val meaningful = password || editable || node.isCheckable || node.isScrollable || text != null || desc != null ||
            (id != null && (visit.tapFrom == 0 || visit.longFrom == 0))
        if (!all && !meaningful) return null
        val role = UiBridgePolicy.role(node.className?.toString(), editable, password, node.isCheckable,
            visit.tapFrom == 0, node.isScrollable)
        return Element(visit.path, role, text, desc, id, hint, value, if (node.isCheckable) node.isChecked else null,
            node.isEnabled, box, can)
    }

    private suspend fun read(window: Window, all: Boolean): List<Element> {
        val visits = collect(window)
        try {
            val elements = visits.mapNotNull { element(it, window, all) }
            if (elements.size > MAX_ELEMENTS) throw Refusal("too_many", "The screen has more than $MAX_ELEMENTS elements")
            return elements
        } finally { release(visits) }
    }

    private fun fingerprint(windowId: Int, elements: List<Element>) =
        "$windowId\n" + elements.joinToString("\n") { it.fingerprint() }

    /** 前面の画面を読み、番号を振り直す。 */
    suspend fun look(all: Boolean, gate: (String) -> Unit): Snapshot {
        val window = window() ?: throw Refusal("no_window", "No foreground app window")
        gate(window.pkg)
        val elements = read(window, all)
        if (window()?.id != window.id) throw Refusal("changed", "The screen changed while reading; look again")
        return Snapshot("s${counter.incrementAndGet()}", window.id, window.pkg, elements).also { last = it }
    }

    /** 画面全体の印。操作の後に画面が変わったかを比べる。 */
    suspend fun fingerprint(): String? {
        val window = window() ?: return null
        return runCatching { fingerprint(window.id, read(window, false)) }.getOrNull()
    }

    fun forget() { last = null }

    /** 直前の look の要素。承認の通知に「何を押すか」を書くためだけに使う。 */
    fun lastElement(n: Int): Element? = last?.elements?.getOrNull(n - 1)

    /** 相手の要素と、その祖先から操作を引き受けるものを探す。 */
    private class Located(val node: AccessibilityNodeInfo, val element: Element, val retained: MutableList<AccessibilityNodeInfo>)

    private suspend fun locate(window: Window, target: Target): Located = when (target) {
        is Target.Ref -> withContext(Dispatchers.IO) {
            val snapshot = last ?: throw Refusal("no_look", "Run z2-ui look before using #N")
            if (snapshot.windowId != window.id || snapshot.pkg != window.pkg) throw Refusal("stale", "The screen changed; look again")
            val wanted = snapshot.elements.getOrNull(target.n - 1) ?: throw Refusal("stale", "No element #${target.n} in the last look")
            val chain = mutableListOf<AccessibilityNodeInfo>()
            try {
                var visit = next(root(window.service, window.id)?.also { chain += it }
                    ?: throw Refusal("no_window", "The foreground window has no readable content"), emptyList(), null)
                for (i in wanted.path) {
                    val child = visit.node.getChild(i)?.also { chain += it } ?: throw Refusal("stale", "The screen changed; look again")
                    visit = next(child, visit.path + i, visit)
                }
                val found = element(visit, window, all = true)
                if (found == null || !found.same(wanted)) throw Refusal("stale", "Element #${target.n} changed; look again")
                Located(visit.node, found, chain)
            } catch (e: Throwable) { chain.forEach { it.recycle() }; throw e }
        }
        is Target.Find -> {
            val visits = collect(window)
            val matches = visits.mapNotNull { visit ->
                element(visit, window, all = true)?.takeIf {
                    // 入力欄は中身ではなくヒントで探す (打った文字で見つかると、指定の意味が変わる)。
                    target.selector.matches(it.id, if (it.role == "input") it.hint else it.text, it.desc)
                }?.let { visit to it }
            }
            when (matches.size) {
                1 -> {
                    val (visit, found) = matches[0]
                    // 相手の要素だけを残し、ほかはここで返す。
                    visits.filter { it !== visit }.forEach { it.node.recycle() }
                    Located(visit.node, found, mutableListOf(visit.node))
                }
                0 -> { release(visits); throw Refusal("not_found", "No element matches ${target.selector}") }
                else -> { release(visits); throw Refusal("ambiguous", "${matches.size} elements match ${target.selector}; use #N from look") }
            }
        }
        is Target.Point -> throw Refusal("bad_args", "Coordinates are only for tap and long")
    }

    /** [op] (`tap` / `long` / `type` / `enter`) を 1 回だけ送る。 */
    suspend fun act(op: String, target: Target, text: String?, append: Boolean, gate: (String) -> Unit) {
        val window = window() ?: throw Refusal("no_window", "No foreground app window")
        gate(window.pkg)
        if (target is Target.Point) {
            if (op != "tap" && op != "long") throw Refusal("bad_args", "Coordinates are only for tap and long")
            if (!window.bounds.contains(target.x, target.y)) throw Refusal("outside", "The point is outside the foreground app window")
            if (AndroidActions.gesturesInFlight()) throw Refusal("busy", "Wait for the previous gesture to finish")
            gesture(window.service, target.x.toFloat(), target.y.toFloat(), if (op == "long") 600 else 80)
            return
        }
        val located = locate(window, target)
        try {
            val node = located.node
            when (op) {
                "tap", "long" -> {
                    val action = if (op == "tap") AccessibilityNodeInfo.ACTION_CLICK else AccessibilityNodeInfo.ACTION_LONG_CLICK
                    val recipient = recipient(node, action, window, located)
                    if (!recipient.isEnabled) throw Refusal("disabled_element", "The element is disabled")
                    perform(window, recipient, action, null)
                }
                "type" -> {
                    if (located.element.role == "password") throw Refusal("password", "z2-ui does not type into password fields")
                    if (located.element.role != "input") throw Refusal("not_input", "The element is not an input field")
                    val content = (if (append) located.element.value.orEmpty() else "") + text.orEmpty()
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, content)
                    }
                    if (!runCatching { perform(window, node, AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.isSuccess) {
                        // 入力欄によってはフォーカスを持つまで受け付けない。
                        perform(window, node, AccessibilityNodeInfo.ACTION_FOCUS, null)
                        perform(window, node, AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    }
                }
                "enter" -> {
                    if (located.element.role != "input") throw Refusal("not_input", "The element is not an input field")
                    // 確定キーはキーボードが付いている間だけ使える。先にフォーカスを移す。
                    if (!node.isFocused) runCatching { perform(window, node, AccessibilityNodeInfo.ACTION_FOCUS, null) }
                    perform(window, node, AccessibilityAction.ACTION_IME_ENTER.id, null)
                }
                else -> throw Refusal("bad_args", "Unknown operation: $op")
            }
        } finally { located.retained.forEach { it.recycle() } }
    }

    private suspend fun recipient(node: AccessibilityNodeInfo, action: Int, window: Window, located: Located): AccessibilityNodeInfo =
        withContext(Dispatchers.IO) {
            var current = node
            var depth = 0
            while (!offers(current, action)) {
                if (depth++ >= 8) throw Refusal("not_tappable", "No element within eight ancestors accepts this operation")
                current = current.parent ?: throw Refusal("not_tappable", "The element does not accept this operation")
                located.retained += current
                if (current.windowId != window.id || current.packageName?.toString() != window.pkg)
                    throw Refusal("not_tappable", "The operation would reach outside the foreground app")
            }
            current
        }

    private suspend fun perform(window: Window, node: AccessibilityNodeInfo, action: Int, args: Bundle?) =
        withContext(Dispatchers.Main) {
            if (AndroidActions.foreground()?.windowId != window.id) throw Refusal("changed", "The screen changed; look again")
            if (!node.performAction(action, args)) throw Refusal("rejected", "Android rejected the operation")
        }

    private suspend fun gesture(service: AccessibilityService, x: Float, y: Float, ms: Long) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val stroke = GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, 0, ms)
            val sent = service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(Unit) }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resumeWithException(Refusal("rejected", "Android cancelled the gesture"))
                    }
                }, null)
            if (!sent && cont.isActive) cont.resumeWithException(Refusal("rejected", "Android rejected the gesture"))
        }
    }

    /** 要素が出るまで待つ。前面が読めない・読んではいけない間は「まだ無い」として待ち続ける。 */
    suspend fun waitFor(selector: Target.Find, ms: Long, readable: (String) -> Boolean, alive: () -> Unit): Boolean {
        val deadline = SystemClock.uptimeMillis() + ms
        while (true) {
            alive()
            val window = window()
            if (window != null && readable(window.pkg)) {
                val found = runCatching { read(window, true) }.getOrNull()?.any {
                    selector.selector.matches(it.id, if (it.role == "input") it.hint else it.text, it.desc)
                } == true
                if (found) return true
            }
            if (SystemClock.uptimeMillis() >= deadline) return false
            delay(250)
        }
    }

    /** 直前の look (無ければ呼んだ時点) から画面が変わるまで待つ。 */
    suspend fun waitChange(ms: Long, alive: () -> Unit): Boolean {
        val base = last?.fingerprint ?: fingerprint()
        val deadline = SystemClock.uptimeMillis() + ms
        while (true) {
            alive()
            val now = fingerprint()
            if (now != null && now != base) return true
            if (SystemClock.uptimeMillis() >= deadline) return false
            delay(250)
        }
    }
}
