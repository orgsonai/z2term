package com.zerotoship.z2term.core

/** 折り返しなしのときに端末へ伝える桁数。これより長い行はこの桁で折り返す。 */
internal const val NO_WRAP_COLS = 200

/**
 * 端末へ伝える桁数。全画面で動くプログラム (alt screen) は画面の幅に合わせて描くものなので、
 * 折り返しなしでも画面の桁数のままにする。
 */
internal fun ptyColumns(screenCols: Int, noWrap: Boolean, altScreen: Boolean): Int =
    if (noWrap && !altScreen) maxOf(screenCols, NO_WRAP_COLS) else screenCols

/** 横に動かせる最大量 (px)。内容が画面に収まるなら 0。 */
internal fun maxScrollX(contentCols: Int, cellW: Float, viewportPx: Float, paddingPx: Float): Float =
    (contentCols * cellW + 2 * paddingPx - viewportPx).coerceAtLeast(0f)

/** 列 [col] が左右に少し余裕を持って見える位置を返す。すでに見えていれば [currentPx] のまま。 */
internal fun scrollXToShowColumn(
    currentPx: Float,
    col: Int,
    cellW: Float,
    viewportPx: Float,
    paddingPx: Float,
    maxPx: Float,
): Float {
    val margin = cellW * CURSOR_MARGIN_CELLS
    val left = col * cellW + paddingPx
    val right = left + cellW
    val target = when {
        left - margin < currentPx -> left - margin
        right + margin > currentPx + viewportPx -> right + margin - viewportPx
        else -> currentPx
    }
    return target.coerceIn(0f, maxPx)
}

private const val CURSOR_MARGIN_CELLS = 4
