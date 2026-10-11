package com.zerotoship.z2term.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.terminal.keyboard.CustomKaomojiStore
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.launch

/** 畳んだ行に見せる行数。複数行の AA は最大 40 行あるので、そのまま並べると 1 件で画面が埋まる。 */
private const val COLLAPSED_LINES = 6

/**
 * 自分で足した顔文字・AA の一覧。設定の「顔文字・AA の追加」から開く。
 *
 *  - 足した順 (新しいものが先頭) に並べ、各行の「削除」で 1 件消す。
 *  - 上部の「クリア」で全部消す (確認ダイアログを経由)。
 *  - 長い AA は [COLLAPSED_LINES] 行で畳み、行をタップすると全体が開く。
 *
 * ⚠ **設定画面には並べない。** 数が増えると設定の他の項目が下へ押し出されるので、
 * 設定側は入力欄と件数だけにしてある。作りは [ImeHistorySheet] にそろえた。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomKaomojiSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var forceClose by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            // 上端スクロールしていれば閉じる動作を許可 (シート閉じと一覧スクロールの衝突回避)
            if (target == SheetValue.Hidden)
                forceClose || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)
            else true
        }
    )
    val closeSheet: () -> Unit = {
        forceClose = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val items by CustomKaomojiStore.items.collectAsState()
    LaunchedEffect(Unit) { CustomKaomojiStore.ensureLoaded(context) }

    var confirmClearOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) items else items.filter { it.contains(q, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZtsBgPrimary,
        contentColor = ZtsTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        contentWindowInsets = { WindowInsets.systemBars },
        dragHandle = { Z2TermDragHandle(onClose = closeSheet) }
    ) {
        BackHandler(onBack = closeSheet)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // タイトル + 件数 + 全消去
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.kaomoji_custom_title),
                    color = ZtsGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = stringResource(
                        R.string.settings_kaomoji_custom_count,
                        if (query.isBlank()) items.size else filtered.size,
                        if (query.isBlank()) CustomKaomojiStore.MAX_ENTRIES else items.size,
                    ),
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Box(Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    KaomojiPillButton(
                        label = stringResource(R.string.action_clear),
                        danger = true
                    ) { confirmClearOpen = true }
                }
                KaomojiPillButton(label = stringResource(R.string.action_cancel)) { closeSheet() }
            }

            // 検索ボックス。1 件も無いときは出さない。
            if (items.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ZtsBgCard)
                        .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.kaomoji_custom_search_hint),
                            color = ZtsTextSecondary.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = ZtsTextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(ZtsGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.kaomoji_custom_empty),
                    color = ZtsTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.ime_history_search_empty),
                    color = ZtsTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = filtered, key = { it }) { text ->
                        KaomojiRow(
                            text = text,
                            onDelete = { scope.launch { CustomKaomojiStore.remove(context, text) } }
                        )
                    }
                }
            }
        }
    }

    if (confirmClearOpen) {
        AlertDialog(
            onDismissRequest = { confirmClearOpen = false },
            title = {
                Text(
                    text = stringResource(R.string.kaomoji_custom_title),
                    color = ZtsGreen,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.kaomoji_custom_clear_confirm),
                    color = ZtsTextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearOpen = false
                    scope.launch { CustomKaomojiStore.clear(context) }
                }) {
                    Text(
                        text = stringResource(R.string.action_clear),
                        color = ZtsError,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearOpen = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = ZtsTextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            containerColor = ZtsBgSecondary
        )
    }
}

@Composable
private fun KaomojiRow(text: String, onDelete: () -> Unit) {
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val foldable = lineCount > COLLAPSED_LINES
    var expanded by remember(text) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(enabled = foldable) { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 複数行は行がそろうよう等幅で、折り返さずに横へ流す。
            Text(
                text = text,
                color = ZtsTextPrimary,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
            if (foldable && !expanded) {
                Text(
                    text = "… +${lineCount - COLLAPSED_LINES}",
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        KaomojiPillButton(
            label = stringResource(R.string.ssh_action_delete),
            danger = true,
            onClick = onDelete
        )
    }
}

/** 小さな丸ボタン (このシート専用)。[ImeHistorySheet] のものと同じ見た目。 */
@Composable
private fun KaomojiPillButton(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val border = if (danger) ZtsError else ZtsBorder
    val fg = if (danger) ZtsError else ZtsTextPrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
