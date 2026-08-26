package com.zerotoship.z2term.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.terminal.keyboard.AppAction
import com.zerotoship.z2term.ui.terminal.keyboard.KeyAction
import com.zerotoship.z2term.ui.terminal.keyboard.KeyCellPath
import com.zerotoship.z2term.ui.terminal.keyboard.KeyDef
import com.zerotoship.z2term.ui.terminal.keyboard.KeyFontRole
import com.zerotoship.z2term.ui.terminal.keyboard.KeyGesture
import com.zerotoship.z2term.ui.terminal.keyboard.KeyLayout
import com.zerotoship.z2term.ui.terminal.keyboard.KeyWidth
import com.zerotoship.z2term.ui.terminal.keyboard.LabelTone
import com.zerotoship.z2term.ui.terminal.keyboard.ModKey
import com.zerotoship.z2term.ui.terminal.keyboard.NamedKey
import com.zerotoship.z2term.ui.terminal.keyboard.SlotContent
import com.zerotoship.z2term.ui.terminal.keyboard.SplitDir
import com.zerotoship.z2term.ui.terminal.keyboard.appendKey
import com.zerotoship.z2term.ui.terminal.keyboard.collapseParentTo
import com.zerotoship.z2term.ui.terminal.keyboard.insertRowAfter
import com.zerotoship.z2term.ui.terminal.keyboard.keyAt
import com.zerotoship.z2term.ui.terminal.keyboard.keyPaths
import com.zerotoship.z2term.ui.terminal.keyboard.moveSlot
import com.zerotoship.z2term.ui.terminal.keyboard.removeKeyCell
import com.zerotoship.z2term.ui.terminal.keyboard.removeRow
import com.zerotoship.z2term.ui.terminal.keyboard.splitKey
import com.zerotoship.z2term.ui.terminal.keyboard.updateKey
import com.zerotoship.z2term.ui.terminal.keyboard.updateKeys
import com.zerotoship.z2term.ui.terminal.keyboard.updateSlotWidths
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import com.zerotoship.z2term.ui.theme.ZtsWarning

/**
 * 配列を見た形のまま複数選択し、基本項目とアクション列を編集する（0.8.410〜0.8.412・段階 4）。
 *
 * ⚠ この画面が触らないフィールドは [KeyDef.copy] でそのまま保持する。JSON で作ったレイヤーや
 * 将来の項目を、GUI でラベルを 1 文字直しただけで落とさないことが最優先。
 */
@Composable
fun KeyLayoutVisualEditor(layout: KeyLayout, onChange: (KeyLayout) -> Unit) {
    var selected by remember(layout.id) {
        mutableStateOf(layout.keyPaths().firstOrNull()?.let(::setOf).orEmpty())
    }
    val paths = layout.keyPaths()
    LaunchedEffect(paths, selected) {
        val valid = selected.filterTo(LinkedHashSet()) { it in paths }
        selected = if (valid.isEmpty()) paths.firstOrNull()?.let(::setOf).orEmpty() else valid
    }
    val path = selected.firstOrNull { it in paths }
    val key = path?.let(layout::keyAt)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.settings_key_layout_visual_desc),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            fontFamily = FontFamily.Monospace,
        )
        LayoutPreview(
            layout = layout,
            selected = selected,
            onSelect = { tapped ->
                selected = when {
                    tapped !in selected -> selected + tapped
                    selected.size > 1 -> selected - tapped
                    else -> selected
                }
            },
        )

        if (path != null && key != null) {
            Text(
                text = stringResource(
                    R.string.settings_key_layout_selected,
                    path.row + 1,
                    path.slot + 1,
                    if (path.parts.isEmpty()) "" else "." + path.parts.joinToString(".") { (it + 1).toString() },
                ),
                color = ZtsGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = stringResource(R.string.settings_key_layout_selected_count, selected.size),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )

            VisualTextField(
                label = stringResource(R.string.settings_key_layout_label),
                value = key.label,
                onChange = { value -> onChange(layout.updateKey(path) { it.copy(label = value) }) },
            )
            WidthEditor(layout, selected, path, onChange)
            AppearanceEditor(layout, selected, key, onChange)
            if (selected.size == 1) {
                StructureEditor(
                    layout = layout,
                    path = path,
                    onChange = onChange,
                    onSelect = { selected = it?.let(::setOf).orEmpty() },
                )
            }
            BindingEditor(layout, selected, path, key, onChange)

            if (key.layers.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_key_layout_layers_preserved, key.layers.keys.joinToString()),
                    color = ZtsWarning,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun LayoutPreview(
    layout: KeyLayout,
    selected: Set<KeyCellPath>,
    onSelect: (KeyCellPath) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary, RoundedCornerShape(8.dp))
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        layout.rows.forEachIndexed { rowIndex, row ->
            val weights = row.weights()
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                row.slots.forEachIndexed { slotIndex, slot ->
                    PreviewContent(
                        content = slot.content,
                        path = KeyCellPath(rowIndex, slotIndex),
                        selected = selected,
                        onSelect = onSelect,
                        modifier = Modifier.weight(weights.getOrElse(slotIndex) { 1f }),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(
    content: SlotContent,
    path: KeyCellPath,
    selected: Set<KeyCellPath>,
    onSelect: (KeyCellPath) -> Unit,
    modifier: Modifier,
) {
    when (content) {
        is SlotContent.Single -> {
            val isSelected = path in selected
            Box(
                modifier = modifier
                    .background(
                        if (isSelected) ZtsGreen.copy(alpha = 0.26f) else ZtsBgCard,
                        RoundedCornerShape(5.dp),
                    )
                    .border(
                        1.dp,
                        if (isSelected) ZtsGreen else ZtsBorder,
                        RoundedCornerShape(5.dp),
                    )
                    .clickable { onSelect(path) }
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = content.key.label.ifEmpty { "+" },
                    color = if (content.key.label.isEmpty()) ZtsTextSecondary else ZtsTextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        is SlotContent.Split -> {
            if (content.dir == SplitDir.HORIZONTAL) {
                Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    content.parts.forEachIndexed { index, part ->
                        PreviewContent(
                            content = part.content,
                            path = path.copy(parts = path.parts + index),
                            selected = selected,
                            onSelect = onSelect,
                            modifier = Modifier.weight(part.ratio),
                        )
                    }
                }
            } else {
                Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    content.parts.forEachIndexed { index, part ->
                        PreviewContent(
                            content = part.content,
                            path = path.copy(parts = path.parts + index),
                            selected = selected,
                            onSelect = onSelect,
                            modifier = Modifier.weight(part.ratio),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidthEditor(
    layout: KeyLayout,
    paths: Set<KeyCellPath>,
    path: KeyCellPath,
    onChange: (KeyLayout) -> Unit,
) {
    val width = layout.rows[path.row].slots[path.slot].width
    // ⚠ width を remember key に入れない。入力途中の `1.` も Float 化できる直前値 `1` で
    // layout が更新されるため、width で初期化し直すと末尾の小数点が消えてしまう。
    var draft by remember(path) {
        mutableStateOf(((width as? KeyWidth.Fixed)?.ratio ?: 1f).trimmed())
    }
    EditorSection(stringResource(R.string.settings_key_layout_width)) {
        ChoiceRow {
            ChoiceChip(
                label = stringResource(R.string.settings_key_layout_width_auto),
                selected = width is KeyWidth.Auto,
            ) { onChange(layout.updateSlotWidths(paths, KeyWidth.Auto)) }
            ChoiceChip(
                label = stringResource(R.string.settings_key_layout_width_fixed),
                selected = width is KeyWidth.Fixed,
            ) {
                val ratio = draft.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: 1f
                onChange(layout.updateSlotWidths(paths, KeyWidth.Fixed(ratio)))
            }
        }
        if (width is KeyWidth.Fixed) {
            VisualTextField(
                label = stringResource(R.string.settings_key_layout_width_ratio),
                value = draft,
                onChange = { value ->
                    draft = value
                    value.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }?.let {
                        onChange(layout.updateSlotWidths(paths, KeyWidth.Fixed(it)))
                    }
                },
            )
            Slider(
                value = (draft.toFloatOrNull() ?: (width as? KeyWidth.Fixed)?.ratio ?: 1f)
                    .coerceIn(0.2f, 5f),
                onValueChange = { value ->
                    draft = value.trimmed()
                    onChange(layout.updateSlotWidths(paths, KeyWidth.Fixed(value)))
                },
                valueRange = 0.2f..5f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppearanceEditor(
    layout: KeyLayout,
    paths: Set<KeyCellPath>,
    key: KeyDef,
    onChange: (KeyLayout) -> Unit,
) {
    fun change(block: (KeyDef) -> KeyDef) = onChange(layout.updateKeys(paths, block))
    EditorSection(stringResource(R.string.settings_key_layout_appearance)) {
        ChoiceRow {
            KeyFontRole.entries.forEach { role ->
                ChoiceChip(role.id, key.fontRole == role) { change { it.copy(fontRole = role) } }
            }
        }
        ChoiceRow {
            ChoiceChip(stringResource(R.string.settings_key_layout_repeat), key.repeatable) {
                val target = !key.repeatable
                change { it.copy(repeatable = target) }
            }
            ChoiceChip(stringResource(R.string.settings_key_layout_press_feedback), key.pressFeedback) {
                val target = !key.pressFeedback
                change { it.copy(pressFeedback = target) }
            }
            ChoiceChip(stringResource(R.string.settings_key_layout_flick_release), key.flickOnRelease) {
                val target = !key.flickOnRelease
                change { it.copy(flickOnRelease = target) }
            }
            ChoiceChip(stringResource(R.string.settings_key_layout_highlight), key.highlighted) {
                val target = !key.highlighted
                change { it.copy(highlighted = target) }
            }
            ChoiceChip(stringResource(R.string.settings_key_layout_tone_secondary), key.labelTone == LabelTone.SECONDARY) {
                val target = if (key.labelTone == LabelTone.SECONDARY) LabelTone.PRIMARY else LabelTone.SECONDARY
                change { it.copy(labelTone = target) }
            }
        }
        Text(
            text = stringResource(R.string.settings_key_layout_hints),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        ChoiceRow {
            KeyGesture.FLICKS.forEach { gesture ->
                ChoiceChip(gestureSymbol(gesture), gesture in key.hintGestures) {
                    val shown = gesture !in key.hintGestures
                    change { old -> old.copy(hintGestures = if (shown) old.hintGestures + gesture else old.hintGestures - gesture) }
                }
            }
        }
    }
}

@Composable
private fun StructureEditor(
    layout: KeyLayout,
    path: KeyCellPath,
    onChange: (KeyLayout) -> Unit,
    onSelect: (KeyCellPath?) -> Unit,
) {
    val row = layout.rows[path.row]
    EditorSection(stringResource(R.string.settings_key_layout_structure)) {
        ChoiceRow {
            TinyButton("←", path.slot > 0) {
                onChange(layout.moveSlot(path, -1))
                onSelect(path.copy(slot = path.slot - 1))
            }
            TinyButton("→", path.slot < row.slots.lastIndex) {
                onChange(layout.moveSlot(path, 1))
                onSelect(path.copy(slot = path.slot + 1))
            }
            TinyButton(stringResource(R.string.settings_key_layout_add_key)) {
                onChange(layout.appendKey(path.row))
                onSelect(KeyCellPath(path.row, row.slots.size))
            }
            TinyButton(stringResource(R.string.settings_key_layout_delete_key), row.slots.size > 1 || path.parts.isNotEmpty(), danger = true) {
                val changed = layout.removeKeyCell(path)
                onChange(changed)
                onSelect(changed.keyPaths().firstOrNull())
            }
        }
        ChoiceRow {
            TinyButton(stringResource(R.string.settings_key_layout_split_horizontal), path.parts.size < KeyLayout.MAX_SPLIT_DEPTH) {
                onChange(layout.splitKey(path, SplitDir.HORIZONTAL))
                onSelect(path.copy(parts = path.parts + 0))
            }
            TinyButton(stringResource(R.string.settings_key_layout_split_vertical), path.parts.size < KeyLayout.MAX_SPLIT_DEPTH) {
                onChange(layout.splitKey(path, SplitDir.VERTICAL))
                onSelect(path.copy(parts = path.parts + 0))
            }
            TinyButton(stringResource(R.string.settings_key_layout_collapse), path.parts.isNotEmpty(), danger = true) {
                val parent = path.copy(parts = path.parts.dropLast(1))
                onChange(layout.collapseParentTo(path))
                onSelect(parent)
            }
        }
        ChoiceRow {
            TinyButton(stringResource(R.string.settings_key_layout_add_row)) {
                onChange(layout.insertRowAfter(path.row))
                onSelect(KeyCellPath(path.row + 1, 0))
            }
            TinyButton(stringResource(R.string.settings_key_layout_delete_row), layout.rows.size > 1, danger = true) {
                val changed = layout.removeRow(path.row)
                onChange(changed)
                onSelect(changed.keyPaths().firstOrNull())
            }
        }
    }
}

@Composable
private fun BindingEditor(
    layout: KeyLayout,
    paths: Set<KeyCellPath>,
    path: KeyCellPath,
    key: KeyDef,
    onChange: (KeyLayout) -> Unit,
) {
    var gesture by remember(path) { mutableStateOf(KeyGesture.TAP) }
    var editingIndex by remember(path, gesture) { mutableStateOf<Int?>(null) }
    val actions = key.actionsFor(gesture)

    fun replaceActions(next: List<KeyAction>) {
        onChange(
            layout.updateKey(path) { old ->
                val bindings = LinkedHashMap(old.bindings)
                if (next.isEmpty()) bindings.remove(gesture) else bindings[gesture] = next
                old.copy(bindings = bindings)
            },
        )
    }

    EditorSection(stringResource(R.string.settings_key_layout_bindings)) {
        ChoiceRow {
            KeyGesture.entries.forEach { item ->
                ChoiceChip(gestureSymbol(item), gesture == item) {
                    gesture = item
                    editingIndex = null
                }
            }
        }
        if (actions.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_key_layout_no_actions),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        actions.forEachIndexed { index, action ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZtsBgSecondary, RoundedCornerShape(7.dp))
                    .border(1.dp, ZtsBorder, RoundedCornerShape(7.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${index + 1}. ${actionSummary(action)}",
                        modifier = Modifier.weight(1f),
                        color = ZtsTextPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TinyButton(stringResource(R.string.settings_key_layout_action_edit)) {
                        editingIndex = if (editingIndex == index) null else index
                    }
                    TinyButton("↑", index > 0) {
                        replaceActions(actions.swap(index, index - 1))
                        editingIndex = index - 1
                    }
                    TinyButton("↓", index < actions.lastIndex) {
                        replaceActions(actions.swap(index, index + 1))
                        editingIndex = index + 1
                    }
                    TinyButton("×", danger = true) {
                        replaceActions(actions.filterIndexed { at, _ -> at != index })
                        editingIndex = null
                    }
                }
                if (editingIndex == index) {
                    ActionFields(action) { changed ->
                        replaceActions(actions.mapIndexed { at, old -> if (at == index) changed else old })
                    }
                }
            }
        }
        TinyButton(stringResource(R.string.settings_key_layout_add_action)) {
            replaceActions(actions + KeyAction.Text(""))
            editingIndex = actions.size
        }
        if (paths.size > 1) {
            TinyButton(stringResource(R.string.settings_key_layout_copy_gesture)) {
                onChange(
                    layout.updateKeys(paths - path) { old ->
                        val bindings = LinkedHashMap(old.bindings)
                        if (actions.isEmpty()) bindings.remove(gesture) else bindings[gesture] = actions
                        old.copy(bindings = bindings)
                    },
                )
            }
        }
    }
}

private enum class ActionKind(val id: String) {
    TEXT("text"), NAMED("named"), CHORD("chord"), RAW("raw"), MODIFIER("modifier"),
    LAYER("layer"), APP("app"), SNIPPET("snippet"), MACRO("macro"),
}

@Composable
private fun ActionFields(action: KeyAction, onChange: (KeyAction) -> Unit) {
    val kind = action.kind()
    Text(
        text = stringResource(R.string.settings_key_layout_action_type),
        color = ZtsTextSecondary,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
    )
    ChoiceRow {
        ActionKind.entries.forEach { item ->
            ChoiceChip(item.id, kind == item) {
                if (kind != item) onChange(item.defaultAction())
            }
        }
    }
    when (action) {
        is KeyAction.Text -> VisualTextField("text", action.text) { onChange(action.copy(text = it)) }
        is KeyAction.Named -> EnumChoiceRow(NamedKey.entries, action.key, { it.id }) {
            onChange(action.copy(key = it))
        }
        is KeyAction.Modifier -> EnumChoiceRow(ModKey.entries, action.mod, { it.id }) {
            onChange(action.copy(mod = it))
        }
        is KeyAction.App -> EnumChoiceRow(AppAction.entries, action.action, { it.id }) {
            onChange(action.copy(action = it))
        }
        is KeyAction.Layer -> {
            VisualTextField("layer", action.layer) { onChange(action.copy(layer = it)) }
            ChoiceRow {
                ChoiceChip("sticky", action.sticky) { onChange(action.copy(sticky = !action.sticky)) }
            }
        }
        is KeyAction.Snippet -> VisualTextField("snippet id", action.id) { onChange(action.copy(id = it)) }
        is KeyAction.Macro -> VisualTextField("macro", action.name) { onChange(action.copy(name = it)) }
        is KeyAction.Raw -> RawEditor(action, onChange)
        is KeyAction.Chord -> ChordEditor(action, onChange)
    }
}

@Composable
private fun RawEditor(action: KeyAction.Raw, onChange: (KeyAction) -> Unit) {
    val originalHex = action.bytes.toHex()
    var draft by remember(originalHex) { mutableStateOf(originalHex) }
    val parsed = draft.hexToBytesOrNull()
    VisualTextField("hex", draft) {
        draft = it
        it.hexToBytesOrNull()?.let { bytes -> onChange(KeyAction.Raw(bytes)) }
    }
    if (parsed == null) {
        Text(
            text = stringResource(R.string.settings_key_layout_invalid_hex),
            color = ZtsError,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ChordEditor(action: KeyAction.Chord, onChange: (KeyAction) -> Unit) {
    ChoiceRow {
        ModKey.entries.forEach { mod ->
            ChoiceChip(mod.id, mod in action.mods) {
                val next = if (mod in action.mods) action.mods - mod else action.mods + mod
                if (next.isNotEmpty()) onChange(action.copy(mods = next))
            }
        }
    }
    val named = action.key != null
    ChoiceRow {
        ChoiceChip("text", !named) {
            if (named) onChange(action.copy(text = "", key = null))
        }
        ChoiceChip("named", named) {
            if (!named) onChange(action.copy(text = null, key = NamedKey.ESC))
        }
    }
    if (named) {
        EnumChoiceRow(NamedKey.entries, action.key ?: NamedKey.ESC, { it.id }) {
            onChange(action.copy(text = null, key = it))
        }
    } else {
        VisualTextField("text", action.text.orEmpty()) { onChange(action.copy(text = it, key = null)) }
    }
}

@Composable
private fun <T> EnumChoiceRow(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    ChoiceRow {
        values.forEach { value -> ChoiceChip(label(value), value == selected) { onSelect(value) } }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = ZtsTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        content()
    }
}

@Composable
private fun ChoiceRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) ZtsGreen.copy(alpha = 0.22f) else ZtsBgCard, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) ZtsGreen else ZtsBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) ZtsGreen else ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun TinyButton(
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> ZtsTextSecondary.copy(alpha = 0.35f)
        danger -> ZtsError
        else -> ZtsTextPrimary
    }
    Box(
        modifier = Modifier
            .background(ZtsBgCard, RoundedCornerShape(6.dp))
            .border(1.dp, if (danger && enabled) ZtsError else ZtsBorder, RoundedCornerShape(6.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
private fun VisualTextField(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = ZtsTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = ZtsTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(ZtsGreen),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(ZtsBgSecondary, RoundedCornerShape(6.dp))
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 8.dp),
        )
    }
}

private fun ActionKind.defaultAction(): KeyAction = when (this) {
    ActionKind.TEXT -> KeyAction.Text("")
    ActionKind.NAMED -> KeyAction.Named(NamedKey.ESC)
    ActionKind.CHORD -> KeyAction.Chord(setOf(ModKey.CTRL), text = "c")
    ActionKind.RAW -> KeyAction.Raw(byteArrayOf(0x1b))
    ActionKind.MODIFIER -> KeyAction.Modifier(ModKey.CTRL)
    ActionKind.LAYER -> KeyAction.Layer("fn")
    ActionKind.APP -> KeyAction.App(AppAction.NEXT_FACE)
    ActionKind.SNIPPET -> KeyAction.Snippet("")
    ActionKind.MACRO -> KeyAction.Macro("")
}

private fun KeyAction.kind(): ActionKind = when (this) {
    is KeyAction.Text -> ActionKind.TEXT
    is KeyAction.Named -> ActionKind.NAMED
    is KeyAction.Chord -> ActionKind.CHORD
    is KeyAction.Raw -> ActionKind.RAW
    is KeyAction.Modifier -> ActionKind.MODIFIER
    is KeyAction.Layer -> ActionKind.LAYER
    is KeyAction.App -> ActionKind.APP
    is KeyAction.Snippet -> ActionKind.SNIPPET
    is KeyAction.Macro -> ActionKind.MACRO
}

private fun actionSummary(action: KeyAction): String = when (action) {
    is KeyAction.Text -> "text:${action.text}"
    is KeyAction.Named -> "named:${action.key.id}"
    is KeyAction.Chord -> "chord:${action.mods.joinToString("+") { it.id }}+${action.text ?: action.key?.id.orEmpty()}"
    is KeyAction.Raw -> "raw:${action.bytes.toHex()}"
    is KeyAction.Modifier -> "modifier:${action.mod.id}"
    is KeyAction.Layer -> "layer:${action.layer}${if (action.sticky) ":sticky" else ""}"
    is KeyAction.App -> "app:${action.action.id}"
    is KeyAction.Snippet -> "snippet:${action.id}"
    is KeyAction.Macro -> "macro:${action.name}"
}

private fun gestureSymbol(gesture: KeyGesture): String = when (gesture) {
    KeyGesture.TAP -> "tap"
    KeyGesture.UP -> "↑"
    KeyGesture.DOWN -> "↓"
    KeyGesture.LEFT -> "←"
    KeyGesture.RIGHT -> "→"
    KeyGesture.LONG_PRESS -> "long"
    KeyGesture.DOUBLE_TAP -> "double"
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also {
    val value = it[a]
    it[a] = it[b]
    it[b] = value
}

private fun Float.trimmed(): String = if (this == toInt().toFloat()) toInt().toString() else toString()
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
private fun String.hexToBytesOrNull(): ByteArray? {
    val value = trim()
    if (value.isEmpty() || value.length % 2 != 0) return null
    return runCatching {
        ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }.getOrNull()
}
