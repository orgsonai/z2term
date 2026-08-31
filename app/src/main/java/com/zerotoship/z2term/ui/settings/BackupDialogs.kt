package com.zerotoship.z2term.ui.settings

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.backup.BackupManager
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 持ち出し（書き出し）のダイアログ (0.8.239)。
 *
 * **秘密を含めるかどうかが唯一の選択**で、既定は含めない。含めるときだけ合言葉の欄が現れ、
 * 空のままでは作れない（[BackupManager.export] 側でも弾く）。**合言葉なしで秘密を出す
 * 経路は画面にも API にも用意しない**、が この機能の一番大事な約束。
 */
@Composable
fun BackupExportDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includeSecrets by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val canExport = !busy && (!includeSecrets || passphrase.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZtsBgCard,
        title = {
            Text(
                text = stringResource(R.string.backup_export_title),
                color = ZtsGreen,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.backup_export_body),
                    color = ZtsTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeSecrets,
                        onCheckedChange = { includeSecrets = it },
                        colors = CheckboxDefaults.colors(checkedColor = ZtsGreen)
                    )
                    Text(
                        text = stringResource(R.string.backup_include_secrets),
                        color = ZtsTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (includeSecrets) {
                    // 合言葉が要る理由をその場で書く。「なぜ求められるのか」が分からない
                    // 入力欄は、適当な文字列を入れられて終わる。
                    Text(
                        text = stringResource(R.string.backup_secrets_warning),
                        color = ZtsError,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Field(
                        label = stringResource(R.string.backup_passphrase),
                        value = passphrase,
                        onChange = { passphrase = it },
                        secret = true,
                    )
                }
            }
        },
        confirmButton = {
            // 保存先はユーザーが選ぶ (アプリが勝手にどこかへ置かない)。SAF なので
            // FileProvider も共有シートも要らず、選んだ先へ直接書ける。
            val saver = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                if (uri == null) { busy = false; return@rememberLauncherForActivityResult }
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                BackupManager.export(context, out, BackupManager.Options(includeSecrets, passphrase))
                            } ?: error("cannot open output")
                        }.isSuccess
                    }
                    busy = false
                    if (ok) onDone() else onDismiss()
                }
            }
            TextButton(
                enabled = canExport,
                onClick = {
                    busy = true
                    runCatching { saver.launch(BackupManager.suggestFileName()) }
                }
            ) {
                Text(
                    text = stringResource(R.string.backup_export_do),
                    color = if (canExport) ZtsGreen else ZtsTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = ZtsTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

/**
 * 取り込みのダイアログ (0.8.239)。
 *
 * **中身を一覧してから適用する**。「何が入るのか分からないまま押す」を作らないため、
 * 先に [BackupManager.peek] で件数を出し、確認してから取り込む。
 */
@Composable
fun BackupImportDialog(uri: Uri, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var summary by remember(uri) { mutableStateOf<BackupManager.Summary?>(null) }
    var loaded by remember(uri) { mutableStateOf(false) }
    var passphrase by remember(uri) { mutableStateOf("") }
    var failed by remember(uri) { mutableStateOf(false) }
    var busy by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        summary = withContext(Dispatchers.IO) { BackupManager.peek(context, uri) }
        loaded = true
    }

    val s = summary
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZtsBgCard,
        title = {
            Text(
                text = stringResource(R.string.backup_import_title),
                color = ZtsGreen,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    !loaded -> Text(
                        text = "…",
                        color = ZtsTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    s == null -> Text(
                        text = stringResource(R.string.backup_import_unreadable),
                        color = ZtsError,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    else -> {
                        Text(
                            text = stringResource(
                                R.string.backup_import_summary,
                                s.createdAt, s.appVersion,
                                s.sshCount, s.snippetCount, s.ruleCount, s.macroCount,
                                s.tileCount, s.iconCount,
                                s.themeCount, s.dictCount, s.learnedCount
                            ),
                            color = ZtsTextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = stringResource(R.string.backup_import_merge_note),
                            color = ZtsTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        if (s.encrypted) {
                            Field(
                                label = stringResource(R.string.backup_passphrase),
                                value = passphrase,
                                onChange = { passphrase = it; failed = false },
                                secret = true,
                            )
                        }
                        if (failed) {
                            Text(
                                text = stringResource(R.string.backup_import_failed),
                                color = ZtsError,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canImport = s != null && !busy && (!s.encrypted || passphrase.isNotEmpty())
            TextButton(
                enabled = canImport,
                onClick = {
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { BackupManager.import(context, uri, passphrase) }
                                .getOrDefault(false)
                        }
                        busy = false
                        if (ok) onDone() else failed = true
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.backup_import_do),
                    color = if (canImport) ZtsGreen else ZtsTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = ZtsTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}
