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
    /** 通常ラベルのフォントサイズ (sp) — ESC/TAB/⇧/矢印 等の機能キー */
    val keyFontSp: Float,
    /** 主キー (qwerty / 数字) のラベルフォントサイズ (sp)。機能キーより大きめにできる。 */
    val mainKeyFontSp: Float,
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
    /**
     * 12 キー系の面 (かな [KeyboardFace.KANA] / 数字 [KeyboardFace.NUMBER]) 用に、
     * **フォントサイズだけ** [SPACIOUS] 基準へ揃えたスタイル。
     *
     * 「シンプル / 4 方向フリック」どちらを選んでも文字の見やすさを揃える (ユーザー要望)。
     * シンプル ([COMPACT]) は 1 キーあたりの面積が広い 12 キー系では字が小さく見えていた。
     * ⚠ **高さは選択スタイルのまま**。キーボード高さ設定に応じた拡縮を保つため、
     * `naturalHeight` に入っている「目標総高さ」との比でフォントを同じようにスケールする。
     */
    fun forTwelveKeyFace(): KeyboardStyle {
        val ref = SPACIOUS
        val scale = (naturalHeight.value / ref.naturalHeight.value).coerceIn(0.6f, 2.5f)
        val fontScale = scale.coerceIn(0.85f, 1.4f)
        return copy(
            keyFontSp = ref.keyFontSp * fontScale,
            mainKeyFontSp = ref.mainKeyFontSp * fontScale,
            flickHintFontSp = ref.flickHintFontSp * fontScale
        )
    }

    companion object {
        val COMPACT = KeyboardStyle(
            id = "compact",
            displayNameRes = R.string.keyboard_style_compact,
            keyHeight = 44.dp,
            keyFontSp = 14f,
            mainKeyFontSp = 20f,
            flickHintFontSp = 9f,
            fourDirectionFlick = false,
            // 6 行構成 (上に ESC/TAB/⇧/CTRL の特殊キーバー + 主 5 行): 6*44 + 5*3 + 8 ≒ 287
            naturalHeight = 287.dp
        )
        val SPACIOUS = KeyboardStyle(
            id = "spacious",
            displayNameRes = R.string.keyboard_style_spacious,
            keyHeight = 60.dp,
            keyFontSp = 19f,
            mainKeyFontSp = 21f,
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
