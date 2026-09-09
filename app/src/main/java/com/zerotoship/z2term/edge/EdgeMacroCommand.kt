package com.zerotoship.z2term.edge

/** Shares tile's macro-name shorthand without assigning tile-specific environment variables. */
internal object EdgeMacroCommand {
    fun resolve(command: String, macros: Collection<String>): String {
        val cmd = command.trim()
        val name = if (cmd in macros) cmd else cmd.substringBefore(' ')
        if (name !in macros) return command
        val quoted = "'" + name.replace("'", "'\"'\"'") + "'"
        val args = cmd.removePrefix(name).trimStart()
        return "sh \"\$HOME/.z2term/macros/\"$quoted" + if (args.isEmpty()) "" else " $args"
    }
}
