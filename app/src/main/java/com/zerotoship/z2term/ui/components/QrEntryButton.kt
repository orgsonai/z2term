package com.zerotoship.z2term.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.qr.QrToolsActivity
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary

/**
 * 一覧の見出しに置く「QR」(0.8.601)。QR ツールを開く。
 *
 * QR で取り込んだものは SSH 接続先かコマンドとして保存されるので、その一覧 (接続先タブ・
 * スニペットタブ) の見出しで「+ 新規」の隣に置く。
 * ⚠ **シートの取っ手の行に戻さない**。0.8.597〜0.8.600 は取っ手の左に QR、右に「閉じる」を
 * 置いていたが、シート全体の操作に見えて違和感があった (利用者の指摘)。取っ手はタップで閉じる。
 * 隣の「＋ 新規」と同じ大きさで、色は付けない (追加より目立たせない)。
 */
@Composable
fun QrEntryButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val description = stringResource(R.string.qr_tools_title)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .clickable { QrToolsActivity.open(context) }
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "QR",
            color = ZtsTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
