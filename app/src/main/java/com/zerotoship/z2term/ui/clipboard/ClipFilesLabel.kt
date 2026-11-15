package com.zerotoship.z2term.ui.clipboard

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.clipboard.ClipboardHistoryStore
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 履歴の**画像・ファイルの行**の中身: 左に小さな絵、右に名前と大きさ。
 *
 * 履歴シートとキーボードの 📋 パッドの両方が使う。⚠ 行の見せ方を 2 か所で作り分けない
 * (同じ履歴が入口によって別物に見える)。違うのは文字と絵の大きさだけで、呼ぶ側が渡す。
 *
 * 画像は絵そのものを出す — 名前は `image.png` のように区別の付かないものが多く、
 * 文字だけではどれを貼るのか選べない。画像以外は拡張子を出す。
 */
@Composable
internal fun ClipFilesLabel(
    entry: ClipboardHistoryStore.ClipEntry,
    fontSize: TextUnit,
    thumb: Dp,
    modifier: Modifier = Modifier,
) {
    val first = entry.files.firstOrNull() ?: return
    val context = LocalContext.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(first, thumb, fontSize)
        Column {
            Text(
                // 機微な行 (30 秒で消える) は文字の行と同じく 🔒 を付ける。
                text = (if (entry.sensitive) "🔒 " else "") + first.name,
                color = ZtsTextPrimary,
                fontSize = fontSize,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val size = Formatter.formatShortFileSize(context, entry.files.sumOf { it.size })
            Text(
                // 何個かまとめてコピーしたものは、残りの数を足す (大きさは合計)。
                text = if (entry.files.size > 1) "+${entry.files.size - 1} · $size" else size,
                color = ZtsTextSecondary,
                fontSize = fontSize * 0.8f,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Thumb(file: ClipboardHistoryStore.ClipFile, size: Dp, fontSize: TextUnit) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.path, px) {
        value = if (!file.isImage) null else withContext(Dispatchers.IO) {
            decodeThumb(ClipboardHistoryStore.fileOf(context, file), px)
        }
    }
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(ZtsBgSecondary)
            .border(1.dp, ZtsBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = file.name.substringAfterLast('.', "").take(4).uppercase().ifEmpty { "…" },
                color = ZtsTextSecondary,
                fontSize = fontSize * 0.7f,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

/** [target] px 四方に足りる大きさまで縮めて読む。画像として読めなければ null。 */
private fun decodeThumb(file: File, target: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
