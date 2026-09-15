package com.zerotoship.z2term.automation

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Positions and elapsed times for one continuous touch, through the actual lift-off. */
internal object ActionGesture {
    const val MAX_MS = 30_000L
    const val MAX_POINTS = 2048
    const val SAMPLE_MS = 16L
    val EASINGS = listOf("linear", "accelerate", "decelerate")
    data class Point(val x: Float, val y: Float, val ms: Long)

    fun parse(unit: String, text: String, startsAtZero: Boolean = true): List<Point> {
        require(unit in setOf("px", "percent")) { "Coordinate unit must be px or percent" }
        val maximum = if (unit == "percent") 100f else 32767f
        val tokens = text.trim().split(Regex("\\s+"))
        require(tokens.size in 2..MAX_POINTS) { "Freehand swipe requires 2..$MAX_POINTS points" }
        val points = tokens.map { token ->
            val parts = token.split(',')
            require(parts.size == 3) { "Freehand points use X,Y,MS" }
            fun coordinate(index: Int) = parts[index].toFloatOrNull()?.also {
                require(it.isFinite() && it in 0f..maximum) { "Invalid freehand coordinate" }
            } ?: throw IllegalArgumentException("Invalid freehand coordinate")
            val ms = parts[2].toLongOrNull()
            require(ms != null && ms in 0..MAX_MS) { "Freehand time must be 0..$MAX_MS ms" }
            Point(coordinate(0), coordinate(1), ms)
        }
        require((!startsAtZero || points.first().ms == 0L) && points.zipWithNext().all { (a, b) -> a.ms < b.ms }) {
            "Freehand time must start at zero and increase at each point"
        }
        return points
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long, easing: String): List<Point> {
        if (easing == "linear") return listOf(Point(x1, y1, 0), Point(x2, y2, ms))
        val times = (0L until ms step SAMPLE_MS).toList() + ms
        return times.map { time ->
            val t = time.toFloat() / ms
            val distance = if (easing == "accelerate") t * t else 1 - (1 - t) * (1 - t)
            Point(x1 + (x2 - x1) * distance, y1 + (y2 - y1) * distance, time)
        }
    }

    fun coordinate(value: Float): String = BigDecimal(value.toString()).setScale(3, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
    fun encode(points: List<Point>) = points.joinToString(" ") { "${coordinate(it.x)},${coordinate(it.y)},${it.ms}" }
    fun convert(points: List<Point>, from: String, to: String, screen: ActionDefinition.Screen): List<Point> =
        points.map {
            fun axis(value: Float, extent: Int) = when {
                from == to -> value
                to == "percent" -> value * 100 / (extent - 1).coerceAtLeast(1)
                else -> value * (extent - 1) / 100
            }
            it.copy(x = axis(it.x, screen.width), y = axis(it.y, screen.height))
        }
    fun speed(points: List<Point>): Float = if (points.size < 2 || points.last().ms == 0L) 0f else
        points.zipWithNext().sumOf { (a, b) -> hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) }
            .toFloat() * 1000 / points.last().ms

    /** Sample at most once per 16 ms; always retain the real release time and position. */
    class Recording {
        private val samples = mutableListOf<Point>()
        val points: List<Point> get() = samples
        fun add(x: Float, y: Float, elapsedMs: Long, up: Boolean = false) {
            require(elapsedMs in 0..MAX_MS) { "Freehand swipe exceeds $MAX_MS ms" }
            val time = if (up) elapsedMs.coerceAtLeast(1) else elapsedMs
            val previous = samples.lastOrNull()
            if (previous != null) {
                if (time < previous.ms || (!up && time - previous.ms < SAMPLE_MS)) return
                if (time == previous.ms) { samples[samples.lastIndex] = Point(x, y, time); return }
            }
            require(samples.size < MAX_POINTS) { "Too many freehand points" }
            samples += Point(x, y, time)
        }
    }
}
