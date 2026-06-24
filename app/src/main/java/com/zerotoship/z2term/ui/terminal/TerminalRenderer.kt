package com.zerotoship.z2term.ui.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
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
import com.zerotoship.z2term.emulator.TerminalRow
import com.zerotoship.z2term.ui.terminal.input.UrlFinder
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
    composingText: String = "",
    searchMatches: List<SearchMatch> = emptyList(),
    currentMatch: SearchMatch? = null,
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
        // 左右に余白を設け、文字が画面端で見切れないようにする (選択も端までしやすく)。
        val hPadPx = H_PAD_DP * density.density
        val cols = ((canvasWPx - 2 * hPadPx) / cellW).toInt().coerceAtLeast(1)
        val rows = (canvasHPx / lineHeight).toInt().coerceAtLeast(1)

        LaunchedEffect(session.id, rows, cols) {
            // ピンチ中はフォントサイズが連続変化し rows/cols が高速に変わる。
            // 120ms 待って「最後の値」だけで resize することで、連打 resize による
            // バッファ再構築で文字が一瞬消える症状を防ぐ。
            //
            // session.id をキーに含めないと、同寸の新規タブへ切り替えたときラムダが
            // 再評価されず、新セッションの PTY が初期値 (24x80) のまま残る。すると:
            //  - canvas の rows/cols と PTY の rows/cols がズレ、画面下端に空行ぶんの
            //    隙間が出る (キーボードとの間に「末端じゃない」帯)。
            //  - PTY の cols のほうが広いと、シェルが折り返さず長行が画面外へはみ出す。
            // pinch で fontSize が変わると rows/cols のキーが変わって onResize が再走し
            // 「ピンチすると直る」現象になっていた。session.id をキーに足して、タブ切替
            // 時点で必ず再走させる ([updateCellMetrics] と同じ修正パターン)。
            delay(120)
            session.onResize(rows, cols)
        }
        // session.id をキーに含めないと、同寸の新規タブへ切り替えたとき再実行されず、
        // 新 session の cellMetrics が初期値(0)のままになる。すると pixelToAbsCell() が
        // null を返し、ピンチで resize するまで長押し選択が効かない。
        LaunchedEffect(session.id, cellW, lineHeight, rows, cols) {
            session.updateCellMetrics(
                CellMetrics(
                    cellW = cellW,
                    lineHeight = lineHeight,
                    canvasRows = rows,
                    canvasCols = cols,
                    horizontalPaddingPx = hPadPx
                )
            )
            // Kitty graphics 等で `c=N`/`r=N` 省略時に画像ピクセル数から
            // セル数を自動算出するためのヒントを emulator に伝える。
            session.emulator.setCellMetricsHint(cellW, lineHeight)
        }

        Canvas(modifier = Modifier.matchParentSize()) {
            @Suppress("UNUSED_VARIABLE")
            val tick = redrawTick
            @Suppress("UNUSED_VARIABLE")
            val so = scrollOffset
            @Suppress("UNUSED_VARIABLE")
            val sel = selection
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                // パディング部分も含め全面を背景色で塗ってから、中身を右へずらして描く。
                bgPaint.color = session.emulator.colors.defaultBackground
                nc.drawRect(0f, 0f, canvasWPx, lineHeight * rows, bgPaint)
                nc.save()
                nc.translate(hPadPx, 0f)
                drawBuffer(
                    nativeCanvas = nc,
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
                    selection = selection,
                    composingText = composingText,
                    searchMatches = searchMatches,
                    currentMatch = currentMatch
                )
                nc.restore()
            }
        }
    }
}

/**
 * セル (col) の描画文字列を返す。BMP 外 (絵文字 / CJK Ext) は高サロゲートを左セルに、
 * 低サロゲートを右セル (wideCont) に分けて持つため、結合して 1 グリフとして渡す。
 * 孤立した高サロゲートを単独で drawText すると豆腐/? になるのを防ぐ。
 */
private fun glyphAt(row: TerminalRow, col: Int): String {
    val ch = row.getCell(col).char
    if (ch.isHighSurrogate() && col + 1 < row.columns) {
        val next = row.getCell(col + 1)
        if (next.wideCont && next.char.isLowSurrogate()) return "$ch${next.char}"
    }
    return ch.toString()
}

private const val H_PAD_DP = 4f  // 端末描画の左右余白 (dp)
private const val SELECTION_OVERLAY_ARGB: Int = 0x6622C55E.toInt() // ZtsGreen translucent
private const val HANDLE_FILL_ARGB: Int = 0xFF22C55E.toInt()
private const val HANDLE_BORDER_ARGB: Int = 0xFF0A0A0A.toInt()
private const val PREEDIT_BG_ARGB: Int = 0x3322C55E // 変換中プリエディットの背景 (薄緑)
private const val SEARCH_MATCH_ARGB: Int = 0x66FFD54F.toInt()   // 検索ヒット (薄い琥珀)
private const val SEARCH_CURRENT_ARGB: Int = 0xCCFF9800.toInt() // 現在ヒット (濃いオレンジ)

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
    selection: TerminalSelection?,
    composingText: String = "",
    searchMatches: List<SearchMatch> = emptyList(),
    currentMatch: SearchMatch? = null
) {
    val emu = session.emulator
    val buf = emu.buffer
    val colors = emu.colors
    // 検索ヒットを行ごとに引けるようにしておく (可視行ループでの線形走査を避ける)。
    val matchesByRow: Map<Int, List<SearchMatch>> =
        if (searchMatches.isEmpty()) emptyMap() else searchMatches.groupBy { it.absRow }

    // 全面を default 背景でクリア
    bgPaint.color = colors.defaultBackground
    nativeCanvas.drawRect(
        0f, 0f,
        cellW * canvasCols, lineHeight * canvasRows,
        bgPaint
    )

    val cursorAbsRow = buf.scrollbackSize + emu.cursorRow
    // 下端 (張り付き = scrollOffset 0) の絶対行。表示行数 canvasRows を基準にして
    // 画面いっぱいに敷き詰める。scrollOffset 分だけここから上へずらす。
    //   旧実装は scrollOffset>0 のとき buf.rows を基準にしていたため、初回オープンで
    //   buf.rows (emulator) と canvasRows (表示) が未同期の間、scrollOffset 0→1 の瞬間に
    //   (buf.rows - canvasRows) 行ぶん下端が飛び、キーボードとの間に隙間が出ていた。
    //   両者を canvasRows に一本化し、resize 同期前でも自己整合させる (飛び/隙間を根治)。
    val bottomAtRest = cursorAbsRow.coerceAtLeast(buf.scrollbackSize + canvasRows - 1)
    val bottomAbsRow = bottomAtRest - scrollOffset
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

        // --- Pass 2.5: 検索ヒットのハイライト (現在ヒットは濃色、他は薄色) ---
        // 文字描画 (Pass 3) より前に塗るので、文字はハイライトの上に読める。
        matchesByRow[abs]?.forEach { m ->
            val from = m.colStart.coerceIn(0, canvasCols)
            val to = m.colEnd.coerceIn(0, canvasCols)
            if (from < to) {
                val isCurrent = currentMatch != null &&
                    m.absRow == currentMatch.absRow &&
                    m.colStart == currentMatch.colStart
                bgPaint.color = if (isCurrent) SEARCH_CURRENT_ARGB else SEARCH_MATCH_ARGB
                nativeCanvas.drawRect(from * cellW, y, to * cellW, y + lineHeight, bgPaint)
            }
        }

        // URL / OSC8 リンクのセル (下線でタップ可能と分かるように)。
        val linkMarks = UrlFinder.linkedColumns(buf, abs, rowCols)

        // --- Pass 2.7: 画像 (Kitty graphics 等) のうち z<0 をテキスト下層として描画 ---
        // anchor 行の images から zIndex < 0 のものだけを追加順に描画する。 これによって
        // 文字を画像の「上に」読みやすく重ねる表現 (字幕付きサムネ等) ができる。 z>=0 は
        // 後段 Pass 3.5 でテキストの上に重ねる。
        if (row.images.isNotEmpty()) {
            for (img in row.images) {
                if (img.zIndex >= 0) continue
                drawImagePlacement(nativeCanvas, img, y, cellW, lineHeight)
            }
        }
        // Kitty Unicode placeholder (U=1) のタイル描画 — z<0 のみここで。
        drawPlaceholderTiles(nativeCanvas, buf, row, y, cellW, lineHeight, rowCols, onlyNegativeZ = true)

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
                nativeCanvas.drawText(glyphAt(row, c), c * cellW, baseline, textPaint)
            }
            val isLinkCell = linkMarks != null && c < linkMarks.size && linkMarks[c]
            if ((flags and SgrAttribute.FLAG_UNDERLINE) != 0 || isLinkCell) {
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

        // --- Pass 3.5: 画像 (Kitty graphics 等) のうち z>=0 をテキスト上層として描画 ---
        // アイコン重ね・吹き出し風 placement など、文字の前面に出したいケースを表現する。
        if (row.images.isNotEmpty()) {
            for (img in row.images) {
                if (img.zIndex < 0) continue
                drawImagePlacement(nativeCanvas, img, y, cellW, lineHeight)
            }
        }
        // Kitty Unicode placeholder (U=1) のタイル描画 — z>=0 のみここで。
        drawPlaceholderTiles(nativeCanvas, buf, row, y, cellW, lineHeight, rowCols, onlyNegativeZ = false)
    }

    // --- カーソル (選択中・変換中は描かない: 変換中はプリエディットが位置を示す) ---
    if (emu.cursorVisible && selection == null && composingText.isEmpty()) {
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
                        glyphAt(row, emu.cursorCol),
                        cx, y + baselineOffset, textPaint
                    )
                }
            }
        }
    }

    // --- 変換中プリエディット (確定前の文字をカーソル位置に下線付きで重ねる) ---
    // 最新画面表示中 (scrollOffset==0) のみ。確定したら PTY へ書き込まれ通常文字になる。
    if (composingText.isNotEmpty() && scrollOffset == 0) {
        val absCursorRow = buf.scrollbackSize + emu.cursorRow
        var canvasRow = absCursorRow - topAbsRow
        var col = emu.cursorCol
        for (ch in composingText) {
            val span = 2  // 全角かなは 2 セル幅
            if (col + span > canvasCols) { col = 0; canvasRow++ }
            if (canvasRow !in 0 until canvasRows) break
            val x = col * cellW
            val y = canvasRow * lineHeight
            bgPaint.color = PREEDIT_BG_ARGB
            nativeCanvas.drawRect(x, y, x + span * cellW, y + lineHeight, bgPaint)
            textPaint.color = colors.defaultForeground
            textPaint.isFakeBoldText = false
            nativeCanvas.drawText(ch.toString(), x, y + baselineOffset, textPaint)
            underlinePaint.color = colors.defaultForeground
            val uy = y + lineHeight - underlinePaint.strokeWidth
            nativeCanvas.drawLine(x, uy, x + span * cellW, uy, underlinePaint)
            col += span
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

/**
 * 1 つの [com.zerotoship.z2term.emulator.TerminalImage] を anchor 行の
 * 矩形 (`col`, `widthCells`, `heightCells`) にあわせて Canvas に伸縮描画する。
 * Pass 2.7 (z<0) と Pass 3.5 (z>=0) の両方から呼ばれる。
 */
private fun drawImagePlacement(
    canvas: android.graphics.Canvas,
    img: com.zerotoship.z2term.emulator.TerminalImage,
    y: Float,
    cellW: Float,
    lineHeight: Float
) {
    val left = img.col * cellW
    val top = y
    val right = (img.col + img.widthCells) * cellW
    val bottom = y + img.heightCells * lineHeight
    canvas.drawBitmap(
        img.bitmap,
        null,
        android.graphics.RectF(left, top, right, bottom),
        null
    )
}

/**
 * Kitty Unicode placeholder (`U+10EEEE` + diacritic でタイル位置を指定するセル) を
 * 1 セル幅で描画する。 行内のセルを走査し、 [com.zerotoship.z2term.emulator.PlaceholderRef]
 * を持つセルそれぞれについて、 buffer に登録された virtual placement spec から
 * 元 bitmap を引き、 (srcCol / widthCells, srcRow / heightCells) の領域を 1 セル矩形へ
 * srcRect→dstRect で切り出し描画する。
 *
 * [onlyNegativeZ] で z<0 / z>=0 のフィルタを受ける (テキストの下層 / 上層を分ける呼び分け)。
 * Spec が未登録 (= まだ APC で送られていない / 削除済) の placeholder セルは描画スキップ
 * (TUI が再送/差し替えするまで空のまま)。
 */
private fun drawPlaceholderTiles(
    canvas: android.graphics.Canvas,
    buf: com.zerotoship.z2term.emulator.TerminalBuffer,
    row: TerminalRow,
    y: Float,
    cellW: Float,
    lineHeight: Float,
    rowCols: Int,
    onlyNegativeZ: Boolean
) {
    var c = 0
    while (c < rowCols) {
        val cell = row.getCell(c)
        val ref = cell.placeholder
        if (ref == null) { c++; continue }
        val spec = buf.getVirtualPlacement(ref.imageId)
        if (spec == null) { c++; continue }
        val negativeZ = spec.zIndex < 0
        if (onlyNegativeZ != negativeZ) { c++; continue }
        val bm = spec.bitmap
        val gridW = spec.widthCells.coerceAtLeast(1)
        val gridH = spec.heightCells.coerceAtLeast(1)
        val srcRow = ref.srcRow.coerceIn(0, gridH - 1)
        val srcCol = ref.srcCol.coerceIn(0, gridW - 1)
        val tileWpx = bm.width.toFloat() / gridW
        val tileHpx = bm.height.toFloat() / gridH
        val srcLeft = (srcCol * tileWpx).toInt().coerceAtMost(bm.width - 1).coerceAtLeast(0)
        val srcTop = (srcRow * tileHpx).toInt().coerceAtMost(bm.height - 1).coerceAtLeast(0)
        val srcRight = ((srcCol + 1) * tileWpx).toInt().coerceAtMost(bm.width).coerceAtLeast(srcLeft + 1)
        val srcBottom = ((srcRow + 1) * tileHpx).toInt().coerceAtMost(bm.height).coerceAtLeast(srcTop + 1)
        val srcRect = android.graphics.Rect(srcLeft, srcTop, srcRight, srcBottom)
        val dstRect = android.graphics.RectF(c * cellW, y, (c + 1) * cellW, y + lineHeight)
        canvas.drawBitmap(bm, srcRect, dstRect, null)
        c++
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
