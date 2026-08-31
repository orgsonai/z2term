package com.zerotoship.z2term.ui.ssh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.zerotoship.z2term.service.ServerDaemonService
import com.zerotoship.z2term.service.TunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.ConnectionProtocol
import com.zerotoship.z2term.channel.PortForward
import com.zerotoship.z2term.channel.RemoteService
import com.zerotoship.z2term.channel.RemoteServiceProtocol
import com.zerotoship.z2term.channel.SshKeyGen
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.channel.SshProfileStore
import com.zerotoship.z2term.gui.rfb.VncTarget
import com.zerotoship.z2term.net.HostAddress
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.components.ConfirmDialog
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SSH 接続先と、その SSH を経由する FTP / SMB / WebDAV / VNC 一覧本体。
 *
 * ツールシート ([com.zerotoship.z2term.ui.snippets.SnippetsSheet]) の「接続先」タブから
 * 呼ばれる。スクロール / シート開閉は呼び出し側が持つので、ここは Column の中身だけを描く。
 *
 * リスト表示 → 追加/編集/削除/接続。
 * 接続時は [onConnect] (TerminalScreen 側で新規セッション作成 + startSsh) に委譲。
 * SSH はシェル / SFTP、追加サービスは共通ファイル画面または VNC タブへ進む。
 * 0.8.438 で保存した直接 WebDAV / SMB は読み込み・編集互換だけを維持する。
 *
 * SshProfileStore は本コンポーザブル内で直接インスタンス化する (DataStore 自体が
 * プロセスシングルトンなのでデータの一貫性は保たれる)。
 */
@Composable
fun SshProfilesBody(
    onConnect: (SshProfile) -> Unit,
    onSftp: (SshProfile) -> Unit = {},
    onService: (SshProfile, RemoteService) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SshProfileStore(context.applicationContext) }
    val profilesFlow = remember(store) {
        store.profiles.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }
    val profiles by profilesFlow.collectAsState()
    var editing by remember { mutableStateOf<SshProfile?>(null) }
    // 「削除」は「編集」の隣にあり、押し間違えると接続先が鍵ごと消える (0.8.452)。
    // ⚠ **CLI (z2-ssh) からの削除には確認を挟まない** — 打った時点で明示的なので。
    var pendingDelete by remember { mutableStateOf<SshProfile?>(null) }

    // 常駐トンネル (A2) が張れているかは、ここに出さないと**どこにも出ない**。⏻ の印だけでは
    // 「設定が ON」しか分からず、0.8.367 で LAN 到達性の担保になった以上それでは足りない。
    // ⚠ 常駐対象が 1 つも無いときはループを回さない (シートを開いている間ずっと動くため)。
    val hasResidentTunnel = profiles.any { it.hasSsh && it.residentTunnel }
    var tunnelStatuses by remember { mutableStateOf<List<TunnelManager.Status>>(emptyList()) }
    LaunchedEffect(hasResidentTunnel) {
        if (!hasResidentTunnel) {
            tunnelStatuses = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            tunnelStatuses = TunnelManager.statuses()
            delay(2_000)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
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
                        tunnel = tunnelStatuses.firstOrNull { it.profileId == p.id },
                        onConnect = { onConnect(p) },
                        onSftp = { onSftp(p) },
                        onService = { service -> onService(p, service) },
                        onEdit = { editing = p },
                        onDelete = { pendingDelete = p }
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
                        // 常駐トンネル (A2) を ON にしたら、その場で張り始める。
                        // 常駐サーバーが 1 つも無くてもトンネルだけで常駐してよい。
                        if (saved.hasSsh && saved.residentTunnel) {
                            ServerDaemonService.start(context.applicationContext)
                        } else {
                            // OFF にしたぶんを畳む (サービス自体は他の常駐が残っていれば生きる)。
                            withContext(Dispatchers.IO) {
                                runCatching { TunnelManager.reload(context.applicationContext) }
                            }
                        }
                    }
                },
                onCancel = { editing = null }
            )
        }
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_ssh_profile_title),
            message = stringResource(
                R.string.confirm_delete_item_msg,
                target.name.ifBlank { target.endpointDescription() },
            ),
            confirmLabel = stringResource(R.string.ssh_action_delete),
            confirmColor = ZtsError,
            onConfirm = {
                pendingDelete = null
                scope.launch { store.delete(target.id) }
            },
            onCancel = { pendingDelete = null },
        )
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
    /** 常駐トンネル (A2) のいまの状態。常駐対象でない / まだ起きていないときは null。 */
    tunnel: TunnelManager.Status?,
    onConnect: () -> Unit,
    onSftp: () -> Unit,
    onService: (RemoteService) -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = profile.name.ifEmpty { stringResource(R.string.ssh_unnamed) },
                color = ZtsTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SmallButton(label = stringResource(R.string.ssh_action_edit), onClick = onEdit)
            SmallButton(label = stringResource(R.string.ssh_action_delete), danger = true, onClick = onDelete)
        }
        Text(
            text = "${profile.endpointDescription()} [${profile.fileProtocolLabel}]" +
                (if (profile.hasSsh) " [${profile.authType.name.lowercase()}]" else "") +
                (if (profile.hasSsh && profile.forwards.isNotEmpty()) " 🔀${profile.forwards.size}" else "") +
                // 常駐トンネル (A2) はタブを閉じても生きるので、一覧で分かるようにする。
                (if (profile.hasSsh && profile.residentTunnel) " ⏻" else ""),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        if (profile.hasSsh && profile.residentTunnel) TunnelStatusLine(tunnel)
        // 接続操作は横 1 列。幅を超えた分だけ横スクロールし、カードの高さを増やさない。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (profile.hasSsh) {
                SmallButton(label = stringResource(R.string.ssh_action_connect), accent = true, onClick = onConnect)
                SmallButton(label = "SFTP", onClick = onSftp)
                // ⚠ **サービスの緑 (accent) は付けない。** 緑は「そのカードの主アクション」= 接続
                // だけの印で、種類による色分けではない。VNC だけ緑にすると選択中のように見える。
                profile.services.forEach { service ->
                    SmallButton(label = service.label, onClick = { onService(service) })
                }
            } else {
                SmallButton(label = profile.fileProtocolLabel, accent = true, onClick = onSftp)
            }
        }
    }
}

/**
 * 常駐トンネル (A2) の生死を 1 行で出す。
 *
 * ⚠ **[TunnelManager.Status.detail] は訳さない。** 中身は転送の指定そのもの
 * (`-R 127.0.0.1:65152 → 127.0.0.1:65152`) か JSch が返した理由で、どちらも原文のほうが
 * 検索できる。張れていない転送には `✗` が付く ([TunnelManager.detailOf])。
 */
@Composable
private fun TunnelStatusLine(status: TunnelManager.Status?) {
    val mark: String
    val color: Color
    val text: String
    when {
        // 常駐対象なのに居ない = 常駐サービスがまだ起きていない (ON にした直後・再起動直後)。
        status == null -> {
            mark = "○"; color = ZtsTextSecondary; text = stringResource(R.string.ssh_tunnel_stopped)
        }
        status.connected -> {
            mark = "●"; color = ZtsGreen; text = status.detail
        }
        else -> {
            mark = "○"; color = ZtsError
            val retry = if (status.retries > 0) {
                " (" + stringResource(R.string.ssh_tunnel_retry, status.retries) + ")"
            } else ""
            text = status.detail + retry
        }
    }
    Text(
        text = "$mark $text",
        color = color,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun EditForm(
    initial: SshProfile,
    onSave: (SshProfile) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var protocol by remember(initial.id) { mutableStateOf(initial.protocol) }
    var host by remember(initial.id) { mutableStateOf(initial.host) }
    var port by remember(initial.id) { mutableStateOf(initial.port.toString()) }
    var user by remember(initial.id) { mutableStateOf(initial.user) }
    var remotePath by remember(initial.id) { mutableStateOf(initial.remotePath) }
    var domain by remember(initial.id) { mutableStateOf(initial.domain) }
    var auth by remember(initial.id) { mutableStateOf(initial.authType) }
    var password by remember(initial.id) { mutableStateOf(initial.password) }
    var privateKey by remember(initial.id) { mutableStateOf(initial.privateKey) }
    // 作った直後の公開鍵 (相手に渡す 1 行)。秘密鍵と違って**保存しない** — 秘密鍵から
    // いつでも作り直せるし、渡すのは作った直後だけなので、状態を増やす理由がない。
    var generatedPublicLine by remember(initial.id) { mutableStateOf("") }
    var keyPassphrase by remember(initial.id) { mutableStateOf(initial.keyPassphrase) }
    var services by remember(initial.id) { mutableStateOf(initial.services) }
    var initCmd by remember(initial.id) { mutableStateOf(initial.initCommand) }
    var forwards by remember(initial.id) { mutableStateOf(initial.forwards) }
    var resident by remember(initial.id) { mutableStateOf(initial.residentTunnel) }

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
    // 新規登録は SSH だけ。0.8.438 で作成済みの直接 WebDAV/SMB は編集互換のため残す。
    if (protocol != ConnectionProtocol.SSH) {
        Text(
            text = stringResource(R.string.connection_legacy_direct),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }

    when (protocol) {
        ConnectionProtocol.SSH -> {
            Field(
                label = stringResource(R.string.ssh_field_host),
                value = host,
                onChange = { host = it },
                placeholder = "example.com / 192.168.0.10 / 2001:db8::10",
            )
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
                    Field(
                        label = stringResource(R.string.ssh_field_password),
                        value = password,
                        onChange = { password = it },
                        placeholder = "********",
                        secret = true,
                        visibilityKey = "${initial.id}:ssh-password",
                    )
                }
                SshProfile.AuthType.PUBLIC_KEY -> {
                    SshKeyRow(
                        hasKey = privateKey.isNotBlank(),
                        publicLine = generatedPublicLine,
                        onGenerate = {
                            val gen = SshKeyGen.generate(comment = "z2term")
                            privateKey = gen.privatePem
                            generatedPublicLine = gen.publicLine
                        }
                    )
                    Field(
                        label = stringResource(R.string.ssh_field_private_key),
                        value = privateKey,
                        onChange = { privateKey = it; generatedPublicLine = "" },
                        placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----\n...",
                        secret = true,
                        visibilityKey = "${initial.id}:private-key",
                        multiline = true
                    )
                    Field(
                        label = stringResource(R.string.ssh_field_passphrase),
                        value = keyPassphrase,
                        onChange = { keyPassphrase = it },
                        secret = true,
                        visibilityKey = "${initial.id}:key-passphrase",
                    )
                }
            }

            RemoteServicesSection(
                sshHost = host,
                services = services,
                onChange = { services = it },
            )

            Field(
                label = stringResource(R.string.ssh_field_init_cmd),
                value = initCmd,
                onChange = { initCmd = it },
                placeholder = "tmux a || tmux"
            )

            PortForwardSection(forwards = forwards, onChange = { forwards = it })
            if (forwards.isNotEmpty()) {
                ResidentTunnelToggle(
                    checked = resident,
                    hasReverse = forwards.any { it.reverse },
                    onChange = { resident = it }
                )
            }
        }

        ConnectionProtocol.WEBDAV -> {
            Field(
                label = stringResource(R.string.connection_field_webdav_url),
                value = host,
                onChange = { host = it },
                placeholder = "https://example.com/dav/"
            )
            Field(label = stringResource(R.string.ssh_field_user), value = user, onChange = { user = it })
            Field(
                label = stringResource(R.string.ssh_field_password),
                value = password,
                onChange = { password = it },
                placeholder = "********",
                secret = true,
                visibilityKey = "${initial.id}:webdav-password",
            )
        }

        ConnectionProtocol.SMB -> {
            Field(
                label = stringResource(R.string.connection_field_smb_host),
                value = host,
                onChange = { host = it },
                placeholder = "server.local / 192.168.0.10 / 2001:db8::10"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    Field(
                        label = stringResource(R.string.ssh_field_port),
                        value = port,
                        onChange = { port = it.filter { ch -> ch.isDigit() } },
                        placeholder = "445"
                    )
                }
                Box(modifier = Modifier.weight(2f)) {
                    Field(label = stringResource(R.string.ssh_field_user), value = user, onChange = { user = it })
                }
            }
            Field(
                label = stringResource(R.string.connection_field_smb_share),
                value = remotePath,
                onChange = { remotePath = it },
                placeholder = "share"
            )
            Field(
                label = stringResource(R.string.connection_field_smb_domain),
                value = domain,
                onChange = { domain = it },
                placeholder = "WORKGROUP"
            )
            Field(
                label = stringResource(R.string.ssh_field_password),
                value = password,
                onChange = { password = it },
                placeholder = "********",
                secret = true,
                visibilityKey = "${initial.id}:smb-password",
            )
        }
    }

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
                val defaultPort = when (protocol) {
                    ConnectionProtocol.SSH -> 22
                    ConnectionProtocol.WEBDAV -> 443
                    ConnectionProtocol.SMB -> 445
                }
                val portNum = port.toIntOrNull()?.coerceIn(1, 65535) ?: defaultPort
                val ssh = protocol == ConnectionProtocol.SSH
                val saved = initial.copy(
                    name = name,
                    protocol = protocol,
                    host = if (protocol == ConnectionProtocol.WEBDAV) {
                        host.trim()
                    } else {
                        HostAddress.normalize(host)
                    },
                    port = portNum,
                    user = user,
                    remotePath = if (protocol == ConnectionProtocol.SMB) remotePath.trim('/') else "",
                    domain = if (protocol == ConnectionProtocol.SMB) domain else "",
                    authType = if (ssh) auth else SshProfile.AuthType.PASSWORD,
                    password = if (!ssh || auth == SshProfile.AuthType.PASSWORD) password else "",
                    privateKey = if (ssh && auth == SshProfile.AuthType.PUBLIC_KEY) privateKey else "",
                    keyPassphrase = if (ssh && auth == SshProfile.AuthType.PUBLIC_KEY) keyPassphrase else "",
                    initCommand = if (ssh) initCmd else "",
                    forwards = if (ssh) forwards.filter {
                        it.localPort in 1..65535 && it.remotePort in 1..65535 && it.remoteHost.isNotBlank()
                    }.map {
                        it.copy(
                            bindAddress = HostAddress.normalize(it.bindAddress),
                            remoteHost = HostAddress.normalize(it.remoteHost),
                        )
                    } else emptyList(),
                    residentTunnel = ssh && resident && forwards.isNotEmpty(),
                    services = if (ssh) services.filter {
                        (!it.useSshTunnel || it.host.isNotBlank()) &&
                            it.remotePort in 1..65535 && it.localPort in 0..65535
                    }.map {
                        it.copy(host = HostAddress.normalize(it.host))
                    } else initial.services,
                )
                onSave(saved)
            }
        )
    }
}

/** SSH 接続先にぶら下がる FTP / SMB / WebDAV / VNC の編集。 */
@Composable
private fun RemoteServicesSection(
    sshHost: String,
    services: List<RemoteService>,
    onChange: (List<RemoteService>) -> Unit,
) {
    var pendingDirectId by remember { mutableStateOf<String?>(null) }
    // × はサービスの見出し行にあり、押すと入力済みのホスト・利用者・パスワードごと消える。
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.remote_services_title),
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = stringResource(R.string.remote_services_desc),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RemoteServiceProtocol.entries.forEach { protocol ->
                val exists = services.any { it.protocol == protocol }
                AuthChip(
                    label = if (exists) "✓ ${protocol.name}" else "+ ${protocol.name}",
                    selected = exists,
                    onSelect = {
                        if (!exists) {
                            onChange(
                                services + RemoteService(
                                    id = UUID.randomUUID().toString(),
                                    protocol = protocol,
                                    name = protocol.name,
                                    host = if (protocol == RemoteServiceProtocol.SMB) {
                                        sshHost.ifBlank { "localhost" }
                                    } else {
                                        "localhost"
                                    },
                                    remotePort = protocol.defaultPort,
                                    webDavHttps = protocol == RemoteServiceProtocol.WEBDAV,
                                )
                            )
                        }
                    },
                )
            }
        }
        services.forEach { service ->
            RemoteServiceEditor(
                sshHost = sshHost,
                service = service,
                onChange = { updated ->
                    onChange(services.map { if (it.id == updated.id) updated else it })
                },
                onRequestDirect = { pendingDirectId = service.id },
                onDelete = { pendingDeleteId = service.id },
            )
        }
    }

    val pending = services.firstOrNull { it.id == pendingDirectId }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingDirectId = null },
            title = { Text(stringResource(R.string.remote_direct_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.remote_direct_warning_message,
                        pending.protocol.name,
                        sshHost,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(services.map {
                        if (it.id == pending.id) it.copy(useSshTunnel = false) else it
                    })
                    pendingDirectId = null
                }) {
                    Text(stringResource(R.string.remote_direct_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDirectId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val pendingDelete = services.firstOrNull { it.id == pendingDeleteId }
    if (pendingDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_remote_service_title),
            message = stringResource(R.string.confirm_delete_item_msg, pendingDelete.label),
            confirmLabel = stringResource(R.string.ssh_action_delete),
            confirmColor = ZtsError,
            onConfirm = {
                pendingDeleteId = null
                onChange(services.filterNot { it.id == pendingDelete.id })
            },
            onCancel = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun RemoteServiceEditor(
    sshHost: String,
    service: RemoteService,
    onChange: (RemoteService) -> Unit,
    onRequestDirect: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = service.protocol.name,
                color = ZtsGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Box(Modifier.weight(1f))
            SmallButton(label = "×", danger = true, onClick = onDelete)
        }
        Field(
            label = stringResource(R.string.remote_service_name),
            value = service.name,
            onChange = { onChange(service.copy(name = it)) },
            placeholder = service.protocol.name,
        )
        ServiceTunnelToggle(
            checked = service.useSshTunnel,
            onChange = { checked ->
                if (checked) onChange(service.copy(useSshTunnel = true)) else onRequestDirect()
            },
        )
        if (service.useSshTunnel) {
            Field(
                label = stringResource(R.string.remote_service_host),
                value = service.host,
                onChange = { onChange(service.copy(host = it)) },
                placeholder = if (service.protocol == RemoteServiceProtocol.SMB) {
                    sshHost.ifBlank { "192.168.1.10" }
                } else {
                    "localhost"
                },
            )
            if (service.protocol == RemoteServiceProtocol.SMB) {
                Text(
                    text = stringResource(
                        R.string.remote_service_smb_host_hint,
                        sshHost.ifBlank { "192.168.1.10" },
                    ),
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else {
            Text(
                text = stringResource(
                    R.string.remote_service_direct_target,
                    sshHost.ifBlank { "-" },
                    service.remotePort,
                ),
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                Field(
                    label = stringResource(R.string.remote_service_remote_port),
                    value = service.remotePort.toString().takeIf { it != "0" } ?: "",
                    onChange = {
                        onChange(service.copy(remotePort = it.filter(Char::isDigit).toIntOrNull() ?: 0))
                    },
                    placeholder = service.protocol.defaultPort.toString(),
                )
            }
            if (service.useSshTunnel) {
                Box(Modifier.weight(1f)) {
                    Field(
                        label = stringResource(R.string.remote_service_local_port),
                        value = service.localPort.toString().takeIf { it != "0" } ?: "",
                        onChange = {
                            onChange(service.copy(localPort = it.filter(Char::isDigit).toIntOrNull() ?: 0))
                        },
                        placeholder = stringResource(R.string.remote_service_auto),
                    )
                }
            }
        }
        when (service.protocol) {
            RemoteServiceProtocol.FTP -> {
                ServiceCredentials(service, onChange)
                Field(
                    label = stringResource(R.string.remote_service_start_path),
                    value = service.path,
                    onChange = { onChange(service.copy(path = it)) },
                    placeholder = "/",
                )
            }
            RemoteServiceProtocol.SMB -> {
                ServiceCredentials(service, onChange)
                Text(
                    text = stringResource(R.string.remote_service_smb_anonymous_hint),
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Field(
                    label = stringResource(R.string.connection_field_smb_share),
                    value = service.path,
                    onChange = { onChange(service.copy(path = it)) },
                    placeholder = "share",
                )
                Field(
                    label = stringResource(R.string.connection_field_smb_domain),
                    value = service.domain,
                    onChange = { onChange(service.copy(domain = it)) },
                    placeholder = "WORKGROUP",
                )
            }
            RemoteServiceProtocol.WEBDAV -> {
                Text(
                    text = stringResource(R.string.remote_service_webdav_scheme),
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AuthChip("HTTPS", service.webDavHttps) {
                        onChange(service.copy(webDavHttps = true))
                    }
                    AuthChip("HTTP", !service.webDavHttps) {
                        onChange(service.copy(webDavHttps = false))
                    }
                }
                ServiceCredentials(service, onChange)
                Field(
                    label = stringResource(R.string.remote_service_base_path),
                    value = service.path,
                    onChange = { onChange(service.copy(path = it)) },
                    placeholder = "/dav/",
                )
            }
            RemoteServiceProtocol.VNC -> {
                Field(
                    label = stringResource(R.string.ssh_field_vnc_password),
                    value = service.password,
                    onChange = { onChange(service.copy(password = it)) },
                    placeholder = "********",
                    secret = true,
                    visibilityKey = "${service.id}:vnc-password",
                )
            }
        }
    }
}

@Composable
private fun ServiceCredentials(
    service: RemoteService,
    onChange: (RemoteService) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            Field(
                label = stringResource(R.string.ssh_field_user),
                value = service.user,
                onChange = { onChange(service.copy(user = it)) },
            )
        }
        Box(Modifier.weight(1f)) {
            Field(
                label = stringResource(R.string.ssh_field_password),
                value = service.password,
                onChange = { onChange(service.copy(password = it)) },
                placeholder = "********",
                secret = true,
                visibilityKey = "${service.id}:service-password",
            )
        }
    }
}

@Composable
private fun ServiceTunnelToggle(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgPrimary)
            .border(1.dp, if (checked) ZtsGreen else ZtsError, RoundedCornerShape(8.dp))
            .clickable { onChange(!checked) }
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (checked) "[x]" else "[ ]",
            color = if (checked) ZtsGreen else ZtsError,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Column {
            Text(
                text = stringResource(R.string.remote_service_use_ssh),
                color = if (checked) ZtsGreen else ZtsError,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Text(
                text = stringResource(
                    if (checked) R.string.remote_service_use_ssh_desc
                    else R.string.remote_service_direct_desc
                ),
                color = ZtsTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
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
    // × は各行の右端にあり、押すと転送 1 本の設定がまるごと消える (0.8.452)。
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }

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
                    onDelete = { pendingDeleteIndex = idx }
                )
            }
        }
    }

    // 行が減ると添字がずれるので、確認中に消える可能性のある添字は毎回見直す。
    pendingDeleteIndex?.let { idx ->
        val target = forwards.getOrNull(idx)
        if (target == null) {
            pendingDeleteIndex = null
        } else {
            ConfirmDialog(
                title = stringResource(R.string.confirm_delete_forward_title),
                // ⚠ 転送の指定は訳さない (一覧の状態表示と同じ理由 — そのまま検索できる形)。
                message = stringResource(
                    R.string.confirm_delete_item_msg,
                    "${if (target.reverse) "-R" else "-L"} ${target.localPort} → " +
                        "${target.remoteHost}:${target.remotePort}",
                ),
                confirmLabel = stringResource(R.string.ssh_action_delete),
                confirmColor = ZtsError,
                onConfirm = {
                    pendingDeleteIndex = null
                    onChange(forwards.toMutableList().also { it.removeAt(idx) })
                },
                onCancel = { pendingDeleteIndex = null },
            )
        }
    }
}

/**
 * 常駐トンネル (A2) の ON/OFF。
 *
 * ON にすると SSH タブを閉じても転送が生き続ける（常駐サーバーと同じ枠にぶら下がる）。
 * **明示 opt-in**にしているのは、知らないうちに外向きの口が開いたままにならないため。
 * `-R` を含むときは「外から入れるようになる」ことを併記する。
 */
@Composable
private fun ResidentTunnelToggle(
    checked: Boolean,
    hasReverse: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, if (checked) ZtsGreen else ZtsBorder, RoundedCornerShape(8.dp))
            .clickable { onChange(!checked) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (checked) "[x]" else "[ ]",
            color = if (checked) ZtsGreen else ZtsTextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ssh_resident_tunnel),
                color = if (checked) ZtsGreen else ZtsTextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = stringResource(
                    if (hasReverse) R.string.ssh_resident_tunnel_reverse_desc
                    else R.string.ssh_resident_tunnel_desc
                ),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** `-L` / `-R` を選ぶ小さなチップ。 */
@Composable
private fun DirectionChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) ZtsGreen.copy(alpha = 0.18f) else ZtsBgCard)
            .border(1.dp, if (selected) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = if (selected) ZtsGreen else ZtsTextPrimary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
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
        // 向きの切替。-L は「遠くをこちらへ」、-R は「こちらを遠くから」。
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionChip(
                label = "-L",
                selected = !fw.reverse,
                onSelect = { onChange(fw.copy(reverse = false)) }
            )
            DirectionChip(
                label = "-R",
                selected = fw.reverse,
                onSelect = { onChange(fw.copy(reverse = true)) }
            )
            Text(
                text = stringResource(
                    if (fw.reverse) R.string.ssh_forward_reverse_desc else R.string.ssh_forward_local_desc
                ),
                color = ZtsTextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ⚠ **どちらの端がどちらの欄なのか**をラベルで言い切る。-R では上下とも "Remote" と
            // 出ていて、何をどこへ書くのか画面から読めなかった (利用者の指摘)。
            Text(
                text = if (fw.reverse) stringResource(R.string.ssh_forward_side_listen_remote)
                else stringResource(R.string.ssh_forward_side_listen_local),
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
                text = if (fw.reverse) stringResource(R.string.ssh_forward_side_dest_local)
                else stringResource(R.string.ssh_forward_side_dest_remote),
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
    /** 別の接続先へ切り替わったら、前の接続先で「表示」にしていても必ず伏せ字へ戻す。 */
    visibilityKey: Any? = null,
    multiline: Boolean = false
) {
    var secretVisible by remember(label, visibilityKey) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
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
                    visualTransformation = if (secret && !secretVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    textStyle = TextStyle(
                        color = ZtsTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(ZtsGreen),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (secret) {
                SmallButton(
                    label = stringResource(
                        if (secretVisible) R.string.password_hide else R.string.password_show
                    ),
                    accent = secretVisible,
                    onClick = { secretVisible = !secretVisible },
                )
            }
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
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

/**
 * SSH クライアント鍵を「作る」「渡す」「この端末に登録する」ための 1 行 (0.8.238)。
 *
 * これまでは秘密鍵の PEM を貼るしか手が無く、**スマホでそれを用意するのがまず無理**で、
 * SSH を使い始める前にここで止まっていた。作ったらその場で公開鍵を渡せるところまでを
 * 1 か所に置く（渡すのは作った直後だけなので、公開鍵は保存せず画面の状態に留める）。
 */
@Composable
private fun SshKeyRow(
    hasKey: Boolean,
    publicLine: String,
    onGenerate: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                label = stringResource(
                    if (hasKey) R.string.ssh_key_regenerate else R.string.ssh_key_generate
                ),
                onClick = onGenerate
            )
        }
        if (publicLine.isNotEmpty()) {
            Text(
                text = stringResource(R.string.ssh_key_public_hint),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = publicLine,
                color = ZtsGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(label = stringResource(R.string.ssh_key_copy)) {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("z2term", publicLine))
                    Toast.makeText(context, R.string.ssh_key_copied, Toast.LENGTH_SHORT).show()
                }
                SmallButton(label = stringResource(R.string.ssh_key_share)) {
                    runCatching {
                        val i = android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, publicLine)
                        context.startActivity(
                            android.content.Intent.createChooser(i, null)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                // この端末の内蔵 sshd に登録する。これまで端末で
                // `cat … >> ~/.ssh/authorized_keys && chmod 600 …` と打たせていた作業。
                SmallButton(label = stringResource(R.string.ssh_key_authorize)) {
                    val added = runCatching {
                        SshKeyGen.addToAuthorizedKeys(context, publicLine)
                    }.getOrDefault(false)
                    Toast.makeText(
                        context,
                        if (added) R.string.ssh_key_authorized else R.string.ssh_key_authorized_already,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
