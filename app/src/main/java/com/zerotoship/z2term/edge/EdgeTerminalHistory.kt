package com.zerotoship.z2term.edge

/** A cursor over the regular shell history, plus commands accepted during this opening. */
internal class EdgeTerminalHistory {
    private val recent = mutableListOf<String>()
    private var saved = emptyList<String>()
    private var entries = emptyList<String>()
    private val edits = mutableMapOf<Int, String>()
    private var cursor = -1
    private var draft = ""
    val browsing: Boolean get() = cursor >= 0

    fun load(commands: List<String>) {
        saved = commands
        entries = (recent + saved).asSequence().filter { it.isNotBlank() && it.length <= 16384 &&
            it.none { c -> c == '\n' || c == '\r' || c == '\u0000' } }.distinct().take(300).toList()
        cursor = -1; draft = ""; edits.clear()
    }

    fun accepted(command: String) {
        recent.remove(command); recent.add(0, command)
        if (recent.size > 300) recent.removeAt(recent.lastIndex)
        load(saved)
    }

    fun older(current: String): String? {
        if (entries.isEmpty()) return null
        if (cursor < 0) draft = current else edits[cursor] = current
        if (cursor < entries.lastIndex) cursor++
        return edits[cursor] ?: entries[cursor]
    }

    fun newer(current: String): String? {
        if (cursor < 0) return null
        edits[cursor] = current
        cursor--
        return if (cursor < 0) draft else edits[cursor] ?: entries[cursor]
    }
}
