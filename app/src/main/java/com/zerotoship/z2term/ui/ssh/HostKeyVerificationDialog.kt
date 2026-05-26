package com.zerotoship.z2term.ui.ssh

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.HostKeyVerifier
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import com.zerotoship.z2term.ui.theme.ZtsWarning

/**
 * SSH ホスト鍵の信頼確認ダイアログ。
 *
 * [HostKeyVerifier.flow] が non-null になるとモーダル表示。
 *  - 「信頼して接続」: known_hosts に追加 + 接続継続 (HostKeyVerifier.resolve(true))
 *  - 「キャンセル」: 接続中断 (HostKeyVerifier.resolve(false))
 *
 * 接続スレッドは resolve() が呼ばれるまで blocking する設計のため、
 * このダイアログを必ずどこかでマウントしておく必要がある (TerminalScreen ルート)。
 */
@Composable
fun HostKeyVerificationDialog() {
    val prompt by HostKeyVerifier.flow.collectAsState()
    val current = prompt ?: return
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { HostKeyVerifier.resolve(false) },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ZtsBgSecondary)
                .border(1.dp, ZtsBorder, RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.hostkey_title),
                color = ZtsWarning,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = current.message.ifEmpty {
                    context.getString(R.string.hostkey_not_in_known_hosts, current.host)
                },
                color = ZtsTextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            FingerprintRow(label = stringResource(R.string.hostkey_field_host), value = current.host)
            FingerprintRow(label = stringResource(R.string.hostkey_field_keytype), value = current.keyType)
            FingerprintRow(label = "fingerprint", value = current.fingerprint)

            Text(
                text = stringResource(R.string.hostkey_warning),
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f))
                DialogButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = { HostKeyVerifier.resolve(false) }
                )
                DialogButton(
                    label = stringResource(R.string.hostkey_action_trust),
                    accent = true,
                    onClick = { HostKeyVerifier.resolve(true) }
                )
            }
        }
    }
}

@Composable
private fun FingerprintRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = value,
                color = ZtsTextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val border = if (accent) ZtsGreen else ZtsBorder
    val fg = if (accent) ZtsGreen else ZtsTextPrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
