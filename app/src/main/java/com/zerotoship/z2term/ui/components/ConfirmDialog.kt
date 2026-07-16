package com.zerotoship.z2term.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
 * 汎用の確認ダイアログ (タイトル + 本文 + 実行 / やめる)。
 *
 * 「実行して良いか」を一言問うだけの用途を 1 か所に集約するための共通コンポーネント。
 * ダウンロード確認 ([DownloadConfirmDialog]) やタブ削除確認などがこれを再利用する。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmColor: Color = ZtsGreen
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
                    color = confirmColor,
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
