package com.zerotoship.z2term.ui.terminal.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary

/**
 * 数字だけの面 ([KeyboardFace.NUMBER]・0.8.305)。
 *
 * **なぜ要るか**: フリック面 ([JapaneseFlickKeyboard]) には数字が 1 つも無く、ASCII 面
 * ([TerminalKeyboard]) の Row 1 (`ESC 1〜0 ⌫`) は横に 10 個並ぶので指が細かい。
 * ポート番号・IP・`chmod 755` のように**数字だけを続けて打つ場面**が端末では多いので、
 * そこだけ大きなキーで打てる面を用意する。
 *
 * ⛔ **ASCII 面の Row 1 を流用しない**。狙いは「大きいキー」なので、かな面と同じ
 * 5 列 × 4 行のマス目に**テンキー 3 列 × 4 段**を置き、1 キーの面積をかなと同じにする。
 *
 * 配列 (かな面と同じ骨格。両端の列は役割まで揃えてあるので指の運びが変わらない):
 *   ESC      1  2  3   ⌫
 *   ◀/▼      4  5  6   ▶/▲
 *   😀/␣     7  8  9   -//
 *   面切替   .  0  :   ⏎
 *
 * **記号は 4 つだけ** (`.` `:` `-` `/`)。端末で数字と一緒に打つもの (IP・ポート・時刻・
 * パス・オプション) に絞る。⚠ **載せすぎると「記号面」になって狙いがぼける** —
 * 記号を一通り打ちたいときは ASCII 面の `?#` がある。
 *
 * **パッド (絵文字 / 貼り付け)**: [KeyboardPad] を開く手は 2 つあり、**かな面と同じに揃えてある**
 * (0.8.348):
 *   - **ESC の上フリック = 貼り付け / 下フリック = 絵文字** ([JpEscKey]・かな面と同一)
 *   - **😀 キーのタップ = 絵文字** (数字面だけの近道。Row 3 左上の席)
 *
 * ⚠ **面が変わると開き方が変わる、が一番戸惑う。** 0.8.347 まで数字面の ESC はタップのみで、
 * **貼り付けを開く手が 1 つも無かった** (😀 は絵文字専用)。かな面で覚えた「ESC を上へ」が
 * 数字面で効かないので、利用者からは「開かない」としか見えない。**入口を足すときは、
 * 先に他の面と同じ動きになっているかを見ること。**
 *
 * どちらも中央 3 列だけがパッドになり両端の列は残る。⚠ 😀 の入口は**アプリの言語が英語のとき
 * 本命**になる — 英語では「あ」キーが無く、その席を面の切替キーが使うため、ASCII 面から
 * 貼り付け / 絵文字の入口が消える。数字面に置いておけば、どの言語でも入口が残る。
 */
@Composable
fun NumberKeyboard(
    onBytes: (ByteArray) -> Unit,
    onCursorKey: (TerminalEmulator.CursorKey) -> Unit,
    onSwitchFace: () -> Unit,
    /** 面の切替キーに出すラベル (= 押すと**行く先**の面 / [KeyboardFace.switchLabel])。 */
    switchLabel: String,
    composing: ComposingState,
    selectedStyle: KeyboardStyle,
    modifier: Modifier = Modifier
) {
    // フォントは選択スタイルによらず SPACIOUS 基準へ揃える (かな面と共通)。
    val style = selectedStyle.forTwelveKeyFace()
    val rowSpacing = if (style.keyHeight >= 56.dp) 4.dp else 3.dp
    // 数字は機能キー (ESC/⏎/矢印) より一回り大きく出す。
    val digitScale = style.mainKeyFontSp / style.keyFontSp

    var pad by remember { mutableStateOf(PadMode.NONE) }

    fun togglePad(mode: PadMode) { pad = if (pad == mode) PadMode.NONE else mode }

    // 数字・記号は**確定と同じ出口** ([ComposingState.commitExternalText]) を通す。
    // ⚠ バイト列 (onBytes) で送ると、OS の入力メソッドとして使っているときに改行や
    // 記号が performEditorAction 等へ読み替えられる。打ちかけのかなが残っていれば
    // 先に確定されるので、かな面から来た直後でも順序が入れ替わらない。
    fun insert(s: String) { composing.commitExternalText(s) }

    val backspaceTap = { if (!composing.backspace()) onBytes(byteArrayOf(0x7F)) }
    val backspaceLeft = { if (composing.isActive) composing.reset() else onBytes(byteArrayOf(0x17)) }
    val backspaceRight = { if (composing.isActive) composing.reset() else onBytes(byteArrayOf(0x15)) }

    if (pad != PadMode.NONE) {
        // パッド表示中: かな面とまったく同じ骨格 (中央 3 列だけ差し替え、両端は残す)。
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(ZtsBgSecondary)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            Column(
                modifier = Modifier.weight(JP_EDGE_WEIGHT).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(rowSpacing)
            ) {
                JpFuncKey("×", style, Modifier.weight(1f).fillMaxWidth(), accent = true) {
                    pad = PadMode.NONE
                }
                JpFuncKey("◀", style, Modifier.weight(1f).fillMaxWidth(), repeatable = true) {
                    onCursorKey(TerminalEmulator.CursorKey.LEFT)
                }
                JpFuncKey("␣", style, Modifier.weight(1f).fillMaxWidth(), repeatable = true) {
                    insert(" ")
                }
                JpFuncKey(
                    switchLabel, style, Modifier.weight(1f).fillMaxWidth(),
                    fontScale = 0.7f, accent = true
                ) {
                    pad = PadMode.NONE
                    onSwitchFace()
                }
            }
            KeyboardPad(
                mode = pad,
                onMode = { pad = it },
                style = style,
                onInsert = ::insert,
                modifier = Modifier.weight(3f).fillMaxHeight()
            )
            Column(
                modifier = Modifier.weight(JP_EDGE_WEIGHT).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(rowSpacing)
            ) {
                JpBackspaceKeyBody(
                    style = style,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onTap = backspaceTap,
                    onFlickLeft = backspaceLeft,
                    onFlickRight = backspaceRight
                )
                JpFuncKey("▶", style, Modifier.weight(1f).fillMaxWidth(), repeatable = true) {
                    onCursorKey(TerminalEmulator.CursorKey.RIGHT)
                }
                JpFuncKey("⏎", style, Modifier.weight(2f).fillMaxWidth(), repeatable = true) {
                    if (!composing.commitRaw()) onBytes(byteArrayOf(0x0D))
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZtsBgSecondary)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        // Row 1: ESC  1 2 3  ⌫
        JpRow(rowSpacing) {
            // ESC: かな面 ([JapaneseFlickKeyboard]) と**同じ開き方**にする (0.8.348)。
            //   タップ=ESC (変換中なら取り消し) / 上フリック=貼り付けパッド / 下フリック=絵文字パッド。
            // ⚠ **面ごとに開き方が違うのが一番戸惑う。** ここは JpKey (タップのみ) だったので、
            //   数字面では**貼り付けを開く手が 1 つも無く**、絵文字も 😀 の席にしか無かった。
            //   かな面で覚えた指の動きがそのまま通らないので「開かない」としか見えない。
            JpEscKey(
                style = style,
                weight = JP_EDGE_WEIGHT,
                onTap = { if (composing.isActive) composing.reset() else onBytes(byteArrayOf(0x1B)) },
                onFlickUp = { togglePad(PadMode.CLIPBOARD) },
                onFlickDown = { togglePad(PadMode.EMOJI) }
            )
            NumKey("1", style, digitScale, ::insert)
            NumKey("2", style, digitScale, ::insert)
            NumKey("3", style, digitScale, ::insert)
            JpBackspaceKey(
                style = style,
                weight = JP_EDGE_WEIGHT,
                onTap = backspaceTap,
                onFlickLeft = backspaceLeft,
                onFlickRight = backspaceRight
            )
        }
        // Row 2: [◀ / ▼]  4 5 6  [▶ / ▲]  — かな面と同じ積み方 ([JpEdgeStack])。
        JpRow(rowSpacing) {
            JpEdgeStack(
                weight = JP_EDGE_WEIGHT, spacing = rowSpacing,
                top = {
                    JpFuncKey("◀", style, Modifier.fillMaxSize(), repeatable = true) {
                        onCursorKey(TerminalEmulator.CursorKey.LEFT)
                    }
                },
                bottom = {
                    JpFuncKey("▼", style, Modifier.fillMaxSize(), repeatable = true) {
                        onCursorKey(TerminalEmulator.CursorKey.DOWN)
                    }
                }
            )
            NumKey("4", style, digitScale, ::insert)
            NumKey("5", style, digitScale, ::insert)
            NumKey("6", style, digitScale, ::insert)
            JpEdgeStack(
                weight = JP_EDGE_WEIGHT, spacing = rowSpacing,
                top = {
                    JpFuncKey("▶", style, Modifier.fillMaxSize(), repeatable = true) {
                        onCursorKey(TerminalEmulator.CursorKey.RIGHT)
                    }
                },
                bottom = {
                    JpFuncKey("▲", style, Modifier.fillMaxSize(), repeatable = true) {
                        onCursorKey(TerminalEmulator.CursorKey.UP)
                    }
                }
            )
        }
        // Row 3: [😀 / ␣]  7 8 9  [- / /]
        //   右端はかな面で「変換」が座る席。数字面に変換は無いので、残り 2 つの記号を積む。
        JpRow(rowSpacing) {
            JpEdgeStack(
                weight = JP_EDGE_WEIGHT, spacing = rowSpacing,
                top = {
                    JpFuncKey(
                        "😀", style, Modifier.fillMaxSize(),
                        fontScale = 0.85f, accent = pad == PadMode.EMOJI
                    ) { togglePad(PadMode.EMOJI) }
                },
                bottom = {
                    JpFuncKey("␣", style, Modifier.fillMaxSize(), repeatable = true) { insert(" ") }
                }
            )
            NumKey("7", style, digitScale, ::insert)
            NumKey("8", style, digitScale, ::insert)
            NumKey("9", style, digitScale, ::insert)
            JpEdgeStack(
                weight = JP_EDGE_WEIGHT, spacing = rowSpacing,
                top = {
                    JpFuncKey("-", style, Modifier.fillMaxSize(), repeatable = true) { insert("-") }
                },
                bottom = {
                    JpFuncKey("/", style, Modifier.fillMaxSize(), repeatable = true) { insert("/") }
                }
            )
        }
        // Row 4: 面切替(次の面へ)  .  0  :  ⏎
        JpRow(rowSpacing) {
            JpKey(switchLabel, style, fontScale = 0.7f, accent = true, weight = JP_EDGE_WEIGHT) {
                onSwitchFace()
            }
            NumKey(".", style, digitScale, ::insert)
            NumKey("0", style, digitScale, ::insert)
            NumKey(":", style, digitScale, ::insert)
            JpKey("⏎", style, repeatable = true, weight = JP_EDGE_WEIGHT) {
                if (!composing.commitRaw()) onBytes(byteArrayOf(0x0D))
            }
        }
    }
}

/**
 * 数字面の中央 3 列のキー。長押しで連打できる (`255` のように同じ数字を続ける場面がある)。
 */
@Composable
private fun RowScope.NumKey(
    label: String,
    style: KeyboardStyle,
    fontScale: Float,
    onInsert: (String) -> Unit
) {
    JpKey(label, style, fontScale = fontScale, repeatable = true) { onInsert(label) }
}
