package com.zerotoship.z2term.automation

import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.widget.EditText

/**
 * 操作マクロの色分け。種類は 3 つだけ: コメント=控えめの文字色 / 流れの語 (行頭の repeat if else end call)=アクセント色 /
 * その他=通常の文字色 (何も付けない)。判定はコメントの位置も含めて [ActionDefinition] の解析と同じものを使う。
 */
internal object ActionSyntaxColors {
    private class Mark(color: Int) : ForegroundColorSpan(color)

    /** [text] の中の色の範囲。(開始, 終了, コメントなら true／流れの語なら false) */
    fun ranges(text: CharSequence): List<Triple<Int, Int, Boolean>> {
        val result = mutableListOf<Triple<Int, Int, Boolean>>()
        var offset = 0
        text.toString().split('\n').forEach { line ->
            val code = line.trimEnd('\r')
            val comment = ActionDefinition.commentStart(code)
            val body = comment?.let { code.substring(0, it) } ?: code
            val indent = body.length - body.trimStart().length
            val word = body.substring(indent).takeWhile { !it.isWhitespace() }
            if (word in ActionDefinition.FLOW_WORDS) result += Triple(offset + indent, offset + indent + word.length, false)
            if (comment != null) result += Triple(offset + comment, offset + code.length, true)
            offset += line.length + 1
        }
        return result
    }

    fun apply(text: Spannable) {
        text.getSpans(0, text.length, Mark::class.java).forEach(text::removeSpan)
        ranges(text).forEach { (start, end, comment) ->
            text.setSpan(Mark(if (comment) ActionUi.mutedColor else ActionUi.accentColor), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun colored(text: String): CharSequence = SpannableString(text).also(::apply)

    /** 打つたびに塗り直す。64 KiB までの全文でも 1 回の走査で済む。 */
    fun attach(field: EditText) {
        apply(field.text)
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) = apply(s)
        })
    }
}
