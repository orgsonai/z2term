package com.zerotoship.z2term.edge

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * パネルの上で部品を別の部品へ落としたときの行き先。
 *
 * ⚠ 以前は部品のどこへ落としても、その部品の段へ横並びで入った。設定の段の見取り図では
 * 段の間へ落とせるのに、パネルの上ではできなかった (利用者の報告)。⇒ 部品の上端・下端の帯は
 * 「その段の上・下に新しい段」、真ん中は「左・右に横並び」にする。
 */
internal enum class EdgeDropSpot {
    ABOVE, BELOW, BEFORE, AFTER;

    /** 段の並びに当てはめる。[target] の段の上下に新しい段を作るか、同じ段の左右へ入れる。 */
    fun apply(rows: List<List<String>>, id: String, target: String): List<List<String>> {
        if (id == target) return rows
        val row = rows.indexOfFirst { target in it }
        if (row < 0) return rows
        return when (this) {
            ABOVE -> EdgeRows.newRow(rows, id, row)
            BELOW -> EdgeRows.newRow(rows, id, row + 1)
            BEFORE -> EdgeRows.drop(rows, id, target, after = false)
            AFTER -> EdgeRows.drop(rows, id, target, after = true)
        }
    }

    companion object {
        /** 部品の中の位置 ([x], [y]) から行き先を決める。上下の帯は高さの 3 割、ただし [band] まで。 */
        fun at(x: Float, y: Float, width: Int, height: Int, band: Float): EdgeDropSpot {
            val edge = (height * 0.3f).coerceAtMost(band)
            return when {
                y < edge -> ABOVE
                y > height - edge -> BELOW
                x < width / 2f -> BEFORE
                else -> AFTER
            }
        }
    }
}

/** 落とす先を示す線。段の上下なら横線、横並びなら入る側の縦線を引く。 */
internal class EdgeDropMark(private val spot: EdgeDropSpot, color: Int, private val thickness: Float) : Drawable() {
    private val paint = Paint().apply { this.color = color }

    override fun draw(canvas: Canvas) {
        val b = bounds
        when (spot) {
            EdgeDropSpot.ABOVE -> canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.top + thickness, paint)
            EdgeDropSpot.BELOW -> canvas.drawRect(b.left.toFloat(), b.bottom - thickness, b.right.toFloat(), b.bottom.toFloat(), paint)
            EdgeDropSpot.BEFORE -> canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.left + thickness, b.bottom.toFloat(), paint)
            EdgeDropSpot.AFTER -> canvas.drawRect(b.right - thickness, b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
