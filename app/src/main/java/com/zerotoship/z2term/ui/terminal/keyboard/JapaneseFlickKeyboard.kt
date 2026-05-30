package com.zerotoship.z2term.ui.terminal.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsGreenBright
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 内蔵の日本語フリックキーボード (ひらがな直接入力)。標準的な 12 キー配列。
 *
 * OS IME に頼らず、端末へ直接ひらがな (UTF-8) を送る。「変換」キーで
 * かな漢字変換 (スプリット変換 + 候補サイクル) に入り、◀ / ▶ で範囲調整、
 * 候補をタップ or `⏎` で確定すると次のブロックへ自動でフォーカスが移る。
 * composing が空のときに `変換` を押すと、直前 commit を呼び戻す **再変換** が走る
 * (端末側に DEL バイトを送って確定済みテキストを消し、composing に戻して再度変換)。
 *
 * フリック規約 (一般的な日本語 12 キーと同じ):
 *   タップ = あ段 / 左 = い段 / 上 = う段 / 右 = え段 / 下 = お段
 * 押下中はキーの真上に四角いポップアップが浮き、今選ばれているかなを大きく示す。
 * 同じかなを連打したときは循環させず素直に重ねる (「つつ」が「っ」にならないように)。
 *
 * 「小゛゜」キーは **直前に入力したかな** を 濁点→半濁点→小書き→元 の順に
 * 循環させる (小書き・濁点はこのキーで付ける)。
 *
 * 配列 (5 列 × 4 行、画面高さを充填):
 *   ESC  あ   か  さ   ⌫
 *   ◀   た   な  は   ▶
 *   ␣    ま   や  ら   変換
 *   ABC  小゛゜ わ  、。  ⏎
 *
 * 両端の列 (ESC/◀/␣/ABC と ⌫/▶/変換/⏎) は [JP_EDGE_WEIGHT] で幅を狭め、
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

    // かなは composing に積む (連打サイクル含む)。予測候補が随時更新される。
    fun emitKana(hira: Char) { composing.emitKana(hira) }

    // 記号 (、。？！…): 入力中のかなを **強制確定しない** で、composing に積むだけ。
    //   - composing が空のときだけ直接 PTY へ送る (= 1 タップで完結)。
    //   - composing がある場合は append して未確定のまま続行できる。
    //     例: 「わたしは、がっこうへ」と打って ⏎ や 変換 でまとめて変換確定する流れに対応。
    fun emitPlain(ch: Char) {
        if (composing.isActive) {
            composing.append(ch)
        } else {
            onBytes(ch.toString().toByteArray(Charsets.UTF_8))
        }
    }

    // 「小゛゜」: composing 末尾のかなを 濁点→半濁点→小書き→元 の順に循環。
    //   連打サイクル ([ComposingState.emitKana]) のフォールバック (フリック後や空打ち後)。
    fun cycleDakuten() {
        val cur = composing.text.lastOrNull() ?: return
        val (forms, idx) = CYCLE_INDEX[cur] ?: return
        composing.replaceLast(forms[(idx + 1) % forms.size])
    }

    // 変換キーの挙動:
    //   1. composing が空 ∧ 直前 commit が残っている → **再変換**:
    //      端末側へ 0x7F を直前出力のコードポイント数だけ送って消し、composing を復元 → convert。
    //   2. composing が active → 通常の [ComposingState.convert]
    //      (1 回目: スプリット起動 / 2 回目以降: 候補サイクル)。
    //   3. それ以外 (空 ∧ 直前なし) → 何もしない。
    fun handleConvert() {
        if (composing.isActive) {
            composing.convert()
            return
        }
        if (composing.canReconvert) {
            val toErase = composing.restoreLastCommit()
            if (toErase > 0) {
                // 端末読み出し側 (シェル readline 等) は 0x7F を「1 文字削除」として扱う想定。
                // UTF-8 マルチバイト文字も bash/zsh の readline では 1 押下 = 1 コードポイント削除になる。
                // 生 PTY モード (vim 等) では削除されないことがある (= 再変換は実質シェル文脈のみ)。
                onBytes(ByteArray(toErase) { 0x7F.toByte() })
            }
            composing.convert()
        }
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
            // ⌫: タップ=1文字削除、長押し連打、左フリック=単語削除(Ctrl+W)、
            //     右フリック=全削除(Ctrl+U)。英字キーボードの BackspaceKey と挙動を統一。
            JpBackspaceKey(
                style = style,
                weight = JP_EDGE_WEIGHT,
                onTap = { if (!composing.backspace()) onBytes(byteArrayOf(0x7F)) },
                onFlickLeft = { if (composing.isActive) composing.reset() else onBytes(byteArrayOf(0x17)) },
                onFlickRight = { if (composing.isActive) composing.reset() else onBytes(byteArrayOf(0x15)) }
            )
        }
        // Row 2: ◀  た  な  は  ▶
        //   スプリット変換中は ◀ ▶ をフォーカス範囲調整に流用 (左 = 縮める / 右 = 広げる)。
        //   composing 中で未スプリットなら、◀ ▶ は **変換キーを押したのと同じ** 扱いで
        //   スプリット変換へ突入する (ユーザー要望: 変換を押さず左右でブロック範囲変更に入る)。
        //   composing が空のときだけ従来どおりカーソルキー送信。
        JpRow(rowSpacing) {
            JpKey("◀", style, repeatable = true, accent = composing.isActive, weight = JP_EDGE_WEIGHT) {
                when {
                    composing.isSplitMode -> composing.shrinkSplitHead()
                    composing.isActive -> composing.convert()   // 変換キー相当: スプリット突入
                    else -> { flush(); onCursorKey(TerminalEmulator.CursorKey.LEFT) }
                }
            }
            JpFlickKey(KANA_TA, style, ::emitKana)
            JpFlickKey(KANA_NA, style, ::emitKana)
            JpFlickKey(KANA_HA, style, ::emitKana)
            JpKey("▶", style, repeatable = true, accent = composing.isActive, weight = JP_EDGE_WEIGHT) {
                when {
                    composing.isSplitMode -> composing.extendSplitHead()
                    composing.isActive -> composing.convert()   // 変換キー相当: スプリット突入
                    else -> { flush(); onCursorKey(TerminalEmulator.CursorKey.RIGHT) }
                }
            }
        }
        // Row 3: ␣  ま  や  ら  変換
        //   ␣ も composing がある間は **強制確定しない** で空白を append (記号と同じ方針)。
        JpRow(rowSpacing) {
            JpKey("␣", style, repeatable = true, weight = JP_EDGE_WEIGHT) {
                if (composing.isActive) composing.append(' ')
                else onBytes(byteArrayOf(0x20))
            }
            JpFlickKey(KANA_MA, style, ::emitKana)
            JpFlickKey(KANA_YA, style, ::emitKana)
            JpFlickKey(KANA_RA, style, ::emitKana)
            // 変換キー: composing 空 ∧ 再変換可なら label を「再変換」に切替えてユーザーへ示す。
            val convertLabel = if (!composing.isActive && composing.canReconvert) "再変換" else "変換"
            val convertAccent = composing.isActive || composing.canReconvert
            JpKey(convertLabel, style, fontScale = 0.65f, accent = convertAccent, weight = JP_EDGE_WEIGHT) {
                handleConvert()
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
    var pressed by remember { mutableStateOf(false) }
    val bg = when {
        pressed -> ZtsGreenBright
        accent -> ZtsGreen
        else -> ZtsBgCard
    }
    val fg = if (accent || pressed) Color.Black else ZtsTextPrimary
    val border = if (accent || pressed) ZtsGreen else ZtsBorder
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val tap = if (repeatable) {
        Modifier.pointerInput(Unit) {
            detectTapWithRepeat(scope, onPressedChange = { pressed = it }) { currentOnClick() }
        }
    } else {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    var fired = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) break
                        if (!change.pressed) { fired = true; break }
                    }
                    pressed = false
                    if (fired) currentOnClick()
                }
            }
        }
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
 * 日本語キーボードの ⌫ 専用キー (英字キーボードの BackspaceKey と挙動を統一)。
 * タップ=単発削除、長押し=連打 (500ms 後 60ms 間隔)、
 * 左フリック=[onFlickLeft] (単語削除 Ctrl+W)、右フリック=[onFlickRight] (全削除 Ctrl+U)。
 */
@Composable
private fun RowScope.JpBackspaceKey(
    style: KeyboardStyle,
    weight: Float,
    onTap: () -> Unit,
    onFlickLeft: () -> Unit,
    onFlickRight: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnFlickLeft by rememberUpdatedState(onFlickLeft)
    val currentOnFlickRight by rememberUpdatedState(onFlickRight)
    val bg = if (pressed) ZtsGreenBright else ZtsBgCard
    val fg = if (pressed) Color.Black else ZtsTextPrimary
    val border = if (pressed) ZtsGreen else ZtsBorder
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                val flickThreshold = viewConfiguration.touchSlop * 1.4f
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        val startX = down.position.x
                        val startY = down.position.y
                        var resolved = false       // フリック発火済み
                        var repeatStarted = false  // 長押し連打開始
                        var repeatJob: Job? = null

                        repeatJob = scope.launch {
                            delay(500)
                            if (!resolved) {
                                repeatStarted = true
                                currentOnTap()
                                while (isActive) {
                                    delay(60)
                                    currentOnTap()
                                }
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (!resolved && !repeatStarted &&
                                abs(dx) > flickThreshold && abs(dx) > abs(dy)
                            ) {
                                resolved = true
                                repeatJob.cancel()
                                if (dx < 0) currentOnFlickLeft() else currentOnFlickRight()
                                change.consume()
                            }
                            if (!change.pressed) {
                                repeatJob.cancel()
                                if (!resolved && !repeatStarted) currentOnTap()
                                break
                            }
                        }
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⌫",
            color = fg,
            fontSize = style.keyFontSp.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * かな用 4 方向フリックキー。中央=タップ、左/上/右/下 で い/う/え/お 段。
 *
 * キー本体には中央のかなだけを表示する (フリック先文字はキー上には出さない)。
 * 押下中はキーの真上に正方形のポップアップが浮き、5 マス (中央+4 方向) に
 * 各かなを並べる。今フリック中の方向のマスが緑でハイライトされ、どの文字が
 * 確定対象かが一目でわかる。指を離すとそのマスのかなを [onEmit] へ送る。
 */
@Composable
private fun RowScope.JpFlickKey(
    km: KanaFlick,
    style: KeyboardStyle,
    onEmit: (Char) -> Unit
) {
    val currentOnEmit by rememberUpdatedState(onEmit)
    var pressed by remember { mutableStateOf(false) }
    var flickPreview by remember { mutableStateOf<Char?>(null) }
    val bg = if (pressed) ZtsGreenBright else ZtsBgCard
    val fg = if (pressed) Color.Black else ZtsTextPrimary
    val border = if (pressed) ZtsGreen else ZtsBorder
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .pointerInput(km) {
                val flickThreshold = viewConfiguration.touchSlop * 1.4f
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        flickPreview = null
                        val startX = down.position.x
                        val startY = down.position.y
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            // プレビュー: 移動量に応じてフリック方向の文字を表示 (確定はしない)
                            if (abs(dx) > flickThreshold || abs(dy) > flickThreshold) {
                                val ch = if (abs(dx) > abs(dy)) {
                                    if (dx < 0) km.left else km.right
                                } else {
                                    if (dy < 0) km.up else km.down
                                }
                                flickPreview = ch
                            } else {
                                flickPreview = null
                            }
                            if (!change.pressed) {
                                val committed = flickPreview ?: km.center
                                pressed = false
                                flickPreview = null
                                currentOnEmit(committed)
                                break
                            }
                        }
                        pressed = false
                        flickPreview = null
                    }
                }
            }
    ) {
        // キー本体: フリック割り当ての全文字を「ミニ十字」で常時表示する (デフォルト表示)。
        //   中央 = タップ文字を大きく、上 / 下 / 左 / 右の各段を小さく周囲に並べる。
        //   (旧: 中央のかなだけ表示し、押下ポップアップで全方向を見せる構成。
        //    ユーザー要望でデフォルトとポップアップの役割を反転した。)
        val hintColor = fg.copy(alpha = 0.6f)
        Text(
            text = km.center.toString(),
            color = fg,
            fontSize = style.keyFontSp.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Center)
        )
        km.up?.let {
            Text(it.toString(), color = hintColor, fontSize = style.flickHintFontSp.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.TopCenter))
        }
        km.down?.let {
            Text(it.toString(), color = hintColor, fontSize = style.flickHintFontSp.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomCenter))
        }
        km.left?.let {
            Text(it.toString(), color = hintColor, fontSize = style.flickHintFontSp.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp))
        }
        km.right?.let {
            Text(it.toString(), color = hintColor, fontSize = style.flickHintFontSp.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
        }
        // 押下中: キー直上のポップアップに「今このまま離すと確定する 1 文字」だけを
        //   大きく表示する (緑地に黒文字でハイライト)。
        if (pressed) {
            FlickCommitPopup(ch = flickPreview ?: km.center, style = style)
        }
    }
}

/**
 * フリックキー押下時にキー直上へ浮かべる確定文字ポップアップ。
 *
 * 「今このまま指を離すと送出される 1 文字」だけを大きく表示する (緑地に黒文字)。
 * フリック方向を変えると [ch] が差し替わり、何が確定するか一目で分かる。
 * Popup を使うことでキー本体の境界を越えて画面上方へ描けるので、最上段のキーでも
 * 端末画面側に重ねて表示できる。
 */
@Composable
private fun FlickCommitPopup(
    ch: Char,
    style: KeyboardStyle
) {
    val density = LocalDensity.current
    // 1 文字を大きく見せる正方形 (キーフォント sp に比例)。
    val popupSize = (style.keyFontSp * 2.7f).dp
    val gap = 6.dp
    val offsetY = with(density) { -(popupSize + gap).roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, offsetY),
        properties = PopupProperties(focusable = false, clippingEnabled = true)
    ) {
        Box(
            modifier = Modifier
                .size(popupSize, popupSize)
                .clip(RoundedCornerShape(10.dp))
                .background(ZtsGreen)
                .border(2.dp, ZtsGreenBright, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ch.toString(),
                color = Color.Black,
                fontSize = (style.keyFontSp * 1.5f).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
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
// 記号キー: 、。?!…
private val PUNCT = KanaFlick('、', '。', '？', '！', '…')
