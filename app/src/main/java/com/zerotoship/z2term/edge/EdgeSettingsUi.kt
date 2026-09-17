package com.zerotoship.z2term.edge

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.theme.AppColors

/**
 * Every surface of the hand-built editors is declared here - the panel editor and the action-macro
 * screens. Overlays carry no Activity theme, so a control that styles itself locally is a control
 * that drifts from the rest of the screen, and two screens that style themselves separately drift
 * from each other. The vocabulary is deliberately small: one ground, one hairline, one accent,
 * four button weights.
 */
internal object EdgeSettingsUi {
    /** One gutter for the whole editor; every heading, field and row starts on this line. */
    const val GUTTER = 16

    fun dp(context: Context, value: Int): Int = EdgeEditorUi.dp(context, value)

    // The editor does not invent colours: it reads the app palette, which is derived from the
    // selected terminal theme (ground and text straight from it, surfaces and borders lifted off
    // the ground, one accent - the ZTS green). Nothing here to keep in step with a second palette.
    fun canvas(context: Context): Int = AppColors.bgPrimary.toArgb()
    fun surface(context: Context): Int = AppColors.bgCard.toArgb()
    fun line(context: Context): Int = AppColors.border.toArgb()
    fun strong(context: Context): Int = AppColors.textTertiary.toArgb()
    fun foreground(context: Context): Int = AppColors.textPrimary.toArgb()
    fun muted(context: Context): Int = AppColors.textSecondary.toArgb()
    fun accent(context: Context): Int = AppColors.accent.toArgb()
    fun danger(context: Context): Int = AppColors.error.toArgb()
    /** A theme may hand us any accent, so the label on a filled button is chosen by luminance. */
    private fun onAccent(context: Context): Int =
        if (ColorUtils.calculateLuminance(accent(context)) > 0.4) Color.rgb(10, 12, 14) else Color.WHITE
    fun tint(color: Int, alpha: Int): Int = (color and 0x00ffffff) or (alpha shl 24)

    private fun box(fill: Int, stroke: Int, width: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill); setStroke(width, stroke); cornerRadius = radius.toFloat()
    }

    fun ripple(context: Context, content: Drawable?, mask: Drawable? = ColorDrawable(Color.WHITE)): Drawable =
        RippleDrawable(ColorStateList.valueOf(tint(accent(context), 0x38)), content, mask)

    /** A bordered block for content that must be read as one unit (a warning, a step, a draft). */
    fun frame(context: Context, stroke: Int = line(context), fill: Int = surface(context)): Drawable =
        box(fill, stroke, dp(context, 1).coerceAtLeast(1), dp(context, 4))

    // ---- type ----------------------------------------------------------------------------------

    fun title(context: Context, value: String): TextView = TextView(context).apply {
        text = value; textSize = 17f; setTextColor(foreground(context)); setTypeface(null, Typeface.BOLD)
        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
    }

    fun body(context: Context, value: String): TextView = TextView(context).apply {
        text = value; textSize = 15f; setTextColor(foreground(context))
    }

    /** Field names and row subtitles: small, quiet, and never competing with the value beside it. */
    fun caption(context: Context, value: String): TextView = TextView(context).apply {
        text = value; textSize = 12f; setTextColor(muted(context)); letterSpacing = 0.02f
        setPadding(0, 0, 0, dp(context, 5))
    }

    /** A saved definition is source, so it is set in a fixed pitch and its arguments line up. */
    fun mono(context: Context, value: String): TextView = TextView(context).apply {
        text = value; textSize = 13.5f; setTextColor(foreground(context)); typeface = Typeface.MONOSPACE
    }

    /** The step number lives in its own column so the lines beneath it stay aligned. */
    fun index(context: Context, value: String): TextView = TextView(context).apply {
        text = value; textSize = 12f; setTextColor(muted(context)); typeface = Typeface.MONOSPACE
        gravity = Gravity.END
    }

    /** Explanations are read once and then skipped, so they sit on their own tinted ground. */
    fun note(context: Context, value: String): TextView = TextView(context).apply {
        text = value; textSize = 12.5f; setTextColor(muted(context))
        setLineSpacing(dp(context, 3).toFloat(), 1f)
        setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        background = box(surface(context), line(context), dp(context, 1), dp(context, 4))
    }

    // ---- structure -----------------------------------------------------------------------------

    fun hairline(context: Context): View = View(context).apply {
        setBackgroundColor(line(context))
        layoutParams = LinearLayout.LayoutParams(-1, dp(context, 1))
    }

    fun column(context: Context, gutter: Boolean = false): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        if (gutter) setPadding(dp(context, GUTTER), dp(context, 12), dp(context, GUTTER), dp(context, 12))
    }

    fun row(context: Context): LinearLayout = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
    }

    fun spacer(context: Context, height: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(-1, dp(context, height))
    }

    /** Name above, control below, one gap after: the only field shape used in the editor. */
    fun labeled(context: Context, parent: LinearLayout, label: String, control: View, gap: Int = 14) {
        parent.addView(caption(context, label))
        parent.addView(control, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, gap) })
    }

    /**
     * Depth is drawn with a left rule and a margin, never with a second box: nesting boxes inside
     * boxes stops being readable at the second level, and macro blocks nest further than that.
     * Returns the container the caller fills; the wrapper is already attached to [parent].
     */
    fun indent(context: Context, parent: LinearLayout): LinearLayout {
        val inner = column(context)
        parent.addView(LinearLayout(context).apply {
            setPadding(dp(context, 12), 0, 0, 0)
            addView(View(context).apply { setBackgroundColor(line(context)) },
                LinearLayout.LayoutParams(dp(context, 1), -1))
            addView(inner, LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, -2))
        return inner
    }

    /**
     * A setting whose value is edited elsewhere: name above, current value below, one row.
     * Baking the value into a button's label made the label change length every time it was set.
     */
    fun valueRow(context: Context, label: String, value: String, action: () -> Unit): View =
        row(context).apply {
            minimumHeight = dp(context, 56)
            setPadding(dp(context, GUTTER), dp(context, 8), dp(context, GUTTER), dp(context, 8))
            background = ripple(context, null)
            isClickable = true
            contentDescription = "$label: $value"
            addView(column(context).apply {
                addView(caption(context, label).apply { setPadding(0, 0, 0, dp(context, 2)) })
                addView(body(context, value).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply {
                text = "›"; textSize = 16f; setTextColor(muted(context))
                setPadding(dp(context, 10), 0, 0, 0)
            })
            setOnClickListener { action() }
        }

    /**
     * Collapsible group. [sub] marks a group nested inside another one: it loses the full-width rule
     * and gains a left rule instead, so depth reads from the margin rather than from a second border.
     */
    fun section(context: Context, parent: LinearLayout, name: String,
        expanded: Boolean = false, sub: Boolean = false): LinearLayout {
        if (!sub) parent.addView(hairline(context))
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, if (sub) 12 else GUTTER), 0,
                if (sub) 0 else dp(context, GUTTER), dp(context, if (sub) 10 else 18))
        }
        val container: View = if (!sub) inner else LinearLayout(context).apply {
            addView(View(context).apply { setBackgroundColor(line(context)) },
                LinearLayout.LayoutParams(dp(context, 1), -1))
            addView(inner, LinearLayout.LayoutParams(0, -2, 1f))
            setPadding(dp(context, 12), 0, 0, 0)
        }
        container.visibility = if (expanded) View.VISIBLE else View.GONE
        val mark = TextView(context).apply {
            textSize = 13f; setTextColor(muted(context)); setPadding(dp(context, 10), 0, 0, 0)
        }
        val heading = TextView(context).apply {
            text = name; textSize = if (sub) 14f else 15f; setTextColor(foreground(context))
            setTypeface(null, Typeface.BOLD)
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, if (sub) 44 else 52)
            setPadding(dp(context, if (sub) 12 else GUTTER), dp(context, 6), dp(context, GUTTER), dp(context, 6))
            background = ripple(context, null)
            isClickable = true
            contentDescription = name
            addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
            addView(mark)
        }
        fun sync() { mark.text = if (container.visibility == View.VISIBLE) "▾" else "▸" }
        header.setOnClickListener {
            container.visibility = if (container.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            sync()
        }
        sync()
        parent.addView(header, LinearLayout.LayoutParams(-1, -2))
        parent.addView(container, LinearLayout.LayoutParams(-1, -2))
        return inner
    }

    // ---- controls ------------------------------------------------------------------------------

    enum class Kind { PRIMARY, OUTLINE, QUIET, DANGER }

    fun button(context: Context, label: String, kind: Kind = Kind.OUTLINE, action: () -> Unit): Button =
        Button(context).apply {
            text = label; textSize = 14f; isAllCaps = false
            minWidth = 0; minimumWidth = 0
            minHeight = dp(context, 44); minimumHeight = dp(context, 44)
            setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6))
            setTypeface(null, if (kind == Kind.PRIMARY) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(labelColor(context, kind))
            background = ripple(context, shape(context, kind))
            setOnClickListener { action() }
        }

    /** Square, glyph-only controls (reorder, remove) that must not out-shout the row they sit in. */
    fun iconButton(context: Context, glyph: String, action: () -> Unit): Button = Button(context).apply {
        text = glyph; textSize = 16f; isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setTextColor(labelColor(context, Kind.QUIET))
        background = ripple(context, null, ColorDrawable(Color.WHITE))
        setOnClickListener { action() }
    }

    private fun shape(context: Context, kind: Kind): Drawable? {
        val radius = dp(context, 4)
        val width = dp(context, 1).coerceAtLeast(1)
        return when (kind) {
            Kind.PRIMARY -> StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled),
                    box(tint(muted(context), 0x2E), Color.TRANSPARENT, 0, radius))
                addState(intArrayOf(), box(accent(context), Color.TRANSPARENT, 0, radius))
            }
            Kind.OUTLINE -> StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled),
                    box(Color.TRANSPARENT, tint(line(context), 0x80), width, radius))
                addState(intArrayOf(), box(Color.TRANSPARENT, strong(context), width, radius))
            }
            Kind.DANGER -> box(Color.TRANSPARENT, danger(context), width, radius)
            Kind.QUIET -> null
        }
    }

    private fun labelColor(context: Context, kind: Kind): ColorStateList {
        val enabled = when (kind) {
            Kind.PRIMARY -> onAccent(context)
            Kind.OUTLINE -> foreground(context)
            Kind.QUIET -> muted(context)
            Kind.DANGER -> danger(context)
        }
        return ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(tint(muted(context), 0x80), enabled))
    }

    fun field(context: Context, lines: Int = 1): EditText = EditText(context).apply {
        // A single-line field is the norm; a source editor asks for a floor, not a ceiling.
        if (lines <= 1) setSingleLine(true) else {
            minLines = lines; gravity = Gravity.TOP or Gravity.START
        }
        textSize = 15f
        setTextColor(foreground(context)); setHintTextColor(muted(context))
        setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        minHeight = dp(context, 44); minimumHeight = dp(context, 44)
        backgroundTintList = null
        background = StateListDrawable().apply {
            val radius = dp(context, 4)
            addState(intArrayOf(android.R.attr.state_focused),
                box(surface(context), accent(context), dp(context, 2).coerceAtLeast(2), radius))
            addState(intArrayOf(), box(surface(context), line(context), dp(context, 1).coerceAtLeast(1), radius))
        }
    }

    /** The platform spinner keeps its own arrow; only its colours are brought into line. */
    fun dress(context: Context, spinner: Spinner): Spinner = spinner.apply {
        backgroundTintList = ColorStateList.valueOf(strong(context))
        setPopupBackgroundDrawable(box(surface(context), line(context), dp(context, 1).coerceAtLeast(1), dp(context, 4)))
        minimumHeight = dp(context, 44)
    }

    fun dress(context: Context, bar: SeekBar): SeekBar = bar.apply {
        progressTintList = ColorStateList.valueOf(accent(context))
        thumbTintList = ColorStateList.valueOf(accent(context))
        progressBackgroundTintList = ColorStateList.valueOf(line(context))
        setPadding(paddingLeft, dp(context, 4), paddingRight, dp(context, 8))
    }

    /**
     * A switch has to read as clearly OFF as it does ON, so both sides are coloured; leaving the
     * unchecked side to the platform makes it vanish into a dark ground.
     */
    fun switchOf(context: Context, checked: Boolean): Switch = Switch(context).apply {
        isChecked = checked
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(states, intArrayOf(accent(context), strong(context)))
        trackTintList = ColorStateList(states, intArrayOf(tint(accent(context), 0x70), line(context)))
    }

    /** Label left, switch right: the same shape as every other row, so the eye keeps one edge. */
    fun toggleRow(context: Context, label: String, checked: Boolean, changed: (Boolean) -> Unit): View {
        val control = switchOf(context, checked).apply {
            contentDescription = label
            setOnCheckedChangeListener { _, value -> changed(value) }
        }
        return row(context).apply {
            minimumHeight = dp(context, 48)
            addView(body(context, label), LinearLayout.LayoutParams(0, -2, 1f))
            addView(control)
            setOnClickListener { control.toggle() }
            background = ripple(context, null)
        }
    }

    /** Primary navigation: the active page is named by an accent rule, not by a filled shape. */
    fun pageTab(context: Context, label: String, active: Boolean, action: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            contentDescription = label
            background = ripple(context, null)
            addView(TextView(context).apply {
                text = label; textSize = 15f; gravity = Gravity.CENTER
                setTextColor(if (active) accent(context) else muted(context))
                setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                setPadding(dp(context, 6), dp(context, 13), dp(context, 6), dp(context, 11))
            }, LinearLayout.LayoutParams(-1, -2))
            addView(View(context).apply {
                setBackgroundColor(if (active) accent(context) else Color.TRANSPARENT)
            }, LinearLayout.LayoutParams(-1, dp(context, 2).coerceAtLeast(2)))
            setOnClickListener { action() }
        }

    /** Secondary scope (which tab is being edited): outlined, smaller, never mistaken for a page. */
    fun chip(context: Context, label: String, active: Boolean, action: () -> Unit): Button =
        Button(context).apply {
            text = label; textSize = 13f; isAllCaps = false
            minWidth = 0; minimumWidth = 0
            minHeight = dp(context, 36); minimumHeight = dp(context, 36)
            setPadding(dp(context, 12), 0, dp(context, 12), 0)
            setTextColor(if (active) accent(context) else muted(context))
            setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            background = ripple(context, box(
                if (active) tint(accent(context), 0x1E) else Color.TRANSPARENT,
                if (active) accent(context) else line(context),
                dp(context, 1).coerceAtLeast(1), dp(context, 4)))
            isSelected = active
            setOnClickListener { action() }
        }
}
