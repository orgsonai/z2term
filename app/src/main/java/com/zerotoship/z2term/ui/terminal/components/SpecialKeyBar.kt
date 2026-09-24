package com.zerotoship.z2term.ui.terminal.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.zerotoship.z2term.ui.terminal.input.KeyModifiers
import com.zerotoship.z2term.ui.terminal.keyboard.ComposingState
import com.zerotoship.z2term.ui.terminal.keyboard.KeyboardFaceEntry
import com.zerotoship.z2term.ui.terminal.keyboard.KeyboardStyle
import com.zerotoship.z2term.ui.terminal.keyboard.NamedKey
import com.zerotoship.z2term.ui.terminal.keyboard.TerminalKeyboard
import com.zerotoship.z2term.ui.terminal.keyboard.specialKeyLayoutFromJson

/** 端末と GUI で、同じ編集済み配列・ジェスチャ・修飾キー処理を使う。 */
@Composable
fun SpecialKeyBar(
    layoutJson: String,
    composing: ComposingState,
    ctrlState: MutableState<Boolean>,
    onBytes: (ByteArray) -> Unit,
    onKey: (NamedKey, KeyModifiers) -> Unit,
    onNamedKey: ((NamedKey) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val layout = remember(layoutJson) { specialKeyLayoutFromJson(layoutJson) }
    TerminalKeyboard(
        onBytes = onBytes,
        onKey = onKey,
        onNamedKey = onNamedKey,
        composing = composing,
        style = KeyboardStyle.COMPACT,
        faceEntries = listOf(KeyboardFaceEntry.custom(layout)),
        accessoryBar = true,
        ctrlState = ctrlState,
        modifier = modifier,
    )
}
