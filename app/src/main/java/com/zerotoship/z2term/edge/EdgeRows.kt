package com.zerotoship.z2term.edge

/**
 * A tab is a stack of rows; the items of one row sit side by side. The row is stored per item
 * (`row=N`), so each tab keeps its own arrangement instead of sharing the parent's `flow`.
 * A tab without any `row` still follows the legacy `flow`, and its rows are derived from it so
 * the first move in the editor keeps what the panel already looked like.
 */
internal object EdgeRows {
    /** Items that take the height left over by the other rows when the panel height is bounded. */
    val growingTypes = setOf("terminal", "view", "note")

    fun validate(fields: Map<String, String>) {
        fields["row"]?.let { require(it.toIntOrNull()?.let { n -> n in 1..64 } == true) { "row: 1–64" } }
    }

    fun arranged(items: List<EdgeStore.Item>): Boolean = items.any { !it.fields["row"].isNullOrBlank() }

    fun grows(item: EdgeStore.Item): Boolean = item.type in growingTypes && item.fields["height"].isNullOrBlank()

    /** Saved rows in number order; an item without a number (added from the CLI) gets its own row at the end. */
    fun saved(items: List<EdgeStore.Item>): List<List<EdgeStore.Item>> {
        val numbered = items.filter { it.fields["row"]?.toIntOrNull() != null }
            .groupBy { it.fields.getValue("row").toInt() }.toSortedMap().values.toList()
        return numbered + items.filter { it.fields["row"]?.toIntOrNull() == null }.map { listOf(it) }
    }

    /** The rows the legacy renderer draws for [flow] (`root.flow`, falling back to the tab layout). */
    fun derived(items: List<EdgeStore.Item>, rootFlow: String?, tabLayout: String?, columns: Int): List<List<EdgeStore.Item>> {
        val flow = rootFlow?.takeIf { it.isNotEmpty() } ?: if (tabLayout == "grid") "grid" else "vertical"
        return when (flow) {
            "horizontal" -> if (items.isEmpty()) emptyList() else listOf(items)
            "grid" -> if (rootFlow.isNullOrEmpty()) {
                // The legacy grid only gridded apps and stacked everything else below them.
                items.filter { it.type == "run" }.chunked(columns.coerceAtLeast(1)) +
                    items.filter { it.type != "run" }.map { listOf(it) }
            } else items.chunked(columns.coerceAtLeast(1))
            else -> items.map { listOf(it) }
        }
    }

    fun of(items: List<EdgeStore.Item>, rootFlow: String?, tabLayout: String?, columns: Int): List<List<EdgeStore.Item>> =
        if (arranged(items)) saved(items) else derived(items, rootFlow, tabLayout, columns)

    /**
     * Moves [id] one step through the rows as they read top to bottom. Stepping past the start or
     * end of a shared row gives the item a row of its own just above or below; a step from a row
     * of its own joins the neighbouring row. Every arrangement is reachable with the two arrows.
     */
    fun move(rows: List<List<String>>, id: String, delta: Int): List<List<String>> {
        val lines = rows.map { it.toMutableList() }.toMutableList()
        val r = lines.indexOfFirst { id in it }
        require(r >= 0) { "No item to move" }
        val line = lines[r]
        val i = line.indexOf(id)
        val j = i + delta
        when {
            j in line.indices -> { line.removeAt(i); line.add(j, id) }
            line.size > 1 -> { line.removeAt(i); lines.add(if (delta < 0) r else r + 1, mutableListOf(id)) }
            delta < 0 && r > 0 -> { line.removeAt(i); lines[r - 1].add(id) }
            delta > 0 && r < lines.lastIndex -> { line.removeAt(i); lines[r + 1].add(0, id) }
        }
        return lines.filter { it.isNotEmpty() }
    }

    /** Places [id] next to [target] (after it when [after]), in the target's row. */
    fun drop(rows: List<List<String>>, id: String, target: String, after: Boolean): List<List<String>> {
        if (id == target) return rows
        val lines = rows.map { line -> line.filter { it != id }.toMutableList() }
        val line = lines.first { target in it }
        line.add(line.indexOf(target) + if (after) 1 else 0, id)
        return lines.filter { it.isNotEmpty() }
    }

    /** Whether [id] can move by [delta]; an item alone in the first or last row has nowhere to go. */
    fun canMove(rows: List<List<String>>, id: String, delta: Int): Boolean = move(rows, id, delta) != rows

    /** The row a newly picked app joins: the last row when it holds only apps, otherwise a new one. */
    fun rowForNewApp(items: List<EdgeStore.Item>): Int? {
        if (!arranged(items)) return null
        val last = saved(items).last()
        val number = last.first().fields["row"]?.toIntOrNull()
        if (number != null && last.all { it.type == "run" }) return number
        return (items.mapNotNull { it.fields["row"]?.toIntOrNull() }.max() + 1).coerceAtMost(64)
    }
}
