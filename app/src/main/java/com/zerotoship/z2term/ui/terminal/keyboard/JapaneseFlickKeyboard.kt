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
 *
 * 両端の列 (ESC/◀/カナ/ABC と ⌫/▶/␣/⏎) は [JP_EDGE_WEIGHT] で幅を狭め、
 * 中央 3 列のかな (フリック) を広く取って打ちやすくしている。
 */
// 両端 (機能キー) 列の幅。中央のかな列 (1f) より狭くする。
private const val JP_EDGE_WEIGHT = 0.7f

@Composable
fun JapaneseFlickKeyboard(
    onBytes: (ByteArray) -> Unit,
    onCursorKey: (TerminalEmulator.CursorKey) -> Unit,
    onSwitchToAscii: () -> Unit,
    composing: ComposingState,
    style: KeyboardStyle,
    modifier: Modifier = Modifier
) {
    // 入力中のひらがなを確定して PTY へ流す (composing が空なら何もしない)。
    fun flush() { composing.commitRaw() }

    // かなは composing に積む (変換前バッファ)。予測候補が随時更新される。
    fun emitKana(hira: Char) { composing.append(hira) }

    // 記号は確定 → そのまま送出 (変換対象外)。
    fun emitPlain(ch: Char) {
        flush()
        onBytes(ch.toString().toByteArray(Charsets.UTF_8))
    }

    // 「小゛゜」: composing 末尾のかなを 濁点→半濁点→小書き→元 の順に循環。
    fun cycleDakuten() {
        val cur = composing.text.lastOrNull() ?: return
        val (forms, idx) = CYCLE_INDEX[cur] ?: return
        composing.replaceLast(forms[(idx + 1) % forms.size])
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
            JpKey("ESC", style, fontScale = 0.7f, weight = JP_EDGE_WEIGHT) {
                if (composing.isActive) composing.reset() else onBytes(byteArrayOf(0x1B))
            }
            JpFlickKey(KANA_A, style, ::emitKana)
            JpFlickKey(KANA_KA, style, ::emitKana)
            JpFlickKey(KANA_SA, style, ::emitKana)
            JpKey("⌫", style, repeatable = true, weight = JP_EDGE_WEIGHT) {
                if (!composing.backspace()) onBytes(byteArrayOf(0x7F))
            }
        }
        // Row 2: ◀  た  な  は  ▶
        JpRow(rowSpacing) {
            JpKey("◀", style, repeatable = true, weight = JP_EDGE_WEIGHT) { flush(); onCursorKey(TerminalEmulator.CursorKey.LEFT) }
            JpFlickKey(KANA_TA, style, ::emitKana)
            JpFlickKey(KANA_NA, style, ::emitKana)
            JpFlickKey(KANA_HA, style, ::emitKana)
            JpKey("▶", style, repeatable = true, weight = JP_EDGE_WEIGHT) { flush(); onCursorKey(TerminalEmulator.CursorKey.RIGHT) }
        }
        // Row 3: ␣  ま  や  ら  変換
        JpRow(rowSpacing) {
            JpKey("␣", style, repeatable = true, weight = JP_EDGE_WEIGHT) { flush(); onBytes(byteArrayOf(0x20)) }
            JpFlickKey(KANA_MA, style, ::emitKana)
            JpFlickKey(KANA_YA, style, ::emitKana)
            JpFlickKey(KANA_RA, style, ::emitKana)
            JpKey("変換", style, fontScale = 0.65f, accent = composing.isActive, weight = JP_EDGE_WEIGHT) {
                composing.convert()
            }
        }
        // Row 4: ABC(英字へ)  小゛゜  わ  、。  ⏎
        JpRow(rowSpacing) {
            JpKey("ABC", style, fontScale = 0.7f, accent = true, weight = JP_EDGE_WEIGHT) { flush(); onSwitchToAscii() }
            JpKey("小゛゜", style, fontScale = 0.6f) { cycleDakuten() }
            JpFlickKey(KANA_WA, style, ::emitKana)
            JpFlickKey(PUNCT, style, ::emitPlain)
            JpKey("⏎", style, weight = JP_EDGE_WEIGHT) {
                if (!composing.commitRaw()) onBytes(byteArrayOf(0x0D))
            }
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
    weight: Float = 1f,
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
            .weight(weight)
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
        // 中央のかな
        Text(
            text = km.center.toString(),
            color = ZtsTextPrimary,
            fontSize = style.keyFontSp.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Center)
        )
        // フリックヒント (灰色、四隅/端)
        km.up?.let { Hint(it, style, Modifier.align(Alignment.TopCenter)) }
        km.down?.let { Hint(it, style, Modifier.align(Alignment.BottomCenter)) }
        km.left?.let { Hint(it, style, Modifier.align(Alignment.CenterStart).padding(start = 2.dp)) }
        km.right?.let { Hint(it, style, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp)) }
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
 * 順番は base → 小書き → 濁点 → 半濁点。
 * 例: つ → っ → づ → つ、う → ぅ → ゔ → う、は → ば → ぱ → は。
 */
private val CYCLE_GROUPS: List<List<Char>> = listOf(
    listOf('あ', 'ぁ'), listOf('い', 'ぃ'), listOf('う', 'ぅ', 'ゔ'), listOf('え', 'ぇ'), listOf('お', 'ぉ'),
    listOf('か', 'が'), listOf('き', 'ぎ'), listOf('く', 'ぐ'), listOf('け', 'げ'), listOf('こ', 'ご'),
    listOf('さ', 'ざ'), listOf('し', 'じ'), listOf('す', 'ず'), listOf('せ', 'ぜ'), listOf('そ', 'ぞ'),
    listOf('た', 'だ'), listOf('ち', 'ぢ'), listOf('つ', 'っ', 'づ'), listOf('て', 'で'), listOf('と', 'ど'),
    listOf('は', 'ば', 'ぱ'), listOf('ひ', 'び', 'ぴ'), listOf('ふ', 'ぶ', 'ぷ'), listOf('へ', 'べ', 'ぺ'), listOf('ほ', 'ぼ', 'ぽ'),
    listOf('や', 'ゃ'), listOf('ゆ', 'ゅ'), listOf('よ', 'ょ'), listOf('わ', 'ゎ')
)

/** char → (グループ, そのグループ内 index)。濁点キーの循環に使う。 */
private val CYCLE_INDEX: Map<Char, Pair<List<Char>, Int>> = buildMap {
    for (group in CYCLE_GROUPS) {
        for ((i, c) in group.withIndex()) put(c, group to i)
    }
}
