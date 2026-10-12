package com.zerotoship.z2term.edge

/** A private framing channel around a persistent sh; commands never share the app's terminal tabs. */
internal class EdgeTerminalProtocol(private val token: String, private val event: (String) -> Unit) {
    private val pending = StringBuilder()
    private val output = StringBuilder()
    private var escape = 0
    /** A carriage return was seen; the next printable character starts the line over. */
    private var carriage = false
    var truncated = false
        private set
    val text: String get() = output.toString()

    fun clear() { output.setLength(0); truncated = false; carriage = false }

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
                // Progress displays redraw one line with CR. Dropping CR strings every redraw into one
                // endless line; honour it by starting the line over. CR LF stays a plain newline.
                c == '\r' -> carriage = true
                c == '\n' -> { carriage = false; output.append(c) }
                c == '\t' || c >= ' ' && c != '\u007f' -> {
                    if (carriage) { carriage = false; output.setLength(output.lastIndexOf("\n") + 1) }
                    output.append(c)
                }
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

        /** The line that runs [command]. Only lines carrying the token are commands; see [script]. */
        fun commandLine(token: String, command: String): String = "$token $command\n"

        /**
         * The command reads the terminal, so a running program can be answered (1.0.8).
         *
         * - While a command runs the line discipline is canonical with echo: answers show in the result,
         *   a program that hides its input (a password prompt) still hides it, and end-of-input works.
         *   Between commands it is raw without echo, because a command may exceed the canonical line limit.
         * - An answer can arrive after its program has ended. The loop would then read it as the next
         *   command, so **only lines that start with the token are run**; anything else is dropped.
         * - Pagers wait for keys this view cannot send, so they are turned into plain output.
         */
        fun script(token: String): String {
            require(token.matches(Regex("[a-zA-Z0-9]+")))
            return """
                stty -echo -icanon min 1 time 0
                Z2_EDGE_TERMINAL_ID=$token
                export Z2_EDGE_TERMINAL_ID
                PS1= PS2= PROMPT_COMMAND= HISTFILE=/dev/null
                export PS1 PS2 PROMPT_COMMAND HISTFILE
                PAGER=cat GIT_PAGER=cat MANPAGER=cat SYSTEMD_PAGER=cat
                export PAGER GIT_PAGER MANPAGER SYSTEMD_PAGER
                set -m
                printf '\036$token:ready:%s\037' "${'$'}${'$'}"
                while IFS= read -r _z2edge_line; do
                    case "${'$'}_z2edge_line" in "$token "*) ;; *) continue ;; esac
                    stty icanon echo
                    eval "${'$'}{_z2edge_line#* }"
                    _z2edge_status=${'$'}?
                    stty -echo -icanon min 1 time 0
                    printf '\036$token:done:%s\037' "${'$'}_z2edge_status"
                done
            """.trimIndent()
        }
    }
}
