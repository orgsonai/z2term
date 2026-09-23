package com.zerotoship.z2term.edge

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/** Per-note paper and ink, retaining theme defaults and actual line positions. */
internal object EdgeNoteUi {
    // The double tap calls performClick(); single taps are withheld on purpose (see onTouchEvent).
    @SuppressLint("ClickableViewAccessibility")
    fun preview(context: Context, ruled: Boolean, ink: Int? = null, paper: Int? = null): TextView = object : TextView(context) {
        private val rules = Rules(this, ruled, ink != null || paper != null)
        private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true
            override fun onDoubleTap(event: MotionEvent): Boolean = performClick()
        }).apply { setIsLongpressEnabled(false) }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) return false
            taps.onTouchEvent(event)
            // Do not send single taps to TextView's click handler. Parent scrolling can still cancel.
            return true
        }

        // Keep the normal click action for accessibility services and keyboard activation.
        override fun performClick(): Boolean = super.performClick()

        override fun onDraw(canvas: Canvas) {
            rules.draw(canvas)
            super.onDraw(canvas)
        }
    }.apply { style(this, ink, paper) }

    /**
     * メモの編集欄。選択が変わったら [onSelectionChange] を呼ぶ。
     *
     * ⚠ **重ねて表示する窓 (エッジパネル) では、長押しで選べてもフローティングの選択メニューが
     * 出ないことがある** (利用者の報告: 「長押し選択してもメニューが出ずにコピーとか切り取りも
     * 出来ません」)。あれは `PopupWindow` を親の窓にぶら下げて出すもので、Activity ではない窓の
     * token では黙って出ないことがある。⇒ **コピー・切り取り・貼り付けはパネル側のボタンからも
     * 叩けるようにする** ([EdgeRuntime] の note)。選択の有無でボタンの可否を切り替えるために、
     * ここで選択の変化を知らせる。⚠ システムの選択メニューは**殺さない** — 出る端末では
     * そちらの方が手数が少ないので、こちらは常に使える控えとして足すだけにする。
     */
    @SuppressLint("ViewConstructor") // Built in code only, never inflated from XML.
    internal class NoteEditor(context: Context, ruled: Boolean, custom: Boolean) : EditText(context) {
        private val rules = Rules(this, ruled, custom)
        var onSelectionChange: (() -> Unit)? = null

        override fun onDraw(canvas: Canvas) {
            rules.draw(canvas)
            super.onDraw(canvas)
        }

        override fun onSelectionChanged(selStart: Int, selEnd: Int) {
            super.onSelectionChanged(selStart, selEnd)
            // ⚠ 親クラスの初期化中にも呼ばれる。そのときは代入前なので null で素通りする。
            onSelectionChange?.invoke()
        }
    }

    fun editor(context: Context, ruled: Boolean, ink: Int? = null, paper: Int? = null): NoteEditor =
        NoteEditor(context, ruled, ink != null || paper != null).apply {
            background = null
            style(this, ink, paper)
        }

    /**
     * 選択メニューの代わりに押すボタン (コピー・切り取り・貼り付け)。
     * ⚠ 文言は Android の物 (`android.R.string.copy` など) を使う — 端末の言語でそのまま出るし、
     * 利用者が他のアプリで見慣れた言い回しと揃う。
     */
    fun action(context: Context, textRes: Int): TextView = TextView(context).apply {
        text = context.getString(textRes)
        contentDescription = text
        textSize = 12f
        gravity = Gravity.CENTER
        isClickable = true
        // ⚠ **入力欄から焦点を奪わない**。奪うと押した瞬間に選択が外れ、コピーも切り取りも
        //   空振りする。押せるが焦点は取らない、が正解 (読み上げからは押せるままになる)。
        isFocusable = false
        isFocusableInTouchMode = false
        background = EdgeSettingsUi.ripple(context, null)
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(EdgeEditorUi.muted(context), EdgeEditorUi.foreground(context))))
        setPadding(EdgeEditorUi.dp(context, 10), EdgeEditorUi.dp(context, 8),
            EdgeEditorUi.dp(context, 10), EdgeEditorUi.dp(context, 8))
    }

    private fun style(view: TextView, ink: Int?, paper: Int?) = with(view) {
        gravity = Gravity.TOP or Gravity.START
        val custom = ink != null || paper != null
        val foreground = ink ?: if (paper != null) {
            if (ColorUtils.calculateLuminance(paper) > 0.179) Color.BLACK else Color.WHITE
        } else EdgeEditorUi.foreground(context)
        setTextColor(foreground)
        setHintTextColor(if (custom) ColorUtils.setAlphaComponent(foreground, 160) else EdgeEditorUi.muted(context))
        paper?.let { setBackgroundColor(it) }
        if (custom) {
            highlightColor = ColorUtils.setAlphaComponent(foreground, 64)
            if (this is EditText) {
                textCursorDrawable?.mutate()?.let { it.setTint(foreground); textCursorDrawable = it }
                textSelectHandle?.mutate()?.let { it.setTint(foreground); setTextSelectHandle(it) }
                textSelectHandleLeft?.mutate()?.let { it.setTint(foreground); setTextSelectHandleLeft(it) }
                textSelectHandleRight?.mutate()?.let { it.setTint(foreground); setTextSelectHandleRight(it) }
            }
        }
        setPadding(EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 8),
            EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 8))
    }

    fun button(context: Context, icon: Int, description: Int): ImageButton = ImageButton(context).apply {
        setImageResource(icon)
        contentDescription = context.getString(description)
        tooltipText = contentDescription
        imageTintList = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(EdgeEditorUi.muted(context), EdgeEditorUi.foreground(context)))
        background = EdgeSettingsUi.ripple(context, null)
        setPadding(EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 12),
            EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 12))
        layoutParams = LinearLayout.LayoutParams(EdgeEditorUi.dp(context, 48), EdgeEditorUi.dp(context, 48))
    }

    private class Rules(private val view: TextView, private val enabled: Boolean, private val custom: Boolean) {
        private val paint = Paint().apply {
            color = EdgeEditorUi.line(view.context)
            strokeWidth = view.resources.displayMetrics.density
        }
        fun draw(canvas: Canvas) {
            if (!enabled) return
            if (custom) paint.color = ColorUtils.setAlphaComponent(view.currentTextColor, 64)
            val layout = view.layout ?: return
            val top = view.extendedPaddingTop.toFloat()
            val first = layout.getLineForVertical((view.scrollY - top).toInt().coerceAtLeast(0))
            val bottom = view.scrollY + view.height - view.extendedPaddingBottom
            for (line in first until layout.lineCount) {
                val y = top + layout.getLineBottom(line) - paint.strokeWidth / 2
                if (y > bottom) break
                canvas.drawLine(view.paddingLeft.toFloat(), y, (view.width - view.paddingRight).toFloat(), y, paint)
            }
            var y = top + layout.height + view.lineHeight - paint.strokeWidth / 2
            while (y <= bottom) {
                canvas.drawLine(view.paddingLeft.toFloat(), y, (view.width - view.paddingRight).toFloat(), y, paint)
                y += view.lineHeight.coerceAtLeast(1)
            }
        }
    }
}
