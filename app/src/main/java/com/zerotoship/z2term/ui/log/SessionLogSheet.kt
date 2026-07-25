package com.zerotoship.z2term.ui.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 端末ログの詳細設定シート (ツールバー ⏺ の**ダブルタップ**で開く)。
 *
 * 短押し = 記録の開始/停止という「ワンタップで済む」経路は必ず残し、細かい設定はここに寄せる
 * (ツールバーのボタンは長押しが並べ替えで埋まっているため、ダブルタップを入口にしている
 *  = 📋 のクリップボード履歴・⌨ のキーボード開閉と同じ作法)。
 *
 * 記録の ON/OFF は**このタブだけ**に効く。保存先・ファイル名・書式などの設定はアプリ全体で共通で、
 * **次に記録を始めるときから**反映される (記録中に変えても書き込み中のファイルは変わらない)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLogSheet(
    session: TerminalSession,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val settings by session.settingsFlow.collectAsState()
    val log by session.logState.collectAsState()
    var forceClose by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // スクロール途中の下スワイプで誤って閉じないよう、最上部のときだけスワイプ閉じを許可。
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden) forceClose || scrollState.value == 0 else true
        }
    )
    val closeSheet: () -> Unit = {
        forceClose = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.log_sheet_title),
                color = ZtsGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )

            // --- 記録の ON/OFF (このタブ) ---
            LogToggle(
                title = stringResource(R.string.log_recording),
                description = stringResource(R.string.log_recording_desc),
                checked = log.recording,
                onChange = { if (it) session.startLogging() else session.stopLogging() }
            )

            // 自動開始はアプリ全体の設定 (次に開くタブから効く)。記録の ON/OFF のすぐ下に置くのは、
            // 「録り忘れた」と気付いた人がその場で二度と忘れない設定に手が届くようにするため。
            LogToggle(
                title = stringResource(R.string.log_auto_start),
                description = stringResource(R.string.log_auto_start_desc),
                checked = settings.sessionLogAutoStart,
                onChange = { session.setSessionLogAutoStart(it) }
            )

            // 書き込み中 (または直前に書いた) ファイル。ローテーションしないのでサイズを必ず出す。
            if (log.path.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = log.path,
                        color = ZtsTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = formatSize(log.bytes),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    // 端末から中身を見るためのコマンド。1 タップでコピーしてそのまま貼れる。
                    LogActionButton(
                        label = stringResource(R.string.log_copy_open_command),
                        onClick = { copyToClipboard(context, "less ${shellQuote(log.path)}") }
                    )
                }
            }

            LogDivider()

            // --- 保存先とファイル名 (次に開始するときから有効) ---
            LogTextField(
                title = stringResource(R.string.log_dir),
                description = stringResource(R.string.log_dir_desc),
                value = settings.sessionLogDir,
                placeholder = AppSettings.DEFAULT_SESSION_LOG_DIR,
                onChange = { session.setSessionLogDir(it) }
            )
            LogTextField(
                title = stringResource(R.string.log_name),
                description = stringResource(R.string.log_name_desc),
                value = settings.sessionLogNameTemplate,
                placeholder = AppSettings.DEFAULT_SESSION_LOG_NAME,
                onChange = { session.setSessionLogNameTemplate(it) }
            )
            LogTextField(
                title = stringResource(R.string.log_time_format),
                description = stringResource(R.string.log_time_format_desc),
                value = settings.sessionLogTimeFormat,
                placeholder = AppSettings.DEFAULT_SESSION_LOG_TIME,
                onChange = { session.setSessionLogTimeFormat(it) }
            )
            // 設定した書式で実際にどんな名前になるかを出す (書式の書き間違いにすぐ気付ける)。
            Text(
                text = stringResource(
                    R.string.log_name_preview,
                    "~/${settings.sessionLogDir.trim().trim('/').ifBlank { AppSettings.DEFAULT_SESSION_LOG_DIR }}/" +
                        previewName(settings, session.label.collectAsState().value)
                ),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            LogDivider()

            // --- 記録の内容 ---
            LogToggle(
                title = stringResource(R.string.log_include_scrollback),
                description = stringResource(R.string.log_include_scrollback_desc),
                checked = settings.sessionLogIncludeScrollback,
                onChange = { session.setSessionLogIncludeScrollback(it) }
            )
            LogToggle(
                title = stringResource(R.string.log_append),
                description = stringResource(R.string.log_append_desc),
                checked = settings.sessionLogAppend,
                onChange = { session.setSessionLogAppend(it) }
            )
            LogToggle(
                title = stringResource(R.string.log_alt_screen),
                description = stringResource(R.string.log_alt_screen_desc),
                checked = settings.sessionLogAltScreen,
                onChange = { session.setSessionLogAltScreen(it) }
            )
            LogToggle(
                title = stringResource(R.string.log_raw),
                description = stringResource(R.string.log_raw_desc),
                checked = settings.sessionLogRaw,
                onChange = { session.setSessionLogRaw(it) }
            )
            LogToggle(
                title = stringResource(R.string.log_mask),
                description = stringResource(R.string.log_mask_desc),
                checked = settings.sessionLogMaskSecrets,
                onChange = { session.setSessionLogMaskSecrets(it) }
            )

            LogDivider()

            // 画面に出たものはそのまま入る、を隠さずに書く (記録中はボタンが点灯している、と対で守る約束)。
            // 伏せ字は「完全ではない」を必ず添える — ここを書かないと、伏せ字 ON を安全の保証と
            // 受け取られてしまう。
            Text(
                text = stringResource(R.string.log_secret_warning),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** 設定どおりに組み立てたときのファイル名 (プレビュー用。実際の採番 `-2` 等は付けない)。 */
private fun previewName(settings: AppSettings.Snapshot, label: String): String {
    val stamp = runCatching {
        SimpleDateFormat(
            settings.sessionLogTimeFormat.ifBlank { AppSettings.DEFAULT_SESSION_LOG_TIME },
            Locale.US
        ).format(Date())
    }.getOrElse { "????" }
    val tab = label.ifBlank { "term" }.replace(Regex("""[^A-Za-z0-9._\-]"""), "_").take(32)
    return settings.sessionLogNameTemplate.ifBlank { AppSettings.DEFAULT_SESSION_LOG_NAME }
        .replace("{date}", stamp)
        .replace("{tab}", tab)
        .replace(Regex("""[/\\:*?"<>| ]"""), "_")
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/** 端末に貼るときのためにパスをクォートする (スペース入りの保存先でも壊れないように)。 */
private fun shellQuote(path: String): String =
    if (path.none { it.isWhitespace() || it in "'\"\\$`" }) path
    else "'" + path.replace("'", "'\\''") + "'"

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("z2term", text))
}

@Composable
private fun LogDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(ZtsBorder)
            .padding(top = 1.dp)
    )
}

@Composable
private fun LogToggle(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ZtsTextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = description,
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ZtsGreen,
                uncheckedThumbColor = ZtsTextSecondary,
                uncheckedTrackColor = ZtsBgCard,
                uncheckedBorderColor = ZtsBorder
            )
        )
    }
}

@Composable
private fun LogTextField(
    title: String,
    description: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit
) {
    // 外から値が変わったときだけ差し替える (打っている最中にカーソルが飛ばないように)。
    var draft by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    remember(value) {
        if (value != draft.text) draft = TextFieldValue(value, TextRange(value.length))
        value
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = ZtsTextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = description,
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (draft.text.isEmpty()) {
                Text(
                    text = placeholder,
                    color = ZtsTextSecondary.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it; onChange(it.text) },
                textStyle = TextStyle(
                    color = ZtsTextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(ZtsGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LogActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = ZtsTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
