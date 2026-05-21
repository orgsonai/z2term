package com.zerotoship.z2term.ui.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.core.CellMetrics
import com.zerotoship.z2term.core.TerminalSelection
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.emulator.SgrAttribute
import com.zerotoship.z2term.emulator.TerminalColors
import com.zerotoship.z2term.ui.theme.TerminalFontOptions

/**
 * エミュレータバッファをネイティブ Canvas に描く。
 *
 * 設計ポイント:
 * - **セル単位 drawText**: 同 SGR の文字列をまとめて drawText すると、フォントの
 *   実 advance と cellW (= measureText("M")) のサブピクセル誤差が累積し、
 *   行末に向かってカーソルとの間が広がる症状になる。各セルを `c * cellW` で
 *   個別 drawText することで厳密にグリッドへ吸着させる。
 * - **CellMetrics 公開**: InputView がピクセル → セル変換に使えるよう
 *   `session.updateCellMetrics` で寸法を流す。
 * - **選択ハイライト**: background ペイント → selection 半透明オーバーレイ →
 *   テキスト の順で描き、選択中の文字も読める色を保つ。
 * - 全角文字は `wideCont` セルをスキップして 1 文字を 2 セル幅で描く。
 * - カーソルは前景・背景を反転 (cursorColor を背景に、defaultBackground を文字色に)。
 */
@Composable
fun TerminalRenderer(
    session: TerminalSession,
    modifier: Modifier = Modifier
) {
    val redrawTick by session.redrawTick.collectAsState()
    val scrollOffset by session.scrollOffset.collectAsState()
    val settings by session.settingsFlow.collectAsState()
    val selection by session.selection.collectAsState()

    val density = LocalDensity.current
    val context = LocalContext.current

    val fontOption = remember(settings.fontId) { TerminalFontOptions.byId(settings.fontId) }
    val typeface = remember(fontOption.id) {
        val asset = fontOption.assetFile
        if (asset != null) {
            try {
                Typeface.createFromAsset(context.assets, "fonts/$asset")
            } catch (_: Exception) {
                Typeface.MONOSPACE
            }
        } else {
            Typeface.MONOSPACE
        }
    }

    val fontSizePx = with(density) { settings.fontSizeSp.sp.toPx() }
    val textPaint = remember(typeface, fontSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = fontSizePx
            isSubpixelText = true
        }
    }
    val bgPaint = remember { Paint() }
    val underlinePaint = remember(fontSizePx) {
        Paint().apply { strokeWidth = (fontSizePx / 14f).coerceAtLeast(1f) }
    }

    val metrics = textPaint.fontMetrics
    val lineHeight = (metrics.descent - metrics.ascent + metrics.leading).coerceAtLeast(1f)
    val cellW = textPaint.measureText("M").coerceAtLeast(1f)
    val baselineOffset = -metrics.ascent

    BoxWithConstraints(modifier = modifier) {
        val canvasWPx = with(density) { maxWidth.toPx() }
        val canvasHPx = with(density) { maxHeight.toPx() }
        val cols = (canvasWPx / cellW).toInt().coerceAtLeast(1)
        val rows = (canvasHPx / lineHeight).toInt().coerceAtLeast(1)

        LaunchedEffect(rows, cols) {
            session.onResize(rows, cols)
        }
        LaunchedEffect(cellW, lineHeight, rows, cols) {
            session.updateCellMetrics(
                CellMetrics(
                    cellW = cellW,
                    lineHeight = lineHeight,
                    canvasRows = rows,
                    canvasCols = cols
                )
            )
        }

        Canvas(modifier = Modifier.matchParentSize()) {
            @Suppress("UNUSED_VARIABLE")
            val tick = redrawTick
            @Suppress("UNUSED_VARIABLE")
            val so = scrollOffset
            @Suppress("UNUSED_VARIABLE")
            val sel = selection
            drawIntoCanvas { canvas ->
                drawBuffer(
                    nativeCanvas = canvas.nativeCanvas,
                    session = session,
                    textPaint = textPaint,
                    bgPaint = bgPaint,
                    underlinePaint = underlinePaint,
                    cellW = cellW,
                    lineHeight = lineHeight,
                    baselineOffset = baselineOffset,
                    canvasRows = rows,
                    canvasCols = cols,
                    scrollOffset = scrollOffset,
                    selection = selection
                )
            }
        }
    }
}

private const val SELECTION_OVERLAY_ARGB: Int = 0x6622C55E.toInt() // ZtsGreen translucent
private const val HANDLE_FILL_ARGB: Int = 0xFF22C55E.toInt()
private const val HANDLE_BORDER_ARGB: Int = 0xFF0A0A0A.toInt()

private fun drawBuffer(
    nativeCanvas: android.graphics.Canvas,
    session: TerminalSession,
    textPaint: Paint,
    bgPaint: Paint,
    underlinePaint: Paint,
    cellW: Float,
    lineHeight: Float,
    baselineOffset: Float,
    canvasRows: Int,
    canvasCols: Int,
    scrollOffset: Int,
    selection: TerminalSelection?
) {
    val emu = session.emulator
    val buf = emu.buffer
    val colors = emu.colors

    // 全面を default 背景でクリア
    bgPaint.color = colors.defaultBackground
    nativeCanvas.drawRect(
        0f, 0f,
        cellW * canvasCols, lineHeight * canvasRows,
        bgPaint
    )

    val cursorAbsRow = buf.scrollbackSize + emu.cursorRow
    val bottomAbsRow = if (scrollOffset == 0) {
        cursorAbsRow.coerceAtLeast(buf.scrollbackSize + canvasRows - 1)
    } else {
        buf.scrollbackSize + buf.rows - 1 - scrollOffset
    }
    val topAbsRow = bottomAbsRow - canvasRows + 1

    val totalRows = buf.totalRows

    for (i in 0 until canvasRows) {
        val abs = topAbsRow + i
        if (abs < 0 || abs >= totalRows) continue
        val row = buf.getRow(abs)
        val y = i * lineHeight
        val baseline = y + baselineOffset
        val rowCols = minOf(row.columns, canvasCols)

        // --- Pass 1: 背景 (セル毎、default 背景はスキップ) ---
        var c = 0
        while (c < rowCols) {
            val cell = row.getCell(c)
            if (cell.wideCont) { c++; continue }
            val isWide = c + 1 < rowCols && row.getCell(c + 1).wideCont
            val span = if (isWide) 2 else 1
            val flags = cell.fgAttr and SgrAttribute.FLAG_INVERSE
            val inverse = flags != 0
            val fgArgb = resolveColor(colors, cell.fgAttr, isFg = true)
            val bgArgb = resolveColor(colors, cell.bgAttr, isFg = false)
            val drawBg = if (inverse) fgArgb else bgArgb
            if (drawBg != colors.defaultBackground) {
                bgPaint.color = drawBg
                nativeCanvas.drawRect(c * cellW, y, (c + span) * cellW, y + lineHeight, bgPaint)
            }
            c += span
        }

        // --- Pass 2: 選択ハイライト (半透明) ---
        if (selection != null && selection.contains(abs)) {
            val (from, to) = selection.colRangeFor(abs, rowCols)
            if (from < to) {
                bgPaint.color = SELECTION_OVERLAY_ARGB
                nativeCanvas.drawRect(from * cellW, y, to * cellW, y + lineHeight, bgPaint)
            }
        }

        // --- Pass 3: 文字 + 下線/取り消し線 (セル単位 drawText でグリッド吸着) ---
        c = 0
        while (c < rowCols) {
            val cell = row.getCell(c)
            if (cell.wideCont) { c++; continue }
            val isWide = c + 1 < rowCols && row.getCell(c + 1).wideCont
            val span = if (isWide) 2 else 1
            val flags = cell.fgAttr and (
                SgrAttribute.FLAG_BOLD or
                    SgrAttribute.FLAG_UNDERLINE or
                    SgrAttribute.FLAG_INVERSE or
                    SgrAttribute.FLAG_STRIKE
                )
            val inverse = (flags and SgrAttribute.FLAG_INVERSE) != 0
            val fgArgb = resolveColor(colors, cell.fgAttr, isFg = true)
            val bgArgb = resolveColor(colors, cell.bgAttr, isFg = false)
            val drawFg = if (inverse) bgArgb else fgArgb

            if (cell.char != ' ') {
                textPaint.color = drawFg
                textPaint.isFakeBoldText = (flags and SgrAttribute.FLAG_BOLD) != 0
                nativeCanvas.drawText(cell.char.toString(), c * cellW, baseline, textPaint)
            }
            if ((flags and SgrAttribute.FLAG_UNDERLINE) != 0) {
                underlinePaint.color = drawFg
                val uy = y + lineHeight - underlinePaint.strokeWidth
                nativeCanvas.drawLine(c * cellW, uy, (c + span) * cellW, uy, underlinePaint)
            }
            if ((flags and SgrAttribute.FLAG_STRIKE) != 0) {
                underlinePaint.color = drawFg
                val sy = y + lineHeight * 0.55f
                nativeCanvas.drawLine(c * cellW, sy, (c + span) * cellW, sy, underlinePaint)
            }
            c += span
        }
    }

    // --- カーソル (選択中は描かない: 選択時はカーソル要らない、選択 UI が主) ---
    if (emu.cursorVisible && selection == null) {
        val absCursorRow = buf.scrollbackSize + emu.cursorRow
        val canvasRow = absCursorRow - topAbsRow
        if (canvasRow in 0 until canvasRows && emu.cursorCol in 0 until canvasCols) {
            val y = canvasRow * lineHeight
            val cx = emu.cursorCol * cellW
            val row = buf.getRow(absCursorRow)
            val cellW2 = run {
                val isWide = emu.cursorCol + 1 < row.columns && row.getCell(emu.cursorCol + 1).wideCont
                if (isWide) cellW * 2f else cellW
            }
            bgPaint.color = colors.cursorColor
            nativeCanvas.drawRect(cx, y, cx + cellW2, y + lineHeight, bgPaint)
            if (emu.cursorCol < row.columns) {
                val cell = row.getCell(emu.cursorCol)
                if (cell.char != ' ' || cell.wideCont) {
                    textPaint.color = colors.defaultBackground
                    textPaint.isFakeBoldText = false
                    nativeCanvas.drawText(
                        cell.char.toString(),
                        cx, y + baselineOffset, textPaint
                    )
                }
            }
        }
    }

    // --- 選択ハンドル (start, end の cell 角に小さな円を描く) ---
    if (selection != null) {
        val handleRadius = lineHeight * 0.5f
        val borderWidth = (lineHeight * 0.07f).coerceAtLeast(1.5f)
        val startCanvasRow = selection.startAbsRow - topAbsRow
        if (startCanvasRow in 0 until canvasRows) {
            val sx = selection.startCol * cellW
            val sy = startCanvasRow * lineHeight + lineHeight
            drawHandle(nativeCanvas, bgPaint, sx, sy, handleRadius, borderWidth)
        }
        val endCanvasRow = selection.endAbsRow - topAbsRow
        if (endCanvasRow in 0 until canvasRows) {
            val ex = (selection.endCol + 1) * cellW
            val ey = endCanvasRow * lineHeight + lineHeight
            drawHandle(nativeCanvas, bgPaint, ex, ey, handleRadius, borderWidth)
        }
    }
}

private fun drawHandle(
    canvas: android.graphics.Canvas,
    paint: Paint,
    cx: Float,
    cy: Float,
    radius: Float,
    borderWidth: Float
) {
    paint.style = Paint.Style.FILL
    paint.color = HANDLE_FILL_ARGB
    canvas.drawCircle(cx, cy, radius, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = borderWidth
    paint.color = HANDLE_BORDER_ARGB
    canvas.drawCircle(cx, cy, radius, paint)
    paint.style = Paint.Style.FILL
}

private fun resolveColor(colors: TerminalColors, attr: Int, isFg: Boolean): Int {
    return when {
        SgrAttribute.isDefault(attr) ->
            if (isFg) colors.defaultForeground else colors.defaultBackground
        SgrAttribute.isIndexed(attr) ->
            colors.getColor(SgrAttribute.getIndex(attr))
        SgrAttribute.isRgb(attr) ->
            0xFF000000.toInt() or (attr and SgrAttribute.COLOR_MASK)
        else ->
            if (isFg) colors.defaultForeground else colors.defaultBackground
    }
}
