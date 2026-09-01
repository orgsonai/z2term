package com.zerotoship.z2term.ui.sftp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SFTP / FTP / WebDAV / SMB 共通ファイルブラウザ (全画面ページ)。
 *
 * 指定 [profile] のプロトコルで接続し、リモートのファイルを一覧 / 移動 / ダウンロード /
 * アップロード / 削除 / 名前変更 / フォルダ作成できる。端末側は Android の SAF ツリー権限で
 * 同じ画面内に一覧し、ファイルとフォルダを再帰転送する。
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
    var side by remember { mutableStateOf(FileSide.REMOTE) }

    // Android の SAF で一度選んだフォルダを永続権限付きで記憶し、以後はこの画面内で
    // 端末側ファイルを一覧する。アップロードのたびにシステム画面へ飛ばない。
    val localPrefs = remember {
        context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
    }
    var localTreeUri by remember {
        mutableStateOf(localPrefs.getString(LOCAL_TREE_URI, null)?.let(Uri::parse))
    }
    val localTree = remember(localTreeUri) {
        localTreeUri?.let { uri -> runCatching { SafFileTree(context.contentResolver, uri) }.getOrNull() }
    }
    var localFolders by remember(localTreeUri) {
        mutableStateOf(
            localTree?.let { listOf(LocalFolder(it.rootId, context.getString(R.string.sftp_local_root))) }
                ?: emptyList()
        )
    }
    var localEntries by remember { mutableStateOf<List<LocalFileEntry>>(emptyList()) }
    var localLoading by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var localRefreshTick by remember { mutableStateOf(0) }
    var transferLabel by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<FilePreview?>(null) }
    var previewLoading by remember { mutableStateOf(false) }

    // ダイアログ状態
    var renameTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var mkdirOpen by remember { mutableStateOf(false) }
    var exitConfirmOpen by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun requestBack() {
        if (side == FileSide.REMOTE && (transferLabel != null || previewLoading)) return
        if (side == FileSide.LOCAL && localFolders.size > 1) {
            localFolders = localFolders.dropLast(1)
        } else if (side == FileSide.LOCAL) {
            side = FileSide.REMOTE
        } else if (currentPath != "/") {
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

    LaunchedEffect(localTree, localFolders.lastOrNull()?.documentId, localRefreshTick) {
        val tree = localTree ?: return@LaunchedEffect
        val folder = localFolders.lastOrNull() ?: return@LaunchedEffect
        localLoading = true
        localError = null
        runCatching { tree.list(folder.documentId) }
            .onSuccess { localEntries = it }
            .onFailure {
                localEntries = emptyList()
                localError = it.message ?: it.javaClass.simpleName
            }
        localLoading = false
    }

    // 破棄時に接続を閉じる (別スレッドで disconnect)
    DisposableEffect(Unit) {
        onDispose { val c = client; if (c != null) Thread { c.close() }.start() }
    }

    val localFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            localPrefs.edit().putString(LOCAL_TREE_URI, uri.toString()).apply()
            localTreeUri = uri
            side = FileSide.LOCAL
        }
    }

    fun download(entry: SftpEntry) {
        if (transferLabel != null || previewLoading) return
        val c = client ?: return
        val tree = localTree
        val folder = localFolders.lastOrNull()
        if (tree == null || folder == null) {
            side = FileSide.LOCAL
            toast(context.getString(R.string.sftp_local_choose_first))
            return
        }
        val remote = RemotePath.resolve(currentPath, entry.name)
        transferLabel = entry.name
        scope.launch {
            runCatching {
                downloadRemoteTree(
                    c,
                    tree,
                    entry,
                    remote,
                    folder.documentId,
                    ::mimeTypeForName,
                    onProgress = { transferLabel = it },
                )
            }.onSuccess {
                toast(context.getString(R.string.sftp_toast_download_complete, entry.name))
                localRefreshTick++
            }.onFailure {
                toast(context.getString(R.string.sftp_toast_download_failed, it.message ?: ""))
            }
            transferLabel = null
        }
    }

    fun upload(entry: LocalFileEntry) {
        if (transferLabel != null || previewLoading) return
        val c = client ?: return
        val tree = localTree ?: return
        val remoteParent = currentPath
        transferLabel = entry.name
        scope.launch {
            runCatching {
                uploadLocalTree(
                    c,
                    tree,
                    entry,
                    remoteParent,
                    onProgress = { transferLabel = it },
                )
            }.onSuccess {
                toast(context.getString(R.string.sftp_toast_upload_complete, entry.name))
                refreshTick++
            }.onFailure {
                toast(context.getString(R.string.sftp_toast_upload_failed, it.message ?: ""))
            }
            transferLabel = null
        }
    }

    fun previewRemote(entry: SftpEntry) {
        if (previewLoading || transferLabel != null) return
        val c = client ?: return
        if (!isPreviewable(entry.name)) {
            toast(context.getString(R.string.sftp_preview_unsupported))
            return
        }
        previewLoading = true
        scope.launch {
            val remote = RemotePath.resolve(currentPath, entry.name)
            runCatching {
                val bytes = readLimited(previewByteLimit(entry.name)) { sink -> c.download(remote, sink) }
                decodePreview(entry.name, bytes)
            }.onSuccess { preview = it }
                .onFailure { toast(context.getString(R.string.sftp_preview_failed, it.message ?: "")) }
            previewLoading = false
        }
    }

    fun previewLocal(entry: LocalFileEntry) {
        if (previewLoading || transferLabel != null) return
        val tree = localTree ?: return
        if (!isPreviewable(entry.name, entry.mimeType)) {
            toast(context.getString(R.string.sftp_preview_unsupported))
            return
        }
        previewLoading = true
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    tree.openInput(entry).use { input ->
                        readLimited(previewByteLimit(entry.name, entry.mimeType)) { input.copyTo(it) }
                    }
                }
                decodePreview(entry.name, bytes, entry.mimeType)
            }.onSuccess { preview = it }
                .onFailure { toast(context.getString(R.string.sftp_preview_failed, it.message ?: "")) }
            previewLoading = false
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
            FileSideTabs(side = side, onSelect = { side = it })

            // 現在パス + 上へ / 更新。リモートと端末側で同じ形を使う。
            val remoteIdle = transferLabel == null && !previewLoading
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (side == FileSide.REMOTE) currentPath
                        else localFolders.joinToString("/") { it.name },
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if ((side == FileSide.REMOTE && loading) || (side == FileSide.LOCAL && localLoading)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ZtsGreen
                    )
                    Spacer(Modifier.width(8.dp))
                }
                val canGoUp = if (side == FileSide.REMOTE) client != null && currentPath != "/" && remoteIdle
                    else localFolders.size > 1
                PillButton(stringResource(R.string.sftp_button_up), enabled = canGoUp) {
                    if (side == FileSide.REMOTE) currentPath = RemotePath.resolve(currentPath, "..")
                    else localFolders = localFolders.dropLast(1)
                }
                Spacer(Modifier.width(6.dp))
                PillButton("⟳", enabled = side == FileSide.LOCAL || (client != null && remoteIdle)) {
                    if (side == FileSide.REMOTE) refreshTick++ else localRefreshTick++
                }
            }

            transferLabel?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ZtsGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ZtsGreen,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.sftp_transfer_progress, it),
                        color = ZtsTextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
            }

            if (side == FileSide.REMOTE) {
                when {
                    connecting -> CenterStatus(stringResource(R.string.sftp_status_connecting))
                    connError != null -> CenterStatus(
                        stringResource(R.string.sftp_status_connect_failed, connError ?: ""),
                        isError = true,
                    )
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
                                    if (remoteIdle && (entry.isDir || entry.isLink)) {
                                        currentPath = RemotePath.resolve(currentPath, entry.name)
                                    }
                                },
                                onPreview = { previewRemote(entry) },
                                onDownload = { download(entry) },
                                onRename = { if (remoteIdle) renameTarget = entry },
                                onDelete = { if (remoteIdle) deleteTarget = entry }
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
                        PillButton(stringResource(R.string.sftp_tab_local), enabled = true) {
                            side = FileSide.LOCAL
                        }
                        PillButton(
                            stringResource(R.string.sftp_button_new_folder),
                            enabled = client != null && remoteIdle,
                        ) {
                            mkdirOpen = true
                        }
                    }
                }
                }
            } else {
                when {
                    localTree == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    stringResource(R.string.sftp_local_choose_desc),
                                    color = ZtsTextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                                PillButton(stringResource(R.string.sftp_local_choose)) {
                                    localFolderLauncher.launch(null)
                                }
                            }
                        }
                    }
                    localError != null -> {
                        CenterStatus(localError ?: "", isError = true)
                        PillButton(stringResource(R.string.sftp_local_choose_again)) {
                            localFolderLauncher.launch(localTreeUri)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(localEntries, key = { it.documentId }) { entry ->
                                LocalFileRow(
                                    entry = entry,
                                    transferEnabled = transferLabel == null && client != null,
                                    onOpen = {
                                        if (entry.isDir) {
                                            localFolders = localFolders + LocalFolder(
                                                entry.documentId,
                                                entry.name,
                                            )
                                        }
                                    },
                                    onPreview = { previewLocal(entry) },
                                    onUpload = { upload(entry) },
                                )
                            }
                            if (localEntries.isEmpty() && !localLoading) {
                                item {
                                    Text(
                                        stringResource(R.string.sftp_status_empty_dir),
                                        color = ZtsTextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 12.dp),
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton(stringResource(R.string.sftp_local_change_folder)) {
                                localFolderLauncher.launch(localTreeUri)
                            }
                            PillButton(stringResource(R.string.sftp_tab_remote)) {
                                side = FileSide.REMOTE
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (previewLoading) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = ZtsBgCard,
            title = { Text(stringResource(R.string.sftp_preview_loading)) },
            text = { CenterStatus(stringResource(R.string.sftp_preview_loading)) },
            confirmButton = {},
        )
    }
    preview?.let { value ->
        PreviewDialog(value, onDismiss = { preview = null })
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
    onPreview: () -> Unit,
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
            .clickable { if (entry.isDir || entry.isLink) onOpen() else onPreview() }
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
                            text = { Text(stringResource(R.string.sftp_action_preview), fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                            onClick = { menuOpen = false; onPreview() }
                        )
                    }
                    DropdownMenuItem(
                            text = { Text(stringResource(R.string.sftp_action_download), fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                            onClick = { menuOpen = false; onDownload() }
                        )
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
private fun LocalFileRow(
    entry: LocalFileEntry,
    transferEnabled: Boolean,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onUpload: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .clickable { if (entry.isDir) onOpen() else onPreview() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (entry.isDir) "📁" else "📄", fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                color = ZtsTextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                buildString {
                    if (!entry.isDir) append(humanSize(entry.size)).append("  ")
                    append(formatLocalMtime(entry.modifiedMs))
                },
                color = ZtsTextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Box {
            Text(
                "⋮",
                color = ZtsTextSecondary,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { menuOpen = true }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!entry.isDir) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.sftp_action_preview),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            )
                        },
                        onClick = { menuOpen = false; onPreview() },
                    )
                }
                DropdownMenuItem(
                    enabled = transferEnabled,
                    text = {
                        Text(
                            stringResource(
                                if (entry.isDir) R.string.sftp_action_upload_folder
                                else R.string.sftp_action_upload,
                            ),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    },
                    onClick = { menuOpen = false; onUpload() },
                )
            }
        }
    }
}

private enum class FileSide { REMOTE, LOCAL }

@Composable
private fun FileSideTabs(side: FileSide, onSelect: (FileSide) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FileSideTab(
            label = stringResource(R.string.sftp_tab_remote),
            selected = side == FileSide.REMOTE,
            modifier = Modifier.weight(1f),
        ) { onSelect(FileSide.REMOTE) }
        FileSideTab(
            label = stringResource(R.string.sftp_tab_local),
            selected = side == FileSide.LOCAL,
            modifier = Modifier.weight(1f),
        ) { onSelect(FileSide.LOCAL) }
    }
}

@Composable
private fun FileSideTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) ZtsGreen.copy(alpha = 0.18f) else ZtsBgCard)
            .border(1.dp, if (selected) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) ZtsGreen else ZtsTextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private data class FilePreview(
    val name: String,
    val text: String? = null,
    val bitmap: Bitmap? = null,
)

@Composable
private fun PreviewDialog(value: FilePreview, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZtsBgCard,
        titleContentColor = ZtsTextPrimary,
        title = {
            Text(
                value.name,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                maxLines = 2,
            )
        },
        text = {
            when {
                value.bitmap != null -> Image(
                    bitmap = value.bitmap.asImageBitmap(),
                    contentDescription = value.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 520.dp),
                )
                value.text != null -> Text(
                    value.text,
                    color = ZtsTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sftp_preview_close), color = ZtsGreen)
            }
        },
    )
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

private fun formatLocalMtime(ms: Long): String =
    if (ms <= 0) "" else MTIME_FMT.format(Date(ms))

private fun mimeTypeForName(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: if (extension in TEXT_EXTENSIONS) "text/plain" else "application/octet-stream"
}

private fun isPreviewable(name: String, declaredMime: String = ""): Boolean {
    val mime = declaredMime.ifBlank { mimeTypeForName(name) }
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return mime.startsWith("image/") || mime.startsWith("text/") || extension in TEXT_EXTENSIONS
}

private fun decodePreview(name: String, bytes: ByteArray, declaredMime: String = ""): FilePreview {
    val mime = declaredMime.ifBlank { mimeTypeForName(name) }
    if (mime.startsWith("image/")) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged image" }
        var sample = 1
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > MAX_PREVIEW_PIXELS) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
            ?: error("Unsupported or damaged image")
        return FilePreview(name = name, bitmap = bitmap)
    }
    check(bytes.none { it == 0.toByte() }) { "This file is binary, not text" }
    return FilePreview(
        name = name,
        text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"),
    )
}

private fun previewByteLimit(name: String, declaredMime: String = ""): Int {
    val mime = declaredMime.ifBlank { mimeTypeForName(name) }
    return if (mime.startsWith("image/")) MAX_IMAGE_PREVIEW_BYTES else MAX_TEXT_PREVIEW_BYTES
}

private suspend fun readLimited(
    maxBytes: Int,
    writer: suspend (java.io.OutputStream) -> Unit,
): ByteArray {
    val sink = object : ByteArrayOutputStream() {
        override fun write(value: Int) {
            if (count >= maxBytes) throw PreviewTooLargeException()
            super.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (count + length > maxBytes) throw PreviewTooLargeException()
            super.write(buffer, offset, length)
        }
    }
    writer(sink)
    return sink.toByteArray()
}

private class PreviewTooLargeException : IllegalStateException("This file is too large to preview")

private const val MAX_IMAGE_PREVIEW_BYTES = 24 * 1024 * 1024
private const val MAX_TEXT_PREVIEW_BYTES = 2 * 1024 * 1024
private const val MAX_PREVIEW_PIXELS = 16L * 1024 * 1024
private const val LOCAL_PREFS = "remote_file_browser"
private const val LOCAL_TREE_URI = "local_tree_uri"
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "log", "csv", "tsv", "json", "xml", "yaml", "yml",
    "ini", "conf", "cfg", "properties", "html", "htm", "css", "js", "ts", "kt",
    "java", "c", "h", "cpp", "hpp", "py", "rb", "php", "sh", "zsh", "fish", "sql",
)
