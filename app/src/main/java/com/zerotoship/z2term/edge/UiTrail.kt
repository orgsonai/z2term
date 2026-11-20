package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * AI 操作 (`z2-ui`) の指の軌跡を画面に描く (1.0.46、設定「操作の軌跡を表示」)。人が見て追えるようにするためのもの。
 * 1.0.185 から画面操作のマクロ (`z2-action`) も [timed] で描く (画面操作のタブの設定)。こちらは送る経路そのもの
 * (時刻つきの点・2 本指・人らしい揺らぎ込み) を時刻どおりに描き、点の番号は付けない。
 *
 * タップ・長押しは押した位置の丸、swipe / drag はなぞった線を指の動きに合わせて伸ばし、通る点に番号を添える。
 * 指が離れてから [LINGER_MS] で消える。z2term のユーザー補助サービスの重ね窓に描くので、
 * 「他のアプリの上に表示」の許可は要らない。触れない窓 (FLAG_NOT_TOUCHABLE) で、送る操作の邪魔をしない。
 *
 * ⚠ 重ね窓は撮影に写る。z2-ui が撮る直前に [clear] で消し、AI に届く画像・色・「止まったか」の判定を
 * 軌跡なしの画面に保つ (`--peek` も同じで、指を置いている途中でも撮る前に消す)。
 */
internal object UiTrail {
    /** 指が離れてから消えるまで。 */
    private const val LINGER_MS = 1000L
    /** 消してから画面に反映されるまで待つ時間 (60 Hz で数フレーム分)。 */
    private const val CLEAR_SETTLE_MS = 60L

    private var view: TrailView? = null
    private var manager: WindowManager? = null

    /**
     * 操作を送る直前に呼ぶ (メインスレッド)。[points] は画面の px、指は [holdMs] 始点に止まってから
     * [ms] で道のり全体を一定の速さで通る (StrokeDescription と同じ)。点が 1 つならタップ・長押し。
     */
    @SuppressLint("ClickableViewAccessibility")
    fun start(service: AccessibilityService, points: List<Pair<Int, Int>>, ms: Long, holdMs: Long) {
        val at = points.map { it.first.toFloat() to it.second.toFloat() }
        // 道のり全体を一定の速さで通る時刻に直す (始点で holdMs 止まってから)。
        val lengths = FloatArray(at.size)
        for (i in 1 until at.size) lengths[i] = lengths[i - 1] + hypot(at[i].first - at[i - 1].first, at[i].second - at[i - 1].second)
        val total = lengths.last()
        val times = LongArray(at.size) { i -> holdMs + if (total <= 0f) (if (i == 0) 0 else ms) else (ms * lengths[i] / total).toLong() }
        if (at.size > 1) times[0] = holdMs
        // 指は 0 ms に置き、始点で holdMs 止まる (動き出すのが times[0])。
        show(service, listOf(Track(at, times, downAt = 0)), holdMs + ms, numbered = true)
    }

    /**
     * 画面操作のマクロの指 (メインスレッド)。[tracks] は指ごとの時刻つきの点 (画面の px、最初の指が 0 ms から)、
     * 指は [endMs] で離れる。点が 1 つの指はその場に置いたまま (タップ・長押し)。
     */
    fun timed(service: AccessibilityService, tracks: List<List<Triple<Float, Float, Long>>>, endMs: Long) {
        show(service, tracks.filter { it.isNotEmpty() }.map { track ->
            Track(track.map { it.first to it.second }, LongArray(track.size) { track[it].third }, downAt = track[0].third)
        }, endMs, numbered = false)
    }

    /** 指 1 本の点と、その点に着く時刻 (描き始めからのミリ秒)。指を置くのは [downAt]。 */
    private class Track(val points: List<Pair<Float, Float>>, val times: LongArray, val downAt: Long)

    @SuppressLint("ClickableViewAccessibility")
    private fun show(service: AccessibilityService, tracks: List<Track>, endMs: Long, numbered: Boolean) {
        if (tracks.isEmpty()) return
        val current = view ?: TrailView(service).also { v ->
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = ScreenGravity.TOP_LEFT
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (runCatching { wm.addView(v, params) }.isFailure) return
            view = v
            manager = wm
        }
        current.begin(tracks, endMs, numbered)
    }

    /** 撮る直前に消す。消したものがあれば、画面から消えるまで少し待つ。 */
    suspend fun clear() {
        val removed = withContext(Dispatchers.Main) { remove() }
        if (removed) delay(CLEAR_SETTLE_MS)
    }

    private fun remove(): Boolean {
        val v = view ?: return false
        runCatching { manager?.removeViewImmediate(v) }
        view = null
        manager = null
        return true
    }

    @SuppressLint("ViewConstructor")
    private class TrailView(context: Context) : View(context) {
        private val density = context.resources.displayMetrics.density
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 5 * density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            color = TRAIL
        }
        // どんな背景の上でも見えるよう、線と丸の下に暗い縁を敷く。
        private val edge = Paint(line).apply { strokeWidth = 9 * density; color = EDGE }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12 * density; color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        private val location = IntArray(2)

        private var tracks: List<Track> = emptyList()
        private var startedAt = 0L
        private var endMs = 0L
        private var numbered = false

        fun begin(at: List<Track>, end: Long, number: Boolean) {
            tracks = at
            endMs = end
            numbered = number
            startedAt = SystemClock.uptimeMillis()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (tracks.isEmpty()) return
            val now = SystemClock.uptimeMillis()
            val up = startedAt + endMs
            if (now >= up + LINGER_MS) { post { if (view === this) remove() }; return }
            // 窓が画面の左上から始まらない端末もあるので、画面の px から窓の中の位置へずらす。
            getLocationOnScreen(location)
            canvas.translate(-location[0].toFloat(), -location[1].toFloat())
            val fade = if (now <= up) 1f else 1f - (now - up).toFloat() / LINGER_MS
            val alpha = (255 * fade).toInt().coerceIn(0, 255)
            line.alpha = alpha; edge.alpha = alpha * EDGE_ALPHA / 255; label.alpha = alpha
            val elapsed = (now - startedAt).coerceAtMost(endMs)
            tracks.forEach { track -> drawTrack(canvas, track, elapsed, now < up, alpha) }
            postInvalidateOnAnimation()
        }

        private fun drawTrack(canvas: Canvas, track: Track, elapsed: Long, down: Boolean, alpha: Int) {
            val points = track.points
            val times = track.times
            // 2 本目の指は遅れて置くことがある。置く前は描かない。
            if (elapsed < track.downAt && down) return
            val finger = position(track, elapsed)
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points[0].first, points[0].second)
                    for (i in 1 until points.size) {
                        if (times[i] > elapsed) break
                        lineTo(points[i].first, points[i].second)
                    }
                    lineTo(finger.first, finger.second)
                }
                canvas.drawPath(path, edge)
                canvas.drawPath(path, line)
                // 通る点の番号 (AI 操作の点だけ)。同じ点を何度も通るときは最初の番号だけ (重なって読めなくなるため)。
                if (numbered) {
                    val labelled = HashSet<Pair<Float, Float>>()
                    for (i in points.indices) {
                        if (times[i] > elapsed) break
                        if (!labelled.add(points[i])) continue
                        fill.color = EDGE; fill.alpha = alpha * EDGE_ALPHA / 255
                        canvas.drawCircle(points[i].first, points[i].second, 10 * density, fill)
                        canvas.drawText("${i + 1}", points[i].first, points[i].second + label.textSize / 3, label)
                    }
                }
            }
            // 指の位置。置いている間は塗った丸、離したあとは輪で残す。
            val radius = 18 * density
            val liftAt = if (points.size == 1) endMs else times.last()
            if (down && elapsed < liftAt) {
                fill.color = TRAIL; fill.alpha = alpha * FINGER_ALPHA / 255
                canvas.drawCircle(finger.first, finger.second, radius, fill)
            }
            canvas.drawCircle(finger.first, finger.second, radius, edge)
            canvas.drawCircle(finger.first, finger.second, radius, line)
        }

        /** 時刻 [elapsed] の指の位置 (点と点の間は時刻で割り振る)。 */
        private fun position(track: Track, elapsed: Long): Pair<Float, Float> {
            val points = track.points
            val times = track.times
            if (elapsed <= times[0]) return points[0]
            for (i in 1 until points.size) {
                if (times[i] >= elapsed) {
                    val span = times[i] - times[i - 1]
                    val t = if (span <= 0L) 1f else ((elapsed - times[i - 1]).toFloat() / span).coerceIn(0f, 1f)
                    val (x0, y0) = points[i - 1]
                    val (x1, y1) = points[i]
                    return x0 + (x1 - x0) * t to y0 + (y1 - y0) * t
                }
            }
            return points.last()
        }
    }

    private const val TRAIL = 0xFF22C55E.toInt()
    private const val EDGE = 0xFF000000.toInt()
    private const val EDGE_ALPHA = 140
    private const val FINGER_ALPHA = 110
}
