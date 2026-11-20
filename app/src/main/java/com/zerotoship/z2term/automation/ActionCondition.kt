package com.zerotoship.z2term.automation

/**
 * Condition expressions: a strict subset of z2-state vocabulary, focused-window package metadata, and (1.0.182)
 * screen elements, screen colours and shell exit codes.
 *
 * `,` is AND and binds tighter than `|` (OR); there are no parentheses. `!` negates one term.
 * A `command` term takes the rest of the expression as shell text, so it is always the last term.
 */
internal object ActionCondition {
    private val booleans = setOf("screen", "locked", "idle", "charging", "wifi", "airplane", "headset", "bt_audio")
    private val numbers = setOf("level", "temp", "volume", "volume_max")
    private val strings = setOf("ssid", "ringer", "plug", "foreground")
    private val truth = setOf("true", "on", "yes", "1")
    private val falsity = setOf("false", "off", "no", "0")
    const val MAX_TERMS = 16
    /** `#RRGGBB` matches when red, green and blue each differ by at most this much. */
    const val COLOR_TOLERANCE = 24

    sealed interface Term {
        val negate: Boolean
        data class State(override val negate: Boolean, val key: String, val operator: String, val value: String) : Term
        /** An element matching [selector] is on the target app's screen (any number of matches). */
        data class Ui(override val negate: Boolean, val selector: ActionSelector) : Term
        /** The screen pixel at [x],[y] (px, median of a small square) is named [name] or close to [rgb]. */
        data class Color(override val negate: Boolean, val x: Int, val y: Int, val name: String?, val rgb: Int?) : Term {
            fun matches(actual: Int): Boolean = if (name != null) UiBridgePolicy.colorName(actual) == name else {
                val expected = rgb ?: 0
                listOf(16, 8, 0).all { shift -> kotlin.math.abs(((actual shr shift) and 0xff) - ((expected shr shift) and 0xff)) <= COLOR_TOLERANCE }
            }
        }
        /** The shell [text] exits with status 0. */
        data class Shell(override val negate: Boolean, val text: String) : Term
        /** `var 名前=値` / `<` / `>` (1.0.192): a macro variable; one not set yet is 0. */
        data class Var(override val negate: Boolean, val name: String, val operator: String, val value: String) : Term
    }
    /** OR of AND groups. */
    data class Expression(val groups: List<List<Term>>) {
        val terms get() = groups.flatten()
        /** Terms that read the screen; these follow the macro's target app. */
        val readsScreen get() = terms.any { it is Term.Ui || it is Term.Color }
    }

    /** Index of `command` when the term starting at [start] is a shell term, else null. */
    private fun shellAt(spec: String, start: Int): Int? {
        val term = spec.substring(start)
        val body = term.trimStart().removePrefix("!").trimStart()
        return if (body.startsWith("command") && body.length > 7 && body[7].isWhitespace())
            start + term.length - body.length else null
    }

    /** Start of each term and whether it is a shell term (which runs to the end). */
    private fun bounds(spec: String): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var start = 0
        while (true) {
            // `color X,Y=…` のカンマは座標の区切り。区切りは `=` の後ろから探す。
            val body = spec.substring(start).trimStart().removePrefix("!").trimStart()
            val from = if (Regex("color\\s.*").matches(body)) spec.indexOf('=', start).let { if (it < 0) start else it } else start
            val separator = spec.indexOfAny(charArrayOf(',', '|'), from)
            val end = if (shellAt(spec, start) != null || separator < 0) spec.length else separator
            result += start to end
            if (end == spec.length) return result
            start = end + 1
        }
    }

    /**
     * Where a `command` term starts inside [spec] (index of `command`), or null. Text after it is shell, so a
     * `#` there is not a macro comment. Uses the same term boundaries as [parse].
     */
    fun shellStart(spec: String): Int? = bounds(spec).last().first.let { shellAt(spec, it) }

    fun parse(spec: String): Expression {
        val parts = bounds(spec)
        require(parts.size <= MAX_TERMS) { "A condition takes 1..$MAX_TERMS terms" }
        val groups = mutableListOf(mutableListOf<Term>())
        parts.forEachIndexed { index, (start, end) ->
            if (index > 0 && spec[start - 1] == '|') groups += mutableListOf<Term>()
            groups.last() += term(spec.substring(start, end))
        }
        return Expression(groups)
    }

    private fun term(raw: String): Term {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Empty condition term" }
        val negate = trimmed.startsWith('!')
        val body = trimmed.removePrefix("!").trimStart()
        val kind = body.substringBefore(' ').substringBefore('\t')
        val argument = body.substring(kind.length).trim()
        when (kind) {
            "ui" -> return Term.Ui(negate, ActionSelector.parse(argument))
            "color" -> {
                val match = Regex("([0-9]{1,5}),([0-9]{1,5})\\s*=\\s*(#[0-9A-Fa-f]{6}|[a-z]+)").matchEntire(argument)
                    ?: throw IllegalArgumentException("Use color X,Y=NAME or color X,Y=#RRGGBB")
                val (x, y, value) = match.destructured
                return if (value.startsWith('#')) Term.Color(negate, x.toInt(), y.toInt(), null, value.drop(1).toInt(16))
                else {
                    require(value in UiBridgePolicy.COLOR_NAMES) { "Colour names: " + UiBridgePolicy.COLOR_NAMES.joinToString(" ") }
                    Term.Color(negate, x.toInt(), y.toInt(), value, null)
                }
            }
            "command" -> {
                require(argument.isNotEmpty()) { "Empty shell condition" }
                return Term.Shell(negate, argument)
            }
            "var" -> {
                val match = Regex("([A-Za-z_][A-Za-z0-9_]{0,31})\\s*([=<>])\\s*(.+)").matchEntire(argument)
                    ?: throw IllegalArgumentException("Use var NAME=VALUE, var NAME<NUMBER or var NAME>NUMBER")
                val (name, operator, value) = match.destructured
                if (operator != "=") require(value.trim().toDoubleOrNull()?.isFinite() == true) { "var < and > compare numbers" }
                return Term.Var(negate, name, operator, value.trim())
            }
        }
        val match = Regex("(!?)([a-z_]+)\\s*(?:([=<>])\\s*(.+))?").matchEntire(trimmed)
            ?: throw IllegalArgumentException("Invalid condition: $raw")
        val (_, negation, key, operator, value) = match.groupValues.map { it.trim() }
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
        return Term.State(negation.isNotEmpty(), key, operator, value)
    }

    fun validate(spec: String) { parse(spec) }

    /** Before negation. Numbers compare as numbers; `=` falls back to exact text. */
    fun variableMatches(term: Term.Var, variables: Map<String, String>): Boolean {
        val have = variables[term.name] ?: "0"
        val actual = have.toDoubleOrNull()
        val expected = term.value.toDoubleOrNull()
        return when (term.operator) {
            "=" -> if (actual != null && expected != null) actual == expected else have == term.value
            else -> {
                check(actual != null && actual.isFinite()) { "Variable ${term.name} is not a number: $have" }
                if (term.operator == "<") actual < expected!! else actual > expected!!
            }
        }
    }

    private fun stateMatches(term: Term.State, state: Map<String, String>): Boolean {
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
        return if (term.negate) !result else result
    }

    /** Device-state conditions only; screen and shell terms need [evaluate]. */
    fun matches(spec: String, state: Map<String, String>): Boolean = parse(spec).groups.any { group ->
        group.all { term ->
            check(term is Term.State) { "Screen, shell and variable conditions are evaluated while the macro runs" }
            stateMatches(term, state)
        }
    }

    /**
     * Evaluates [expression] term by term, stopping as soon as the answer is known. Device state is read once,
     * on the first state term ([state]). [probe] answers a screen or shell term asynchronously with
     * (result before negation, null) or (null, error) and returns its cancellation. [done] receives
     * (matched, null) or (null, error) once; the returned function cancels without calling it.
     */
    fun evaluate(expression: Expression, state: () -> Map<String, String>,
        probe: (Term, (Boolean?, String?) -> Unit) -> (() -> Unit), variables: () -> Map<String, String> = { emptyMap() },
        done: (Boolean?, String?) -> Unit): () -> Unit {
        var active = true
        var cancel: (() -> Unit)? = null
        var snapshot: Map<String, String>? = null
        fun finish(matched: Boolean?, error: String?) {
            if (!active) return
            active = false; cancel = null
            done(matched, error)
        }
        fun step(group: Int, index: Int) {
            if (!active) return
            if (group == expression.groups.size) { finish(false, null); return }
            val terms = expression.groups[group]
            if (index == terms.size) { finish(true, null); return }
            fun next(result: Boolean) = if (result) step(group, index + 1) else step(group + 1, 0)
            when (val term = terms[index]) {
                is Term.State -> {
                    val result = try { stateMatches(term, snapshot ?: state().also { snapshot = it }) }
                        catch (e: Exception) { finish(null, e.message ?: "Condition state unavailable"); return }
                    next(result)
                }
                is Term.Var -> {
                    val result = try { variableMatches(term, variables()) }
                        catch (e: Exception) { finish(null, e.message ?: "Variable unavailable"); return }
                    next(if (term.negate) !result else result)
                }
                else -> {
                    var answered = false
                    val stop = try {
                        probe(term) { result, error ->
                            if (!active || answered) return@probe
                            answered = true; cancel = null
                            if (error != null || result == null) finish(null, error ?: "Condition failed")
                            else next(if (term.negate) !result else result)
                        }
                    } catch (e: Exception) { finish(null, e.message ?: "Condition failed"); return }
                    if (active && !answered) cancel = stop
                }
            }
        }
        step(0, 0)
        return { if (active) { active = false; val c = cancel; cancel = null; runCatching { c?.invoke() } } }
    }
}
