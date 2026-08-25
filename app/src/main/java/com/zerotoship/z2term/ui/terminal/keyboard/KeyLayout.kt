package com.zerotoship.z2term.ui.terminal.keyboard

/**
 * カスタムキーボードの**レイアウト定義**（0.8.402・段階 1a）。
 *
 * ⚠ **この段階では誰もまだ使っていない。** 既存の描画 ([TerminalKeyboard]) は 5 段べた書きの
 * ままで、1 ドットも変えていない。まず**表現できること**をテストで固めてから描画を移す
 * （いきなり描画ごと差し替えると、壊れたときに「モデルが悪いのか描画が悪いのか」を切り分け
 * られなくなる）。
 *
 * ## 設計の芯: 特別扱いをなくす
 *
 * いまは ESC / ⌫ / 面切替がそれぞれ専用の Composable で、隠し機能もその中に直接書いてある。
 * ここでは**すべてのキーを同じ形**で表す。そうすれば「ESC の上下フリック」も「⌫ の左右
 * フリック」も、利用者が後から作れる**ただの割り当て**になる。
 *
 * ## 層
 *
 *  1. **器**   … [KeyLayout] → [KeyRow] → [KeySlot] → [SlotContent]（キー 1 つ or 分割）
 *  2. **イベント** … [KeyGesture]（タップ / 4 方向 / 長押し / ダブルタップ）
 *  3. **アクション** … [KeyAction] の**列**。`Ctrl+A` → `d` のような連鎖も 1 キーに書ける
 *  4. **レイヤー** … [KeyDef.layers]（⇧ のとき / 記号のとき / 自作の Fn …）
 *  5. **面**   … [KeyLayout] を複数持って巡回する（呼出し側の仕事）
 */

/** キーに割り当てられる入力イベント。⚠ 「1/2/3 フリック」という区分はここには無い。 */
enum class KeyGesture(val id: String) {
    TAP("tap"),
    UP("up"),
    DOWN("down"),
    LEFT("left"),
    RIGHT("right"),
    LONG_PRESS("long"),
    DOUBLE_TAP("double");

    companion object {
        fun byId(id: String): KeyGesture? = entries.firstOrNull { it.id == id }

        /** フリックの 4 方向だけ（補助表示やエディタのテンプレートで使う）。 */
        val FLICKS = listOf(UP, DOWN, LEFT, RIGHT)
    }
}

/**
 * 名前で指す特殊キー。
 *
 * ⚠ **文字列ではなく ID で持つ**。矢印や Home はアプリケーションカーソルモード (DECCKM) で
 * 送るバイト列が変わるので、レイアウト側にバイト列を焼き込むと**モードに追従できなくなる**。
 */
enum class NamedKey(val id: String) {
    ESC("esc"), TAB("tab"), ENTER("enter"), BACKSPACE("backspace"), DELETE("delete"),
    UP("up"), DOWN("down"), LEFT("left"), RIGHT("right"),
    HOME("home"), END("end"), PAGE_UP("pgup"), PAGE_DOWN("pgdn"), INSERT("insert"),
    F1("f1"), F2("f2"), F3("f3"), F4("f4"), F5("f5"), F6("f6"),
    F7("f7"), F8("f8"), F9("f9"), F10("f10"), F11("f11"), F12("f12");

    companion object {
        fun byId(id: String): NamedKey? = entries.firstOrNull { it.id == id }
    }
}

/** 修飾キー。押すたびに切り替わる（⇧ だけは 3 状態 = [ShiftState]）。 */
enum class ModKey(val id: String) {
    SHIFT("shift"), CTRL("ctrl"), ALT("alt");

    companion object {
        fun byId(id: String): ModKey? = entries.firstOrNull { it.id == id }
    }
}

/** キーから呼べるアプリ側の動作。⚠ **逃げ場**になるものは [KeyLayout.hasEscapeHatch] が数える。 */
enum class AppAction(val id: String) {
    /** 次の面へ（あ / ABC / 12 の巡回）。 */
    NEXT_FACE("next_face"),

    /** 貼り付けパッド / 絵文字パッドを開く（同じキーをもう一度押すと閉じる）。 */
    PAD_PASTE("pad_paste"),
    PAD_EMOJI("pad_emoji"),

    /** 開いているパッドを閉じる（パッドの下に出る行の `×`）。 */
    CLOSE_PAD("close_pad"),

    /** 内蔵キーボードを閉じる / OS のキーボードへ渡す。 */
    HIDE_KEYBOARD("hide_keyboard"),
    SWITCH_IME("switch_ime"),

    /** 設定を開く。⚠ これも逃げ場になる。 */
    SETTINGS("settings");

    companion object {
        fun byId(id: String): AppAction? = entries.firstOrNull { it.id == id }

        /** ここから設定・別の面へ抜けられる動作（レイアウトに 1 つは要る）。 */
        val ESCAPE_HATCHES = setOf(NEXT_FACE, SETTINGS, HIDE_KEYBOARD, SWITCH_IME)
    }
}

/**
 * キーを押したときに起きること。最後はすべて「PTY へ送るバイト列」か「アプリの操作」に落ちる。
 *
 * ⭐ 1 キーには**列**として並べられる（[KeyDef.bindings] の値が `List<KeyAction>`）。
 * `Ctrl+A` → `d` のような 2 手を 1 キーにできる。単発は要素 1 個の列というだけ。
 */
sealed interface KeyAction {
    /** 任意の文字列を送る。1 文字も定型文も同じ扱い。⇧ の大文字化はここに掛かる。 */
    data class Text(val text: String) : KeyAction

    /** 名前で指す特殊キー（[NamedKey]）。モードに追従させるため ID で持つ。 */
    data class Named(val key: NamedKey) : KeyAction

    /**
     * 修飾つきの一撃（`Ctrl+C` / `Alt+F` …）。補助バー相当はこれ。
     * ⚠ [mods] が空なら [Text] / [Named] と同じ意味になるので、エディタ側で作らせない。
     */
    data class Chord(
        val mods: Set<ModKey>,
        val text: String? = null,
        val key: NamedKey? = null,
    ) : KeyAction

    /**
     * 生バイトをそのまま送る逃げ道（`ESC [ 1 ; 5 C` のような列）。
     * ⚠ z2term が名前を持っていない列も送れるようにするための最後の手段。
     */
    data class Raw(val bytes: ByteArray) : KeyAction {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Raw && bytes.contentEquals(other.bytes))
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** 修飾のトグル（sticky）。⇧ は OFF → 1 回だけ → 固定 の 3 状態を回る。 */
    data class Modifier(val mod: ModKey) : KeyAction

    /** レイヤーの切替（`shift` / `sym` / 自作の `fn` …）。⚠ 切替もアクションなので自作できる。 */
    data class Layer(val layer: String, val sticky: Boolean = false) : KeyAction

    /** アプリ側の動作（面切替・パッド・設定 …）。 */
    data class App(val action: AppAction) : KeyAction

    /** 登録済みのスニペットを挿入する。 */
    data class Snippet(val id: String) : KeyAction

    /** マクロを実行する。 */
    data class Macro(val name: String) : KeyAction
}

/**
 * キー 1 つ。
 *
 * @param label 表に出す文字。空なら描画側が [bindings] の TAP から起こす。
 * @param bindings どのイベントで何が起きるか。載っていない [KeyGesture] は「割り当て無し」。
 * @param hintGestures 行き先をキーの上に小さく出す方向（0.8.407）。⚠ **方向ごとに持つ** —
 *   英字キーは上・左右だけ出して下（大文字）は出さず、貼り付けキーは上下だけ出す。キー単位の
 *   ON/OFF では今の見た目を表せない。複数選択の一括変更（要望）は「全部入れる / 空にする」。
 * @param repeatInitialMs 長押しから連打が始まるまで。⚠ ⌫ だけ 500ms と遅い（誤爆を減らすため）。
 * @param repeatIntervalMs 連打の間隔。
 * @param pressFeedback 押している間、背景を明るい緑にするか。⚠ `space` と ⇧ は変えない。
 * @param flickOnRelease フリックを**指を離したとき**に確定するか（0.8.407）。⚠ 文字キーは
 *   離すまで確定しない（途中で方向を変えられる・確定する文字がポップアップに出る）が、
 *   **ESC / ⌫ / 貼り付けの入口はしきい値を超えた瞬間に発火する**。行き先を出さない隠し操作
 *   なので、迷う余地が無く、そのぶん速い。ここを揃えると操作感が変わる。
 * @param labelTone ラベルの色。`space` だけ控えめ。
 * @param highlighted 押していなくても目立たせる（緑地）。パッドの `×` のように、
 *   「いまここを押せば戻れる」と分かってほしいキーに使う。
 * @param repeatable 長押しで連打するか（⌫ や矢印）。⚠ 修飾やレイヤー切替では OFF にする。
 * @param layers 状況別の上書き。キーは layer 名（`shift` / `sym` / 自作）。
 *   ⚠ **差分ではなく丸ごと差し替え**にする。差分にすると「どこが継承でどこが上書きか」が
 *   エディタで見えず、直したつもりが直らない事故になる。
 */
data class KeyDef(
    val label: String = "",
    val bindings: Map<KeyGesture, List<KeyAction>> = emptyMap(),
    val hintGestures: Set<KeyGesture> = emptySet(),
    val repeatable: Boolean = false,
    val repeatInitialMs: Long = DEFAULT_REPEAT_INITIAL_MS,
    val repeatIntervalMs: Long = DEFAULT_REPEAT_INTERVAL_MS,
    val pressFeedback: Boolean = true,
    val flickOnRelease: Boolean = true,
    val highlighted: Boolean = false,
    val labelTone: LabelTone = LabelTone.PRIMARY,
    val fontRole: KeyFontRole = KeyFontRole.NORMAL,
    val layers: Map<String, KeyDef> = emptyMap(),
) {
    /** [layer] での姿。無ければ自分自身。 */
    fun onLayer(layer: String?): KeyDef = if (layer == null) this else layers[layer] ?: this

    /** [gesture] に割り当てられたアクション列（無ければ空）。 */
    fun actionsFor(gesture: KeyGesture): List<KeyAction> = bindings[gesture].orEmpty()

    /** フリックの割り当てが 1 つでもあるか。 */
    fun hasFlick(): Boolean = KeyGesture.FLICKS.any { bindings.containsKey(it) }

    /** [gesture] の行き先を、キーの上に小さく出すか。 */
    fun showsHintFor(gesture: KeyGesture): Boolean = gesture in hintGestures

    companion object {
        /** 長押し連打の既定。 */
        const val DEFAULT_REPEAT_INITIAL_MS = 400L
        const val DEFAULT_REPEAT_INTERVAL_MS = 55L

        /** タップで文字を送るだけのキー（一番よく使う形）。 */
        fun text(label: String, send: String = label, fontRole: KeyFontRole = KeyFontRole.MAIN): KeyDef =
            KeyDef(
                label = label,
                bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.Text(send))),
                fontRole = fontRole,
            )

        /** タップで特殊キーを送るキー。 */
        fun named(
            label: String,
            key: NamedKey,
            repeatable: Boolean = false,
            fontRole: KeyFontRole = KeyFontRole.NORMAL,
        ): KeyDef =
            KeyDef(
                label = label,
                bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.Named(key))),
                repeatable = repeatable,
                fontRole = fontRole,
            )

        /** 修飾のトグルキー（CTRL / ALT / ⇧）。 */
        fun modifier(label: String, mod: ModKey, fontRole: KeyFontRole = KeyFontRole.SMALL): KeyDef =
            KeyDef(
                label = label,
                bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.Modifier(mod))),
                fontRole = fontRole,
            )
    }
}

/** ラベルの色の役どころ（0.8.407）。⚠ `space` だけ控えめな色で描くために要る。 */
enum class LabelTone(val id: String) {
    /** ふつうのキー。 */
    PRIMARY("primary"),

    /** 主役ではないキー（`space`）。字を控えめにして、隣の文字キーを目立たせる。 */
    SECONDARY("secondary");

    companion object {
        fun byId(id: String): LabelTone? = entries.firstOrNull { it.id == id }
    }
}

/**
 * ラベルの大きさの役割（0.8.404）。実寸は [KeyboardStyle] が持つので、レイアウト側は
 * 「どの役どころか」だけを持つ — キーボードの大きさ設定で全部が一緒に伸び縮みする。
 */
enum class KeyFontRole(val id: String) {
    /** 機能キーの小さめ（ESC / TAB / CTRL / ALT / `?#`）。`keyFontSp - 3`。 */
    SMALL("small"),

    /** ふつうの機能キー（⏎ / 矢印 / 面の切替）。`keyFontSp`。 */
    NORMAL("normal"),

    /** 打つためのキー（英字・数字）。`mainKeyFontSp` で少し大きい。 */
    MAIN("main");

    companion object {
        fun byId(id: String): KeyFontRole? = entries.firstOrNull { it.id == id }
    }
}

/** 分割の向き。⭐ **縦にも横にも割れる**（利用者要望。縦だけでは選べる形が少なすぎた）。 */
enum class SplitDir(val id: String) {
    VERTICAL("v"),    // 上下に分ける
    HORIZONTAL("h");  // 左右に分ける

    companion object {
        fun byId(id: String): SplitDir? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 枠の中身。キー 1 つ、または分割。
 *
 * ⭐ **分割は向き自由で深さ 2 まで**（[KeyLayout.validate] が数える）。これで
 * 「矢印 4 つを 1 枠に田の字で置く」「上半分に F キー・下半分に数字」まで表せる。
 * ⛔ 3 段目は許さない — 指で目的の区画に辿り着けなくなる。
 */
sealed interface SlotContent {
    data class Single(val key: KeyDef) : SlotContent
    data class Split(val dir: SplitDir, val parts: List<SlotPart>) : SlotContent
}

/** 分割された 1 区画。[ratio] は同じ分割の中での取り分。 */
data class SlotPart(val content: SlotContent, val ratio: Float = 1f)

/**
 * 横幅。
 *
 * - [Auto] … 段の残りを**均等に分ける**（既定）
 * - [Fixed] … 「均等 1 枠分の何倍か」で固定する
 *
 * ⭐ 要望どおりの再配分がこれで成立する: 1 つ固定 → 残りが均等に再配分 → もう 1 つ固定 →
 * また残りだけが再配分（固定したキーは動かない）。計算は [KeyRow.weights]。
 */
sealed interface KeyWidth {
    data object Auto : KeyWidth
    data class Fixed(val ratio: Float) : KeyWidth
}

/** 段の中の 1 枠。 */
data class KeySlot(val content: SlotContent, val width: KeyWidth = KeyWidth.Auto) {
    companion object {
        fun of(key: KeyDef, width: KeyWidth = KeyWidth.Auto): KeySlot =
            KeySlot(SlotContent.Single(key), width)
    }
}

/** 1 段。 */
data class KeyRow(val slots: List<KeySlot>) {

    /**
     * 各枠の重み（Compose の `Modifier.weight` にそのまま渡せる）。
     *
     * 段の予算は「**枠の数**」= 全部 [KeyWidth.Auto] なら全員 1.0。[KeyWidth.Fixed] の合計を
     * 予算から引いた残りを Auto の数で割る。
     *
     * ⚠ 固定が予算を食い尽くしても、Auto を 0 にはしない ([MIN_WEIGHT])。0 にすると
     * **押せないキーが段に居座る**（画面から消えるわけではないので、利用者には壊れて見える）。
     */
    fun weights(): List<Float> {
        if (slots.isEmpty()) return emptyList()
        val budget = slots.size.toFloat()
        val fixedSum = slots.sumOf { ((it.width as? KeyWidth.Fixed)?.ratio ?: 0f).toDouble() }.toFloat()
        val autoCount = slots.count { it.width is KeyWidth.Auto }
        val share =
            if (autoCount == 0) 0f
            else ((budget - fixedSum) / autoCount).coerceAtLeast(MIN_WEIGHT)
        return slots.map { (it.width as? KeyWidth.Fixed)?.ratio ?: share }
    }

    companion object {
        /** 枠 1 つの最小の重み。これ以上細くすると指で押せない。 */
        const val MIN_WEIGHT = 0.2f
    }
}

/**
 * レイアウト 1 枚（= 1 つの面）。
 *
 * @param id 保存・参照用の固定 ID。⚠ 改名しない（設定の参照が切れる）。
 * @param name 利用者が付ける名前。
 */
data class KeyLayout(
    val id: String,
    val name: String,
    val rows: List<KeyRow>,
) {
    /** すべてのキー（分割の中も含む。レイヤーでの姿は含まない）。 */
    fun allKeys(): List<KeyDef> = rows.flatMap { row ->
        row.slots.flatMap { keysIn(it.content) }
    }

    /**
     * ここから**設定か別の面へ抜けられる**キーが 1 つでもあるか。
     *
     * ⚠ **[validate] では見ない**（0.8.403）。端末画面ではツールバーの ⚙ が**キーボードの外**に
     * 常にあるので必須ではなく、現に「面が英字だけのとき」の既定の配列は面の切替キーを持たない
     * （その席は CTRL が埋める）。ここで必須にすると**いまの配列そのものが弾かれる**。
     *
     * ⛔ ただし **OS の入力メソッドとして出しているときはツールバーが無い**。エディタで新しい
     * 配列を保存するときに、これが false なら警告する（判断は呼出し側）。
     */
    fun hasEscapeHatch(): Boolean = allKeys().any { key ->
        (key.bindings.values.flatten() + key.layers.values.flatMap { it.bindings.values.flatten() })
            .any { it is KeyAction.App && it.action in AppAction.ESCAPE_HATCHES }
    }

    /** 壊れたレイアウトを弾く。空 = 問題なし。 */
    fun validate(): List<String> {
        val problems = ArrayList<String>()
        if (rows.isEmpty()) problems.add("rows is empty")
        rows.forEachIndexed { i, row ->
            if (row.slots.isEmpty()) problems.add("row $i has no slots")
            row.slots.forEachIndexed { j, slot ->
                checkContent(slot.content, depth = 1, where = "row $i slot $j", into = problems)
                val w = slot.width
                if (w is KeyWidth.Fixed && w.ratio <= 0f) {
                    problems.add("row $i slot $j has a non-positive fixed width")
                }
            }
        }
        return problems
    }

    private fun checkContent(content: SlotContent, depth: Int, where: String, into: MutableList<String>) {
        when (content) {
            is SlotContent.Single -> Unit
            is SlotContent.Split -> {
                if (depth > MAX_SPLIT_DEPTH) {
                    into.add("$where splits deeper than $MAX_SPLIT_DEPTH")
                    return
                }
                if (content.parts.size !in MIN_SPLIT_PARTS..MAX_SPLIT_PARTS) {
                    into.add("$where splits into ${content.parts.size} (allowed $MIN_SPLIT_PARTS..$MAX_SPLIT_PARTS)")
                }
                if (content.parts.any { it.ratio <= 0f }) into.add("$where has a non-positive part ratio")
                content.parts.forEach { checkContent(it.content, depth + 1, where, into) }
            }
        }
    }

    private fun keysIn(content: SlotContent): List<KeyDef> = when (content) {
        is SlotContent.Single -> listOf(content.key)
        is SlotContent.Split -> content.parts.flatMap { keysIn(it.content) }
    }

    companion object {
        /** ⭐ 分割の深さの上限。1 = 枠を割る、2 = 割った区画をもう一度だけ割る。 */
        const val MAX_SPLIT_DEPTH = 2
        const val MIN_SPLIT_PARTS = 2
        const val MAX_SPLIT_PARTS = 3

        /** 標準のレイヤー名。⚠ 自作のレイヤーも足せるので、これで尽きているわけではない。 */
        const val LAYER_SHIFT = "shift"
        const val LAYER_SYMBOL = "sym"
    }
}
