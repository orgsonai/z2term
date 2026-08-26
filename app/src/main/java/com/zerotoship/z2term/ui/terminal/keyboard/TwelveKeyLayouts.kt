package com.zerotoship.z2term.ui.terminal.keyboard

/** GUI / JSON エディタへ渡せる、日本語 12 キー面の既定配列。 */
fun japaneseKeyLayout(): KeyLayout = KeyLayout(
    id = "builtin_kana",
    name = "Japanese",
    faceId = KeyboardFace.KANA.id,
    rows = listOf(
        row(
            keySlot(
                KeyDef(
                    label = "ESC",
                    bindings = mapOf(
                        KeyGesture.TAP to listOf(KeyAction.Named(NamedKey.ESC)),
                        KeyGesture.UP to listOf(KeyAction.App(AppAction.PAD_PASTE)),
                        KeyGesture.DOWN to listOf(KeyAction.App(AppAction.PAD_EMOJI)),
                    ),
                    hintGestures = setOf(KeyGesture.UP, KeyGesture.DOWN),
                    flickOnRelease = false,
                    fontRole = KeyFontRole.SMALL,
                ),
                JP_EDGE_WEIGHT,
            ),
            kana("あ", "い", "う", "え", "お"),
            kana("か", "き", "く", "け", "こ"),
            kana("さ", "し", "す", "せ", "そ"),
            keySlot(backspace(), JP_EDGE_WEIGHT),
        ),
        row(
            splitSlot(NamedKey.LEFT, "◀", NamedKey.DOWN, "▼", JP_EDGE_WEIGHT),
            kana("た", "ち", "つ", "て", "と"),
            kana("な", "に", "ぬ", "ね", "の"),
            kana("は", "ひ", "ふ", "へ", "ほ"),
            splitSlot(NamedKey.RIGHT, "▶", NamedKey.UP, "▲", JP_EDGE_WEIGHT),
        ),
        row(
            keySlot(textKey("␣", " ", repeatable = true), JP_EDGE_WEIGHT),
            kana("ま", "み", "む", "め", "も"),
            kana("や", "「", "ゆ", "」", "よ"),
            kana("ら", "り", "る", "れ", "ろ"),
            keySlot(appKey("変換", AppAction.IME_CONVERT, fontRole = KeyFontRole.SMALL), JP_EDGE_WEIGHT),
        ),
        row(
            keySlot(appKey("", AppAction.NEXT_FACE), JP_EDGE_WEIGHT),
            keySlot(appKey("小゛゜", AppAction.IME_DAKUTEN, fontRole = KeyFontRole.SMALL)),
            kana("わ", "を", "ん", "ー", "〜"),
            kana("、", "。", "？", "！", "…"),
            keySlot(namedKey("⏎", NamedKey.ENTER, repeatable = true), JP_EDGE_WEIGHT),
        ),
    ),
)

/** GUI / JSON エディタへ渡せる、数字 12 キー面の既定配列。 */
fun numberKeyLayout(): KeyLayout = KeyLayout(
    id = "builtin_number",
    name = "Number",
    faceId = KeyboardFace.NUMBER.id,
    rows = listOf(
        row(
            keySlot(
                KeyDef(
                    label = "ESC",
                    bindings = mapOf(
                        KeyGesture.TAP to listOf(KeyAction.Named(NamedKey.ESC)),
                        KeyGesture.UP to listOf(KeyAction.App(AppAction.PAD_PASTE)),
                        KeyGesture.DOWN to listOf(KeyAction.App(AppAction.PAD_EMOJI)),
                    ),
                    hintGestures = setOf(KeyGesture.UP, KeyGesture.DOWN),
                    flickOnRelease = false,
                    fontRole = KeyFontRole.SMALL,
                ),
                JP_EDGE_WEIGHT,
            ),
            keySlot(textKey("1", "1", repeatable = true)),
            keySlot(textKey("2", "2", repeatable = true)),
            keySlot(textKey("3", "3", repeatable = true)),
            keySlot(backspace(), JP_EDGE_WEIGHT),
        ),
        row(
            splitSlot(NamedKey.LEFT, "◀", NamedKey.DOWN, "▼", JP_EDGE_WEIGHT),
            keySlot(textKey("4", "4", repeatable = true)),
            keySlot(textKey("5", "5", repeatable = true)),
            keySlot(textKey("6", "6", repeatable = true)),
            splitSlot(NamedKey.RIGHT, "▶", NamedKey.UP, "▲", JP_EDGE_WEIGHT),
        ),
        row(
            keySlot(textKey("␣", " ", repeatable = true), JP_EDGE_WEIGHT),
            keySlot(textKey("7", "7", repeatable = true)),
            keySlot(textKey("8", "8", repeatable = true)),
            keySlot(textKey("9", "9", repeatable = true)),
            KeySlot(
                content = SlotContent.Split(
                    SplitDir.VERTICAL,
                    listOf(
                        SlotPart(SlotContent.Single(textKey("-", "-", repeatable = true))),
                        SlotPart(SlotContent.Single(textKey("/", "/", repeatable = true))),
                    ),
                ),
                width = KeyWidth.Fixed(JP_EDGE_WEIGHT),
            ),
        ),
        row(
            keySlot(appKey("", AppAction.NEXT_FACE), JP_EDGE_WEIGHT),
            keySlot(textKey(".", ".", repeatable = true)),
            keySlot(textKey("0", "0", repeatable = true)),
            keySlot(textKey(":", ":", repeatable = true)),
            keySlot(namedKey("⏎", NamedKey.ENTER, repeatable = true), JP_EDGE_WEIGHT),
        ),
    ),
)

private fun row(vararg slots: KeySlot) = KeyRow(slots.toList())

private fun keySlot(key: KeyDef, width: Float? = null) = KeySlot(
    content = SlotContent.Single(key),
    width = width?.let { KeyWidth.Fixed(it) } ?: KeyWidth.Auto,
)

private fun splitSlot(first: NamedKey, firstLabel: String, second: NamedKey, secondLabel: String, width: Float) =
    KeySlot(
        content = SlotContent.Split(
            SplitDir.VERTICAL,
            listOf(
                SlotPart(SlotContent.Single(namedKey(firstLabel, first, repeatable = true))),
                SlotPart(SlotContent.Single(namedKey(secondLabel, second, repeatable = true))),
            ),
        ),
        width = KeyWidth.Fixed(width),
    )

private fun kana(tap: String, left: String, up: String, right: String, down: String) = keySlot(
    KeyDef(
        label = tap,
        bindings = linkedMapOf(
            KeyGesture.TAP to listOf(KeyAction.Text(tap)),
            KeyGesture.LEFT to listOf(KeyAction.Text(left)),
            KeyGesture.UP to listOf(KeyAction.Text(up)),
            KeyGesture.RIGHT to listOf(KeyAction.Text(right)),
            KeyGesture.DOWN to listOf(KeyAction.Text(down)),
        ),
        hintGestures = KeyGesture.FLICKS.toSet(),
        fontRole = KeyFontRole.MAIN,
    ),
)

private fun textKey(label: String, send: String, repeatable: Boolean = false) = KeyDef.text(label, send).copy(
    repeatable = repeatable,
    fontRole = KeyFontRole.MAIN,
)

private fun namedKey(label: String, named: NamedKey, repeatable: Boolean = false) =
    KeyDef.named(label, named, repeatable = repeatable)

private fun appKey(label: String, action: AppAction, fontRole: KeyFontRole = KeyFontRole.NORMAL) = KeyDef(
    label = label,
    bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.App(action))),
    fontRole = fontRole,
)

private fun backspace() = KeyDef(
    label = "⌫",
    bindings = mapOf(
        KeyGesture.TAP to listOf(KeyAction.Named(NamedKey.BACKSPACE)),
        KeyGesture.LEFT to listOf(KeyAction.Chord(setOf(ModKey.CTRL), text = "w")),
        KeyGesture.RIGHT to listOf(KeyAction.Chord(setOf(ModKey.CTRL), text = "u")),
    ),
    repeatable = true,
    repeatInitialMs = 500,
    repeatIntervalMs = 60,
    flickOnRelease = false,
)
