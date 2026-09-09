package com.zerotoship.z2term.edge

/** Recognize only literal launcher commands; never interpret shell syntax. */
internal object AppLaunchCommand {
    private val mode = Regex("--window\\s+(full|freeform|split|ask)")
    private val options = Regex("\\s+--(?:reuse-task|os-bounds|bounds-only)(?=\\s|$)")
    private val launcher = Regex(
        "^z2-intent\\s+(?:-p|--package)\\s+([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)" +
            "(?:\\s+--(?:reuse-task|os-bounds|bounds-only))*" +
            "(?:\\s+--window\\s+(?:full|freeform|split|ask))?" +
            "(?:\\s+--(?:reuse-task|os-bounds|bounds-only))*\\s*$"
    )
    fun packageFrom(command: String): String? = launcher.matchEntire(command.trim())?.groupValues?.get(1)
    fun modeFrom(command: String): String = mode.find(command)?.groupValues?.get(1) ?: "full"
    fun withMode(command: String, selected: String): String {
        require(packageFrom(command) != null) { "Not a literal app launch command" }
        require(selected in setOf("full", "freeform", "split", "ask")) { "Unknown launch mode" }
        val compatible = if (selected == "freeform") command else options.replace(command, "")
        return if (mode.containsMatchIn(compatible)) mode.replace(compatible, "--window $selected")
            else "${compatible.trimEnd()} --window $selected"
    }
}
