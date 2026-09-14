package com.zerotoship.z2term.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.SshProfileStore
import com.zerotoship.z2term.qr.QrActivityBase
import com.zerotoship.z2term.qr.QrDisplay
import com.zerotoship.z2term.ui.theme.*
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
        val preferences = remember { getSharedPreferences("self_hosted_share", Context.MODE_PRIVATE) }
        val state by DirectShareManager.state.collectAsState()
        val store = remember { SshProfileStore(context) }
        val storedProfiles by store.profiles.collectAsState(initial = emptyList())
        val profiles = storedProfiles.filter { it.hasSsh }
        var profileId by rememberSaveable { mutableStateOf(preferences.getString("profile", "").orEmpty()) }
        var origin by rememberSaveable { mutableStateOf(preferences.getString("origin", "").orEmpty()) }
        var port by rememberSaveable { mutableStateOf(preferences.getString("remotePort", "8080").orEmpty()) }
        var minutes by rememberSaveable { mutableStateOf(15) }
        var choosingProfile by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(false) }
        val profile = profiles.firstOrNull { it.id == profileId }
        val config = remember(profile, origin, port, minutes) {
            runCatching { ShareRelayConfig.parse(profile?.id.orEmpty(), origin, port.toInt(), minutes) }.getOrNull()
        }
        fun saveSettings() {
            preferences.edit().putString("profile", profileId).putString("origin", origin)
                .putString("remotePort", port).apply()
            saved = true
        }
        fun select(uri: Uri?, folder: Boolean) {
            if (uri == null) return
            error = false
            try {
                val ready = requireNotNull(config)
                DirectShareManager.start(context, uri, ready, folder)
                saveSettings()
            } catch (_: Exception) { error = true }
        }
        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { select(it, false) }
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { select(it, true) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.direct_share_title), color = ZtsGreen, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { finish() }) { Text(stringResource(R.string.qr_tools_close)) }
            }
            Text(stringResource(R.string.direct_share_intro), color = ZtsTextSecondary)
            if (!state.active && !state.stopping) {
                Text(stringResource(R.string.direct_share_relay_note), color = ZtsTextSecondary)
                Text(stringResource(R.string.direct_share_profile), color = ZtsTextPrimary)
                Box {
                    OutlinedButton(shape = RectangleShape, enabled = profiles.isNotEmpty(),
                        onClick = { choosingProfile = true }) {
                        Text(profile?.let { it.name.ifBlank { it.endpointDescription() } }
                            ?: stringResource(R.string.direct_share_select_profile))
                    }
                    DropdownMenu(expanded = choosingProfile, onDismissRequest = { choosingProfile = false }) {
                        profiles.forEach { candidate ->
                            DropdownMenuItem(text = { Text(candidate.name.ifBlank { candidate.endpointDescription() }) },
                                onClick = { profileId = candidate.id; choosingProfile = false; saved = false; error = false })
                        }
                    }
                }
                Text(stringResource(R.string.direct_share_profile_note), color = ZtsTextSecondary)
                OutlinedTextField(value = origin, onValueChange = { if (it.length <= 512) { origin = it; saved = false } },
                    label = { Text(stringResource(R.string.direct_share_origin)) },
                    placeholder = { Text("https://share.example") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = port, onValueChange = { if (it.length <= 5) { port = it; saved = false } },
                    label = { Text(stringResource(R.string.direct_share_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Text(stringResource(R.string.direct_share_setup_note), color = ZtsTextSecondary)
                TextButton(onClick = { saveSettings() }) { Text(stringResource(R.string.direct_share_save_settings)) }
                if (saved) Text(stringResource(R.string.qr_tools_done), color = ZtsTextSecondary)
                Text(stringResource(R.string.direct_share_duration))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 60).forEach { value ->
                        FilterChip(selected = minutes == value, onClick = { minutes = value },
                            label = { Text(stringResource(R.string.direct_share_minutes, value)) })
                    }
                }
                OutlinedButton(shape = RectangleShape, enabled = config != null,
                    onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.direct_share_choose_start))
                }
                OutlinedButton(shape = RectangleShape, enabled = config != null,
                    onClick = { folderPicker.launch(null) }) {
                    Text(stringResource(R.string.direct_share_choose_folder_start))
                }
                Text(stringResource(R.string.direct_share_folder_note), color = ZtsTextSecondary)
            } else {
                Text(state.name, color = ZtsTextPrimary)
                if (state.preparing || state.stopping || state.connecting) {
                    Text(stringResource(when {
                        state.stopping -> R.string.direct_share_stopping
                        state.connecting -> R.string.direct_share_relay_connecting
                        state.folder -> R.string.direct_share_preparing_folder
                        else -> R.string.direct_share_preparing
                    }))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (state.url.isNotEmpty()) {
                    Text(if (state.folder) stringResource(R.string.direct_share_folder_summary,
                        state.fileCount, Formatter.formatFileSize(context, state.size))
                        else Formatter.formatFileSize(context, state.size))
                    QrDisplay(state.url)
                    SelectionContainer { Text(state.url) }
                    OutlinedButton(shape = RectangleShape, onClick = {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("z2term", state.url))
                    }) { Text(stringResource(R.string.qr_tools_copy)) }
                    OutlinedButton(shape = RectangleShape, onClick = {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, state.url), null))
                    }) { Text(stringResource(R.string.direct_share_send_link)) }
                    Text(stringResource(if (state.visited) R.string.direct_share_visited else R.string.direct_share_relay_ready),
                        color = ZtsTextSecondary)
                    Text(stringResource(R.string.direct_share_expires, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.expires))))
                    Text(stringResource(R.string.direct_share_bytes, Formatter.formatFileSize(context, state.sent)))
                }
                OutlinedButton(shape = RectangleShape, enabled = !state.stopping, onClick = { DirectShareManager.stop(context) }) {
                    Text(stringResource(R.string.direct_share_stop))
                }
            }
            val problemText = when {
                state.problem == DirectShareManager.Problem.RELAY_UNAVAILABLE -> R.string.direct_share_relay_failed
                error || state.failed -> R.string.direct_share_failed
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
