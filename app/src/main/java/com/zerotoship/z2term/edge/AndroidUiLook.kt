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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    /** z2-ui が続けて撮るときの間隔。Android の撮影間隔の下限 (333 ミリ秒) に少し足したもの。 */
    private const val SHOT_INTERVAL_MS = 340L
    /** 「前と同じ画像か」を比べる縮小の幅。動きの判定 ([SETTLE_WIDTH]) より細かくし、小さな文字の変化も拾う。 */
    private const val PRINT_WIDTH = 160
    /** 「動きが止まったか」を比べる縮小の幅。 */
    private const val SETTLE_WIDTH = 48
    /** `expectChange` で、前回の画像から変わるのを待つ最大の時間。何も変わらない操作で待たされすぎないように。 */
    const val CHANGE_MAX_MS = 1500L

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

    /**
     * 指を置いたまま撮る (`--peek MS`)。指を置いてから [atMs] 後に [take] を 1 度呼び、返事を [result] に残す。
     * 長押しやなぞる途中にだけ出る表示 (残り時間など) は、指を離してから撮ると消えている。
     * [held] は撮り終えた時点で指がまだ画面にあったか (false なら、写っているのは離したあとかもしれない)。
     */
    class Peek(val atMs: Long, val take: suspend () -> JSONObject) {
        @Volatile var result: JSONObject? = null
        @Volatile var held = false
    }

    private val counter = AtomicInteger()
    /** 撮影の結果を受け取るスレッド。撮るたびに作ると残り続けるので 1 本を使い回す。 */
    private val shotExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "z2-ui-shot").apply { isDaemon = true } }
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

    private suspend fun read(window: Window, all: Boolean, within: List<Int>? = null): List<Element> {
        val visits = collect(window)
        try {
            // 範囲を絞るときは、上限を数える前に絞る (広い画面でも一部なら読めるように)。
            val inside = if (within == null) visits else visits.filter { it.path.size >= within.size && it.path.take(within.size) == within }
            val elements = inside.mapNotNull { element(it, window, all) }
            if (elements.size > MAX_ELEMENTS) throw Refusal("too_many", "The screen has more than $MAX_ELEMENTS elements")
            return elements
        } finally { release(visits) }
    }

    private fun fingerprint(windowId: Int, elements: List<Element>) =
        "$windowId\n" + elements.joinToString("\n") { it.fingerprint() }

    /** 前面の画面を読み、番号を振り直す。 */
    suspend fun look(all: Boolean, within: Int? = null, gate: (String) -> Unit): Snapshot {
        val window = window() ?: throw Refusal("no_window", "No foreground app window")
        gate(window.pkg)
        // `--in N` は直前の look の要素 N の中だけ。窓が変わっていたら道筋は通じない。
        val prefix = within?.let { n ->
            val snapshot = last ?: throw Refusal("no_look", "Run z2-ui look before using --in")
            if (snapshot.windowId != window.id || snapshot.pkg != window.pkg) throw Refusal("stale", "The screen changed; look again")
            snapshot.elements.getOrNull(n - 1)?.path ?: throw Refusal("stale", "No element #$n in the last look")
        }
        val elements = read(window, all, prefix)
        if (window()?.id != window.id) throw Refusal("changed", "The screen changed while reading; look again")
        return Snapshot("s${counter.incrementAndGet()}", window.id, window.pkg, elements).also { last = it }
    }

    /** 画面全体の印。操作の後に画面が変わったかを比べる。 */
    suspend fun fingerprint(): String? {
        val window = window() ?: return null
        return runCatching { fingerprint(window.id, read(window, false)) }.getOrNull()
    }

    fun forget() { last = null }

    /**
     * 要素を親子関係つきで並べ直す (`look --tree`)。番号 `n` は平らな一覧と同じなので、`#n` はそのまま使える。
     * 間の入れ物を省いた要素は、出している一番近い祖先の子として並べる。
     */
    fun tree(snapshot: Snapshot): JSONArray {
        val roots = JSONArray()
        val nodes = snapshot.elements.mapIndexed { i, element -> element.json(i + 1) }
        UiBridgePolicy.parents(snapshot.elements.map { it.path }).forEachIndexed { i, parent ->
            if (parent < 0) roots.put(nodes[i])
            else (nodes[parent].optJSONArray("children") ?: JSONArray().also { nodes[parent].put("children", it) }).put(nodes[i])
        }
        return roots
    }

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
    suspend fun act(op: String, target: Target, text: String?, append: Boolean, gate: (String) -> Unit,
        holdMs: Long = 600, peek: Peek? = null) {
        val window = window() ?: throw Refusal("no_window", "No foreground app window")
        gate(window.pkg)
        if (target is Target.Point) {
            if (op != "tap" && op != "long") throw Refusal("bad_args", "Coordinates are only for tap and long")
            if (!window.bounds.contains(target.x, target.y)) throw Refusal("outside", "The point is outside the foreground app window")
            if (AndroidActions.gesturesInFlight()) throw Refusal("busy", "Wait for the previous gesture to finish")
            gesture(window.service, Path().apply { moveTo(target.x.toFloat(), target.y.toFloat()) }, if (op == "long") holdMs else 80, peek)
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
                "scroll" -> {
                    val (recipient, action) = scroller(node, text.orEmpty(), window, located)
                    perform(window, recipient, action, null)
                }
                "enter" -> {
                    if (located.element.role != "input") throw Refusal("not_input", "The element is not an input field")
                    // 確定キーの操作は Android 11 から。10 では無い操作を呼ぶと落ちる。
                    if (android.os.Build.VERSION.SDK_INT < 30) throw Refusal("unsupported", "enter requires Android 11")
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

    /** 座標でスワイプする。始点・終点とも前面のアプリの窓の中だけ。 */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long, gate: (String) -> Unit, peek: Peek? = null) {
        val window = window() ?: throw Refusal("no_window", "No foreground app window")
        gate(window.pkg)
        if (!window.bounds.contains(x1, y1) || !window.bounds.contains(x2, y2))
            throw Refusal("outside", "Both points must be inside the foreground app window")
        if (AndroidActions.gesturesInFlight()) throw Refusal("busy", "Wait for the previous gesture to finish")
        gesture(window.service, Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }, ms, peek)
    }

    /**
     * 指を離さずに [points] を順に通る 1 本の操作 (`z2-ui drag`)。パズルのように、なぞる途中で指を
     * 離すと終わる画面のためのもの。点はすべて前面のアプリの窓の中。速さは道のり全体で一定 ([ms] で通り切る)。
     */
    suspend fun drag(points: List<Pair<Int, Int>>, ms: Long, gate: (String) -> Unit, peek: Peek? = null, holdMs: Long = 0) {
        val window = window() ?: throw Refusal("no_window", "No foreground app window")
        gate(window.pkg)
        if (points.any { (x, y) -> !window.bounds.contains(x, y) })
            throw Refusal("outside", "All points must be inside the foreground app window")
        if (AndroidActions.gesturesInFlight()) throw Refusal("busy", "Wait for the previous gesture to finish")
        val path = Path().apply {
            moveTo(points[0].first.toFloat(), points[0].second.toFloat())
            points.drop(1).forEach { (x, y) -> lineTo(x.toFloat(), y.toFloat()) }
        }
        gesture(window.service, path, ms, peek, holdMs)
    }

    /**
     * スクロールを引き受ける要素と操作。向きのある操作 (上・下・左・右) を先に探し、無ければ
     * 前後 (下・右 = 先へ、上・左 = 前へ) で代える。8 階層以内の祖先まで探す。
     */
    private suspend fun scroller(node: AccessibilityNodeInfo, direction: String, window: Window, located: Located): Pair<AccessibilityNodeInfo, Int> =
        withContext(Dispatchers.IO) {
            val wanted = when (direction) {
                "up" -> listOf(AccessibilityAction.ACTION_SCROLL_UP.id, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                "down" -> listOf(AccessibilityAction.ACTION_SCROLL_DOWN.id, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                "left" -> listOf(AccessibilityAction.ACTION_SCROLL_LEFT.id, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                "right" -> listOf(AccessibilityAction.ACTION_SCROLL_RIGHT.id, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                else -> throw Refusal("bad_args", "Direction must be up, down, left or right")
            }
            var current = node
            var depth = 0
            while (true) {
                wanted.firstOrNull { offers(current, it) }?.let { return@withContext current to it }
                if (depth++ >= 8) throw Refusal("not_scrollable", "No element within eight ancestors scrolls $direction")
                current = current.parent ?: throw Refusal("not_scrollable", "The element does not scroll $direction")
                located.retained += current
                if (current.windowId != window.id || current.packageName?.toString() != window.pkg)
                    throw Refusal("not_scrollable", "The scroll would reach outside the foreground app")
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }

    /**
     * 前面のアプリの窓の範囲だけを PNG に書く。通知欄やほかのアプリが写らないよう、窓の外は切り落とす。
     * 撮影を禁じている画面 (FLAG_SECURE) は Android が黒く塗った画像を返す。
     *
     * ⚠ Android は画面取得の間隔に下限がある。バーの自動配色 ([EdgeHandleContrast]) と時刻を共有し、
     * 間隔が空くまで待ってから撮る ([RegionShot] と同じ)。
     */
    @androidx.annotation.RequiresApi(30)
    class Shot(val box: Rect, val size: android.util.Size, val stable: Boolean?, val waitedMs: Long, val unchanged: Boolean = false,
        /** `expectChange` のとき、前回返した画像から変わったか (比べる相手が無ければ null)。 */
        val changed: Boolean? = null,
        /** `colors` を渡したとき、同じ 1 枚から読んだ点ごとの色 (0xRRGGBB)。 */
        val rgb: IntArray? = null)

    /** 色を読む点 (`colors` / `shot --grid`)。各点は [radius] px 四方の中央値。 */
    class Points(val points: List<Pair<Int, Int>>, val radius: Int)

    /** 直前に保存した画像の印 ([shot] の `ifChanged`)。ファイル名・窓・範囲・縮小が同じときだけ比べる。 */
    private class Saved(val key: String, val print: IntArray)
    @Volatile private var saved: Saved? = null

    /**
     * [stableMs] > 0 なら、画面の動きが止まるまで (続けて撮った 2 枚がほぼ同じになるまで) 撮り続け、
     * 止まった 1 枚を保存する。最大 [stableMs] 待って止まらなければ、最後の 1 枚を保存する。
     * 止まったかは [box] の中だけで見る (範囲の外で動き続けるものに待たされない)。
     * [delayMs] は撮り始める前の待ち。操作の直後は画面がまだ反応していないことがあるため。
     * [expectChange] なら、止まるのを待つ前に、前回返した画像から変わるまで最大 [CHANGE_MAX_MS] 待つ
     * (操作への反応が遅い画面で、操作前の静かな画面を「止まった」と返さないため)。
     * [colors] を渡すと、保存する 1 枚と同じ画像から色も読む (撮り直さない)。
     */
    @androidx.annotation.RequiresApi(30)
    suspend fun shot(file: java.io.File, scale: Float, jpeg: Boolean, gate: (String) -> Unit,
        stableMs: Long = 0, delayMs: Long = 0, box: Rect? = null, ifChanged: Boolean = false,
        expectChange: Boolean = false, colors: Points? = null): Shot {
        val started = SystemClock.uptimeMillis()
        // 窓 (と `--box`) の範囲。外れていれば null。
        fun areaOf(window: Window, image: android.graphics.Bitmap): Rect? = Rect(window.bounds).takeIf {
            it.intersect(0, 0, image.width, image.height) && (box == null || it.intersect(box))
        }
        fun keyOf(window: Window, area: Rect) = "${file.name}|${window.id}|${area.flattenToString()}|$scale"
        val reference = saved.takeIf { expectChange }
        val f = frame(gate, stableMs, delayMs, settleArea = box, unchanged = reference?.let { ref ->
            { image, window -> areaOf(window, image)?.let { keyOf(window, it) == ref.key && UiBridgePolicy.same(ref.print, thumbnail(image, it, PRINT_WIDTH)) } ?: false }
        })
        val window = f.window
        val image = f.image
        try {
            if (Rect(window.bounds).apply { intersect(0, 0, image.width, image.height) }.isEmpty)
                throw Refusal("no_window", "The window is outside the captured screen")
            // `--box` は窓の中の一部だけ。窓の外へはみ出した分は切り落とす。
            val area = areaOf(window, image) ?: throw Refusal("outside", "The box is outside the foreground app window")
            val rgb = colors?.let { pick(image, window, it) }
            // 直前と同じ画面なら書かずに返す (AI に同じ画像をもう 1 度読ませない)。
            val key = keyOf(window, area)
            val print = thumbnail(image, area, PRINT_WIDTH)
            val previous = saved
            if (ifChanged && previous != null && previous.key == key && UiBridgePolicy.same(previous.print, print))
                return Shot(area, android.util.Size((area.width() * scale).toInt().coerceAtLeast(1),
                    (area.height() * scale).toInt().coerceAtLeast(1)), f.stable, SystemClock.uptimeMillis() - started,
                    unchanged = true, changed = f.changed, rgb = rgb)
            val cropped = android.graphics.Bitmap.createBitmap(image, area.left, area.top, area.width(), area.height())
            val out = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(cropped,
                (area.width() * scale).toInt().coerceAtLeast(1), (area.height() * scale).toInt().coerceAtLeast(1), true) else cropped
            try {
                withContext(Dispatchers.IO) {
                    file.parentFile?.mkdirs()
                    val tmp = java.io.File(file.parentFile, ".${file.name}.tmp")
                    // JPEG は PNG より書き出しが速く小さい (画面の写真なら AI が読むのに十分)。
                    tmp.outputStream().use {
                        if (jpeg) out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
                        else out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    if (!tmp.renameTo(file)) throw Refusal("error", "Could not write ${file.name}")
                }
                saved = Saved(key, print)
                return Shot(area, android.util.Size(out.width, out.height), f.stable, SystemClock.uptimeMillis() - started,
                    changed = f.changed, rgb = rgb)
            } finally {
                if (out !== cropped) out.recycle()
                if (cropped !== image) cropped.recycle()
            }
        } finally { image.recycle() }
    }

    /** [frame] の結果。[stable] は止まったか ([frame] の stableMs が 0 なら null)、[changed] は前回から変わったか。 */
    private class Frame(val window: Window, val image: android.graphics.Bitmap, val stable: Boolean?, val changed: Boolean?)

    /**
     * 前面の窓と、画面全体を撮った 1 枚。撮っている間に前面が変わったら断る。
     *  1. [unchanged] があれば、それが false を返す (前回から変わった) まで最大 [CHANGE_MAX_MS] 撮り直す。
     *  2. [stableMs] > 0 なら、続けて撮った 2 枚が [settleArea] (無ければ窓全体) の中でほぼ同じになるまで撮り続ける。
     *     1 で変化を待った時間は、ここの最大待ちに数えない。
     */
    @androidx.annotation.RequiresApi(30)
    private suspend fun frame(gate: (String) -> Unit, stableMs: Long, delayMs: Long, settleArea: Rect? = null,
        unchanged: ((android.graphics.Bitmap, Window) -> Boolean)? = null): Frame {
        if (delayMs > 0) delay(delayMs)
        val window = window() ?: throw Refusal("no_window", "No foreground app window")
        gate(window.pkg)
        var image = capture(window.service)
        var changed: Boolean? = null
        if (unchanged != null) {
            val until = SystemClock.uptimeMillis() + CHANGE_MAX_MS
            changed = !unchanged(image, window)
            while (changed != true && SystemClock.uptimeMillis() < until) {
                val next = capture(window.service)
                image.recycle(); image = next
                changed = !unchanged(image, window)
            }
        }
        var stable: Boolean? = null
        if (stableMs > 0) {
            stable = false
            // 判定の範囲を窓の中へ絞る。窓と交わらなければ窓全体で見る。
            val area = Rect(window.bounds).also { a -> settleArea?.let { if (!a.intersect(it)) a.set(window.bounds) } }
            val from = SystemClock.uptimeMillis()
            var previous = thumbnail(image, area, SETTLE_WIDTH)
            while (SystemClock.uptimeMillis() - from < stableMs) {
                val next = capture(window.service)
                val current = thumbnail(next, area, SETTLE_WIDTH)
                image.recycle(); image = next
                if (UiBridgePolicy.settled(previous, current, SETTLE_WIDTH)) { stable = true; break }
                previous = current
            }
        }
        if (window()?.id != window.id) { image.recycle(); throw Refusal("changed", "The screen changed while capturing; try again") }
        return Frame(window, image, stable, changed)
    }

    /** [image] の点の色。各点は radius px 四方の中央値。点はすべて [window] の中 (呼ぶ前に確かめる)。 */
    private fun pick(image: android.graphics.Bitmap, window: Window, at: Points): IntArray {
        if (at.points.any { (x, y) -> !window.bounds.contains(x, y) })
            throw Refusal("outside", "All points must be inside the foreground app window")
        val area = Rect(window.bounds).apply { intersect(0, 0, image.width, image.height) }
        return IntArray(at.points.size) { i ->
            val (x, y) = at.points[i]
            val box = Rect(x - at.radius, y - at.radius, x + at.radius + 1, y + at.radius + 1)
            if (!box.intersect(area)) return@IntArray 0
            val pixels = IntArray(box.width() * box.height())
            image.getPixels(pixels, 0, box.width(), box.left, box.top, box.width(), box.height())
            UiBridgePolicy.median(pixels)
        }
    }

    /** [sample] の結果。[rgb] は点ごとの 0xRRGGBB。 */
    class Sample(val rgb: IntArray, val stable: Boolean?)

    /**
     * 画面の点の色を読む (`z2-ui colors`)。画像を保存せず、AI には色の名前を文字で返す (画像を読ませるより速い)。
     * 止まったかは点のまわり (点をすべて囲む範囲) だけで見る。
     */
    @androidx.annotation.RequiresApi(30)
    suspend fun sample(at: Points, gate: (String) -> Unit, stableMs: Long): Sample {
        val window = window() ?: throw Refusal("no_window", "No foreground app window")
        if (at.points.any { (x, y) -> !window.bounds.contains(x, y) })
            throw Refusal("outside", "All points must be inside the foreground app window")
        val around = Rect(at.points.minOf { it.first } - at.radius, at.points.minOf { it.second } - at.radius,
            at.points.maxOf { it.first } + at.radius + 1, at.points.maxOf { it.second } + at.radius + 1)
        val f = frame(gate, stableMs, 0L, settleArea = around)
        try {
            if (f.window.id != window.id) throw Refusal("changed", "The screen changed; try again")
            return Sample(pick(f.image, f.window, at), f.stable)
        } finally { f.image.recycle() }
    }

    /**
     * 範囲を [width] px 幅に縮めた明るさの並び。画面が動いているか (48 px)・前の画像と同じか ([PRINT_WIDTH]) を
     * 比べるだけに使う。
     */
    private fun thumbnail(image: android.graphics.Bitmap, bounds: Rect, width: Int): IntArray {
        val area = Rect(bounds)
        if (!area.intersect(0, 0, image.width, image.height)) return IntArray(0)
        val height = (width.toFloat() * area.height() / area.width().coerceAtLeast(1)).toInt().coerceIn(1, width * 8)
        val cropped = android.graphics.Bitmap.createBitmap(image, area.left, area.top, area.width(), area.height())
        val small = android.graphics.Bitmap.createScaledBitmap(cropped, width, height, true)
        val pixels = IntArray(width * height).also { small.getPixels(it, 0, width, 0, 0, width, height) }
        if (small !== cropped) small.recycle()
        if (cropped !== image) cropped.recycle()
        return IntArray(pixels.size) { i ->
            val c = pixels[i]
            (((c shr 16) and 0xff) * 3 + ((c shr 8) and 0xff) * 6 + (c and 0xff)) / 10
        }
    }

    @androidx.annotation.RequiresApi(30)
    private suspend fun capture(service: AccessibilityService): android.graphics.Bitmap {
        repeat(2) { attempt ->
            // Android の下限 (約 1/3 秒) だけ空ける。バーの配色が使う 1 秒はあちらの都合で、z2-ui が続けて
            // 撮るときまで 1 秒待つ理由にはならない。下限に当たったときは下で 1 度だけ撮り直す。
            val since = SystemClock.uptimeMillis() - EdgeHandleContrast.lastRequestAt
            delay(((if (attempt == 0) SHOT_INTERVAL_MS else EdgeHandleContrast.MIN_INTERVAL_MS) - since).coerceAtLeast(0L))
            EdgeHandleContrast.lastRequestAt = SystemClock.uptimeMillis()
            val result = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Any> { cont ->
                    service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, shotExecutor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                                val buffer = screenshot.hardwareBuffer
                                var hardware: android.graphics.Bitmap? = null
                                val copy = try {
                                    hardware = android.graphics.Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                    // ハードウェア面のままでは画素を切り出せないので 1 度だけ複製する。
                                    hardware?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                } catch (_: Exception) { null } finally { hardware?.recycle(); buffer.close() }
                                if (cont.isActive) cont.resume(copy ?: -1)
                            }
                            override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(errorCode) }
                        })
                }
            }
            if (result is android.graphics.Bitmap) return result
            // 撮影間隔の制限に当たったときだけ、間を空けてもう 1 度撮る。
            if (result != AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT || attempt == 1)
                throw Refusal("capture_failed", "Android could not capture the screen (code $result)")
        }
        throw Refusal("capture_failed", "Android could not capture the screen")
    }

    /**
     * 操作を送り、指が離れるまで待つ。[peek] があれば、指を置いている間に撮る。
     * 撮影は操作の終わりを待たずに並べて走らせる (以前は 1 つずつで、指を離すまで撮れなかった)。
     */
    private suspend fun gesture(service: AccessibilityService, path: Path, ms: Long, peek: Peek? = null, holdMs: Long = 0) = coroutineScope {
        // 指が離れたことは結果の呼び戻しで直接残す (撮っている間はこの流れの再開が後回しになることがあるため)。
        val ended = java.util.concurrent.atomic.AtomicBoolean(false)
        val stroke = async {
            if (holdMs <= 0) send(service, GestureDescription.StrokeDescription(path, 0, ms), ended)
            else {
                // 始点で [holdMs] 指を止めてから動かす (`drag --hold`)。触れてすぐ動くと「掴んだ」と
                // 認められない画面があるため。止める分を指を離さない 1 本目にして、続きを 2 本目でつなぐ。
                val start = FloatArray(2).also { android.graphics.PathMeasure(path, false).getPosTan(0f, it, null) }
                val first = GestureDescription.StrokeDescription(Path().apply { moveTo(start[0], start[1]) }, 0, holdMs, true)
                send(service, first, null)
                send(service, first.continueStroke(path, 0, ms, false), ended)
            }
        }
        peek?.let {
            delay(it.atMs)
            it.result = it.take()
            it.held = !ended.get()
        }
        stroke.await()
    }

    /** [stroke] を 1 回送り、終わるまで待つ。[ended] は指が離れたときに立てる印 (続きがある 1 本目では null)。 */
    private suspend fun send(service: AccessibilityService, stroke: GestureDescription.StrokeDescription,
        ended: java.util.concurrent.atomic.AtomicBoolean?) =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val sent = service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            ended?.set(true)
                            if (cont.isActive) cont.resume(Unit)
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            ended?.set(true)
                            if (cont.isActive) cont.resumeWithException(Refusal("rejected", "Android cancelled the gesture"))
                        }
                    }, null)
                if (!sent) ended?.set(true)
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
