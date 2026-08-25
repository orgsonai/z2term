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
 *   Top  : [ ESC ][ TAB ][ ⇧ ][ CTRL / 貼り付け・絵文字(切替キー無し) ]
 *   Row 1: 1〜0                                              ⌫
 *   Row 2: q w e r t y u i o p
 *   Row 3: a s d f g h j k l                                 ⏎
 *   Row 4: z x c v b n m , . /
 *   Row 5: 面切替 / CTRL(切替キー無し)  ?#  ALT  SPACE       ← ↓ ↑ →
 *   切替キー無しの英字面では、右上を貼り付け・絵文字、左下を CTRL にする。
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

    // ⭐ 段階 1c (0.8.404): **並び・幅・ラベル・フリック先をレイアウト定義から引く**。
    // ⚠ 見た目は 1 ドットも変えない。キーの描画そのもの (BasicKey / FlickKey / ShiftKey /
    //   SilentEscKey / BackspaceKey / PadKey / SpaceKey) は据え置きで、「**どのキーを・どこに・
    //   どの幅で**置くか」だけをデータへ移した。
    // ⚠ キーの描画を 1 つに統合する (= 専用部品をやめる) のは次の段階。ここで一緒にやると、
    //   壊れたときに「並びが悪いのか描画が悪いのか」を切り分けられなくなる。
    val layout = remember(isCompact, hasFaceKey, sym, style.fourDirectionFlick) {
        asciiKeyLayout(
            compact = isCompact,
            hasFaceKey = hasFaceKey,
            symbols = sym,
            fourWayFlick = style.fourDirectionFlick,
        )
    }
    // ⇧ は**キーの姿の差し替え** = レイヤーで表す。⚠ 記号面では大文字にしない (いまと同じ)。
    val activeLayer = if (!sym && shift != ShiftState.OFF) KeyLayout.LAYER_SHIFT else null

    // アクション列を実行する。⚠ **タップとフリックで経路が違う** — タップは ⇧/CTRL/ALT を
    // 適用し (emitChar)、フリックは文字をそのまま送る (emitFlick)。いまの挙動をそのまま保つ。
    fun runActions(actions: List<KeyAction>, gesture: KeyGesture) {
        val isFlick = gesture in KeyGesture.FLICKS
        for (action in actions) {
            when (action) {
                is KeyAction.Text -> {
                    val ch = action.text.firstOrNull() ?: continue
                    if (isFlick) emitFlick(ch) else emitChar(ch)
                }
                is KeyAction.Named -> when (action.key) {
                    NamedKey.ESC -> emitSpecial(byteArrayOf(0x1B))
                    NamedKey.TAB -> emitSpecial(byteArrayOf(0x09))
                    NamedKey.ENTER -> emitSpecial(byteArrayOf(0x0D))
                    NamedKey.BACKSPACE -> emitSpecial(byteArrayOf(0x7F))
                    NamedKey.UP -> emitCursor(TerminalEmulator.CursorKey.UP)
                    NamedKey.DOWN -> emitCursor(TerminalEmulator.CursorKey.DOWN)
                    NamedKey.LEFT -> emitCursor(TerminalEmulator.CursorKey.LEFT)
                    NamedKey.RIGHT -> emitCursor(TerminalEmulator.CursorKey.RIGHT)
                    // ⚠ Delete / Home / F キー等は**まだどの配列にも置いていない**。
                    //    エディタで置けるようになる段階で、ここに送出を足す。
                    else -> Unit
                }
                is KeyAction.Chord -> {
                    // いまの配列で使うのは ⌫ の左右フリック (Ctrl+W / Ctrl+U) だけ。
                    val ch = action.text?.firstOrNull()
                    val b = if (ModKey.CTRL in action.mods && ch != null) {
                        AndroidKeyMapper.controlByteFor(ch)
                    } else null
                    if (b != null) emitSpecial(byteArrayOf(b))
                }
                is KeyAction.Raw -> emitSpecial(action.bytes)
                is KeyAction.Modifier -> when (action.mod) {
                    ModKey.SHIFT -> cycleShift()
                    ModKey.CTRL -> ctrl = !ctrl
                    ModKey.ALT -> alt = !alt
                }
                // 記号面は**枠の数が変わる**ので、レイヤーではなく別レイアウトへ移る
                // (`layout` が sym を見て組み直す)。
                is KeyAction.Layer -> sym = action.layer == KeyLayout.LAYER_SYMBOL
                is KeyAction.App -> when (action.action) {
                    AppAction.NEXT_FACE -> switchFace(nextFace)
                    AppAction.PAD_PASTE -> togglePad(PadMode.CLIPBOARD)
                    AppAction.PAD_EMOJI -> togglePad(PadMode.EMOJI)
                    // ⚠ 設定 / キーボードを閉じる / IME 切替は、この面のキーにはまだ無い。
                    else -> Unit
                }
                // ⚠ スニペットとマクロもエディタが置けるようになってから配線する。
                is KeyAction.Snippet, is KeyAction.Macro -> Unit
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        layout.rows.forEach { keyRow ->
            val weights = keyRow.weights()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                keyRow.slots.forEachIndexed { index, keySlot ->
                    val content = keySlot.content
                    if (content !is SlotContent.Single) {
                        // ⚠ 枠の分割 (上下左右キーを 1 枠に収める等) は**まだ描けない**。
                        //    いまの配列は分割を使っていないのでここには来ない。キーの描画を
                        //    1 つに統合する段階で対応する (縦割りの中では RowScope が使えず、
                        //    いまの専用部品 (RowScope 拡張) をそのままでは置けないため)。
                        return@forEachIndexed
                    }
                    val key = content.key.onLayer(activeLayer)
                    LayoutKey(
                        key = key,
                        weight = weights[index],
                        style = style,
                        smallFont = smallFont,
                        shift = shift,
                        ctrl = ctrl,
                        alt = alt,
                        sym = sym,
                        faceLabel = nextFace.switchLabel,
                        onCycleShift = { cycleShift() },
                        onGesture = { g -> runActions(key.actionsFor(g), g) },
                        onFlickChar = { ch -> emitFlick(ch) },
                    )
                }
            }
        }
    }
}

/**
 * レイアウト定義のキー 1 つを、**いまの見た目のまま**描く (0.8.404・段階 1c)。
 *
 * ⚠ ここは「[KeyDef] を見て、いまある専用部品のどれを使うか選ぶ」だけの振り分け。
 * ⭐ **次の段階でこの振り分けごと無くす** — 専用部品を 1 つの汎用キーに統合すれば、
 * 「ESC だけ / ⌫ だけ」という特別扱いが本当に消えて、利用者が同じことを作れるようになる。
 */
@Composable
private fun RowScope.LayoutKey(
    key: KeyDef,
    weight: Float,
    style: KeyboardStyle,
    smallFont: Float,
    shift: ShiftState,
    ctrl: Boolean,
    alt: Boolean,
    sym: Boolean,
    faceLabel: String,
    onCycleShift: () -> Unit,
    onGesture: (KeyGesture) -> Unit,
    onFlickChar: (Char) -> Unit,
) {
    val tap = key.actionsFor(KeyGesture.TAP).firstOrNull()
    val fontSp = when (key.fontRole) {
        KeyFontRole.SMALL -> smallFont
        KeyFontRole.NORMAL -> style.keyFontSp
        KeyFontRole.MAIN -> style.mainKeyFontSp
    }
    val hasFlick = key.bindings.keys.any { it in KeyGesture.FLICKS }
    when {
        // ⇧: OFF → 1 回だけ → 固定 の 3 状態を色で見せる。
        tap is KeyAction.Modifier && tap.mod == ModKey.SHIFT ->
            ShiftKey(weight = weight, state = shift, style = style, onCycle = onCycleShift)

        // ESC: 上下フリックで貼り付け / 絵文字 (⚠ 印もポップアップも出さない)。
        tap is KeyAction.Named && tap.key == NamedKey.ESC && hasFlick ->
            SilentEscKey(
                weight = weight,
                fontSp = fontSp,
                style = style,
                onTap = { onGesture(KeyGesture.TAP) },
                onFlickUp = { onGesture(KeyGesture.UP) },
                onFlickDown = { onGesture(KeyGesture.DOWN) },
            )

        // ⌫: 左右フリックでまとめて削除 (⚠ こちらも印を出さない)。
        tap is KeyAction.Named && tap.key == NamedKey.BACKSPACE ->
            BackspaceKey(
                weight = weight,
                style = style,
                onTap = { onGesture(KeyGesture.TAP) },
                onFlickLeft = { onGesture(KeyGesture.LEFT) },
                onFlickRight = { onGesture(KeyGesture.RIGHT) },
            )

        // 貼り付け / 絵文字の入口 (面の切替キーが要らない面でだけ席が空く)。
        tap is KeyAction.App && tap.action == AppAction.PAD_PASTE && hasFlick ->
            PadKey(
                weight = weight,
                style = style,
                onTap = { onGesture(KeyGesture.TAP) },
                onFlickUp = { onGesture(KeyGesture.UP) },
                onFlickDown = { onGesture(KeyGesture.DOWN) },
            )

        // スペース。
        tap is KeyAction.Text && tap.text == " " ->
            SpaceKey(weight = weight, style = style) { onGesture(KeyGesture.TAP) }

        // 打つための文字キー。⚠ フリック先の文字は [FlickKey] が方向から選んで返すので、
        //   ここでは受け取った文字をそのまま送る (いまと同じ経路)。
        // ⚠ **記号面はフリックが無いが、それでも [FlickKey] で描く** — いまの実装がそうして
        //   おり (`flick = null` を渡している)、[BasicKey] とは中央テキストの行間と 1dp の
        //   余白がわずかに違う。ここで [BasicKey] に寄せると記号面だけ字がずれる。
        // ⚠ 数字と英字を [KeyDef.repeatable] で振り分けているのは**いまの部品の都合**。
        //   数字は連打を [BasicKey] に任せ、英字は [FlickKey] が自前で連打する。部品を 1 つに
        //   統合する段階で、この分岐ごと消える。
        tap is KeyAction.Text && key.fontRole == KeyFontRole.MAIN && !key.repeatable ->
            FlickKey(
                label = key.label,
                flick = flickMapOf(key),
                weight = weight,
                style = style,
                onTap = { onGesture(KeyGesture.TAP) },
                onFlick = onFlickChar,
            )

        else -> BasicKey(
            // ⚠ 面の切替キーだけラベルが空。「押すと**行く**面」を出すのは呼出し側の仕事。
            label = key.label.ifEmpty { faceLabel },
            weight = weight,
            fontSp = fontSp,
            active = when {
                tap is KeyAction.Modifier && tap.mod == ModKey.CTRL -> ctrl
                tap is KeyAction.Modifier && tap.mod == ModKey.ALT -> alt
                tap is KeyAction.Layer -> sym
                else -> false
            },
            style = style,
            repeatable = key.repeatable,
        ) { onGesture(KeyGesture.TAP) }
    }
}

/** [KeyDef] の 4 方向から、いまの [FlickKey] が受け取る形へ。割り当てが無ければ null。 */
private fun flickMapOf(key: KeyDef): FlickMap? {
    fun charOf(g: KeyGesture): Char? =
        (key.actionsFor(g).firstOrNull() as? KeyAction.Text)?.text?.firstOrNull()
    val up = charOf(KeyGesture.UP)
    val down = charOf(KeyGesture.DOWN)
    val left = charOf(KeyGesture.LEFT)
    val right = charOf(KeyGesture.RIGHT)
    return if (up == null && down == null && left == null && right == null) null
    else FlickMap(up = up, down = down, left = left, right = right)
}

/**
 * 英字面の ESC キー。**見た目は [BasicKey] のまま**で、上下フリックだけを足したもの。
 *
 * タップ = ESC 送出、**上フリック** = 貼り付けパッド、**下フリック** = 絵文字パッド。
 * かな面 ([JpEscKey]) / 数字面と**同じ指の動き**を英字面でも通すためのもの (0.8.362・要望)。
 *
 * ⚠ **なぜ要るか**: 貼り付け / 絵文字の入口 ([PadKey]) は**面の切替キーが無い配列にしか置けない**
 * (席が 1 つしか空かない)。日本語ロケールではその席が面切替に要るので、**英字面から貼り付けを
 * 開く手が 1 つも無かった**。ESC のフリックなら席を増やさずに済む。
 *
 * ⚠ **印もポップアップも出さない (利用者の判断)**。かな面の ESC は上下端にヒントを出すが、
 * あちらは元から記号を載せたキーが並ぶ面で馴染む。英字面の ESC は素のキーなので、ここに印を
 * 足すと**英字面の見た目が変わってしまう**。狙いは「かな面で覚えた指の動きが英字面でも通る」
 * ことなので、表示は据え置いて動きだけ揃える。
 */
@Composable
private fun RowScope.SilentEscKey(
    weight: Float,
    fontSp: Float,
    style: KeyboardStyle,
    onTap: () -> Unit,
    onFlickUp: () -> Unit,
    onFlickDown: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnFlickUp by rememberUpdatedState(onFlickUp)
    val currentOnFlickDown by rememberUpdatedState(onFlickDown)
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
                // しきい値と判定順は [JpEscKey] と同じにする (面ごとに感度が違うと戸惑うため)。
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
                            // 縦の移動が横より大きいときだけフリック扱い (横払いは誤爆させない)。
                            if (!resolved && abs(dy) > flickThreshold && abs(dy) > abs(dx)) {
                                resolved = true
                                if (dy < 0) currentOnFlickUp() else currentOnFlickDown()
                                change.consume()
                            }
                            if (!change.pressed) {
                                // フリックが決まっていなければ通常の ESC として送る。
                                if (!resolved) currentOnTap()
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
            text = "ESC",
            color = fg,
            fontSize = fontSp.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
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
 * タップ = 貼り付け ([onTap])、**上フリック** = 貼り付け ([onFlickUp])、
 * **下フリック** = 絵文字 ([onFlickDown])。上下は [JpEscKey] と同じ割り当て。
 *
 * ⚠ 上端に 📋、下端に 😀 を出して**どちらが何か見て分かる**ようにする — 日本語面の
 * 「ESC の上フリック」は見えない入口だったため辿り着けない人がいた (0.8.279 でヒントを足した)。
 * 同じ轍を踏まないよう、こちらは最初からキーの表示そのものを入口の説明にする。
 */
@Composable
private fun RowScope.PadKey(
    weight: Float,
    style: KeyboardStyle,
    onTap: () -> Unit,
    onFlickUp: () -> Unit,
    onFlickDown: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnFlickUp by rememberUpdatedState(onFlickUp)
    val currentOnFlickDown by rememberUpdatedState(onFlickDown)
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
                            if (!resolved && abs(dy) > flickThreshold && abs(dy) > abs(dx)) {
                                resolved = true
                                if (dy < 0) currentOnFlickUp() else currentOnFlickDown()
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
        HintText("📋", style, modifier = Modifier.align(Alignment.TopCenter))
        Text(
            text = "↕",
            color = fg,
            fontSize = (style.keyFontSp * 0.75f).sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Center)
        )
        HintText("😀", style, modifier = Modifier.align(Alignment.BottomCenter))
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
