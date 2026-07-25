package com.zerotoship.z2term.widget

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/**
 * ウィジェットの設定画面で共通に使う部品 ([WidgetConfigActivity] = D1 / [TailConfigActivity] = D2)。
 *
 * 見た目を 2 か所に書くと必ずズレるので、D2 を足すときにここへ切り出した。
 * アプリ本体の設定シートと同じ ZTS の配色・等幅フォントに揃えてある。
 */

/**
 * 選択肢 1 行。[checked] のときは緑の枠と `[x]`。[subtitle] は空なら出さない。
 *
 * 既定は幅いっぱい。2 つを横に並べたいときだけ [modifier] に `weight` を渡す。
 */
@Composable
internal fun ConfigSelectRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onToggle: () -> Unit,
) {
    val fg = when {
        checked -> ZtsGreen
        enabled -> ZtsTextPrimary
        else -> ZtsTextSecondary.copy(alpha = 0.5f)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, if (checked) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (checked) "[x]" else "[ ]",
            color = fg,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = fg,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/** 保存 / キャンセルなどのボタン。 */
@Composable
internal fun ConfigButton(
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, if (accent) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = if (accent) ZtsGreen else ZtsTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
