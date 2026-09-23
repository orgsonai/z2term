package com.zerotoship.z2term.automation

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.theme.AppColors
import androidx.core.view.isNotEmpty
import androidx.core.graphics.drawable.toDrawable

/**
 * 操作自動化タブの View 部品 (0.8.603)。
 *
 * コマンド一覧の他のタブ (Compose) と同じ寸法・色・等幅の文字を View で出す。見本は自動化ルールタブ
 * (`WhenRulesBody`)、`ServersSheet.kt` の `PillButton` / `IconCell` / `Field` / `HintBox`、確認は
 * `ConfirmDialog`。0.8.578〜0.8.602 はエッジパネル編集画面の部品 (`EdgeSettingsUi`) を使っていて、
 * 等幅でない文字・高さ 44dp の塗りボタン・画面幅の罫線・端末標準のダイアログが隣のタブから浮いていた
 * (利用者の指摘)。保存・下書き・座標取得の動きは変えていない。
 *
 * ⚠ `EdgeSettingsUi` は変えない — エッジパネルの編集画面まで変わる。座標取得の浮かぶ操作欄
 * (`ActionCoordinatePicker`) は対象外 (利用者の指定)。
 * ⚠ 寸法・色を変えるときは Compose 側の部品と一緒に変える。
 */
internal object ActionUi {
    /** コマンド一覧の本文の左右。 */
    const val GUTTER = 16

    /** 項目どうしの間隔 (`Arrangement.spacedBy(10.dp)`)。 */
    const val GAP = 10

    val groundColor: Int get() = AppColors.bgPrimary.toArgb()
    val secondaryColor: Int get() = AppColors.bgSecondary.toArgb()
    val cardColor: Int get() = AppColors.bgCard.toArgb()
    val lineColor: Int get() = AppColors.border.toArgb()
    val textColor: Int get() = AppColors.textPrimary.toArgb()
    val mutedColor: Int get() = AppColors.textSecondary.toArgb()
    val faintColor: Int get() = AppColors.textTertiary.toArgb()
    val accentColor: Int get() = AppColors.accent.toArgb()
    val dangerColor: Int get() = AppColors.error.toArgb()

    val mono: Typeface = Typeface.MONOSPACE
    val medium: Typeface = Typeface.create(Typeface.MONOSPACE, 500, false)
    val semibold: Typeface = Typeface.create(Typeface.MONOSPACE, 600, false)
    val bold: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun alpha(color: Int, fraction: Float): Int = (color and 0x00ffffff) or ((fraction * 255).toInt() shl 24)

    private fun box(context: Context, fill: Int, stroke: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(context, 1).coerceAtLeast(1), stroke)
        cornerRadius = dp(context, radius).toFloat()
    }

    /** 押したときの波紋。[radius] は部品の角丸に合わせる (0 なら四角)。 */
    fun pressable(context: Context, radius: Int, content: Drawable? = null): Drawable = RippleDrawable(
        ColorStateList.valueOf(alpha(accentColor, 0.22f)), content,
        GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(context, radius).toFloat() })

    /** [parent] の末尾に全幅で置く。2 つ目以降は [gap] だけ離す。 */
    fun add(parent: LinearLayout, view: View, gap: Int = GAP) {
        val params = LinearLayout.LayoutParams(-1, -2)
        if (parent.isNotEmpty()) params.topMargin = dp(parent.context, gap)
        parent.addView(view, params)
    }

    // ---- 文字 --------------------------------------------------------------------------------

    fun label(context: Context, value: String, size: Float, color: Int = textColor, face: Typeface = mono): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = face
        }

    /** タブの見出し (緑の等幅 18sp)。編集フォームの見出しは 16sp。 */
    fun heading(context: Context, value: String, size: Float = 18f): TextView =
        label(context, value, size, accentColor, semibold).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }

    /** 入力欄の名前・小見出し。 */
    fun caption(context: Context, value: String): TextView = label(context, value, 11f, mutedColor)

    /** `HintBox`。説明は読んだら飛ばすものなので、一段沈んだ地に置く。 */
    fun note(context: Context, value: String, color: Int = mutedColor, stroke: Int = lineColor): TextView =
        label(context, value, 10f, color).apply {
            background = box(context, secondaryColor, stroke, 6)
            val pad = dp(context, 10)
            setPadding(pad, pad, pad, pad)
        }

    // ---- 押すもの ------------------------------------------------------------------------------

    private fun buttonText(context: Context): TextView = object : TextView(context) {
        override fun getAccessibilityClassName(): CharSequence = Button::class.java.name
    }

    /** `PillButton`。押せないときは枠を無彩色に、文字を薄くする。 */
    fun pill(context: Context, value: String, accent: Boolean = false, danger: Boolean = false, action: () -> Unit): TextView =
        buttonText(context).apply {
            text = value
            textSize = 12f
            typeface = mono
            gravity = Gravity.CENTER
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            val stroke = when { danger -> dangerColor; accent -> accentColor; else -> lineColor }
            // Not `foreground`: inside this apply that name is the View's own foreground drawable.
            val ink = when { danger -> dangerColor; accent -> accentColor; else -> textColor }
            val fill = if (accent) alpha(accentColor, 0.18f) else secondaryColor
            background = pressable(context, 6, StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled), box(context, secondaryColor, lineColor, 6))
                addState(intArrayOf(), box(context, fill, stroke, 6))
            })
            setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(faintColor, ink)))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    /** `IconCell`。行の右端に並べる記号 (▶ ✎ ✕ …)。 */
    fun iconCell(context: Context, glyph: String, description: String, danger: Boolean = false,
        size: Float = 15f, action: () -> Unit): TextView =
        buttonText(context).apply {
            text = glyph
            textSize = size
            typeface = mono
            gravity = Gravity.CENTER
            setTextColor(if (danger) dangerColor else mutedColor)
            setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10))
            contentDescription = description
            background = pressable(context, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    // ---- 入れ物 --------------------------------------------------------------------------------

    /** 一覧の 1 行 (地・1dp の枠・角丸 8dp)。 */
    fun card(context: Context, vertical: Boolean = true): LinearLayout = LinearLayout(context).apply {
        orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        if (!vertical) gravity = Gravity.CENTER_VERTICAL
        background = box(context, cardColor, lineColor, 8)
        setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
    }

    /**
     * 入れ子 (繰り返し・条件分岐) は左の罫線と字下げで出す。枠の中に枠を重ねると 2 段目から読めなくなる。
     * 中身を入れる側を返す。
     */
    fun indent(context: Context, parent: LinearLayout): LinearLayout {
        val inner = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        add(parent, LinearLayout(context).apply {
            setPadding(dp(context, 12), 0, 0, 0)
            addView(View(context).apply { setBackgroundColor(lineColor) }, LinearLayout.LayoutParams(dp(context, 1), -1))
            addView(inner, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(context, 10) })
        }, gap = 8)
        return inner
    }

    // ---- 入力 ----------------------------------------------------------------------------------

    /** `Field` の入力部分。 */
    fun field(context: Context, lines: Int = 1): EditText = EditText(context).apply {
        if (lines <= 1) setSingleLine(true) else {
            minLines = lines
            gravity = Gravity.TOP or Gravity.START
        }
        textSize = 12f
        typeface = mono
        setTextColor(textColor)
        setHintTextColor(alpha(mutedColor, 0.55f))
        setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        minHeight = 0
        minimumHeight = 0
        backgroundTintList = null
        background = box(context, cardColor, lineColor, 6)
        setTextCursorDrawable(GradientDrawable().apply { setColor(accentColor); setSize(dp(context, 2), 0) })
    }

    /** 名前を上、入力を下に置く (`Field` と同じ並び)。 */
    fun labeled(context: Context, parent: LinearLayout, name: String, control: View, gap: Int = GAP) {
        add(parent, LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption(context, name))
            addView(control, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 2) })
        }, gap)
    }

    /** 等幅の一覧。[roomy] は選択肢の一覧用 (押しやすい高さ)。 */
    fun listAdapter(context: Context, items: MutableList<String>, roomy: Boolean = false): ArrayAdapter<String> =
        object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getView(position, convertView, parent), roomy)

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getDropDownView(position, convertView, parent), true)
        }

    private fun style(view: View, roomy: Boolean): View {
        (view as? TextView)?.apply {
            typeface = mono
            textSize = 12f
            setTextColor(textColor)
            val vertical = dp(context, if (roomy) 10 else 8)
            minHeight = if (roomy) dp(context, 44) else 0
            minimumHeight = minHeight
            setPadding(dp(context, if (roomy) 12 else 10), vertical, dp(context, if (roomy) 12 else 4), vertical)
        }
        return view
    }

    fun spinner(context: Context, items: List<String>, selected: Int): Spinner = Spinner(context, Spinner.MODE_DROPDOWN).apply {
        adapter = listAdapter(context, items.toMutableList())
        setSelection(selected)
        background = null
        setPopupBackgroundDrawable(box(context, cardColor, lineColor, 6))
    }

    /** 選択欄を `Field` と同じ枠に入れ、▾ で選べることを示す。 */
    fun framed(context: Context, spinner: Spinner): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = pressable(context, 6, box(context, cardColor, lineColor, 6))
        addView(spinner, LinearLayout.LayoutParams(0, -2, 1f))
        addView(label(context, "▾", 12f, mutedColor).apply { setPadding(0, 0, dp(context, 10), 0) })
        isClickable = true
        setOnClickListener { spinner.performClick() }
    }

    // ---- ダイアログ ----------------------------------------------------------------------------

    /**
     * `ConfirmDialog` と同じ見た目のダイアログ。地は bgCard、見出しは等幅 16sp 太字、本文は等幅 13sp、
     * 右下に文字だけのボタン (確定は太字で色付き、やめるは薄い色)。
     *
     * [onConfirm] が閉じる責任を持つ — 入力に誤りがあれば開いたまま文言を出せるように。
     * やめる・戻る・外側のタップは [Dialog.cancel] なので、`setOnCancelListener` で受けられる。
     */
    fun modal(context: Context, title: String, content: View? = null, message: String? = null,
        confirm: String? = null, confirmColor: Int = accentColor,
        dismiss: String = context.getString(R.string.action_cancel), scroll: Boolean = true,
        onConfirm: (Dialog) -> Unit = { it.dismiss() }): Dialog {
        val dialog = object : Dialog(context) {
            override fun onStart() {
                super.onStart()
                val metrics = context.resources.displayMetrics
                window?.setLayout(minOf(metrics.widthPixels - dp(context, 48), dp(context, 560)),
                    WindowManager.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(context, 28).toFloat() }
            setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 16))
        }
        root.addView(label(context, title, 16f, textColor, bold).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 16) })
        val inner = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        message?.let { add(inner, label(context, it, 13f, mutedColor)) }
        content?.let { add(inner, it, gap = 12) }
        val body: View = if (scroll) ScrollView(context).apply { addView(inner) } else inner
        root.addView(body, LinearLayout.LayoutParams(-1, -2, 1f))
        val buttons = LinearLayout(context).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        buttons.addView(textButton(context, dismiss, mutedColor, mono) { dialog.cancel() })
        if (confirm != null) {
            buttons.addView(textButton(context, confirm, confirmColor, bold) { onConfirm(dialog) },
                LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(context, 8) })
        }
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 16) })
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        return dialog
    }

    /** 選ぶと閉じる一覧のダイアログ (以前の `AlertDialog.setItems`)。[danger] の行は赤で出す。 */
    fun choices(context: Context, title: String, labels: List<String>, danger: Set<Int> = emptySet(),
        onPick: (Int) -> Unit): Dialog {
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        lateinit var dialog: Dialog
        labels.forEachIndexed { index, value ->
            list.addView(buttonText(context).apply {
                text = value
                textSize = 13f
                typeface = mono
                setTextColor(if (index in danger) dangerColor else textColor)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(context, 44)
                setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 10))
                background = pressable(context, 6)
                isClickable = true
                isFocusable = true
                setOnClickListener { dialog.dismiss(); onPick(index) }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        dialog = modal(context, title, content = list)
        return dialog
    }

    private fun textButton(context: Context, value: String, color: Int, face: Typeface, action: () -> Unit): TextView =
        buttonText(context).apply {
            text = value
            textSize = 14f
            typeface = face
            setTextColor(color)
            gravity = Gravity.CENTER
            minHeight = dp(context, 40)
            setPadding(dp(context, 12), 0, dp(context, 12), 0)
            background = pressable(context, 20)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
}
