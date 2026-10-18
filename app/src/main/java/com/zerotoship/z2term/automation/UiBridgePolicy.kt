package com.zerotoship.z2term.automation

/**
 * `z2-ui`（AI からのアプリ操作）の判定のうち、Android に依存しない部分。
 *
 * 可否の判定はすべて [UiBridge] の受付 1 か所で行い、ここはその材料（段階・常に断る画面・
 * 要素の指定の読み方・要素の種類）だけを持つ。設計は `docs/ja/AI-BRIDGE.md`。
 */
internal object UiBridgePolicy {
    /** アプリごとの段階。[rank] が大きいほど AI にできることが広い。 */
    enum class Level(val key: String, val rank: Int) {
        OFF("off", 0), LOOK("look", 1), ASK("ask", 2), AUTO("auto", 3);

        companion object {
            fun parse(value: String): Level = entries.firstOrNull { it.key == value }
                ?: throw Refusal("bad_args", "Level must be off, look, ask or auto")
        }
    }

    /** 一覧に無いアプリに使う行の名前。 */
    const val DEFAULT_PATTERN = "*"
    val DEFAULT_LEVEL = Level.LOOK

    /**
     * 常に [Level.OFF] として扱う画面。AI がこの許可そのもの・ユーザー補助の設定・
     * 通知欄の承認ボタンへ届かないようにする。機種ごとに名前の違う設定画面は、
     * 実行時に Intent の解決先からも足す（[UiBridge.protectedPackages]）。
     */
    val PROTECTED = setOf(
        "com.android.settings", "com.android.systemui",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
    )

    fun level(pkg: String, protected: Set<String>, levels: Map<String, Level>): Level = when {
        pkg in protected -> Level.OFF
        else -> levels[pkg] ?: levels[DEFAULT_PATTERN] ?: DEFAULT_LEVEL
    }

    /** 人の承認が要る変更か。狭める変更はそのまま反映する。 */
    fun widens(old: Level, new: Level) = new.rank > old.rank

    /** 「このあと N 分は許可」の N の既定値。 */
    const val GRANT_DEFAULT_MINUTES = 5
    /** 設定画面のスライダーの右端。 */
    const val GRANT_SLIDER_MAX = 60
    /**
     * 「このあと N 分は許可」の N。画面のスライダーは 1..[GRANT_SLIDER_MAX] だが、保存できる範囲は 1..1440。
     * 61 分以上は利用者の指示（2026-10-18）による裏設定で、docs・--help には書かない。
     * 入口は (1) 設定項目の長押しで出る数値入力 (2) `z2-ui allow --grant <分>`。
     * 延ばす変更は裏設定でも通知での承認を必須にする（AI が自分で延ばせないように）。
     * 上限は `z2-screen keepon` の 24h に揃えた。それ以上が要るアプリは `auto` にすればよい。
     */
    const val GRANT_MAX_MINUTES = 1440

    fun grantMinutes(value: Int) = value.coerceIn(1, GRANT_MAX_MINUTES)

    /** `wait` / `wait-change` の待ち時間（ミリ秒）。 */
    const val WAIT_DEFAULT_MS = 5000L
    const val WAIT_MAX_MS = 30_000L

    fun waitMs(value: String?): Long {
        if (value == null) return WAIT_DEFAULT_MS
        val ms = value.toLongOrNull() ?: throw Refusal("bad_args", "Wait time must be milliseconds")
        if (ms !in 1..WAIT_MAX_MS) throw Refusal("bad_args", "Wait time must be 1..$WAIT_MAX_MS milliseconds")
        return ms
    }

    /**
     * 画面の動きが止まったか。縮めた 2 枚の明るさ (0..255) を比べ、次の両方を満たせば止まったとみる。
     *  - 全体の差の平均が [SETTLE_THRESHOLD] 未満。
     *  - [SETTLE_BLOCK] 四方の区画ごとの差の平均が、どれも [SETTLE_BLOCK_THRESHOLD] 未満。
     *    全体の平均だけだと、画面の一部 (数個の駒が消える・小さなダイアログ・数字の表示) だけが動いている
     *    最中を「止まった」とみてしまう (面積が 1 割に満たないと平均が薄まる)。
     * 背景が少しずつ動き続ける画面 (きらめき等) で永遠に待たないよう、まばらな小さな差は無視する。
     * [width] は縮めた画像の幅 (区画に分けるため)。
     * [blocks] = false なら区画を見ない (1.0.41 までの判定)。一部がずっと動いている画面 (待機中のキャラクター等) で、
     * 区画の判定だと最大待ちまで止まらないときに使う (`--settle-check loose`・AI 操作の設定)。
     */
    fun settled(previous: IntArray, current: IntArray, width: Int = previous.size, blocks: Boolean = true): Boolean {
        if (previous.isEmpty() || previous.size != current.size) return false
        var sum = 0L
        for (i in previous.indices) sum += kotlin.math.abs(previous[i] - current[i])
        if (sum.toDouble() / previous.size >= SETTLE_THRESHOLD) return false
        if (!blocks) return true
        val w = width.coerceIn(1, previous.size)
        val h = previous.size / w
        for (top in 0 until h step SETTLE_BLOCK) for (left in 0 until w step SETTLE_BLOCK) {
            var block = 0L
            var n = 0
            for (y in top until minOf(top + SETTLE_BLOCK, h)) for (x in left until minOf(left + SETTLE_BLOCK, w)) {
                block += kotlin.math.abs(previous[y * w + x] - current[y * w + x]); n++
            }
            if (block.toDouble() / n >= SETTLE_BLOCK_THRESHOLD) return false
        }
        return true
    }

    const val SETTLE_THRESHOLD = 3.0
    const val SETTLE_BLOCK = 4
    const val SETTLE_BLOCK_THRESHOLD = 10.0
    /** `--shot` / `shot --stable` の既定の最大待ち (ミリ秒)。 */
    const val SETTLE_DEFAULT_MS = 3000L
    const val SETTLE_MAX_MS = 10_000L

    /** 座標の長押し (`long @X,Y --hold MS`) で指を置く長さ。 */
    const val HOLD_DEFAULT_MS = 600L
    const val HOLD_MIN_MS = 100L
    const val HOLD_MAX_MS = 10_000L

    /**
     * 前に返した画像と同じ画面か (`--if-changed`)。[settled] より厳しく、縮めた明るさが 1 点でも
     * [SAME_THRESHOLD] を超えて違えば「変わった」とみる。数字 1 つの書き換えも見落とさないため。
     */
    fun same(previous: IntArray, current: IntArray): Boolean =
        previous.size == current.size && previous.indices.all { kotlin.math.abs(previous[it] - current[it]) <= SAME_THRESHOLD }

    const val SAME_THRESHOLD = 8

    /**
     * 空白でつないで 1 つにされた引数を分ける (`"540,1200 600,1300"` → 2 つ、`"brighter 125"` → 2 つ)。
     * AI が引用符で 1 語にまとめて送り、`bad_args` で 1 往復むだにするのを防ぐ。
     * ⚠ 数・座標・決まった語だけの引数に使う。文字を入れる・探す引数 (`type` / `text=` など) には使わない。
     */
    fun spread(args: List<String>): List<String> = args.flatMap { arg -> arg.split(' ', '\t', '\n').filter { it.isNotEmpty() } }

    /** 点ごとの中央値 (0xRRGGBB)。赤・緑・青をそれぞれ並べて真ん中を取る。光沢や縁の 1 点に引っぱられない。 */
    fun median(pixels: IntArray): Int {
        if (pixels.isEmpty()) return 0
        fun channel(shift: Int) = pixels.map { (it shr shift) and 0xff }.sorted()[pixels.size / 2]
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** `z2-ui colors` が返す色の名前。AI が画像を読まずに盤面や状態を見分けるための粗い分け方。 */
    val COLOR_NAMES = listOf("black", "gray", "white", "red", "orange", "brown", "yellow", "green", "cyan", "blue", "purple", "pink")

    fun colorName(rgb: Int): String {
        val r = (rgb shr 16) and 0xff
        val g = (rgb shr 8) and 0xff
        val b = rgb and 0xff
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val v = max / 255f
        val s = if (max == 0) 0f else (max - min) / max.toFloat()
        if (v < 0.2f) return "black"
        if (s < 0.2f) return when { v > 0.85f -> "white"; else -> "gray" }
        val d = (max - min).toFloat()
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return when {
            h < 15f || h >= 345f -> if (s < 0.5f && v > 0.7f) "pink" else "red"
            h < 45f -> if (v < 0.6f) "brown" else "orange"
            h < 70f -> "yellow"
            h < 160f -> "green"
            h < 195f -> "cyan"
            h < 255f -> "blue"
            h < 290f -> "purple"
            else -> "pink"
        }
    }

    fun hex(rgb: Int) = "#%06x".format(rgb and 0xffffff)

    /** 点の色 (0xRRGGBB) の明るさの平均 (0..255)。重みは [settled] の縮小画像と同じ。 */
    fun brightness(rgb: IntArray): Double = if (rgb.isEmpty()) 0.0 else rgb.sumOf { c ->
        (((c shr 16) and 0xff) * 3 + ((c shr 8) and 0xff) * 6 + (c and 0xff)) / 10.0
    } / rgb.size

    /**
     * `colors --until` の条件。画面の色が条件を満たすまで、撮り直しながら待つ (画像は作らない)。
     * 操作できる番になると盤面が明るくなる・ランプが緑になる・読み込みの印が消える、のような
     * 「決まった場所の色」で分かる状態を、AI が画像を読まずに待つためのもの。
     */
    sealed interface Until {
        /** 判定する。[first] は最初に読んだ色 ([Changed] が比べる相手)。 */
        fun met(rgb: IntArray, first: IntArray): Boolean

        data class Brighter(val level: Int) : Until { override fun met(rgb: IntArray, first: IntArray) = brightness(rgb) > level }
        data class Darker(val level: Int) : Until { override fun met(rgb: IntArray, first: IntArray) = brightness(rgb) < level }
        /** どれかの点の色の名前が、最初に読んだときから変わった。 */
        object Changed : Until {
            override fun met(rgb: IntArray, first: IntArray) = rgb.indices.any { colorName(rgb[it]) != colorName(first[it]) }
        }
        /** すべての点が [name] の色。 */
        data class Named(val name: String) : Until { override fun met(rgb: IntArray, first: IntArray) = rgb.all { colorName(it) == name } }

        companion object {
            /** `brighter N` / `darker N` / `changed` / 色の名前。読めなければ null。読んだ語の数も返す。 */
            fun parse(words: List<String>): Pair<Until, Int>? {
                val level = words.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..255 }
                return when (val head = words.firstOrNull()) {
                    "brighter" -> level?.let { Brighter(it) to 2 }
                    "darker" -> level?.let { Darker(it) to 2 }
                    "changed" -> Changed to 1
                    in COLOR_NAMES -> Named(head!!) to 1
                    else -> null
                }
            }
        }
    }

    const val UNTIL_DEFAULT_MS = 10_000L
    const val UNTIL_MAX_MS = 30_000L

    /** 操作の相手の指定。 */
    sealed interface Target {
        /** 直前の `look` の要素番号（`#2`）。 */
        data class Ref(val n: Int) : Target
        /** その場で探す完全一致（`text=次へ` / `desc=…` / `id=…`）。 */
        data class Find(val selector: ActionSelector) : Target
        /** 座標（`@540,1200`、画面の px）。要素が取れない画面の最後の手段。 */
        data class Point(val x: Int, val y: Int) : Target
    }

    fun target(value: String): Target = when {
        value.startsWith("#") -> Target.Ref(value.drop(1).toIntOrNull()?.takeIf { it >= 1 }
            ?: throw Refusal("bad_args", "Element numbers look like #2"))
        value.startsWith("@") -> {
            val parts = value.drop(1).split(',')
            val x = parts.getOrNull(0)?.toIntOrNull()
            val y = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size != 2 || x == null || y == null || x < 0 || y < 0)
                throw Refusal("bad_args", "Coordinates look like @540,1200")
            Target.Point(x, y)
        }
        else -> Target.Find(try { ActionSelector.parse(value) } catch (e: IllegalArgumentException) {
            throw Refusal("bad_args", e.message ?: "Use #N, @X,Y, text=, desc= or id=")
        })
    }

    /**
     * `look --tree` の親。各要素について、一覧の中で一番近い祖先の添字 (無ければ -1)。
     * [paths] は根からの子の添字の道筋で、幅優先の順 (祖先が子孫より先) に並んでいること。
     */
    fun parents(paths: List<List<Int>>): IntArray {
        val index = HashMap<List<Int>, Int>()
        return IntArray(paths.size) { i ->
            val path = paths[i]
            val parent = (path.size - 1 downTo 0).firstNotNullOfOrNull { index[path.take(it)] } ?: -1
            index[path] = i
            parent
        }
    }

    /** 要素の種類。AI が意味を読み取れる最小限の語彙に畳む。 */
    fun role(className: String?, editable: Boolean, password: Boolean, checkable: Boolean,
        clickable: Boolean, scrollable: Boolean): String {
        val name = className.orEmpty().substringAfterLast('.')
        return when {
            password -> "password"
            editable -> "input"
            checkable && (name.contains("Switch") || name.contains("Toggle")) -> "switch"
            checkable -> "check"
            scrollable -> "list"
            name.endsWith("Button") || clickable -> "button"
            name.endsWith("ImageView") || name == "Image" -> "image"
            name.endsWith("TextView") || name == "Text" -> "text"
            else -> "other"
        }
    }

    /** 画面に出す文字の整形。制御文字を空白にし、長すぎるものは切る。 */
    fun clean(raw: CharSequence?, limit: Int = 512): String? {
        val text = raw?.toString()?.map { if (it.isISOControl()) ' ' else it }?.joinToString("")?.trim()
        if (text.isNullOrEmpty()) return null
        return if (text.length <= limit) text else text.take(limit) + "…"
    }
}

/**
 * `z2-ui save` の材料。成功した操作を z2-action の 1 行へ書き直す (Android 非依存・UiBridgePolicyTest)。
 *
 * ⚠ **書き直せない操作は黙って落とさない。** 文字入力・確定キー・スクロール・画面の変化待ち・URI を開く
 * ・通知は z2-action に同じ命令が無い。落として保存すると、再生したときに途中が抜けた別の手順になる。
 * そうした操作を含む区間は保存を断り、`mark` で区切り直してもらう。
 */
internal object UiBridgeReplay {
    /** `#N` で押した要素の呼び名と中心。番号は再生時には通じないので、ID → 文字 → 説明 → 座標の順に置き換える。 */
    data class Named(val id: String?, val text: String?, val desc: String?, val x: Int, val y: Int)

    /** [line] が z2-action の 1 行。書き直せないときは [unsupported] に操作名。読むだけの操作は両方 null。 */
    data class Step(val line: String?, val unsupported: String?, val coordinates: Boolean = false)

    private val READ_ONLY = setOf("front", "look", "status", "shot", "colors", "notes", "note", "mark", "save", "allow", "pause", "say", "stop")

    private fun selector(named: Named): String? = listOfNotNull(
        named.id?.let { "id=$it" },
        // 切り詰めた文字 (…) は元の表示と一致しないので使わない。
        named.text?.takeIf { !it.endsWith("…") }?.let { "text=$it" },
        named.desc?.takeIf { !it.endsWith("…") }?.let { "desc=$it" },
    ).firstOrNull { runCatching { ActionSelector.parse(it) }.isSuccess }

    fun step(verb: String, args: List<String>, named: (Int) -> Named?): Step {
        if (verb in READ_ONLY) return Step(null, null)
        return when (verb) {
            "open" -> if (':' in args[0]) Step(null, "open URI") else Step("launch ${args[0]}", null)
            "key" -> Step("key ${args[0]}", null)
            "swipe" -> Step("swipe px ${args.take(4).joinToString(" ")} ${args.getOrNull(4) ?: 300}", null, coordinates = true)
            "wait" -> Step("wait-ui ${args.getOrNull(1) ?: UiBridgePolicy.WAIT_DEFAULT_MS} ${args[0]}", null)
            // なぞる操作は画面操作の「点を順になぞる」(drag) にする。各点を同じ間隔で通る。
            "drag" -> {
                val flags = setOf("--step", "--hold")
                fun flag(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args[it + 1].toLong() }
                val step = flag("--step") ?: 100L
                val points = args.filterIndexed { i, v -> v !in flags && (i == 0 || args[i - 1] !in flags) }
                    .map { it.removePrefix("@") }
                // z2-action の drag は始点で止まれないので、始点を重ねて同じだけ止める (1 区間 = step ms)。
                val hold = flag("--hold")?.let { ((it + step - 1) / step).toInt() } ?: 0
                Step("drag px $step " + (List(hold) { points[0] } + points).joinToString(" "), null, coordinates = true)
            }
            "tap", "long" -> {
                val press = if (verb == "tap") "click" else "long-click"
                // `--hold` は座標の長押しだけ。z2-action の長押しは 500..3000 ms なので、外れる長さは保存を断る。
                val hold = args.indexOf("--hold").takeIf { it >= 0 }?.let { args[it + 1].toLong() } ?: UiBridgePolicy.HOLD_DEFAULT_MS
                fun at(x: Int, y: Int) = when {
                    verb == "tap" -> Step("tap px $x $y", null, coordinates = true)
                    hold !in 500L..3000L -> Step(null, "long --hold $hold")
                    else -> Step("long-press px $x $y $hold", null, coordinates = true)
                }
                when (val target = UiBridgePolicy.target(args[0])) {
                    is UiBridgePolicy.Target.Find -> Step("$press ${target.selector}", null)
                    is UiBridgePolicy.Target.Point -> at(target.x, target.y)
                    is UiBridgePolicy.Target.Ref -> {
                        val element = named(target.n) ?: return Step(null, "$verb #${target.n}")
                        selector(element)?.let { Step("$press $it", null) } ?: at(element.x, element.y)
                    }
                }
            }
            else -> Step(null, verb)
        }
    }

    /** 記録した手順 ([steps] と各操作の時刻) から z2-action の定義を作る。間は実際の間隔 (最大 2 秒) を待つ。 */
    fun definition(steps: List<Pair<Step, Long>>): String {
        val lines = mutableListOf<String>()
        var previous: Long? = null
        for ((step, at) in steps) {
            val line = step.line ?: continue
            val gap = previous?.let { (at - it).coerceIn(0, 2000) }
            if (gap != null && gap >= 200) lines += "wait $gap"
            lines += line
            previous = at
        }
        if (lines.size > 64) throw Refusal("too_long", "z2-action allows 64 steps; mark and save shorter parts")
        val waits = lines.filter { it.startsWith("wait ") }.sumOf { it.substringAfter(' ').toLong() }
        val timeout = (waits / 1000 + lines.size * 5 + 10).coerceIn(30, 300)
        val header = listOfNotNull("version=2", "timeout=$timeout", "screen=current".takeIf { steps.any { it.first.coordinates } })
        return (header + lines).joinToString("\n") + "\n"
    }
}

/** AI が理由で次の手を分けられるよう、決まった [code] を持つ失敗。 */
internal class Refusal(val code: String, message: String) : Exception(message)
