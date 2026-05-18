package com.zerotoship.z2term.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.ui.terminal.components.SpecialKeyBar
import com.zerotoship.z2term.ui.terminal.input.TerminalInputView
import com.zerotoship.z2term.ui.terminal.keyboard.TerminalKeyboard
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/** キーボードモード。CUSTOM=独自キーボード、SYSTEM=OS IME + 特殊キーバー */
enum class KeyboardMode { CUSTOM, SYSTEM }

/**
 * 単一セッション用の画面。
 *
 * 既定は CUSTOM (独自キーボード、OS IME 非表示)。
 * TopBar の「Aあ」ボタンで SYSTEM に切替えると OS IME を起動し、
 * 画面下は SpecialKeyBar (ESC/TAB/CTRL/矢印/Enter/C-C/D/L) に切替わる。
 */
@Composable
fun TerminalScreen(
    session: TerminalSession,
    modifier: Modifier = Modifier
) {
    val label by session.label.collectAsState()
    val cwd by session.cwd.collectAsState()
    val uiState by session.uiState.collectAsState()
    var ctrlSticky by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(KeyboardMode.CUSTOM) }
    var inputViewRef by remember { mutableStateOf<TerminalInputView?>(null) }

    LaunchedEffect(session.id) {
        if (!session.isRunning) session.startTerminal()
    }

    // モード切替を InputView に反映
    LaunchedEffect(keyboardMode, inputViewRef) {
        val v = inputViewRef ?: return@LaunchedEffect
        v.imeEnabled = (keyboardMode == KeyboardMode.SYSTEM)
        if (keyboardMode == KeyboardMode.SYSTEM) v.requestKeyboard()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZtsBgPrimary)
            .systemBarsPadding()
            .imePadding()
    ) {
        TopBar(
            label = label,
            cwd = cwd,
            state = uiState.state.name,
            mode = uiState.mode,
            keyboardMode = keyboardMode,
            onToggleKeyboardMode = {
                keyboardMode = if (keyboardMode == KeyboardMode.CUSTOM)
                    KeyboardMode.SYSTEM else KeyboardMode.CUSTOM
            }
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            TerminalRenderer(session = session, modifier = Modifier.fillMaxSize())
            AndroidView(
                factory = { ctx ->
                    TerminalInputView(ctx).also { v ->
                        v.session = session
                        v.imeEnabled = (keyboardMode == KeyboardMode.SYSTEM)
                        inputViewRef = v
                    }
                },
                update = { v ->
                    v.session = session
                    v.ctrlSticky = ctrlSticky
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        when (keyboardMode) {
            KeyboardMode.CUSTOM -> TerminalKeyboard(
                onBytes = { session.writeBytes(it) },
                onCursorKey = { key ->
                    session.writeBytes(session.emulator.cursorKeyBytes(key))
                },
                onRequestSystemKeyboard = { keyboardMode = KeyboardMode.SYSTEM }
            )
            KeyboardMode.SYSTEM -> SpecialKeyBar(
                session = session,
                ctrlSticky = ctrlSticky,
                onCtrlToggle = { ctrlSticky = !ctrlSticky }
            )
        }
    }
}

@Composable
private fun TopBar(
    label: String,
    cwd: String,
    state: String,
    mode: String,
    keyboardMode: KeyboardMode,
    onToggleKeyboardMode: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = ZtsGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
        if (mode.isNotEmpty()) {
            Text(
                text = "[$mode]",
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        if (cwd.isNotEmpty()) {
            Text(
                text = cwd,
                color = ZtsTextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1
            )
        }
        Box(modifier = Modifier.weight(1f))

        KeyboardToggleButton(keyboardMode, onToggleKeyboardMode)

        Text(
            text = state,
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun KeyboardToggleButton(
    mode: KeyboardMode,
    onClick: () -> Unit
) {
    val label = if (mode == KeyboardMode.CUSTOM) "Aあ" else "z2"
    val bg = if (mode == KeyboardMode.SYSTEM) ZtsGreen else ZtsBgCard
    val fg = if (mode == KeyboardMode.SYSTEM) Color.Black else ZtsTextPrimary
    val border = if (mode == KeyboardMode.SYSTEM) ZtsGreen else ZtsBorder
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
