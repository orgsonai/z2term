package com.zerotoship.z2term.viewer

import org.json.JSONArray
import org.json.JSONObject

/** Explicit local control definition, independent of the displayed HTML and of any macro's purpose. */
internal data class ViewerControls(val source: String, val handler: String, val refresh: List<String>,
    val actions: List<Action>) {
    data class Field(val label: String, val type: String, val initial: String, val required: Boolean,
        val choices: List<Pair<String, String>>)
    data class Action(val id: String, val label: String, val args: List<String>, val toolbar: Boolean,
        val confirmation: String, val fields: List<Field>)
    companion object {
        const val LIMIT = 256 * 1024
        private fun JSONArray.strings(): List<String> {
            require(length() <= 32) { "Too many viewer arguments" }
            return (0 until length()).map { getString(it).also { value ->
                require(value.length <= 4096 && '\u0000' !in value) { "Invalid viewer argument" }
            } }
        }
        fun parse(text: String): ViewerControls {
            require(text.toByteArray().size <= LIMIT) { "Viewer controls are too large" }
            val obj = JSONObject(text)
            val handler = obj.getString("handler")
            require(handler.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,120}\\.sh"))) { "Use an installed macro name for handler" }
            val refresh = obj.optJSONArray("refresh")?.strings().orEmpty()
            val array = obj.optJSONArray("actions") ?: JSONArray()
            require(array.length() <= 500) { "Too many viewer actions" }
            val actions = (0 until array.length()).map { index ->
                val a = array.getJSONObject(index)
                val id = a.getString("id")
                require(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid viewer action ID" }
                val fields = a.optJSONArray("fields") ?: JSONArray()
                require(fields.length() <= 8) { "Too many viewer fields" }
                Action(id, a.getString("label"), a.getJSONArray("args").strings(), a.optBoolean("toolbar"),
                    a.optString("confirm"), (0 until fields.length()).map { f ->
                        val field = fields.getJSONObject(f)
                        val type = field.optString("type", "text")
                        require(type in setOf("text", "datetime", "choice")) { "Unknown viewer field: $type" }
                        val choices = field.optJSONArray("choices") ?: JSONArray()
                        require(choices.length() <= 32 && (type != "choice" || choices.length() > 0)) { "Invalid viewer choices" }
                        Field(field.getString("label"), type, field.optString("default"), field.optBoolean("required", true),
                            (0 until choices.length()).map { c -> choices.getJSONObject(c).let {
                                it.getString("value").also { value -> require(value.length <= 4096 && '\u0000' !in value) } to it.getString("label")
                            } })
                    })
            }
            require(actions.map { it.id }.distinct().size == actions.size) { "Duplicate viewer action ID" }
            return ViewerControls(text, handler, refresh, actions)
        }
    }
}
