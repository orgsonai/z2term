package com.zerotoship.z2term.qr

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.components.ConfirmDialog
import com.zerotoship.z2term.ui.settings.PillButton
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import java.text.DateFormat
import java.util.Date

/**
 * QR ツールの履歴 (0.8.603)。行はコマンド一覧の自動化ルールの行と同じ形 (地・1dp 枠・角丸 8dp・等幅)。
 *
 * タップで内容欄へ表示し直し、長押しのメニューでピン留め・削除する (利用者の指定)。「すべて消す」は
 * ピン留めを残すので、消える範囲を確認してから消す。履歴が空のあいだは見出しごと出さない。
 * 親の Column に直接並べる (間隔は親の spacedBy に任せる)。
 */
@Composable
internal fun QrHistorySection(
    entries: List<QrHistoryEntry>,
    enabled: Boolean,
    onOpen: (QrHistoryEntry) -> Unit,
    onPin: (QrHistoryEntry) -> Unit,
    onDelete: (QrHistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.qr_history_title), color = ZtsTextSecondary, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        if (entries.any { !it.pinned }) {
            PillButton(label = stringResource(R.string.qr_history_clear), enabled = enabled) { confirmClear = true }
        }
    }
    entries.forEach { entry ->
        key(entry.text) { QrHistoryRow(entry, enabled, onOpen, onPin, onDelete) }
    }
    ToolNote(stringResource(R.string.qr_history_note))
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.qr_history_clear_title),
            message = stringResource(R.string.qr_history_clear_message),
            confirmLabel = stringResource(R.string.qr_history_clear),
            confirmColor = ZtsError,
            onConfirm = { confirmClear = false; onClear() },
            onCancel = { confirmClear = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QrHistoryRow(
    entry: QrHistoryEntry,
    enabled: Boolean,
    onOpen: (QrHistoryEntry) -> Unit,
    onPin: (QrHistoryEntry) -> Unit,
    onDelete: (QrHistoryEntry) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val time = remember(entry.time) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.time))
    }
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
                .combinedClickable(
                    enabled = enabled,
                    onLongClickLabel = stringResource(R.string.qr_history_menu),
                    onLongClick = { menu = true },
                    onClick = { onOpen(entry) },
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(entry.text, color = ZtsTextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            // ピン留めは枠や印を足さず、時刻の行に言葉で出す。
            Text(if (entry.pinned) stringResource(R.string.qr_history_pinned) + "  " + time else time,
                color = if (entry.pinned) ZtsGreen else ZtsTextSecondary, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { MenuText(stringResource(if (entry.pinned) R.string.qr_history_unpin else R.string.qr_history_pin)) },
                onClick = { menu = false; onPin(entry) },
            )
            DropdownMenuItem(
                text = { MenuText(stringResource(R.string.qr_history_delete), ZtsError) },
                onClick = { menu = false; onDelete(entry) },
            )
        }
    }
}

@Composable
private fun MenuText(text: String, color: Color = ZtsTextPrimary) {
    Text(text, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
}
