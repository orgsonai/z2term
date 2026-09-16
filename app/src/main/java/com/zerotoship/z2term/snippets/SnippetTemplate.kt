package com.zerotoship.z2term.snippets

/** Values are positional arguments to sh, never substituted into executable source. */
object SnippetTemplate {
    enum class Kind { TEXT, NUMBER, CHOICE, FILE }
    data class Field(val name: String, val kind: Kind, val initial: String, val choices: List<String> = emptyList())
    private val token = Regex("\\{\\{([^{}]+)}}")
    private val name = Regex("[\\p{L}_][\\p{L}\\p{N}_-]{0,63}")
    private val boundary = " \t\r\n;|&()"

    fun fields(command: String): List<Field> {
        val result = linkedMapOf<String, Field>()
        val matches = token.findAll(command).toList()
        require(command.replace(token, "").let { "{{" !in it && "}}" !in it })
        matches.forEach { match ->
            // A placeholder occupies a shell word; surrounding quotes would change its meaning.
            require(match.range.first == 0 || command[match.range.first - 1] in boundary)
            require(match.range.last == command.lastIndex || command[match.range.last + 1] in boundary)
            require(outsideQuotes(command, match.range.first))
            val definition = match.groupValues[1]
            val key = definition.substringBefore(':').substringBefore('=')
            require(name.matches(key))
            val rest = definition.removePrefix(key)
            val type = if (rest.startsWith(':')) rest.drop(1).substringBefore('=') else "text"
            val kind = Kind.entries.firstOrNull { it.name.equals(type, true) } ?: error("Unknown input type")
            val initial = if ('=' in rest) rest.substringAfter('=') else ""
            val choices = if (kind == Kind.CHOICE) initial.split('|') else emptyList()
            require(kind != Kind.CHOICE || choices.size >= 2 && choices.all { it.isNotEmpty() } && choices.distinct().size == choices.size)
            val field = Field(key, kind, choices.firstOrNull() ?: initial, choices)
            require(result[key] == null || result[key] == field)
            result[key] = field
        }
        require(result.size <= 16)
        return result.values.toList()
    }

    fun valid(field: Field, value: String): Boolean = '\u0000' !in value && when (field.kind) {
        Kind.TEXT -> true
        Kind.NUMBER -> value.isNotBlank() && value.toBigDecimalOrNull() != null
        Kind.CHOICE -> value in field.choices
        Kind.FILE -> value.isNotBlank()
    }

    fun render(command: String, values: Map<String, String>): String {
        val fields = fields(command)
        if (fields.isEmpty()) return command
        val args = fields.map { field ->
            val value = values[field.name] ?: field.initial
            require(valid(field, value)) { field.name }
            if (field.kind == Kind.FILE && value.startsWith("~/")) "\"\$HOME\"/" + quote(value.drop(2))
            else quote(value)
        }
        val body = token.replace(command) { match ->
            val key = match.groupValues[1].substringBefore(':').substringBefore('=')
            "\"\${" + (fields.indexOfFirst { it.name == key } + 1) + "}\""
        }
        return "sh -c ${quote(body)} z2-snippet " + args.joinToString(" ")
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun outsideQuotes(command: String, end: Int): Boolean {
        var quote: Char? = null
        var escaped = false
        for (c in token.replace(command.take(end), "argument")) {
            if (escaped) { escaped = false; continue }
            if (c == '\\' && quote != '\'') { escaped = true; continue }
            if (quote == c) quote = null
            else if (quote == null && c in "'\"`") quote = c
        }
        return quote == null && !escaped
    }
}
