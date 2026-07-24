package com.zerotoship.z2term.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/**
 * 常駐サーバー稼働中に🔒(バックグラウンド常駐トグル)をタップしたときの操作ダイアログ。
 *
 * 常駐サーバーが動いている間はプロセスが生き続けるため、🔒 を OFF にしてもセッションは消えない
 * (最近履歴からのスワイプも効かない)。トグルの代わりに、常駐に閉じ込められないための「終了の出口」
 * を提示する:
 *  - [onResetSession]: 対話セッションだけ初期状態へ戻す (常駐サーバーはそのまま)。
 *  - [onStopAll]: 常駐サーバーもセッションも止めてアプリを閉じる (タスクキル相当)。
 */
@Composable
fun ResidentActionDialog(
    onResetSession: () -> Unit,
    onStopAll: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = ZtsBgCard,
        title = {
            Text(
                text = stringResource(R.string.resident_dialog_title),
                color = ZtsTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.resident_dialog_message),
                    color = ZtsTextSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                ActionRow(
                    label = stringResource(R.string.resident_dialog_reset_session),
                    desc = stringResource(R.string.resident_dialog_reset_session_desc),
                    accent = ZtsGreen,
                    onClick = onResetSession
                )
                ActionRow(
                    label = stringResource(R.string.resident_dialog_stop_all),
                    desc = stringResource(R.string.resident_dialog_stop_all_desc),
                    accent = ZtsError,
                    onClick = onStopAll
                )
            }
        },
        // 実行ボタンは行カード側に持たせているので confirmButton は空にする。
        confirmButton = {},
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

/** ダイアログ内の 1 アクション (見出し + 説明) をタップ可能なカードで表す。 */
@Composable
private fun ActionRow(
    label: String,
    desc: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, accent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = desc,
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
