package com.zerotoship.z2term.qr

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.settings.PillButton
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/*
 * QR ツールと中継共有の画面で使う部品 (0.8.601)。
 *
 * どちらも別 Activity の全画面だが、見た目はコマンド一覧のタブ (サーバー・SSH) に揃える:
 * 等幅の文字、緑の見出し、枠付きの PillButton / Field / HintBox。以前は Material 標準の
 * ボタン・入力欄・チップのままで、アプリの他の画面から浮いていた (利用者の指摘)。
 */

/** 見出しと「閉じる」。コマンド一覧の各タブの見出し行と同じ大きさ。 */
@Composable
internal fun ToolScreenHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = ZtsGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        PillButton(label = stringResource(R.string.qr_tools_close), onClick = onClose)
    }
}

/** ボタンや入力欄の組の上に置く 1 行。 */
@Composable
internal fun ToolLabel(text: String) {
    Text(text = text, color = ZtsTextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
}

/** 補足の文。 */
@Composable
internal fun ToolNote(text: String, color: Color = ZtsTextSecondary) {
    Text(text = text, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}

@Composable
internal fun ToolError(text: String) {
    ToolNote(text, ZtsError)
}

@Composable
internal fun ToolProgress() {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ZtsGreen, trackColor = ZtsBgCard)
}
