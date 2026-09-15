package com.zerotoship.z2term.automation

import kotlin.math.roundToInt

/** A contact keeps its ID until UP; two fingers may arrive and leave at different times. */
internal object ActionGesturePlan {
    data class Part(val id: Int, val points: List<ActionGesture.Point>, val continues: Boolean, val downOnly: Boolean = false) {
        val startMs get() = points.first().ms
        val endMs get() = points.last().ms
        val durationMs get() = if (downOnly) 1L else endMs - startMs
    }
    data class Segment(val parts: List<Part>, val initial: Boolean = false) {
        val startMs get() = parts.minOf { it.startMs }
        val endMs get() = parts.maxOf { it.endMs }
    }

    fun create(tracks: List<List<ActionGesture.Point>>, screen: ActionDefinition.Screen): List<Segment> {
        require(tracks.size in 1..2 && tracks.all { it.size >= 2 })
        require(tracks.first().first().ms == 0L)
        require(tracks.all { track -> track.zipWithNext().all { (a, b) -> a.ms < b.ms } })
        val pixels = tracks.map { track -> track.map { it.copy(
            x = it.x.roundToInt().coerceIn(0, screen.width - 1).toFloat(),
            y = it.y.roundToInt().coerceIn(0, screen.height - 1).toFloat()) } }
        val join = pixels.maxOf { it.first().ms }
        require(pixels.all { it.last().ms > join }) { "Separate non-overlapping touches into separate steps" }
        fun at(track: List<ActionGesture.Point>, time: Long): ActionGesture.Point {
            track.firstOrNull { it.ms == time }?.let { return it }
            val end = track.indexOfFirst { it.ms > time }
            require(end > 0)
            val a = track[end - 1]; val b = track[end]
            val f = (time - a.ms).toFloat() / (b.ms - a.ms)
            return ActionGesture.Point((a.x + (b.x - a.x) * f).roundToInt().toFloat(),
                (a.y + (b.y - a.y) * f).roundToInt().toFloat(), time)
        }
        // Android continuations cannot introduce a new pointer. Introduce both in the first
        // description, preserving their DOWN times, then use timed continuations for motion.
        val initial = pixels.mapIndexed { id, track ->
            val prefix = track.filter { it.ms < join } + at(track, join)
            Part(id, prefix, continues = true, downOnly = prefix.size == 1)
        }
        val result = mutableListOf(Segment(initial, initial = true))
        val boundaries = (listOf(join) + pixels.flatten().map { it.ms }.filter { it > join }).distinct().sorted()
        boundaries.zipWithNext().forEach { (start, end) ->
            val count = ((end - start + 49) / 50).toInt()
            var left = start
            for (part in 1..count) {
                val right = start + (end - start) * part / count
                val active = pixels.indices.filter { pixels[it].last().ms > left }
                val parts = active.map { id ->
                    Part(id, listOf(at(pixels[id], left), at(pixels[id], right)),
                        continues = pixels[id].last().ms > right)
                }
                val moves = parts.any { it.points.first().let { a -> it.points.last().let { b -> a.x != b.x || a.y != b.y } } }
                // A stationary continued description has no events and is rejected by Android.
                if (moves || parts.any { !it.continues }) result += Segment(parts)
                left = right
            }
        }
        return result
    }
}
