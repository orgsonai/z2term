package com.zerotoship.z2term.ui.terminal.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 内蔵の日本語フリックキーボード (ひらがな直接入力)。標準的な 12 キー配列。
 *
 * OS IME に頼らず、端末へ直接ひらがな (UTF-8) を送る。漢字変換は行わない
 * (辞書エンジンが必要なため範囲外) が、ファイル名やコメント等のかな入力には十分。
 *
 * フリック規約 (一般的な日本語 12 キーと同じ):
 *   タップ = あ段 / 左 = い段 / 上 = う段 / 右 = え段 / 下 = お段
 * 各かなキーには 4 方向のフリック先を小さく表示する (見本の市販 IME と同様)。
 *
 * 「小゛゜」キーは **直前に入力したかな** を 濁点→半濁点→小書き→元 の順に
 * 循環させる (端末へ DEL を送ってから変換後の文字を送り直す)。
 *
 * 「カナ」キーで かな ⇄ カタカナ を切替える (カタカナモードでは出力も表示も
 * カタカナになる)。「ABC」で英字 (QWERTY) キーボードへ戻る。
 *
 * 配列 (5 列 × 4 行、画面高さを充填):
 *   ESC  あ   か  さ   ⌫
 *   ◀   た   な  は   ▶
 *   カナ ま   や  ら   ␣
 *   ABC  小゛゜ わ  、。  ⏎
 */
@Composable
fun JapaneseFlickKeyboard(
    onBytes: (ByteArray) -> Unit,
    onCursorKey: (TerminalEmulator.CursorKey) -> Unit,
    onSwitchToAscii: () -> Unit,
    style: KeyboardStyle,
    modifier: Modifier = Modifier
) {
    // 直前に確定したかな (濁点キーの変換対象)。フリック/タップで更新される。
    var lastKana by remember { mutableStateOf<Char?>(null) }
    // カタカナモード (ON のとき、かなをカタカナで出力・表示する)。
    var katakana by remember { mutableStateOf(false) }

    // フリックマップはひらがなで定義されているので、出力時にモードに応じて変換する。
    fun emitKana(baseHira: Char) {
        val out = toKana(baseHira, katakana)
        onBytes(out.toString().toByteArray(Charsets.UTF_8))
        lastKana = out
    }

    fun emitPlain(ch: Char) {
        onBytes(ch.toString().toByteArray(Charsets.UTF_8))
        lastKana = null  // 記号は濁点変換の対象外
    }

    // 「小゛゜」: 直前のかなを次の形へ。端末へ DEL + 変換後を送る。
    // 循環表はひらがな基準なので、カタカナの場合は一旦ひらがなへ戻して引く。
    fun cycleDakuten() {
        val cur = lastKana ?: return
        val (forms, idx) = CYCLE_INDEX[toHira(cur)] ?: return
        val nextHira = forms[(idx + 1) % forms.size]
        val next = toKana(nextHira, katakana)
        onBytes(byteArrayOf(0x7F) + next.toString().toByteArray(Charsets.UTF_8))
        lastKana = next
    }

    val rowSpacing = if (style.keyHeight >= 56.dp) 4.dp else 3.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZtsBgSecondary)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        // Row 1: ESC  あ  か  さ  ⌫
        JpRow(rowSpacing) {
            JpKey("ESC", style, fontScale = 0.7f) { onBytes(byteArrayOf(0x1B)); lastKana = null }
            JpFlickKey(KANA_A, style, katakana, ::emitKana)
            JpFlickKey(KANA_KA, style, katakana, ::emitKana)
            JpFlickKey(KANA_SA, style, katakana, ::emitKana)
            JpKey("⌫", style, repeatable = true) { onBytes(byteArrayOf(0x7F)); lastKana = null }
        }
        // Row 2: ◀  た  な  は  ▶
        JpRow(rowSpacing) {
            JpKey("◀", style, repeatable = true) { onCursorKey(TerminalEmulator.CursorKey.LEFT); lastKana = null }
            JpFlickKey(KANA_TA, style, katakana, ::emitKana)
            JpFlickKey(KANA_NA, style, katakana, ::emitKana)
            JpFlickKey(KANA_HA, style, katakana, ::emitKana)
            JpKey("▶", style, repeatable = true) { onCursorKey(TerminalEmulator.CursorKey.RIGHT); lastKana = null }
        }
        // Row 3: カナ(かな⇄カタカナ切替)  ま  や  ら  ␣
        JpRow(rowSpacing) {
            JpKey(if (katakana) "かな" else "カナ", style, fontScale = 0.7f, accent = katakana) {
                katakana = !katakana
            }
            JpFlickKey(KANA_MA, style, katakana, ::emitKana)
            JpFlickKey(KANA_YA, style, katakana, ::emitKana)
            JpFlickKey(KANA_RA, style, katakana, ::emitKana)
            JpKey("␣", style, repeatable = true) { onBytes(byteArrayOf(0x20)); lastKana = null }
        }
        // Row 4: ABC(英字へ)  小゛゜  わ  、。  ⏎
        JpRow(rowSpacing) {
            JpKey("ABC", style, fontScale = 0.7f, accent = true, onClick = onSwitchToAscii)
            JpKey("小゛゜", style, fontScale = 0.6f) { cycleDakuten() }
            JpFlickKey(KANA_WA, style, katakana, ::emitKana)
            JpFlickKey(PUNCT, style, katakana, ::emitPlain)
            JpKey("⏎", style) { onBytes(byteArrayOf(0x0D)); lastKana = null }
        }
    }
}

@Composable
private fun ColumnScope.JpRow(spacing: androidx.compose.ui.unit.Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/** タップ専用 (任意で長押し連打) の機能キー。 */
@Composable
private fun RowScope.JpKey(
    label: String,
    style: KeyboardStyle,
    fontScale: Float = 1f,
    accent: Boolean = false,
    repeatable: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (accent) ZtsGreen else ZtsBgCard
    val fg = if (accent) Color.Black else ZtsTextPrimary
    val border = if (accent) ZtsGreen else ZtsBorder
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val tap = if (repeatable) {
        Modifier.pointerInput(Unit) { detectTapWithRepeat(scope) { currentOnClick() } }
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .then(tap),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = (style.keyFontSp * fontScale).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * かな用 4 方向フリックキー。中央=タップ、左/上/右/下 で い/う/え/お 段。
 * 4 方向のフリック先を四隅(端)に小さく表示する (市販 IME と同様の見た目)。
 */
@Composable
private fun RowScope.JpFlickKey(
    km: KanaFlick,
    style: KeyboardStyle,
    katakana: Boolean,
    onEmit: (Char) -> Unit
) {
    val currentOnEmit by rememberUpdatedState(onEmit)
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .pointerInput(km) {
                val flickThreshold = viewConfiguration.touchSlop * 1.4f
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var resolved = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (!resolved && (abs(dx) > flickThreshold || abs(dy) > flickThreshold)) {
                                val ch = if (abs(dx) > abs(dy)) {
                                    if (dx < 0) km.left else km.right
                                } else {
                                    if (dy < 0) km.up else km.down
                                }
                                resolved = true
                                currentOnEmit(ch ?: km.center)
                                change.consume()
                            }
                            if (!change.pressed) {
                                if (!resolved) currentOnEmit(km.center)
                                break
                            }
                        }
                    }
                }
            }
    ) {
        // 中央のかな (カタカナモードならカタカナ表示)
        Text(
            text = toKana(km.center, katakana).toString(),
            color = ZtsTextPrimary,
            fontSize = style.keyFontSp.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Center)
        )
        // フリックヒント (灰色、四隅/端。カタカナモードならカタカナ表示)
        km.up?.let { Hint(toKana(it, katakana), style, Modifier.align(Alignment.TopCenter)) }
        km.down?.let { Hint(toKana(it, katakana), style, Modifier.align(Alignment.BottomCenter)) }
        km.left?.let { Hint(toKana(it, katakana), style, Modifier.align(Alignment.CenterStart).padding(start = 2.dp)) }
        km.right?.let { Hint(toKana(it, katakana), style, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp)) }
    }
}

@Composable
private fun Hint(ch: Char, style: KeyboardStyle, modifier: Modifier) {
    Text(
        text = ch.toString(),
        color = ZtsTextSecondary,
        fontSize = (style.flickHintFontSp + 1f).sp,
        lineHeight = (style.flickHintFontSp + 1f).sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

// ひらがな (U+3041..U+3096) ⇄ カタカナ (U+30A1..U+30F6) は +0x60 のオフセット。
// 「ー」「〜」や記号 (、。？！「」…) は範囲外なので変換しない。
private fun toKana(ch: Char, katakana: Boolean): Char =
    if (katakana && ch in 'ぁ'..'ゖ') ch + 0x60 else ch

private fun toHira(ch: Char): Char =
    if (ch in 'ァ'..'ヶ') ch - 0x60 else ch

/** かなフリック割り当て (中央=タップ、なければ中央を送る)。 */
private data class KanaFlick(
    val center: Char,
    val left: Char? = null,
    val up: Char? = null,
    val right: Char? = null,
    val down: Char? = null
)

// タップ=あ段 / 左=い段 / 上=う段 / 右=え段 / 下=お段
private val KANA_A = KanaFlick('あ', 'い', 'う', 'え', 'お')
private val KANA_KA = KanaFlick('か', 'き', 'く', 'け', 'こ')
private val KANA_SA = KanaFlick('さ', 'し', 'す', 'せ', 'そ')
private val KANA_TA = KanaFlick('た', 'ち', 'つ', 'て', 'と')
private val KANA_NA = KanaFlick('な', 'に', 'ぬ', 'ね', 'の')
private val KANA_HA = KanaFlick('は', 'ひ', 'ふ', 'へ', 'ほ')
private val KANA_MA = KanaFlick('ま', 'み', 'む', 'め', 'も')
private val KANA_YA = KanaFlick('や', '「', 'ゆ', '」', 'よ')
private val KANA_RA = KanaFlick('ら', 'り', 'る', 'れ', 'ろ')
private val KANA_WA = KanaFlick('わ', 'を', 'ん', 'ー', '〜')
// 記号キー: 、。？！…
private val PUNCT = KanaFlick('、', '。', '？', '！', '…')

/**
 * 濁点/半濁点/小書きの循環グループ。各かなを「次の形」へ回す。
 * 例: は → ば → ぱ → は、つ → づ → っ → つ。
 */
private val CYCLE_GROUPS: List<List<Char>> = listOf(
    listOf('あ', 'ぁ'), listOf('い', 'ぃ'), listOf('う', 'ゔ', 'ぅ'), listOf('え', 'ぇ'), listOf('お', 'ぉ'),
    listOf('か', 'が'), listOf('き', 'ぎ'), listOf('く', 'ぐ'), listOf('け', 'げ'), listOf('こ', 'ご'),
    listOf('さ', 'ざ'), listOf('し', 'じ'), listOf('す', 'ず'), listOf('せ', 'ぜ'), listOf('そ', 'ぞ'),
    listOf('た', 'だ'), listOf('ち', 'ぢ'), listOf('つ', 'づ', 'っ'), listOf('て', 'で'), listOf('と', 'ど'),
    listOf('は', 'ば', 'ぱ'), listOf('ひ', 'び', 'ぴ'), listOf('ふ', 'ぶ', 'ぷ'), listOf('へ', 'べ', 'ぺ'), listOf('ほ', 'ぼ', 'ぽ'),
    listOf('や', 'ゃ'), listOf('ゆ', 'ゅ'), listOf('よ', 'ょ'), listOf('わ', 'ゎ')
)

/** char → (グループ, そのグループ内 index)。濁点キーの循環に使う。 */
private val CYCLE_INDEX: Map<Char, Pair<List<Char>, Int>> = buildMap {
    for (group in CYCLE_GROUPS) {
        for ((i, c) in group.withIndex()) put(c, group to i)
    }
}
