package com.zerotoship.z2term.legal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R

/**
 * OSS ライセンス一覧 Dialog (設定 → 「OSS ライセンス」をタップしたとき開く)。
 *
 * 設定シートに直に並べると視認性が悪い (項目数が多い) ため、タップで開く全画面 Dialog に切り出した。
 * 内部の一行ずつのエントリ表示は [LicensesSection] を再利用 (Dialog の中で縦スクロール)。
 */
@Composable
fun LicensesDialog(
    onDismiss: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    border: Color,
    background: Color,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = background,
            modifier = Modifier
                .fillMaxSize()
                .border(width = 1.dp, color = border),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.licenses_dialog_title),
                        color = accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Box(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close_dialog), color = textPrimary) }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    LicensesSection(
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accent = accent,
                        border = border,
                    )
                }
            }
        }
    }
}

/**
 * OSS ライセンス一覧 (Dialog 内に展開する縦並びの本体)。
 *
 * 「タップでライセンス全文ダイアログを開く」エントリを縦に並べる。全文は `assets/licenses/<id>.txt`
 * から読む。
 *
 * GPL/LGPL の頒布要件 (ソース提供義務) は **対応ソース URL の表示** + ライセンス全文同梱 で果たす。
 */
@Composable
fun LicensesSection(
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    border: Color,
) {
    val context = LocalContext.current
    val components = remember { OssComponents.forCurrentFlavor() }
    var openComponent by remember { mutableStateOf<OssComponent?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.licenses_section_summary),
            color = textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        components.forEach { c ->
            LicenseRow(
                component = c,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accent = accent,
                border = border,
                onClick = { openComponent = c },
                onOpenSource = { openUrl(context, c.sourceUrl) },
            )
        }
    }

    openComponent?.let { c ->
        LicenseFullTextDialog(
            component = c,
            onDismiss = { openComponent = null },
            onOpenSource = { openUrl(context, c.sourceUrl) },
        )
    }
}

@Composable
private fun LicenseRow(
    component: OssComponent,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    border: Color,
    onClick: () -> Unit,
    onOpenSource: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = component.name,
                color = textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = component.licenseId,
                color = accent,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = stringResource(component.purposeRes),
            color = textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = component.copyright,
            color = textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = stringResource(R.string.licenses_source_line, component.sourceUrl),
            color = textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { onOpenSource() },
        )
    }
}

@Composable
private fun LicenseFullTextDialog(
    component: OssComponent,
    onDismiss: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val context = LocalContext.current
    val body = remember(component.licenseId) { readLicenseText(context, component.licenseId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${component.name}  (${component.licenseId})") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(min = 240.dp, max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = component.copyright, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(text = stringResource(R.string.licenses_source_line, component.sourceUrl), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(text = "—", fontSize = 11.sp)
                Text(text = body, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSource) { Text(stringResource(R.string.action_open_source)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close_dialog)) }
        },
    )
}

/**
 * `assets/licenses/<id>.txt` を UTF-8 で読む。無ければ「公式 URL を参照」のフォールバック文字列。
 * プレースホルダ TXT を同梱する運用では、ファイル内に curl コマンドの取得手順が書かれている。
 */
private fun readLicenseText(context: Context, licenseId: String): String =
    runCatching {
        context.assets.open("licenses/$licenseId.txt").use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrElse {
        context.getString(R.string.licenses_full_text_missing, licenseId)
    }

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
