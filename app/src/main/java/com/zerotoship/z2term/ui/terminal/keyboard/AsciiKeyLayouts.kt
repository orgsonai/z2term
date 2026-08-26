package com.zerotoship.z2term.ui.terminal.keyboard

/**
 * いまの英字面を [KeyLayout] で表したもの（0.8.403・段階 1b）。
 *
 * ⚠ **描画はまだここを見ていない。** [TerminalKeyboard] は 5 段べた書きのままで、画面は
 * 1 ドットも変わっていない。この段階の目的は「**いまの配列をモデルで表せる**」ことを
 * `AsciiKeyLayoutTest` で固定すること。描画を移すのは次の段階。
 *
 * ラベルとフリックの表 ([AsciiKeys]) は**[TerminalKeyboard] と共有している**（あちらの
 * ローカル変数をここへ出した）。⚠ 二重に持つと、片方だけ直したときテストが「一致」と
 * 言い張ってしまう。
 *
 * ## 実装して分かったこと（設計の当たりが 1 つ外れた）
 *
 * ⭐ **記号面 (`?#`) はレイヤーでは表せない。** レイヤーは「キーの姿の差し替え」で枠の数は
 * 変えられないが、記号面は Row 4 が **10 個 → 8 個**に減る（`?§°¥€£~…`）。枠の数が変わる
 * ものは**別のレイアウト**にするしかない。⇧ は枠が変わらないので予定どおりレイヤーで表す。
 *
 * ⭐ 0.8.411 から、文字・数字・記号・矢印は [KeyWidth.Auto]、機能キーだけ指定幅にした。
 * たとえば 1 段目は `ESC 1.4 + Auto × 10 + ⌫ 1.4`。端の幅を先に確保し、残りを入力キーへ
 * 均等配分する。記号面にも同じ規則を使うので、`?#` を押しても機能キーの幅は動かない。
 */
object AsciiKeys {

    // ---- ラベル（[TerminalKeyboard] と共有）--------------------------------------------

    val ROW1: List<String> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val ROW2: List<String> = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val ROW3: List<String> = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val ROW4: List<String> = listOf("z", "x", "c", "v", "b", "n", "m", ",", ".", "/")

    val SYM_ROW1: List<String> = listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")")
    val SYM_ROW2: List<String> = listOf("-", "_", "+", "=", "/", "\\", "[", "]", "{", "}")
    val SYM_ROW3: List<String> = listOf("`", "~", "'", "\"", ";", ":", "<", ">", "|")

    /** ⚠ ここだけ 8 個。だから記号面はレイヤーではなく別レイアウトになる。 */
    val SYM_ROW4: List<String> = listOf("?", "§", "°", "¥", "€", "£", "~", "…")

    // ---- フリック（[TerminalKeyboard] と共有）------------------------------------------

    /** 1 方向フリック（COMPACT）の上。 */
    val FLICK_UP_ROW2: List<Char> = listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')')
    val FLICK_UP_ROW3: List<Char> = listOf('-', '_', '+', '=', '|', '\\', '/', '[', ']')
    val FLICK_UP_ROW4: List<Char> = listOf('`', '~', '\'', '"', '<', '>', '?', ':', ';', '{')

    /**
     * 4 方向フリック（SPACIOUS）の Row 2。down は「そのキーの大文字」を動的に入れるので空。
     */
    val FLICK4_ROW2: List<FlickMap> = listOf(
        FlickMap(up = '!', left = '`', right = '~'),
        FlickMap(up = '@', left = '\'', right = '"'),
        FlickMap(up = '#', left = '(', right = ')'),
        FlickMap(up = '$', left = '[', right = ']'),
        FlickMap(up = '%', left = '{', right = '}'),
        FlickMap(up = '^', left = '<', right = '>'),
        FlickMap(up = '&', left = ':', right = ';'),
        FlickMap(up = '*', left = ',', right = '.'),
        FlickMap(up = '(', left = '/', right = '\\'),
        FlickMap(up = ')', left = '|', right = '?'),
    )

    // ---- 幅（いまの `weight` をそのまま） ------------------------------------------------

    /** ESC / ⌫ / CTRL。 */
    const val W_SIDE = 1.4f

    /** TAB。 */
    const val W_TAB = 1.2f

    /** ⇧。 */
    const val W_SHIFT = 1.3f

    /** ⏎。 */
    const val W_ENTER = 1.2f

    /** 最下段の面切替。Fixed(1) として Auto の矢印とは区別する。 */
    const val W_FACE = 1f

    /** 最下段の `?#` と ALT。 */
    const val W_MOD = 0.8f

    /** 最下段のスペース。 */
    const val W_SPACE = 2f

    /** COMPACT の上バーと、パッドを開いている間の補助行で使う標準幅。 */
    const val W_KEY = 1f

    /** パッドを閉じる ×。最下段の修飾キーとは別に、従来の押しやすさを保つ。 */
    const val W_PAD_CLOSE = 1.2f
}

/**
 * いまの英字面を組み立てる。
 *
 * @param compact シンプル（[KeyboardStyle.COMPACT]）か。⚠ **段の数が変わる** — シンプルは
 *   `ESC TAB ⇧ CTRL` を上のバーへ出して 6 段、4 方向フリック版は左端の列に置いて 5 段。
 * @param hasFaceKey 面の切替キーが要るか（面が 2 つ以上あるとき）。⚠ **無いときだけ**
 *   ⇧ と CTRL が 1 段ずつずれて、空いた席が貼り付け / 絵文字の入口になる。
 * @param symbols 記号面（`?#` を押した状態）か。
 * @param fourWayFlick Row 2 を 4 方向フリックにするか（[KeyboardStyle.fourDirectionFlick]）。
 */
fun asciiKeyLayout(
    compact: Boolean,
    hasFaceKey: Boolean,
    symbols: Boolean = false,
    fourWayFlick: Boolean = !compact,
): KeyLayout {
    val id = buildString {
        append("ascii")
        append(if (compact) "_compact" else "_spacious")
        if (!hasFaceKey) append("_noface")
        if (symbols) append("_sym")
    }

    val r1 = if (symbols) AsciiKeys.SYM_ROW1 else AsciiKeys.ROW1
    val r2 = if (symbols) AsciiKeys.SYM_ROW2 else AsciiKeys.ROW2
    val r3 = if (symbols) AsciiKeys.SYM_ROW3 else AsciiKeys.ROW3
    val r4 = if (symbols) AsciiKeys.SYM_ROW4 else AsciiKeys.ROW4

    val rows = ArrayList<KeyRow>(6)

    // シンプルだけ: ESC / TAB / ⇧ と、CTRL または貼り付けの入口を上のバーへ出す。
    // 主行の左 1.4 列が空くぶん、英字キーが少しずつ広くなる。
    if (compact) {
        rows.add(
            KeyRow(
                listOf(
                    slot(escKey(), AsciiKeys.W_KEY),
                    slot(KeyDef.named("TAB", NamedKey.TAB, fontRole = KeyFontRole.SMALL), AsciiKeys.W_KEY),
                    slot(KeyDef.modifier("⇧", ModKey.SHIFT), AsciiKeys.W_KEY),
                    slot(if (hasFaceKey) ctrlKey() else padKey(), AsciiKeys.W_KEY),
                )
            )
        )
    }

    // Row 1: (4 方向フリック版のみ ESC) + 数字 + ⌫
    rows.add(
        KeyRow(
            buildList {
                if (!compact) add(slot(escKey(), AsciiKeys.W_SIDE))
                r1.forEach { add(slot(digitKey(it))) }
                add(slot(backspaceKey(), AsciiKeys.W_SIDE))
            }
        )
    )

    // Row 2: (4 方向フリック版のみ TAB) + qwerty
    rows.add(
        KeyRow(
            buildList {
                if (!compact) {
                    add(slot(KeyDef.named("TAB", NamedKey.TAB, fontRole = KeyFontRole.SMALL), AsciiKeys.W_TAB))
                }
                r2.forEachIndexed { i, label ->
                    add(slot(letterKey(label, flickRow2(i, symbols, fourWayFlick))))
                }
            }
        )
    )

    // Row 3: 左端は ⇧（面の切替キーがあるとき）/ 貼り付けの入口（無いとき）。右端は ⏎。
    rows.add(
        KeyRow(
            buildList {
                if (!compact) {
                    add(slot(if (hasFaceKey) shiftKey() else padKey(), AsciiKeys.W_SHIFT))
                }
                r3.forEachIndexed { i, label ->
                    add(slot(letterKey(label, flickUpRow(AsciiKeys.FLICK_UP_ROW3, i, label, symbols))))
                }
                add(slot(KeyDef.named("⏎", NamedKey.ENTER, repeatable = true), AsciiKeys.W_ENTER))
            }
        )
    )

    // Row 4: 左端は CTRL（切替キーがあるとき）/ ⇧ を 1 段下げたもの（無いとき）。
    rows.add(
        KeyRow(
            buildList {
                if (!compact) add(slot(if (hasFaceKey) ctrlKey() else shiftKey(), AsciiKeys.W_SIDE))
                r4.forEachIndexed { i, label ->
                    add(slot(letterKey(label, flickUpRow(AsciiKeys.FLICK_UP_ROW4, i, label, symbols))))
                }
            }
        )
    )

    // Row 5: 面の切替キー（無ければ CTRL）/ ?# / ALT / スペース / 矢印 4 つ。
    rows.add(
        KeyRow(
            listOf(
                slot(if (hasFaceKey) faceKey() else ctrlKey(), AsciiKeys.W_FACE),
                slot(symbolToggleKey(symbols), AsciiKeys.W_MOD),
                slot(KeyDef.modifier("ALT", ModKey.ALT), AsciiKeys.W_MOD),
                slot(spaceKey(), AsciiKeys.W_SPACE),
                slot(arrowKey("←", NamedKey.LEFT)),
                slot(arrowKey("↓", NamedKey.DOWN)),
                slot(arrowKey("↑", NamedKey.UP)),
                slot(arrowKey("→", NamedKey.RIGHT)),
            )
        )
    )

    return KeyLayout(id = id, name = id, rows = rows)
}

/**
 * パッド（貼り付け / 絵文字）を開いている間、下に残す 1 段。
 *
 * ⚠ **⌫ を「閉じる」に置き換えない** — 置き換えるとパッドを開いている間に文字を消せなくなる。
 * 閉じるのは `×` か、開いた入口キーをもう一度押す。
 */
fun asciiPadRow(): KeyRow = KeyRow(
    listOf(
        slot(
            KeyDef(
                label = "×",
                bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.App(AppAction.CLOSE_PAD))),
                highlighted = true,
            ),
            AsciiKeys.W_PAD_CLOSE,
        ),
        slot(backspaceKey(), AsciiKeys.W_SIDE),
        slot(spaceKey(), 3f),
        slot(KeyDef.named("⏎", NamedKey.ENTER, repeatable = true), AsciiKeys.W_ENTER),
        slot(arrowKey("←", NamedKey.LEFT), AsciiKeys.W_KEY),
        slot(arrowKey("→", NamedKey.RIGHT), AsciiKeys.W_KEY),
    )
)

// ---- 部品 ---------------------------------------------------------------------------------

private fun slot(key: KeyDef) = KeySlot.of(key)
private fun slot(key: KeyDef, width: Float) = KeySlot.of(key, KeyWidth.Fixed(width))

/**
 * ESC。⚠ **上下フリックで貼り付け / 絵文字**（0.8.362）。
 * ⚠ 印もポップアップも出さない（利用者判断・英字面の見た目を変えないため）。
 */
private fun escKey() = KeyDef(
    fontRole = KeyFontRole.SMALL,
    // ⚠ しきい値を超えた瞬間に開く（行き先を出さない隠し操作なので迷う余地が無い）。
    flickOnRelease = false,
    label = "ESC",
    bindings = mapOf(
        KeyGesture.TAP to listOf(KeyAction.Named(NamedKey.ESC)),
        KeyGesture.UP to listOf(KeyAction.App(AppAction.PAD_PASTE)),
        KeyGesture.DOWN to listOf(KeyAction.App(AppAction.PAD_EMOJI)),
    ),
)

/** ⌫。左右フリックで単語削除 / 行頭まで削除。⚠ こちらも印を出さない（隠し機能のまま）。 */
private fun backspaceKey() = KeyDef(
    label = "⌫",
    bindings = mapOf(
        KeyGesture.TAP to listOf(KeyAction.Named(NamedKey.BACKSPACE)),
        KeyGesture.LEFT to listOf(KeyAction.Chord(mods = setOf(ModKey.CTRL), text = "w")),
        KeyGesture.RIGHT to listOf(KeyAction.Chord(mods = setOf(ModKey.CTRL), text = "u")),
    ),
    // ⚠ しきい値を超えた瞬間に消す（ESC と同じ。隠し操作なので速い方がよい）。
    flickOnRelease = false,
    // ⚠ ⌫ だけ連打の出だしが遅い (500ms)。速いと、消そうとしただけで消しすぎる。
    repeatable = true,
    repeatInitialMs = 500L,
    repeatIntervalMs = 60L,
)

/** 貼り付け / 絵文字の入口（面の切替キーが要らない面でだけ席が空く）。 */
private fun padKey() = KeyDef(
    label = "↕",
    bindings = mapOf(
        KeyGesture.TAP to listOf(KeyAction.App(AppAction.PAD_PASTE)),
        KeyGesture.UP to listOf(KeyAction.App(AppAction.PAD_PASTE)),
        KeyGesture.DOWN to listOf(KeyAction.App(AppAction.PAD_EMOJI)),
    ),
    // ⚠ ここだけ**上下にヒントを出す** (📋 / 😀)。行き先が分からないと押されないため。
    hintGestures = setOf(KeyGesture.UP, KeyGesture.DOWN),
    flickOnRelease = false,
)

// ⚠ ⇧ は押下で背景を変えない。OFF / 1 回だけ / 固定 の 3 状態を色で見せており、
// そこに押下中の色を重ねると「いまどの状態か」が読めなくなる。
private fun shiftKey() = KeyDef.modifier("⇧", ModKey.SHIFT).copy(pressFeedback = false)
private fun ctrlKey() = KeyDef.modifier("CTRL", ModKey.CTRL)

/** 面の切替。⚠ ラベルは描画側が「押すと行く面」で差し替える（`あ` / `12`）。 */
private fun faceKey() = KeyDef(
    label = "",
    bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.App(AppAction.NEXT_FACE))),
)

/** `?#` / `ABC`。⚠ 記号面は**枠の数が変わる**ので、レイヤーではなく別レイアウトへ移る。 */
private fun symbolToggleKey(symbols: Boolean) = KeyDef(
    fontRole = KeyFontRole.SMALL,
    label = if (symbols) "ABC" else "?#",
    bindings = mapOf(
        KeyGesture.TAP to listOf(
            KeyAction.Layer(if (symbols) "" else KeyLayout.LAYER_SYMBOL, sticky = true)
        )
    ),
)

private fun spaceKey() = KeyDef(
    // ⚠ 表示は小文字の `space`・控えめな色・押しても背景を変えない (いまのまま)。
    fontRole = KeyFontRole.SMALL,
    labelTone = LabelTone.SECONDARY,
    pressFeedback = false,
    label = "space",
    bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.Text(" "))),
    repeatable = true,
)

private fun arrowKey(label: String, key: NamedKey) =
    KeyDef.named(label, key, repeatable = true, fontRole = KeyFontRole.NORMAL)

/** 数字キー（連打あり・フリック無し）。 */
private fun digitKey(label: String) = KeyDef(
    fontRole = KeyFontRole.MAIN,
    label = label,
    bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.Text(label))),
    repeatable = true,
)

/**
 * 英字（記号面では記号）キー。
 *
 * ⚠ **⇧ はレイヤーで表す** — 枠の数が変わらないので姿の差し替えで足りる。⚠ 記号キーには
 * 大文字が無いので、レイヤーも下フリックも付けない（いまの `sym` のときと同じ）。
 */
private fun letterKey(label: String, flick: Map<KeyGesture, List<KeyAction>>): KeyDef {
    val upper = label.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.toString()
    // ⚠ ヒントは**上・左右だけ**。下 (大文字) は出さない — 出すと 10 個並ぶ段が字だらけになる。
    val hints = flick.keys.filter { it != KeyGesture.DOWN }.toSet()
    return KeyDef(
        label = label,
        bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.Text(label))) + flick,
        hintGestures = hints,
        fontRole = KeyFontRole.MAIN,
        layers = if (upper == null) emptyMap() else mapOf(
            KeyLayout.LAYER_SHIFT to KeyDef(
                label = upper,
                bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.Text(upper))) + flick,
                hintGestures = hints,
                fontRole = KeyFontRole.MAIN,
            )
        ),
    )
}

/** Row 2 のフリック。4 方向版は上/左右に記号、1 方向版は上だけ。下はどちらも大文字。 */
private fun flickRow2(
    index: Int,
    symbols: Boolean,
    fourWay: Boolean,
): Map<KeyGesture, List<KeyAction>> {
    if (symbols) return emptyMap()
    val out = HashMap<KeyGesture, List<KeyAction>>()
    if (fourWay) {
        val f = AsciiKeys.FLICK4_ROW2.getOrNull(index) ?: return emptyMap()
        f.up?.let { out[KeyGesture.UP] = listOf(KeyAction.Text(it.toString())) }
        f.left?.let { out[KeyGesture.LEFT] = listOf(KeyAction.Text(it.toString())) }
        f.right?.let { out[KeyGesture.RIGHT] = listOf(KeyAction.Text(it.toString())) }
    } else {
        AsciiKeys.FLICK_UP_ROW2.getOrNull(index)?.let {
            out[KeyGesture.UP] = listOf(KeyAction.Text(it.toString()))
        }
    }
    AsciiKeys.ROW2.getOrNull(index)?.firstOrNull()?.uppercaseChar()?.let {
        out[KeyGesture.DOWN] = listOf(KeyAction.Text(it.toString()))
    }
    return out
}

/** Row 3 / Row 4 のフリック。上は記号、下はそのキーの大文字。 */
private fun flickUpRow(
    table: List<Char>,
    index: Int,
    label: String,
    symbols: Boolean,
): Map<KeyGesture, List<KeyAction>> {
    if (symbols) return emptyMap()
    val out = HashMap<KeyGesture, List<KeyAction>>()
    table.getOrNull(index)?.let { out[KeyGesture.UP] = listOf(KeyAction.Text(it.toString())) }
    label.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.let {
        out[KeyGesture.DOWN] = listOf(KeyAction.Text(it.toString()))
    }
    return out
}
