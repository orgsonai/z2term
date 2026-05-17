package com.zerotoship.z2term.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.ui.settings.SettingsSheet
import com.zerotoship.z2term.ui.theme.AnsiGreen
import com.zerotoship.z2term.ui.theme.TerminalFontFamily
import com.zerotoship.z2term.ui.theme.TerminalFontOptions
import com.zerotoship.z2term.ui.theme.rememberTerminalFontFamily
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import com.zerotoship.z2term.ui.theme.ZtsTextTertiary
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Z2Term M2 ターミナル画面。
 *
 * 構成:
 *   ┌────────────────────────────────────┐
 *   │ TopBar: Z2Term + ステータス + Clear/再起動  │
 *   ├────────────────────────────────────┤
 *   │ TerminalRenderer (Compose Canvas)            │
 *   │   ・縦ドラッグでスクロールバック閲覧         │
 *   ├────────────────────────────────────┤
 *   │ 特殊キーバー                                │
 *   ├────────────────────────────────────┤
 *   │ 入力欄                                      │
 *   └────────────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val redrawTick by viewModel.redrawTick.collectAsState()
    val scrollOffset by viewModel.scrollOffset.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val sessionList by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeId.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    var inputField by remember { mutableStateOf(TextFieldValue("")) }
    var realtimeMode by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (uiState.state == TerminalSession.TerminalState.IDLE) {
            viewModel.startTerminal()
        }
    }

    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Z2Term",
                            style = MaterialTheme.typography.titleLarge,
                            color = ZtsGreen
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(uiState.state, uiState.mode)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.copyAllToClipboard() }) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "全文コピー",
                            tint = ZtsTextSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.pasteFromClipboard() }) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = "ペースト",
                            tint = ZtsTextSecondary
                        )
                    }
                    TextButton(onClick = { viewModel.clearOutput() }) {
                        Text("Clear", color = ZtsTextSecondary, fontSize = 12.sp)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "設定",
                            tint = ZtsTextSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.restart() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "再起動",
                            tint = ZtsTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ZtsBgSecondary,
                    titleContentColor = ZtsTextPrimary
                )
            )
        },
        containerColor = ZtsBgPrimary
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // タブバー (セッションが 1 つでも + ボタンが必要なので常時表示)
            TabBar(
                sessions = sessionList,
                activeId = activeSessionId,
                onSelect = { viewModel.selectSession(it) },
                onClose = { viewModel.closeSession(it) },
                onAdd = { viewModel.openNewSession() }
            )
            HorizontalDivider(color = ZtsBorder, thickness = 1.dp)

            // ターミナル本体
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val fontFamily = rememberTerminalFontFamily(TerminalFontOptions.byId(settings.fontId))
                // activeSession が切替わると key で再構築 → focus 等もリセット
                androidx.compose.runtime.key(activeSession.id) {
                    TerminalCanvasArea(
                        viewModel = viewModel,
                        redrawTick = redrawTick,
                        scrollOffset = scrollOffset,
                        fontSizeSp = settings.fontSizeSp,
                        fontFamily = fontFamily,
                        selection = selection
                    )
                }

                // 選択モード中: コピー / キャンセルボタン
                if (selection != null) {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        onCopy = { viewModel.copySelectionToClipboard() },
                        onCancel = { viewModel.cancelSelection() }
                    )
                } else if (scrollOffset > 0) {
                    JumpToBottomButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 12.dp),
                        onClick = { viewModel.jumpToBottom() }
                    )
                }
            }

            HorizontalDivider(color = ZtsBorder, thickness = 1.dp)
            SpecialKeyBar(onKey = { viewModel.sendSpecialKey(it) })

            HorizontalDivider(color = ZtsBorder, thickness = 1.dp)
            InputBar(
                value = inputField,
                onValueChange = { newValue ->
                    if (realtimeMode) {
                        // composition (IME 確定前) は preedit として表示維持
                        if (newValue.composition != null) {
                            inputField = newValue
                        } else if (newValue.text.isNotEmpty()) {
                            // 確定したら PTY へ即送出してフィールドをクリア
                            viewModel.sendInput(newValue.text)
                            inputField = TextFieldValue("")
                        } else {
                            inputField = newValue
                        }
                    } else {
                        inputField = newValue
                    }
                },
                onSend = {
                    if (inputField.text.isNotEmpty()) {
                        viewModel.sendInput(inputField.text + "\n")
                        inputField = TextFieldValue("")
                    } else {
                        viewModel.sendSpecialKey(TerminalViewModel.SpecialKey.ENTER)
                    }
                },
                realtimeMode = realtimeMode,
                onToggleRealtime = { realtimeMode = !realtimeMode },
                enabled = uiState.state == TerminalSession.TerminalState.RUNNING
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            snapshot = settings,
            onThemeChange = { viewModel.updateTheme(it) },
            onFontSizeChange = { viewModel.updateFontSize(it) },
            onScrollbackChange = { viewModel.updateScrollbackLines(it) },
            onDistroChange = { viewModel.updateDistro(it) },
            onFontIdChange = { viewModel.updateFontId(it) },
            onAmbiguousAsWideChange = { viewModel.updateAmbiguousAsWide(it) },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun TerminalCanvasArea(
    viewModel: TerminalViewModel,
    redrawTick: Int,
    scrollOffset: Int,
    fontSizeSp: Float,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    selection: TerminalViewModel.Selection?
) {
    val density = LocalDensity.current
    var charWidthPx by remember { mutableFloatStateOf(0f) }
    var charHeightPx by remember { mutableFloatStateOf(0f) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 物理キーボードからの入力を拾うため focus を要求
        runCatching { focusRequester.requestFocus() }
    }

    fun pointerToCell(offset: androidx.compose.ui.geometry.Offset): Pair<Int, Int>? {
        if (charWidthPx <= 0f || charHeightPx <= 0f) return null
        val buffer = viewModel.emulatorRef.buffer
        val col = (offset.x / charWidthPx).toInt().coerceIn(0, buffer.columns - 1)
        val viewRow = (offset.y / charHeightPx).toInt().coerceIn(0, buffer.rows - 1)
        val startRowIndex = buffer.scrollbackSize - scrollOffset.coerceIn(0, buffer.scrollbackSize)
        return Pair((startRowIndex + viewRow).coerceAtLeast(0), col)
    }

    val scrollDragModifier = Modifier.pointerInput(selection != null) {
        if (selection != null) return@pointerInput
        detectDragGestures { _, dragAmount ->
            val lineHeightPx = with(density) { fontSizeSp.sp.toPx() }
            val deltaLines = (-dragAmount.y / lineHeightPx).roundToInt()
            if (deltaLines != 0) viewModel.scrollBy(deltaLines)
        }
    }

    // ピンチで fontSize、2 本指縦パンでスクロールバック閲覧
    val transformModifier = Modifier.pointerInput(Unit) {
        detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
            if (abs(zoom - 1f) > 0.005f) {
                val current = fontSizeSp
                val target = (current * zoom).coerceIn(8f, 32f)
                if (abs(target - current) >= 0.5f) {
                    viewModel.updateFontSize(target)
                }
            }
            if (charHeightPx > 0f && abs(pan.y) >= charHeightPx) {
                val deltaLines = (-pan.y / charHeightPx).roundToInt()
                if (deltaLines != 0) viewModel.scrollBy(deltaLines)
            }
        }
    }

    val selectionDragModifier = Modifier.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { pos ->
                pointerToCell(pos)?.let { (r, c) -> viewModel.beginSelection(r, c) }
            },
            onDrag = { change, _ ->
                pointerToCell(change.position)?.let { (r, c) -> viewModel.updateSelection(r, c) }
                change.consume()
            },
            onDragEnd = { /* 選択は維持、アクションバーで確定 */ },
            onDragCancel = { /* 維持 */ }
        )
    }

    val sel = selection
    TerminalRenderer(
        emulator = viewModel.emulatorRef,
        fontSize = fontSizeSp.sp,
        fontFamily = fontFamily,
        modifier = Modifier
            .fillMaxSize()
            .background(ZtsBgPrimary)
            .focusRequester(focusRequester)
            .focusTarget()
            .onKeyEvent { event ->
                val bytes = PhysicalKeyMapper.map(event)
                if (bytes != null) {
                    viewModel.sendRawBytes(bytes)
                    true
                } else false
            }
            .then(transformModifier)
            .then(selectionDragModifier)
            .then(scrollDragModifier),
        onSizeChanged = { rows, cols -> viewModel.onTerminalResize(rows, cols) },
        onCharSizeChanged = { w, h -> charWidthPx = w; charHeightPx = h },
        redrawTrigger = redrawTick,
        scrollOffset = scrollOffset,
        selectionStartRow = sel?.anchorRow ?: -1,
        selectionStartCol = sel?.anchorCol ?: -1,
        selectionEndRow = sel?.focusRow ?: -1,
        selectionEndCol = sel?.focusCol ?: -1
    )
}

@Composable
private fun TabBar(
    sessions: List<TerminalSession>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onAdd: () -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        sessions.forEachIndexed { index, s ->
            val label by s.label.collectAsState()
            val selected = s.id == activeId
            TabChip(
                index = index + 1,
                label = label,
                selected = selected,
                showClose = sessions.size > 1,
                onClick = { onSelect(s.id) },
                onClose = { onClose(s.id) }
            )
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "新規セッション",
                tint = ZtsGreen
            )
        }
    }
}

@Composable
private fun TabChip(
    index: Int,
    label: String,
    selected: Boolean,
    showClose: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (selected) ZtsGreen else ZtsBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (selected) ZtsBgPrimary else ZtsBgSecondary)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
        ) {
            Text(
                text = "$index:$label",
                color = if (selected) ZtsGreen else ZtsTextSecondary,
                fontFamily = TerminalFontFamily,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        if (showClose) {
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "閉じる",
                    tint = ZtsTextTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, ZtsBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel) {
            Text("キャンセル", color = ZtsTextSecondary, fontSize = 13.sp)
        }
        Button(
            onClick = onCopy,
            colors = ButtonDefaults.buttonColors(
                containerColor = ZtsGreen,
                contentColor = ZtsBgPrimary
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("コピー", fontSize = 13.sp)
        }
    }
}

@Composable
private fun JumpToBottomButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = ZtsBgSecondary,
        contentColor = ZtsGreen,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = "最下部へ"
        )
    }
}

@Composable
private fun StatusBadge(state: TerminalSession.TerminalState, mode: String) {
    val (label, color) = when (state) {
        TerminalSession.TerminalState.IDLE -> "待機中" to ZtsTextTertiary
        TerminalSession.TerminalState.INSTALLING -> "セットアップ中" to ZtsTextSecondary
        TerminalSession.TerminalState.STARTING -> "起動中" to ZtsTextSecondary
        TerminalSession.TerminalState.RUNNING -> (if (mode.isNotEmpty()) mode else "稼働中") to ZtsGreen
        TerminalSession.TerminalState.EXITED -> "終了" to ZtsTextTertiary
        TerminalSession.TerminalState.ERROR -> "エラー" to androidx.compose.ui.graphics.Color.Red
    }
    Box(
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SpecialKeyBar(onKey: (TerminalViewModel.SpecialKey) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val primary = listOf(
        "ESC" to TerminalViewModel.SpecialKey.ESC,
        "TAB" to TerminalViewModel.SpecialKey.TAB,
        "^C" to TerminalViewModel.SpecialKey.CTRL_C,
        "^D" to TerminalViewModel.SpecialKey.CTRL_D,
        "^L" to TerminalViewModel.SpecialKey.CTRL_L,
        "←" to TerminalViewModel.SpecialKey.LEFT,
        "↓" to TerminalViewModel.SpecialKey.DOWN,
        "↑" to TerminalViewModel.SpecialKey.UP,
        "→" to TerminalViewModel.SpecialKey.RIGHT
    )
    val extra = listOf(
        "^A" to TerminalViewModel.SpecialKey.CTRL_A,
        "^E" to TerminalViewModel.SpecialKey.CTRL_E,
        "^K" to TerminalViewModel.SpecialKey.CTRL_K,
        "^R" to TerminalViewModel.SpecialKey.CTRL_R,
        "^U" to TerminalViewModel.SpecialKey.CTRL_U,
        "^W" to TerminalViewModel.SpecialKey.CTRL_W,
        "^Z" to TerminalViewModel.SpecialKey.CTRL_Z,
        "Home" to TerminalViewModel.SpecialKey.HOME,
        "End" to TerminalViewModel.SpecialKey.END,
        "PgUp" to TerminalViewModel.SpecialKey.PAGE_UP,
        "PgDn" to TerminalViewModel.SpecialKey.PAGE_DOWN,
        "F1" to TerminalViewModel.SpecialKey.F1,
        "F2" to TerminalViewModel.SpecialKey.F2,
        "F3" to TerminalViewModel.SpecialKey.F3,
        "F4" to TerminalViewModel.SpecialKey.F4,
        "F5" to TerminalViewModel.SpecialKey.F5,
        "F6" to TerminalViewModel.SpecialKey.F6,
        "F7" to TerminalViewModel.SpecialKey.F7,
        "F8" to TerminalViewModel.SpecialKey.F8,
        "F9" to TerminalViewModel.SpecialKey.F9,
        "F10" to TerminalViewModel.SpecialKey.F10,
        "F11" to TerminalViewModel.SpecialKey.F11,
        "F12" to TerminalViewModel.SpecialKey.F12
    )

    Column(modifier = Modifier.background(ZtsBgSecondary)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            primary.forEach { (label, key) ->
                SpecialKeyButton(label = label, onClick = { onKey(key) })
            }
            SpecialKeyButton(
                label = if (expanded) "▾" else "▸",
                onClick = { expanded = !expanded }
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                extra.forEach { (label, key) ->
                    SpecialKeyButton(label = label, onClick = { onKey(key) })
                }
            }
        }
    }
}

@Composable
private fun SpecialKeyButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .background(ZtsBgPrimary)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = AnsiGreen,
                fontFamily = TerminalFontFamily,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun InputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    realtimeMode: Boolean,
    onToggleRealtime: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "❯",
            style = TextStyle(
                fontFamily = TerminalFontFamily,
                fontSize = 16.sp,
                color = AnsiGreen
            ),
            modifier = Modifier.padding(end = 8.dp)
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = TerminalFontFamily,
                fontSize = 14.sp,
                color = ZtsTextPrimary
            ),
            cursorBrush = SolidColor(ZtsGreen),
            keyboardOptions = KeyboardOptions(
                imeAction = if (realtimeMode) ImeAction.Default else ImeAction.Send,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )

        // リアルタイム送信トグル
        IconButton(onClick = onToggleRealtime, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = if (realtimeMode) "リアルタイム ON" else "リアルタイム OFF",
                tint = if (realtimeMode) ZtsGreen else ZtsTextTertiary
            )
        }

        if (!realtimeMode) {
            Button(
                onClick = onSend,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZtsGreen,
                    contentColor = ZtsBgPrimary,
                    disabledContainerColor = ZtsBorder
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "送信",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
