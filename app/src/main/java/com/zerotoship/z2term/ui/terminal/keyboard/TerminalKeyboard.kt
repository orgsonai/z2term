package com.zerotoship.z2term.ui.terminal.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.ui.terminal.input.AndroidKeyMapper
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/**
 * Z2Term 独自キーボード。
 *
 * OS の IME を使わずに ASCII + 制御キーを 100% コントロール下に置くため、
 * 5 行構成の QWERTY 風キーボードを Compose で実装する。
 *
 * 構造:
 *   Row 1: ESC  1〜0 (or 記号 ! 〜 ))                            ⌫
 *   Row 2: TAB  q w e r t y u i o p (or - _ + = / \ [ ] { })
 *   Row 3: CTL  a s d f g h j k l    (or 記号)            ENTER
 *   Row 4: ⇧   z x c v b n m , . /  (or 記号)
 *   Row 5: ?#  ALT  ⌨(System IME)  SPACE        ← ↓ ↑ →
 *
 * Modifier 動作:
 *   - ⇧ (Shift): 押下中は次の英字を大文字。one-shot (1 タップで自動解除)。
 *   - CTL (Ctrl): 次の英字に Ctrl 修飾を適用 (例: a → 0x01)。one-shot。
 *   - ALT: 次のキーに ESC プレフィックスを付与。one-shot。
 *   - ?# / ABC: 記号モードトグル (連続的、明示的に戻すまで持続)。
 *
 * Modifier はアクティブ中、緑背景で視覚化される。
 */
@Composable
fun TerminalKeyboard(
    onBytes: (ByteArray) -> Unit,
    onCursorKey: (TerminalEmulator.CursorKey) -> Unit,
    onRequestSystemKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shift by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var sym by remember { mutableStateOf(false) }

    fun resetOneShotMods() {
        if (shift) shift = false
        if (ctrl) ctrl = false
        if (alt) alt = false
    }

    fun emitChar(raw: Char) {
        // Shift は英字にのみ大文字化として作用。記号モード中は無視。
        val effective = if (!sym && shift && raw.isLetter()) raw.uppercaseChar() else raw
        val base = effective.toString().toByteArray(Charsets.UTF_8)
        val withCtrl = if (ctrl) {
            AndroidKeyMapper.controlByteFor(effective)?.let { byteArrayOf(it) } ?: base
        } else base
        val final = if (alt) byteArrayOf(0x1B) + withCtrl else withCtrl
        onBytes(final)
        resetOneShotMods()
    }

    fun emitSpecial(bytes: ByteArray) {
        val final = if (alt) byteArrayOf(0x1B) + bytes else bytes
        onBytes(final)
        if (alt) alt = false
    }

    fun emitCursor(key: TerminalEmulator.CursorKey) {
        onCursorKey(key)
        if (alt) alt = false
    }

    val r1 = if (sym) listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")")
             else listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val r2 = if (sym) listOf("-", "_", "+", "=", "/", "\\", "[", "]", "{", "}")
             else listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val r3 = if (sym) listOf("`", "~", "'", "\"", ";", ":", "<", ">", "|")
             else listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val r4 = if (sym) listOf("?", "§", "°", "¥", "€", "£", "~", "…")
             else listOf("z", "x", "c", "v", "b", "n", "m", ",", ".", "/")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Row 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Key("ESC", weight = 1.4f, fontSize = 11.sp) { emitSpecial(byteArrayOf(0x1B)) }
            r1.forEach { s -> Key(s, weight = 1f) { emitChar(s[0]) } }
            Key("⌫", weight = 1.4f) { emitSpecial(byteArrayOf(0x7F)) }
        }
        // Row 2
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Key("TAB", weight = 1.4f, fontSize = 11.sp) { emitSpecial(byteArrayOf(0x09)) }
            r2.forEach { s ->
                val display = if (!sym && shift && s[0].isLetter()) s.uppercase() else s
                Key(display, weight = 1f) { emitChar(s[0]) }
            }
        }
        // Row 3
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Key("CTRL", weight = 1.4f, fontSize = 11.sp, active = ctrl) { ctrl = !ctrl }
            r3.forEach { s ->
                val display = if (!sym && shift && s[0].isLetter()) s.uppercase() else s
                Key(display, weight = 1f) { emitChar(s[0]) }
            }
            Key("⏎", weight = 1.4f) { emitSpecial(byteArrayOf(0x0D)) }
        }
        // Row 4
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Key("⇧", weight = 1.4f, active = shift) { shift = !shift }
            r4.forEach { s ->
                val display = if (!sym && shift && s[0].isLetter()) s.uppercase() else s
                Key(display, weight = 1f) { emitChar(s[0]) }
            }
            Key("⌫", weight = 1.4f) { emitSpecial(byteArrayOf(0x7F)) }
        }
        // Row 5
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Key(if (sym) "ABC" else "?#", weight = 1.4f, fontSize = 11.sp, active = sym) { sym = !sym }
            Key("ALT", weight = 1.2f, fontSize = 11.sp, active = alt) { alt = !alt }
            Key("⌨", weight = 1.2f, onClick = onRequestSystemKeyboard)
            SpaceKey(weight = 4f) { emitChar(' ') }
            Key("←", weight = 1f) { emitCursor(TerminalEmulator.CursorKey.LEFT) }
            Key("↓", weight = 1f) { emitCursor(TerminalEmulator.CursorKey.DOWN) }
            Key("↑", weight = 1f) { emitCursor(TerminalEmulator.CursorKey.UP) }
            Key("→", weight = 1f) { emitCursor(TerminalEmulator.CursorKey.RIGHT) }
        }
    }
}

@Composable
private fun RowScope.Key(
    label: String,
    weight: Float = 1f,
    active: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    onClick: () -> Unit
) {
    val bg = if (active) ZtsGreen else ZtsBgCard
    val fg = if (active) Color.Black else ZtsTextPrimary
    val border = if (active) ZtsGreen else ZtsBorder
    Box(
        modifier = Modifier
            .weight(weight)
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RowScope.SpaceKey(weight: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "space",
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
