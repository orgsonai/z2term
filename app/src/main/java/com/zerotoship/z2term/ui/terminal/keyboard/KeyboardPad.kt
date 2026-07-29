package com.zerotoship.z2term.ui.terminal.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/** キーボード中央に出すパッドの種類。[NONE] = かなキーがそのまま出ている通常状態。 */
internal enum class PadMode { NONE, EMOJI, CLIPBOARD }

/**
 * 日本語キーボードの**中央 3 列を差し替える**パッド (絵文字 / 貼り付け)。
 *
 * ⚠ **キーを増やさない**ための作り。絵文字も貼り付けも「置く隙間が無い」ので、`あ` で
 * かな面へ、`?#` で記号面へ切り替えるのと同じ **面の差し替え**として実装する。両端の列
 * (⌫ ⏎ ␣ …) は残るので、貼った直後に消す・改行するといった操作はそのままできる。
 *
 * 絵文字と貼り付けを**同じパッドの 2 タブ**にしているのは、入口が別々 (😀 キー / ESC の
 * 上フリック) だから — 片方に入れれば、もう片方は見えるタブから辿れる。
 */
@Composable
internal fun KeyboardPad(
    mode: PadMode,
    onMode: (PadMode) -> Unit,
    style: KeyboardStyle,
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mode == PadMode.NONE) return
    val context = LocalContext.current
    // ⚠ クリップボードは**パッドを開いたときにしか読めない** (Android 10 以降、裏で見張れない)。
    // 開くたびに 1 件取り込む = 利用者から見ると「コピーしてからキーボードを開くと入る」。
    LaunchedEffect(mode) {
        when (mode) {
            PadMode.CLIPBOARD -> {
                ClipboardHistoryStore.ensureLoaded(context)
                ClipboardHistoryStore.capture(context)
            }
            PadMode.EMOJI -> RecentEmojiStore.ensureLoaded(context)
            PadMode.NONE -> Unit
        }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgSecondary)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PadTab("😀", selected = mode == PadMode.EMOJI, style = style) { onMode(PadMode.EMOJI) }
            PadTab("📋", selected = mode == PadMode.CLIPBOARD, style = style) { onMode(PadMode.CLIPBOARD) }
            Box(Modifier.weight(1f))
            if (mode == PadMode.CLIPBOARD) {
                PadTab("🗑", selected = false, style = style) { ClipboardHistoryStore.clearAll() }
            }
        }
        when (mode) {
            PadMode.EMOJI -> EmojiPane(style = style, onInsert = onInsert)
            PadMode.CLIPBOARD -> ClipboardPane(style = style, onInsert = onInsert)
            PadMode.NONE -> Unit
        }
    }
}

/** パッド上部のタブ (絵文字 / 貼り付け / 全消去)。 */
@Composable
private fun PadTab(label: String, selected: Boolean, style: KeyboardStyle, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) ZtsGreen else ZtsBgCard)
            .border(1.dp, if (selected) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else ZtsTextPrimary,
            fontSize = (style.keyFontSp * 0.85f).sp
        )
    }
}

/** 絵文字パッド: カテゴリ (横スクロール) + グリッド (縦スクロール)。 */
@Composable
private fun ColumnScope.EmojiPane(style: KeyboardStyle, onInsert: (String) -> Unit) {
    val recent by RecentEmojiStore.items.collectAsState()
    val categories = remember { EmojiCatalog.categories() }
    // 0 = 最近使った順。⚠ 先頭を最近にするのは、実際に使う絵文字が 20 字ほどに偏るため。
    var tab by remember { mutableIntStateOf(0) }
    val items = if (tab == 0) recent else categories.getOrNull(tab - 1)?.items.orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        PadTab("🕘", selected = tab == 0, style = style) { tab = 0 }
        categories.forEachIndexed { i, c ->
            PadTab(c.label, selected = tab == i + 1, style = style) { tab = i + 1 }
        }
    }
    if (items.isEmpty()) {
        PadEmptyText(stringResource(R.string.pad_emoji_empty), style, Modifier.weight(1f))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 40.dp),
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 4.dp)
    ) {
        items(items.size, key = { items[it] }) { i ->
            val e = items[i]
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable {
                        RecentEmojiStore.record(e)
                        onInsert(e)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = e, fontSize = (style.mainKeyFontSp * 0.95f).sp)
            }
        }
    }
}

/** 貼り付けパッド: 新しい順のリスト。タップで貼り付け、✕ で 1 件削除。 */
@Composable
private fun ColumnScope.ClipboardPane(style: KeyboardStyle, onInsert: (String) -> Unit) {
    val items by ClipboardHistoryStore.items.collectAsState()
    if (items.isEmpty()) {
        PadEmptyText(stringResource(R.string.pad_clip_empty), style, Modifier.weight(1f))
        return
    }
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = PaddingValues(bottom = 4.dp)
    ) {
        items(items, key = { it.text }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(ZtsBgCard)
                    .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.text,
                    color = ZtsTextPrimary,
                    fontSize = (style.keyFontSp * 0.8f).sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onInsert(item.text) }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { ClipboardHistoryStore.remove(item.text) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = ZtsTextSecondary,
                        fontSize = (style.keyFontSp * 0.8f).sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** 中身が無いときの案内 (何をすれば入るのかを書く)。 */
@Composable
private fun PadEmptyText(text: String, style: KeyboardStyle, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = ZtsTextSecondary,
            fontSize = (style.keyFontSp * 0.7f).sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
