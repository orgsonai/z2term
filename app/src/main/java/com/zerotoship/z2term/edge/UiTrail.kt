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
        current.begin(points, ms, holdMs)
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

        private var points: List<Pair<Float, Float>> = emptyList()
        /** 始点から各点までの道のり。 */
        private var lengths = FloatArray(0)
        private var startedAt = 0L
        private var holdMs = 0L
        private var moveMs = 0L

        fun begin(at: List<Pair<Int, Int>>, ms: Long, hold: Long) {
            points = at.map { it.first.toFloat() to it.second.toFloat() }
            lengths = FloatArray(points.size)
            for (i in 1 until points.size)
                lengths[i] = lengths[i - 1] + hypot(points[i].first - points[i - 1].first, points[i].second - points[i - 1].second)
            startedAt = SystemClock.uptimeMillis()
            holdMs = hold
            moveMs = ms
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (points.isEmpty()) return
            val now = SystemClock.uptimeMillis()
            val up = startedAt + holdMs + moveMs
            if (now >= up + LINGER_MS) { post { if (view === this) remove() }; return }
            // 窓が画面の左上から始まらない端末もあるので、画面の px から窓の中の位置へずらす。
            getLocationOnScreen(location)
            canvas.translate(-location[0].toFloat(), -location[1].toFloat())
            val fade = if (now <= up) 1f else 1f - (now - up).toFloat() / LINGER_MS
            val alpha = (255 * fade).toInt().coerceIn(0, 255)
            line.alpha = alpha; edge.alpha = alpha * EDGE_ALPHA / 255; label.alpha = alpha

            val total = lengths.last()
            val progress = if (moveMs <= 0 || now >= up) 1f
                else ((now - startedAt - holdMs).toFloat() / moveMs).coerceIn(0f, 1f)
            val reached = total * progress
            val finger = if (points.size == 1) points[0] else position(reached)
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points[0].first, points[0].second)
                    for (i in 1 until points.size) {
                        if (lengths[i] > reached) break
                        lineTo(points[i].first, points[i].second)
                    }
                    lineTo(finger.first, finger.second)
                }
                canvas.drawPath(path, edge)
                canvas.drawPath(path, line)
                // 通る点の番号。同じ点を何度も通るときは最初の番号だけ (重なって読めなくなるため)。
                val labelled = HashSet<Pair<Float, Float>>()
                for (i in points.indices) {
                    if (lengths[i] > reached) break
                    if (!labelled.add(points[i])) continue
                    fill.color = EDGE; fill.alpha = alpha * EDGE_ALPHA / 255
                    canvas.drawCircle(points[i].first, points[i].second, 10 * density, fill)
                    canvas.drawText("${i + 1}", points[i].first, points[i].second + label.textSize / 3, label)
                }
            }
            // 指の位置。置いている間は塗った丸、離したあとは輪で残す。
            val radius = 18 * density
            if (now < up) {
                fill.color = TRAIL; fill.alpha = alpha * FINGER_ALPHA / 255
                canvas.drawCircle(finger.first, finger.second, radius, fill)
            }
            canvas.drawCircle(finger.first, finger.second, radius, edge)
            canvas.drawCircle(finger.first, finger.second, radius, line)
            postInvalidateOnAnimation()
        }

        /** 道のり [distance] の位置。 */
        private fun position(distance: Float): Pair<Float, Float> {
            for (i in 1 until points.size) {
                if (lengths[i] >= distance) {
                    val span = lengths[i] - lengths[i - 1]
                    val t = if (span <= 0f) 1f else (distance - lengths[i - 1]) / span
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
