package com.zerotoship.z2term.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zerotoship.z2term.R

/**
 * 通信を伴うダウンロードの前に出す確認ダイアログ (M8-6 T7)。
 *
 * distro 切替・GUI パッケージ導入など、回線・データ通信を使う処理の直前に表示する。
 * 「勝手にダウンロードしない」方針 (memory: no-unsanctioned-downloads) の UI 実装。
 * 設定の「ダウンロード前に確認」が OFF のときは呼び出し側が表示せず即実行する。
 *
 * 見た目・ボタン配置は汎用の [ConfirmDialog] に委譲する (実行ラベルの既定だけ違う)。
 */
@Composable
fun DownloadConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.action_download),
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    ConfirmDialog(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onCancel = onCancel
    )
}
