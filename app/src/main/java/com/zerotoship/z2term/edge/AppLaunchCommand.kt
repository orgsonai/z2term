package com.zerotoship.z2term.edge

/** Recognize only literal launcher commands; never interpret shell syntax. */
internal object AppLaunchCommand {
    private val mode = Regex("--window\\s+(full|freeform|split|ask)")
    private val options = Regex("\\s+--(?:reuse-task|os-bounds|bounds-only|no-scale)(?=\\s|$)")
    private val profile = Regex("(?:^|\\s)--profile\\s+(\\d{1,18})(?=\\s|$)")
    private const val option = "(?:\\s+--(?:reuse-task|os-bounds|bounds-only|no-scale)|\\s+--profile\\s+\\d{1,18})*"
    private val launcher = Regex(
        "^z2-intent\\s+(?:-p|--package)\\s+([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)" +
            option + "(?:\\s+--window\\s+(?:full|freeform|split|ask))?" + option + "\\s*$"
    )
    fun packageFrom(command: String): String? = launcher.matchEntire(command.trim())?.groupValues?.get(1)
    /** The other profile a launch command opens the app in, or null for z2term's own profile. */
    fun profileFrom(command: String): Long? =
        if (packageFrom(command) == null) null else profile.find(command)?.groupValues?.get(1)?.toLongOrNull()
    /** `@app:<package>`, or `@app:<package>@<profile>` for an app in another profile. */
    fun iconFrom(command: String): String? = packageFrom(command)?.let { pkg ->
        profileFrom(command)?.let { "@app:$pkg@$it" } ?: "@app:$pkg"
    }
    fun modeFrom(command: String): String = mode.find(command)?.groupValues?.get(1) ?: "full"
    fun scalesFreeform(command: String): Boolean = !Regex("(?:^|\\s)--no-scale(?=\\s|$)").containsMatchIn(command)
    fun withFreeformScale(command: String, scaled: Boolean): String {
        require(packageFrom(command) != null) { "Not a literal app launch command" }
        val without = Regex("\\s+--no-scale(?=\\s|$)").replace(command, "")
        return if (scaled) without else "${without.trimEnd()} --no-scale"
    }
    fun withMode(command: String, selected: String): String {
        require(packageFrom(command) != null) { "Not a literal app launch command" }
        require(selected in setOf("full", "freeform", "split", "ask")) { "Unknown launch mode" }
        require(selected != "split" || profileFrom(command) == null) { "Split screen cannot open an app in another profile" }
        val compatible = if (selected == "freeform") command else options.replace(command, "")
        return if (mode.containsMatchIn(compatible)) mode.replace(compatible, "--window $selected")
            else "${compatible.trimEnd()} --window $selected"
    }
}
