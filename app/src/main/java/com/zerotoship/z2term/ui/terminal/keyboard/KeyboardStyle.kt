package com.zerotoship.z2term.ui.terminal.keyboard

import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.R

/**
 * Z2Term 独自キーボードの見た目・挙動プリセット。
 *
 * 各スタイルはキー高さ・フォントサイズ・フリック方向数 (1 or 4) を持つ。
 * [displayNameRes] は言語追従用の文字列リソース。
 */
data class KeyboardStyle(
    val id: String,
    @StringRes val displayNameRes: Int,
    /** 普通キーの高さ */
    val keyHeight: Dp,
    /** 通常ラベルのフォントサイズ (sp) */
    val keyFontSp: Float,
    /** フリックヒントのフォントサイズ (sp) */
    val flickHintFontSp: Float,
    /** Row 2-4 で 4 方向フリック (true) か 上方向のみ (false) か */
    val fourDirectionFlick: Boolean,
    /**
     * 5 行構成全体の自然高さ (TerminalScreen がキーボード領域を
     * 自動拡張する基準値)。5 * keyHeight + 4 行間スペース + 上下 padding。
     */
    val naturalHeight: Dp
) {
    companion object {
        val COMPACT = KeyboardStyle(
            id = "compact",
            displayNameRes = R.string.keyboard_style_compact,
            keyHeight = 44.dp,
            keyFontSp = 14f,
            flickHintFontSp = 9f,
            fourDirectionFlick = false,
            // 5*44 + 4*3 + 8 ≒ 240
            naturalHeight = 240.dp
        )
        val SPACIOUS = KeyboardStyle(
            id = "spacious",
            displayNameRes = R.string.keyboard_style_spacious,
            keyHeight = 60.dp,
            keyFontSp = 19f,
            flickHintFontSp = 13f,
            fourDirectionFlick = true,
            // 5*60 + 4*4 + 8 ≒ 324
            naturalHeight = 324.dp
        )
        val ALL = listOf(COMPACT, SPACIOUS)
        fun byId(id: String): KeyboardStyle = ALL.firstOrNull { it.id == id } ?: COMPACT
    }
}

/** ⇧ キーの 3 状態。OFF → ONESHOT → LOCKED → OFF の順に循環。 */
enum class ShiftState { OFF, ONESHOT, LOCKED }

/** 4 方向フリックの割り当て (null は割り当て無し)。 */
data class FlickMap(
    val up: Char? = null,
    val down: Char? = null,
    val left: Char? = null,
    val right: Char? = null
)
