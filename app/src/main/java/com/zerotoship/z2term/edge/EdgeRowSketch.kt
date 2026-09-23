package com.zerotoship.z2term.edge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.zerotoship.z2term.R

/**
 * The Layout page's drawing of a tab, at the real panel's proportions. Items are moved by
 * long-pressing and dropping them on a row (between its items) or on a gap between rows (a new
 * row). The rule under a row sets its height, the rule between two items sets their widths.
 * Nothing is saved while a finger is down; the [Listener] receives the result on release.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class EdgeRowSketch(
    context: Context,
    private val rows: List<List<String>>,
    /** Height of each row on the real panel, in px. */
    heights: List<Int>,
    /** Relative widths of the items of each row (see [EdgeRows.weights]). */
    weights: List<List<Float>>,
    private val panelWidth: Int,
    private val panelHeight: Int,
    /** The menu keeps its full height (fixed, or holding a terminal): space under the rows is drawn empty. */
    private val bounded: Boolean,
    private val fill: (LinearLayout, String, Boolean) -> Unit,
    private val listener: Listener,
) : LinearLayout(context) {
    interface Listener {
        fun move(id: String, change: (List<List<String>>) -> List<List<String>>)
        fun height(row: Int, percent: Int)
        fun widths(row: Int, percents: List<Int>)
    }

    private data class Drag(val id: String)

    private val heights = heights.map { it.toFloat() }.toMutableList()
    private val weights = weights.map { it.toMutableList() }
    private val board = LinearLayout(context).apply {
        orientation = VERTICAL
        background = EdgeSettingsUi.frame(context)
        val pad = dp(4); setPadding(pad, pad, pad, pad)
    }
    private val strips = mutableListOf<LinearLayout>()
    private val status = EdgeSettingsUi.caption(context, context.getString(R.string.edge_sketch_help)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private var scale = 1f

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(board, LayoutParams(-2, -2))
        addView(status, LayoutParams(-1, -2).apply { topMargin = dp(6) })
        board.addView(gap(0))
        rows.forEachIndexed { r, line ->
            val strip = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            line.forEachIndexed { i, id ->
                if (i > 0) strip.addView(widthRule(r, i - 1), LayoutParams(dp(12), -1))
                val cell = LinearLayout(context).apply {
                    gravity = Gravity.CENTER
                    tag = id
                    setOnLongClickListener {
                        it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        it.startDragAndDrop(null, DragShadowBuilder(it), Drag(id), 0)
                    }
                }
                fill(cell, id, line.size == 1)
                strip.addView(cell, LayoutParams(0, -1, this.weights[r][i]))
            }
            strip.setOnDragListener { _, event -> dropOnRow(strip, r, event) }
            strips += strip
            board.addView(strip, LayoutParams(-1, dp(24)))
            board.addView(gap(r + 1))
        }
        board.addView(View(context), LayoutParams(-1, 0, 1f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep the real proportions: as wide as the page allows, but never taller than a phone's third.
        val room = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val tall = (if (bounded) maxOf(heights.sum(), panelHeight.toFloat()) else heights.sum()).coerceAtLeast(1f)
        scale = minOf(room.toFloat() / panelWidth.coerceAtLeast(1), dp(280) / tall)
        board.layoutParams.width = (panelWidth * scale).toInt().coerceAtLeast(dp(120)).coerceAtMost(room)
        var drawn = board.paddingTop + board.paddingBottom + dp(16) * (strips.size + 1)
        strips.forEachIndexed { r, strip ->
            strip.layoutParams.height = (heights[r] * scale).toInt().coerceAtLeast(dp(28))
            drawn += strip.layoutParams.height
        }
        // The rules between rows take room of their own; the frame grows by them, never squeezes the rows.
        board.layoutParams.height = if (bounded) maxOf(drawn, (tall * scale).toInt() + drawn - (heights.sum() * scale).toInt()) else -2
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** The space above, between and below rows: a drop target for a new row, and the height rule of the row above. */
    private fun gap(index: Int): View = View(context).apply {
        layoutParams = LayoutParams(-1, dp(16))
        background = rule(horizontal = true, active = false)
        contentDescription = context.getString(R.string.edge_sketch_new_row)
        setOnDragListener { view, event ->
            if (event.localState !is Drag) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED -> view.background = rule(horizontal = true, active = true, target = true)
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED ->
                    view.background = rule(horizontal = true, active = false)
                DragEvent.ACTION_DROP -> {
                    val id = (event.localState as Drag).id
                    listener.move(id) { EdgeRows.newRow(it, id, index) }
                }
            }
            true
        }
        if (index > 0) setOnTouchListener(resizer(horizontal = true) { delta, done ->
            val r = index - 1
            val real = (heights[r] + delta / scale).coerceIn(dp(20) / scale, panelHeight.toFloat())
            heights[r] = real
            val percent = (real / panelHeight.coerceAtLeast(1) * 100f).toInt().coerceIn(3, 100)
            status.text = context.getString(R.string.edge_sketch_height, r + 1, percent)
            requestLayout()
            if (done) listener.height(r, percent)
        })
    }

    /** The rule between items i and i + 1 of row r. */
    private fun widthRule(r: Int, i: Int): View = View(context).apply {
        background = rule(horizontal = false, active = false)
        setOnTouchListener(resizer(horizontal = false) { delta, done ->
            val strip = strips[r]
            val line = weights[r]
            val room = (strip.width - strip.paddingLeft - strip.paddingRight - dp(12) * (line.size - 1)).coerceAtLeast(1)
            val pair = line[i] + line[i + 1]
            val least = line.sum() * 0.05f
            val moved = (line[i] + delta / room * line.sum()).coerceIn(least, pair - least)
            line[i] = moved; line[i + 1] = pair - moved
            // Cells sit at even child positions; the rules are between them.
            line.forEachIndexed { k, weight -> (strip.getChildAt(k * 2).layoutParams as LayoutParams).weight = weight }
            val percents = EdgeRows.percents(line)
            status.text = context.getString(R.string.edge_sketch_widths, r + 1, percents.joinToString(" : ") { "$it%" })
            strip.requestLayout()
            if (done) listener.widths(r, percents)
        })
    }

    /** Drags on a rule (across a horizontal one, along a vertical one); reports each step, then done on release. */
    private fun resizer(horizontal: Boolean, change: (Float, Boolean) -> Unit) = object : OnTouchListener {
        private var start = 0f
        private var last = 0f
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val at = if (horizontal) event.rawY else event.rawX
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    start = at; last = at
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    view.background = rule(horizontal, active = true)
                }
                MotionEvent.ACTION_MOVE -> { change(at - last, false); last = at }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.background = rule(horizontal, active = false)
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP && kotlin.math.abs(at - start) > dp(2)) change(0f, true)
                }
            }
            return true
        }
    }

    private fun dropOnRow(strip: LinearLayout, r: Int, event: DragEvent): Boolean {
        val drag = event.localState as? Drag ?: return false
        when (event.action) {
            DragEvent.ACTION_DRAG_ENTERED -> strip.foreground = GradientDrawable().apply {
                setStroke(dp(2).coerceAtLeast(1), EdgeSettingsUi.accent(context))
            }
            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> strip.foreground = null
            DragEvent.ACTION_DROP -> {
                // The position counts the other items whose centre lies left of the finger.
                val position = (0 until strip.childCount).map { strip.getChildAt(it) }
                    .filter { it.tag is String && it.tag != drag.id }
                    .count { it.left + it.width / 2 < event.x }
                listener.move(drag.id) { EdgeRows.insert(it, drag.id, r, position) }
            }
        }
        return true
    }

    /** A thin line centred in the rule's touch area, stronger while held or targeted. */
    private fun rule(horizontal: Boolean, active: Boolean, target: Boolean = false): android.graphics.drawable.Drawable {
        val color = if (active) EdgeSettingsUi.accent(context) else EdgeSettingsUi.line(context)
        val thickness = dp(if (target) 4 else 2).coerceAtLeast(1)
        val line = ColorDrawable(color)
        return android.graphics.drawable.InsetDrawable(line,
            if (horizontal) dp(24) else (dp(12) - thickness) / 2,
            if (horizontal) (dp(16) - thickness) / 2 else dp(4),
            if (horizontal) dp(24) else (dp(12) - thickness) / 2,
            if (horizontal) (dp(16) - thickness) / 2 else dp(4))
    }

    private fun dp(value: Int): Int = EdgeSettingsUi.dp(context, value)
}
