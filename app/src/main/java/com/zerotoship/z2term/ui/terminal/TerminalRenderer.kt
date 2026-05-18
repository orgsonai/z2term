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
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.emulator.SgrAttribute
import com.zerotoship.z2term.emulator.TerminalColors
import com.zerotoship.z2term.ui.theme.TerminalFontOptions

/**
 * エミュレータバッファをネイティブ Canvas に描く。
 *
 * 設計:
 * - `BoxWithConstraints` で利用領域を測り、フォントメトリクスから rows/cols を逆算して
 *   `session.onResize(rows, cols)` を発火。
 * - 描画は「絶対行 (scrollback + 画面) インデックス」で行うことで、emulator.resize 非同期
 *   反映中でも「最新行が常に canvas 下端」になる (handoff §D 参照)。
 * - 同 SGR 属性が続くセルは 1 回の `drawText` にまとめ、塗りつぶしは矩形 1 つで処理。
 * - 全角文字は `wideCont` セルをスキップして 1 文字を 2 セル幅で描く。
 * - カーソルは前景・背景を反転 (cursorColor を背景に、defaultBackground を文字色に)。
 * - ハイパーリンク (`cell.link`) はアンダーラインで視覚化 (Phase 1 は描画のみ、tap は後続)。
 *
 * 入力イベントは取らない。タップは親 (TerminalScreen) で IME 起動に変換する。
 */
@Composable
fun TerminalRenderer(
    session: TerminalSession,
    modifier: Modifier = Modifier
) {
    val redrawTick by session.redrawTick.collectAsState()
    val scrollOffset by session.scrollOffset.collectAsState()
    val settings by session.settingsFlow.collectAsState()

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

        Canvas(modifier = Modifier.matchParentSize()) {
            // closure が redrawTick を読むことで recomposition → draw 再実行
            @Suppress("UNUSED_VARIABLE")
            val tick = redrawTick
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
                    scrollOffset = scrollOffset
                )
            }
        }
    }
}

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
    scrollOffset: Int
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

    // 描画範囲の計算 (絶対行 = scrollback + screen の連結インデックス)。
    //
    // - 手動スクロール中 (scrollOffset > 0): ユーザーが固定した視点を保つ。
    //   bottom はバッファ最下行から scrollOffset 行ぶん上がった位置。
    // - 張り付き mode (scrollOffset == 0): カーソルを必ず canvas 内に収めるよう
    //   bottom はカーソル行と「最初に画面が埋まる行 (canvasRows-1)」のうち大きい方。
    //   これにより fresh shell (cursorRow=0, buf.rows=51, canvasRows=25 等) でも
    //   プロンプトが top に表示され、シェルが output で進むに従い canvas 下端に張り付く。
    val cursorAbsRow = buf.scrollbackSize + emu.cursorRow
    val bottomAbsRow = if (scrollOffset == 0) {
        cursorAbsRow.coerceAtLeast(buf.scrollbackSize + canvasRows - 1)
    } else {
        buf.scrollbackSize + buf.rows - 1 - scrollOffset
    }
    val topAbsRow = bottomAbsRow - canvasRows + 1

    val totalRows = buf.totalRows
    val sb = StringBuilder(canvasCols)

    for (i in 0 until canvasRows) {
        val abs = topAbsRow + i
        if (abs < 0 || abs >= totalRows) continue
        val row = buf.getRow(abs)
        val y = i * lineHeight
        val baseline = y + baselineOffset
        val rowCols = minOf(row.columns, canvasCols)

        var c = 0
        while (c < rowCols) {
            val cell = row.getCell(c)
            if (cell.wideCont) { c++; continue }

            val fg = cell.fgAttr
            val bg = cell.bgAttr
            val flags = (fg and (SgrAttribute.FLAG_BOLD or
                SgrAttribute.FLAG_UNDERLINE or
                SgrAttribute.FLAG_INVERSE or
                SgrAttribute.FLAG_STRIKE))
            val startCol = c

            sb.setLength(0)
            sb.append(cell.char)
            val firstWide = c + 1 < rowCols && row.getCell(c + 1).wideCont
            c += if (firstWide) 2 else 1

            // 同 SGR 連続セルを集める
            while (c < rowCols) {
                val n = row.getCell(c)
                if (n.wideCont) { c++; continue }
                val nFlags = (n.fgAttr and (SgrAttribute.FLAG_BOLD or
                    SgrAttribute.FLAG_UNDERLINE or
                    SgrAttribute.FLAG_INVERSE or
                    SgrAttribute.FLAG_STRIKE))
                if (n.fgAttr != fg || n.bgAttr != bg || nFlags != flags) break
                sb.append(n.char)
                val nextWide = c + 1 < rowCols && row.getCell(c + 1).wideCont
                c += if (nextWide) 2 else 1
            }

            val inverse = (flags and SgrAttribute.FLAG_INVERSE) != 0
            val fgArgb = resolveColor(colors, fg, isFg = true)
            val bgArgb = resolveColor(colors, bg, isFg = false)
            val drawFg = if (inverse) bgArgb else fgArgb
            val drawBg = if (inverse) fgArgb else bgArgb

            if (drawBg != colors.defaultBackground) {
                bgPaint.color = drawBg
                nativeCanvas.drawRect(
                    startCol * cellW, y,
                    c * cellW, y + lineHeight,
                    bgPaint
                )
            }

            textPaint.color = drawFg
            textPaint.isFakeBoldText = (flags and SgrAttribute.FLAG_BOLD) != 0
            nativeCanvas.drawText(sb, 0, sb.length, startCol * cellW, baseline, textPaint)

            if ((flags and SgrAttribute.FLAG_UNDERLINE) != 0) {
                underlinePaint.color = drawFg
                val uy = y + lineHeight - underlinePaint.strokeWidth
                nativeCanvas.drawLine(startCol * cellW, uy, c * cellW, uy, underlinePaint)
            }
            if ((flags and SgrAttribute.FLAG_STRIKE) != 0) {
                underlinePaint.color = drawFg
                val sy = y + lineHeight * 0.55f
                nativeCanvas.drawLine(startCol * cellW, sy, c * cellW, sy, underlinePaint)
            }
        }
    }

    // カーソル (Primary/Alt 共に絶対行で表現)
    if (emu.cursorVisible) {
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
