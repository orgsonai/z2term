package com.zerotoship.z2term.ui.snippets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.snippets.Snippet
import com.zerotoship.z2term.snippets.SnippetStore
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.ssh.SshProfilesBody
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** ツールシートのタブ。スニペット一覧と SSH/SFTP プロファイル一覧を 1 枚にまとめる。 */
private enum class ToolsTab { SNIPPETS, SSH }

/**
 * ツールシート (ツールバーの 📜 から開く)。
 *
 * 上部のタブで「スニペット」と「SSH / SFTP」を切替える。
 *  - スニペット: よく使うコマンドを挿入 ([onRun])。並べ替え / 編集 / 削除可。
 *  - SSH / SFTP: 保存したホストへ接続 ([onConnect]) / SFTP で開く ([onSftp])。
 *
 * GUI タブからは SSH 接続の概念が無いので [showSshTab] = false でスニペットのみ表示する。
 *
 * 永続化はそれぞれ [SnippetStore] / SshProfileStore (DataStore)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsSheet(
    onDismiss: () -> Unit,
    onRun: (String) -> Unit,
    onConnect: (SshProfile) -> Unit = {},
    onSftp: (SshProfile) -> Unit = {},
    showSshTab: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var forceClose by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // スクロール途中の下スワイプで誤って閉じないよう、最上部のときだけスワイプ閉じを許可。
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden) forceClose || scrollState.value == 0 else true
        }
    )
    val closeSheet: () -> Unit = {
        forceClose = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    var tab by remember { mutableStateOf(ToolsTab.SNIPPETS) }
    // タブ切替時はスクロールを先頭へ戻す (前のタブの位置を引き継がない)。
    LaunchedEffect(tab) { scrollState.scrollTo(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZtsBgPrimary,
        contentColor = ZtsTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        contentWindowInsets = { WindowInsets.systemBars },
        dragHandle = { Z2TermDragHandle(onClose = closeSheet) }
    ) {
        BackHandler(onBack = closeSheet)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showSshTab) {
                ToolsTabBar(selected = tab, onSelect = { tab = it })
            }
            when (tab) {
                ToolsTab.SNIPPETS -> SnippetsBody(onRun = onRun, onDismiss = onDismiss)
                ToolsTab.SSH -> SshProfilesBody(
                    onConnect = { p -> onConnect(p); onDismiss() },
                    onSftp = { p -> onSftp(p); onDismiss() }
                )
            }
        }
    }
}

/** スニペット / SSH・SFTP を切替えるセグメントタブ。 */
@Composable
private fun ToolsTabBar(selected: ToolsTab, onSelect: (ToolsTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabChip(
            label = stringResource(R.string.tools_tab_snippets),
            selected = selected == ToolsTab.SNIPPETS,
            modifier = Modifier.weight(1f),
            onSelect = { onSelect(ToolsTab.SNIPPETS) }
        )
        TabChip(
            label = stringResource(R.string.tools_tab_ssh),
            selected = selected == ToolsTab.SSH,
            modifier = Modifier.weight(1f),
            onSelect = { onSelect(ToolsTab.SSH) }
        )
    }
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val bg = if (selected) ZtsGreen.copy(alpha = 0.18f) else ZtsBgCard
    val border = if (selected) ZtsGreen else ZtsBorder
    val fg = if (selected) ZtsGreen else ZtsTextPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * スニペット一覧本体 (タブのコンテンツ)。スクロールは呼び出し側 ([SnippetsSheet]) が持つ。
 *
 * - リスト表示: 各行をタップすると [onRun] で command 文字列をターミナルへ挿入する
 *   (Enter は付けない、ユーザーが必要なら手動で確定)。
 * - 「編集」: 編集モードに切替えて name / command を編集、削除も可能。
 * - 「+ 新規」: 空エントリで編集モードに入る。
 */
@Composable
private fun SnippetsBody(
    onRun: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SnippetStore(context.applicationContext) }
    val snippetsFlow = remember(store) {
        store.snippets.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }
    val snippets by snippetsFlow.collectAsState()
    var editing by remember { mutableStateOf<Snippet?>(null) }

    // 初回だけサンプル (ls -la --color=auto) を投入。
    LaunchedEffect(Unit) { store.ensureSeeded() }

    // ドラッグ並べ替え: 表示順はローカル [order] を真実とし、ドラッグ中は flow 更新で上書きしない。
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableStateOf(0f) }
    var order by remember { mutableStateOf<List<Snippet>>(emptyList()) }
    LaunchedEffect(snippets, draggingId) { if (draggingId == null) order = snippets }
    // 1 行ぶんのピッチ (行高 + Column の spacedBy 10dp)。これを超えて動かしたら隣と入れ替える。
    val rowPitchPx = with(LocalDensity.current) { (SNIPPET_ROW_HEIGHT + 10.dp).toPx() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val currentEdit = editing
        if (currentEdit == null) {
            ListHeader(onNew = { editing = newSnippet() })
            if (order.isEmpty()) {
                EmptyState()
            } else {
                // key(s.id) でノード identity を固定 → 並べ替え中も掴んだ行に
                // ポインタ(ドラッグ)が追従する (Column でも item が移動できる)。
                order.forEach { s ->
                    key(s.id) {
                        val dragging = s.id == draggingId
                        SnippetRow(
                            snippet = s,
                            dragging = dragging,
                            dragOffsetY = if (dragging) dragDy else 0f,
                            onRun = {
                                onRun(s.command)
                                onDismiss()
                            },
                            onEdit = { editing = s },
                            onDelete = { scope.launch { store.delete(s.id) } },
                            onDragStart = { draggingId = s.id; dragDy = 0f },
                            onDrag = { dy ->
                                dragDy += dy
                                val cur = order.indexOfFirst { it.id == draggingId }
                                if (cur >= 0) {
                                    if (dragDy > rowPitchPx && cur < order.lastIndex) {
                                        order = order.toMutableList()
                                            .also { it.add(cur + 1, it.removeAt(cur)) }
                                        dragDy -= rowPitchPx
                                    } else if (dragDy < -rowPitchPx && cur > 0) {
                                        order = order.toMutableList()
                                            .also { it.add(cur - 1, it.removeAt(cur)) }
                                        dragDy += rowPitchPx
                                    }
                                }
                            },
                            onDragEnd = {
                                val finalOrder = order
                                draggingId = null
                                dragDy = 0f
                                scope.launch { store.replaceAll(finalOrder) }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            HintBlock()
        } else {
            EditForm(
                initial = currentEdit,
                onSave = { saved ->
                    scope.launch {
                        store.upsert(saved)
                        editing = null
                    }
                },
                onCancel = { editing = null }
            )
        }
    }
}

private fun newSnippet() = Snippet(
    id = UUID.randomUUID().toString(),
    label = "",
    command = ""
)

/** スニペット 1 行の固定高さ。ドラッグ並べ替えのピッチ計算に使うため固定にする。 */
private val SNIPPET_ROW_HEIGHT = 52.dp

@Composable
private fun ListHeader(onNew: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.snippets_title),
            color = ZtsGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
        Box(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ZtsGreen.copy(alpha = 0.18f))
                .border(1.dp, ZtsGreen, RoundedCornerShape(8.dp))
                .clickable(onClick = onNew)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.snippets_new),
                color = ZtsGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.snippets_empty),
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HintBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(
            text = stringResource(R.string.snippets_hint),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * スニペット 1 行 (固定高 [SNIPPET_ROW_HEIGHT])。
 *  - 左側のテキスト領域をタップ → 挿入 (onRun)。コマンドを主表示、ラベルは上に薄く添える。
 *  - 右側に ≡ (ドラッグして並べ替え) / ✎ (編集) / ✕ (削除)。
 *  - ドラッグ中は緑枠 + 前面 (zIndex) + 指追従 (translationY) で表示。
 */
@Composable
private fun SnippetRow(
    snippet: Snippet,
    dragging: Boolean,
    dragOffsetY: Float,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SNIPPET_ROW_HEIGHT)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, if (dragging) ZtsGreen else ZtsBorder, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // テキスト領域 = 挿入ボタン (行タップで挿入)。コマンドを主表示。
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onRun)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (snippet.label.isNotBlank()) {
                Text(
                    text = snippet.label,
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            Text(
                text = snippet.command.ifBlank { stringResource(R.string.snippets_empty_command) },
                color = ZtsTextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
        // ドラッグハンドル (≡)。ここを掴んで上下に動かすと並べ替え。
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .pointerInput(snippet.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        }
                    )
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "≡",
                color = if (dragging) ZtsGreen else ZtsTextSecondary,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        IconCell(label = "✎", onClick = onEdit)
        IconCell(label = "✕", danger = true, onClick = onDelete)
    }
}

@Composable
private fun IconCell(
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val fg = when {
        !enabled -> ZtsTextSecondary.copy(alpha = 0.3f)
        danger -> ZtsError
        else -> ZtsTextSecondary
    }
    Box(
        modifier = Modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EditForm(
    initial: Snippet,
    onSave: (Snippet) -> Unit,
    onCancel: () -> Unit
) {
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var command by remember(initial.id) { mutableStateOf(initial.command) }

    Text(
        text = if (initial.label.isEmpty() && initial.command.isEmpty())
            stringResource(R.string.snippets_new_entry_title)
        else
            stringResource(R.string.snippets_edit_entry_title),
        color = ZtsGreen,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace
    )

    Field(
        label = stringResource(R.string.snippets_label_field),
        value = label,
        onChange = { label = it },
        placeholder = stringResource(R.string.snippets_label_placeholder)
    )
    Field(
        label = stringResource(R.string.snippets_command_field),
        value = command,
        onChange = { command = it },
        placeholder = "ls -la --color=auto",
        multiline = true
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallButton(label = stringResource(R.string.action_cancel), onClick = onCancel)
        Box(modifier = Modifier.weight(1f))
        SmallButton(
            label = stringResource(R.string.action_save),
            accent = true,
            onClick = {
                if (command.isNotBlank()) {
                    onSave(initial.copy(label = label.trim(), command = command))
                }
            }
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    multiline: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = ZtsTextSecondary.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (multiline) 6 else 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = !multiline,
                textStyle = TextStyle(
                    color = ZtsTextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(ZtsGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SmallButton(
    label: String,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val border = when {
        danger -> ZtsError
        accent -> ZtsGreen
        else -> ZtsBorder
    }
    val fg = when {
        danger -> ZtsError
        accent -> ZtsGreen
        else -> ZtsTextPrimary
    }
    val bg = when {
        accent -> ZtsGreen.copy(alpha = 0.18f)
        else -> ZtsBgSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
