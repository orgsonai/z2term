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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * GUI セッションのリモート画面 (M8-2 表示 + M8-3 入力)。
 *
 * [RfbClient][com.zerotoship.z2term.gui.rfb.RfbClient] が更新する Bitmap を、アスペクト比を保ったまま
 * 中央にフィット表示する。再描画は `rfb.redraw` の collect で発火（端末 TerminalRenderer と同方式）。
 * CONNECTED 後は [GuiInputView] を上に重ねてタッチ/キー入力を RFB へ送る（座標フィット計算は
 * GuiInputView 側と一致させてある）。ピンチ/パン・回転対応は M8-4。
 */
@Composable
fun GuiScreen(session: GuiSession, modifier: Modifier = Modifier) {
    val state by session.state.collectAsState()
    val message by session.message.collectAsState()
    val tick by session.rfb.redraw.collectAsState()
    var inputView by remember { mutableStateOf<GuiInputView?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") tick // recompose / redraw トリガ
            val bmp = session.rfb.frame ?: return@Canvas
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            if (bw <= 0f || bh <= 0f) return@Canvas
            val scale = minOf(size.width / bw, size.height / bh)
            val dw = bw * scale
            val dh = bh * scale
            val left = (size.width - dw) / 2f
            val top = (size.height - dh) / 2f
            drawIntoCanvas { canvas ->
                synchronized(session.rfb.frameLock) {
                    canvas.nativeCanvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), null)
                }
            }
        }

        if (state == GuiSession.State.CONNECTED) {
            // 透明オーバーレイ: タッチ/キー → RFB 入力。
            AndroidView(
                factory = { ctx -> GuiInputView(ctx).also { it.rfb = session.rfb; inputView = it } },
                update = { it.rfb = session.rfb },
                modifier = Modifier.fillMaxSize(),
            )
            // キーボード表示トグル。
            FloatingActionButton(
                onClick = { inputView?.toggleKeyboard() },
                containerColor = Color(0xFF22C55E),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                Text("⌨", fontSize = 22.sp, color = Color.Black)
            }
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
                Text(
                    text = message.ifBlank { state.name },
                    color = if (state == GuiSession.State.ERROR) Color(0xFFEF4444) else Color.White,
                )
            }
        }
    }
}
