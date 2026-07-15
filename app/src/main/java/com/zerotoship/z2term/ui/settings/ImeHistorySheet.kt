package com.zerotoship.z2term.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.terminal.keyboard.ImeHistoryStore
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * IME 学習履歴の管理シート。設定シートの「日本語 IME 学習履歴」セクションから開く。
 *
 *  - スナップショットを score 降順で一覧表示 (読み → 単語、利用回数、最終利用日)。
 *  - 各行に「削除」ボタン (タップで 1 件削除)。
 *  - 上部に「すべて消去」ボタン (確認ダイアログを経由)。
 *  - [ImeHistoryStore.versionFlow] を購読し、別経路で履歴が変化した場合も自動再描画。
 *
 * 確定済みの読み/単語以外の情報 (日付、ヒット回数) を見せることで、誤確定を見つけて削除できる
 * 「ノイズ取り」が現実的にできるようにする。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImeHistorySheet(onDismiss: () -> Unit) {
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

    // 履歴は IO スレッドからスナップショットを取って State にロードする。
    // versionFlow を購読し、変化したら都度再フェッチ → 削除/全消去にもリアルタイム追従。
    var items by remember { mutableStateOf<List<ImeHistoryStore.HistoryItem>>(emptyList()) }
    val version by ImeHistoryStore.versionFlow.collectAsState()
    LaunchedEffect(version) {
        ImeHistoryStore.ensureLoaded(context)
        items = ImeHistoryStore.snapshot()
    }

    var confirmClearOpen by remember { mutableStateOf(false) }

    // 検索: 読み / 単語に対する部分一致 (大文字小文字無視) で絞り込む。
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) items
        else items.filter {
            it.reading.contains(q, ignoreCase = true) || it.word.contains(q, ignoreCase = true)
        }
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
                    text = stringResource(R.string.ime_history_title),
                    color = ZtsGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (query.isBlank())
                        stringResource(R.string.settings_ime_history_count, items.size)
                    else
                        stringResource(R.string.ime_history_count_filtered, filtered.size, items.size),
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Box(Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    SheetPillButton(
                        label = stringResource(R.string.action_clear),
                        danger = true
                    ) { confirmClearOpen = true }
                }
                SheetPillButton(label = stringResource(R.string.action_cancel)) { closeSheet() }
            }

            // 検索ボックス (読み / 単語で絞り込み)。履歴が無いときは出さない。
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
                            text = stringResource(R.string.ime_history_search_hint),
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
                    text = stringResource(R.string.ime_history_empty),
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
                // 件数が多いと縦に長くなるので LazyColumn + heightIn で上限を設ける。
                // 画面下端のキーボード安全領域 (ModalBottomSheet 側で処理済み) には触れない。
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = filtered,
                        key = { item -> "${item.reading} ${item.word}" }
                    ) { item ->
                        HistoryRow(
                            item = item,
                            onDelete = {
                                scope.launch {
                                    ImeHistoryStore.deleteEntry(item.reading, item.word)
                                }
                            }
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
                    text = stringResource(R.string.ime_history_title),
                    color = ZtsGreen,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.ime_history_clear_confirm),
                    color = ZtsTextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearOpen = false
                    scope.launch { ImeHistoryStore.clearAll() }
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
private fun HistoryRow(
    item: ImeHistoryStore.HistoryItem,
    onDelete: () -> Unit
) {
    val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.word,
                    color = ZtsTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = stringResource(R.string.ime_history_count_badge, item.count),
                    color = ZtsGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "${item.reading}    ${df.format(Date(item.lastUsedAt))}",
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        SheetPillButton(
            label = stringResource(R.string.ssh_action_delete),
            danger = true,
            onClick = onDelete
        )
    }
}

/** 小さな丸ボタン (このシート専用)。CustomThemeSheet の PillButton と同等の見た目。 */
@Composable
private fun SheetPillButton(
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

