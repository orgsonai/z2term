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
    val naturalHeight: Dp,
    /** プリセットの段の数 ([naturalHeight] の内訳)。 */
    val rows: Int = 5,
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
        val scale = (naturalHeight.value / ref.naturalHeight.value).coerceIn(MIN_SCALE, 2.5f)
        val fontScale = fontScaleFor(scale)
        return copy(
            keyFontSp = ref.keyFontSp * fontScale,
            mainKeyFontSp = ref.mainKeyFontSp * fontScale,
            flickHintFontSp = ref.flickHintFontSp * fontScale
        )
    }

    /** 面ごとのスタイルを、設定されたキーボード総高さへ収める。 */
    fun scaledToHeight(targetHeight: Dp): KeyboardStyle {
        val scale = (targetHeight.value / naturalHeight.value).coerceIn(MIN_SCALE, 2.5f)
        val fontScale = fontScaleFor(scale)
        return copy(
            keyHeight = fittedKeyHeight(targetHeight),
            keyFontSp = keyFontSp * fontScale,
            mainKeyFontSp = mainKeyFontSp * fontScale,
            flickHintFontSp = flickHintFontSp * fontScale,
            naturalHeight = targetHeight,
        )
    }

    /**
     * [target] の席に [rows] 段がちょうど収まるキーの高さ (1.0.81)。
     *
     * ⛔ **高さの比 (target / naturalHeight) でキーだけを縮めない。** 段の間の隙間 (キーが 56dp 以上で 4dp、
     * 未満で 3dp) と上下の余白 8dp は縮まないので、比で縮めると小さい席ほどはみ出した (200dp で約 5dp、
     * 140dp では約 10dp。端末の画面にかぶる)。横画面の下限を 140dp へ下げたとき (利用者の要望) に直した。
     */
    private fun fittedKeyHeight(target: Dp): Dp {
        fun fit(spacing: Float) = (target.value - KEYBOARD_PADDING_DP - (rows - 1) * spacing) / rows
        val wide = fit(4f)
        return (if (wide >= 56f) wide else fit(3f)).coerceAtLeast(1f).dp
    }

    companion object {
        /** 上下の余白の合計 (TerminalKeyboard の vertical padding 4dp × 2)。 */
        private const val KEYBOARD_PADDING_DP = 8f
        /** 横画面の下限 140dp (= 4 方向フリックの 324dp の約 0.43 倍) まで縮められるようにする。 */
        private const val MIN_SCALE = 0.4f

        /**
         * キーの字の倍率。0.6 倍以上はこれまでどおり 0.85〜1.4 倍に収め (小さめの席でも字を読める大きさに保つ)、
         * それより小さい席 (横画面で 200dp 未満・1.0.81) だけ、キーに入るよう比例して小さくする。
         */
        private fun fontScaleFor(scale: Float): Float =
            if (scale >= 0.6f) scale.coerceIn(0.85f, 1.4f) else 0.85f * scale / 0.6f

        val COMPACT = KeyboardStyle(
            id = "compact",
            displayNameRes = R.string.keyboard_style_compact,
            keyHeight = 44.dp,
            keyFontSp = 14f,
            mainKeyFontSp = 20f,
            flickHintFontSp = 9f,
            fourDirectionFlick = false,
            // 6 行構成 (上に ESC/TAB/⇧/CTRL の特殊キーバー + 主 5 行): 6*44 + 5*3 + 8 ≒ 287
            naturalHeight = 287.dp,
            rows = 6,
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
