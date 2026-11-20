package com.zerotoship.z2term.automation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 人らしい操作 (`human=1..10`、`on` は 5。1.0.184、強さは 1.0.186): 機械的な操作 (同じ点・直線・一定の速さ・
 * 同じ待ち) を、人の指に近い揺らぎのある操作に変える。揺らぎの大きさは強さ [level] に比例する
 * (下の数値は強さ 2 のときのもの。強さ 10 ならその 5 倍)。
 *
 * - タップ・長押し・ダブルタップ: 押す位置を画面の短辺の 0.6% までずらし、押す長さと間隔をばらつかせる。
 * - スワイプ (直線): 長さの 5% まで弓なりにし、動き出しと止まり際を遅く、途中の速さも揺らす。終点も少しずらす。
 * - なぞる (`drag`): 区間ごとに長さの 6% まで弓なりにし、通過点の近くで速さを落とし (止まらない)、区間の中でも
 *   速さを揺らす (1〜3 回の波)。角は道のりに
 *   沿って 8 px ほどの幅でならして丸める。通過点からの外れは強さ×2 px 程度 (強さ 10 で 20 px)。
 * - スワイプとなぞるには、ゆっくりしたふらつき (1 px) と細かい震え (0.5 px) を足す。始点と終点では 0。
 * - 記録した軌跡: 形はそのまま位置だけずらし、1 本指なら区間ごとの時間を ±20% 揺らす (全体の長さは同じ)。
 * - wait: ±10% (最大 ±300 ms) ばらつかせる。
 *
 * 点は [area] (前面の窓の中) に収める。座標は px の手順にして返す。
 */
internal object ActionHumanize {
    /** 窓の範囲 (px、right / bottom は含まない)。 */
    data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int)

    const val MAX_LEVEL = 10
    /** `human=on` の強さ。 */
    const val DEFAULT_LEVEL = 5
    private const val MANUAL_MAX_MS = 3000L

    /** 強さ 2 を 1 とした倍率。 */
    private fun scale(level: Int) = level.coerceIn(0, MAX_LEVEL) / 2.0

    fun waitMs(ms: Long, random: Random, level: Int = DEFAULT_LEVEL): Long {
        val f = scale(level)
        if (ms <= 0 || f <= 0) return ms
        val spread = minOf(ms * minOf(0.1 * f, 0.5), 300.0 * f)
        return (ms + (random.nextDouble() * 2 - 1) * spread).roundToLong().coerceAtLeast(0)
    }

    fun stroke(step: ActionDefinition.Step.Stroke, screen: ActionDefinition.Screen, area: Area, random: Random,
        level: Int = DEFAULT_LEVEL): ActionDefinition.Step.Stroke {
        val f = scale(level)
        if (f <= 0) return step
        val radius = maxOf(2f, (minOf(screen.width, screen.height) * 0.003 * f).toFloat())
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
                // タップ (80 ms) は 60〜(110 + 強さに応じて) ms、長押しは ±8%×倍率、ダブルタップの間隔は ±15%×倍率。
                val tapMax = 110 + (15 * (f - 1)).coerceAtLeast(0.0)
                val ms = if (step.ms < 500) 60 + (random.nextDouble() * (tapMax - 60)).roundToLong()
                    else spread(step.ms, minOf(0.08 * f, 0.3)).coerceIn(500, MANUAL_MAX_MS)
                val x = clampX(x1 + dx); val y = clampY(y1 + dy)
                return step.copy(unit = "px", x1 = x, y1 = y, x2 = x, y2 = y, ms = ms, easing = "linear",
                    gapMs = if (step.taps > 1) spread(step.gapMs, minOf(0.15 * f, 0.5)).coerceIn(40, 300) else step.gapMs)
            }
            val path = swipe(x1 + dx, y1 + dy, x2, y2, step.ms, radius, f, random).map { it.copy(x = clampX(it.x), y = clampY(it.y)) }
            return step.copy(unit = "px", path = path, easing = "linear", x1 = path.first().x, y1 = path.first().y,
                x2 = path.last().x, y2 = path.last().y, ms = path.last().ms)
        }
        val tracks = step.tracks(screen).map { track -> track.map { it.copy(x = clampX(it.x + dx), y = clampY(it.y + dy)) } }
        val first = when {
            tracks.size != 1 -> tracks[0]
            step.waypoints -> waypoints(tracks[0], f, random)
            else -> retime(tracks[0], radius, f, random)
        }.map { it.copy(x = clampX(it.x), y = clampY(it.y)) }
        return step.copy(unit = "px", x1 = first.first().x, y1 = first.first().y, x2 = first.last().x, y2 = first.last().y,
            path = first, secondPath = tracks.getOrNull(1).orEmpty())
    }

    /**
     * 直線のスワイプを人の線にする。終点を少しずらし、横へ弓なりに曲げ、進み方は動き出しと止まり際が遅い形
     * (余弦) に小さな揺れを足す (後戻りはしない)。長さは ±8%×倍率 (手動の上限 3 秒は超えない)。
     */
    private fun swipe(x1: Float, y1: Float, endX: Float, endY: Float, ms: Long, radius: Float, f: Double,
        random: Random): List<ActionGesture.Point> {
        val end = random.nextDouble() * 2 * PI
        val x2 = endX + (cos(end) * radius * 0.6).toFloat()
        val y2 = endY + (sin(end) * radius * 0.6).toFloat()
        val duration = (ms * (1 + (random.nextDouble() * 2 - 1) * minOf(0.08 * f, 0.3))).roundToLong()
            .coerceIn(1, maxOf(ms, MANUAL_MAX_MS))
        val length = hypot(x2 - x1, y2 - y1)
        val bow = ((random.nextDouble() * 2 - 1) * minOf(0.05 * f, 0.3) * length).toFloat()
            .coerceIn((-48 * f).toFloat(), (48 * f).toFloat())
        val nx = if (length > 0) -(y2 - y1) / length else 0f
        val ny = if (length > 0) (x2 - x1) / length else 0f
        val wobble = random.nextDouble() * minOf(0.04 * f, 0.15)
        val phase = random.nextDouble() * 2 * PI
        val times = ((0L until duration step ActionGesture.SAMPLE_MS).toList() + duration).distinct()
        var reached = 0.0
        val line = times.mapIndexed { i, time ->
            val t = time.toDouble() / duration
            val eased = (1 - cos(PI * t)) / 2 + wobble * sin(2 * PI * t * 2 + phase) * sin(PI * t)
            reached = if (i == times.lastIndex) 1.0 else maxOf(reached, eased.coerceIn(0.0, 1.0))
            val s = reached.toFloat()
            val side = bow * sin(PI * s).toFloat()
            ActionGesture.Point(x1 + (x2 - x1) * s + nx * side, y1 + (y2 - y1) * s + ny * side, time)
        }
        return shake(line, f, random)
    }

    /**
     * `drag` の通過点を人の線にする。区間ごとに長さの 6%×倍率 (最大 12 px×倍率) まで弓なりにし、進み方は
     * `u - k·sin(2πu)/2π` (k = 0.5〜0.8) で点の近くを遅く・区間の中ほどを速くする (k < 1 なので止まらず
     * 後戻りもしない)。区間の時間は ±20%×倍率 (合計は同じ)。角は道のりで前後 4 px×倍率の範囲をならして丸める。
     */
    private fun waypoints(track: List<ActionGesture.Point>, f: Double, random: Random): List<ActionGesture.Point> {
        if (track.size < 2) return track
        val start = track.first().ms
        val total = track.last().ms - start
        val jitter = minOf(0.2 * f, 0.45)
        val weights = track.zipWithNext { a, b -> (b.ms - a.ms).toDouble() * (1 + (random.nextDouble() * 2 - 1) * jitter) }
        val sum = weights.sum().takeIf { it > 0 } ?: return track
        // 点は 16 ms ごと。長いなぞりでも点の数が上限 (2048) を超えないよう間隔を広げる。
        val sample = maxOf(ActionGesture.SAMPLE_MS, total / 1800 + 1)
        val bowCap = (12 * f).toFloat()
        val out = mutableListOf(track.first())
        var segmentStart = start.toDouble()
        for (i in 0 until track.lastIndex) {
            val a = track[i]; val b = track[i + 1]
            val last = i == track.lastIndex - 1
            val segmentEnd = if (last) track.last().ms.toDouble() else segmentStart + weights[i] / sum * total
            val length = hypot(b.x - a.x, b.y - a.y)
            val nx = if (length > 0) -(b.y - a.y) / length else 0f
            val ny = if (length > 0) (b.x - a.x) / length else 0f
            val bow = ((random.nextDouble() * 2 - 1) * minOf(0.06 * f, 0.3) * length).toFloat().coerceIn(-bowCap, bowCap)
            val k = 0.5 + random.nextDouble() * 0.3
            // 区間の中の速さの揺れ (強さに比例)。進み具合は後戻りさせない。
            val wobble = random.nextDouble() * minOf(0.015 * f, 0.12)
            val cycles = 1 + random.nextInt(3)
            val phase = random.nextDouble() * 2 * PI
            var reached = 0.0
            var time = out.last().ms + sample
            while (time < segmentEnd.roundToLong()) {
                val u = (time - segmentStart) / (segmentEnd - segmentStart)
                val eased = u - k * sin(2 * PI * u) / (2 * PI) + wobble * sin(2 * PI * cycles * u + phase) * sin(PI * u)
                reached = maxOf(reached, eased.coerceIn(0.0, 1.0))
                val s = reached.toFloat()
                val side = bow * sin(PI * s).toFloat()
                out += ActionGesture.Point(a.x + (b.x - a.x) * s + nx * side, a.y + (b.y - a.y) * s + ny * side, time)
                time += sample
            }
            out += ActionGesture.Point(b.x, b.y, if (last) track.last().ms else segmentEnd.roundToLong().coerceAtLeast(out.last().ms + 1))
            segmentStart = segmentEnd
        }
        // 時刻は増え続けること (最後の点は元の終わりの時刻)。詰まった点は落とす。
        val timed = out.dropLast(1).filter { it.ms < track.last().ms }
            .fold(mutableListOf<ActionGesture.Point>()) { list, p -> if (list.isEmpty() || p.ms > list.last().ms) list += p; list }
        timed += out.last()
        return shake(round(timed, (4 * f).toFloat()), f, random)
    }

    /**
     * 角を丸める。各点を、道のりで前後 [reach] px (始点・終点に近いほど狭く) の点の重み付き平均へ置き換える。
     * 始点と終点は動かない。時刻はそのまま。
     */
    private fun round(points: List<ActionGesture.Point>, reach: Float): List<ActionGesture.Point> {
        if (points.size < 3 || reach <= 0f) return points
        val arc = FloatArray(points.size)
        for (i in 1 until points.size) arc[i] = arc[i - 1] + hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        return points.mapIndexed { i, p ->
            val h = minOf(reach, arc[i], arc.last() - arc[i])
            if (i == 0 || i == points.lastIndex || h <= 0f) return@mapIndexed p
            var sx = 0f; var sy = 0f; var sw = 0f
            var j = i
            while (j > 0 && arc[i] - arc[j - 1] <= h) j--
            while (j < points.size && arc[j] - arc[i] <= h) {
                val w = 1 - abs(arc[j] - arc[i]) / h
                sx += points[j].x * w; sy += points[j].y * w; sw += w
                j++
            }
            if (sw <= 0f) p else p.copy(x = sx / sw, y = sy / sw)
        }
    }

    /**
     * 手のふらつき: ゆっくりした揺れ (0.5 px×倍率、周期 0.3〜0.9 秒) と細かい震え (0.25 px×倍率)。
     * 始点と終点では 0 になるよう、時間の進み具合で弱める。
     */
    private fun shake(points: List<ActionGesture.Point>, f: Double, random: Random): List<ActionGesture.Point> {
        if (points.size < 3) return points
        val drift = (0.5 * f).toFloat()
        val tremor = (0.25 * f).toFloat()
        val px = 300 + random.nextDouble() * 600; val py = 300 + random.nextDouble() * 600
        val ax = random.nextDouble() * 2 * PI; val ay = random.nextDouble() * 2 * PI
        val start = points.first().ms; val span = (points.last().ms - start).coerceAtLeast(1)
        return points.mapIndexed { i, p ->
            if (i == 0 || i == points.lastIndex) return@mapIndexed p
            val t = (p.ms - start).toDouble()
            val taper = sin(PI * t / span).toFloat()
            p.copy(x = p.x + taper * (drift * sin(2 * PI * t / px + ax).toFloat() + tremor * (random.nextFloat() * 2 - 1)),
                y = p.y + taper * (drift * sin(2 * PI * t / py + ay).toFloat() + tremor * (random.nextFloat() * 2 - 1)))
        }
    }

    /** 1 本指の記録した軌跡の区間ごとの時間を ±20%×倍率 揺らす。始まりと終わりの時刻は同じ。途中の点を少し震わせる。 */
    private fun retime(track: List<ActionGesture.Point>, radius: Float, f: Double, random: Random): List<ActionGesture.Point> {
        if (track.size < 3) return track
        val total = track.last().ms - track.first().ms
        val jitter = minOf(0.2 * f, 0.45)
        val gaps = track.zipWithNext { a, b -> (b.ms - a.ms).toDouble() * (1 + (random.nextDouble() * 2 - 1) * jitter) }
        val sum = gaps.sum().takeIf { it > 0 } ?: return track
        val tremor = minOf((1.5 * f).toFloat(), radius / 4)
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
