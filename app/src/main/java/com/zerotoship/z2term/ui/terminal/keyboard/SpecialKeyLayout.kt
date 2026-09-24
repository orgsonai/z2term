package com.zerotoship.z2term.ui.terminal.keyboard

/** OS キーボードの補助バー。通常の面巡回とは独立して保存・編集する。 */
const val SPECIAL_KEY_LAYOUT_ID = "special_key_bar"

fun specialKeyLayout(name: String = "ESC / TAB / CTRL"): KeyLayout {
    fun control(label: String, text: String) = KeyDef(
        label = label,
        bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.Chord(setOf(ModKey.CTRL), text = text))),
    )
    val keys = listOf(
        KeyDef.named("ESC", NamedKey.ESC),
        KeyDef.named("TAB", NamedKey.TAB),
        KeyDef.modifier("CTRL", ModKey.CTRL),
        KeyDef.named("←", NamedKey.LEFT),
        KeyDef.named("↓", NamedKey.DOWN),
        KeyDef.named("↑", NamedKey.UP),
        KeyDef.named("→", NamedKey.RIGHT),
        KeyDef.named("⏎", NamedKey.ENTER, repeatable = true),
        control("^C", "c"), control("^D", "d"), control("^L", "l"),
    )
    val rows = listOf(KeyRow(keys.map { KeySlot(SlotContent.Single(it)) }))
    return KeyLayout(
        id = SPECIAL_KEY_LAYOUT_ID,
        name = name,
        rows = rows,
        styleId = KeyboardStyle.COMPACT.id,
        defaultName = name,
        defaultRows = rows,
    )
}

/** 壊れた保存値は既定へ戻す。旧 ON/OFF 設定は配列の保存値と分離して維持する。 */
fun specialKeyLayoutFromJson(json: String, name: String = "ESC / TAB / CTRL"): KeyLayout {
    val default = specialKeyLayout(name)
    return KeyLayoutJson.fromJsonString(json)?.takeIf { it.validate().isEmpty() }?.copy(
        id = SPECIAL_KEY_LAYOUT_ID,
        faceId = KeyboardFace.ASCII.id,
        styleId = KeyboardStyle.COMPACT.id,
        defaultName = default.name,
        defaultRows = default.rows,
        defaultSymbolRows = null,
    ) ?: default
}
