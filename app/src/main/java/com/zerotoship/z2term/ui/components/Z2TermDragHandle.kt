package com.zerotoship.z2term.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.ui.theme.ZtsBorder

/**
 * ModalBottomSheet 共通のドラッグハンドル。
 *
 * 見た目は従来どおりの小さなバーだが、**ハンドル行全体をタップで閉じられる**ように
 * してある (幅いっぱいのタップ領域)。これにより、内容をスクロールしている途中の
 * 下スワイプで誤ってシートが閉じてしまう問題があっても、確実に閉じる手段を確保する。
 *
 * スワイプでの閉じ可否は各シート側の `confirmValueChange` で「最上部のときだけ許可」
 * と制御する。タップ時は [onClose] (forceClose 経由でアニメ付きクローズ) を呼ぶ。
 */
@Composable
fun Z2TermDragHandle(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
            .padding(top = 10.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ZtsBorder)
        )
    }
}
