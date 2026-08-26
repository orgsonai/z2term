package com.zerotoship.z2term.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.components.ConfirmDialog
import com.zerotoship.z2term.ui.terminal.keyboard.KeyLayout
import com.zerotoship.z2term.ui.terminal.keyboard.KeyLayoutJson
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import com.zerotoship.z2term.ui.theme.ZtsWarning

/**
 * キー配列 1 枚を GUI / JSON で編集する固定画面（0.8.409〜0.8.412・段階 3〜4）。
 *
 * **JSON を正規の入口にする理由**: [KeyLayout] はタップだけでなく 4 方向フリック、長押し、
 * アクション列、2 段の分割、レイヤーまで持つ。これを「よく使う項目だけ」のフォームへ写すと、
 * 画面で開いて保存しただけでフォームが知らない割り当てを落とす。JSON ならモデルの全項目を
 * そのまま往復でき、AI に雛形を書いてもらって貼る入口にもなる。
 *
 * ⚠ 保存までは [initial] を一切変更しない。途中の壊れた JSON が、入力中のキーボードを消す
 * ことを避けるため。保存時は構造検証を通し、OS の入力メソッドで設定へ戻れない配列だけは
 * 明示警告を挟む。
 */
@Composable
fun KeyLayoutEditorSheet(
    initial: KeyLayout,
    onSave: (KeyLayout) -> Unit,
    onDelete: (KeyLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    val scroll = rememberScrollState()
    val editorScroll = rememberScrollState()
    // v1 の自作面にも記号子面とリセット基準を補う。開いただけでは保存せず、編集保存時に移行する。
    val editorInitial = remember(initial) { initial.withEditorDefaults() }
    val initialJson = remember(editorInitial) { KeyLayoutJson.toPrettyJsonString(editorInitial) }
    var source by remember(editorInitial.id) { mutableStateOf(initialJson) }
    var discardPending by remember { mutableStateOf(false) }
    var deletePending by remember { mutableStateOf(false) }
    var resetPending by remember { mutableStateOf(false) }
    var escapeWarningPending by remember { mutableStateOf(false) }
    var jsonMode by remember(initial.id) { mutableStateOf(false) }

    // id は選択状態から参照される固定値。貼り付けた JSON が id を変えていても、編集対象の
    // identity は保持する。name / rows 以下は JSON に書かれたものをそのまま採る。
    val decoded = remember(source, editorInitial.id, editorInitial.faceId, editorInitial.styleId) {
        KeyLayoutJson.fromJsonString(source)?.copy(
            id = editorInitial.id,
            faceId = editorInitial.faceId,
            styleId = editorInitial.styleId,
            defaultName = editorInitial.defaultName,
            defaultRows = editorInitial.defaultRows,
            defaultSymbolRows = editorInitial.defaultSymbolRows,
        )
    }
    val problems = remember(decoded) { decoded?.validate().orEmpty() }
    val candidate = decoded?.takeIf { problems.isEmpty() }

    fun closeNow() = onDismiss()

    fun requestClose() {
        if (source == initialJson) closeNow() else discardPending = true
    }

    fun requestSave() {
        val value = candidate ?: return
        if (value.hasEscapeHatch()) onSave(value) else escapeWarningPending = true
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ZtsBgPrimary,
        contentColor = ZtsTextPrimary,
    ) {
        BackHandler(onBack = ::requestClose)
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            SettingsPageTopBar(
                title = stringResource(
                    R.string.settings_key_layout_editor_title,
                    candidate?.name?.ifBlank { editorInitial.name } ?: editorInitial.name,
                ),
                onBack = ::requestClose,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        if (jsonMode) R.string.settings_key_layout_editor_desc
                        else R.string.settings_key_layout_visual_intro,
                    ),
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorButton(
                        label = stringResource(R.string.settings_key_layout_mode_visual),
                        modifier = Modifier.weight(1f),
                        primary = !jsonMode,
                        onClick = { jsonMode = false },
                    )
                    EditorButton(
                        label = stringResource(R.string.settings_key_layout_mode_json),
                        modifier = Modifier.weight(1f),
                        primary = jsonMode,
                        onClick = { jsonMode = true },
                    )
                }

                when {
                    decoded == null -> EditorStatus(
                        text = stringResource(R.string.settings_key_layout_editor_invalid_json),
                        color = ZtsError,
                    )
                    problems.isNotEmpty() -> EditorStatus(
                        text = stringResource(
                            R.string.settings_key_layout_editor_invalid_layout,
                            problems.joinToString("; "),
                        ),
                        color = ZtsError,
                    )
                    else -> {
                        EditorStatus(
                            text = stringResource(
                                R.string.settings_key_layout_editor_summary,
                                decoded.rows.size,
                                decoded.rows.sumOf { it.slots.size },
                                decoded.allKeys().size,
                            ),
                            color = ZtsGreen,
                        )
                        if (!decoded.hasEscapeHatch()) {
                            EditorStatus(
                                text = stringResource(R.string.settings_key_layout_editor_no_escape_inline),
                                color = ZtsWarning,
                            )
                        }
                    }
                }
            }

            if (jsonMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scroll)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicTextField(
                        value = source,
                        onValueChange = { source = it },
                        textStyle = TextStyle(
                            color = ZtsTextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(ZtsGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp, max = 520.dp)
                            .verticalScroll(editorScroll)
                            .background(ZtsBgSecondary, RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (candidate == null) ZtsError else ZtsBorder,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(12.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_key_layout_editor_id_note, editorInitial.id),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else if (candidate != null) {
                KeyLayoutVisualEditor(
                    layout = candidate,
                    modifier = Modifier.weight(1f),
                    onChange = { changed ->
                        // GUI と JSON は別の正本を持たない。GUI の 1 操作ごとに同じ JSON へ戻し、
                        // JSON タブへ切り替えればそのまま続きを編集できる。
                        source = KeyLayoutJson.toPrettyJsonString(
                            changed.copy(
                                id = editorInitial.id,
                                faceId = editorInitial.faceId,
                                styleId = editorInitial.styleId,
                            ),
                        )
                    },
                )
            }

            EditorButton(
                label = stringResource(R.string.settings_key_layout_restore_default),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                danger = true,
                onClick = { resetPending = true },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZtsBgPrimary)
                    .border(1.dp, ZtsBorder)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorButton(
                    label = stringResource(R.string.settings_key_layout_delete),
                    modifier = Modifier.weight(1f),
                    danger = true,
                    onClick = { deletePending = true },
                )
                EditorButton(
                    label = stringResource(R.string.action_cancel),
                    modifier = Modifier.weight(1f),
                    onClick = ::requestClose,
                )
                EditorButton(
                    label = stringResource(R.string.settings_key_layout_editor_save),
                    modifier = Modifier.weight(1f),
                    enabled = candidate != null,
                    primary = true,
                    onClick = ::requestSave,
                )
            }
        }
    }

    if (discardPending) {
        ConfirmDialog(
            title = stringResource(R.string.settings_key_layout_editor_discard_title),
            message = stringResource(R.string.settings_key_layout_editor_discard_msg),
            confirmLabel = stringResource(R.string.settings_key_layout_editor_discard),
            confirmColor = ZtsError,
            onConfirm = { discardPending = false; closeNow() },
            onCancel = { discardPending = false },
        )
    }
    if (escapeWarningPending) {
        ConfirmDialog(
            title = stringResource(R.string.settings_key_layout_editor_escape_title),
            message = stringResource(R.string.settings_key_layout_editor_escape_msg),
            confirmLabel = stringResource(R.string.settings_key_layout_editor_save_anyway),
            confirmColor = ZtsWarning,
            onConfirm = {
                escapeWarningPending = false
                candidate?.let(onSave)
            },
            onCancel = { escapeWarningPending = false },
        )
    }
    if (deletePending) {
        ConfirmDialog(
            title = stringResource(R.string.settings_key_layout_delete_confirm_title),
            message = stringResource(
                R.string.settings_key_layout_delete_confirm_msg,
                candidate?.name?.ifBlank { editorInitial.name } ?: editorInitial.name,
            ),
            confirmLabel = stringResource(R.string.settings_key_layout_delete),
            confirmColor = ZtsError,
            onConfirm = {
                deletePending = false
                onDelete(editorInitial)
            },
            onCancel = { deletePending = false },
        )
    }
    if (resetPending) {
        ConfirmDialog(
            title = stringResource(R.string.settings_key_layout_restore_default_title),
            message = stringResource(R.string.settings_key_layout_restore_default_msg),
            confirmLabel = stringResource(R.string.settings_key_layout_restore_default),
            confirmColor = ZtsError,
            onConfirm = {
                resetPending = false
                val restored = (decoded ?: editorInitial).restoreDefaults().copy(
                    id = editorInitial.id,
                    faceId = editorInitial.faceId,
                    styleId = editorInitial.styleId,
                )
                source = KeyLayoutJson.toPrettyJsonString(restored)
            },
            onCancel = { resetPending = false },
        )
    }
}

@Composable
private fun EditorStatus(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun EditorButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val border = when {
        danger && enabled -> ZtsError
        primary && enabled -> ZtsGreen
        else -> ZtsBorder
    }
    val text = when {
        !enabled -> ZtsTextSecondary.copy(alpha = 0.45f)
        danger -> ZtsError
        primary -> ZtsGreen
        else -> ZtsTextPrimary
    }
    Box(
        modifier = modifier
            .background(ZtsBgCard, RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
