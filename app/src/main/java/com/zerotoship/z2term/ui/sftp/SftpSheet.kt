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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.channel.SftpClient
import com.zerotoship.z2term.channel.SftpEntry
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
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
 * SFTP ファイルブラウザ (ModalBottomSheet)。
 *
 * 指定 [profile] へ SFTP 接続し、リモートのファイルを一覧 / 移動 / ダウンロード /
 * アップロード / 削除 / 名前変更 / フォルダ作成できる。ダウンロード/アップロードは
 * Android の SAF (CreateDocument / OpenDocument) と連携する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpSheet(
    profile: SshProfile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var forceClose by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden)
                forceClose || (listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0)
            else true
        }
    )
    val closeSheet: () -> Unit = {
        forceClose = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    var client by remember { mutableStateOf<SftpClient?>(null) }
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

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // 接続 (1 度だけ)
    LaunchedEffect(profile.id) {
        connecting = true
        connError = null
        runCatching { SftpClient.connect(profile, context) }
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
            .onFailure { toast("一覧取得に失敗: ${it.message}") }
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
            val remote = SftpClient.resolve(currentPath, entry.name)
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            client?.download(remote, os)
                        } ?: error("保存先を開けません")
                    }
                }.onSuccess { toast("ダウンロード完了: ${entry.name}") }
                    .onFailure { toast("ダウンロード失敗: ${it.message}") }
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
                    val remote = SftpClient.resolve(currentPath, name)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            client?.upload(ins, remote)
                        } ?: error("ファイルを開けません")
                    }
                    name
                }.onSuccess { toast("アップロード完了: $it"); refreshTick++ }
                    .onFailure { toast("アップロード失敗: ${it.message}") }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZtsBgPrimary,
        contentColor = ZtsTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        contentWindowInsets = { WindowInsets.statusBars },
        dragHandle = { Z2TermDragHandle(onClose = closeSheet) }
    ) {
        BackHandler(onBack = closeSheet)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ヘッダ: プロファイル名 + 現在パス + 上へ / 更新
            Text(
                text = "SFTP : ${profile.user}@${profile.host}",
                color = ZtsGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
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
                PillButton("↑上へ", enabled = client != null && currentPath != "/") {
                    currentPath = SftpClient.resolve(currentPath, "..")
                }
                Spacer(Modifier.width(6.dp))
                PillButton("⟳", enabled = client != null) { refreshTick++ }
            }

            when {
                connecting -> CenterStatus("接続中…")
                connError != null -> CenterStatus("接続失敗: $connError", isError = true)
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(entries, key = { it.name }) { entry ->
                            SftpRow(
                                entry = entry,
                                onOpen = {
                                    if (entry.isDir || entry.isLink) {
                                        currentPath = SftpClient.resolve(currentPath, entry.name)
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
                                    "(空のディレクトリ)",
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
                        PillButton("⬆ アップロード", enabled = client != null) {
                            uploadLauncher.launch(arrayOf("*/*"))
                        }
                        PillButton("＋ 新規フォルダ", enabled = client != null) {
                            mkdirOpen = true
                        }
                    }
                }
            }
        }
    }

    // 名前変更ダイアログ
    renameTarget?.let { target ->
        InputDialog(
            title = "名前変更",
            initial = target.name,
            confirmLabel = "変更",
            onConfirm = { newName ->
                renameTarget = null
                if (newName.isNotBlank() && newName != target.name) {
                    val from = SftpClient.resolve(currentPath, target.name)
                    val to = SftpClient.resolve(currentPath, newName)
                    scope.launch {
                        runCatching { client?.rename(from, to) }
                            .onSuccess { toast("変更しました"); refreshTick++ }
                            .onFailure { toast("失敗: ${it.message}") }
                    }
                }
            },
            onDismiss = { renameTarget = null }
        )
    }

    // 新規フォルダダイアログ
    if (mkdirOpen) {
        InputDialog(
            title = "新規フォルダ",
            initial = "",
            confirmLabel = "作成",
            onConfirm = { name ->
                mkdirOpen = false
                if (name.isNotBlank()) {
                    val path = SftpClient.resolve(currentPath, name)
                    scope.launch {
                        runCatching { client?.mkdir(path) }
                            .onSuccess { toast("作成しました"); refreshTick++ }
                            .onFailure { toast("失敗: ${it.message}") }
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
            title = { Text("削除", fontFamily = FontFamily.Monospace) },
            text = {
                Text(
                    "${if (target.isDir) "フォルダ" else "ファイル"} \"${target.name}\" を削除しますか?" +
                        if (target.isDir) "\n(空でないと失敗します)" else "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    val path = SftpClient.resolve(currentPath, target.name)
                    scope.launch {
                        runCatching {
                            if (target.isDir) client?.rmdir(path) else client?.rm(path)
                        }
                            .onSuccess { toast("削除しました"); refreshTick++ }
                            .onFailure { toast("失敗: ${it.message}") }
                    }
                }) { Text("削除", color = ZtsError, fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("キャンセル", color = ZtsTextSecondary, fontFamily = FontFamily.Monospace)
                }
            }
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
                            text = { Text("ダウンロード", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                            onClick = { menuOpen = false; onDownload() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("名前変更", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("削除", color = ZtsError, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
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
                Text("キャンセル", color = ZtsTextSecondary, fontFamily = FontFamily.Monospace)
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

private val MTIME_FMT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun formatMtime(sec: Long): String =
    if (sec <= 0) "" else MTIME_FMT.format(Date(sec * 1000))

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
