package com.zerotoship.z2term.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.emulator.SgrAttribute
import com.zerotoship.z2term.emulator.TerminalColors
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.emulator.TerminalRow

/**
 * TerminalEmulator のバッファを Compose Canvas で描画する。
 *
 * - `BoxWithConstraints` で利用可能領域を取得
 * - `TextMeasurer` で 1 文字 (FullWidth ASCII の "M") の幅・高さを測り、cols/rows を逆算
 * - rows/cols が変わったら `emulator.resize()` + `onSizeChanged` を発火
 * - 同じ属性が並ぶセルをまとめて 1 回の `drawText` で描画 (最適化)
 * - `redrawTrigger` を読むことで recomposition を強制
 * - `scrollOffset` で表示開始行をずらす (スクロールバック閲覧)
 */
@Composable
fun TerminalRenderer(
    emulator: TerminalEmulator,
    fontSize: TextUnit = 13.sp,
    fontFamily: FontFamily = FontFamily.Monospace,
    modifier: Modifier = Modifier,
    onSizeChanged: (rows: Int, cols: Int) -> Unit = { _, _ -> },
    redrawTrigger: Int = 0,
    scrollOffset: Int = 0
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val baseStyle = remember(fontSize, fontFamily) {
        TextStyle(fontFamily = fontFamily, fontSize = fontSize)
    }
    val charSize = remember(baseStyle) {
        // モノスペースなので "M" の幅で代用 (CJK は別途半角扱い、M2 範囲)
        val r = textMeasurer.measure("M", baseStyle)
        Size(r.size.width.toFloat(), r.size.height.toFloat())
    }

    val sizeCallback by rememberUpdatedState(onSizeChanged)

    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val cols = (widthPx / charSize.width).toInt().coerceAtLeast(1)
        val rows = (heightPx / charSize.height).toInt().coerceAtLeast(1)

        LaunchedEffect(rows, cols) {
            emulator.resize(rows, cols)
            sizeCallback(rows, cols)
        }

        // redrawTrigger を Canvas のキャプチャに巻き込み、recomposition を発生させる
        @Suppress("UNUSED_EXPRESSION") redrawTrigger

        Canvas(Modifier.fillMaxSize()) {
            drawTerminal(
                emulator = emulator,
                charSize = charSize,
                textMeasurer = textMeasurer,
                baseStyle = baseStyle,
                scrollOffset = scrollOffset
            )
        }
    }
}

private fun DrawScope.drawTerminal(
    emulator: TerminalEmulator,
    charSize: Size,
    textMeasurer: TextMeasurer,
    baseStyle: TextStyle,
    scrollOffset: Int
) {
    val colors = emulator.colors
    val buffer = emulator.buffer

    // 背景全塗り
    drawRect(color = argbToColor(colors.defaultBackground), size = size)

    val viewRows = buffer.rows
    val viewCols = buffer.columns

    // スクロールオフセットの正規化:
    //   0 = 通常 (スクリーン表示)
    //   N > 0 = N 行スクロールバックを表示
    val maxOffset = buffer.scrollbackSize
    val offset = scrollOffset.coerceIn(0, maxOffset)
    val startRowIndex = buffer.scrollbackSize - offset  // getRow に渡す開始 index

    for (rowOnScreen in 0 until viewRows) {
        val absoluteIndex = startRowIndex + rowOnScreen
        if (absoluteIndex < 0 || absoluteIndex >= buffer.totalRows) continue
        val row = buffer.getRow(absoluteIndex)
        drawRow(
            row = row,
            screenRow = rowOnScreen,
            cols = viewCols,
            charSize = charSize,
            colors = colors,
            textMeasurer = textMeasurer,
            baseStyle = baseStyle
        )
    }

    // カーソル描画 (スクロールバック閲覧中は非表示)
    if (offset == 0 && emulator.cursorVisible) {
        val cx = emulator.cursorCol * charSize.width
        val cy = emulator.cursorRow * charSize.height
        drawRect(
            color = argbToColor(colors.cursorColor).copy(alpha = 0.6f),
            topLeft = Offset(cx, cy),
            size = charSize,
            style = Stroke(width = 2f)
        )
    }
}

/**
 * 1 行を描画。同じ fg/bg/flags が連続するセルを 1 つの span にまとめて drawText する。
 * 空白だけ続く span は文字描画をスキップ (背景塗りは行う)。
 */
private fun DrawScope.drawRow(
    row: TerminalRow,
    screenRow: Int,
    cols: Int,
    charSize: Size,
    colors: TerminalColors,
    textMeasurer: TextMeasurer,
    baseStyle: TextStyle
) {
    val y = screenRow * charSize.height
    val rowCols = row.columns.coerceAtMost(cols)

    var col = 0
    while (col < rowCols) {
        val startCell = row.getCell(col)
        val startFg = startCell.fgAttr
        val startBg = startCell.bgAttr

        // 同じ属性が続く範囲を探索
        var end = col + 1
        while (end < rowCols) {
            val c = row.getCell(end)
            if (c.fgAttr != startFg || c.bgAttr != startBg) break
            end++
        }

        val inverse = SgrAttribute.hasFlag(startFg, SgrAttribute.FLAG_INVERSE)
        val fgArgb = resolveColor(startFg, colors, isFg = true)
        val bgArgb = resolveColor(startBg, colors, isFg = false)
        val effectiveFg = if (inverse) bgArgb else fgArgb
        val effectiveBg = if (inverse) fgArgb else bgArgb

        val x = col * charSize.width
        val spanWidth = (end - col) * charSize.width

        // 背景: デフォルト背景以外なら塗る (デフォルトは既に塗り済み)
        if (effectiveBg != colors.defaultBackground || inverse) {
            drawRect(
                color = argbToColor(effectiveBg),
                topLeft = Offset(x, y),
                size = Size(spanWidth, charSize.height)
            )
        }

        // 文字 (全部空白ならスキップ)
        val text = extractText(row, col, end)
        if (text.isNotBlank()) {
            val style = baseStyle.copy(
                color = argbToColor(effectiveFg),
                fontWeight = if (SgrAttribute.hasFlag(startFg, SgrAttribute.FLAG_BOLD))
                    FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (SgrAttribute.hasFlag(startFg, SgrAttribute.FLAG_ITALIC))
                    FontStyle.Italic else FontStyle.Normal
            )
            val layout = textMeasurer.measure(text, style)
            drawText(layout, topLeft = Offset(x, y))
        }

        // 下線
        if (SgrAttribute.hasFlag(startFg, SgrAttribute.FLAG_UNDERLINE)) {
            val uy = y + charSize.height - 1f
            drawLine(
                color = argbToColor(effectiveFg),
                start = Offset(x, uy),
                end = Offset(x + spanWidth, uy),
                strokeWidth = 1f
            )
        }

        // 取り消し線
        if (SgrAttribute.hasFlag(startFg, SgrAttribute.FLAG_STRIKE)) {
            val sy = y + charSize.height / 2f
            drawLine(
                color = argbToColor(effectiveFg),
                start = Offset(x, sy),
                end = Offset(x + spanWidth, sy),
                strokeWidth = 1f
            )
        }

        col = end
    }

    row.dirty = false
}

private fun extractText(
    row: TerminalRow,
    from: Int,
    toExclusive: Int
): String = buildString(toExclusive - from) {
    for (i in from until toExclusive) append(row.getCell(i).char)
}

private fun resolveColor(attr: Int, colors: TerminalColors, isFg: Boolean): Int = when {
    SgrAttribute.isDefault(attr) ->
        if (isFg) colors.defaultForeground else colors.defaultBackground
    SgrAttribute.isIndexed(attr) -> colors.getColor(SgrAttribute.getIndex(attr))
    SgrAttribute.isRgb(attr) ->
        (0xFF shl 24) or
            (SgrAttribute.getR(attr) shl 16) or
            (SgrAttribute.getG(attr) shl 8) or
            SgrAttribute.getB(attr)
    else -> if (isFg) colors.defaultForeground else colors.defaultBackground
}

private fun argbToColor(argb: Int): Color = Color(argb.toLong() or 0xFF000000)
