package com.zerotoship.z2term.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zerotoship.z2term.ui.theme.AnsiGreen
import com.zerotoship.z2term.ui.theme.TerminalFontFamily
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import com.zerotoship.z2term.ui.theme.ZtsTextTertiary
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
    var inputText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (uiState.state == TerminalViewModel.TerminalState.IDLE) {
            viewModel.startTerminal()
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
                    TextButton(onClick = { viewModel.clearOutput() }) {
                        Text("Clear", color = ZtsTextSecondary, fontSize = 12.sp)
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
            // ターミナル本体
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                TerminalCanvasArea(
                    viewModel = viewModel,
                    redrawTick = redrawTick,
                    scrollOffset = scrollOffset
                )

                // スクロールバック閲覧中: 最下部へ戻るボタン
                if (scrollOffset > 0) {
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
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    if (inputText.isNotEmpty()) {
                        viewModel.sendInput(inputText + "\n")
                        inputText = ""
                    } else {
                        viewModel.sendSpecialKey(TerminalViewModel.SpecialKey.ENTER)
                    }
                },
                enabled = uiState.state == TerminalViewModel.TerminalState.RUNNING
            )
        }
    }
}

@Composable
private fun TerminalCanvasArea(
    viewModel: TerminalViewModel,
    redrawTick: Int,
    scrollOffset: Int
) {
    val density = LocalDensity.current
    // 縦ドラッグでスクロールバック閲覧
    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { _, dragAmount ->
            // 上スワイプ (dragAmount.y < 0) で履歴方向へ
            val lineHeightPx = with(density) { 18.sp.toPx() }
            val deltaLines = (-dragAmount.y / lineHeightPx).roundToInt()
            if (deltaLines != 0) viewModel.scrollBy(deltaLines)
        }
    }

    TerminalRenderer(
        emulator = viewModel.emulatorRef,
        fontSize = 13.sp,
        fontFamily = TerminalFontFamily,
        modifier = Modifier
            .fillMaxSize()
            .background(ZtsBgPrimary)
            .then(dragModifier),
        onSizeChanged = { rows, cols -> viewModel.onTerminalResize(rows, cols) },
        redrawTrigger = redrawTick,
        scrollOffset = scrollOffset
    )
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
private fun StatusBadge(state: TerminalViewModel.TerminalState, mode: String) {
    val (label, color) = when (state) {
        TerminalViewModel.TerminalState.IDLE -> "待機中" to ZtsTextTertiary
        TerminalViewModel.TerminalState.INSTALLING -> "セットアップ中" to ZtsTextSecondary
        TerminalViewModel.TerminalState.STARTING -> "起動中" to ZtsTextSecondary
        TerminalViewModel.TerminalState.RUNNING -> (if (mode.isNotEmpty()) mode else "稼働中") to ZtsGreen
        TerminalViewModel.TerminalState.EXITED -> "終了" to ZtsTextTertiary
        TerminalViewModel.TerminalState.ERROR -> "エラー" to androidx.compose.ui.graphics.Color.Red
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
    val keys = listOf(
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
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEach { (label, key) ->
            SpecialKeyButton(label = label, onClick = { onKey(key) })
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
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
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
                imeAction = ImeAction.Send,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )

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
