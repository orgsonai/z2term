package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * スクロールを**スクロールできる部品そのものに頼む** ([AccessibilityNodeInfo.ACTION_SCROLL_FORWARD] /
 * `ACTION_SCROLL_BACKWARD`)。[AndroidAutoScroll] の代わりに使う。
 *
 * **なぜ要るか**: [AndroidAutoScroll] は本物のスワイプを画面へ注入するので、**アプリから見ると
 * 指で触ったのと区別が付かない**。スワイプに機能が割り当たっている画面 (一覧を横に払うと返信・
 * 削除が走るもの) では**その機能が動いてしまい**、グライド入力のキーボードに線が当たれば文字になる
 * (利用者の報告:「ジェスチャーすると文字が勝手入力されたりかなり危険です」)。⇒ 画面に**一切触らず**
 * 部品へ直接頼めば、誤入力・誤送信・払う操作の誤爆が**原理的に起こらない**。
 *
 * ⚠ **座標ではなく部品を見る**ので、自由な大きさの窓 (フリーフォーム) でも当たる。スワイプ側は
 * 窓の矩形の中央へ線を引くため、キャプションや余白に当たって滑らないことがあった。
 *
 * ⚠ **送り量はアプリ持ち**。1 回で動くのは部品が決める量 (おおむね 1 画面) で、Android 14 以降だけ
 * [AccessibilityNodeInfo.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT] で端数を頼める。⇒ **速度は
 * 「送る間隔」で作る** (1 回ぶんの距離 ÷ 速度)。指で速さを変える滑らかさはスワイプに劣るので、
 * どちらを使うかは `scroll-how` で選べるようにしてある。
 *
 * ⚠ **スクロールできる部品を公開していないアプリでは何も起きない** (キャンバス系・一部のゲーム)。
 * [start] は**そのとき false を返す**ので、呼び出し側が `auto` ならスワイプへ落とせる。
 */
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
    /** ⚠ 古くなったノードから窓の番号は読めない。探し直すために別に持つ。 */
    private var nodeWindow = -1

    /** 直前まで動いていたか ([AndroidAutoScroll.recentlyRunning] と同じ 500ms の猶予)。 */
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
        val result = error ?: if (singleShot && !completed)
            service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed) else null
        singleShot = false
        callback?.invoke(result)
    }

    /**
     * 指で触ったら止める。⚠ **注入した操作と本物の指を取り違えない** — こちらは画面へ何も注入しないが、
     * 同じ画面に [AndroidAutoScroll] が出した合成イベントが来ることがある ([AndroidAutoScroll.outsideTouch]
     * と同じ判定を使う)。
     */
    fun outsideTouch(event: android.view.MotionEvent) {
        val synthetic = event.deviceId == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD &&
            event.getToolType(0) == android.view.MotionEvent.TOOL_TYPE_UNKNOWN
        if (!synthetic) stop()
    }

    /**
     * [windowId] の中でスクロールできる部品を探して送り始める。
     *
     * @param speedDp 正で下へ (指を下ろすのと同じ = 前の内容へ戻る)、負で上へ
     * @param area 窓のうち実際に見えている範囲 (キーボードのぶんを除いてある)
     * @return 始められたら true。⚠ **スクロールできる部品が無ければ false** (呼び出し側の判断で
     *   スワイプへ落とす)。ここで例外にすると `auto` の落とし先が書けない
     */
    fun start(speedDp: Float, windowId: Int, area: Rect, xPercent: Float, yPercent: Float,
        once: Boolean, stillTarget: () -> Boolean, done: (String?) -> Unit): Boolean {
        require(speedDp.isFinite() && speedDp != 0f && !area.isEmpty)
        stop()
        val x = area.left + area.width() * xPercent.coerceIn(0f, 100f) / 100f
        val y = area.top + area.height() * yPercent.coerceIn(0f, 100f) / 100f
        val found = find(windowId, x.toInt(), y.toInt()) ?: return false
        node = found
        nodeWindow = windowId
        running = true
        singleShot = once
        stoppedAt = null
        finished = done
        val token = generation
        val density = service.resources.displayMetrics.density
        // 1 回で動く量。端数を頼めない Android では部品任せ (ほぼ 1 画面) になるので、
        // 見えている高さをそのまま 1 回ぶんとみなして間隔を決める。
        val fraction = if (Build.VERSION.SDK_INT >= 34) 0.25f else 1f
        val stepDp = (bounds(found).height() / density).coerceAtLeast(48f) * fraction
        val period = (stepDp / abs(speedDp) * 1000f).toLong().coerceIn(40L, 3000L)
        val tick = object : Runnable {
            override fun run() {
                if (!running || token != generation) return
                if (!runCatching(stillTarget).getOrDefault(false)) { stop(); return }
                if (!send(speedDp, fraction)) {
                    stop(service.getString(com.zerotoship.z2term.R.string.edge_scroll_failed)); return
                }
                if (once) { stop(completed = true); return }
                next?.let { main.postDelayed(it, period) }
            }
        }
        next = tick
        main.post(tick)
        return true
    }

    private fun bounds(target: AccessibilityNodeInfo) = Rect().also { target.getBoundsInScreen(it) }

    /**
     * 1 回ぶん送る。⚠ **ノードは古くなる** — 一覧は送るたびに作り直されるので、[AccessibilityNodeInfo.refresh]
     * が false を返したら探し直す (探し直さないと 1 回動いたきり止まって見える)。
     */
    private fun send(speedDp: Float, fraction: Float): Boolean {
        var target = node ?: return false
        if (!runCatching { target.refresh() }.getOrDefault(false)) {
            val again = find(nodeWindow, null, null) ?: return false
            target.recycle(); node = again; target = again
        }
        // 正の速度 = 指を下ろす = 前 (上) の内容が出てくる。スワイプ側と向きを合わせる。
        val action = if (speedDp > 0) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        val arguments = if (Build.VERSION.SDK_INT >= 34) Bundle().apply {
            putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, fraction)
        } else null
        return runCatching { target.performAction(action, arguments) }.getOrDefault(false)
    }

    /**
     * [windowId] の中でスクロールできる部品を探す。
     *
     * ⚠ **点 ([x], [y]) を含むもののうち、いちばん内側**を採る。外側から採ると、入れ子になった一覧
     * (画面全体のスクロールビューの中にリストがある形) で**外側だけが動いて中身が動かない**。
     * 点を含むものが無ければ、窓の中でいちばん広いものへ落とす (`scroll-x` / `scroll-y` が
     * たまたま余白を指しているだけのことがある)。
     */
    private fun find(windowId: Int, x: Int?, y: Int?): AccessibilityNodeInfo? {
        val windows = service.windows
        val root = try {
            windows.firstOrNull { it.id == windowId }?.root
        } catch (_: Exception) { null } finally { windows.forEach { it.recycle() } }
        if (root == null) return null
        val queue = ArrayList<AccessibilityNodeInfo>()
        queue += root
        var inner: AccessibilityNodeInfo? = null
        var innerArea = Long.MAX_VALUE
        var widest: AccessibilityNodeInfo? = null
        var widestArea = 0L
        var index = 0
        val started = SystemClock.uptimeMillis()
        try {
            while (index < queue.size) {
                if (SystemClock.uptimeMillis() - started > 400 || queue.size > 1024) break
                val current = queue[index++]
                if (current.isScrollable && current.isVisibleToUser) {
                    val box = bounds(current)
                    val size = box.width().toLong() * box.height().toLong()
                    if (size > 0) {
                        if (x != null && y != null && box.contains(x, y) && size < innerArea) {
                            inner = current; innerArea = size
                        }
                        if (size > widestArea) { widest = current; widestArea = size }
                    }
                }
                repeat(current.childCount) { child -> current.getChild(child)?.let { queue += it } }
            }
        } catch (_: Exception) { /* 木は途中で作り直される。拾えたところまでで決める。 */ }
        val chosen = inner ?: widest
        queue.forEach { if (it !== chosen) it.recycle() }
        return chosen
    }
}
