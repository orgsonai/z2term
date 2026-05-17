package com.zerotoship.z2term.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.emulator.AvailableThemes
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.ui.theme.TerminalFontFamily
import com.zerotoship.z2term.ui.theme.TerminalFontOptions
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlin.math.roundToInt

/**
 * 設定画面 (ModalBottomSheet として表示)。
 *
 * - テーマ切替 (AvailableThemes)
 * - フォントサイズ (8-32sp)
 * - スクロールバック行数 (500-50000)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    snapshot: AppSettings.Snapshot,
    onThemeChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onScrollbackChange: (Int) -> Unit,
    onDistroChange: (String) -> Unit,
    onFontIdChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZtsBgSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ZtsBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "設定",
                color = ZtsGreen,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            DistroSection(
                current = snapshot.distroId,
                onSelect = onDistroChange
            )

            ThemeSection(
                current = snapshot.themeName,
                onSelect = onThemeChange
            )

            FontSection(
                currentId = snapshot.fontId,
                onSelect = onFontIdChange
            )

            FontSizeSection(
                current = snapshot.fontSizeSp,
                onChange = onFontSizeChange
            )

            ScrollbackSection(
                current = snapshot.scrollbackLines,
                onChange = onScrollbackChange
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DistroSection(current: String, onSelect: (String) -> Unit) {
    SectionHeader("ディストロ (再起動後に反映)")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DistroSpec.ALL.forEach { spec ->
            val selected = spec.id == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = if (selected) ZtsGreen else ZtsBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(ZtsBgPrimary)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onSelect(spec.id) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = ZtsGreen,
                        unselectedColor = ZtsTextSecondary
                    )
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.displayName,
                        color = ZtsTextPrimary,
                        fontFamily = TerminalFontFamily,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${spec.id} · ${spec.packageManagerHint}",
                        color = ZtsTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(current: String, onSelect: (String) -> Unit) {
    SectionHeader("テーマ")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AvailableThemes.forEach { theme ->
            val selected = theme.name == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = if (selected) ZtsGreen else ZtsBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(ZtsBgPrimary)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onSelect(theme.name) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = ZtsGreen,
                        unselectedColor = ZtsTextSecondary
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = theme.name,
                    color = ZtsTextPrimary,
                    fontFamily = TerminalFontFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                ColorSwatch(argb = theme.background)
                Spacer(Modifier.width(4.dp))
                ColorSwatch(argb = theme.green)
                Spacer(Modifier.width(4.dp))
                ColorSwatch(argb = theme.red)
                Spacer(Modifier.width(4.dp))
                ColorSwatch(argb = theme.blue)
            }
        }
    }
}

@Composable
private fun FontSection(currentId: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    SectionHeader("フォント")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TerminalFontOptions.ALL.forEach { option ->
            val available = TerminalFontOptions.isAvailable(context, option)
            val selected = option.id == currentId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = if (selected) ZtsGreen else ZtsBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(ZtsBgPrimary)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { if (available) onSelect(option.id) },
                    enabled = available,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = ZtsGreen,
                        unselectedColor = ZtsTextSecondary
                    )
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.displayName,
                        color = if (available) ZtsTextPrimary else ZtsTextSecondary,
                        fontFamily = TerminalFontFamily,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (available) (option.assetFile ?: "システムフォント") else "assets/fonts/${option.assetFile} 未配置",
                        color = ZtsTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FontSizeSection(current: Float, onChange: (Float) -> Unit) {
    SectionHeader("フォントサイズ: ${current.roundToInt()}sp")
    Slider(
        value = current,
        onValueChange = onChange,
        valueRange = AppSettings.MIN_FONT_SIZE_SP..AppSettings.MAX_FONT_SIZE_SP,
        steps = (AppSettings.MAX_FONT_SIZE_SP - AppSettings.MIN_FONT_SIZE_SP).toInt() - 1,
        colors = SliderDefaults.colors(
            thumbColor = ZtsGreen,
            activeTrackColor = ZtsGreen,
            inactiveTrackColor = ZtsBorder
        )
    )
}

@Composable
private fun ScrollbackSection(current: Int, onChange: (Int) -> Unit) {
    SectionHeader("スクロールバック: ${current} 行")
    Slider(
        value = current.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = AppSettings.MIN_SCROLLBACK_LINES.toFloat()..AppSettings.MAX_SCROLLBACK_LINES.toFloat(),
        steps = 49,  // ~1000 行刻み
        colors = SliderDefaults.colors(
            thumbColor = ZtsGreen,
            activeTrackColor = ZtsGreen,
            inactiveTrackColor = ZtsBorder
        )
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = ZtsTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun ColorSwatch(argb: Int) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(androidx.compose.ui.graphics.Color(argb.toLong() or 0xFF000000))
            .border(0.5.dp, ZtsBorder, RoundedCornerShape(3.dp))
    )
}
