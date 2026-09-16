package com.zerotoship.z2term.edge

import com.zerotoship.z2term.service.HeadlessRun

/** Output paths follow the launched shell's HOME, in both Linux and Android. */
internal object EdgeCommandScript {
    fun create(token: String, command: String, input: String?, value: String?, arguments: List<String>? = null): String {
        require(Regex("[a-zA-Z0-9-]+").matches(token))
        fun path(extension: String) = "\"\$HOME/.z2term/edge/.runtime/$token.$extension\""
        val quote = HeadlessRun::shSingleQuote
        val body = command + if (arguments != null) " \"\$@\"" else ""
        val argv = arguments ?: listOfNotNull(value)
        val invoke = "sh -c ${quote(body)} z2-edge" + argv.joinToString("") { " ${quote(it)}" }
        val pipe = input?.let { "printf %s ${quote(it)} | " }.orEmpty()
        // Drain both streams after reaching the display limit to avoid blocking the command.
        return "{ { $pipe$invoke; printf '%s' \"\$?\" > ${path("status")}; " +
            "} 2>&1 1>&3 | { head -c 65537 > ${path("err")}; cat > /dev/null; }; " +
            "} 3>&1 | { head -c 65537 > ${path("out")}; cat > /dev/null; }"
    }
}
