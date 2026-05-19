package com.zerotoship.z2term.ui.terminal.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.ui.terminal.input.AndroidKeyMapper
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsGreenBright
import com.zerotoship.z2term.ui.theme.ZtsGreenDim
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Z2Term 独自キーボード。
 *
 * 主な仕様:
 *  - Row 5 左下に CTRL を配置 (PC ライクなレイアウト)
 *  - Shift は OFF / ONESHOT / LOCKED の 3 状態 (タップ毎に循環)
 *  - ⌫ 長押しで連打、左フリックで `Ctrl+W` (単語削除)、右フリックで `Ctrl+U` (行頭まで削除)
 *  - 各英字キー: スタイルに応じて 1 方向 (compact) or 4 方向 (spacious) フリック
 *
 * レイアウト:
 *   Row 1: ESC  1〜0 (or 記号)                              ⌫(長押し連打 / ←=C-W / →=C-U)
 *   Row 2: TAB  q w e r t y u i o p
 *   Row 3: ⌨   a s d f g h j k l                            ⏎
 *   Row 4: ⇧   z x c v b n m , . /
 *   Row 5: CTL  ?#  ALT  SPACE                              ← ↓ ↑ →
 */
@Composable
fun TerminalKeyboard(
    onBytes: (ByteArray) -> Unit,
    onCursorKey: (TerminalEmulator.CursorKey) -> Unit,
    onRequestSystemKeyboard: () -> Unit,
    style: KeyboardStyle = KeyboardStyle.COMPACT,
    modifier: Modifier = Modifier
) {
    var shift by remember { mutableStateOf(ShiftState.OFF) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var sym by remember { mutableStateOf(false) }

    fun cycleShift() {
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.ONESHOT
            ShiftState.ONESHOT -> ShiftState.LOCKED
            ShiftState.LOCKED -> ShiftState.OFF
        }
    }

    fun resetOneShotMods() {
        if (shift == ShiftState.ONESHOT) shift = ShiftState.OFF
        if (ctrl) ctrl = false
        if (alt) alt = false
    }

    fun emitChar(raw: Char) {
        val shifted = shift != ShiftState.OFF
        val effective = if (!sym && shifted && raw.isLetter()) raw.uppercaseChar() else raw
        val base = effective.toString().toByteArray(Charsets.UTF_8)
        val withCtrl = if (ctrl) {
            AndroidKeyMapper.controlByteFor(effective)?.let { byteArrayOf(it) } ?: base
        } else base
        val final = if (alt) byteArrayOf(0x1B) + withCtrl else withCtrl
        onBytes(final)
        resetOneShotMods()
    }

    fun emitFlick(raw: Char) {
        val base = raw.toString().toByteArray(Charsets.UTF_8)
        val final = if (alt) byteArrayOf(0x1B) + base else base
        onBytes(final)
        if (alt) alt = false
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

    val r1Labels = if (sym) listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")")
                   else listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val r2Labels = if (sym) listOf("-", "_", "+", "=", "/", "\\", "[", "]", "{", "}")
                   else listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val r3Labels = if (sym) listOf("`", "~", "'", "\"", ";", ":", "<", ">", "|")
                   else listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val r4Labels = if (sym) listOf("?", "§", "°", "¥", "€", "£", "~", "…")
                   else listOf("z", "x", "c", "v", "b", "n", "m", ",", ".", "/")

    // 上フリック (1 方向用) — 各行 10 個
    val r2FlickUp = listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')')
    val r3FlickUp = listOf('-', '_', '+', '=', '|', '\\', '/', '[', ']')
    val r4FlickUp = listOf('`', '~', '\'', '"', '<', '>', '?', ':', ';', '{')

    // 4 方向フリック (spacious 用) — Row 2 のみ 4 方向、Row 3/4 は up のみ
    val r2Flick4 = listOf(
        FlickMap(up = '!', down = '1', left = '`', right = '~'),
        FlickMap(up = '@', down = '2', left = '\'', right = '"'),
        FlickMap(up = '#', down = '3', left = '(', right = ')'),
        FlickMap(up = '$', down = '4', left = '[', right = ']'),
        FlickMap(up = '%', down = '5', left = '{', right = '}'),
        FlickMap(up = '^', down = '6', left = '<', right = '>'),
        FlickMap(up = '&', down = '7', left = ':', right = ';'),
        FlickMap(up = '*', down = '8', left = ',', right = '.'),
        FlickMap(up = '(', down = '9', left = '/', right = '\\'),
        FlickMap(up = ')', down = '0', left = '|', right = '?')
    )

    fun flickFor(rowIdx: Int, colIdx: Int): FlickMap? {
        if (sym) return null
        return when (rowIdx) {
            2 -> if (style.fourDirectionFlick) r2Flick4.getOrNull(colIdx)
                 else r2FlickUp.getOrNull(colIdx)?.let { FlickMap(up = it) }
            3 -> r3FlickUp.getOrNull(colIdx)?.let { FlickMap(up = it) }
            4 -> r4FlickUp.getOrNull(colIdx)?.let { FlickMap(up = it) }
            else -> null
        }
    }

    val rowSpacing = if (style.keyHeight >= 56.dp) 4.dp else 3.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        // Row 1: ESC + 数字行 + ⌫
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            BasicKey("ESC", weight = 1.4f, fontSp = (style.keyFontSp - 3f).coerceAtLeast(10f), style = style) {
                emitSpecial(byteArrayOf(0x1B))
            }
            r1Labels.forEach { s ->
                BasicKey(s, weight = 1f, fontSp = style.keyFontSp, style = style) { emitChar(s[0]) }
            }
            BackspaceKey(
                weight = 1.4f,
                style = style,
                onTap = { emitSpecial(byteArrayOf(0x7F)) },
                onFlickLeft = { emitSpecial(byteArrayOf(0x17)) },  // Ctrl+W: 単語削除
                onFlickRight = { emitSpecial(byteArrayOf(0x15)) }  // Ctrl+U: 行頭まで削除
            )
        }
        // Row 2
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            BasicKey("TAB", weight = 1.4f, fontSp = (style.keyFontSp - 3f).coerceAtLeast(10f), style = style) {
                emitSpecial(byteArrayOf(0x09))
            }
            r2Labels.forEachIndexed { idx, s ->
                val display = if (!sym && shift != ShiftState.OFF && s[0].isLetter()) s.uppercase() else s
                FlickKey(
                    label = display,
                    flick = flickFor(2, idx),
                    weight = 1f,
                    style = style,
                    onTap = { emitChar(s[0]) },
                    onFlick = { ch -> emitFlick(ch) }
                )
            }
        }
        // Row 3 (旧 CTRL の位置を ⌨ に変更)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            BasicKey("⌨", weight = 1.4f, fontSp = style.keyFontSp, style = style, onClick = onRequestSystemKeyboard)
            r3Labels.forEachIndexed { idx, s ->
                val display = if (!sym && shift != ShiftState.OFF && s[0].isLetter()) s.uppercase() else s
                FlickKey(
                    label = display,
                    flick = flickFor(3, idx),
                    weight = 1f,
                    style = style,
                    onTap = { emitChar(s[0]) },
                    onFlick = { ch -> emitFlick(ch) }
                )
            }
            BasicKey("⏎", weight = 1.4f, fontSp = style.keyFontSp, style = style) {
                emitSpecial(byteArrayOf(0x0D))
            }
        }
        // Row 4
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            ShiftKey(weight = 1.4f, state = shift, style = style, onCycle = { cycleShift() })
            r4Labels.forEachIndexed { idx, s ->
                val display = if (!sym && shift != ShiftState.OFF && s[0].isLetter()) s.uppercase() else s
                FlickKey(
                    label = display,
                    flick = flickFor(4, idx),
                    weight = 1f,
                    style = style,
                    onTap = { emitChar(s[0]) },
                    onFlick = { ch -> emitFlick(ch) }
                )
            }
        }
        // Row 5: 左下を CTRL に
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            BasicKey(
                label = "CTRL",
                weight = 1.4f,
                fontSp = (style.keyFontSp - 3f).coerceAtLeast(10f),
                active = ctrl,
                style = style
            ) { ctrl = !ctrl }
            BasicKey(
                label = if (sym) "ABC" else "?#",
                weight = 1.2f,
                fontSp = (style.keyFontSp - 3f).coerceAtLeast(10f),
                active = sym,
                style = style
            ) { sym = !sym }
            BasicKey(
                label = "ALT",
                weight = 1.2f,
                fontSp = (style.keyFontSp - 3f).coerceAtLeast(10f),
                active = alt,
                style = style
            ) { alt = !alt }
            SpaceKey(weight = 4f, style = style) { emitChar(' ') }
            BasicKey("←", weight = 1f, fontSp = style.keyFontSp, style = style) { emitCursor(TerminalEmulator.CursorKey.LEFT) }
            BasicKey("↓", weight = 1f, fontSp = style.keyFontSp, style = style) { emitCursor(TerminalEmulator.CursorKey.DOWN) }
            BasicKey("↑", weight = 1f, fontSp = style.keyFontSp, style = style) { emitCursor(TerminalEmulator.CursorKey.UP) }
            BasicKey("→", weight = 1f, fontSp = style.keyFontSp, style = style) { emitCursor(TerminalEmulator.CursorKey.RIGHT) }
        }
    }
}

@Composable
private fun RowScope.BasicKey(
    label: String,
    weight: Float,
    fontSp: Float,
    active: Boolean = false,
    style: KeyboardStyle,
    onClick: () -> Unit
) {
    val bg = if (active) ZtsGreen else ZtsBgCard
    val fg = if (active) Color.Black else ZtsTextPrimary
    val border = if (active) ZtsGreen else ZtsBorder
    Box(
        modifier = Modifier
            .weight(weight)
            .height(style.keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = fontSp.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * ⇧ キー: OFF / ONESHOT / LOCKED の 3 状態を視覚化。
 *  - OFF: 通常配色
 *  - ONESHOT: 緑背景 (1 回大文字 → 自動 OFF)
 *  - LOCKED: 緑暗色 + 「⇪」アイコン (連続大文字、再タップで OFF)
 */
@Composable
private fun RowScope.ShiftKey(
    weight: Float,
    state: ShiftState,
    style: KeyboardStyle,
    onCycle: () -> Unit
) {
    val bg: Color
    val fg: Color
    val border: Color
    val label: String
    when (state) {
        ShiftState.OFF -> { bg = ZtsBgCard; fg = ZtsTextPrimary; border = ZtsBorder; label = "⇧" }
        ShiftState.ONESHOT -> { bg = ZtsGreen; fg = Color.Black; border = ZtsGreen; label = "⇧" }
        ShiftState.LOCKED -> { bg = ZtsGreenDim; fg = Color.Black; border = ZtsGreen; label = "⇪" }
    }
    Box(
        modifier = Modifier
            .weight(weight)
            .height(style.keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onCycle),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = style.keyFontSp.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 4 方向フリック対応キー (compact 時は up のみ、spacious 時は up/down/left/right)。
 *
 * 視覚レイアウト:
 *  - 主文字は Column 内で中央配置。上下フリックが定義されているときは
 *    Column の上/下端にヒントを並べて **主文字と重ならない** ようにする。
 *  - 左右フリックは Box overlay で中央左端/中央右端に置く (Column と直交)。
 *  - ヒント色は `ZtsGreenBright` で主文字 (白) と明確に区別。
 *
 * インタラクション:
 *  - 短いタップ → onTap
 *  - 上下左右どれかに touchSlop * 1.4 px 以上で離す → onFlick(対応する char)
 */
@Composable
private fun RowScope.FlickKey(
    label: String,
    flick: FlickMap?,
    weight: Float,
    style: KeyboardStyle,
    onTap: () -> Unit,
    onFlick: (Char) -> Unit
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(style.keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .pointerInput(label, flick) {
                val flickThreshold = viewConfiguration.touchSlop * 1.4f
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var resolved = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (!resolved && flick != null && (abs(dx) > flickThreshold || abs(dy) > flickThreshold)) {
                                val ch = if (abs(dx) > abs(dy)) {
                                    if (dx < 0) flick.left else flick.right
                                } else {
                                    if (dy < 0) flick.up else flick.down
                                }
                                if (ch != null) {
                                    resolved = true
                                    onFlick(ch)
                                    change.consume()
                                }
                            }
                            if (!change.pressed) {
                                if (!resolved) onTap()
                                break
                            }
                        }
                    }
                }
            }
    ) {
        // Column で 上/主/下 を縦に並べる:
        //   - up hint: 自然サイズ (wrap)
        //   - main: weight(1f) で残りを取得し中央寄せ → 主文字は必ず可視
        //   - down hint: 自然サイズ (wrap)
        // こうすると Compose 既定の line-leading 由来のはみ出しが起きない。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (flick?.up != null) {
                HintText(flick.up.toString(), style)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = ZtsTextPrimary,
                    fontSize = style.keyFontSp.sp,
                    lineHeight = style.keyFontSp.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (flick?.down != null) {
                HintText(flick.down.toString(), style)
            }
        }
        // 左右ヒントは Box overlay (Column と独立)
        if (flick?.left != null) {
            HintText(
                flick.left.toString(),
                style,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp)
            )
        }
        if (flick?.right != null) {
            HintText(
                flick.right.toString(),
                style,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp)
            )
        }
    }
}

@Composable
private fun HintText(text: String, style: KeyboardStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = ZtsGreenBright,
        fontSize = style.flickHintFontSp.sp,
        lineHeight = style.flickHintFontSp.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}

/**
 * ⌫ 専用キー: タップ単発、長押し連打 (500ms 後から 60ms 間隔)、
 * 左フリックで onFlickLeft (Ctrl+W)、右フリックで onFlickRight (Ctrl+U)。
 */
@Composable
private fun RowScope.BackspaceKey(
    weight: Float,
    style: KeyboardStyle,
    onTap: () -> Unit,
    onFlickLeft: () -> Unit,
    onFlickRight: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .weight(weight)
            .height(style.keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                val flickThreshold = viewConfiguration.touchSlop * 1.4f
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var resolved = false       // フリック発火済み
                        var repeatStarted = false  // 長押し連打開始
                        var repeatJob: Job? = null

                        repeatJob = scope.launch {
                            delay(500)
                            if (!resolved) {
                                repeatStarted = true
                                onTap()
                                while (isActive) {
                                    delay(60)
                                    onTap()
                                }
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (!resolved && !repeatStarted &&
                                abs(dx) > flickThreshold && abs(dx) > abs(dy)
                            ) {
                                resolved = true
                                repeatJob.cancel()
                                if (dx < 0) onFlickLeft() else onFlickRight()
                                change.consume()
                            }
                            if (!change.pressed) {
                                repeatJob.cancel()
                                if (!resolved && !repeatStarted) onTap()
                                break
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // BS は ⌫ のみを表示。左右フリック (Ctrl+W / Ctrl+U) は隠し機能として
        // ヒント表示せずに残す。
        Text(
            text = "⌫",
            color = ZtsTextPrimary,
            fontSize = style.keyFontSp.sp,
            lineHeight = style.keyFontSp.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun RowScope.SpaceKey(weight: Float, style: KeyboardStyle, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(style.keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "space",
            color = ZtsTextSecondary,
            fontSize = (style.keyFontSp - 3f).coerceAtLeast(10f).sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
