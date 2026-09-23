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

    /** Moves [id] one place left or right inside its own row; it never leaves the row. */
    fun shift(rows: List<List<String>>, id: String, delta: Int): List<List<String>> = rows.map { line ->
        val i = line.indexOf(id)
        if (i < 0 || i + delta !in line.indices) line
        else line.toMutableList().apply { removeAt(i); add(i + delta, id) }
    }

    fun canShift(rows: List<List<String>>, id: String, delta: Int): Boolean = shift(rows, id, delta) != rows

    /** Puts [id] into row [row] at [position], counted among that row's other items. */
    fun insert(rows: List<List<String>>, id: String, row: Int, position: Int): List<List<String>> {
        val lines = rows.map { line -> line.filter { it != id }.toMutableList() }
        lines[row].add(position.coerceIn(0, lines[row].size), id)
        return lines.filter { it.isNotEmpty() }
    }

    /** Gives [id] a row of its own at gap [index] (0 = above the first row, rows.size = below the last). */
    fun newRow(rows: List<List<String>>, id: String, index: Int): List<List<String>> {
        val lines = rows.map { line -> line.filter { it != id } }.toMutableList()
        lines.add(index.coerceIn(0, lines.size), listOf(id))
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

    /** The other items [id] shares a row with; a changed set means it moved to another row. */
    fun companions(rows: List<List<String>>, id: String): Set<String>? =
        rows.firstOrNull { id in it }?.let { it.toSet() - id }

    /**
     * Relative widths for one row. `N%` is a share of the panel width and dp is converted to one;
     * items without a width split what is left, or take the average share once nothing is left.
     */
    fun weights(widths: List<String?>, panelWidthDp: Float): List<Float> {
        val given = widths.map { raw ->
            raw?.takeIf { it.isNotBlank() }?.let { runCatching { EdgeStore.dimension(it) }.getOrNull() }
                ?.let { (value, percent) -> if (percent) value else value / panelWidthDp.coerceAtLeast(1f) * 100f }
        }
        val known = given.filterNotNull()
        val open = given.count { it == null }
        val rest = if (open == 0) 0f else (100f - known.sum()).takeIf { it >= 5f * open }?.div(open)
            ?: (known.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: (100f / widths.size))
        return given.map { (it ?: rest).coerceAtLeast(1f) }
    }

    /**
     * Icon size (dp) in a row cell. A row with a height fills it, larger or smaller than the panel's
     * icon size; otherwise the icon only shrinks when the cell is too narrow. Null keeps the panel's
     * size and the usual padding. A named item keeps its width for the name, so only height limits it.
     */
    fun iconFit(base: Int, cellWidthDp: Float, rowHeightDp: Float?, labelled: Boolean): Int? {
        val padding = 4f
        val byWidth = if (labelled) Float.MAX_VALUE else cellWidthDp - padding
        if (rowHeightDp == null && byWidth >= base) return null
        val size = if (rowHeightDp != null) minOf(rowHeightDp - padding, byWidth) else byWidth
        return size.toInt().coerceIn(12, 192)
    }

    /** Whole percentages that keep [weights] in proportion and add up to exactly 100. */
    fun percents(weights: List<Float>): List<Int> {
        val total = weights.sum().coerceAtLeast(0.001f)
        val shares = weights.map { (it / total * 100f).toInt().coerceAtLeast(1) }.toMutableList()
        shares[shares.indices.maxBy { shares[it] }] += 100 - shares.sum()
        return shares
    }

    /** The row a newly picked app joins: the last row when it holds only apps, otherwise a new one. */
    fun rowForNewApp(items: List<EdgeStore.Item>): Int? {
        if (!arranged(items)) return null
        val last = saved(items).last()
        val number = last.first().fields["row"]?.toIntOrNull()
        if (number != null && last.all { it.type == "run" }) return number
        return (items.mapNotNull { it.fields["row"]?.toIntOrNull() }.max() + 1).coerceAtMost(64)
    }
}
