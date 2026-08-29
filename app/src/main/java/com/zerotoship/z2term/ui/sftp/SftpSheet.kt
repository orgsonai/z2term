package com.zerotoship.z2term.ui.sftp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.RemoteFs
import com.zerotoship.z2term.channel.RemoteFsFactory
import com.zerotoship.z2term.channel.RemotePath
import com.zerotoship.z2term.channel.RemoteService
import com.zerotoship.z2term.channel.SftpEntry
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.ui.components.ConfirmDialog
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SFTP / FTP / WebDAV / SMB 共通ファイルブラウザ (全画面ページ)。
 *
 * 指定 [profile] のプロトコルで接続し、リモートのファイルを一覧 / 移動 / ダウンロード /
 * アップロード / 削除 / 名前変更 / フォルダ作成できる。ダウンロード/アップロードは
 * Android の SAF (CreateDocument / OpenDocument) と連携する。
 *
 * 従来は下から重なる ModalBottomSheet だったが、一覧を下へスクロールする操作が
 * 「シートを閉じる」ドラッグと競合して勝手に閉じてしまうため、設定ページと同じ
 * 「別ページ (全画面)」に変更した (要望)。戻る矢印 / システムバックはフォルダ内なら
 * 1 階層上へ移動し、ルートでだけ接続終了の確認を出す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpSheet(
    profile: SshProfile,
    service: RemoteService? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var client by remember { mutableStateOf<RemoteFs?>(null) }
    var connecting by remember { mutableStateOf(true) }
    var connError by remember { mutableStateOf<String?>(null) }
    var currentPath by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<SftpEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    // ダイアログ状態
    var renameTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var mkdirOpen by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<SftpEntry?>(null) }
    var exitConfirmOpen by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun requestBack() {
        if (currentPath != "/") {
            currentPath = RemotePath.resolve(currentPath, "..")
        } else {
            exitConfirmOpen = true
        }
    }

    // 接続 (1 度だけ)
    LaunchedEffect(profile.id, service?.id) {
        connecting = true
        connError = null
        runCatching { RemoteFsFactory.connect(profile, context, service) }
            .onSuccess { c ->
                client = c
                currentPath = c.home
                connecting = false
            }
            .onFailure { e ->
                connError = e.message ?: e.javaClass.simpleName
                connecting = false
            }
    }

    // currentPath / refreshTick 変化で ls
    LaunchedEffect(client, currentPath, refreshTick) {
        val c = client ?: return@LaunchedEffect
        loading = true
        runCatching { c.list(currentPath) }
            .onSuccess { entries = it }
            .onFailure { toast(context.getString(R.string.sftp_toast_list_failed, it.message ?: "")) }
        loading = false
    }

    // 破棄時に接続を閉じる (別スレッドで disconnect)
    DisposableEffect(Unit) {
        onDispose { val c = client; if (c != null) Thread { c.close() }.start() }
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val entry = pendingDownload
        pendingDownload = null
        if (uri != null && entry != null) {
            val remote = RemotePath.resolve(currentPath, entry.name)
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            client?.download(remote, os)
                        } ?: error(context.getString(R.string.sftp_toast_save_target_unavailable))
                    }
                }.onSuccess { toast(context.getString(R.string.sftp_toast_download_complete, entry.name)) }
                    .onFailure { toast(context.getString(R.string.sftp_toast_download_failed, it.message ?: "")) }
            }
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val name = queryDisplayName(context, uri) ?: "uploaded_file"
                    val remote = RemotePath.resolve(currentPath, name)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            client?.upload(ins, remote)
                        } ?: error(context.getString(R.string.sftp_toast_open_file_failed))
                    }
                    name
                }.onSuccess { toast(context.getString(R.string.sftp_toast_upload_complete, it)); refreshTick++ }
                    .onFailure { toast(context.getString(R.string.sftp_toast_upload_failed, it.message ?: "")) }
            }
        }
    }

    // 全画面の「別ページ」として表示する。背景はバー裏まで塗りつつ、中身はシステムバー
    // (上=ステータス / 下=ナビゲーション) の内側に収める。
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        color = ZtsBgPrimary,
        contentColor = ZtsTextPrimary
    ) {
        BackHandler(onBack = ::requestBack)
        Column(modifier = Modifier.fillMaxSize()) {
        // ヘッダ: 戻る矢印 + プロファイル名 (設定ページと同じ上部バー)
        SftpTopBar(
            title = if (service == null) {
                "${profile.fileProtocolLabel} : ${profile.endpointDescription()}"
            } else {
                "${service.protocol.name} : ${service.endpointDescription(profile)}"
            },
            onBack = ::requestBack
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 現在パス + 上へ / 更新
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentPath,
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ZtsGreen
                    )
                    Spacer(Modifier.width(8.dp))
                }
                PillButton(stringResource(R.string.sftp_button_up), enabled = client != null && currentPath != "/") {
                    currentPath = RemotePath.resolve(currentPath, "..")
                }
                Spacer(Modifier.width(6.dp))
                PillButton("⟳", enabled = client != null) { refreshTick++ }
            }

            when {
                connecting -> CenterStatus(stringResource(R.string.sftp_status_connecting))
                connError != null -> CenterStatus(stringResource(R.string.sftp_status_connect_failed, connError ?: ""), isError = true)
                else -> {
                    LazyColumn(
                        state = listState,
                        // 全画面ページなので、残りの高さいっぱいまで一覧を伸ばす
                        // (下のアクション行は常に画面下に残る)。
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(entries, key = { it.name }) { entry ->
                            SftpRow(
                                entry = entry,
                                onOpen = {
                                    if (entry.isDir || entry.isLink) {
                                        currentPath = RemotePath.resolve(currentPath, entry.name)
                                    }
                                },
                                onDownload = {
                                    pendingDownload = entry
                                    downloadLauncher.launch(entry.name)
                                },
                                onRename = { renameTarget = entry },
                                onDelete = { deleteTarget = entry }
                            )
                        }
                        if (entries.isEmpty() && !loading) {
                            item {
                                Text(
                                    stringResource(R.string.sftp_status_empty_dir),
                                    color = ZtsTextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(stringResource(R.string.sftp_button_upload), enabled = client != null) {
                            uploadLauncher.launch(arrayOf("*/*"))
                        }
                        PillButton(stringResource(R.string.sftp_button_new_folder), enabled = client != null) {
                            mkdirOpen = true
                        }
                    }
                }
            }
        }
        }
    }

    // 名前変更ダイアログ
    renameTarget?.let { target ->
        InputDialog(
            title = stringResource(R.string.sftp_dialog_rename_title),
            initial = target.name,
            confirmLabel = stringResource(R.string.sftp_dialog_rename_confirm),
            onConfirm = { newName ->
                renameTarget = null
                if (newName.isNotBlank() && newName != target.name) {
                    val from = RemotePath.resolve(currentPath, target.name)
                    val to = RemotePath.resolve(currentPath, newName)
                    scope.launch {
                        runCatching { client?.rename(from, to) }
                            .onSuccess { toast(context.getString(R.string.sftp_toast_renamed)); refreshTick++ }
                            .onFailure { toast(context.getString(R.string.sftp_toast_failed, it.message ?: "")) }
                    }
                }
            },
            onDismiss = { renameTarget = null }
        )
    }

    // 新規フォルダダイアログ
    if (mkdirOpen) {
        InputDialog(
            title = stringResource(R.string.sftp_dialog_new_folder_title),
            initial = "",
            confirmLabel = stringResource(R.string.sftp_dialog_new_folder_confirm),
            onConfirm = { name ->
                mkdirOpen = false
                if (name.isNotBlank()) {
                    val path = RemotePath.resolve(currentPath, name)
                    scope.launch {
                        runCatching { client?.mkdir(path) }
                            .onSuccess { toast(context.getString(R.string.sftp_toast_created)); refreshTick++ }
                            .onFailure { toast(context.getString(R.string.sftp_toast_failed, it.message ?: "")) }
                    }
                }
            },
            onDismiss = { mkdirOpen = false }
        )
    }

    // 削除確認ダイアログ
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = ZtsBgCard,
            titleContentColor = ZtsTextPrimary,
            textContentColor = ZtsTextSecondary,
            title = { Text(stringResource(R.string.sftp_dialog_delete_title), fontFamily = FontFamily.Monospace) },
            text = {
                val label = stringResource(
                    if (target.isDir) R.string.sftp_dialog_delete_dir_label
                    else R.string.sftp_dialog_delete_file_label
                )
                val main = stringResource(R.string.sftp_dialog_delete_msg, label, target.name)
                val note = if (target.isDir) stringResource(R.string.sftp_dialog_delete_dir_note) else ""
                Text(
                    main + note,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    val path = RemotePath.resolve(currentPath, target.name)
                    scope.launch {
                        runCatching {
                            if (target.isDir) client?.rmdir(path) else client?.rm(path)
                        }
                            .onSuccess { toast(context.getString(R.string.sftp_toast_deleted)); refreshTick++ }
                            .onFailure { toast(context.getString(R.string.sftp_toast_failed, it.message ?: "")) }
                    }
                }) { Text(stringResource(R.string.sftp_action_delete), color = ZtsError, fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel), color = ZtsTextSecondary, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

    if (exitConfirmOpen) {
        ConfirmDialog(
            title = stringResource(R.string.sftp_exit_title),
            message = stringResource(R.string.sftp_exit_message),
            confirmLabel = stringResource(R.string.sftp_exit_confirm),
            onConfirm = {
                exitConfirmOpen = false
                onDismiss()
            },
            onCancel = { exitConfirmOpen = false },
        )
    }
}

/**
 * 上部バー (設定ページの `SettingsTopBar` と同じ形)。
 * 左上の矢印だけでなくバー全体をタップしても戻れる。
 */
@Composable
private fun SftpTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZtsBgPrimary)
            .border(width = 1.dp, color = ZtsBorder)
            .clickable(onClick = onBack)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "←",
                color = ZtsGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = title,
            color = ZtsGreen,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun SftpRow(
    entry: SftpEntry,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val icon = when {
        entry.isDir -> "📁"
        entry.isLink -> "🔗"
        else -> "📄"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .clickable { if (entry.isDir || entry.isLink) onOpen() else menuOpen = true }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = ZtsTextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            if (entry.name != "..") {
                Text(
                    text = buildString {
                        if (!entry.isDir) append(humanSize(entry.size)).append("  ")
                        append(formatMtime(entry.mtimeSec))
                        if (entry.permissions.isNotEmpty()) append("  ").append(entry.permissions)
                    },
                    color = ZtsTextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
        if (entry.name != "..") {
            Box {
                Text(
                    "⋮",
                    color = ZtsTextSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { menuOpen = true }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    if (!entry.isDir) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sftp_action_download), fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                            onClick = { menuOpen = false; onDownload() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sftp_action_rename), fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sftp_action_delete), color = ZtsError, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val fg = if (enabled) ZtsTextPrimary else ZtsTextSecondary.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = fg, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CenterStatus(message: String, isError: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isError) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ZtsGreen
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                message,
                color = if (isError) ZtsError else ZtsTextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun InputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZtsBgCard,
        titleContentColor = ZtsTextPrimary,
        title = { Text(title, fontFamily = FontFamily.Monospace) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(confirmLabel, color = ZtsGreen, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = ZtsTextSecondary, fontFamily = FontFamily.Monospace)
            }
        }
    )
}

// --- helpers ---

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.1f GB", mb / 1024.0)
}

// パターンが数字のみでロケール非依存なので ROOT 固定。Locale.getDefault() を静的初期化時に
// 焼き込むと、アプリ言語を実行中に切り替えても古いロケールのまま残る (lint ConstantLocale)。
private val MTIME_FMT = SimpleDateFormat("MM-dd HH:mm", Locale.ROOT)

private fun formatMtime(sec: Long): String =
    if (sec <= 0) "" else MTIME_FMT.format(Date(sec * 1000))

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
