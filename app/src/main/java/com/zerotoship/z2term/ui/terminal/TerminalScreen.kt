package com.zerotoship.z2term.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Z2Term M1 ターミナル画面。
 *
 * 構成:
 * ┌──────────────────────────────┐
 * │ TopBar: タイトル + ステータス + 再起動 │
 * ├──────────────────────────────┤
 * │                              │
 * │  ターミナル出力エリア          │
 * │  (append-only)                │
 * │                              │
 * ├──────────────────────────────┤
 * │ 特殊キーバー                   │
 * │ [ESC][TAB][^C][^D][↑][↓][←][→] │
 * ├──────────────────────────────┤
 * │ 入力欄 + 送信ボタン            │
 * └──────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }

    // 初回起動
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
                    IconButton(onClick = { viewModel.clearOutput() }) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZtsTextSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.restart() }) {
                        androidx.compose.material3.Icon(
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
            // ターミナル出力エリア
            TerminalOutputArea(
                output = uiState.output,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // 特殊キーバー
            HorizontalDivider(color = ZtsBorder, thickness = 1.dp)
            SpecialKeyBar(
                onKey = { viewModel.sendSpecialKey(it) }
            )

            // 入力欄
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
private fun TerminalOutputArea(output: String, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    LaunchedEffect(output) {
        // 出力追加時に最下部へスクロール
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Box(
        modifier = modifier
            .background(ZtsBgPrimary)
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        Text(
            text = if (output.isEmpty()) "Z2Term v0.1.0-alpha\n初回起動中…\n" else output,
            style = TextStyle(
                fontFamily = TerminalFontFamily,
                fontSize = 13.sp,
                color = ZtsTextPrimary,
                lineHeight = 18.sp
            )
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
