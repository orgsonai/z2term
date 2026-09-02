package com.zerotoship.z2term.gui

import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * GUI セッションのリモート画面 (M8-2 表示 + M8-3 入力)。
 *
 * [RemoteDesktopClient] が更新する Bitmap を、アスペクト比を保ったまま
 * 中央にフィット表示する。再描画は `desktopClient.redraw` の collect で発火（端末 TerminalRenderer と同方式）。
 * CONNECTED 後は [GuiInputView] を上に重ねてタッチ/キー入力を接続先へ送る（座標フィット計算は
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
    val tick by session.desktopClient.redraw.collectAsState()
    val vrev by session.viewport.rev.collectAsState()
    val crev by session.cursor.rev.collectAsState()
    val clipboardFiles by session.clipboardFiles.collectAsState()

    // 長押し右クリックの輪。押している間だけフレームごとに現在時刻を更新して弧を伸ばす
    // (0.8.431)。押していない間 (holdStart == 0) はループを回さないので、通常の描画負荷は
    // 変わらない。
    val holdStart by session.cursor.holdStart.collectAsState()
    var holdNow by remember { mutableLongStateOf(0L) }
    LaunchedEffect(holdStart) {
        if (holdStart == 0L) return@LaunchedEffect
        while (true) {
            withFrameMillis { holdNow = SystemClock.uptimeMillis() }
        }
    }

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
            @Suppress("UNUSED_EXPRESSION") run { tick; vrev; crev } // FB / 表示変換 / カーソル変更で再描画
            val bmp = session.desktopClient.frame ?: return@Canvas
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
                synchronized(session.desktopClient.frameLock) {
                    canvas.nativeCanvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), null)
                }
            }

            // 画面に出るポインタはこの 1 個だけ。RFB クライアントがカーソル擬似エンコーディングを
            // 要求しているので、対応するサーバは framebuffer へ焼き込まない (0.8.431。要求する
            // 前は焼き込まれた相手のポインタとこの矢印が並んで 2 個に見えていた)。要求を
            // 無視するサーバでも位置が分かるよう、こちらの矢印は常に描く。形は
            // GuiCursor.Visual 経由で差し替え可能にしてある。
            val cursor = session.cursor.snapshot()
            if (cursor.initialized) {
                val cx = left + cursor.x * eff
                val cy = top + cursor.y * eff
                when (cursor.visual) {
                    GuiCursor.Visual.Arrow -> drawCursorArrow(cx, cy, cursor.pressed, cursor.mode)
                }
                if (holdStart != 0L) {
                    // holdStart は「輪を出し始めた時刻」なので、尺は遅らせたぶんを引いた残り。
                    val elapsed = (holdNow - holdStart).toFloat()
                    drawHoldRing(cx, cy, (elapsed / GuiCursor.HOLD_RING_MS).coerceIn(0f, 1f))
                }
            }
        }

        if (state == GuiSession.State.CONNECTED) {
            // 透明オーバーレイ: タッチ/キー → リモート入力。OS IME 表示はキーボードモードに追従。
            AndroidView(
                factory = { ctx ->
                    GuiInputView(ctx).also {
                        it.desktopClient = session.desktopClient
                        it.viewport = session.viewport
                        it.cursor = session.cursor
                        it.ctrlSticky = ctrlSticky
                        it.onCtrlConsumed = onCtrlConsumed
                    }
                },
                update = {
                    it.desktopClient = session.desktopClient
                    it.viewport = session.viewport
                    it.cursor = session.cursor
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

        clipboardFiles?.let { offer ->
            ClipboardFileBar(
                offer = offer,
                onReceive = session::receiveClipboardFiles,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * 相手がコピーしたファイルの受け取り口 (0.8.483)。
 *
 * ⭐ **コピーしただけでは何も落ちてこない。** 相手側のコピーは相手の中で完結することも多く、
 * そのたびに端末へ保存していたら通信も置き場も浪費する。⇒ ここに何が来ているかだけ出し、
 * **押されたときに初めて中身を取り寄せる**。⚠ 無視すればそのまま消えるので、操作を増やさない。
 */
@Composable
private fun ClipboardFileBar(
    offer: GuiSession.ClipboardFileOffer,
    onReceive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = offer.entries.firstOrNull()?.name.orEmpty()
    val label = when {
        offer.entries.size > 1 -> "$first ほか${offer.entries.size - 1}件"
        else -> first
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .padding(12.dp)
            .background(Color(0xE6111827), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (offer.receiving) "📎 受け取っています…" else "📎 $label",
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        if (!offer.receiving) {
            Text(
                text = "受け取る",
                color = Color(0xFF22C55E),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onReceive),
            )
        }
    }
}

/** Canvas Path だけで描く既定カーソル。第三者画像アセットは使わない。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCursorArrow(
    x: Float,
    y: Float,
    pressed: Boolean,
    mode: GuiCursor.Mode,
) {
    val u = density
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x, y + 21f * u)
        lineTo(x + 5.5f * u, y + 15.5f * u)
        lineTo(x + 10f * u, y + 25f * u)
        lineTo(x + 14f * u, y + 23f * u)
        lineTo(x + 9.5f * u, y + 14f * u)
        lineTo(x + 17f * u, y + 14f * u)
        close()
    }
    drawPath(path, color = if (pressed) Color(0xFF22C55E) else Color.White)
    drawPath(path, color = Color.Black, style = Stroke(width = 1.5f * u))
    if (mode == GuiCursor.Mode.ABSOLUTE) {
        // 絶対座標モードは矢印の根元に緑の輪を出し、設定を開かなくても状態を判別できる。
        drawCircle(
            color = Color(0xFF22C55E),
            radius = 4f * u,
            center = Offset(x, y),
            style = Stroke(width = 1.5f * u),
        )
    }
}

/**
 * 長押し右クリックの進み具合 (0.8.431)。矢印の先端 = クリックされる点を中心に、
 * [GuiCursor.HOLD_RING_MS] で 1 周する緑の弧を描く。
 *
 * ⚠ **大きさは画面の密度で決める（表示倍率に掛けない）。** 縮小表示のときに輪まで小さくなると、
 * 指の下に隠れて「押せているのか」が分からなくなる。薄い白の輪を下に敷くのは、明るい壁紙の上でも
 * 弧の始点と終点が読めるようにするため。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHoldRing(
    x: Float,
    y: Float,
    progress: Float,
) {
    val u = density
    val r = 13f * u
    val w = 2.5f * u
    drawCircle(
        color = Color(0x66FFFFFF),
        radius = r,
        center = Offset(x, y),
        style = Stroke(width = w),
    )
    drawArc(
        color = Color(0xFF22C55E),
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = Offset(x - r, y - r),
        size = Size(r * 2f, r * 2f),
        style = Stroke(width = w, cap = StrokeCap.Round),
    )
}
