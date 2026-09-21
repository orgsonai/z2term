package com.zerotoship.z2term.edge

/** Editor vocabulary is independent of the on-disk types, so old items need no migration. */
internal object EdgeItemComponent {
    data class Selection(val component: String, val action: String)
    val actions = linkedMapOf(
        "button" to listOf("execute", "state_button"),
        "display" to listOf("receive", "fixed", "read", "view"),
        "entry" to listOf("value", "file", "send", "shell"),
        "choice" to listOf("value"),
        "switch" to listOf("state"),
        "list" to listOf("select")
    )
    fun from(item: EdgeStore.Item?): Selection = if (item?.isStateButton == true) Selection("button", "state_button") else when (item?.type) {
        "text" -> Selection("display", "read")
        "view" -> Selection("display", "view")
        "result" -> Selection("display", "receive")
        "argument" -> when (item.fields["argument-kind"]) {
            "fixed" -> Selection("display", "fixed")
            "choice" -> Selection("choice", "value")
            else -> Selection("entry", "value")
        }
        "note" -> Selection("entry", "file")
        "input" -> Selection("entry", "send")
        "terminal" -> Selection("entry", "shell")
        "toggle" -> Selection("switch", "state")
        "list" -> Selection("list", "select")
        else -> Selection("button", "execute")
    }
    fun type(selection: Selection, original: EdgeStore.Item?): String {
        require(selection.action in actions.getValue(selection.component))
        return when (selection.component) {
            "button" -> if (original?.type == "macro" && selection.action == "execute") "macro" else "run"
            "display" -> when (selection.action) { "receive" -> "result"; "read" -> "text"; "view" -> "view"; else -> "argument" }
            "entry" -> when (selection.action) { "file" -> "note"; "send" -> "input"; "shell" -> "terminal"; else -> "argument" }
            "choice" -> "argument"
            "switch" -> "toggle"
            else -> "list"
        }
    }
    fun apply(selection: Selection, original: EdgeStore.Item?, fields: MutableMap<String, String>) {
        fields["type"] = type(selection, original)
        if (fields["type"] == "argument") fields["argument-kind"] = when (selection.component) {
            "display" -> "fixed"; "choice" -> "choice"; else -> "text"
        }
    }
    fun linked(item: EdgeStore.Item): Boolean = item.type == "macro" ||
        (item.type == "run" && !item.isStateButton && listOf("args", "stdin", "result").any { !item.fields[it].isNullOrBlank() })
    fun source(item: EdgeStore.Item): Boolean = item.type in setOf("argument", "note", "input", "result", "text")
}
