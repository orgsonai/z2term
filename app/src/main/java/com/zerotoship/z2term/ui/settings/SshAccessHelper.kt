package com.zerotoship.z2term.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.proot.Z2TERM_SSHD_PORT
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 設定シートに埋め込む「PC からの SSH 接続」ヘルパー。
 *
 *  - 端末 IPv4 (Wi-Fi / Cellular など) を NetworkInterface から自動列挙
 *  - 「sshd を起動」ボタンで Alpine 内に必要な準備 → /usr/sbin/sshd 起動を 1 タップ
 *  - 「ssh コマンドをコピー」でクリップボードへ `ssh -p 2222 root@<ip>` を送る
 *  - 「passwd を実行」で `passwd` をターミナルへ送って対話入力を促す
 */
@Composable
fun SshAccessHelper(session: TerminalSession) {
    val context = LocalContext.current
    var ips by remember { mutableStateOf<List<String>>(emptyList()) }
    // 表示用ポートは sshd_config の Port を反映 (無ければ既定 2222)。`sshd` コマンドと一致。
    var sshdPort by remember { mutableStateOf(Z2TERM_SSHD_PORT) }
    LaunchedEffect(Unit) {
        ips = withContext(Dispatchers.IO) { detectIpv4Addresses() }
        sshdPort = withContext(Dispatchers.IO) { readConfiguredSshdPort(context, session) }
    }
    val primaryIp = ips.firstOrNull() ?: "<端末IP>"
    val sshCmd = "ssh -p $sshdPort root@$primaryIp"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.sshaccess_title),
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        // IP リスト
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (ips.isEmpty()) {
                Text(
                    text = stringResource(R.string.sshaccess_wifi_unavailable),
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "端末 IPv4:",
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                ips.forEach { ip ->
                    Text(
                        text = "  $ip",
                        color = ZtsTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                text = stringResource(R.string.sshaccess_from_pc_header),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "  $sshCmd",
                color = ZtsGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HelperButton(
                label = "sshd 起動",
                accent = true,
                onClick = {
                    // `sshd` = /usr/local/sbin/sshd ラッパー (ProotLauncher が配置)。
                    // sshd_config の Port を読んで dropbear を起動する。
                    session.writeBytes("sshd\n".toByteArray(Charsets.UTF_8))
                }
            )
            HelperButton(
                label = "passwd 実行",
                onClick = {
                    session.writeBytes("passwd\n".toByteArray(Charsets.UTF_8))
                }
            )
            HelperButton(
                label = stringResource(R.string.sshaccess_copy_ssh_cmd),
                onClick = { copyToClipboard(context, "z2term ssh", sshCmd) }
            )
        }
        Text(
            text = stringResource(R.string.sshaccess_note),
            color = ZtsTextSecondary.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 端末から Android 共有ストレージ (/sdcard) を読み書きするための権限ヘルパー。
 *
 * proot は `/sdcard` に `/storage/emulated/0` をバインドしているが、全ファイル
 * アクセス権が無いと中身が見えない (EACCES)。ここから許可画面へ誘導する。
 * 権限付与後は `cd /sdcard` で Download や写真などへ移動できる。
 */
@Composable
fun StorageAccessHelper() {
    val context = LocalContext.current
    val granted = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else true  // API 29 は requestLegacyExternalStorage で従来権限が効く
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.storage_access_title),
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = if (granted) stringResource(R.string.storage_access_granted)
                   else stringResource(R.string.storage_access_denied),
            color = if (granted) ZtsGreen else ZtsTextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        if (!granted) {
            Row {
                HelperButton(
                    label = stringResource(R.string.storage_access_grant_button),
                    accent = true,
                    onClick = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                        runCatching { context.startActivity(intent) }
                    }
                )
            }
        }
    }
}

@Composable
private fun HelperButton(
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val border = if (accent) ZtsGreen else ZtsBorder
    val fg = if (accent) ZtsGreen else ZtsTextPrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * NetworkInterface を全列挙して、ローカルじゃない IPv4 を返す。
 * 通常 Wi-Fi の wlan0 が `192.168.x.x` を返す。
 */
private fun detectIpv4Addresses(): List<String> {
    return try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface -> iface.inetAddresses.toList().map { iface.name to it } }
            .filter { (_, addr) -> addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress }
            .mapNotNull { (name, addr) -> addr.hostAddress?.let { "$it ($name)" } }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * 実行中 distro の `/etc/ssh/sshd_config` から `Port` を読む。
 * `sshd` コマンド (dropbear ラッパー) と同じ優先順 (config の Port、無ければ既定)。
 * コメント行や `PortForwarding` 等の別ディレクティブは無視する。
 */
private fun readConfiguredSshdPort(context: Context, session: TerminalSession): Int {
    val distroId = session.settingsFlow.value.distroId
    val cfg = java.io.File(context.filesDir, "distros/$distroId/etc/ssh/sshd_config")
    if (!cfg.isFile) return Z2TERM_SSHD_PORT
    return runCatching {
        cfg.readLines().firstNotNullOfOrNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2 && parts[0].equals("Port", ignoreCase = true)) parts[1].toIntOrNull()
            else null
        }
    }.getOrNull() ?: Z2TERM_SSHD_PORT
}
