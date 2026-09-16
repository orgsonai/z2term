package com.zerotoship.z2term.edge

/** A private framing channel around a persistent sh; commands never share the app's terminal tabs. */
internal class EdgeTerminalProtocol(private val token: String, private val event: (String) -> Unit) {
    private val pending = StringBuilder()
    private val output = StringBuilder()
    private var escape = 0
    var truncated = false
        private set
    val text: String get() = output.toString()

    fun clear() { output.setLength(0); truncated = false }

    fun append(chunk: String) {
        for (c in chunk) {
            if (pending.isNotEmpty()) {
                if (c == '\u001f') {
                    val value = pending.substring(1)
                    pending.setLength(0)
                    if (value.startsWith("$token:")) event(value.removePrefix("$token:"))
                } else if (pending.length < 160) pending.append(c)
                else pending.setLength(0)
                continue
            }
            if (c == '\u001e') { pending.append(c); continue }
            // Plain result view: consume CSI/OSC, including sequences split across reads.
            when (escape) {
                1 -> { escape = when (c) { '[' -> 2; ']' -> 3; else -> 0 }; continue }
                2 -> { if (c in '@'..'~') escape = 0; continue }
                3 -> { if (c == '\u0007') escape = 0 else if (c == '\u001b') escape = 4; continue }
                4 -> { escape = if (c == '\\') 0 else 3; continue }
            }
            when {
                c == '\u001b' -> escape = 1
                c == '\b' -> if (output.isNotEmpty()) output.deleteCharAt(output.lastIndex)
                c == '\n' || c == '\t' || c >= ' ' && c != '\u007f' -> output.append(c)
            }
        }
        if (output.length > LIMIT) {
            output.delete(0, output.length - LIMIT)
            if (output.firstOrNull()?.isLowSurrogate() == true) output.deleteCharAt(0)
            truncated = true
        }
    }

    companion object {
        const val LIMIT = 65536
        fun script(token: String): String {
            require(token.matches(Regex("[a-zA-Z0-9]+")))
            return """
                stty -echo -icanon min 1 time 0
                Z2_EDGE_TERMINAL_ID=$token
                export Z2_EDGE_TERMINAL_ID
                PS1= PS2= PROMPT_COMMAND= HISTFILE=/dev/null
                export PS1 PS2 PROMPT_COMMAND HISTFILE
                set -m
                printf '\036$token:ready:%s\037' "${'$'}${'$'}"
                while IFS= read -r _z2edge_line; do
                    eval "${'$'}_z2edge_line" </dev/null
                    printf '\036$token:done:%s\037' "${'$'}?"
                done
            """.trimIndent()
        }
    }
}
