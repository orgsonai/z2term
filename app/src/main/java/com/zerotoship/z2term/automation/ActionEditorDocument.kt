package com.zerotoship.z2term.automation

/** Source-line edits preserve comments, unknown syntax and shell quoting until validation on save. */
internal class ActionEditorDocument(val text: String) {
    private val newline = if ("\r\n" in text) "\r\n" else "\n"
    private val lines = text.split(newline)
    data class Row(val line: Int, val text: String)

    val rows: List<Row> get() {
        var body = false
        return lines.mapIndexedNotNull { index, original ->
            val line = ActionDefinition.code(original)
            if (line.isEmpty()) return@mapIndexedNotNull null
            if (!body && Regex("[a-z]+=.*").matches(line)) return@mapIndexedNotNull null
            body = true
            Row(index, original)
        }
    }

    val hasBlocks get() = rows.any { ActionDefinition.code(it.text).split(Regex("\\s+")).first() in setOf("repeat", "if", "else", "end") }

    fun appendControl(branch: Boolean): String {
        val block = if (branch) "if charging\n  wait 800\nelse\n  wait 300\nend\n"
            else "repeat 3\n  wait 800\nend\n"
        val upgraded = withHeader("version", "2")
        return upgraded + (if (upgraded.endsWith('\n')) "" else newline) + block.replace("\n", newline)
    }

    fun header(name: String): String? = lines.take(rows.firstOrNull()?.line ?: lines.size)
        .firstOrNull { it.trim().startsWith("$name=") }?.let(ActionDefinition::code)?.substringAfter('=')

    fun withHeader(name: String, value: String): String {
        require(name in ActionDefinition.HEADERS)
        singleLine(value)
        val end = rows.firstOrNull()?.line ?: lines.size
        val indices = lines.indices.filter { it < end && lines[it].trim().startsWith("$name=") }
        require(indices.size <= 1) { "Duplicate header: $name" }
        val updated = lines.toMutableList()
        if (indices.isEmpty()) updated.add(0, "$name=$value") else updated[indices.single()] = "$name=$value" + comment(lines[indices.single()])
        return updated.joinToString(newline)
    }

    fun withoutScreen(): String {
        val end = rows.firstOrNull()?.line ?: lines.size
        return lines.filterIndexed { index, line -> index >= end || !line.trim().startsWith("screen=") }.joinToString(newline)
    }

    fun replace(line: Int, value: String): String {
        singleLine(value)
        require(rows.any { it.line == line }) { "Select an action line" }
        // フォームで書き換えるのは本文だけ。行末のコメントは残す。
        return lines.toMutableList().apply {
            this[line] = lines[line].takeWhile { it.isWhitespace() } + value.trim() + comment(lines[line])
        }.joinToString(newline)
    }

    fun append(value: String): String {
        singleLine(value)
        return text + (if (text.isEmpty() || text.endsWith('\n')) "" else newline) + value + newline
    }

    fun remove(line: Int): String {
        require(!hasBlocks) { "Edit structured blocks in text mode" }
        require(rows.any { it.line == line })
        return lines.toMutableList().apply { removeAt(line) }.joinToString(newline)
    }

    fun move(line: Int, direction: Int): String {
        require(!hasBlocks) { "Edit structured blocks in text mode" }
        require(direction == -1 || direction == 1)
        val actions = rows
        val index = actions.indexOfFirst { it.line == line }
        require(index >= 0 && index + direction in actions.indices)
        val other = actions[index + direction].line
        return lines.toMutableList().apply {
            val old = this[line]; this[line] = this[other]; this[other] = old
        }.joinToString(newline)
    }

    fun targetBefore(line: Int): String? {
        var target: String? = null
        val scopes = mutableListOf<String?>()
        rows.filter { it.line < line }.forEach {
            val words = ActionDefinition.code(it.text).split(Regex("\\s+"))
            when (words[0]) {
                "repeat", "if" -> scopes += target
                "else" -> { if (scopes.isEmpty()) return null; target = scopes.last() }
                "end" -> { if (scopes.isEmpty()) return null; target = scopes.removeAt(scopes.lastIndex) }
                "launch", "target" -> if (words.size == 2) target = words[1]
            }
        }
        return target
    }

    /**
     * 選んだ範囲 [start]..[end] (文字位置) にかかる行を、まとめてコメントにする／戻す。
     * 空行以外がすべてコメントなら先頭の `#` (と直後の空白 1 つ) を外し、そうでなければ字下げの後に `# ` を足す。
     */
    fun toggleComment(start: Int, end: Int): String {
        fun lineAt(position: Int): Int {
            var offset = 0
            lines.forEachIndexed { index, line ->
                if (position <= offset + line.length) return index
                offset += line.length + newline.length
            }
            return lines.lastIndex
        }
        val low = minOf(start, end).coerceIn(0, text.length)
        val high = maxOf(start, end).coerceIn(0, text.length)
        val first = lineAt(low)
        // 行の頭までを選んだときは、その行を含めない (行を下までなぞって選んだ形)。
        val last = maxOf(first, lineAt(if (high > low && text.startsWith(newline, high - newline.length)) high - newline.length else high))
        val range = first..last
        val targets = range.filter { lines[it].isNotBlank() }
        if (targets.isEmpty()) return text
        val commented = targets.all { lines[it].trimStart().startsWith('#') }
        return lines.mapIndexed { index, line ->
            if (index !in targets) return@mapIndexed line
            val indent = line.takeWhile { it.isWhitespace() }
            val rest = line.substring(indent.length)
            indent + if (commented) rest.removePrefix("#").removePrefix(" ") else "# $rest"
        }.joinToString(newline)
    }

    companion object {
        /** 行末のコメント (前の空白ごと)。無ければ空。 */
        fun comment(line: String): String {
            val trimmed = line.trimEnd()
            val start = ActionDefinition.commentStart(trimmed) ?: return ""
            if (trimmed.substring(0, start).isBlank()) return ""
            return trimmed.substring(trimmed.substring(0, start).trimEnd().length)
        }
        const val EMPTY = "version=1\ntimeout=30\n"
        fun singleLine(value: String) {
            require('\n' !in value && '\r' !in value && '\u0000' !in value) { "Use one line per step" }
        }
    }
}
