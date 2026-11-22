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
 * 人らしい操作 (`human=1..20`、`on` は 5。1.0.184、強さは 1.0.186、20 までは 1.0.187): 機械的な操作 (同じ点・直線・一定の速さ・
 * 同じ待ち) を、人の指に近い揺らぎのある操作に変える。揺らぎの大きさは強さ [level] に比例する
 * (下の数値は強さ 2 のときのもの。強さ 10 ならその 5 倍、20 なら 10 倍)。強さ 11 以上は動きが崩れてもよい前提で、
 * 割合の揺れの頭打ちも広げてある (区間の時間が 0 に近づくなど、操作として成り立つ範囲だけは守る)。
 *
 * 揺らすものは 3 つに分かれ、見出し `jitter=` で選ぶ ([Parts]、1.0.203。既定は 3 つとも):
 * - **速さ** (`speed`): 押す長さ・間隔、スワイプとなぞる時間と途中の速さの揺れ、wait (±10%、最大 ±300 ms)、打鍵の間隔。
 * - **線** (`path`): スワイプとなぞるの弓なり (長さの 5〜6%)・角の丸め・ふらつきと震え、記録した軌跡の震え。
 * - **到達点** (`aim`): 押す点・始点・終点・なぞるの通過点を 1 つずつ、画面の短辺の 0.3% (強さ 2) の円の中でずらす。
 *   記録した軌跡は形を保ったまま全体をずらす。手順の行末の `exact` はその手順だけ到達点を揺らさない。
 *
 * ⛔ **揺らすものを全部切っても、動き出しは止まった状態から加速し、終点では止まるまで減速する** (1.0.203)。
 * 人の指は一定の速さで動き出したり止まったりしない。なぞるの通過点では止まらず、少しだけ速さを落とす。
 *
 * 点は [area] (前面の窓の中) に収める。座標は px の手順にして返す。
 */
internal object ActionHumanize {
    /** 窓の範囲 (px、right / bottom は含まない)。 */
    data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** 何を揺らすか (`jitter=speed,path,aim` / `none`、1.0.203)。見出しが無ければ 3 つとも。 */
    data class Parts(val speed: Boolean = true, val path: Boolean = true, val aim: Boolean = true) {
        override fun toString() = listOfNotNull("speed".takeIf { speed }, "path".takeIf { path }, "aim".takeIf { aim })
            .joinToString(",").ifEmpty { "none" }
        companion object {
            val ALL = Parts()
            fun parse(value: String?): Parts {
                if (value == null) return ALL
                if (value == "none") return Parts(false, false, false)
                val words = value.split(',').map { it.trim() }
                require(words.isNotEmpty() && words.all { it in setOf("speed", "path", "aim") } && words.size == words.distinct().size) {
                    "jitter must be none or a comma list of speed, path, aim"
                }
                return Parts("speed" in words, "path" in words, "aim" in words)
            }
        }
    }

    const val MAX_LEVEL = 20
    /** `human=on` の強さ。 */
    const val DEFAULT_LEVEL = 5
    private const val MANUAL_MAX_MS = 3000L
    /** 動き出しの加速と止まり際の減速にかける時間の上限 (ms)。短いなぞりでは全体の 1/4 まで。 */
    private const val RAMP_MS = 160.0

    /** 強さ 2 を 1 とした倍率。 */
    private fun scale(level: Int) = level.coerceIn(0, MAX_LEVEL) / 2.0

    /** 人らしい打鍵で 1 文字ずつ入れる上限 (コードポイント)。長い文字は一度に入れる。 */
    const val MAX_TYPED_ONE_BY_ONE = 256

    /**
     * 次の文字を打つまでの間 (1.0.193)。[previous] は打ったばかりの文字。空白や句読点の後は少し長く止まり、
     * 強さが大きいほど間がばらつく (速さを揺らさないなら一定)。
     */
    fun keyMs(previous: Int, random: Random, level: Int = DEFAULT_LEVEL, parts: Parts = Parts.ALL): Long {
        val pause = Character.isWhitespace(previous) || previous.toChar() in ".,!?、。！？・"
        val base = if (pause) 220.0 else 110.0
        if (!parts.speed) return base.roundToLong()
        val ratio = (0.15 * scale(level)).coerceAtMost(0.9)
        return (base * (1 + (random.nextDouble() * 2 - 1) * ratio)).roundToLong().coerceAtLeast(20)
    }

    /** [text] を打っていく途中の文字 (1 文字ずつ長くなる。サロゲートペアは分けない)。 */
    fun prefixes(text: String): List<String> {
        val ends = mutableListOf<Int>()
        var i = 0
        while (i < text.length) { i = text.offsetByCodePoints(i, 1); ends += i }
        return ends.map { text.substring(0, it) }
    }

    fun waitMs(ms: Long, random: Random, level: Int = DEFAULT_LEVEL, parts: Parts = Parts.ALL): Long {
        val f = scale(level)
        if (ms <= 0 || f <= 0 || !parts.speed) return ms
        val spread = minOf(ms * minOf(0.1 * f, 0.9), 300.0 * f)
        return (ms + (random.nextDouble() * 2 - 1) * spread).roundToLong().coerceAtLeast(0)
    }

    fun stroke(step: ActionDefinition.Step.Stroke, screen: ActionDefinition.Screen, area: Area, random: Random,
        level: Int = DEFAULT_LEVEL, parts: Parts = Parts.ALL): ActionDefinition.Step.Stroke {
        val f = scale(level)
        if (f <= 0) return step
        // 到達点の揺れの半径。強さに比例する (強さ 5 で短辺の 0.75%、20 で 3%)。
        val radius = maxOf(2f, (minOf(screen.width, screen.height) * 0.003 * f).toFloat())
        val aim = parts.aim && !step.exact
        fun offset(): Pair<Float, Float> {
            if (!aim) return 0f to 0f
            val angle = random.nextDouble() * 2 * PI
            val r = radius * sqrt(random.nextDouble()).toFloat()
            return (cos(angle) * r).toFloat() to (sin(angle) * r).toFloat()
        }
        fun clampX(x: Float) = x.coerceIn(area.left.toFloat(), (area.right - 1).toFloat())
        fun clampY(y: Float) = y.coerceIn(area.top.toFloat(), (area.bottom - 1).toFloat())
        fun spread(value: Long, ratio: Double) = if (!parts.speed) value
            else (value * (1 + (random.nextDouble() * 2 - 1) * ratio)).roundToLong()
        if (step.path.isEmpty()) {
            val (x1, y1, x2, y2) = step.points(screen)
            val (dx, dy) = offset()
            if (x1 == x2 && y1 == y2) {
                // タップ (80 ms) は 60〜(110 + 強さに応じて) ms、長押しは ±8%×倍率、ダブルタップの間隔は ±15%×倍率。
                val tapMax = 110 + (15 * (f - 1)).coerceAtLeast(0.0)
                val ms = if (step.ms < 500) (if (parts.speed) 60 + (random.nextDouble() * (tapMax - 60)).roundToLong() else step.ms)
                    else spread(step.ms, minOf(0.08 * f, 0.6)).coerceIn(500, MANUAL_MAX_MS)
                val x = clampX(x1 + dx); val y = clampY(y1 + dy)
                return step.copy(unit = "px", x1 = x, y1 = y, x2 = x, y2 = y, ms = ms, easing = "linear",
                    gapMs = if (step.taps > 1) spread(step.gapMs, minOf(0.15 * f, 0.9)).coerceIn(40, 300) else step.gapMs)
            }
            val (ex, ey) = offset()
            val path = swipe(x1 + dx, y1 + dy, x2 + ex, y2 + ey, step.ms, f, parts, random)
                .map { it.copy(x = clampX(it.x), y = clampY(it.y)) }
            return step.copy(unit = "px", path = path, easing = "linear", x1 = path.first().x, y1 = path.first().y,
                x2 = path.last().x, y2 = path.last().y, ms = path.last().ms)
        }
        val tracks = step.tracks(screen)
        if (tracks.size == 1 && step.waypoints) {
            // なぞるの通過点は 1 つずつ別々にずらす (形ごとずらすと、各点へはぴったり届いてしまう)。
            val aimed = tracks[0].map { val (dx, dy) = offset(); it.copy(x = clampX(it.x + dx), y = clampY(it.y + dy)) }
            val line = waypoints(aimed, f, parts, random).map { it.copy(x = clampX(it.x), y = clampY(it.y)) }
            return step.copy(unit = "px", x1 = line.first().x, y1 = line.first().y, x2 = line.last().x, y2 = line.last().y,
                path = line, secondPath = emptyList())
        }
        // 記録した軌跡と 2 本指は、形を保ったまま全体をずらす。
        val (dx, dy) = offset()
        val moved = tracks.map { track -> track.map { it.copy(x = clampX(it.x + dx), y = clampY(it.y + dy)) } }
        val first = if (moved.size != 1) moved[0]
            else retime(moved[0], radius, f, parts, random).map { it.copy(x = clampX(it.x), y = clampY(it.y)) }
        return step.copy(unit = "px", x1 = first.first().x, y1 = first.first().y, x2 = first.last().x, y2 = first.last().y,
            path = first, secondPath = moved.getOrNull(1).orEmpty())
    }

    /**
     * 直線のスワイプを人の線にする。横へ弓なりに曲げ (線)、進み方は動き出しと止まり際が遅い形 (余弦。いつも) に
     * 小さな揺れを足す (速さ。後戻りはしない)。長さは ±8%×倍率 (速さ。手動の上限 3 秒は超えない)。
     */
    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long, f: Double, parts: Parts,
        random: Random): List<ActionGesture.Point> {
        val duration = if (!parts.speed) ms else (ms * (1 + (random.nextDouble() * 2 - 1) * minOf(0.08 * f, 0.6))).roundToLong()
            .coerceIn(1, maxOf(ms, MANUAL_MAX_MS))
        val length = hypot(x2 - x1, y2 - y1)
        val bow = if (!parts.path) 0f else ((random.nextDouble() * 2 - 1) * minOf(0.05 * f, 0.6) * length).toFloat()
            .coerceIn((-48 * f).toFloat(), (48 * f).toFloat())
        val nx = if (length > 0) -(y2 - y1) / length else 0f
        val ny = if (length > 0) (x2 - x1) / length else 0f
        val wobble = if (!parts.speed) 0.0 else random.nextDouble() * minOf(0.04 * f, 0.3)
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
        return if (parts.path) shake(line, f, random) else line
    }

    /**
     * `drag` の通過点を人の線にする。区間ごとに長さの 6%×倍率 (最大 12 px×倍率) まで弓なりにし (線)、進み方は
     * `u - k·sin(2πu)/2π` で点の近くを少し遅く・区間の中ほどを少し速くする (k = 0.25〜0.45 で、点では
     * 区間の平均の 55〜75% の速さ。止まらず後戻りもしない。速さを揺らさないなら k = 0.35)。区間の時間は
     * ±20%×倍率 (速さ。合計は同じ)。角は道のりで前後 4 px×倍率の範囲をならして丸める (線)。
     * 最後に [ramp] で、動き出しを止まった状態からの加速に、終点を止まるまでの減速にする。
     */
    private fun waypoints(track: List<ActionGesture.Point>, f: Double, parts: Parts, random: Random): List<ActionGesture.Point> {
        if (track.size < 2) return track
        val start = track.first().ms
        val total = track.last().ms - start
        val jitter = if (parts.speed) minOf(0.2 * f, 0.9) else 0.0
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
            val bow = if (!parts.path) 0f
                else ((random.nextDouble() * 2 - 1) * minOf(0.06 * f, 0.6) * length).toFloat().coerceIn(-bowCap, bowCap)
            val k = if (parts.speed) 0.25 + random.nextDouble() * 0.2 else 0.35
            // 区間の中の速さの揺れ (強さに比例)。進み具合は後戻りさせない。
            val wobble = if (parts.speed) random.nextDouble() * minOf(0.015 * f, 0.25) else 0.0
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
        val shaped = if (parts.path) shake(round(timed, (4 * f).toFloat()), f, random) else timed
        return ramp(shaped, sample, if (parts.speed) 0.8 + random.nextDouble() * 0.4 else 1.0)
    }

    /**
     * 動き出しと止まり際 (1.0.203)。時間の流れを付け替えて、始点では速さ 0 から sin² で加速し、終点では
     * sin² で速さ 0 まで減速する (幅は [RAMP_MS]×[stretch]、全体の 1/4 まで)。間は一定の割合で速める
     * ので、全体の長さと通る道筋は変わらない。[points] の時刻は「仮の時間」として読み、実際の時刻ごとに
     * 道筋の上を線形に補間する。
     */
    internal fun ramp(points: List<ActionGesture.Point>, sample: Long, stretch: Double = 1.0): List<ActionGesture.Point> {
        if (points.size < 2) return points
        val start = points.first().ms
        val total = (points.last().ms - start).toDouble()
        if (total <= 2) return points
        val r = minOf(RAMP_MS * stretch, total / 4)
        // 速さ v(t) を積んだもの。両端の sin² は面積 r/2 ずつ。
        val area = total - r
        fun integral(t: Double): Double = when {
            t <= r -> t / 2 - r / (2 * PI) * sin(PI * t / r)
            t >= total - r -> area - integral(total - t)
            else -> r / 2 + (t - r)
        }
        var j = 0
        fun at(virtual: Double): Pair<Float, Float> {
            val ms = start + virtual
            while (j < points.lastIndex - 1 && points[j + 1].ms < ms) j++
            val a = points[j]; val b = points[j + 1]
            val span = (b.ms - a.ms).toDouble()
            val u = if (span <= 0) 1.0 else ((ms - a.ms) / span).coerceIn(0.0, 1.0)
            return (a.x + (b.x - a.x) * u).toFloat() to (a.y + (b.y - a.y) * u).toFloat()
        }
        val times = ((0L until total.toLong() step sample).toList() + total.toLong()).distinct()
        return times.mapIndexed { i, t ->
            when (i) {
                0 -> points.first()
                times.lastIndex -> points.last()
                else -> {
                    val (x, y) = at(integral(t.toDouble()) / area * total)
                    ActionGesture.Point(x, y, start + t)
                }
            }
        }
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

    /** 1 本指の記録した軌跡の区間ごとの時間を ±20%×倍率 揺らす (速さ)。始まりと終わりの時刻は同じ。途中の点を少し震わせる (線)。 */
    private fun retime(track: List<ActionGesture.Point>, radius: Float, f: Double, parts: Parts, random: Random): List<ActionGesture.Point> {
        if (track.size < 3 || (!parts.speed && !parts.path)) return track
        val total = track.last().ms - track.first().ms
        val jitter = if (parts.speed) minOf(0.2 * f, 0.9) else 0.0
        val gaps = track.zipWithNext { a, b -> (b.ms - a.ms).toDouble() * (1 + (random.nextDouble() * 2 - 1) * jitter) }
        val sum = gaps.sum().takeIf { it > 0 } ?: return track
        val tremor = if (parts.path) minOf((1.5 * f).toFloat(), radius / 4) else 0f
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
