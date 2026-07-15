package com.zerotoship.z2term.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.emulator.DEFAULT_CUSTOM_THEME_NAME
import com.zerotoship.z2term.emulator.TerminalTheme
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.launch

/** 編集対象の色スロット (key は TerminalTheme のフィールド名, ラベルは R.string で言語追従) */
private val COLOR_SLOTS = listOf(
    "background" to com.zerotoship.z2term.R.string.theme_color_background,
    "foreground" to com.zerotoship.z2term.R.string.theme_color_foreground,
    "cursor" to com.zerotoship.z2term.R.string.theme_color_cursor,
    "black" to com.zerotoship.z2term.R.string.theme_color_black,
    "red" to com.zerotoship.z2term.R.string.theme_color_red,
    "green" to com.zerotoship.z2term.R.string.theme_color_green,
    "yellow" to com.zerotoship.z2term.R.string.theme_color_yellow,
    "blue" to com.zerotoship.z2term.R.string.theme_color_blue,
    "magenta" to com.zerotoship.z2term.R.string.theme_color_magenta,
    "cyan" to com.zerotoship.z2term.R.string.theme_color_cyan,
    "white" to com.zerotoship.z2term.R.string.theme_color_white,
    "brightBlack" to com.zerotoship.z2term.R.string.theme_color_bright_black,
    "brightRed" to com.zerotoship.z2term.R.string.theme_color_bright_red,
    "brightGreen" to com.zerotoship.z2term.R.string.theme_color_bright_green,
    "brightYellow" to com.zerotoship.z2term.R.string.theme_color_bright_yellow,
    "brightBlue" to com.zerotoship.z2term.R.string.theme_color_bright_blue,
    "brightMagenta" to com.zerotoship.z2term.R.string.theme_color_bright_magenta,
    "brightCyan" to com.zerotoship.z2term.R.string.theme_color_bright_cyan,
    "brightWhite" to com.zerotoship.z2term.R.string.theme_color_bright_white
)

/**
 * ユーザー独自テーマの編集シート。
 *
 * [base] (現在選択中テーマ) または [existing] (既存の独自テーマ) を初期値に、各色を
 * #RRGGBB で編集する。緑がアプリのアクセント色に、背景/前景がアプリ全体の地色に反映される。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThemeSheet(
    base: TerminalTheme,
    existing: TerminalTheme?,
    onSave: (TerminalTheme) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val initial = existing ?: base
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var forceClose by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden) forceClose || scrollState.value == 0 else true
        }
    )
    val closeSheet: () -> Unit = {
        forceClose = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    // 新規作成時の初期テーマ名 (= "マイテーマ"/"My theme") は言語設定に追従させる。
    // 編集モードでは保存済みの name を使う。空欄保存時もここに戻す。
    val defaultName = stringResource(R.string.default_custom_theme_name)
    var name by remember { mutableStateOf(existing?.name ?: defaultName) }
    val hex = remember {
        mutableStateMapOf<String, String>().apply {
            COLOR_SLOTS.forEach { (key, _) -> put(key, colorToHex(initial.componentOf(key))) }
        }
    }

    fun parse(key: String): Int = hexToColorOrNull(hex[key] ?: "") ?: initial.componentOf(key)
    fun buildTheme(): TerminalTheme = TerminalTheme(
        name = name.ifBlank { defaultName },
        foreground = parse("foreground"), background = parse("background"), cursor = parse("cursor"),
        black = parse("black"), red = parse("red"), green = parse("green"), yellow = parse("yellow"),
        blue = parse("blue"), magenta = parse("magenta"), cyan = parse("cyan"), white = parse("white"),
        brightBlack = parse("brightBlack"), brightRed = parse("brightRed"),
        brightGreen = parse("brightGreen"), brightYellow = parse("brightYellow"),
        brightBlue = parse("brightBlue"), brightMagenta = parse("brightMagenta"),
        brightCyan = parse("brightCyan"), brightWhite = parse("brightWhite")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZtsBgPrimary,
        contentColor = ZtsTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        contentWindowInsets = { WindowInsets.systemBars },
        dragHandle = { Z2TermDragHandle(onClose = closeSheet) }
    ) {
        BackHandler(onBack = closeSheet)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existing == null) stringResource(R.string.theme_title_new)
                       else stringResource(R.string.theme_title_edit),
                color = ZtsGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )

            // 名前
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ssh_field_name), color = ZtsTextSecondary, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.width(80.dp)
                )
                EditBox(value = name, onChange = { name = it }, modifier = Modifier.weight(1f))
            }

            // プレビュー (現在の入力で即更新)
            ThemePreview(buildTheme())

            // 色入力
            COLOR_SLOTS.forEach { (key, labelRes) ->
                HexField(
                    label = stringResource(labelRes),
                    value = hex[key] ?: "",
                    onChange = { hex[key] = it }
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.theme_action_save_apply), accent = true) {
                    onSave(buildTheme())
                }
                if (existing != null) {
                    PillButton(stringResource(R.string.ssh_action_delete), danger = true, onClick = onDelete)
                }
                Box(Modifier.weight(1f))
                PillButton(stringResource(R.string.action_cancel), onClick = closeSheet)
            }
            Text(
                text = stringResource(R.string.theme_hint),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ThemePreview(theme: TerminalTheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(theme.background))
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("user@host:~$ ls", color = Color(theme.foreground), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("dir", color = Color(theme.blue), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("file", color = Color(theme.green), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("err", color = Color(theme.red), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("warn", color = Color(theme.yellow), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.theme_preview_cursor), color = Color(theme.foreground), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(width = 8.dp, height = 14.dp).background(Color(theme.cursor)))
        }
    }
}

@Composable
private fun HexField(label: String, value: String, onChange: (String) -> Unit) {
    val parsed = hexToColorOrNull(value)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = ZtsTextSecondary, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.width(80.dp)
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(parsed?.let { Color(it) } ?: ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(8.dp))
        EditBox(
            value = value,
            onChange = onChange,
            modifier = Modifier.width(110.dp),
            isError = parsed == null
        )
    }
}

@Composable
private fun EditBox(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(
            color = if (isError) ZtsError else ZtsTextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        ),
        cursorBrush = SolidColor(ZtsGreen),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, if (isError) ZtsError else ZtsBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun PillButton(
    label: String,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val border = when { accent -> ZtsGreen; danger -> ZtsError; else -> ZtsBorder }
    val fg = when { accent -> ZtsGreen; danger -> ZtsError; else -> ZtsTextPrimary }
    val bg = if (accent) ZtsGreen.copy(alpha = 0.12f) else ZtsBgCard
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

// --- helpers ---

private fun TerminalTheme.componentOf(key: String): Int = when (key) {
    "foreground" -> foreground
    "background" -> background
    "cursor" -> cursor
    "black" -> black
    "red" -> red
    "green" -> green
    "yellow" -> yellow
    "blue" -> blue
    "magenta" -> magenta
    "cyan" -> cyan
    "white" -> white
    "brightBlack" -> brightBlack
    "brightRed" -> brightRed
    "brightGreen" -> brightGreen
    "brightYellow" -> brightYellow
    "brightBlue" -> brightBlue
    "brightMagenta" -> brightMagenta
    "brightCyan" -> brightCyan
    "brightWhite" -> brightWhite
    else -> foreground
}

private fun colorToHex(argb: Int): String = String.format("#%06X", 0xFFFFFF and argb)

private fun hexToColorOrNull(s: String): Int? {
    val h = s.trim().removePrefix("#")
    if (h.length != 6) return null
    val v = h.toIntOrNull(16) ?: return null
    return 0xFF000000.toInt() or v
}
