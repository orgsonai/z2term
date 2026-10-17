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

    private val READ_ONLY = setOf("front", "look", "status", "shot", "notes", "note", "mark", "save", "allow", "pause", "say", "stop")

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
            // なぞる操作は z2-action の時刻つきの軌跡にする。各点を同じ間隔で通る。
            "drag" -> {
                val step = args.indexOf("--step").takeIf { it >= 0 }?.let { args[it + 1].toLong() } ?: 100L
                val points = args.filterIndexed { i, v -> v != "--step" && (i == 0 || args[i - 1] != "--step") }
                    .map { it.removePrefix("@") }
                Step("swipe-path px " + points.mapIndexed { i, xy -> "$xy,${i * step}" }.joinToString(" "), null, coordinates = true)
            }
            "tap", "long" -> {
                val press = if (verb == "tap") "click" else "long-click"
                fun at(x: Int, y: Int) = Step(if (verb == "tap") "tap px $x $y" else "long-press px $x $y 600", null, coordinates = true)
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
