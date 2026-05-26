package com.zerotoship.z2term.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/**
 * 通信を伴うダウンロードの前に出す確認ダイアログ (M8-6 T7)。
 *
 * distro 切替・GUI パッケージ導入など、回線・データ通信を使う処理の直前に表示する。
 * 「勝手にダウンロードしない」方針 (memory: no-unsanctioned-downloads) の UI 実装。
 * 設定の「ダウンロード前に確認」が OFF のときは呼び出し側が表示せず即実行する。
 */
@Composable
fun DownloadConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.action_download),
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = ZtsBgCard,
        title = {
            Text(
                text = title,
                color = ZtsTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Text(
                text = message,
                color = ZtsTextSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = ZtsGreen,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = ZtsTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}
