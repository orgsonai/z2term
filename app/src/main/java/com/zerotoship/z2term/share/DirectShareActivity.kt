package com.zerotoship.z2term.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.zerotoship.z2term.R
import com.zerotoship.z2term.qr.QrActivityBase
import com.zerotoship.z2term.qr.QrDisplay
import com.zerotoship.z2term.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

internal class DirectShareActivity : QrActivityBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Z2TermTheme { Surface(color = ZtsBgPrimary, modifier = Modifier.fillMaxSize()) { Unlocked { ShareScreen() } } }
        }
    }

    @Composable private fun ShareScreen() {
        val context = LocalContext.current
        val preferences = remember { getSharedPreferences("direct_share", Context.MODE_PRIVATE) }
        val scope = rememberCoroutineScope()
        val state by DirectShareManager.state.collectAsState()
        var origin by rememberSaveable { mutableStateOf(preferences.getString("origin", "").orEmpty()) }
        var port by rememberSaveable { mutableStateOf(preferences.getInt("port", 8080).toString()) }
        var minutes by rememberSaveable { mutableStateOf(15) }
        var automatic by rememberSaveable { mutableStateOf(true) }
        var selected by rememberSaveable { mutableStateOf("") }
        var selectedFolder by rememberSaveable { mutableStateOf(false) }
        var filename by rememberSaveable { mutableStateOf("") }
        var certificate by rememberSaveable { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var allowHttp by rememberSaveable { mutableStateOf(false) }
        var error by remember { mutableStateOf(false) }
        val config = remember(origin, port, minutes) { runCatching { DirectShareConfig.parse(origin, port.toInt(), minutes) }.getOrNull() }
        fun select(uri: Uri?, folder: Boolean) {
            if (uri != null) {
                error = false
                if (automatic) {
                    // Start while the picker has returned us to the foreground; preparation belongs to the service.
                    try {
                        DirectShareManager.startAutomatic(context, uri, minutes, folder)
                        selected = ""; filename = ""; selectedFolder = false
                    } catch (_: Exception) { error = true }
                } else scope.launch {
                    try {
                        val name = withContext(Dispatchers.IO) {
                            val document = if (folder) DocumentsContract.buildDocumentUriUsingTree(uri,
                                DocumentsContract.getTreeDocumentId(uri)) else uri
                            contentResolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                                if (it.moveToFirst()) it.getString(0) else null
                            }.orEmpty().ifBlank { "download" }
                        }
                        selected = uri.toString(); filename = name; selectedFolder = folder
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = true }
                }
            }
        }
        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { select(it, false) }
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { select(it, true) }
        val certificatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) certificate = uri.toString()
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.direct_share_title), color = ZtsGreen, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { finish() }) { Text(stringResource(R.string.qr_tools_close)) }
            }
            Text(stringResource(R.string.direct_share_intro), color = ZtsTextSecondary)
            if (!state.active && !state.stopping) {
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    listOf(true, false).forEach { value ->
                        Row(Modifier.weight(1f).selectable(selected = automatic == value,
                            role = Role.RadioButton, onClick = { automatic = value; error = false })
                            .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = automatic == value, onClick = null)
                            Text(stringResource(if (value) R.string.direct_share_mode_auto else R.string.direct_share_mode_manual))
                        }
                    }
                }
                if (automatic) {
                    Text(stringResource(R.string.direct_share_auto_note), color = ZtsTextSecondary)
                    Text(stringResource(R.string.direct_share_auto_http), color = ZtsTextSecondary)
                } else {
                    OutlinedTextField(value = origin, onValueChange = { if (it.length <= 512) { origin = it; allowHttp = false } },
                        label = { Text(stringResource(R.string.direct_share_origin)) },
                        placeholder = { Text("https://share.example:8443") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.direct_share_network), color = ZtsTextSecondary)
                    OutlinedTextField(value = port, onValueChange = { if (it.length <= 5) port = it },
                        label = { Text(stringResource(R.string.direct_share_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                Text(stringResource(R.string.direct_share_duration))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 60).forEach { value ->
                        FilterChip(selected = minutes == value, onClick = { minutes = value },
                            label = { Text(stringResource(R.string.direct_share_minutes, value)) })
                    }
                }
                if (!automatic && config?.tls == true) {
                    OutlinedButton(shape = RectangleShape, onClick = { certificatePicker.launch(arrayOf("*/*")) }) {
                        Text(stringResource(if (certificate.isEmpty()) R.string.direct_share_certificate else R.string.direct_share_certificate_selected))
                    }
                    OutlinedTextField(value = password, onValueChange = { password = it },
                        label = { Text(stringResource(R.string.direct_share_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.direct_share_tls_note), color = ZtsTextSecondary)
                } else if (!automatic && config != null) {
                    Row {
                        Checkbox(checked = allowHttp, onCheckedChange = { allowHttp = it })
                        Text(stringResource(R.string.direct_share_http_note), modifier = Modifier.weight(1f))
                    }
                }
                OutlinedButton(shape = RectangleShape, onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Text(stringResource(if (automatic) R.string.direct_share_choose_start else R.string.direct_share_choose))
                }
                OutlinedButton(shape = RectangleShape, onClick = { folderPicker.launch(null) }) {
                    Text(stringResource(if (automatic) R.string.direct_share_choose_folder_start else R.string.direct_share_choose_folder))
                }
                Text(stringResource(R.string.direct_share_folder_note), color = ZtsTextSecondary)
                if (!automatic && filename.isNotEmpty()) Text(
                    if (selectedFolder) stringResource(R.string.direct_share_selected_folder, filename) else filename,
                    color = ZtsTextPrimary)
                if (!automatic) Button(shape = RectangleShape, enabled = config != null && selected.isNotEmpty() &&
                    (if (config.tls) certificate.isNotEmpty() else allowHttp), onClick = {
                    try {
                        val ready = requireNotNull(config)
                        DirectShareManager.start(context, selected.toUri(), filename, ready,
                            certificate.takeIf { it.isNotEmpty() }?.toUri(), password, allowHttp, selectedFolder)
                        preferences.edit().putString("origin", ready.origin).putInt("port", ready.port).apply()
                        password = ""; error = false
                    } catch (_: Exception) { error = true }
                }) { Text(stringResource(R.string.direct_share_start)) }
            } else {
                Text(state.name, color = ZtsTextPrimary)
                if (state.preparing || state.stopping) {
                    Text(stringResource(when {
                        state.stopping -> R.string.direct_share_stopping
                        state.folder -> R.string.direct_share_preparing_folder
                        else -> R.string.direct_share_preparing
                    }))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (state.url.isNotEmpty()) {
                    Text(if (state.folder) stringResource(R.string.direct_share_folder_summary,
                        state.fileCount, Formatter.formatFileSize(context, state.size))
                        else Formatter.formatFileSize(context, state.size))
                    if (state.automatic) Text(stringResource(R.string.direct_share_auto_http), color = ZtsTextSecondary)
                    if (state.ipv6) Text(stringResource(R.string.direct_share_ipv6), color = ZtsTextSecondary)
                    QrDisplay(state.url)
                    SelectionContainer { Text(state.url) }
                    OutlinedButton(shape = RectangleShape, onClick = {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("z2term", state.url))
                    }) { Text(stringResource(R.string.qr_tools_copy)) }
                    OutlinedButton(shape = RectangleShape, onClick = {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, state.url), null))
                    }) { Text(stringResource(R.string.direct_share_send_link)) }
                    Text(stringResource(if (state.visited) R.string.direct_share_visited else R.string.direct_share_unverified), color = ZtsTextSecondary)
                    Text(stringResource(R.string.direct_share_expires, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.expires))))
                    Text(stringResource(R.string.direct_share_bytes, Formatter.formatFileSize(context, state.sent)))
                }
                OutlinedButton(shape = RectangleShape, enabled = !state.stopping, onClick = { DirectShareManager.stop(context) }) {
                    Text(stringResource(R.string.direct_share_stop))
                }
            }
            val problemText = when {
                error || state.failed -> R.string.direct_share_failed
                state.problem == DirectShareManager.Problem.NO_PUBLIC_ADDRESS -> R.string.direct_share_no_public_address
                state.problem == DirectShareManager.Problem.NETWORK_CHANGED -> R.string.direct_share_network_changed
                else -> null
            }
            if (problemText != null) Text(stringResource(problemText), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.direct_share_lifetime), color = ZtsTextSecondary)
        }
    }
    companion object {
        fun open(context: Context) { context.startActivity(Intent(context, DirectShareActivity::class.java)) }
    }
}
