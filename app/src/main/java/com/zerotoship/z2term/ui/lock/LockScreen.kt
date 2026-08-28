package com.zerotoship.z2term.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/**
 * ロック画面 ([com.zerotoship.z2term.security.AppLock])。
 *
 * ⛔ **端末の中身を 1 文字も出さない。** 背景ごと覆い、下の画面は組み立てない
 * (呼び出し側が端末の代わりにこれを出す)。透過や薄い幕にすると、履歴画面の縮小画像に
 * 中身が透けて残る。
 *
 * 置いてあるのはボタン 1 つだけ。⚠ **自動で確認をやり直さない** — 外したときに勝手に
 * 出し直すと、指を置くつもりがないまま端末側のロックアウトまで進んでしまう。
 */
@Composable
fun LockScreen(failed: Boolean, onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZtsBgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = "🔒", fontSize = 40.sp)
            Text(
                text = stringResource(R.string.lock_title),
                color = ZtsTextPrimary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(if (failed) R.string.lock_failed else R.string.lock_desc),
                color = if (failed) ZtsError else ZtsTextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ZtsBgCard)
                    .border(1.dp, ZtsGreen, RoundedCornerShape(8.dp))
                    .clickable { onUnlock() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.lock_unlock),
                    color = ZtsGreen,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
