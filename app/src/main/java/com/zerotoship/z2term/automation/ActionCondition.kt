package com.zerotoship.z2term.automation

/** A strict subset of z2-state vocabulary, plus focused-window package metadata. */
internal object ActionCondition {
    private val booleans = setOf("screen", "locked", "idle", "charging", "wifi", "airplane", "headset", "bt_audio")
    private val numbers = setOf("level", "temp", "volume", "volume_max")
    private val strings = setOf("ssid", "ringer", "plug", "foreground")
    private val truth = setOf("true", "on", "yes", "1")
    private val falsity = setOf("false", "off", "no", "0")
    private data class Term(val negate: Boolean, val key: String, val operator: String, val value: String)
    private fun terms(spec: String): List<Term> {
        val parts = spec.split(',')
        require(parts.size in 1..16) { "A condition requires 1..16 comma-separated terms" }
        return parts.map { raw ->
            val match = Regex("(!?)([a-z_]+)\\s*(?:([=<>])\\s*(.+))?").matchEntire(raw.trim())
                ?: throw IllegalArgumentException("Invalid condition: $raw")
            val (_, negation, key, operator, argument) = match.groupValues
            val value = argument.trim()
            require(key in booleans || key in numbers || key in strings) { "Unknown condition: $key" }
            when (key) {
                in booleans -> require(operator.isEmpty() || operator == "=" && value.lowercase() in truth + falsity) {
                    "Boolean conditions use a key, !key, or key=true/false"
                }
                in numbers -> require(operator in setOf("=", "<", ">") && value.toDoubleOrNull()?.isFinite() == true) {
                    "Numeric conditions require =, < or > and a finite number"
                }
                else -> {
                    require(operator == "=" && value.isNotEmpty()) { "Text conditions require key=value" }
                    if (key == "foreground") ActionDefinition.packageName(value)
                }
            }
            Term(negation.isNotEmpty(), key, operator, value)
        }
    }
    fun validate(spec: String) { terms(spec) }
    fun matches(spec: String, state: Map<String, String>): Boolean = terms(spec).all { term ->
        val have = state[term.key] ?: error("Condition state unavailable: " + term.key)
        val result = when (term.key) {
            in booleans -> {
                val normalized = have.lowercase()
                check(normalized in truth + falsity) { "Condition state unavailable: " + term.key }
                val actual = normalized in truth
                if (term.operator.isEmpty()) actual else actual == (term.value.lowercase() in truth)
            }
            in numbers -> {
                val actual = have.toDoubleOrNull()
                check(actual != null && actual.isFinite() && actual != -1.0) { "Condition state unavailable: " + term.key }
                val expected = term.value.toDouble()
                when (term.operator) { "<" -> actual < expected; ">" -> actual > expected; else -> actual == expected }
            }
            else -> {
                check(have.isNotBlank()) { "Condition state unavailable: " + term.key }
                if (term.key == "foreground") have == term.value else have.equals(term.value, ignoreCase = true)
            }
        }
        if (term.negate) !result else result
    }
}
