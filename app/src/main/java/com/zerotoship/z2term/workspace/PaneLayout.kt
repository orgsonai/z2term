package com.zerotoship.z2term.workspace

/**
 * How a group's panes are split, with vertical and horizontal splits mixed (1.0.177).
 *
 * A [Split] lays its children side by side ([horizontal]) or top to bottom, each with a share in
 * [weights] (summing to 1). A child may itself be a split in the other direction, so any mix of
 * rows and columns can be built by dragging a pane onto the edge of another ([dock]).
 * Pure value, unit-tested without Compose.
 */
internal sealed interface PaneNode {
    data class Leaf(val id: String) : PaneNode
    data class Split(val horizontal: Boolean, val children: List<PaneNode>, val weights: List<Float>) : PaneNode
}

/** Where a dragged pane lands on another: beside it on one side, or in its place (the two swap). */
internal enum class DockSide { LEFT, RIGHT, TOP, BOTTOM, CENTER }

/** Smallest share of one pane, relative to an equal share, so dragging a divider never hides a pane. */
internal const val MIN_SHARE_OF_EQUAL = 0.4f

/** Tab ids in reading order (depth first). This is also the group's member order. */
internal fun PaneNode.ids(): List<String> = when (this) {
    is PaneNode.Leaf -> listOf(id)
    is PaneNode.Split -> children.flatMap { it.ids() }
}

/** One row or column of [ids] in order, with [weights] when they match (the layout before 1.0.177). */
internal fun flatLayout(ids: List<String>, horizontal: Boolean, weights: List<Float>): PaneNode? = when (ids.size) {
    0 -> null
    1 -> PaneNode.Leaf(ids[0])
    else -> PaneNode.Split(horizontal, ids.map { PaneNode.Leaf(it) },
        if (weights.size == ids.size) weights else equalWeights(ids.size))
}

/**
 * Takes [id] out. The parent's other children share its space; a split left with one child is
 * replaced by that child, and a split of the same direction as its parent is merged into it.
 */
internal fun PaneNode.without(id: String): PaneNode? = when (this) {
    is PaneNode.Leaf -> if (this.id == id) null else this
    is PaneNode.Split -> {
        val kept = children.mapIndexedNotNull { i, c -> c.without(id)?.let { it to weights.getOrElse(i) { 0f } } }
        when (kept.size) {
            0 -> null
            1 -> kept[0].first
            else -> PaneNode.Split(horizontal, kept.map { it.first }, normalize(kept.map { it.second })).merged()
        }
    }
}

/** Adds [id] after everything, as one more child of the outermost split (a new row or column). */
internal fun PaneNode?.appended(id: String, horizontal: Boolean): PaneNode = when (this) {
    null -> PaneNode.Leaf(id)
    is PaneNode.Leaf -> PaneNode.Split(horizontal, listOf(this, PaneNode.Leaf(id)), listOf(0.5f, 0.5f))
    is PaneNode.Split -> {
        val n = children.size
        PaneNode.Split(this.horizontal, children + PaneNode.Leaf(id), weights.map { it * n / (n + 1) } + (1f / (n + 1)))
    }
}

/**
 * Puts [dragged] on [side] of [target]. Beside a pane whose parent already runs that way it becomes
 * one more child there, taking half of [target]'s share; otherwise [target] is split in two.
 * [CENTER][DockSide.CENTER] swaps the two panes. Returns null when either is missing or they are the same.
 */
internal fun PaneNode.dock(dragged: String, target: String, side: DockSide): PaneNode? {
    val ids = ids()
    if (dragged == target || dragged !in ids || target !in ids) return null
    if (side == DockSide.CENTER) return mapLeaves { when (it) { dragged -> target; target -> dragged; else -> it } }
    val horizontal = side == DockSide.LEFT || side == DockSide.RIGHT
    val before = side == DockSide.LEFT || side == DockSide.TOP
    val rest = without(dragged) ?: return null
    return rest.insertBeside(target, dragged, horizontal, before).merged()
}

private fun PaneNode.insertBeside(target: String, dragged: String, horizontal: Boolean, before: Boolean): PaneNode = when (this) {
    is PaneNode.Leaf -> if (id != target) this else {
        val pair = if (before) listOf(PaneNode.Leaf(dragged), this) else listOf(this, PaneNode.Leaf(dragged))
        PaneNode.Split(horizontal, pair, listOf(0.5f, 0.5f))
    }
    is PaneNode.Split -> {
        val at = children.indexOfFirst { it is PaneNode.Leaf && it.id == target }
        if (at >= 0 && this.horizontal == horizontal) {
            val half = weights[at] / 2
            val kids = children.toMutableList()
            val w = weights.toMutableList()
            w[at] = half
            val insertAt = if (before) at else at + 1
            kids.add(insertAt, PaneNode.Leaf(dragged))
            w.add(insertAt, half)
            PaneNode.Split(this.horizontal, kids, w)
        } else PaneNode.Split(this.horizontal, children.map { it.insertBeside(target, dragged, horizontal, before) }, weights)
    }
}

/** The same shape with the tabs placed in [order] (reading order). Used when members are reordered in a list. */
internal fun PaneNode.withOrder(order: List<String>): PaneNode {
    var i = 0
    fun walk(n: PaneNode): PaneNode = when (n) {
        is PaneNode.Leaf -> PaneNode.Leaf(order.getOrElse(i++) { n.id })
        is PaneNode.Split -> PaneNode.Split(n.horizontal, n.children.map { walk(it) }, n.weights)
    }
    return walk(this)
}

/**
 * Drags the divider after child [divider] of the split at [path] (child indexes from the outermost
 * split) by [delta], a fraction of that split's extent. Only the two neighbours trade share, and
 * neither goes below [MIN_SHARE_OF_EQUAL] of an equal share. Returns null when nothing changes.
 */
internal fun PaneNode.resized(path: List<Int>, divider: Int, delta: Float): PaneNode? {
    if (this !is PaneNode.Split || !delta.isFinite()) return null
    if (path.isNotEmpty()) {
        val i = path[0]
        val child = children.getOrNull(i)?.resized(path.drop(1), divider, delta) ?: return null
        return copy(children = children.toMutableList().apply { this[i] = child })
    }
    val n = children.size
    if (divider !in 0 until n - 1 || weights.size != n) return null
    val min = MIN_SHARE_OF_EQUAL / n
    val a = weights[divider]
    val b = weights[divider + 1]
    val newA = (a + delta).coerceIn(min, a + b - min)
    return copy(weights = weights.toMutableList().apply { this[divider] = newA; this[divider + 1] = a + b - newA })
}

/** A split whose child runs the same way absorbs that child's children, scaled by its share. */
private fun PaneNode.merged(): PaneNode {
    if (this !is PaneNode.Split) return this
    val kids = mutableListOf<PaneNode>()
    val w = mutableListOf<Float>()
    children.forEachIndexed { i, c ->
        val m = c.merged()
        val share = weights.getOrElse(i) { 0f }
        if (m is PaneNode.Split && m.horizontal == horizontal) {
            kids += m.children
            w += m.weights.map { it * share }
        } else {
            kids += m
            w += share
        }
    }
    return if (kids.size == 1) kids[0] else PaneNode.Split(horizontal, kids, normalize(w))
}

private fun PaneNode.mapLeaves(f: (String) -> String): PaneNode = when (this) {
    is PaneNode.Leaf -> PaneNode.Leaf(f(id))
    is PaneNode.Split -> copy(children = children.map { it.mapLeaves(f) })
}

private fun normalize(w: List<Float>): List<Float> {
    val sum = w.sum()
    return if (sum <= 0f || !sum.isFinite()) equalWeights(w.size) else w.map { it / sum }
}

/**
 * Which side of a pane at ([left], [top], [width], [height]) the point ([x], [y]) is over, or null
 * outside it. The outer quarter towards each edge means that side; the middle means swap.
 */
internal fun dockSideAt(x: Float, y: Float, left: Float, top: Float, width: Float, height: Float): DockSide? {
    if (width <= 0f || height <= 0f) return null
    val fx = (x - left) / width
    val fy = (y - top) / height
    if (fx !in 0f..1f || fy !in 0f..1f) return null
    val edges = listOf(DockSide.LEFT to fx, DockSide.RIGHT to 1 - fx, DockSide.TOP to fy, DockSide.BOTTOM to 1 - fy)
    val (side, distance) = edges.minBy { it.second }
    return if (distance > 0.25f) DockSide.CENTER else side
}
