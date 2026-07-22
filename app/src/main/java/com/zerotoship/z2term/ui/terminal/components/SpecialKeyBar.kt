package com.zerotoship.z2term.ui.terminal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.ui.terminal.keyboard.detectTapWithRepeat
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary

/**
 * 画面下端の特殊キーバー (Phase 1 最小構成)。
 *
 * 含むキー:
 *  - ESC / TAB
 *  - CTRL (sticky toggle)
 *  - 矢印 4 つ
 *  - Enter
 *  - Ctrl+C / Ctrl+D / Ctrl+L (頻出ショートカット)
 *
 * 横スクロール対応。F1-F12 / Home/End/PgUp/PgDn / 折り畳み式は後続フェーズ。
 */
@Composable
fun SpecialKeyBar(
    session: TerminalSession,
    ctrlSticky: Boolean,
    onCtrlToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emulator = session.emulator
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Key("ESC") { session.writeBytes(byteArrayOf(0x1B)) }
        Key("TAB") { session.writeBytes(byteArrayOf(0x09)) }
        Key("CTRL", active = ctrlSticky, onClick = onCtrlToggle)
        Key("←") { session.writeBytes(emulator.cursorKeyBytes(TerminalEmulator.CursorKey.LEFT)) }
        Key("↓") { session.writeBytes(emulator.cursorKeyBytes(TerminalEmulator.CursorKey.DOWN)) }
        Key("↑") { session.writeBytes(emulator.cursorKeyBytes(TerminalEmulator.CursorKey.UP)) }
        Key("→") { session.writeBytes(emulator.cursorKeyBytes(TerminalEmulator.CursorKey.RIGHT)) }
        // ⏎ は長押しで連打できる (内蔵キーボードの ⏎ と揃える・要望)。
        Key("⏎", repeatable = true) { session.writeBytes(byteArrayOf(0x0D)) }
        Key("^C") { session.writeBytes(byteArrayOf(0x03)) }
        Key("^D") { session.writeBytes(byteArrayOf(0x04)) }
        Key("^L") { session.writeBytes(byteArrayOf(0x0C)) }
    }
}

@Composable
private fun Key(
    label: String,
    active: Boolean = false,
    repeatable: Boolean = false,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val bg = if (active || pressed) ZtsGreen else ZtsBgCard
    val fg = if (active || pressed) Color.Black else ZtsTextPrimary
    val border = if (active || pressed) ZtsGreen else ZtsBorder
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    // 連打キーは内蔵キーボードと同じジェスチャ (長押しで一定間隔リピート) を使う。
    val tapModifier = if (repeatable) {
        Modifier.pointerInput(Unit) {
            detectTapWithRepeat(scope, onPressedChange = { pressed = it }) { currentOnClick() }
        }
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .then(tapModifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

