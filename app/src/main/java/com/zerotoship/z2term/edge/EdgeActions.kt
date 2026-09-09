package com.zerotoship.z2term.edge

import java.net.URLDecoder
import java.net.URLEncoder

/** Shared, ordered actions. Arguments use UTF-8 form encoding so definitions remain one line. */
internal object EdgeActions {
    enum class Trigger(val id: String) {
        TAP("tap"), DOUBLE_TAP("double-tap"), UP("up"), DOWN("down"), INWARD("inward"), OUTWARD("outward");
        val key get() = "actions-$id"
    }
    enum class Type(val id: String, val parameter: Boolean = false) {
        PANEL("panel"), BACK("back"), HOME("home"), RECENTS("recents"), SHADE("shade"),
        LAUNCH("launch", true), WAIT("wait", true), COMMAND("command", true),
        SWIPE_UP("swipe-up"), SWIPE_DOWN("swipe-down"),
        SCROLL_VARIABLE("scroll-variable"), SCROLL_FIXED("scroll-fixed"),
        SCROLL_STOP("scroll-stop"), SCROLL_FASTER("scroll-faster"), SCROLL_SLOWER("scroll-slower"),
        SCROLL_REVERSE("scroll-reverse")
    }
    data class Action(val type: Type, val argument: String = "")
    const val MAX_ACTIONS = 16

    fun encode(actions: List<Action>): String = actions.joinToString("|") {
        it.type.id + if (it.type.parameter) ":" + URLEncoder.encode(it.argument, "UTF-8") else ""
    }

    fun decode(raw: String): List<Action> {
        if (raw.isEmpty()) return emptyList()
        require(raw.length <= 16384) { "Action definition exceeds 16 KiB" }
        val parts = raw.split('|')
        require(parts.size <= MAX_ACTIONS) { "At most $MAX_ACTIONS actions" }
        return parts.map { part ->
            val type = Type.entries.firstOrNull { it.id == part.substringBefore(':') }
                ?: throw IllegalArgumentException("Unknown action: ${part.substringBefore(':')}")
            require(type.parameter == (':' in part)) { "Invalid arguments for ${type.id}" }
            Action(type, if (type.parameter) URLDecoder.decode(part.substringAfter(':'), "UTF-8") else "")
        }.also(::validate)
    }

    fun validate(actions: List<Action>) {
        require(actions.size <= MAX_ACTIONS) { "At most $MAX_ACTIONS actions" }
        actions.forEach { action ->
            require(!action.argument.contains('\u0000')) { "Invalid action argument" }
            when (action.type) {
                Type.WAIT -> require(action.argument.toLongOrNull()?.let { it in 0L..30000L } == true) { "Wait: 0–30000 ms" }
                Type.LAUNCH -> require(action.argument.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))) { "Enter an application package ID" }
                Type.COMMAND -> require(action.argument.isNotBlank()) { "Enter a command" }
                else -> require(action.argument.isEmpty()) { "Unexpected action argument" }
            }
        }
        require(actions.filter { it.type == Type.WAIT }.sumOf { it.argument.toLong() } <= 60000) {
            "Total wait: at most 60000 ms"
        }
    }

    /** An explicit empty binding disables a gesture; only absent fields inherit legacy settings. */
    fun binding(fields: Map<String, String>, trigger: Trigger): List<Action> {
        fields[trigger.key]?.let { return decode(it) }
        val opening = fields["open"].orEmpty().ifEmpty { if (fields["handle"] == "button") "tap" else "swipe" }
        fun command(raw: String?) = raw?.takeIf { it.isNotBlank() }?.let { listOf(Action(Type.COMMAND, it)) }.orEmpty()
        return when (trigger) {
            Trigger.TAP, Trigger.INWARD -> {
                val enabled = if (trigger == Trigger.TAP) opening in setOf("tap", "both") else opening in setOf("swipe", "both")
                if (!enabled) emptyList() else command(fields["run"]).ifEmpty { listOf(Action(Type.PANEL)) }
            }
            Trigger.DOUBLE_TAP -> command(fields["gesture-double-tap"])
            Trigger.UP, Trigger.DOWN -> when (fields["gesture-scroll"]) {
                "variable" -> listOf(Action(Type.SCROLL_VARIABLE))
                "fixed" -> listOf(Action(Type.SCROLL_FIXED))
                else -> command(fields["gesture-${trigger.id}"])
            }
            Trigger.OUTWARD -> emptyList()
        }
    }
}
