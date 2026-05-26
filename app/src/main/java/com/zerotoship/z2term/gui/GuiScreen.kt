package com.zerotoship.z2term.gui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.widthIn

/**
 * GUI セッションのリモート画面 (M8-2 表示 + M8-3 入力)。
 *
 * [RfbClient][com.zerotoship.z2term.gui.rfb.RfbClient] が更新する Bitmap を、アスペクト比を保ったまま
 * 中央にフィット表示する。再描画は `rfb.redraw` の collect で発火（端末 TerminalRenderer と同方式）。
 * CONNECTED 後は [GuiInputView] を上に重ねてタッチ/キー入力を RFB へ送る（座標フィット計算は
 * GuiInputView 側と一致させてある）。
 *
 * キーボードは端末と共通の「ツールバー仕様」を [GuiTabScreen][com.zerotoship.z2term.ui.terminal] 側で
 * GUI に**上乗せ**表示する（この画面の解像度・フィットは変えない）。この画面は表示とポインタ、
 * および SYSTEM モード時の OS IME 表示制御だけを担う。
 *
 * @param imeVisible    SYSTEM キーボードモードで OS ソフト IME を出すか。
 * @param ctrlSticky    SYSTEM モードの sticky Ctrl。OS IME 確定文字へ Ctrl を付ける。
 * @param onCtrlConsumed sticky Ctrl を 1 文字に適用したとき呼ばれる（呼び出し側で解除）。
 */
@Composable
fun GuiScreen(
    session: GuiSession,
    imeVisible: Boolean = false,
    ctrlSticky: Boolean = false,
    onCtrlConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsState()
    val message by session.message.collectAsState()
    val tick by session.rfb.redraw.collectAsState()
    val vrev by session.viewport.rev.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            // ズーム/パンで Bitmap がこの領域より大きく描かれても、上の TabBar/TopBar へは
            // はみ出さないよう描画をクリップ (M8-6 T2)。FB 解像度・入力座標 (toFb) は不変。
            .clipToBounds()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") run { tick; vrev } // FB 更新 / ズーム・パン変更で再描画
            val bmp = session.rfb.frame ?: return@Canvas
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            if (bw <= 0f || bh <= 0f) return@Canvas
            // フィット倍率 × ユーザーズーム。中央フィット + パン (GuiInputView の toFb と一致)。
            val eff = minOf(size.width / bw, size.height / bh) * session.viewport.scale
            val dw = bw * eff
            val dh = bh * eff
            val left = (size.width - dw) / 2f + session.viewport.panX
            val top = (size.height - dh) / 2f + session.viewport.panY
            drawIntoCanvas { canvas ->
                synchronized(session.rfb.frameLock) {
                    canvas.nativeCanvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), null)
                }
            }
        }

        if (state == GuiSession.State.CONNECTED) {
            // 透明オーバーレイ: タッチ/キー → RFB 入力。OS IME 表示はキーボードモードに追従。
            AndroidView(
                factory = { ctx ->
                    GuiInputView(ctx).also {
                        it.rfb = session.rfb
                        it.viewport = session.viewport
                        it.ctrlSticky = ctrlSticky
                        it.onCtrlConsumed = onCtrlConsumed
                    }
                },
                update = {
                    it.rfb = session.rfb
                    it.viewport = session.viewport
                    it.ctrlSticky = ctrlSticky
                    it.onCtrlConsumed = onCtrlConsumed
                    if (imeVisible) it.showIme() else it.hideIme()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (state != GuiSession.State.CONNECTED) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                if (state == GuiSession.State.STARTING || state == GuiSession.State.CONNECTING) {
                    CircularProgressIndicator(color = Color(0xFF22C55E))
                }
                // 進捗テキストはパッケージ取得ログがそのまま流れることがあり、
                // 端末横幅を超える長い行や複数情報が混じる。横にはみ出して右端ステータスや
                // タブ名を押し出すのを防ぐため、画面幅の 80% を上限・最大 4 行で省略する。
                // フォントは monospace (進捗の "(1/9)" 等が読みやすい)。
                Text(
                    text = message.ifBlank { state.name },
                    color = if (state == GuiSession.State.ERROR) Color(0xFFEF4444) else Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    softWrap = true,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
        }
    }
}
