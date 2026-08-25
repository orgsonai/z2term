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
import androidx.compose.foundation.layout.fillMaxHeight
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
    /**
     * 自分で作ったキー配列 (0.8.408・段階 2)。**null = 既定のプリセット**。
     *
     * ⚠ 効くのは**英字面の素の姿だけ**。記号面 (`?#`) はまだプリセットのまま
     * (枠の数が変わるので別の 1 枚が要る — 段階 3)。かな面 / 数字面は別の Composable。
     *
     * ⚠ **呼出し側は「読めなかったら null」を渡す。** 壊れた JSON でキーボードが
     * 1 枚も出ない端末を作らないため (`activeKeyLayout` がその判断をしている)。
     */
    customLayout: KeyLayout? = null,
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
                    AppAction.CLOSE_PAD -> pad = PadMode.NONE
                    // ⚠ 設定 / キーボードを閉じる / IME 切替は、この面のキーにはまだ無い。
                    else -> Unit
                }
                // ⚠ スニペットとマクロもエディタが置けるようになってから配線する。
                is KeyAction.Snippet, is KeyAction.Macro -> Unit
            }
        }
    }

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
            // ⚠ 閉じるのはここ (×) か、開いた入口キーをもう一度押すか。⌫ を「閉じる」に
            // 置き換えない (パッドを開いている間に文字を消せなくなる)。
            val padRow = remember { asciiPadRow() }
            val padWeights = padRow.weights()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                padRow.slots.forEachIndexed { index, keySlot ->
                    LayoutSlot(
                        content = keySlot.content,
                        modifier = Modifier.weight(padWeights[index]).height(style.keyHeight),
                        activeLayer = null,
                        style = style,
                        smallFont = smallFont,
                        shift = shift,
                        ctrl = ctrl,
                        alt = alt,
                        sym = sym,
                        faceLabel = nextFace.switchLabel,
                        onGesture = { key, g -> runActions(key.actionsFor(g), g) },
                    )
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
    //
    // ⭐ 段階 2 (0.8.408): [customLayout] があればそれを描く。無ければ従来どおりプリセット。
    // ⚠ **記号面 (`?#`) はまだプリセットのまま。** 記号面は Row 4 の枠が 10 → 8 個に減るので
    //   レイヤーでは表せず、**別の 1 枚**として持つしかない (0.8.403 で分かったこと)。自分の
    //   配列に記号面を持たせるのはエディタ (段階 3) と一緒に入れる。それまで `?#` を押したら
    //   既定の記号面が出る — 記号が打てなくなるよりは、面が 1 枚だけ既定に戻る方がまし。
    val layout = remember(isCompact, hasFaceKey, sym, style.fourDirectionFlick, customLayout) {
        customLayout?.takeIf { !sym } ?: asciiKeyLayout(
            compact = isCompact,
            hasFaceKey = hasFaceKey,
            symbols = sym,
            fourWayFlick = style.fourDirectionFlick,
        )
    }
    // ⇧ は**キーの姿の差し替え** = レイヤーで表す。⚠ 記号面では大文字にしない (いまと同じ)。
    val activeLayer = if (!sym && shift != ShiftState.OFF) KeyLayout.LAYER_SHIFT else null

    // ⚠ **段の数が違う配列は 1 段の高さを割り直す** (0.8.408)。キーボードの席は
    // `style.naturalHeight` で固定してあり、その高さは「シンプル = 6 段 / 4 方向フリック =
    // 5 段」を前提に `style.keyHeight` から作られている。自分で作った配列は段の数が違い得る
    // (シンプルのときに複製した 6 段の配列を 4 方向フリックで使う等) ので、そのまま描くと
    // **席からはみ出して端末の画面にかぶる**。プリセットは段の数が一致するので何も変わらない。
    val presetRowCount = if (isCompact) 6 else 5
    val rowHeight =
        if (layout.rows.size == presetRowCount) style.keyHeight
        else style.keyHeight * presetRowCount / layout.rows.size

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
                    LayoutSlot(
                        content = keySlot.content,
                        // ⚠ 段の高さはここで決める。枠を割ったときは中で分け合う。
                        modifier = Modifier.weight(weights[index]).height(rowHeight),
                        activeLayer = activeLayer,
                        style = style,
                        smallFont = smallFont,
                        shift = shift,
                        ctrl = ctrl,
                        alt = alt,
                        sym = sym,
                        faceLabel = nextFace.switchLabel,
                        onGesture = { key, g -> runActions(key.actionsFor(g), g) },
                    )
                }
            }
        }
    }
}

/**
 * レイアウト定義の枠 1 つを描く (0.8.407・段階 1d)。
 *
 * ⭐ **枠の分割はここで再帰する** — 縦割り・横割りを向き自由で深さ 2 まで
 * ([KeyLayout.MAX_SPLIT_DEPTH])。「上下左右キーは 1 つのキーの半分」(利用者) が
 * これで描けるようになった。⚠ 分割の中で `weight` を使うので、[modifier] は
 * **呼出し側が幅と高さを決めて渡す**（この関数は Row/Column どちらの中でも置ける）。
 */
@Composable
private fun LayoutSlot(
    content: SlotContent,
    modifier: Modifier,
    activeLayer: String?,
    style: KeyboardStyle,
    smallFont: Float,
    shift: ShiftState,
    ctrl: Boolean,
    alt: Boolean,
    sym: Boolean,
    faceLabel: String,
    onGesture: (KeyDef, KeyGesture) -> Unit,
) {
    when (content) {
        is SlotContent.Single -> {
            val key = content.key.onLayer(activeLayer)
            KeyCell(
                key = key,
                modifier = modifier,
                style = style,
                smallFont = smallFont,
                shift = shift,
                ctrl = ctrl,
                alt = alt,
                sym = sym,
                faceLabel = faceLabel,
                onGesture = { g -> onGesture(key, g) },
            )
        }
        is SlotContent.Split -> {
            // 区画の間は狭めに空ける (段と段の間より詰める。1 つの枠に見えるように)。
            val gap = 2.dp
            if (content.dir == SplitDir.VERTICAL) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
                    content.parts.forEach { part ->
                        LayoutSlot(
                            content = part.content,
                            modifier = Modifier.weight(part.ratio).fillMaxWidth(),
                            activeLayer = activeLayer,
                            style = style,
                            smallFont = smallFont,
                            shift = shift,
                            ctrl = ctrl,
                            alt = alt,
                            sym = sym,
                            faceLabel = faceLabel,
                            onGesture = onGesture,
                        )
                    }
                }
            } else {
                Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
                    content.parts.forEach { part ->
                        LayoutSlot(
                            content = part.content,
                            modifier = Modifier.weight(part.ratio).fillMaxHeight(),
                            activeLayer = activeLayer,
                            style = style,
                            smallFont = smallFont,
                            shift = shift,
                            ctrl = ctrl,
                            alt = alt,
                            sym = sym,
                            faceLabel = faceLabel,
                            onGesture = onGesture,
                        )
                    }
                }
            }
        }
    }
}

/**
 * キー 1 つ。⭐ **これがキーボードの唯一のキー部品** (0.8.407・段階 1d)。
 *
 * それまでは ESC / ⌫ / ⇧ / 貼り付け / スペース / 文字キーが**それぞれ専用の部品**で、
 * 隠し機能もその中に直接書いてあった。ここに統合したことで、**利用者が同じものを
 * [KeyDef] だけで作れる**ようになった（= カスタム配列の土台）。
 *
 * 見た目と手触りの違いは、すべて [KeyDef] のフィールドで表す:
 *
 * - [KeyDef.hintGestures] … どの方向の行き先をキーの上に小さく出すか
 *   (英字は上・左右だけ / 貼り付けは上下 / ESC・⌫ は**出さない**＝隠し操作のまま)
 * - [KeyDef.flickOnRelease] … 指を離したときに確定する (文字キー) か、しきい値を超えた
 *   瞬間に発火する (ESC・⌫・貼り付け) か
 * - [KeyDef.pressFeedback] … 押している間に背景を明るくするか (`space` は変えない)
 * - [KeyDef.labelTone] / [KeyDef.fontRole] … 字の色と大きさの役どころ
 * - [KeyDef.repeatable] / [KeyDef.repeatInitialMs] / [KeyDef.repeatIntervalMs] … 長押し連打
 *
 * ⚠ **⇧ だけは 3 状態 (OFF / 1 回だけ / 固定) を色で見せる**。これは修飾キーの状態表示で、
 * CTRL・ALT が `active` で緑になるのと同じ筋（[ShiftState] が 3 値なぶん色が 1 つ多い）。
 */
@Composable
private fun KeyCell(
    key: KeyDef,
    modifier: Modifier,
    style: KeyboardStyle,
    smallFont: Float,
    shift: ShiftState,
    ctrl: Boolean,
    alt: Boolean,
    sym: Boolean,
    faceLabel: String,
    onGesture: (KeyGesture) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnGesture by rememberUpdatedState(onGesture)
    var pressed by remember { mutableStateOf(false) }
    // いま指が向いている方向 (null = しきい値未到達 = このまま離せばタップ)。
    var flickTo by remember { mutableStateOf<KeyGesture?>(null) }

    val tap = key.actionsFor(KeyGesture.TAP).firstOrNull()
    val isShiftKey = tap is KeyAction.Modifier && tap.mod == ModKey.SHIFT
    val active = key.highlighted || when {
        tap is KeyAction.Modifier && tap.mod == ModKey.CTRL -> ctrl
        tap is KeyAction.Modifier && tap.mod == ModKey.ALT -> alt
        tap is KeyAction.Layer -> sym
        else -> false
    }
    val lit = pressed && key.pressFeedback

    val bg: Color
    val fg: Color
    val border: Color
    val label: String
    if (isShiftKey) {
        when (shift) {
            ShiftState.OFF -> { bg = ZtsBgCard; fg = ZtsTextPrimary; border = ZtsBorder; label = "⇧" }
            ShiftState.ONESHOT -> { bg = ZtsGreen; fg = Color.Black; border = ZtsGreen; label = "⇧" }
            ShiftState.LOCKED -> { bg = ZtsGreenDim; fg = Color.Black; border = ZtsGreen; label = "⇪" }
        }
    } else {
        bg = when {
            lit -> ZtsGreenBright
            active -> ZtsGreen
            else -> ZtsBgCard
        }
        fg = when {
            lit || active -> Color.Black
            key.labelTone == LabelTone.SECONDARY -> ZtsTextSecondary
            else -> ZtsTextPrimary
        }
        border = if (lit || active) ZtsGreen else ZtsBorder
        // ⚠ 面の切替キーだけラベルが空。「押すと**行く**面」を入れるのは呼出し側の仕事。
        label = key.label.ifEmpty { faceLabel }
    }
    val fontSp = when (key.fontRole) {
        KeyFontRole.SMALL -> smallFont
        KeyFontRole.NORMAL -> style.keyFontSp
        KeyFontRole.MAIN -> style.mainKeyFontSp
    }
    val hasFlick = key.hasFlick()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .pointerInput(key) {
                val threshold = viewConfiguration.touchSlop * 1.4f
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        flickTo = null
                        val startX = down.position.x
                        val startY = down.position.y
                        var fired = false        // しきい値到達で発火済み (flickOnRelease=false)
                        var repeating = false    // 長押し連打が始まった
                        var repeatJob: Job? = null
                        if (key.repeatable) {
                            repeatJob = scope.launch {
                                delay(key.repeatInitialMs)
                                // フリックへ向かっている最中は連打を始めない。
                                if (flickTo == null && !fired) {
                                    repeating = true
                                    while (isActive) {
                                        currentOnGesture(KeyGesture.TAP)
                                        delay(key.repeatIntervalMs)
                                    }
                                }
                            }
                        }
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (hasFlick && !fired && !repeating) {
                                val dx = change.position.x - startX
                                val dy = change.position.y - startY
                                val dir = directionOf(dx, dy, threshold, key)
                                if (dir != null && !key.flickOnRelease) {
                                    // ESC / ⌫ / 貼り付け: しきい値を超えた瞬間に発火する。
                                    fired = true
                                    repeatJob?.cancel()
                                    currentOnGesture(dir)
                                    change.consume()
                                } else {
                                    // 文字キー: 離すまで確定しない (途中で方向を変えられる)。
                                    flickTo = dir
                                    if (dir != null) repeatJob?.cancel()
                                }
                            }
                            if (!change.pressed) {
                                repeatJob?.cancel()
                                if (!repeating && !fired) {
                                    val dir = flickTo
                                    currentOnGesture(dir ?: KeyGesture.TAP)
                                }
                                break
                            }
                        }
                        repeatJob?.cancel()
                        pressed = false
                        flickTo = null
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // ⚠ 上のヒントがあるときだけ Column にする。⚠ ヒントの無いキーまで Column にすると
        //   縦の余白 1dp のぶん中央の字が動く (統合前の [BasicKey] と揃わない)。
        if (key.showsHintFor(KeyGesture.UP) || key.showsHintFor(KeyGesture.DOWN)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                hintTextOrNull(key, KeyGesture.UP)?.let { HintText(it, style, pressed) }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = fg,
                        fontSize = fontSp.sp,
                        lineHeight = fontSp.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
                hintTextOrNull(key, KeyGesture.DOWN)?.let { HintText(it, style, pressed) }
            }
        } else {
            Text(
                text = label,
                color = fg,
                fontSize = fontSp.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
        // 左右のヒントは Box に重ねる (縦の並びと独立)。
        hintTextOrNull(key, KeyGesture.LEFT)?.let {
            HintText(it, style, pressed, Modifier.align(Alignment.CenterStart).padding(start = 3.dp))
        }
        hintTextOrNull(key, KeyGesture.RIGHT)?.let {
            HintText(it, style, pressed, Modifier.align(Alignment.CenterEnd).padding(end = 3.dp))
        }
        // 押下中: キー直上に「今このまま離すと確定する 1 文字」を大きく出す (かな面と同じ)。
        // ⚠ **離してから確定するキーだけ**。しきい値で即発火するキー (ESC・⌫・貼り付け) では
        //   出しても見る間が無いうえ、行き先を隠す約束と食い違う。
        if (pressed && hasFlick && key.flickOnRelease) {
            val text = flickTo?.let { g ->
                (key.actionsFor(g).firstOrNull() as? KeyAction.Text)?.text
            } ?: label
            FlickCommitPopup(text = text, style = style)
        }
    }
}

/** 指の動きから向きを決める。⚠ 割り当ての無い向きは null (= タップ扱い)。 */
private fun directionOf(dx: Float, dy: Float, threshold: Float, key: KeyDef): KeyGesture? {
    val horizontal = abs(dx) > abs(dy)
    val g = when {
        horizontal && abs(dx) > threshold -> if (dx < 0) KeyGesture.LEFT else KeyGesture.RIGHT
        !horizontal && abs(dy) > threshold -> if (dy < 0) KeyGesture.UP else KeyGesture.DOWN
        else -> null
    } ?: return null
    return if (key.bindings.containsKey(g)) g else null
}

/** その向きのヒントに出す文字。出さない設定 / 割り当て無しなら null。 */
private fun hintTextOrNull(key: KeyDef, gesture: KeyGesture): String? {
    if (!key.showsHintFor(gesture)) return null
    return when (val a = key.actionsFor(gesture).firstOrNull()) {
        is KeyAction.Text -> a.text
        is KeyAction.App -> when (a.action) {
            AppAction.PAD_PASTE -> "📋"
            AppAction.PAD_EMOJI -> "😀"
            else -> null
        }
        else -> null
    }
}

@Composable
private fun HintText(
    text: String,
    style: KeyboardStyle,
    pressed: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        // ⚠ **押下中はキーの背景が緑 (ZtsGreenBright) になる**ので、ヒントを緑のままにすると
        //    背景に溶けて**押している間だけ消える** (0.8.406・利用者指摘)。かな面も同じ理由で
        //    ヒント色を前景色から作っている (`fg.copy(alpha = 0.6f)`)。
        //    ⚠ 平常時は緑のまま (利用者の指定。かな面は薄白、英字面は緑)。
        color = if (pressed) Color.Black else ZtsGreenBright,
        fontSize = style.flickHintFontSp.sp,
        lineHeight = style.flickHintFontSp.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}
