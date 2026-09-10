package com.zerotoship.z2term.automation

/** One exact field match; values may contain spaces and '=' without shell quoting. */
internal data class ActionSelector(val key: String, val value: String) {
    fun matches(id: String?, text: String?, description: String?): Boolean =
        value == when (key) { "id" -> id; "text" -> text?.trim(); "desc" -> description?.trim(); else -> null }
    override fun toString() = "$key=$value"
    companion object {
        fun parse(source: String): ActionSelector {
            val key = source.substringBefore('=')
            val value = source.substringAfter('=', "").trim()
            require(key in setOf("id", "text", "desc") && value.isNotEmpty() && value.length <= 256 &&
                value.none { it.isISOControl() }) { "Use id=PACKAGE:id/NAME, text=TEXT or desc=DESCRIPTION (1..256 characters)" }
            if (key == "id") require(value.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+:id/[A-Za-z0-9_.]+"))) {
                "Use the complete resource ID: PACKAGE:id/NAME"
            }
            return ActionSelector(key, value)
        }
    }
}
