package com.zerotoship.z2term.automation

/** Text is authoritative; shell text is preserved and other steps use a strict grammar. */
internal data class ActionDefinition(val text: String, val timeoutMs: Long, val screen: Screen?, val steps: List<Step>) {
    data class Screen(val width: Int, val height: Int, val rotation: Int) {
        init { require(width in 1..32768 && height in 1..32768 && rotation in 0..3) { "Invalid screen geometry" } }
        override fun toString() = "${width}x${height}@$rotation"
    }
    sealed interface Step {
        data class Wait(val ms: Long) : Step
        data class Launch(val packageName: String) : Step
        data class Key(val name: String) : Step
        data class Command(val text: String) : Step
        data class Stroke(val target: String, val unit: String, val x1: Float, val y1: Float,
            val x2: Float, val y2: Float, val ms: Long) : Step {
            fun points(screen: Screen): List<Float> {
                val points = if (unit == "percent") listOf(x1 * (screen.width - 1) / 100,
                    y1 * (screen.height - 1) / 100, x2 * (screen.width - 1) / 100, y2 * (screen.height - 1) / 100)
                    else listOf(x1, y1, x2, y2)
                require(points[0] < screen.width && points[2] < screen.width &&
                    points[1] < screen.height && points[3] < screen.height) { "Coordinates are outside the saved screen" }
                return points
            }
        }
        data class Scroll(val target: String, val speed: Float, val ms: Long, val x: Float, val y: Float) : Step
    }
    companion object {
        const val MAX_BYTES = 65536
        const val MAX_STEPS = 64
        fun packageName(value: String): String {
            require(value.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))) { "Invalid application package ID" }
            return value
        }
        fun parse(raw: String, current: Screen? = null): ActionDefinition {
            require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES && '\u0000' !in raw) { "Definition exceeds 64 KiB or contains NUL" }
            val headers = linkedMapOf<String, String>()
            val steps = mutableListOf<Step>()
            var target: String? = null
            var body = false
            val lines = raw.replace("\r\n", "\n").lines().map { original ->
                val line = original.trim()
                if (line.isEmpty() || line.startsWith('#')) return@map original
                require('\r' !in original) { "Invalid line ending" }
                if (!body && '=' in line && line.substringBefore('=').matches(Regex("[a-z]+"))) {
                    val key = line.substringBefore('=')
                    require(key in setOf("version", "timeout", "screen") && key !in headers) { "Unknown or duplicate header: $key" }
                    val value = line.substringAfter('=').let {
                        if (key == "screen" && it == "current") (current ?: error("screen=current requires z2-action save")).toString() else it
                    }
                    headers[key] = value
                    return@map "$key=$value"
                }
                body = true
                val words = line.split(Regex("\\s+"))
                fun count(n: Int) { require(words.size == n) { "Wrong arguments: ${words[0]}" } }
                fun number(i: Int, low: Float, high: Float): Float = words[i].toFloatOrNull()?.also {
                    require(it.isFinite() && it in low..high) { "Invalid number: ${words[i]}" }
                } ?: throw IllegalArgumentException("Invalid number: ${words[i]}")
                fun millis(i: Int, low: Long, high: Long): Long = words[i].toLongOrNull()?.also {
                    require(it in low..high) { "Duration must be $low..$high ms" }
                } ?: throw IllegalArgumentException("Invalid duration")
                fun destination() = target ?: throw IllegalArgumentException("Set target PACKAGE or launch PACKAGE before coordinates/scroll")
                when (words[0]) {
                    "target" -> { count(2); target = packageName(words[1]) }
                    "launch" -> { count(2); target = packageName(words[1]); steps += Step.Launch(words[1]) }
                    "wait" -> { count(2); steps += Step.Wait(millis(1, 0, 30000)) }
                    "key" -> {
                        count(2)
                        require(words[1] in setOf("back", "home", "recents", "shade", "quicksettings", "screenshot", "split")) { "Unknown Android key" }
                        steps += Step.Key(words[1])
                    }
                    "command" -> {
                        val text = line.dropWhile { !it.isWhitespace() }.trimStart()
                        require(text.isNotBlank()) { "Empty shell command" }
                        steps += Step.Command(text)
                    }
                    "tap", "long-press", "swipe" -> {
                        val swipe = words[0] == "swipe"
                        count(if (swipe) 7 else if (words[0] == "tap") 4 else 5)
                        require(words[1] in setOf("px", "percent")) { "Coordinate unit must be px or percent" }
                        val maximum = if (words[1] == "percent") 100f else 32767f
                        val x1 = number(2, 0f, maximum); val y1 = number(3, 0f, maximum)
                        steps += Step.Stroke(destination(), words[1], x1, y1,
                            if (swipe) number(4, 0f, maximum) else x1,
                            if (swipe) number(5, 0f, maximum) else y1,
                            if (swipe) millis(6, 1, 3000) else if (words[0] == "tap") 80 else millis(4, 500, 3000))
                    }
                    "scroll" -> {
                        count(5)
                        val speed = number(1, -40000f, 40000f)
                        require(kotlin.math.abs(speed) >= 50f) { "Scroll speed: 50..40000 dp/s, negative moves forward" }
                        steps += Step.Scroll(destination(), speed, millis(2, 1, 30000), number(3, 10f, 90f), number(4, 10f, 90f))
                    }
                    else -> throw IllegalArgumentException("Unknown step: ${words[0]}")
                }
                require(steps.size <= MAX_STEPS) { "At most $MAX_STEPS steps" }
                original
            }
            require(headers["version"] == "1") { "Start the definition with version=1" }
            require(steps.isNotEmpty()) { "No executable steps" }
            val timeout = (headers["timeout"] ?: "30").toLongOrNull()
            require(timeout != null && timeout in 1..300) { "timeout must be 1..300 seconds" }
            val screen = headers["screen"]?.let {
                val match = Regex("([0-9]+)x([0-9]+)@([0-3])").matchEntire(it)
                    ?: throw IllegalArgumentException("screen must be WIDTHxHEIGHT@ROTATION or current when saving")
                Screen(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
            }
            if (steps.any { it is Step.Stroke || it is Step.Scroll }) require(screen != null) { "Coordinates/scroll require screen=current or saved geometry" }
            steps.filterIsInstance<Step.Stroke>().forEach { it.points(screen!!) }
            return ActionDefinition(lines.joinToString("\n").trimEnd() + "\n", timeout * 1000, screen, steps.toList())
        }
    }
}
