package com.zerotoship.z2term.automation

/** Structural edits keep each block, its delimiters and leading body comments together. */
internal class ActionBlockDocument(val text: String) {
    private val newline = if ("\r\n" in text) "\r\n" else "\n"
    private val lines = text.split(newline)
    private val rows = ActionEditorDocument(text).rows
    data class Item(val start: Int, val line: Int, val end: Int, val verb: String, val path: String,
        val body: Group? = null, val otherwise: Group? = null)
    data class Group(val start: Int, val end: Int, val depth: Int, val path: String, val branch: String, val items: List<Item>)
    val root: Group
    val groups: List<Group> get() {
        fun collect(group: Group): List<Group> = listOf(group) + group.items.flatMap {
            (it.body?.let(::collect) ?: emptyList()) + (it.otherwise?.let(::collect) ?: emptyList())
        }
        return collect(root)
    }
    init {
        require(rows.size <= 256) { "More than 256 source rows; use text editing" }
        var index = 0
        val endOfText = if (lines.last().isEmpty()) lines.lastIndex else lines.size
        fun verb(row: ActionEditorDocument.Row) = row.text.trim().split(Regex("\\s+")).first()
        fun group(start: Int, depth: Int, path: String, branch: String): Group {
            require(depth <= ActionDefinition.MAX_NESTING) { "Block nesting exceeds eight levels" }
            val items = mutableListOf<Item>()
            var cursor = start
            while (index < rows.size && verb(rows[index]) !in setOf("else", "end")) {
                val row = rows[index++]
                val kind = verb(row)
                val prefix = when (branch) {
                    "yes", "no" -> "$path.$branch."
                    else -> if (path.isEmpty()) "" else "$path."
                }
                val suffix = if (kind == "target") "target" + (items.count { it.verb == "target" } + 1)
                    else (items.count { it.verb != "target" } + 1).toString()
                val position = prefix + suffix
                var body: Group? = null
                var otherwise: Group? = null
                var end = row.line + 1
                if (kind in setOf("repeat", "if")) {
                    body = group(row.line + 1, depth + 1, position, if (kind == "if") "yes" else "repeat")
                    if (index < rows.size && verb(rows[index]) == "else") {
                        require(kind == "if") { "else requires an if block" }
                        otherwise = group(rows[index++].line + 1, depth + 1, position, "no")
                    }
                    require(index < rows.size && verb(rows[index]) == "end") { "Missing end for block at line " + (row.line + 1) }
                    end = rows[index++].line + 1
                }
                items += Item(cursor, row.line, end, kind, position, body, otherwise)
                cursor = end
            }
            return Group(start, rows.getOrNull(index)?.line ?: endOfText, depth, path, branch, items)
        }
        root = group(rows.firstOrNull()?.line ?: endOfText, 0, "", "root")
        require(index == rows.size) { "Unexpected else or end" }
    }
    fun item(line: Int) = groups.flatMap { it.items }.firstOrNull { it.line == line } ?: error("Select a step or block")
    fun owner(line: Int) = groups.first { group -> group.items.any { it.line == line } }
    fun group(start: Int) = groups.firstOrNull { it.start == start } ?: error("Insertion location changed")
    private fun splice(start: Int, end: Int, replacement: List<String>): String =
        lines.toMutableList().apply { subList(start, end).clear(); addAll(start, replacement) }.joinToString(newline)
    private fun content(source: String, depth: Int): List<String> {
        val indent = "  ".repeat(depth)
        return source.replace("\r\n", "\n").trimEnd('\n').split('\n').map { if (it.isBlank()) it else indent + it }
    }
    fun insert(start: Int, source: String): String {
        val group = group(start)
        return splice(group.end, group.end, content(source, group.depth))
    }
    fun remove(line: Int): String = item(line).let { splice(it.start, it.end, emptyList()) }
    fun duplicate(line: Int): String = item(line).let { splice(it.end, it.end, lines.subList(it.start, it.end)) }
    fun move(line: Int, direction: Int): String {
        require(direction == -1 || direction == 1)
        val siblings = owner(line).items
        val index = siblings.indexOfFirst { it.line == line }
        require(index + direction in siblings.indices) { "No adjacent step in this block" }
        val first = siblings[minOf(index, index + direction)]
        val last = siblings[maxOf(index, index + direction)]
        return splice(first.start, last.end, lines.subList(last.start, last.end) + lines.subList(first.start, first.end))
    }
    fun destinations(line: Int): List<Group> {
        val item = item(line)
        return groups.filterNot { it.start > item.line && it.start < item.end }
    }
    fun moveTo(line: Int, start: Int): String {
        val item = item(line)
        val destination = destinations(line).firstOrNull { it.start == start } ?: error("Cannot move a block into itself")
        val indent = lines[item.line].takeWhile { it.isWhitespace() }
        val shifted = lines.subList(item.start, item.end).map {
            if (it.isBlank()) it else "  ".repeat(destination.depth) + it.removePrefix(indent)
        }
        val changed = lines.toMutableList()
        changed.subList(item.start, item.end).clear()
        val insertion = destination.end - if (destination.end >= item.end) item.end - item.start else 0
        changed.addAll(insertion, shifted)
        return changed.joinToString(newline)
    }
    fun addElse(line: Int): String {
        val item = item(line)
        require(item.verb == "if" && item.otherwise == null) { "This block already has an else branch" }
        return splice(item.end - 1, item.end - 1, content("else\n  wait 800", owner(line).depth))
    }
    fun removeElse(line: Int): String {
        val group = item(line).otherwise ?: error("No else branch")
        return splice(group.start - 1, group.end, emptyList())
    }
}
