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
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.proot.Z2TERM_SSHD_PORT
import com.zerotoship.z2term.proot.dropbearBootstrapScript
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
    LaunchedEffect(Unit) {
        ips = withContext(Dispatchers.IO) { detectIpv4Addresses() }
    }
    val primaryIp = ips.firstOrNull() ?: "<端末IP>"
    val sshCmd = "ssh -p $Z2TERM_SSHD_PORT root@$primaryIp"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "PC からの SSH 接続",
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
                    text = "Wi-Fi に接続して下さい (IP 未検出)",
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
                text = "\nPC から:",
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
                    // スクリプトは rootfs (= /root) にファイルとして書き出し、`sh` で実行する。
                    // 端末へ複数行スクリプトを直接打鍵すると zsh がコメント(#)を
                    // 「command not found」にしたり継続プロンプト(cursh>)で崩れるため。
                    writeSshdScript(context, Z2TERM_SSHD_PORT)
                    session.writeBytes("sh \"\$HOME/.z2term-sshd.sh\"\n".toByteArray(Charsets.UTF_8))
                }
            )
            HelperButton(
                label = "passwd 実行",
                onClick = {
                    session.writeBytes("passwd\n".toByteArray(Charsets.UTF_8))
                }
            )
            HelperButton(
                label = "ssh コマンドをコピー",
                onClick = { copyToClipboard(context, "z2term ssh", sshCmd) }
            )
        }
        Text(
            text = "端末で `sshd` と打つだけでも起動できます (OpenSSH の /usr/sbin/sshd は\n" +
                "proot で privsep 破綻のため使えません → dropbear を使用)。\n" +
                "詳細は docs/SSH-INTO-Z2TERM.md を参照",
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
            text = "ストレージアクセス (cd /sdcard)",
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = if (granted)
                "✅ 許可済み。端末から `cd /sdcard` で共有ストレージへ移動できます。\n" +
                    "   権限不要のアプリ専用領域は `/storage/app` です。"
            else
                "未許可。`cd /sdcard` の中身は見えません。下のボタンで全ファイル\n" +
                    "アクセスを許可してください。(`/storage/app` は許可不要で使えます)",
            color = if (granted) ZtsGreen else ZtsTextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        if (!granted) {
            Row {
                HelperButton(
                    label = "ストレージ全体を許可",
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
 * dropbear 起動スクリプト ([dropbearBootstrapScript]) を rootfs (= /root) に
 * ファイルとして書き出す。`sh <file>` で実行されるため、コメントや複数行・
 * パイプが安全に使える (端末への直接打鍵だと zsh がコメントを誤実行する)。
 * (端末では `sshd` コマンド = /usr/local/sbin/sshd でも同じ処理が走る)
 */
private fun writeSshdScript(context: Context, port: Int): java.io.File {
    val dir = java.io.File(context.filesDir, "shared_home").apply { mkdirs() }
    val file = java.io.File(dir, ".z2term-sshd.sh")
    file.writeText(dropbearBootstrapScript(port))
    return file
}
