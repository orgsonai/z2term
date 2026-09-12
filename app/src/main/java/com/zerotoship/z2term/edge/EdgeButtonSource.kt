package com.zerotoship.z2term.edge

/** A state source describes the effect of a command, independently of its exit status. */
internal object EdgeButtonSource {
    val choices = listOf("auto", "torch", "screen", "process", "remember")

    fun direct(command: String): String? {
        // Only recognize a single literal command. Scripts learn their source from API calls.
        val words = command.trim().split(Regex("\\s+"))
        val head = words.firstOrNull()?.removePrefix("/usr/local/bin/")
        return when {
            head == "z2-torch" && (words.size == 1 || words.size == 2 &&
                words[1] in setOf("on", "off", "toggle", "1", "0", "true", "false")) -> "torch"
            head == "z2-screen" && words.size == 3 && words[1] == "keepon" &&
                words[2].matches(Regex("[0-9]+[smh]?")) -> "screen"
            else -> null
        }
    }

    fun resolve(item: EdgeStore.Item, learned: String?, macros: Collection<String> = emptyList()): String? {
        if (!item.isStateButton || !item.fields["state"].isNullOrBlank()) return null
        return when (val source = item.fields["button-source"] ?: "auto") {
            "remember" -> null
            "auto" -> (if (item.command.trim().substringBefore(' ') in macros) null else direct(item.command))
                ?: learned?.takeIf { it in setOf("torch", "screen") }
            else -> source
        }
    }

    fun action(item: EdgeStore.Item, on: Boolean, source: String?): String = when {
        !on -> item.command
        !item.fields["off"].isNullOrBlank() -> item.fields.getValue("off")
        source == "torch" -> "z2-torch off"
        source == "screen" -> "z2-screen keepon off"
        else -> item.command
    }
}
