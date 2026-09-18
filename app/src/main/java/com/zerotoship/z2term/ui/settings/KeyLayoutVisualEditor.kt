@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zerotoship.z2term.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import java.util.Locale
import kotlin.math.roundToInt

/** Shared by the scrolling settings and the fixed preview; selection never edits the layout. */
internal class KeyLayoutEditorSelection(layout: KeyLayout) {
    var editingSymbols by mutableStateOf(false)
    var multiSelect by mutableStateOf(false)
    var selected by mutableStateOf(layout.keyPaths().firstOrNull()?.let(::setOf).orEmpty())
    var detailsY = 0f

    fun surface(layout: KeyLayout): KeyLayout =
        if (editingSymbols) layout.copy(rows = layout.symbolRows.orEmpty()) else layout

    fun changeSurface(layout: KeyLayout, symbols: Boolean) {
        if (editingSymbols == symbols) return
        editingSymbols = symbols
        selected = surface(layout).keyPaths().firstOrNull()?.let(::setOf).orEmpty()
        multiSelect = false
    }

    fun select(path: KeyCellPath) {
        selected = when {
            !multiSelect -> setOf(path)
            path !in selected -> selected + path
            selected.size > 1 -> selected - path
            else -> selected
        }
    }
}

@Composable
internal fun rememberKeyLayoutEditorSelection(layout: KeyLayout): KeyLayoutEditorSelection {
    val state = remember(layout.id) { KeyLayoutEditorSelection(layout) }
    val supportsSymbols = layout.faceId == com.zerotoship.z2term.ui.terminal.keyboard.KeyboardFace.ASCII.id &&
        layout.symbolRows != null
    LaunchedEffect(supportsSymbols) {
        if (!supportsSymbols) state.changeSurface(layout, false)
    }
    val paths = state.surface(layout).keyPaths()
    LaunchedEffect(paths, state.selected, state.multiSelect) {
        val valid = state.selected.filterTo(LinkedHashSet()) { it in paths }
        val repaired = if (valid.isEmpty()) paths.firstOrNull()?.let(::setOf).orEmpty() else valid
        state.selected = if (state.multiSelect) repaired else repaired.firstOrNull()?.let(::setOf).orEmpty()
    }
    return state
}

/**
 * 配列を見た形のまま複数選択し、基本項目とアクション列を編集する（0.8.410〜0.8.412・段階 4）。
 *
 * ⚠ この画面が触らないフィールドは [KeyDef.copy] でそのまま保持する。JSON で作ったレイヤーや
 * 将来の項目を、GUI でラベルを 1 文字直しただけで落とさないことが最優先。
 */
@Composable
internal fun KeyLayoutVisualEditor(
    layout: KeyLayout,
    modifier: Modifier = Modifier,
    state: KeyLayoutEditorSelection,
    onChange: (KeyLayout) -> Unit,
) {
    val supportsSymbols = layout.faceId == com.zerotoship.z2term.ui.terminal.keyboard.KeyboardFace.ASCII.id &&
        layout.symbolRows != null
    val workingLayout = state.surface(layout)
    val selected = state.selected
    val path = selected.firstOrNull { it in workingLayout.keyPaths() }
    val key = path?.let(workingLayout::keyAt)
    fun publish(changed: KeyLayout) {
        onChange(if (state.editingSymbols) layout.copy(name = changed.name, symbolRows = changed.rows) else changed)
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (supportsSymbols) {
            ChoiceRow {
                ChoiceChip(
                    label = stringResource(R.string.settings_key_layout_child_letters),
                    selected = !state.editingSymbols,
                ) { state.changeSurface(layout, false) }
                ChoiceChip(
                    label = stringResource(R.string.settings_key_layout_child_symbols),
                    selected = state.editingSymbols,
                ) { state.changeSurface(layout, true) }
            }
        }
        VisualTextField(
            label = stringResource(R.string.settings_key_layout_name),
            value = layout.name,
            onChange = { publish(workingLayout.copy(name = it)) },
        )
        Text(
            text = stringResource(R.string.settings_key_layout_visual_desc),
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontFamily = FontFamily.Monospace,
        )
        if (path != null && key != null) {
            Row(
                modifier = Modifier.fillMaxWidth().onGloballyPositioned { state.detailsY = it.positionInRoot().y },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
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
            }
            VisualTextField(
                label = stringResource(R.string.settings_key_layout_label),
                value = key.label,
                onChange = { value -> publish(workingLayout.updateKey(path) { it.copy(label = value) }) },
            )
            WidthEditor(workingLayout, selected, path, ::publish)
            BindingEditor(workingLayout, selected, path, key, ::publish)
            AppearanceEditor(workingLayout, selected, key, ::publish)
            if (selected.size == 1) {
                StructureEditor(
                    layout = workingLayout,
                    path = path,
                    onChange = ::publish,
                    onSelect = { state.selected = it?.let(::setOf).orEmpty() },
                )
            }

            if (key.layers.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_key_layout_layers_preserved, key.layers.keys.joinToString()),
                    color = ZtsWarning,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** Stays above Save/Cancel. Only overflowing key rows scroll within the bounded preview. */
@Composable
internal fun KeyLayoutEditorPreview(
    layout: KeyLayout,
    state: KeyLayoutEditorSelection,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .background(ZtsBgSecondary)
            .border(1.dp, ZtsBorder)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.multiSelect)
                    stringResource(R.string.settings_key_layout_selected_count, state.selected.size)
                else stringResource(R.string.settings_key_layout_preview_title),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                color = if (state.multiSelect) ZtsGreen else ZtsTextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ChoiceChip(stringResource(R.string.settings_key_layout_multi_select), state.multiSelect) {
                state.multiSelect = !state.multiSelect
                if (!state.multiSelect) state.selected = state.selected.firstOrNull()?.let(::setOf).orEmpty()
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            val surface = state.surface(layout)
            val rowCount = surface.rows.size.coerceAtLeast(1)
            val gaps = (10 + (rowCount - 1) * 4).dp
            val rowHeight = ((maxHeight - gaps) / rowCount).coerceIn(28.dp, 48.dp)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                LayoutPreview(
                    layout = surface,
                    selected = state.selected,
                    rowHeight = rowHeight,
                    onSelect = { state.select(it); onSelect() },
                )
            }
        }
    }
}

@Composable
private fun LayoutPreview(
    layout: KeyLayout,
    selected: Set<KeyCellPath>,
    rowHeight: Dp,
    onSelect: (KeyCellPath) -> Unit,
) {
    // Rows adapt to the preview budget; large layouts scroll rather than shrinking below 28dp.
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
                modifier = Modifier.fillMaxWidth().height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                row.slots.forEachIndexed { slotIndex, slot ->
                    PreviewContent(
                        content = slot.content,
                        path = KeyCellPath(rowIndex, slotIndex),
                        selected = selected,
                        onSelect = onSelect,
                        modifier = Modifier.weight(weights.getOrElse(slotIndex) { 1f }).fillMaxHeight(),
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
            val key = content.key
            val isHighlighted = key.highlighted
            val background = when {
                isSelected -> ZtsGreen.copy(alpha = 0.32f)
                isHighlighted -> ZtsGreen.copy(alpha = 0.75f)
                else -> ZtsBgCard
            }
            val foreground = when {
                isHighlighted && !isSelected -> ZtsBgSecondary
                key.labelTone == LabelTone.SECONDARY -> ZtsTextSecondary
                else -> ZtsTextPrimary
            }
            val fontSize = when (key.fontRole) {
                KeyFontRole.SMALL -> 9.sp
                KeyFontRole.NORMAL -> 11.sp
                KeyFontRole.MAIN -> 14.sp
            }
            Box(
                modifier = modifier
                    .background(
                        background,
                        RoundedCornerShape(5.dp),
                    )
                    .border(
                        1.dp,
                        if (isSelected) ZtsGreen else ZtsBorder,
                        RoundedCornerShape(5.dp),
                    )
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Button) { onSelect(path) }
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = previewKeyLabel(key),
                    color = foreground,
                    fontSize = fontSize,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                previewHint(key, KeyGesture.UP)?.let {
                    PreviewHint(it, Modifier.align(Alignment.TopCenter))
                }
                previewHint(key, KeyGesture.DOWN)?.let {
                    PreviewHint(it, Modifier.align(Alignment.BottomCenter))
                }
                previewHint(key, KeyGesture.LEFT)?.let {
                    PreviewHint(it, Modifier.align(Alignment.CenterStart).padding(start = 2.dp))
                }
                previewHint(key, KeyGesture.RIGHT)?.let {
                    PreviewHint(it, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
                }
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
                            modifier = Modifier.weight(part.ratio).fillMaxHeight(),
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
                            modifier = Modifier.weight(part.ratio).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewHint(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = ZtsGreen,
        fontSize = 7.sp,
        lineHeight = 7.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
    )
}

private fun previewKeyLabel(key: KeyDef): String =
    key.label.ifEmpty { previewActionLabel(key.actionsFor(KeyGesture.TAP).firstOrNull()) ?: "+" }

private fun previewHint(key: KeyDef, gesture: KeyGesture): String? =
    if (gesture in key.hintGestures) previewActionLabel(key.actionsFor(gesture).firstOrNull()) else null

private fun previewActionLabel(action: KeyAction?): String? = when (action) {
    is KeyAction.Text -> action.text
    is KeyAction.Named -> when (action.key) {
        NamedKey.BACKSPACE -> "⌫"
        NamedKey.ENTER -> "⏎"
        NamedKey.UP -> "↑"
        NamedKey.DOWN -> "↓"
        NamedKey.LEFT -> "←"
        NamedKey.RIGHT -> "→"
        NamedKey.ZENKAKU_HANKAKU -> "半/全"
        NamedKey.HENKAN -> "変換"
        NamedKey.MUHENKAN -> "無変換"
        NamedKey.KATAKANA_HIRAGANA -> "かな"
        NamedKey.EISU -> "英数"
        else -> action.key.id.uppercase()
    }
    is KeyAction.Modifier -> action.mod.id.uppercase()
    is KeyAction.App -> when (action.action) {
        AppAction.NEXT_FACE -> "↻"
        AppAction.PAD_PASTE -> "📋"
        AppAction.PAD_EMOJI -> "😀"
        AppAction.CLOSE_PAD -> "×"
        AppAction.HIDE_KEYBOARD -> "⌄"
        AppAction.SWITCH_IME -> "⌨"
        AppAction.SETTINGS -> "⚙"
        AppAction.IME_CONVERT -> "変換"
        AppAction.IME_DAKUTEN -> "゛"
    }
    is KeyAction.Chord -> action.text ?: action.key?.id?.uppercase()
    is KeyAction.Raw -> "0x"
    is KeyAction.Layer -> action.layer
    is KeyAction.Snippet -> action.id
    is KeyAction.Macro -> action.name
    null -> null
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
        mutableStateOf(formatEditorFloat((width as? KeyWidth.Fixed)?.ratio ?: 1f))
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
            SliderField(
                title = stringResource(R.string.settings_key_layout_width_slider),
                value = (draft.toFloatOrNull() ?: (width as? KeyWidth.Fixed)?.ratio ?: 1f)
                    .coerceIn(0.2f, 5f),
                range = 0.2f..5f,
                steps = 47,
                valueLabel = { formatKeyWidthSliderValue(it) },
                onChange = { value ->
                    val snapped = snapKeyWidthToTenth(value)
                    draft = formatKeyWidthSliderValue(snapped)
                    onChange(layout.updateSlotWidths(paths, KeyWidth.Fixed(snapped)))
                },
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
    EditorSection(stringResource(R.string.settings_key_layout_appearance), collapsible = true) {
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
            fontSize = 12.sp,
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
    EditorSection(stringResource(R.string.settings_key_layout_structure), collapsible = true) {
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
                fontSize = 12.sp,
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${index + 1}. ${actionSummary(action)}",
                        modifier = Modifier.fillMaxWidth(),
                        color = ZtsTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ChoiceRow {
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
        fontSize = 12.sp,
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
        is KeyAction.Named -> EnumChoiceRow(NamedKey.entries, action.key, ::namedKeyChoiceLabel) {
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
            fontSize = 12.sp,
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
        EnumChoiceRow(NamedKey.entries, action.key ?: NamedKey.ESC, ::namedKeyChoiceLabel) {
            onChange(action.copy(text = null, key = it))
        }
    } else {
        VisualTextField("text", action.text.orEmpty()) { onChange(action.copy(text = it, key = null)) }
    }
}

private fun namedKeyChoiceLabel(key: NamedKey): String =
    previewActionLabel(KeyAction.Named(key)) ?: key.id

@Composable
private fun <T> EnumChoiceRow(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    ChoiceRow {
        values.forEach { value -> ChoiceChip(label(value), value == selected) { onSelect(value) } }
    }
}

@Composable
private fun EditorSection(title: String, collapsible: Boolean = false, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(!collapsible) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (collapsible) "${if (expanded) "▾" else "▸"} $title" else title,
            modifier = Modifier.fillMaxWidth().then(
                if (collapsible) Modifier.clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = 16.dp) else Modifier,
            ),
            color = ZtsTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        if (expanded) content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { this.selected = selected }
            .background(if (selected) ZtsGreen.copy(alpha = 0.22f) else ZtsBgCard, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) ZtsGreen else ZtsBorder, RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) ZtsGreen else ZtsTextSecondary,
            fontSize = 12.sp,
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
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .background(ZtsBgCard, RoundedCornerShape(6.dp))
            .border(1.dp, if (danger && enabled) ZtsError else ZtsBorder, RoundedCornerShape(6.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun VisualTextField(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = ZtsTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = ZtsTextPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(ZtsGreen),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
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

/** Float の丸め誤差 (`1.3000001` 等) を編集欄へ見せず、通常入力の精度は 3 桁まで保つ。 */
internal fun formatEditorFloat(value: Float): String = if (!value.isFinite()) value.toString() else
    String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

/** 幅スライダーは表示だけでなく保存値そのものも必ず 0.1 刻みにそろえる。 */
internal fun snapKeyWidthToTenth(value: Float): Float = (value * 10f).roundToInt() / 10f
internal fun formatKeyWidthSliderValue(value: Float): String =
    String.format(Locale.US, "%.1f", snapKeyWidthToTenth(value))
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
private fun String.hexToBytesOrNull(): ByteArray? {
    val value = trim()
    if (value.isEmpty() || value.length % 2 != 0) return null
    return runCatching {
        ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }.getOrNull()
}
