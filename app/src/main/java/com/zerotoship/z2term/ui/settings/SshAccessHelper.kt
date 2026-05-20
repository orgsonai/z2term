package com.zerotoship.z2term.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

/** ssh で待ち受けるポート (1024 未満は Android kernel が拒否するため 2222) */
private const val Z2TERM_SSHD_PORT = 2222

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
                    val script = buildSshdSetupScript(Z2TERM_SSHD_PORT)
                    session.writeBytes(script.toByteArray(Charsets.UTF_8))
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
            text = "詳細は docs/SSH-INTO-Z2TERM.md を参照",
            color = ZtsTextSecondary.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
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
 * Alpine 内で dropbear (SSH サーバ) を起動するワンライナースクリプト。
 *
 * OpenSSH sshd は proot 環境で権限分離 (privsep) に失敗して接続が即 reset
 * されるため、proot 下でも安定動作する dropbear を使う。
 *  - ホスト鍵が無ければ dropbearkey で生成 (ed25519 / rsa)
 *  - 既存 dropbear を止めてから指定ポートで起動 (-R 自動鍵, パスワード認証 OK)
 *  - root にパスワードが無ければ警告 (dropbear は空パスワード接続を拒否する)
 */
private fun buildSshdSetupScript(port: Int): String = """
    {
      mkdir -p /etc/dropbear
      [ -f /etc/dropbear/dropbear_ed25519_host_key ] || dropbearkey -t ed25519 -f /etc/dropbear/dropbear_ed25519_host_key 2>/dev/null
      [ -f /etc/dropbear/dropbear_rsa_host_key ] || dropbearkey -t rsa -s 2048 -f /etc/dropbear/dropbear_rsa_host_key 2>/dev/null
      pkill -x dropbear 2>/dev/null
      # -p ポート / -R 鍵自動 / -E stderr ログ。root ログイン・パスワード認証は既定で許可。
      dropbear -p $port -R -E 2>/tmp/dropbear.log && \
        echo "✅ dropbear listening on :$port (root @ $(ip -4 addr show 2>/dev/null | grep -oE 'inet [0-9.]+' | awk '{print ${'$'}2}' | grep -v '^127' | head -n1))" || \
        echo "❌ dropbear 起動失敗 (/tmp/dropbear.log を確認)"
      [ "${'$'}(grep -c '^root:[^!*]' /etc/shadow 2>/dev/null)" = 0 ] && \
        echo "⚠️ root パスワード未設定です。'passwd' で設定してから接続してください。"
    }

""".trimIndent()
