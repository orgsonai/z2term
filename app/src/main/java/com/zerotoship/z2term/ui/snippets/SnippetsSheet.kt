package com.zerotoship.z2term.ui.snippets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.snippets.Snippet
import com.zerotoship.z2term.snippets.SnippetStore
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * よく使うコマンド (スニペット) 管理シート。
 *
 * - リスト表示: 各行をタップすると [onRun] で command 文字列をターミナルへ挿入する
 *   (Enter は付けない、ユーザーが必要なら手動で確定)。
 * - 「編集」: 編集モードに切替えて name / command を編集、削除も可能。
 * - 「+ 新規」: 空エントリで編集モードに入る。
 *
 * 永続化は [SnippetStore] (DataStore Preferences + JSON 直列化)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsSheet(
    onDismiss: () -> Unit,
    onRun: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var forceClose by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // スクロール途中の下スワイプで誤って閉じないよう、最上部のときだけスワイプ閉じを許可。
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden) forceClose || scrollState.value == 0 else true
        }
    )
    val closeSheet: () -> Unit = {
        forceClose = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    val store = remember { SnippetStore(context.applicationContext) }
    val snippetsFlow = remember(store) {
        store.snippets.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }
    val snippets by snippetsFlow.collectAsState()
    var editing by remember { mutableStateOf<Snippet?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZtsBgPrimary,
        contentColor = ZtsTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        contentWindowInsets = { WindowInsets.statusBars },
        dragHandle = { Z2TermDragHandle(onClose = closeSheet) }
    ) {
        BackHandler(onBack = closeSheet)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val currentEdit = editing
            if (currentEdit == null) {
                ListHeader(
                    onNew = { editing = newSnippet() },
                    onLoadPreset = { tier ->
                        scope.launch { presetFor(tier).forEach { store.upsert(it) } }
                    }
                )
                if (snippets.isEmpty()) {
                    EmptyState()
                } else {
                    snippets.forEachIndexed { idx, s ->
                        SnippetRow(
                            snippet = s,
                            isFirst = idx == 0,
                            isLast = idx == snippets.lastIndex,
                            onRun = {
                                onRun(s.command)
                                onDismiss()
                            },
                            onEdit = { editing = s },
                            onDelete = {
                                scope.launch { store.delete(s.id) }
                            },
                            onMoveUp = {
                                scope.launch { store.reorder(idx, idx - 1) }
                            },
                            onMoveDown = {
                                scope.launch { store.reorder(idx, idx + 1) }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                HintBlock()
            } else {
                EditForm(
                    initial = currentEdit,
                    onSave = { saved ->
                        scope.launch {
                            store.upsert(saved)
                            editing = null
                        }
                    },
                    onCancel = { editing = null }
                )
            }
        }
    }
}

private fun newSnippet() = Snippet(
    id = UUID.randomUUID().toString(),
    label = "",
    command = ""
)

/** プリセット規模 */
private enum class PresetTier { SMALL, MEDIUM, LARGE }

// 各段は (ラベル, コマンド)。PRoot 制約で動かないもの (ip / ping / nmap -sS) は避け、
// 動く代替 (nmap -sT / ss) を採用。id は "preset:<command>" で安定化し、
// 再投入しても重複しない (upsert される)。
private val PRESET_SMALL = listOf(
    "一覧 (詳細)" to "ls -la --color=auto",
    "ディスク使用量" to "df -h",
    "メモリ" to "free -h",
    "プロセス" to "ps aux",
    "OS 情報" to "cat /etc/os-release",
    "パッケージ更新" to "apk update && apk upgrade",
    "パッケージ追加" to "apk add "
)
private val PRESET_MEDIUM_ADD = listOf(
    "巨大ファイル探索" to "du -sh * | sort -h",
    "再帰 grep" to "grep -rn '' .",
    "ファイル検索" to "find . -name ",
    "圧縮 (tar.gz)" to "tar czf out.tgz ",
    "展開 (tar)" to "tar xf ",
    "ダウンロード" to "curl -LO ",
    "開放ポート" to "ss -tlnp",
    "TCP スキャン (SYN 不使用)" to "nmap -sT ",
    "SSH(dropbear) 起動" to "sshd"
)
private val PRESET_LARGE_ADD = listOf(
    "git 状態" to "git status",
    "git ログ (グラフ)" to "git log --oneline --graph --all",
    "git クローン" to "git clone ",
    "tmux 開始/復帰" to "tmux a || tmux new -s main",
    "htop" to "htop",
    "jq 整形" to "jq '.' ",
    "rsync 同期" to "rsync -av src/ dst/",
    "監視実行" to "watch -n1 "
)

private fun presetFor(tier: PresetTier): List<Snippet> {
    val pairs = when (tier) {
        PresetTier.SMALL -> PRESET_SMALL
        PresetTier.MEDIUM -> PRESET_SMALL + PRESET_MEDIUM_ADD
        PresetTier.LARGE -> PRESET_SMALL + PRESET_MEDIUM_ADD + PRESET_LARGE_ADD
    }
    return pairs.map { (label, cmd) -> Snippet(id = "preset:$cmd", label = label, command = cmd) }
}

@Composable
private fun ListHeader(onNew: () -> Unit, onLoadPreset: (PresetTier) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "スニペット",
                color = ZtsGreen,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Box(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ZtsGreen.copy(alpha = 0.18f))
                    .border(1.dp, ZtsGreen, RoundedCornerShape(8.dp))
                    .clickable(onClick = onNew)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "+ 新規",
                    color = ZtsGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "おすすめ投入:",
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            PresetChip("小 (7)") { onLoadPreset(PresetTier.SMALL) }
            Spacer(modifier = Modifier.width(6.dp))
            PresetChip("中 (16)") { onLoadPreset(PresetTier.MEDIUM) }
            Spacer(modifier = Modifier.width(6.dp))
            PresetChip("大 (24)") { onLoadPreset(PresetTier.LARGE) }
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = ZtsTextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "未登録。「おすすめ投入」の 小/中/大 で定番を一括追加、または「+ 新規」で個別追加。",
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HintBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(
            text = "▸ 行をタップ → ターミナルに挿入 (Enter は付けない)\n" +
                "▸ ↑↓ で並べ替え / ✎ 編集 / ✕ 削除\n" +
                "▸ 小⊂中⊂大。再投入しても重複しません",
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * スニペット 1 行。
 *  - 左側のテキスト領域をタップ → 挿入 (onRun)。コマンドそのものを主表示する
 *    (名前だけだと分かりにくいため)。ラベルがあれば上に小さく薄く添える。
 *  - 右側に ↑ ↓ (並べ替え) / ✎ (編集) / ✕ (削除) の小アイコン。
 */
@Composable
private fun SnippetRow(
    snippet: Snippet,
    isFirst: Boolean,
    isLast: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // テキスト領域 = 挿入ボタン (行タップで挿入)。コマンドを主表示。
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRun)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (snippet.label.isNotBlank()) {
                Text(
                    text = snippet.label,
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            Text(
                text = snippet.command.ifBlank { "(空)" },
                color = ZtsTextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
        IconCell(label = "↑", enabled = !isFirst, onClick = onMoveUp)
        IconCell(label = "↓", enabled = !isLast, onClick = onMoveDown)
        IconCell(label = "✎", onClick = onEdit)
        IconCell(label = "✕", danger = true, onClick = onDelete)
    }
}

@Composable
private fun IconCell(
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val fg = when {
        !enabled -> ZtsTextSecondary.copy(alpha = 0.3f)
        danger -> ZtsError
        else -> ZtsTextSecondary
    }
    Box(
        modifier = Modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EditForm(
    initial: Snippet,
    onSave: (Snippet) -> Unit,
    onCancel: () -> Unit
) {
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var command by remember(initial.id) { mutableStateOf(initial.command) }

    Text(
        text = if (initial.label.isEmpty() && initial.command.isEmpty()) "新規スニペット" else "編集",
        color = ZtsGreen,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace
    )

    Field(
        label = "ラベル (空なら command 先頭が表示)",
        value = label,
        onChange = { label = it },
        placeholder = "ls (詳細)"
    )
    Field(
        label = "コマンド",
        value = command,
        onChange = { command = it },
        placeholder = "ls -la --color=auto",
        multiline = true
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallButton(label = "キャンセル", onClick = onCancel)
        Box(modifier = Modifier.weight(1f))
        SmallButton(
            label = "保存",
            accent = true,
            onClick = {
                if (command.isNotBlank()) {
                    onSave(initial.copy(label = label.trim(), command = command))
                }
            }
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    multiline: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = ZtsTextSecondary.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (multiline) 6 else 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = !multiline,
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
}

@Composable
private fun SmallButton(
    label: String,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val border = when {
        danger -> ZtsError
        accent -> ZtsGreen
        else -> ZtsBorder
    }
    val fg = when {
        danger -> ZtsError
        accent -> ZtsGreen
        else -> ZtsTextPrimary
    }
    val bg = when {
        accent -> ZtsGreen.copy(alpha = 0.18f)
        else -> ZtsBgSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
