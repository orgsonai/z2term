package com.zerotoship.z2term.ui.settings

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.automation.UiBridge
import com.zerotoship.z2term.automation.UiBridgePolicy
import com.zerotoship.z2term.automation.UiBridgePolicy.Level
import com.zerotoship.z2term.automation.UiBridgeSettings
import com.zerotoship.z2term.edge.AndroidActions
import com.zerotoship.z2term.edge.AppPickerActivity
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import com.zerotoship.z2term.ui.theme.ZtsWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * コマンド一覧 → 自動化 → AI 操作。`z2-ui` の許可を決める画面 (設計は docs/ja/AI-BRIDGE.md)。
 *
 * ⚠ ここで変えた許可は**承認なしで**反映する (人が自分で操作しているため)。AI が `z2-ui allow` で
 * 広げるときだけ通知で承認を取る ([UiBridge])。
 */
@Composable
fun UiBridgeBody() {
    val context = LocalContext.current
    val app = context.applicationContext
    val settings = remember { UiBridgeSettings(app) }
    val scope = rememberCoroutineScope()
    // CLI からの変更 (承認後の allow・通知の停止) も開いたまま反映する。
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> tick++ }
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val enabled = remember(tick) { settings.enabled }
    val stopped = remember(tick) { settings.stopped }
    val levels = remember(tick) { settings.levels() }
    val minutes = remember(tick) { settings.grantMinutes }
    var editingGrant by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(UiBridge.isPaused) }

    Text(stringResource(R.string.ui_bridge_title), color = ZtsGreen, fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    ToggleRow(
        title = stringResource(R.string.ui_bridge_enable),
        desc = stringResource(R.string.ui_bridge_desc),
        checked = enabled,
        onChange = { on ->
            settings.enabled = on
            if (!on) UiBridge.disabled(app)
        }
    )
    if (stopped) HintBox(stringResource(R.string.ui_bridge_stopped_note))
    if (enabled && paused) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_bridge_paused_note), color = ZtsWarning, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            PillButton(label = stringResource(R.string.ui_bridge_resume), accent = true) {
                UiBridge.resume(app); paused = false
            }
        }
    }
    if (!AndroidActions.connected()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_bridge_accessibility_needed), color = ZtsWarning, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            PillButton(label = stringResource(R.string.ui_bridge_open_accessibility)) {
                runCatching { AndroidActions.command(context, listOf("permission")) }
            }
        }
    }

    SectionTitle(stringResource(R.string.ui_bridge_default_apps))
    LevelChoice(levels[UiBridgePolicy.DEFAULT_PATTERN] ?: UiBridgePolicy.DEFAULT_LEVEL) {
        settings.setLevel(UiBridgePolicy.DEFAULT_PATTERN, it)
    }

    SectionTitle(stringResource(R.string.ui_bridge_apps_title))
    levels.filterKeys { it != UiBridgePolicy.DEFAULT_PATTERN }.toSortedMap().forEach { (pkg, level) ->
        key(pkg) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ZtsBgCard)
                    .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp)).padding(start = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(UiBridge.label(app, pkg), color = ZtsTextPrimary, fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pkg, color = ZtsTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconCell(label = "✕", danger = true) { settings.setLevel(pkg, null) }
                }
                LevelChoice(level) { settings.setLevel(pkg, it) }
            }
        }
    }
    PillButton(label = stringResource(R.string.ui_bridge_add_app)) {
        scope.launch {
            val pkg = withContext(Dispatchers.IO) { runCatching { AppPickerActivity.pick(app) }.getOrNull() }
                ?: return@launch
            // 常に断る画面は一覧に足しても意味が無いので足さない (段階を変えても効かず、誤解を生む)。
            if (pkg !in UiBridge.protectedPackages(app) && pkg !in settings.levels())
                settings.setLevel(pkg, Level.ASK)
        }
    }

    // まとめて許可する時間。見出しの長押しで 61 分以上を入れられる (裏設定: UiBridgePolicy.GRANT_MAX_MINUTES)。
    Text(
        stringResource(R.string.ui_bridge_grant_title), color = ZtsTextSecondary, fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 6.dp).pointerInput(Unit) { detectTapGestures(onLongPress = { editingGrant = true }) }
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Slider(
            value = minutes.coerceAtMost(UiBridgePolicy.GRANT_SLIDER_MAX).toFloat(),
            onValueChange = { settings.grantMinutes = it.toInt() },
            valueRange = 1f..UiBridgePolicy.GRANT_SLIDER_MAX.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = ZtsGreen, activeTrackColor = ZtsGreen, inactiveTrackColor = ZtsBorder)
        )
        // 61 分以上でも実際の値を出す (60 に丸めると、確認なしで進む時間を取り違える)。
        Text(stringResource(R.string.ui_bridge_grant_value, minutes), color = ZtsTextPrimary, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace)
    }
    HintBox(stringResource(R.string.ui_bridge_grant_desc, minutes))
    HintBox(stringResource(R.string.ui_bridge_protected_note) + "\n" + stringResource(R.string.ui_bridge_log_note))

    if (editingGrant) {
        var text by remember { mutableStateOf(minutes.toString()) }
        val value = text.toIntOrNull()?.takeIf { it in 1..UiBridgePolicy.GRANT_MAX_MINUTES }
        AlertDialog(
            onDismissRequest = { editingGrant = false },
            containerColor = ZtsBgPrimary,
            title = { Text(stringResource(R.string.ui_bridge_grant_input_title), fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
            text = {
                Field(label = stringResource(R.string.ui_bridge_grant_title), value = text, onChange = { text = it.take(4) },
                    placeholder = stringResource(R.string.ui_bridge_grant_input_hint), keyboardType = KeyboardType.Number)
            },
            confirmButton = {
                PillButton(label = stringResource(R.string.ui_bridge_set), accent = true, enabled = value != null) {
                    value?.let { settings.grantMinutes = it }
                    editingGrant = false
                }
            },
            dismissButton = { PillButton(label = stringResource(R.string.action_cancel)) { editingGrant = false } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = ZtsTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 6.dp))
}

/** 4 段階から 1 つ選ぶ。選んでいるものだけ緑の枠。 */
@Composable
private fun LevelChoice(selected: Level, onSelect: (Level) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Level.entries.forEach { level ->
            val on = level == selected
            Text(
                UiBridge.levelName(context, level),
                color = if (on) ZtsGreen else ZtsTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                    .background(if (on) ZtsGreen.copy(alpha = 0.18f) else ZtsBgCard)
                    .border(1.dp, if (on) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
                    .clickable { onSelect(level) }.padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
