package com.zerotoship.z2term.automation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * `human=on` (1.0.184): 機械的な操作 (同じ点・直線・一定の速さ・同じ待ち) を、人の指に近い揺らぎのある操作に変える。
 * 手順の意味 (どこを押すか・どこからどこへ動かすか・全体の長さ) は保ち、揺らぎは画面の短辺の 0.6% 程度に収める。
 *
 * - タップ・長押し・ダブルタップ: 押す位置を少しずらし、押す長さと間隔をばらつかせる。
 * - スワイプ (直線): 線を少し曲げ、動き出しと止まり際を遅く、途中の速さも揺らす。終点も少しずらす。
 * - なぞる (`drag`、1.0.185): 決めた点は必ず通り、区間ごとに少し弓なりにして、点の近くで速さを落とす
 *   (止まりはしない)。角は最大 4 px だけ丸める。区間の時間を揺らし、全体の長さは同じ。
 * - 記録した軌跡: 形はそのまま位置だけずらし、1 本指なら区間ごとの時間を揺らす (全体の長さは同じ)。
 * - wait: ±10% (最大 ±300 ms) ばらつかせる。
 *
 * 点は [area] (前面の窓の中) に収める。座標は px の手順にして返す。
 */
internal object ActionHumanize {
    /** 窓の範囲 (px、right / bottom は含まない)。 */
    data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private const val JITTER_RATIO = 0.006f
    private const val BOW_RATIO = 0.05f
    private const val MAX_BOW_PX = 48f
    private const val TAP_MIN_MS = 60L
    private const val TAP_MAX_MS = 110L
    private const val MANUAL_MAX_MS = 3000L

    fun waitMs(ms: Long, random: Random): Long {
        if (ms <= 0) return ms
        val spread = minOf(ms * 0.1, 300.0)
        return (ms + (random.nextDouble() * 2 - 1) * spread).roundToLong().coerceAtLeast(0)
    }

    fun stroke(step: ActionDefinition.Step.Stroke, screen: ActionDefinition.Screen, area: Area, random: Random): ActionDefinition.Step.Stroke {
        val radius = maxOf(2f, minOf(screen.width, screen.height) * JITTER_RATIO)
        fun offset(): Pair<Float, Float> {
            val angle = random.nextDouble() * 2 * PI
            val r = radius * sqrt(random.nextDouble()).toFloat()
            return (cos(angle) * r).toFloat() to (sin(angle) * r).toFloat()
        }
        fun clampX(x: Float) = x.coerceIn(area.left.toFloat(), (area.right - 1).toFloat())
        fun clampY(y: Float) = y.coerceIn(area.top.toFloat(), (area.bottom - 1).toFloat())
        fun spread(value: Long, ratio: Double) = (value * (1 + (random.nextDouble() * 2 - 1) * ratio)).roundToLong()
        val (dx, dy) = offset()
        if (step.path.isEmpty()) {
            val (x1, y1, x2, y2) = step.points(screen)
            if (x1 == x2 && y1 == y2) {
                // タップ (80 ms) は 60〜110 ms、長押しは ±8%。
                val ms = if (step.ms < 500) TAP_MIN_MS + (random.nextDouble() * (TAP_MAX_MS - TAP_MIN_MS)).roundToLong()
                    else spread(step.ms, 0.08).coerceIn(500, MANUAL_MAX_MS)
                val x = clampX(x1 + dx); val y = clampY(y1 + dy)
                return step.copy(unit = "px", x1 = x, y1 = y, x2 = x, y2 = y, ms = ms, easing = "linear",
                    gapMs = if (step.taps > 1) spread(step.gapMs, 0.15).coerceIn(40, 300) else step.gapMs)
            }
            return step.copy(unit = "px", path = swipe(x1 + dx, y1 + dy, x2, y2, step.ms, radius, random)
                .map { it.copy(x = clampX(it.x), y = clampY(it.y)) }, easing = "linear").let { swiped ->
                swiped.copy(x1 = swiped.path.first().x, y1 = swiped.path.first().y,
                    x2 = swiped.path.last().x, y2 = swiped.path.last().y, ms = swiped.path.last().ms)
            }
        }
        val tracks = step.tracks(screen).map { track -> track.map { it.copy(x = clampX(it.x + dx), y = clampY(it.y + dy)) } }
        val first = when {
            tracks.size != 1 -> tracks[0]
            step.waypoints -> waypoints(tracks[0], radius, random)
            else -> retime(tracks[0], radius, random)
        }.map { it.copy(x = clampX(it.x), y = clampY(it.y)) }
        return step.copy(unit = "px", x1 = first.first().x, y1 = first.first().y, x2 = first.last().x, y2 = first.last().y,
            path = first, secondPath = tracks.getOrNull(1).orEmpty())
    }

    /**
     * 直線のスワイプを人の線にする。終点を少しずらし、横へ弓なりに曲げ、進み方は動き出しと止まり際が遅い形
     * (余弦) に小さな揺れを足す (後戻りはしない)。長さは ±8% (手動の上限 3 秒は超えない)。
     */
    private fun swipe(x1: Float, y1: Float, endX: Float, endY: Float, ms: Long, radius: Float, random: Random): List<ActionGesture.Point> {
        val end = random.nextDouble() * 2 * PI
        val x2 = endX + (cos(end) * radius * 0.6).toFloat()
        val y2 = endY + (sin(end) * radius * 0.6).toFloat()
        val duration = (ms * (1 + (random.nextDouble() * 2 - 1) * 0.08)).roundToLong().coerceIn(1, maxOf(ms, MANUAL_MAX_MS))
        val length = hypot(x2 - x1, y2 - y1)
        val bow = ((random.nextDouble() * 2 - 1) * BOW_RATIO * length).toFloat().coerceIn(-MAX_BOW_PX, MAX_BOW_PX)
        val nx = if (length > 0) -(y2 - y1) / length else 0f
        val ny = if (length > 0) (x2 - x1) / length else 0f
        val wobble = random.nextDouble() * 0.04
        val phase = random.nextDouble() * 2 * PI
        val times = ((0L until duration step ActionGesture.SAMPLE_MS).toList() + duration).distinct()
        var reached = 0.0
        return times.mapIndexed { i, time ->
            val t = time.toDouble() / duration
            val eased = (1 - cos(PI * t)) / 2 + wobble * sin(2 * PI * t * 2 + phase) * sin(PI * t)
            reached = if (i == times.lastIndex) 1.0 else maxOf(reached, eased.coerceIn(0.0, 1.0))
            val s = reached.toFloat()
            val side = bow * sin(PI * s).toFloat()
            ActionGesture.Point(x1 + (x2 - x1) * s + nx * side, y1 + (y2 - y1) * s + ny * side, time)
        }
    }

    /**
     * `drag` の通過点を人の線にする。点は必ず通る (パズルのように、通るマスが決まっている画面のため)。
     * 区間ごとに長さの 4% (最大 12 px) まで弓なりにし、進み方は `u - k·sin(2πu)/2π` (k = 0.5〜0.8) で
     * 点の近くを遅く・区間の中ほどを速くする (k < 1 なので止まらず後戻りもしない)。区間の時間は ±20%
     * (合計は同じ)。最後に角を最大 [MAX_CORNER_PX] だけ丸め、0.5 px ほど震わせる。
     */
    private fun waypoints(track: List<ActionGesture.Point>, radius: Float, random: Random): List<ActionGesture.Point> {
        if (track.size < 2) return track
        val start = track.first().ms
        val total = track.last().ms - start
        val weights = track.zipWithNext { a, b -> (b.ms - a.ms).toDouble() * (1 + (random.nextDouble() * 2 - 1) * 0.2) }
        val sum = weights.sum().takeIf { it > 0 } ?: return track
        // 点は 16 ms ごと。長いなぞりでも点の数が上限 (2048) を超えないよう間隔を広げる。
        val sample = maxOf(ActionGesture.SAMPLE_MS, total / 1800 + 1)
        val bowCap = minOf(12f, radius * 2)
        val out = mutableListOf(track.first())
        var segmentStart = start.toDouble()
        for (i in 0 until track.lastIndex) {
            val a = track[i]; val b = track[i + 1]
            val segmentEnd = if (i == track.lastIndex - 1) track.last().ms.toDouble() else segmentStart + weights[i] / sum * total
            val length = hypot(b.x - a.x, b.y - a.y)
            val nx = if (length > 0) -(b.y - a.y) / length else 0f
            val ny = if (length > 0) (b.x - a.x) / length else 0f
            val bow = ((random.nextDouble() * 2 - 1) * 0.04 * length).toFloat().coerceIn(-bowCap, bowCap)
            val k = 0.5 + random.nextDouble() * 0.3
            var time = out.last().ms + sample
            while (time < segmentEnd.roundToLong()) {
                val u = (time - segmentStart) / (segmentEnd - segmentStart)
                val s = (u - k * sin(2 * PI * u) / (2 * PI)).toFloat()
                val side = bow * sin(PI * s).toFloat()
                out += ActionGesture.Point(a.x + (b.x - a.x) * s + nx * side, a.y + (b.y - a.y) * s + ny * side, time)
                time += sample
            }
            val end = segmentEnd.roundToLong().coerceAtLeast(out.last().ms + 1)
            out += ActionGesture.Point(b.x, b.y, if (i == track.lastIndex - 1) track.last().ms else end)
            segmentStart = segmentEnd
        }
        // 時刻は増え続けること (最後の点は元の終わりの時刻)。詰まった点は落とす。
        val timed = out.dropLast(1).filter { it.ms < track.last().ms }
            .fold(mutableListOf<ActionGesture.Point>()) { list, p -> if (list.isEmpty() || p.ms > list.last().ms) list += p; list }
        timed += out.last()
        val corner = minOf(MAX_CORNER_PX, radius / 2)
        val tremor = minOf(0.5f, radius / 8)
        return timed.mapIndexed { i, p ->
            if (i == 0 || i == timed.lastIndex) return@mapIndexed p
            val mx = (timed[i - 1].x + timed[i + 1].x) / 2 - p.x
            val my = (timed[i - 1].y + timed[i + 1].y) / 2 - p.y
            val shift = hypot(mx, my)
            val scale = if (shift > corner) corner / shift else 1f
            p.copy(x = p.x + mx * scale + ((random.nextDouble() * 2 - 1) * tremor).toFloat(),
                y = p.y + my * scale + ((random.nextDouble() * 2 - 1) * tremor).toFloat())
        }
    }
    private const val MAX_CORNER_PX = 4f

    /** 1 本指の軌跡の区間ごとの時間を ±20% 揺らす。始まりと終わりの時刻は同じ。途中の点を 1 px ほど震わせる。 */
    private fun retime(track: List<ActionGesture.Point>, radius: Float, random: Random): List<ActionGesture.Point> {
        if (track.size < 3) return track
        val total = track.last().ms - track.first().ms
        val gaps = track.zipWithNext { a, b -> (b.ms - a.ms).toDouble() * (1 + (random.nextDouble() * 2 - 1) * 0.2) }
        val sum = gaps.sum().takeIf { it > 0 } ?: return track
        val tremor = minOf(1.5f, radius / 4)
        var elapsed = 0.0
        var previous = track.first().ms
        return track.mapIndexed { i, point ->
            if (i == 0) return@mapIndexed point
            elapsed += gaps[i - 1]
            val last = i == track.lastIndex
            // どの区間も 1 ms 以上。最後の点は元の時刻のまま。
            val time = if (last) track.last().ms
                else (track.first().ms + elapsed / sum * total).roundToLong().coerceIn(previous + 1, track.last().ms - (track.lastIndex - i))
            previous = time
            if (last) point.copy(ms = time) else point.copy(ms = time,
                x = point.x + ((random.nextDouble() * 2 - 1) * tremor).toFloat(), y = point.y + ((random.nextDouble() * 2 - 1) * tremor).toFloat())
        }
    }
}
