package com.zerotoship.z2term.ui.ssh

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.PortForward
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.channel.SshProfileStore
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SSH プロファイル管理シート。
 *
 * リスト表示 → 追加/編集/削除/接続。
 * 接続時は [onConnect] (TerminalScreen 側で新規セッション作成 + startSsh) に委譲。
 *
 * SshProfileStore は本コンポーザブル内で直接インスタンス化する (DataStore 自体が
 * プロセスシングルトンなのでデータの一貫性は保たれる)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshProfilesSheet(
    onDismiss: () -> Unit,
    onConnect: (SshProfile) -> Unit,
    onSftp: (SshProfile) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
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
    val store = remember { SshProfileStore(context.applicationContext) }
    val profilesFlow = remember(store) {
        store.profiles.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }
    val profiles by profilesFlow.collectAsState()
    var editing by remember { mutableStateOf<SshProfile?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZtsBgPrimary,
        contentColor = ZtsTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { Z2TermDragHandle(onClose = closeSheet) }
    ) {
        BackHandler(onBack = closeSheet)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val currentEdit = editing
            if (currentEdit == null) {
                ListHeader(onNew = { editing = newProfile() })
                if (profiles.isEmpty()) {
                    EmptyState()
                } else {
                    profiles.forEach { p ->
                        ProfileRow(
                            profile = p,
                            onConnect = {
                                onConnect(p)
                                onDismiss()
                            },
                            onSftp = {
                                onSftp(p)
                                onDismiss()
                            },
                            onEdit = { editing = p },
                            onDelete = {
                                scope.launch { store.delete(p.id) }
                            }
                        )
                    }
                }
            } else {
                EditForm(
                    initial = currentEdit,
                    onSave = { saved ->
                        scope.launch {
                            store.upsert(saved)
                            editing = null
                        }
                    },
                    onCancel = { editing = null }
                )
            }
        }
    }
}

private fun newProfile() = SshProfile(
    id = UUID.randomUUID().toString(),
    name = "",
    host = "",
    port = 22,
    user = ""
)

@Composable
private fun ListHeader(onNew: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.ssh_title),
            color = ZtsGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
        Box(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ZtsGreen.copy(alpha = 0.18f))
                .border(1.dp, ZtsGreen, RoundedCornerShape(8.dp))
                .clickable(onClick = onNew)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.ssh_new),
                color = ZtsGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.ssh_empty),
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ProfileRow(
    profile: SshProfile,
    onConnect: () -> Unit,
    onSftp: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = profile.name.ifEmpty { stringResource(R.string.ssh_unnamed) },
            color = ZtsTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "${profile.user}@${profile.host}:${profile.port} " +
                "[${profile.authType.name.lowercase()}]" +
                (if (profile.forwards.isNotEmpty()) " 🔀${profile.forwards.size}" else ""),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton(label = stringResource(R.string.ssh_action_connect), accent = true, onClick = onConnect)
            SmallButton(label = "SFTP", onClick = onSftp)
            SmallButton(label = stringResource(R.string.ssh_action_edit), onClick = onEdit)
            Box(modifier = Modifier.weight(1f))
            SmallButton(label = stringResource(R.string.ssh_action_delete), danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun EditForm(
    initial: SshProfile,
    onSave: (SshProfile) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var host by remember(initial.id) { mutableStateOf(initial.host) }
    var port by remember(initial.id) { mutableStateOf(initial.port.toString()) }
    var user by remember(initial.id) { mutableStateOf(initial.user) }
    var auth by remember(initial.id) { mutableStateOf(initial.authType) }
    var password by remember(initial.id) { mutableStateOf(initial.password) }
    var privateKey by remember(initial.id) { mutableStateOf(initial.privateKey) }
    var keyPassphrase by remember(initial.id) { mutableStateOf(initial.keyPassphrase) }
    var initCmd by remember(initial.id) { mutableStateOf(initial.initCommand) }
    var forwards by remember(initial.id) { mutableStateOf(initial.forwards) }

    Text(
        text = if (initial.name.isEmpty() && initial.host.isEmpty())
            stringResource(R.string.ssh_new_profile_title)
        else
            stringResource(R.string.ssh_edit_profile_title, initial.name.ifEmpty { initial.host }),
        color = ZtsGreen,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace
    )

    Field(label = stringResource(R.string.ssh_field_name), value = name, onChange = { name = it }, placeholder = "my-server")
    Field(label = stringResource(R.string.ssh_field_host), value = host, onChange = { host = it }, placeholder = "example.com or 192.168.0.10")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            Field(
                label = stringResource(R.string.ssh_field_port),
                value = port,
                onChange = { port = it.filter { ch -> ch.isDigit() } },
                placeholder = "22"
            )
        }
        Box(modifier = Modifier.weight(2f)) {
            Field(label = stringResource(R.string.ssh_field_user), value = user, onChange = { user = it }, placeholder = "ubuntu")
        }
    }

    Text(
        text = stringResource(R.string.ssh_auth_method),
        color = ZtsTextSecondary,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AuthChip(
            label = stringResource(R.string.ssh_auth_password),
            selected = auth == SshProfile.AuthType.PASSWORD,
            onSelect = { auth = SshProfile.AuthType.PASSWORD }
        )
        AuthChip(
            label = stringResource(R.string.ssh_auth_publickey),
            selected = auth == SshProfile.AuthType.PUBLIC_KEY,
            onSelect = { auth = SshProfile.AuthType.PUBLIC_KEY }
        )
    }

    when (auth) {
        SshProfile.AuthType.PASSWORD -> {
            Field(label = stringResource(R.string.ssh_field_password), value = password, onChange = { password = it }, placeholder = "********", secret = true)
        }
        SshProfile.AuthType.PUBLIC_KEY -> {
            Field(
                label = stringResource(R.string.ssh_field_private_key),
                value = privateKey,
                onChange = { privateKey = it },
                placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----\n...",
                multiline = true
            )
            Field(label = stringResource(R.string.ssh_field_passphrase), value = keyPassphrase, onChange = { keyPassphrase = it }, secret = true)
        }
    }

    Field(
        label = stringResource(R.string.ssh_field_init_cmd),
        value = initCmd,
        onChange = { initCmd = it },
        placeholder = "tmux a || tmux"
    )

    PortForwardSection(
        forwards = forwards,
        onChange = { forwards = it }
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallButton(label = stringResource(R.string.action_cancel), onClick = onCancel)
        Box(modifier = Modifier.weight(1f))
        SmallButton(
            label = stringResource(R.string.action_save),
            accent = true,
            onClick = {
                val portNum = port.toIntOrNull()?.coerceIn(1, 65535) ?: 22
                val saved = initial.copy(
                    name = name,
                    host = host,
                    port = portNum,
                    user = user,
                    authType = auth,
                    password = if (auth == SshProfile.AuthType.PASSWORD) password else "",
                    privateKey = if (auth == SshProfile.AuthType.PUBLIC_KEY) privateKey else "",
                    keyPassphrase = if (auth == SshProfile.AuthType.PUBLIC_KEY) keyPassphrase else "",
                    initCommand = initCmd,
                    forwards = forwards.filter { it.localPort in 1..65535 && it.remotePort in 1..65535 && it.remoteHost.isNotBlank() }
                )
                onSave(saved)
            }
        )
    }
}

/**
 * ポート転送 (-L) のリスト編集セクション。
 *
 * 各エントリ: bindAddress (既定 127.0.0.1) / localPort / remoteHost / remotePort。
 * 「+ 追加」で空エントリを末尾に追加。各エントリは右上の × で削除。
 * 保存時に EditForm 側で「localPort と remotePort が 1..65535 かつ remoteHost が
 * 非空」のものだけが永続化される (バリデーション)。
 */
@Composable
private fun PortForwardSection(
    forwards: List<PortForward>,
    onChange: (List<PortForward>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ssh_port_forward),
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Box(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ZtsGreen.copy(alpha = 0.18f))
                    .border(1.dp, ZtsGreen, RoundedCornerShape(6.dp))
                    .clickable {
                        onChange(forwards + PortForward(
                            bindAddress = "127.0.0.1",
                            localPort = 8080,
                            remoteHost = "localhost",
                            remotePort = 80
                        ))
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.ssh_port_forward_add),
                    color = ZtsGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (forwards.isEmpty()) {
            Text(
                text = stringResource(R.string.ssh_port_forward_empty),
                color = ZtsTextSecondary.copy(alpha = 0.65f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            forwards.forEachIndexed { idx, fw ->
                ForwardRow(
                    fw = fw,
                    onChange = { newFw ->
                        onChange(forwards.toMutableList().also { it[idx] = newFw })
                    },
                    onDelete = {
                        onChange(forwards.toMutableList().also { it.removeAt(idx) })
                    }
                )
            }
        }
    }
}

@Composable
private fun ForwardRow(
    fw: PortForward,
    onChange: (PortForward) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Local",
                color = ZtsGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 6.dp)
            )
            Box(modifier = Modifier.weight(1.4f)) {
                ForwardField(
                    value = fw.bindAddress,
                    onChange = { onChange(fw.copy(bindAddress = it)) },
                    placeholder = "127.0.0.1"
                )
            }
            Text(text = ":", color = ZtsTextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp))
            Box(modifier = Modifier.weight(0.7f)) {
                ForwardField(
                    value = fw.localPort.toString().takeIf { it != "0" } ?: "",
                    onChange = { s ->
                        val p = s.filter { it.isDigit() }.toIntOrNull() ?: 0
                        onChange(fw.copy(localPort = p))
                    },
                    placeholder = "8080",
                    digitsOnly = true
                )
            }
            Box(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "×",
                    color = ZtsError,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Remote",
                color = ZtsGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 6.dp)
            )
            Box(modifier = Modifier.weight(1.4f)) {
                ForwardField(
                    value = fw.remoteHost,
                    onChange = { onChange(fw.copy(remoteHost = it)) },
                    placeholder = "localhost"
                )
            }
            Text(text = ":", color = ZtsTextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp))
            Box(modifier = Modifier.weight(0.7f)) {
                ForwardField(
                    value = fw.remotePort.toString().takeIf { it != "0" } ?: "",
                    onChange = { s ->
                        val p = s.filter { it.isDigit() }.toIntOrNull() ?: 0
                        onChange(fw.copy(remotePort = p))
                    },
                    placeholder = "80",
                    digitsOnly = true
                )
            }
            Box(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ForwardField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    digitsOnly: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(ZtsBgPrimary)
            .border(1.dp, ZtsBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = ZtsTextSecondary.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { v ->
                if (digitsOnly) onChange(v.filter { it.isDigit() }) else onChange(v)
            },
            singleLine = true,
            textStyle = TextStyle(
                color = ZtsTextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(ZtsGreen),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    secret: Boolean = false,
    multiline: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = ZtsTextSecondary,
            fontSize = 11.sp,
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
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = ZtsTextSecondary.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (multiline) 6 else 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = !multiline,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
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
private fun AuthChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    val bg = if (selected) ZtsGreen.copy(alpha = 0.18f) else ZtsBgCard
    val border = if (selected) ZtsGreen else ZtsBorder
    val fg = if (selected) ZtsGreen else ZtsTextPrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SmallButton(
    label: String,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val border = when {
        danger -> ZtsError
        accent -> ZtsGreen
        else -> ZtsBorder
    }
    val fg = when {
        danger -> ZtsError
        accent -> ZtsGreen
        else -> ZtsTextPrimary
    }
    val bg = when {
        accent -> ZtsGreen.copy(alpha = 0.18f)
        else -> ZtsBgSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
