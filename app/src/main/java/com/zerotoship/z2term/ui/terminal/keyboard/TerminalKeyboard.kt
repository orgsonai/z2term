package com.zerotoship.z2term.ui.terminal.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.ui.terminal.input.AndroidKeyMapper
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsGreenBright
import com.zerotoship.z2term.ui.theme.ZtsGreenDim
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Z2Term 独自キーボード。
 *
 * 主な仕様:
 *  - 面 ([KeyboardFace]) の入口。かな / 数字は別の Composable が描き、ここは英字面と
 *    面の巡回を持つ。最下段の左端が**面の切替キー**で、ラベルは**行き先の面** (あ / 12)。
 *    面が英字だけ (英語ロケール ∧ 数字面 OFF) のときは切替キーを出さず、その席は
 *    CTRL / 貼り付けの入口が使う (= 従来の英語レイアウトのまま)。
 *  - Shift は OFF / ONESHOT / LOCKED の 3 状態 (タップ毎に循環)
 *  - ⌫ 長押しで連打、左フリックで `Ctrl+W` (単語削除)、右フリックで `Ctrl+U` (行頭まで削除)
 *  - 各英字キー: スタイルに応じて 1 方向 (compact) or 4 方向 (spacious) フリック
 *
 * レイアウト (spacious / 日本語ロケール = 従来どおり):
 *   Row 1: ESC  1〜0 (or 記号)                              ⌫
 *   Row 2: TAB  q w e r t y u i o p
 *   Row 3: ⇧    a s d f g h j k l                            ⏎
 *   Row 4: CTRL z x c v b n m , . /
 *   Row 5: 面切替 ?#  ALT  SPACE                            ← ↓ ↑ →
 *   切替キーが無いときのみ ⇧/CTRL を 1 段下げ、Row 3 左を貼り付けの入口、Row 5 左を CTRL にする
 *   (切替キーが座らない分の縦 1 列を埋め、a 行頭の空きをなくす)。
 *
 * レイアウト (compact, 特殊キーを上に追い出して主キー幅を広く):
 *   Top  : [ ESC ][ TAB ][ ⇧ ][ CTRL ]
 *   Row 1: 1〜0                                              ⌫
 *   Row 2: q w e r t y u i o p
 *   Row 3: a s d f g h j k l                                 ⏎
 *   Row 4: z x c v b n m , . /
 *   Row 5: 面切替 / CTRL(切替キー無し)  ?#  ALT  SPACE       ← ↓ ↑ →
 *
 * 各英字キーの下フリック = そのローマ字の大文字 (ヒント非表示)。
 */
@Composable
fun TerminalKeyboard(
    onBytes: (ByteArray) -> Unit,
    onCursorKey: (TerminalEmulator.CursorKey) -> Unit,
    composing: ComposingState,
    style: KeyboardStyle = KeyboardStyle.COMPACT,
    /**
     * 日本語面 ([KeyboardFace.KANA]) を巡回に入れるか。English モードでは false で外す
     * (Locale=en のとき呼出し側で false を渡す)。
     */
    showJapaneseKeyboard: Boolean = true,
    /**
     * 面 ([KeyboardFace]) の巡回順。設定 (巡回順のプリセット + 数字面の有無) から
     * `KeyboardFace.orderFrom(...)` で組んで渡す。
     *
     * ⚠ 日本語面はここに入っていても [showJapaneseKeyboard] が false なら飛ばす。
     * 巡回は「**設定の順序 ∩ いま出せる面**」で回る ([KeyboardFace.available])。
     */
    faceOrder: List<KeyboardFace> = KeyboardFace.ORDER_ASCII_FIRST,
    /**
     * 開いたときの面。既定は英字面。
     *
     * ⚠ 面を覚えるかどうかは**呼出し側が決める**。端末画面は常に英字面から始め、
     * OS の入力メソッド ([com.zerotoship.z2term.ime.Z2ImeService]) だけが前回の面を渡す —
     * 端末では英字で打ち始めることが多く、他アプリでは日本語で打ち始めることが多いため。
     */
    initialFace: KeyboardFace = KeyboardFace.ASCII,
    /** 切替キーで面が変わったときの通知。面を永続化する呼出し側だけが受ける。 */
    onFaceChange: (KeyboardFace) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var shift by remember { mutableStateOf(ShiftState.OFF) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var sym by remember { mutableStateOf(false) }
    // いま回せる面。⚠ 日本語面はアプリの言語が日本語のときだけ、数字面は設定 ON のときだけ
    // (= 呼出し側が [faceOrder] から外している)。英字面は必ず残る。
    val faces = KeyboardFace.available(faceOrder, allowKana = showJapaneseKeyboard)

    // いま出している面。⚠ 始まりの面は [initialFace] (= 呼出し側が覚えている面) だが、
    // 巡回に無い面では開かない — 「あ」キーの出ない英語ロケールで日本語面から開いたり、
    // 設定で切った数字面が復元されたりするのは筋が通らない。
    var face by remember(initialFace, faces) {
        mutableStateOf(if (initialFace in faces) initialFace else KeyboardFace.ASCII)
    }
    // 開いているパッド (絵文字 / 貼り付け)。⚠ 入口は**面の切替キーがこの面に無いときだけ**出す。
    // 切替キーがあるときは、その席 (日本語面の「あ」の位置) を切替キーが使うため。
    // 入口はかな面 (ESC の上下フリック) と数字面 (😀 キー) にもある。
    var pad by remember { mutableStateOf(PadMode.NONE) }

    // 面が 2 つ以上あるなら、最下段の左端は面の切替キー (= 日本語面の「あ」の席)。
    // ⛔ 切替キーを新設しない — 面が増えても画面に見えるキーの数は変わらない。
    val hasFaceKey = faces.size > 1
    val nextFace = KeyboardFace.next(faces, face)

    // 面を移る。⚠ 打ちかけのかなは**先に確定**する (面をまたいで持ち越さない)。
    fun switchFace(to: KeyboardFace) {
        composing.commitRaw()
        face = to
        onFaceChange(to)
    }

    if (face == KeyboardFace.KANA) {
        JapaneseFlickKeyboard(
            onBytes = onBytes,
            onCursorKey = onCursorKey,
            onSwitchFace = { switchFace(nextFace) },
            switchLabel = nextFace.switchLabel,
            composing = composing,
            selectedStyle = style,
            modifier = modifier
        )
        return
    }

    if (face == KeyboardFace.NUMBER) {
        NumberKeyboard(
            onBytes = onBytes,
            onCursorKey = onCursorKey,
            onSwitchFace = { switchFace(nextFace) },
            switchLabel = nextFace.switchLabel,
            composing = composing,
            selectedStyle = style,
            modifier = modifier
        )
        return
    }

    fun cycleShift() {
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.ONESHOT
            ShiftState.ONESHOT -> ShiftState.LOCKED
            ShiftState.LOCKED -> ShiftState.OFF
        }
    }

    fun resetOneShotMods() {
        if (shift == ShiftState.ONESHOT) shift = ShiftState.OFF
        if (ctrl) ctrl = false
        if (alt) alt = false
    }

    fun emitChar(raw: Char) {
        val shifted = shift != ShiftState.OFF
        val effective = if (!sym && shifted && raw.isLetter()) raw.uppercaseChar() else raw
        val base = effective.toString().toByteArray(Charsets.UTF_8)
        val withCtrl = if (ctrl) {
            AndroidKeyMapper.controlByteFor(effective)?.let { byteArrayOf(it) } ?: base
        } else base
        val final = if (alt) byteArrayOf(0x1B) + withCtrl else withCtrl
        onBytes(final)
        resetOneShotMods()
    }

    fun emitFlick(raw: Char) {
        val base = raw.toString().toByteArray(Charsets.UTF_8)
        val final = if (alt) byteArrayOf(0x1B) + base else base
        onBytes(final)
        if (alt) alt = false
    }

    fun emitSpecial(bytes: ByteArray) {
        val final = if (alt) byteArrayOf(0x1B) + bytes else bytes
        onBytes(final)
        if (alt) alt = false
    }

    // パッドの開閉。⚠ 同じキーをもう一度押したら閉じる (日本語面と同じ約束)。
    fun togglePad(mode: PadMode) { pad = if (pad == mode) PadMode.NONE else mode }

    // 絵文字 / 貼り付けの出口。⚠ **確定と同じ経路**を通すこと — バイト列 (onBytes) で送ると、
    // OS の入力メソッドとして使っているとき改行が performEditorAction (1 行欄では検索実行) へ
    // 読み替えられてしまう。
    fun insertText(text: String) { composing.commitExternalText(text) }

    fun emitCursor(key: TerminalEmulator.CursorKey) {
        // ALT/META 押下中は ESC プレフィックスを付ける (Meta+矢印)。矢印そのもののバイト列は
        // DECCKM の状態で変わり端末側が組むため、ここでは ESC だけ先に送って続けて矢印を送る。
        // 以前は修飾が捨てられ、ALT+矢印がただの矢印になっていた。
        if (alt) onBytes(byteArrayOf(0x1B))
        onCursorKey(key)
        if (alt) alt = false
    }

    val r1Labels = if (sym) listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")")
                   else listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val r2Labels = if (sym) listOf("-", "_", "+", "=", "/", "\\", "[", "]", "{", "}")
                   else listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val r3Labels = if (sym) listOf("`", "~", "'", "\"", ";", ":", "<", ">", "|")
                   else listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val r4Labels = if (sym) listOf("?", "§", "°", "¥", "€", "£", "~", "…")
                   else listOf("z", "x", "c", "v", "b", "n", "m", ",", ".", "/")

    // 上フリック (1 方向用) — 各行 10 個
    val r2FlickUp = listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')')
    val r3FlickUp = listOf('-', '_', '+', '=', '|', '\\', '/', '[', ']')
    val r4FlickUp = listOf('`', '~', '\'', '"', '<', '>', '?', ':', ';', '{')

    // 4 方向フリック (spacious 用) — Row 2 の up/left/right に記号を割当。
    // down は下フリック=大文字 (flickFor で動的に上書き) のため未指定。
    val r2Flick4 = listOf(
        FlickMap(up = '!', left = '`', right = '~'),
        FlickMap(up = '@', left = '\'', right = '"'),
        FlickMap(up = '#', left = '(', right = ')'),
        FlickMap(up = '$', left = '[', right = ']'),
        FlickMap(up = '%', left = '{', right = '}'),
        FlickMap(up = '^', left = '<', right = '>'),
        FlickMap(up = '&', left = ':', right = ';'),
        FlickMap(up = '*', left = ',', right = '.'),
        FlickMap(up = '(', left = '/', right = '\\'),
        FlickMap(up = ')', left = '|', right = '?')
    )

    // 下フリック = そのキーのローマ字大文字 (英字キーのみ)。数字は廃止。
    fun downUpperOf(rowIdx: Int, colIdx: Int): Char? {
        val list = when (rowIdx) { 2 -> r2Labels; 3 -> r3Labels; 4 -> r4Labels; else -> return null }
        return list.getOrNull(colIdx)?.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()
    }

    fun flickFor(rowIdx: Int, colIdx: Int): FlickMap? {
        if (sym) return null
        val down = downUpperOf(rowIdx, colIdx)
        return when (rowIdx) {
            2 -> if (style.fourDirectionFlick) r2Flick4.getOrNull(colIdx)?.copy(down = down)
                 else r2FlickUp.getOrNull(colIdx)?.let { FlickMap(up = it, down = down) }
            3 -> {
                val up = r3FlickUp.getOrNull(colIdx)
                if (up == null && down == null) null else FlickMap(up = up, down = down)
            }
            4 -> {
                val up = r4FlickUp.getOrNull(colIdx)
                if (up == null && down == null) null else FlickMap(up = up, down = down)
            }
            else -> null
        }
    }

    val rowSpacing = if (style.keyHeight >= 56.dp) 4.dp else 3.dp
    val isCompact = style.id == "compact"
    val smallFont = (style.keyFontSp - 3f).coerceAtLeast(10f)

    if (pad != PadMode.NONE) {
        // パッド表示中: キーの面をまるごとパッドへ差し替え、**最下段だけ機能キーを残す**。
        // ⚠ 日本語面 ([JapaneseFlickKeyboard]) は両端の列を残せるが、こちらは 10 列あって
        // 縁が細いので、残すのは行単位にする。貼った直後に消す・改行するのは同じようにできる。
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ZtsBgSecondary)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            KeyboardPad(
                mode = pad,
                onMode = { pad = it },
                style = style,
                onInsert = ::insertText,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                // ⚠ 閉じるのはここか、入口キー ([PadKey]) をもう一度押すか。⌫ を「閉じる」に
                // 置き換えない (パッドを開いている間に文字を消せなくなる)。
                BasicKey("×", weight = 1.2f, fontSp = style.keyFontSp, active = true, style = style) {
                    pad = PadMode.NONE
                }
                BackspaceKey(
                    weight = 1.4f,
                    style = style,
                    onTap = { emitSpecial(byteArrayOf(0x7F)) },
                    onFlickLeft = { emitSpecial(byteArrayOf(0x17)) },
                    onFlickRight = { emitSpecial(byteArrayOf(0x15)) }
                )
                SpaceKey(weight = 3f, style = style) { emitChar(' ') }
                BasicKey("⏎", weight = 1.4f, fontSp = style.keyFontSp, repeatable = true, style = style) {
                    emitSpecial(byteArrayOf(0x0D))
                }
                BasicKey("←", weight = 1f, fontSp = style.keyFontSp, repeatable = true, style = style) {
                    emitCursor(TerminalEmulator.CursorKey.LEFT)
                }
                BasicKey("→", weight = 1f, fontSp = style.keyFontSp, repeatable = true, style = style) {
                    emitCursor(TerminalEmulator.CursorKey.RIGHT)
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        // Compact 限定: 特殊キー (ESC/TAB/⇧/CTRL) を主キー領域の上に追い出すバー。
        // 主行の左 1.4f 列を解放することで英字キーが少しずつ広くなる。
        if (isCompact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                BasicKey("ESC", weight = 1f, fontSp = smallFont, style = style) {
                    emitSpecial(byteArrayOf(0x1B))
                }
                BasicKey("TAB", weight = 1f, fontSp = smallFont, style = style) {
                    emitSpecial(byteArrayOf(0x09))
                }
                ShiftKey(weight = 1f, state = shift, style = style, onCycle = { cycleShift() })
                BasicKey(
                    label = "CTRL",
                    weight = 1f,
                    fontSp = smallFont,
                    active = ctrl,
                    style = style
                ) { ctrl = !ctrl }
            }
        }
        // Row 1: (spacious のみ ESC) + 数字行 + ⌫
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            if (!isCompact) {
                BasicKey("ESC", weight = 1.4f, fontSp = smallFont, style = style) {
                    emitSpecial(byteArrayOf(0x1B))
                }
            }
            r1Labels.forEach { s ->
                BasicKey(s, weight = 1f, fontSp = style.mainKeyFontSp, repeatable = true, style = style) { emitChar(s[0]) }
            }
            BackspaceKey(
                weight = 1.4f,
                style = style,
                onTap = { emitSpecial(byteArrayOf(0x7F)) },
                onFlickLeft = { emitSpecial(byteArrayOf(0x17)) },  // Ctrl+W: 単語削除
                onFlickRight = { emitSpecial(byteArrayOf(0x15)) }  // Ctrl+U: 行頭まで削除
            )
        }
        // Row 2: (spacious のみ TAB) + qwerty
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            if (!isCompact) {
                BasicKey("TAB", weight = 1.4f, fontSp = smallFont, style = style) {
                    emitSpecial(byteArrayOf(0x09))
                }
            }
            r2Labels.forEachIndexed { idx, s ->
                val display = if (!sym && shift != ShiftState.OFF && s[0].isLetter()) s.uppercase() else s
                FlickKey(
                    label = display,
                    flick = flickFor(2, idx),
                    weight = 1f,
                    style = style,
                    onTap = { emitChar(s[0]) },
                    onFlick = { ch -> emitFlick(ch) }
                )
            }
        }
        // Row 3: spacious 左端。最下段に面の切替キーがあるなら従来どおり ⇧ (配置は変えない)。
        //        切替キーが無いとき (英語ロケール ∧ 数字面 OFF) だけ ⇧ を Row 4 へ 1 段下げ、
        //        ここを貼り付け / 絵文字の入口にする — その面には他に入口が無いため。
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            if (!isCompact) {
                if (hasFaceKey) {
                    ShiftKey(weight = 1.4f, state = shift, style = style, onCycle = { cycleShift() })
                } else {
                    // 切替キーが無い面のみ: 旧 META キーを**貼り付け / 絵文字の入口**にする (要望)。
                    // ⚠ META は Row 5 の ALT と同じ修飾 (ESC プレフィックス) だったので、
                    // 潰しても ALT で代わりが利く。英字面には絵文字も貼り付けも入口が無かった。
                    PadKey(
                        weight = 1.4f,
                        style = style,
                        onTap = { togglePad(PadMode.CLIPBOARD) },
                        onFlickUp = { togglePad(PadMode.EMOJI) }
                    )
                }
            }
            r3Labels.forEachIndexed { idx, s ->
                val display = if (!sym && shift != ShiftState.OFF && s[0].isLetter()) s.uppercase() else s
                FlickKey(
                    label = display,
                    flick = flickFor(3, idx),
                    weight = 1f,
                    style = style,
                    onTap = { emitChar(s[0]) },
                    onFlick = { ch -> emitFlick(ch) }
                )
            }
            // ⏎ も長押しで連打できる (矢印・space・⌫ と同じ扱い・要望)。
            BasicKey("⏎", weight = 1.4f, fontSp = style.keyFontSp, repeatable = true, style = style) {
                emitSpecial(byteArrayOf(0x0D))
            }
        }
        // Row 4: spacious 左端。切替キーがあるなら CTRL (従来どおり)、無いなら ⇧ を 1 段下げてここへ。
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            if (!isCompact) {
                if (hasFaceKey) {
                    BasicKey(
                        label = "CTRL",
                        weight = 1.4f,
                        fontSp = smallFont,
                        active = ctrl,
                        style = style
                    ) { ctrl = !ctrl }
                } else {
                    ShiftKey(weight = 1.4f, state = shift, style = style, onCycle = { cycleShift() })
                }
            }
            r4Labels.forEachIndexed { idx, s ->
                val display = if (!sym && shift != ShiftState.OFF && s[0].isLetter()) s.uppercase() else s
                FlickKey(
                    label = display,
                    flick = flickFor(4, idx),
                    weight = 1f,
                    style = style,
                    onTap = { emitChar(s[0]) },
                    onFlick = { ch -> emitFlick(ch) }
                )
            }
        }
        // Row 5: 最下段の左端 = 面の切替キー (行き先の面のラベル: あ / 12)。
        //   面が英字だけなら切替キーは要らないので、その空きを CTRL で埋める
        //   (spacious は ⇧/CTRL を 1 段下げた結果として、compact は上部バーとは別にここへ)。
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
            if (hasFaceKey) {
                BasicKey(nextFace.switchLabel, weight = 1.4f, fontSp = style.keyFontSp, style = style) {
                    switchFace(nextFace)
                }
            } else if (isCompact) {
                // compact の英語面は上部バーに CTRL があり、ここは**同じキーが 2 つ**あった。
                // 日本語面で「あ」(面の切替) が座っている位置なので、面を差し替えるパッドの
                // 入口をここに置く (spacious 英語は Row 3 左の旧 META がその役)。
                PadKey(
                    weight = 1.4f,
                    style = style,
                    onTap = { togglePad(PadMode.CLIPBOARD) },
                    onFlickUp = { togglePad(PadMode.EMOJI) }
                )
            } else {
                BasicKey("CTRL", weight = 1.4f, fontSp = smallFont, active = ctrl, style = style) { ctrl = !ctrl }
            }
            BasicKey(
                label = if (sym) "ABC" else "?#",
                weight = 1.2f,
                fontSp = smallFont,
                active = sym,
                style = style
            ) { sym = !sym }
            BasicKey(
                label = "ALT",
                weight = 1.2f,
                fontSp = smallFont,
                active = alt,
                style = style
            ) { alt = !alt }
            SpaceKey(weight = 4f, style = style) { emitChar(' ') }
            BasicKey("←", weight = 1f, fontSp = style.keyFontSp, repeatable = true, style = style) { emitCursor(TerminalEmulator.CursorKey.LEFT) }
            BasicKey("↓", weight = 1f, fontSp = style.keyFontSp, repeatable = true, style = style) { emitCursor(TerminalEmulator.CursorKey.DOWN) }
            BasicKey("↑", weight = 1f, fontSp = style.keyFontSp, repeatable = true, style = style) { emitCursor(TerminalEmulator.CursorKey.UP) }
            BasicKey("→", weight = 1f, fontSp = style.keyFontSp, repeatable = true, style = style) { emitCursor(TerminalEmulator.CursorKey.RIGHT) }
        }
    }
}

@Composable
private fun RowScope.BasicKey(
    label: String,
    weight: Float,
    fontSp: Float,
    active: Boolean = false,
    repeatable: Boolean = false,
    style: KeyboardStyle,
    onClick: () -> Unit
) {
    // タップ中は背景を明るい緑に変えて「ここを押した」が見えるようにする (`active` は別系統)。
    var pressed by remember { mutableStateOf(false) }
    val bg = when {
        pressed -> ZtsGreenBright
        active -> ZtsGreen
        else -> ZtsBgCard
    }
    val fg = if (active || pressed) Color.Black else ZtsTextPrimary
    val border = if (active || pressed) ZtsGreen else ZtsBorder
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val tapModifier = if (repeatable) {
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
            .height(style.keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .then(tapModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = fontSp.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 貼り付け / 絵文字パッド ([KeyboardPad]) の入口キー。**英語ロケールの英字面だけ**に出る。
 *
 * タップ = 貼り付け ([onTap])、**上フリック** = 絵文字 ([onFlickUp])。
 *
 * ⚠ 中央に 📋、上端に 😀 を出して**どちらが何か見て分かる**ようにする — 日本語面の
 * 「ESC の上フリック」は見えない入口だったため辿り着けない人がいた (0.8.279 でヒントを足した)。
 * 同じ轍を踏まないよう、こちらは最初からキーの表示そのものを入口の説明にする。
 */
@Composable
private fun RowScope.PadKey(
    weight: Float,
    style: KeyboardStyle,
    onTap: () -> Unit,
    onFlickUp: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnFlickUp by rememberUpdatedState(onFlickUp)
    val bg = if (pressed) ZtsGreenBright else ZtsBgCard
    val fg = if (pressed) Color.Black else ZtsTextPrimary
    val border = if (pressed) ZtsGreen else ZtsBorder
    Box(
        modifier = Modifier
            .weight(weight)
            .height(style.keyHeight)
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
                        var resolved = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (!resolved && dy < -flickThreshold && abs(dy) > abs(dx)) {
                                resolved = true
                                currentOnFlickUp()
                                change.consume()
                            }
                            if (!change.pressed) {
                                if (!resolved) currentOnTap()
                                break
                            }
                        }
                        pressed = false
                    }
                }
            }
    ) {
        HintText("😀", style, modifier = Modifier.align(Alignment.TopCenter))
        Text(
            text = "📋",
            color = fg,
            fontSize = style.keyFontSp.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * ⇧ キー: OFF / ONESHOT / LOCKED の 3 状態を視覚化。
 *  - OFF: 通常配色
 *  - ONESHOT: 緑背景 (1 回大文字 → 自動 OFF)
 *  - LOCKED: 緑暗色 + 「⇪」アイコン (連続大文字、再タップで OFF)
 */
@Composable
private fun RowScope.ShiftKey(
    weight: Float,
    state: ShiftState,
    style: KeyboardStyle,
    onCycle: () -> Unit
) {
    val bg: Color
    val fg: Color
    val border: Color
    val label: String
    when (state) {
        ShiftState.OFF -> { bg = ZtsBgCard; fg = ZtsTextPrimary; border = ZtsBorder; label = "⇧" }
        ShiftState.ONESHOT -> { bg = ZtsGreen; fg = Color.Black; border = ZtsGreen; label = "⇧" }
        ShiftState.LOCKED -> { bg = ZtsGreenDim; fg = Color.Black; border = ZtsGreen; label = "⇪" }
    }
    Box(
        modifier = Modifier
            .weight(weight)
            .height(style.keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onCycle),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = style.keyFontSp.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 4 方向フリック対応キー (compact 時は up のみ、spacious 時は up/down/left/right)。
 *
 * 視覚レイアウト:
 *  - 主文字は Column 内で中央配置。上フリックが定義されているときは
 *    Column の上端にヒントを並べて **主文字と重ならない** ようにする。
 *  - 左右フリックは Box overlay で中央左端/中央右端に置く (Column と直交)。
 *  - 下フリック (= ローマ字大文字) はヒントを出さない (隠し動作)。
 *  - ヒント色は `ZtsGreenBright` で主文字 (白) と明確に区別。
 *
 * インタラクション:
 *  - 短いタップ → onTap
 *  - 上下左右どれかに touchSlop * 1.4 px 以上で離す → onFlick(対応する char)
 */
@Composable
private fun RowScope.FlickKey(
    label: String,
    flick: FlickMap?,
    weight: Float,
    style: KeyboardStyle,
    onTap: () -> Unit,
    onFlick: (Char) -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnFlick by rememberUpdatedState(onFlick)
    // 押し中か / 押し中に向かっているフリック方向の文字。null = しきい値未到達 (= タップ予定)
    var pressed by remember { mutableStateOf(false) }
    var flickPreview by remember { mutableStateOf<Char?>(null) }
    val bg = if (pressed) ZtsGreenBright else ZtsBgCard
    val fg = if (pressed) Color.Black else ZtsTextPrimary
    val border = if (pressed) ZtsGreen else ZtsBorder
    Box(
        modifier = Modifier
            .weight(weight)
            .height(style.keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .pointerInput(label, flick) {
                val flickThreshold = viewConfiguration.touchSlop * 1.4f
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        flickPreview = null
                        val startX = down.position.x
                        val startY = down.position.y
                        var repeated = false  // 長押し連打開始済み
                        // 押しっぱなしで連打 (フリックされたらキャンセル)
                        val repeatJob = scope.launch {
                            delay(KEY_REPEAT_INITIAL_MS)
                            if (flickPreview == null) {
                                repeated = true
                                while (isActive) { currentOnTap(); delay(KEY_REPEAT_INTERVAL_MS) }
                            }
                        }
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            // 移動量に応じてプレビューを更新 (確定はしない)
                            if (!repeated && flick != null &&
                                (abs(dx) > flickThreshold || abs(dy) > flickThreshold)
                            ) {
                                val ch = if (abs(dx) > abs(dy)) {
                                    if (dx < 0) flick.left else flick.right
                                } else {
                                    if (dy < 0) flick.up else flick.down
                                }
                                flickPreview = ch  // null でもよい (該当方向に割当無し)
                            } else {
                                flickPreview = null
                            }
                            if (!change.pressed) {
                                repeatJob.cancel()
                                val committed = flickPreview
                                if (!repeated) {
                                    if (committed != null) currentOnFlick(committed) else currentOnTap()
                                }
                                pressed = false
                                flickPreview = null
                                break
                            }
                        }
                        repeatJob.cancel()
                        pressed = false
                        flickPreview = null
                    }
                }
            }
    ) {
        // 中央は常に主文字、背景色も不変。フリック方向の強調はヒント側 (四隅) だけで行う。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (flick?.up != null) {
                HintText(
                    flick.up.toString(),
                    style,
                    emphasized = flickPreview == flick.up
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = fg,
                    fontSize = style.mainKeyFontSp.sp,
                    lineHeight = style.mainKeyFontSp.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        // 左右ヒントは Box overlay (Column と独立)
        if (flick?.left != null) {
            HintText(
                flick.left.toString(),
                style,
                emphasized = flickPreview == flick.left,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp)
            )
        }
        if (flick?.right != null) {
            HintText(
                flick.right.toString(),
                style,
                emphasized = flickPreview == flick.right,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp)
            )
        }
    }
}

@Composable
private fun HintText(
    text: String,
    style: KeyboardStyle,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = if (emphasized) Color.Black else ZtsGreenBright,
        fontSize = (if (emphasized) style.flickHintFontSp * 1.6f else style.flickHintFontSp).sp,
        lineHeight = (if (emphasized) style.flickHintFontSp * 1.6f else style.flickHintFontSp).sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
        modifier = modifier
    )
}

/**
 * ⌫ 専用キー: タップ単発、長押し連打 (500ms 後から 60ms 間隔)、
 * 左フリックで onFlickLeft (Ctrl+W)、右フリックで onFlickRight (Ctrl+U)。
 */
@Composable
private fun RowScope.BackspaceKey(
    weight: Float,
    style: KeyboardStyle,
    onTap: () -> Unit,
    onFlickLeft: () -> Unit,
    onFlickRight: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnFlickLeft by rememberUpdatedState(onFlickLeft)
    val currentOnFlickRight by rememberUpdatedState(onFlickRight)
    Box(
        modifier = Modifier
            .weight(weight)
            .height(style.keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                val flickThreshold = viewConfiguration.touchSlop * 1.4f
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
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
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // BS は ⌫ のみを表示。左右フリック (Ctrl+W / Ctrl+U) は隠し機能として
        // ヒント表示せずに残す。
        Text(
            text = "⌫",
            color = ZtsTextPrimary,
            fontSize = style.keyFontSp.sp,
            lineHeight = style.keyFontSp.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun RowScope.SpaceKey(weight: Float, style: KeyboardStyle, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = Modifier
            .weight(weight)
            .height(style.keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .pointerInput(Unit) { detectTapWithRepeat(scope) { currentOnClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "space",
            color = ZtsTextSecondary,
            fontSize = (style.keyFontSp - 3f).coerceAtLeast(10f).sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
