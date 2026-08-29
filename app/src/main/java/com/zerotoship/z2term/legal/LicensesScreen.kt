package com.zerotoship.z2term.legal

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.core.net.toUri
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
                LicensesSection(
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accent = accent,
                    border = border,
                    background = background,
                    modifier = Modifier.fillMaxSize(),
                )
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
    background: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val components = remember { OssComponents.list() }
    var openComponent by remember { mutableStateOf<OssComponent?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.licenses_section_summary),
                    color = textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = stringResource(R.string.licenses_component_count, components.size),
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        items(components, key = { it.name }) { c ->
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
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accent = accent,
            border = border,
            background = background,
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color.Black.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, border.copy(alpha = 0.75f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = component.name,
                    color = textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                LicenseBadge(component.licenseId, accent)
            }
            Text(
                text = stringResource(component.purposeRes),
                color = textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Text(
                text = component.copyright,
                color = textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClick) {
                    Text(stringResource(R.string.licenses_view_full_text), color = accent)
                }
                TextButton(onClick = onOpenSource) {
                    Text(stringResource(R.string.action_open_source), color = accent)
                }
            }
        }
    }
}

@Composable
private fun LicenseBadge(
    licenseId: String,
    accent: Color,
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
    ) {
        Text(
            text = licenseId,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun LicenseFullTextDialog(
    component: OssComponent,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    border: Color,
    background: Color,
    onDismiss: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val context = LocalContext.current
    val body = remember(component.licenseAsset) { readLicenseText(context, component) }
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
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.licenses_full_text_title),
                        color = accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close_dialog), color = textPrimary)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, border.copy(alpha = 0.75f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = component.name,
                                    color = textPrimary,
                                    fontSize = 17.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                LicenseBadge(component.licenseId, accent)
                            }
                            Text(
                                text = stringResource(component.purposeRes),
                                color = textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                            SelectionContainer {
                                Text(
                                    text = component.copyright,
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                            TextButton(onClick = onOpenSource) {
                                Text(stringResource(R.string.action_open_source), color = accent)
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.licenses_license_text_heading),
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SelectionContainer {
                        Text(
                            text = body,
                            color = textPrimary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

/**
 * `assets/licenses/<id>.txt` を UTF-8 で読む。無ければ「公式 URL を参照」のフォールバック文字列。
 * プレースホルダ TXT を同梱する運用では、ファイル内に curl コマンドの取得手順が書かれている。
 */
private fun readLicenseText(context: Context, component: OssComponent): String =
    runCatching {
        context.assets.open("licenses/${component.licenseAsset}.txt").use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrElse {
        context.getString(R.string.licenses_full_text_missing, component.licenseId)
    }

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
