package com.zerotoship.z2term.ui.ssh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.channel.HostKeyVerifier
import com.zerotoship.z2term.ui.theme.TerminalFontFamily
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/**
 * `HostKeyVerifier.flow` を購読し、未知ホストの確認ダイアログを表示。
 * 「信頼して接続」で `resolve(true)`、「キャンセル」で `resolve(false)`。
 */
@Composable
fun HostKeyVerificationDialog() {
    val prompt by HostKeyVerifier.flow.collectAsState()
    val current = prompt ?: return

    AlertDialog(
        onDismissRequest = { HostKeyVerifier.resolve(false) },
        containerColor = ZtsBgSecondary,
        title = {
            Text(
                text = "未知のホスト鍵",
                color = ZtsError,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(current.host, color = ZtsTextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text("鍵タイプ: ${current.keyType}", color = ZtsTextSecondary, fontSize = 12.sp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                        .background(ZtsBgPrimary)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = current.fingerprint,
                        color = ZtsGreen,
                        fontFamily = TerminalFontFamily,
                        fontSize = 12.sp
                    )
                }
                Text(
                    "このホストに初めて接続します。鍵を信頼して known_hosts に追加するか、" +
                        "中間者攻撃を疑ってキャンセルしてください。",
                    color = ZtsTextSecondary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { HostKeyVerifier.resolve(true) }) {
                Text("信頼して接続", color = ZtsGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = { HostKeyVerifier.resolve(false) }) {
                Text("キャンセル", color = ZtsTextSecondary)
            }
        }
    )
}
