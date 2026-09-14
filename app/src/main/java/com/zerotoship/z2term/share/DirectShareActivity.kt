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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.SshProfileStore
import com.zerotoship.z2term.qr.QrActivityBase
import com.zerotoship.z2term.qr.QrDisplay
import com.zerotoship.z2term.qr.ToolError
import com.zerotoship.z2term.qr.ToolLabel
import com.zerotoship.z2term.qr.ToolNote
import com.zerotoship.z2term.qr.ToolProgress
import com.zerotoship.z2term.qr.ToolScreenHeader
import com.zerotoship.z2term.ui.settings.Field
import com.zerotoship.z2term.ui.settings.HintBox
import com.zerotoship.z2term.ui.settings.PillButton
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
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolScreenHeader(stringResource(R.string.direct_share_title)) { finish() }
            ToolNote(stringResource(R.string.direct_share_intro))
            if (!state.active && !state.stopping) {
                HintBox(stringResource(R.string.direct_share_relay_note))
                ToolLabel(stringResource(R.string.direct_share_profile))
                // 保存済みの SSH 接続先を並べ、選んだ 1 つだけ緑の枠にする (以前はプルダウン)。
                profiles.forEach { candidate ->
                    PillButton(label = candidate.name.ifBlank { candidate.endpointDescription() },
                        accent = candidate.id == profileId, fill = true) {
                        profileId = candidate.id; saved = false; error = false
                    }
                }
                if (profiles.isNotEmpty() && profile == null) ToolNote(stringResource(R.string.direct_share_select_profile))
                ToolNote(stringResource(R.string.direct_share_profile_note))
                Field(label = stringResource(R.string.direct_share_origin), value = origin, placeholder = "https://share.example",
                    onChange = { if (it.length <= 512) { origin = it; saved = false } })
                Field(label = stringResource(R.string.direct_share_port), value = port, placeholder = "8080",
                    keyboardType = KeyboardType.Number, onChange = { if (it.length <= 5) { port = it; saved = false } })
                ToolNote(stringResource(R.string.direct_share_setup_note))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(label = stringResource(R.string.direct_share_save_settings)) { saveSettings() }
                    if (saved) ToolNote(stringResource(R.string.qr_tools_done))
                }
                ToolLabel(stringResource(R.string.direct_share_duration))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 60).forEach { value ->
                        PillButton(label = stringResource(R.string.direct_share_minutes, value), accent = minutes == value) {
                            minutes = value
                        }
                    }
                }
                PillButton(label = stringResource(R.string.direct_share_choose_start), accent = true, fill = true,
                    enabled = config != null) { filePicker.launch(arrayOf("*/*")) }
                PillButton(label = stringResource(R.string.direct_share_choose_folder_start), accent = true, fill = true,
                    enabled = config != null) { folderPicker.launch(null) }
                ToolNote(stringResource(R.string.direct_share_folder_note))
            } else {
                ToolLabel(state.name)
                if (state.preparing || state.stopping || state.connecting) {
                    ToolNote(stringResource(when {
                        state.stopping -> R.string.direct_share_stopping
                        state.connecting -> R.string.direct_share_relay_connecting
                        state.folder -> R.string.direct_share_preparing_folder
                        else -> R.string.direct_share_preparing
                    }))
                    ToolProgress()
                } else if (state.url.isNotEmpty()) {
                    ToolNote(if (state.folder) stringResource(R.string.direct_share_folder_summary,
                        state.fileCount, Formatter.formatFileSize(context, state.size))
                        else Formatter.formatFileSize(context, state.size))
                    QrDisplay(state.url)
                    SelectionContainer { Text(state.url, color = ZtsGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                    PillButton(label = stringResource(R.string.qr_tools_copy), fill = true) {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("z2term", state.url))
                    }
                    PillButton(label = stringResource(R.string.direct_share_send_link), fill = true) {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, state.url), null))
                    }
                    ToolNote(stringResource(if (state.visited) R.string.direct_share_visited else R.string.direct_share_relay_ready))
                    ToolNote(stringResource(R.string.direct_share_expires, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.expires))))
                    ToolNote(stringResource(R.string.direct_share_bytes, Formatter.formatFileSize(context, state.sent)))
                }
                PillButton(label = stringResource(R.string.direct_share_stop), danger = true, fill = true,
                    enabled = !state.stopping) { DirectShareManager.stop(context) }
            }
            val problemText = when {
                state.problem == DirectShareManager.Problem.RELAY_UNAVAILABLE -> R.string.direct_share_relay_failed
                error || state.failed -> R.string.direct_share_failed
                state.problem == DirectShareManager.Problem.NETWORK_CHANGED -> R.string.direct_share_network_changed
                else -> null
            }
            if (problemText != null) ToolError(stringResource(problemText))
            HintBox(stringResource(R.string.direct_share_lifetime))
        }
    }
    companion object {
        fun open(context: Context) { context.startActivity(Intent(context, DirectShareActivity::class.java)) }
    }
}
