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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.service.ServerDaemonManager
import com.zerotoship.z2term.service.ServerDaemonService
import com.zerotoship.z2term.settings.ServerEntry
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 常駐サーバー管理シート (設定の「常駐サーバー」から開く)。
 *
 * 任意のサーバー (sshd/http/smb 等) を **起動コマンド**として登録し、[ServerDaemonService] で
 * まとめて常駐させる。特定サーバーはハードコードせず、ユーザーがコマンドを自由に書く汎用機構。
 *  - 一覧: 各サーバーの ON/OFF・稼働状態・編集・削除。
 *  - 「起動時に自動で常駐」: 端末起動直後にアプリを開かず自動起動 ([serversAutostartOnBoot])。
 *  - 「起動/停止」: いま全サーバーを一括で起動/停止する。停止は通知の「サーバー停止」でも可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersSheet(
    session: TerminalSession,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
                .padding(bottom = 24.dp)
        ) {
            ServersBody(session = session)
        }
    }
}

/**
 * 常駐サーバー管理の本体 (シートの中身)。スクロールは呼び出し側が持つ。
 *
 * 設定シートの「サーバーを管理」([ServersSheet]) と、ツールシート (📜) の
 * 「サーバー」タブの両方から同じ UI を使うためにここへ切り出してある。
 */
@Composable
fun ServersBody(session: TerminalSession) {
    val context = LocalContext.current

    val settings by session.settingsFlow.collectAsState()
    val entries = remember(settings.serverEntries) { ServerEntry.decode(settings.serverEntries) }

    // 永続化と同時に、稼働中の supervisor へも反映する (A3・無停止リロード)。
    // 追加・編集・削除のどれも、supervisor や他のサーバーを止めずに 2 秒以内に効く。
    fun persist(list: List<ServerEntry>) {
        session.setServerEntries(ServerEntry.encode(list))
        ServerDaemonManager.syncEntries(context, list)
    }

    var editing by remember { mutableStateOf<ServerEntry?>(null) }
    var isNew by remember { mutableStateOf(false) }

    // 稼働状態を定期ポーリング (表示している間だけ)。
    var running by remember { mutableStateOf(ServerDaemonManager.isRunning) }
    var statuses by remember { mutableStateOf(emptyList<ServerDaemonManager.ServerStatus>()) }
    LaunchedEffect(Unit) {
        while (true) {
            running = ServerDaemonManager.isRunning
            statuses = ServerDaemonManager.readStatus(context)
            delay(1500)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val currentEdit = editing
        if (currentEdit != null) {
            ServerEditForm(
                initial = currentEdit,
                isNew = isNew,
                onSave = { saved ->
                    val list = entries.toMutableList()
                    val idx = list.indexOfFirst { it.id == saved.id }
                    if (idx >= 0) list[idx] = saved else list.add(saved)
                    persist(list)
                    editing = null
                },
                onCancel = { editing = null }
            )
            return@Column
        }

        // ヘッダ + 新規追加
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.servers_title),
                color = ZtsGreen,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Box(modifier = Modifier.weight(1f))
            PillButton(label = stringResource(R.string.servers_new), accent = true) {
                isNew = true
                editing = ServerEntry(id = ServerEntry.newId(), name = "", command = "", enabled = true)
            }
        }

        // 起動/停止 + 状態
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (running) stringResource(R.string.servers_state_running)
                else stringResource(R.string.servers_state_stopped),
                color = if (running) ZtsGreen else ZtsTextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Box(modifier = Modifier.weight(1f))
            if (running) {
                PillButton(label = stringResource(R.string.servers_stop), danger = true) {
                    ServerDaemonService.stop(context)
                }
            } else {
                PillButton(label = stringResource(R.string.servers_start), accent = true) {
                    ServerDaemonService.start(context)
                }
            }
        }

        // 起動時に自動で常駐
        ToggleRow(
            title = stringResource(R.string.servers_autostart_boot),
            desc = stringResource(R.string.servers_autostart_boot_desc),
            checked = settings.serversAutostartOnBoot,
            onChange = { session.setServersAutostartOnBoot(it) }
        )

        // 省電力モード (WakeLock/WifiLock を握らない)。次回の起動から反映。
        ToggleRow(
            title = stringResource(R.string.servers_low_power),
            desc = stringResource(R.string.servers_low_power_desc),
            checked = settings.serversLowPower,
            onChange = { session.setServersLowPower(it) }
        )

        if (entries.isEmpty()) {
            HintBox(stringResource(R.string.servers_empty))
        } else {
            entries.forEach { e ->
                val st = statuses.firstOrNull { it.id == e.id }
                ServerRow(
                    entry = e,
                    stateLabel = if (running && e.enabled) st?.state else null,
                    status = if (running) st else null,
                    onToggle = { checked ->
                        // 設定を永続化しつつ、稼働中なら該当サーバーだけを即時 起動/停止する
                        // (supervisor を再起動しないので他サーバーは止まらない)。
                        persist(entries.map { if (it.id == e.id) it.copy(enabled = checked) else it })
                        if (running) ServerDaemonManager.setWant(context, e.id, checked)
                    },
                    onEdit = { isNew = false; editing = e },
                    onDelete = { persist(entries.filterNot { it.id == e.id }) }
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        HintBox(stringResource(R.string.servers_hint))
    }
}

@Composable
private fun ServerRow(
    entry: ServerEntry,
    stateLabel: String?,
    status: ServerDaemonManager.ServerStatus?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    // ログは開いている間だけ読む (常時読むと一覧のポーリングが重くなる)。
    var logOpen by remember(entry.id) { mutableStateOf(false) }
    var logText by remember(entry.id) { mutableStateOf("") }
    var logBytes by remember(entry.id) { mutableStateOf(0L) }
    LaunchedEffect(logOpen, entry.id) {
        while (logOpen) {
            logText = ServerDaemonManager.readLog(context, entry.id)
            logBytes = ServerDaemonManager.logSize(context, entry.id)
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = entry.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ZtsGreen,
                    checkedTrackColor = ZtsGreen.copy(alpha = 0.3f)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name.ifBlank { entry.safeToken() },
                        color = ZtsTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    if (stateLabel != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[$stateLabel]",
                            color = if (stateLabel == "running") ZtsGreen else ZtsTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = entry.command.ifBlank { stringResource(R.string.servers_no_command) },
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                // 再起動回数と直近の終了コード (A3)。増え続けているなら「起動しては落ちる」を
                // 繰り返しているので、ログを見るきっかけになる。
                if (status != null && (status.restarts > 0 || status.lastExit != null)) {
                    Text(
                        text = stringResource(
                            R.string.servers_restart_info,
                            status.restarts,
                            status.lastExit ?: "-"
                        ),
                        color = if (status.restarts > 0) ZtsError else ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            IconCell(label = "▤", onClick = { logOpen = !logOpen })
            IconCell(label = "✎", onClick = onEdit)
            IconCell(label = "✕", danger = true, onClick = onDelete)
        }

        if (logOpen) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.servers_log_title, formatBytes(logBytes)),
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                PillButton(label = stringResource(R.string.servers_log_clear)) {
                    ServerDaemonManager.clearLog(context, entry.id)
                    logText = ""
                    logBytes = 0L
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ZtsBgPrimary)
                    .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Text(
                    text = logText.ifBlank { stringResource(R.string.servers_log_empty) },
                    color = if (logText.isBlank()) ZtsTextSecondary else ZtsTextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun ServerEditForm(
    initial: ServerEntry,
    isNew: Boolean,
    onSave: (ServerEntry) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var command by remember(initial.id) { mutableStateOf(initial.command) }

    Text(
        text = if (isNew) stringResource(R.string.servers_new_entry_title)
        else stringResource(R.string.servers_edit_entry_title),
        color = ZtsGreen,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace
    )

    // プリセット (雛形)。タップで name/command を埋める。中身は自由に編集できる。
    if (isNew) {
        Text(
            text = stringResource(R.string.servers_preset),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ServerEntry.PRESETS.forEach { p ->
                PillButton(label = p.label, fill = true) {
                    name = p.name
                    command = p.command
                }
            }
        }
    }

    Field(
        label = stringResource(R.string.servers_name_field),
        value = name,
        onChange = { name = it },
        placeholder = "http"
    )
    Field(
        label = stringResource(R.string.servers_command_field),
        value = command,
        onChange = { command = it },
        placeholder = "python3 -m http.server 8080",
        multiline = true
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PillButton(label = stringResource(R.string.action_cancel), onClick = onCancel)
        Box(modifier = Modifier.weight(1f))
        PillButton(label = stringResource(R.string.action_save), accent = true) {
            if (command.isNotBlank()) {
                onSave(initial.copy(name = name.trim(), command = command.trim()))
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ZtsTextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Text(desc, color = ZtsTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ZtsGreen,
                checkedTrackColor = ZtsGreen.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun HintBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(text = text, color = ZtsTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun IconCell(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (danger) ZtsError else ZtsTextSecondary,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PillButton(
    label: String,
    accent: Boolean = false,
    danger: Boolean = false,
    fill: Boolean = false,
    onClick: () -> Unit
) {
    val border = when { danger -> ZtsError; accent -> ZtsGreen; else -> ZtsBorder }
    val fg = when { danger -> ZtsError; accent -> ZtsGreen; else -> ZtsTextPrimary }
    val bg = if (accent) ZtsGreen.copy(alpha = 0.18f) else ZtsBgSecondary
    Box(
        modifier = Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    multiline: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = ZtsTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = ZtsTextSecondary.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (multiline) 4 else 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = !multiline,
                textStyle = TextStyle(color = ZtsTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(ZtsGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
