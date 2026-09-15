package com.zerotoship.z2term.automation

/** Text is authoritative; shell text is preserved and other steps use a strict grammar. */
internal data class ActionDefinition(val text: String, val timeoutMs: Long, val screen: Screen?, val steps: List<Step>) {
    data class Screen(val width: Int, val height: Int, val rotation: Int) {
        init { require(width in 1..32768 && height in 1..32768 && rotation in 0..3) { "Invalid screen geometry" } }
        override fun toString() = "${width}x${height}@$rotation"
    }
    sealed interface Step {
        data class Repeat(val count: Int?, val body: List<Step>) : Step
        data class Branch(val condition: String, val yes: List<Step>, val no: List<Step>) : Step
        data class Call(val name: String) : Step
        data class Ui(val target: String, val operation: String, val selector: ActionSelector, val timeoutMs: Long = 0) : Step
        data class Wait(val ms: Long) : Step
        data class Launch(val packageName: String) : Step
        data class Key(val name: String) : Step
        data class Command(val text: String) : Step
        data class Stroke(val target: String, val unit: String, val x1: Float, val y1: Float,
            val x2: Float, val y2: Float, val ms: Long, val path: List<ActionGesture.Point> = emptyList(),
            val easing: String = "linear", val secondPath: List<ActionGesture.Point> = emptyList(),
            val taps: Int = 1, val gapMs: Long = 100) : Step {
            val durationMs get() = ms * taps + gapMs * (taps - 1)
            fun tracks(screen: Screen): List<List<ActionGesture.Point>> {
                val tracks = listOf(timedPoints(screen)) + if (secondPath.isEmpty()) emptyList()
                    else listOf(ActionGesture.convert(secondPath, unit, "px", screen))
                require(tracks.flatten().all { it.x.isFinite() && it.y.isFinite() &&
                    it.x >= 0 && it.y >= 0 && it.x < screen.width && it.y < screen.height }) {
                    "Coordinates are outside the saved screen"
                }
                require(tracks.maxOf { it.first().ms } < tracks.minOf { it.last().ms }) {
                    "Separate non-overlapping touches into separate steps"
                }
                return tracks
            }
            fun timedPoints(screen: Screen): List<ActionGesture.Point> {
                val source = path.ifEmpty { ActionGesture.line(x1, y1, x2, y2, ms, easing) }
                val pixels = ActionGesture.convert(source, unit, "px", screen)
                require(pixels.all { it.x.isFinite() && it.y.isFinite() && it.x >= 0 && it.y >= 0 &&
                    it.x < screen.width && it.y < screen.height }) { "Coordinates are outside the saved screen" }
                return pixels
            }
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
        const val CURRENT_TARGET = "current"
        const val MAX_BYTES = 65536
        const val MAX_STEPS = 64
        const val MAX_NESTING = 8
        fun macroName(value: String): String {
            require(value.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Macro names use 1..64 letters, digits, _ or -" }
            return value
        }
        fun allSteps(steps: List<Step>): List<Step> = steps.flatMap { step ->
            listOf(step) + when (step) {
                is Step.Repeat -> allSteps(step.body)
                is Step.Branch -> allSteps(step.yes) + allSteps(step.no)
                else -> emptyList()
            }
        }
        private data class Block(val kind: String, val argument: String, val parent: MutableList<Step>,
            val target: String?, var yes: List<Step>? = null)
        fun packageName(value: String): String {
            require(value.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))) { "Invalid application package ID" }
            return value
        }
        fun parse(raw: String, current: Screen? = null): ActionDefinition {
            require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES && '\u0000' !in raw) { "Definition exceeds 64 KiB or contains NUL" }
            val headers = linkedMapOf<String, String>()
            var steps = mutableListOf<Step>()
            val blocks = mutableListOf<Block>()
            var size = 0
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
                fun destination() = target ?: CURRENT_TARGET
                if (words[0] in setOf("repeat", "if", "else", "end", "call")) {
                    require(headers["version"] == "2") { "Control flow requires version=2" }
                    when (words[0]) {
                        "repeat", "if" -> {
                            if (words[0] == "repeat") {
                                count(2)
                                require(words[1] == "forever" || words[1].toIntOrNull()?.let { it in 1..10000 } == true) {
                                    "repeat requires 1..10000 or forever"
                                }
                            } else ActionCondition.validate(line.dropWhile { !it.isWhitespace() }.trim())
                            require(blocks.size < MAX_NESTING) { "At most $MAX_NESTING nested blocks" }
                            blocks += Block(words[0], if (words[0] == "repeat") words[1]
                                else line.dropWhile { !it.isWhitespace() }.trim(), steps, target)
                            steps = mutableListOf()
                            size++
                        }
                        "else" -> {
                            count(1)
                            val block = blocks.lastOrNull()
                            require(block != null && block.kind == "if" && block.yes == null) { "else requires an unmatched if" }
                            require(steps.isNotEmpty()) { "Empty if branch" }
                            block.yes = steps.toList(); steps = mutableListOf(); target = block.target
                        }
                        "end" -> {
                            count(1)
                            require(blocks.isNotEmpty()) { "Unexpected end" }
                            require(steps.isNotEmpty()) { "Empty control-flow block" }
                            val block = blocks.removeAt(blocks.lastIndex)
                            val closed = if (block.kind == "repeat") Step.Repeat(
                                block.argument.takeUnless { it == "forever" }?.toInt(), steps.toList())
                            else Step.Branch(block.argument, block.yes ?: steps.toList(),
                                if (block.yes != null) steps.toList() else emptyList())
                            steps = block.parent; steps += closed; target = block.target
                        }
                        "call" -> { count(2); steps += Step.Call(macroName(words[1])); size++ }
                    }
                    require(size <= MAX_STEPS) { "At most $MAX_STEPS steps including control flow" }
                    return@map original
                }
                val before = steps.size
                when (words[0]) {
                    "click", "long-click", "wait-ui" -> {
                        require(headers["version"] == "2") { "UI elements require version=2" }
                        val wait = words[0] == "wait-ui"
                        require(words.size >= if (wait) 3 else 2) { "UI operation requires a selector" }
                        val ms = if (wait) millis(1, 1, 30000) else 0
                        val selector = line.split(Regex("\\s+"), limit = if (wait) 3 else 2).last()
                        steps += Step.Ui(destination(), words[0], ActionSelector.parse(selector), ms)
                    }
                    "target" -> { count(2); target = if (words[1] == CURRENT_TARGET) CURRENT_TARGET else packageName(words[1]) }
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
                        if (swipe) require(words.size in 7..8) { "Wrong arguments: swipe" }
                        else count(if (words[0] == "tap") 4 else 5)
                        val easing = if (swipe && words.size == 8) words[7] else "linear"
                        require(easing in setOf("linear", "accelerate", "decelerate")) { "Swipe speed must be linear, accelerate or decelerate" }
                        require(words[1] in setOf("px", "percent")) { "Coordinate unit must be px or percent" }
                        val maximum = if (words[1] == "percent") 100f else 32767f
                        val x1 = number(2, 0f, maximum); val y1 = number(3, 0f, maximum)
                        steps += Step.Stroke(destination(), words[1], x1, y1,
                            if (swipe) number(4, 0f, maximum) else x1,
                            if (swipe) number(5, 0f, maximum) else y1,
                            if (swipe) millis(6, 1, 3000) else if (words[0] == "tap") 80 else millis(4, 500, 3000),
                            easing = easing)
                    }
                    "double-tap" -> {
                        count(5)
                        require(words[1] in setOf("px", "percent")) { "Coordinate unit must be px or percent" }
                        val max = if (words[1] == "percent") 100f else 32767f
                        val x = number(2, 0f, max); val y = number(3, 0f, max)
                        steps += Step.Stroke(destination(), words[1], x, y, x, y, 80,
                            taps = 2, gapMs = millis(4, 40, 300))
                    }
                    "pinch-in", "pinch-out", "swipe-two" -> {
                        val two = words[0] == "swipe-two"
                        val required = if (two) 9 else 7
                        require(words.size in required..required + 1) { "Wrong arguments: ${words[0]}" }
                        require(words[1] in setOf("px", "percent")) { "Coordinate unit must be px or percent" }
                        val max = if (words[1] == "percent") 100f else 32767f
                        val easing = words.getOrNull(required) ?: "linear"
                        require(easing in ActionGesture.EASINGS) { "Swipe speed must be linear, accelerate or decelerate" }
                        val ms = millis(required - 1, 1, 3000)
                        val x = number(2, 0f, max); val y = number(3, 0f, max)
                        val first: List<ActionGesture.Point>
                        val second: List<ActionGesture.Point>
                        if (two) {
                            val x2 = number(4, 0f, max); val y2 = number(5, 0f, max)
                            val dx = number(6, -max, max); val dy = number(7, -max, max)
                            require(dx != 0f || dy != 0f) { "Two fingers require different positions" }
                            first = ActionGesture.line(x, y, x2, y2, ms, easing)
                            second = first.map { it.copy(x = it.x + dx, y = it.y + dy) }
                        } else {
                            val start = number(4, 0.001f, max); val end = number(5, 0.001f, max)
                            require(if (words[0] == "pinch-in") start > end else start < end) { "Check the pinch start/end spacing" }
                            first = ActionGesture.line(x - start / 2, y, x - end / 2, y, ms, easing)
                            second = ActionGesture.line(x + start / 2, y, x + end / 2, y, ms, easing)
                        }
                        require((first + second).all { it.x in 0f..max && it.y in 0f..max }) { "Finger coordinates are outside the coordinate range" }
                        steps += Step.Stroke(destination(), words[1], first.first().x, first.first().y,
                            first.last().x, first.last().y, ms, first, easing, second)
                    }
                    "swipe-path", "swipe-two-path", "touch" -> {
                        require(headers["version"] == "2") { "Freehand swipes require version=2" }
                        require(words.size >= 4) { "Freehand swipe requires a unit and timed points" }
                        val tracks = words.drop(2).joinToString(" ").split('|')
                        require(if (words[0] == "touch") tracks.size in 1..2 else tracks.size == if (words[0] == "swipe-two-path") 2 else 1) { "Wrong number of freehand fingers" }
                        val path = ActionGesture.parse(words[1], tracks[0])
                        val second = tracks.getOrNull(1)?.let { ActionGesture.parse(words[1], it, startsAtZero = words[0] != "touch") }.orEmpty()
                        require(words[0] == "touch" || second.isEmpty() || second.map { it.ms } == path.map { it.ms }) { "Two fingers require matching sample times" }
                        steps += Step.Stroke(destination(), words[1], path.first().x, path.first().y,
                            path.last().x, path.last().y, maxOf(path.last().ms, second.lastOrNull()?.ms ?: 0), path, secondPath = second)
                    }
                    "scroll" -> {
                        count(5)
                        val speed = number(1, -40000f, 40000f)
                        require(kotlin.math.abs(speed) >= 50f) { "Scroll speed: 50..40000 dp/s, negative moves forward" }
                        steps += Step.Scroll(destination(), speed, millis(2, 1, 30000), number(3, 10f, 90f), number(4, 10f, 90f))
                    }
                    else -> throw IllegalArgumentException("Unknown step: ${words[0]}")
                }
                size += steps.size - before
                require(size <= MAX_STEPS) { "At most $MAX_STEPS steps" }
                original
            }
            require(blocks.isEmpty()) { "Missing end for control-flow block" }
            require(headers["version"] in setOf("1", "2")) { "Start the definition with version=1 or version=2" }
            require(steps.isNotEmpty()) { "No executable steps" }
            val timeout = (headers["timeout"] ?: "30").toLongOrNull()
            require(timeout != null && timeout in 1..300) { "timeout must be 1..300 seconds" }
            val screen = headers["screen"]?.let {
                val match = Regex("([0-9]+)x([0-9]+)@([0-3])").matchEntire(it)
                    ?: throw IllegalArgumentException("screen must be WIDTHxHEIGHT@ROTATION or current when saving")
                Screen(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
            }
            val flat = allSteps(steps)
            if (flat.any { it is Step.Stroke || it is Step.Scroll }) require(screen != null) { "Coordinates/scroll require screen=current or saved geometry" }
            flat.filterIsInstance<Step.Stroke>().forEach { it.tracks(screen!!) }
            return ActionDefinition(lines.joinToString("\n").trimEnd() + "\n", timeout * 1000, screen, steps.toList())
        }
    }
}
