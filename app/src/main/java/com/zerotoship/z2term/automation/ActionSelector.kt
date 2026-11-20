package com.zerotoship.z2term.automation

/**
 * One field match; values may contain spaces and '=' without shell quoting.
 * `text=…` / `desc=…` / `id=…` match exactly. `text~=…` / `desc~=…` match when the field contains the value
 * (1.0.188), for labels whose numbers or words change around a fixed part.
 */
internal data class ActionSelector(val key: String, val value: String, val contains: Boolean = false) {
    fun matches(id: String?, text: String?, description: String?): Boolean {
        val actual = when (key) { "id" -> id; "text" -> text?.trim(); "desc" -> description?.trim(); else -> null }
        return if (contains) actual?.contains(value) == true else value == actual
    }
    override fun toString() = if (contains) "$key~=$value" else "$key=$value"
    companion object {
        fun parse(source: String): ActionSelector {
            val written = source.substringBefore('=')
            val contains = written.endsWith('~')
            val key = written.removeSuffix("~")
            val value = source.substringAfter('=', "").trim()
            require(key in setOf("id", "text", "desc") && value.isNotEmpty() && value.length <= 256 &&
                value.none { it.isISOControl() }) {
                "Use id=PACKAGE:id/NAME, text=TEXT, desc=DESCRIPTION, text~=PART or desc~=PART (1..256 characters)"
            }
            require(!contains || key != "id") { "Partial matches (~=) work with text and desc" }
            if (key == "id") require(value.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+:id/[A-Za-z0-9_.]+"))) {
                "Use the complete resource ID: PACKAGE:id/NAME"
            }
            return ActionSelector(key, value, contains)
        }
    }
}
