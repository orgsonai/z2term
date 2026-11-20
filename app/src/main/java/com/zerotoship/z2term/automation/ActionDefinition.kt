package com.zerotoship.z2term.automation

/** Text is authoritative; shell text is preserved and other steps use a strict grammar. */
internal data class ActionDefinition(val text: String, val timeoutMs: Long, val screen: Screen?, val steps: List<Step>,
    /**
     * 人らしい操作の強さ (1.0.184、数字は 1.0.186)。0 はなし、1〜20 は大きいほど揺れる ([ActionHumanize])。
     * 見出しは `human=0..20`、`on` は [ActionHumanize.DEFAULT_LEVEL]、`off` は 0。
     */
    val humanLevel: Int = 0) {
    val human get() = humanLevel > 0
    /** `timeout=0`: no time limit. A root macro with it also has no limit on executed instructions. */
    val unlimited get() = timeoutMs == 0L
    data class Screen(val width: Int, val height: Int, val rotation: Int) {
        init { require(width in 1..32768 && height in 1..32768 && rotation in 0..3) { "Invalid screen geometry" } }
        override fun toString() = "${width}x${height}@$rotation"
    }
    sealed interface Step {
        /**
         * [count] null = forever. [until] (1.0.188): checked before every pass; the loop ends once it holds.
         * [target] is the app whose screen `ui` / `color` terms in [until] read.
         */
        data class Repeat(val count: Int?, val body: List<Step>, val until: String? = null,
            val target: String = CURRENT_TARGET) : Step
        /** `break` (1.0.188): leaves the innermost `repeat` at once. */
        data object Break : Step
        /**
         * `type 文字` / `enter` (1.0.189): the input field that has focus in [target]. [text] replaces its content
         * (empty clears it); null presses the keyboard's enter key. Password fields are refused.
         */
        data class Input(val target: String, val text: String?) : Step
        /** `tap ui 対象` (1.0.189): a finger tap at the centre of the element (works where `click` cannot). */
        data class UiTap(val target: String, val selector: ActionSelector) : Step
        /** [target] is the app whose screen `ui` / `color` terms read (as for the steps around it). */
        data class Branch(val condition: String, val yes: List<Step>, val no: List<Step>,
            val target: String = CURRENT_TARGET) : Step
        /** `wait-until` ([until]) / `wait-while`: poll [condition] for up to [ms] (0 = until the macro's own limit). */
        data class WaitFor(val target: String, val condition: String, val until: Boolean, val ms: Long) : Step
        data class Call(val name: String) : Step
        /**
         * `watch 条件 call 名前`: 書いたブロックが終わるまで [condition] を見張り、成立したら [name] を割り込みで呼ぶ。
         * 条件に `command` は使えない (見張りは短い間隔で繰り返すため)。
         */
        data class Watch(val target: String, val condition: String, val name: String) : Step
        /** `run-output シェル`: 標準出力に書かれた手順を検査して、その場で実行する。 */
        data class RunOutput(val target: String, val text: String) : Step
        data class Ui(val target: String, val operation: String, val selector: ActionSelector, val timeoutMs: Long = 0) : Step
        data class Wait(val ms: Long) : Step
        data class Launch(val packageName: String) : Step
        data class Key(val name: String) : Step
        data class Command(val text: String) : Step
        data class Stroke(val target: String, val unit: String, val x1: Float, val y1: Float,
            val x2: Float, val y2: Float, val ms: Long, val path: List<ActionGesture.Point> = emptyList(),
            val easing: String = "linear", val secondPath: List<ActionGesture.Point> = emptyList(),
            val taps: Int = 1, val gapMs: Long = 100,
            /** `drag`: [path] is points the finger must pass through (not a recorded trace). */
            val waypoints: Boolean = false) : Step {
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
        /** `drag` の点の数の上限。z2-ui の drag と揃える。 */
        const val MAX_DRAG_POINTS = 256
        /** 行頭に来る流れの語。編集画面でアクセント色にする対象。 */
        val FLOW_WORDS = setOf("repeat", "if", "else", "end", "call", "watch", "break")
        private val trailingComment = Regex("(?<=\\s)#(?=\\s|$)")
        /**
         * [line] の中でコメントが始まる位置 (無ければ null)。
         * 行頭 `#` はその行全体。行の途中は「空白 + `#` + 空白か行末」から。`text=#5` のように
         * `#` の直後に文字が続くものは本文。`command` の行はシェルにそのまま渡すので扱わない
         * (シェル自身が `#` をコメントとして読む)。
         */
        fun commentStart(line: String): Int? {
            val code = line.trimStart()
            if (code.startsWith('#')) return line.length - code.length
            val verb = code.split(Regex("\\s+"), limit = 2).first()
            if (verb in SHELL_VERBS) return null
            val found = trailingComment.find(line)?.range?.first ?: return null
            // 条件の `command` 項から後ろはシェルの文。`#` もシェルに渡す。
            if (verb == "repeat") {
                // `repeat … until 条件` の条件も、command の項から後ろはシェルの文。
                val until = Regex("\\suntil\\s").find(line) ?: return found
                val from = until.range.last
                val shell = ActionCondition.shellStart(line.substring(from))?.plus(from)
                return if (shell != null && found > shell) null else found
            }
            if (verb in CONDITION_VERBS) {
                var from = line.length - code.length + verb.length
                if (verb != "if") {
                    while (from < line.length && line[from].isWhitespace()) from++
                    while (from < line.length && !line[from].isWhitespace()) from++
                }
                val shell = ActionCondition.shellStart(line.substring(from))?.plus(from)
                if (shell != null && found > shell) return null
            }
            return found
        }
        val HEADERS = setOf("version", "timeout", "screen", "human")
        /** 後ろをそのままシェルに渡す命令。行末の `#` もシェルのもの。 */
        /** `type` の文字もそのまま入れる (1.0.189)。 */
        val SHELL_VERBS = setOf("command", "run-output", "type")
        /** 条件式を取る命令。`if 条件` / `wait-until ミリ秒 条件` / `wait-while ミリ秒 条件`。 */
        val CONDITION_VERBS = setOf("if", "wait-until", "wait-while")
        /** `wait-until` / `wait-while` の上限 (ミリ秒)。0 は無期限 (マクロ全体の制限時間と停止は効く)。 */
        const val MAX_CONDITION_WAIT_MS = 300_000L
        /** `type` で一度に入れる文字の上限。 */
        const val MAX_TYPE_CHARS = 4096
        /** コメントを除いた本文 (前後の空白も除く)。 */
        fun code(line: String): String = (commentStart(line)?.let { line.substring(0, it) } ?: line).trim()
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
        /**
         * `run-output` が出した手順。呼び出し元の画面寸法と対象を引き継いで検査する。見出しは書けない。
         * `call` / `run-output` / `watch` は断る (呼び出し先は実行前に検査・固定するため、出力から増やさない)。
         * 空の出力は「何もしない」。
         */
        fun fragment(output: String, screen: Screen?, target: String): List<Step> {
            if (output.lines().all { code(it).isEmpty() }) return emptyList()
            val head = "version=2\ntimeout=0\n" + (screen?.let { "screen=$it\n" } ?: "") + "target $target\n"
            val steps = try { parse(head + output, screen).steps } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("run-output: " + e.message, e)
            }
            require(allSteps(steps).none { it is Step.Call || it is Step.RunOutput || it is Step.Watch }) {
                "run-output cannot produce call, run-output or watch"
            }
            return steps
        }
        private data class Block(val kind: String, val argument: String, val parent: MutableList<Step>,
            // `repeat … until 条件` の条件 (1.0.188)。
            val target: String?, var yes: List<Step>? = null, val until: String? = null)
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
                val line = code(original)
                if (line.isEmpty()) return@map original
                require('\r' !in original) { "Invalid line ending" }
                // 見出しは値を書き換えて保存するので、行末のコメントは後ろに付け直す。
                val comment = original.trim().substring(line.length)
                if (!body && '=' in line && line.substringBefore('=').matches(Regex("[a-z]+"))) {
                    val key = line.substringBefore('=')
                    require(key in HEADERS && key !in headers) { "Unknown or duplicate header: $key" }
                    val value = line.substringAfter('=').let {
                        if (key == "screen" && it == "current") (current ?: error("screen=current requires z2-action save")).toString() else it
                    }
                    headers[key] = value
                    return@map "$key=$value$comment"
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
                if (words[0] in setOf("repeat", "if", "else", "end", "call", "break")) {
                    require(headers["version"] == "2") { "Control flow requires version=2" }
                    when (words[0]) {
                        "repeat", "if" -> {
                            var until: String? = null
                            var times = "forever"
                            if (words[0] == "repeat") {
                                // repeat N / repeat forever / repeat [N|forever] until 条件 (1.0.188)
                                val loop = Regex("repeat\\s+(?:(forever|[0-9]+)\\s+)?until\\s+(.+)").matchEntire(line)
                                if (loop != null) {
                                    until = loop.groupValues[2].trim()
                                    ActionCondition.validate(until)
                                    times = loop.groupValues[1].ifEmpty { "forever" }
                                } else { count(2); times = words[1] }
                                require(times == "forever" || times.toIntOrNull()?.let { it in 1..10000 } == true) {
                                    "repeat requires 1..10000 or forever (optionally followed by until CONDITION)"
                                }
                            } else ActionCondition.validate(line.dropWhile { !it.isWhitespace() }.trim())
                            require(blocks.size < MAX_NESTING) { "At most $MAX_NESTING nested blocks" }
                            blocks += Block(words[0], if (words[0] == "repeat") times
                                else line.dropWhile { !it.isWhitespace() }.trim(), steps, target, until = until)
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
                                block.argument.takeUnless { it == "forever" }?.toInt(), steps.toList(), block.until,
                                block.target ?: CURRENT_TARGET)
                            else Step.Branch(block.argument, block.yes ?: steps.toList(),
                                if (block.yes != null) steps.toList() else emptyList(), block.target ?: CURRENT_TARGET)
                            steps = block.parent; steps += closed; target = block.target
                        }
                        "call" -> { count(2); steps += Step.Call(macroName(words[1])); size++ }
                        "break" -> {
                            count(1)
                            require(blocks.any { it.kind == "repeat" }) { "break must be inside repeat" }
                            steps += Step.Break; size++
                        }
                    }
                    require(size <= MAX_STEPS) { "At most $MAX_STEPS steps including control flow" }
                    return@map original
                }
                val before = steps.size
                // `tap ui 対象` は座標の tap と別の手順 (1.0.189)。
                when (if (words[0] == "tap" && words.getOrNull(1) == "ui") "tap ui" else words[0]) {
                    "type", "enter" -> {
                        require(headers["version"] == "2") { "${words[0]} requires version=2" }
                        if (words[0] == "enter") { count(1); steps += Step.Input(destination(), null) }
                        else {
                            val text = line.substring(4).trimStart()
                            require(text.length <= MAX_TYPE_CHARS) { "type takes at most $MAX_TYPE_CHARS characters" }
                            steps += Step.Input(destination(), text)
                        }
                    }
                    "watch" -> {
                        require(headers["version"] == "2") { "watch requires version=2" }
                        // 最後の 2 語が `call 名前`。条件はその前すべて。
                        require(words.size >= 4 && words[words.size - 2] == "call") { "Use watch CONDITION call NAME" }
                        val condition = line.split(Regex("\\s+"), limit = 2).last().trim()
                            .removeSuffix(words.last()).trimEnd().removeSuffix("call").trim()
                        val expression = ActionCondition.parse(condition)
                        require(expression.terms.none { it is ActionCondition.Term.Shell }) { "watch conditions cannot use command" }
                        steps += Step.Watch(destination(), condition, macroName(words.last()))
                    }
                    "run-output" -> {
                        require(headers["version"] == "2") { "run-output requires version=2" }
                        val text = line.dropWhile { !it.isWhitespace() }.trimStart()
                        require(text.isNotBlank()) { "Empty shell command" }
                        steps += Step.RunOutput(destination(), text)
                    }
                    "wait-until", "wait-while" -> {
                        require(headers["version"] == "2") { "Condition waits require version=2" }
                        require(words.size >= 3) { "${words[0]} requires milliseconds and a condition" }
                        val ms = millis(1, 0, MAX_CONDITION_WAIT_MS)
                        val condition = line.split(Regex("\\s+"), limit = 3).last()
                        ActionCondition.validate(condition)
                        steps += Step.WaitFor(destination(), condition, words[0] == "wait-until", ms)
                    }
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
                    "tap ui" -> {
                        require(headers["version"] == "2") { "tap ui requires version=2" }
                        require(words.size >= 3) { "tap ui requires a selector" }
                        steps += Step.UiTap(destination(), ActionSelector.parse(line.split(Regex("\\s+"), limit = 3).last()))
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
                    // 指を離さずに点を順に通る (各区間を同じ時間で)。パズルのように、離すと終わる画面のためのもの。
                    // 記録した軌跡 (swipe-path) と違い、点は手で決めて速さは一定。再生は同じ時刻つきの軌跡で行う。
                    "drag" -> {
                        require(words.size >= 5) { "drag requires a unit, milliseconds per segment and at least two X,Y points" }
                        require(words[1] in setOf("px", "percent")) { "Coordinate unit must be px or percent" }
                        val step = millis(2, 20, 1000)
                        val maximum = if (words[1] == "percent") 100f else 32767f
                        val coordinates = words.drop(3).map { token ->
                            val parts = token.split(',')
                            val x = parts.getOrNull(0)?.toFloatOrNull()
                            val y = parts.getOrNull(1)?.toFloatOrNull()
                            require(parts.size == 2 && x != null && y != null && x in 0f..maximum && y in 0f..maximum) {
                                "drag points use X,Y within the coordinate range"
                            }
                            x to y
                        }
                        require(coordinates.size in 2..MAX_DRAG_POINTS) { "drag takes 2..$MAX_DRAG_POINTS points" }
                        val total = step * (coordinates.size - 1)
                        require(total <= ActionGesture.MAX_MS) { "The whole drag must finish within ${ActionGesture.MAX_MS} ms" }
                        val path = coordinates.mapIndexed { i, (x, y) -> ActionGesture.Point(x, y, i * step) }
                        steps += Step.Stroke(destination(), words[1], path.first().x, path.first().y,
                            path.last().x, path.last().y, total, path, waypoints = true)
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
            require(timeout != null && timeout in 0..300) { "timeout must be 0..300 seconds (0 = no limit)" }
            val screen = headers["screen"]?.let {
                val match = Regex("([0-9]+)x([0-9]+)@([0-3])").matchEntire(it)
                    ?: throw IllegalArgumentException("screen must be WIDTHxHEIGHT@ROTATION or current when saving")
                Screen(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
            }
            val flat = allSteps(steps)
            if (flat.any { it is Step.Stroke || it is Step.Scroll }) require(screen != null) { "Coordinates/scroll require screen=current or saved geometry" }
            val colors = flat.mapNotNull { when (it) { is Step.Branch -> it.condition; is Step.WaitFor -> it.condition
                is Step.Watch -> it.condition; is Step.Repeat -> it.until; else -> null } }
                .flatMap { ActionCondition.parse(it).terms }.filterIsInstance<ActionCondition.Term.Color>()
            if (colors.isNotEmpty()) {
                require(screen != null) { "Colour conditions require screen=current or saved geometry" }
                require(colors.all { it.x < screen.width && it.y < screen.height }) { "Colour points are outside the saved screen" }
            }
            flat.filterIsInstance<Step.Stroke>().forEach { it.tracks(screen!!) }
            val human = when (val value = headers["human"]) {
                null, "off" -> 0
                "on" -> ActionHumanize.DEFAULT_LEVEL
                else -> value.toIntOrNull()?.takeIf { it in 0..ActionHumanize.MAX_LEVEL }
                    ?: throw IllegalArgumentException("human must be on, off or 0..${ActionHumanize.MAX_LEVEL}")
            }
            return ActionDefinition(lines.joinToString("\n").trimEnd() + "\n", timeout * 1000, screen, steps.toList(), human)
        }
    }
}
