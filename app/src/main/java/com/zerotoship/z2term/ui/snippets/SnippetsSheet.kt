package com.zerotoship.z2term.ui.snippets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.channel.RemoteService
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.snippets.Snippet
import com.zerotoship.z2term.snippets.SnippetGroup
import com.zerotoship.z2term.snippets.SnippetStore
import com.zerotoship.z2term.ui.components.REORDER_SETTLE_MS
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.settings.ServersBody
import com.zerotoship.z2term.ui.settings.WhenRulesBody
import com.zerotoship.z2term.ui.ssh.SshProfilesBody
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** ツールシートのタブ。スニペット / SSH・SFTP / 常駐サーバーを 1 枚にまとめる。 */
private enum class ToolsTab { SNIPPETS, HISTORY, SSH, SERVERS, WHEN }

/**
 * ツールシート (ツールバーの 📜 から開く)。
 *
 * 上部のタブで「スニペット」「SSH / SFTP」「サーバー」を切替える。
 *  - スニペット: よく使うコマンドを挿入 ([onRun])。並べ替え / 編集 / 削除可。
 *  - 履歴 (B2): 端末で実行した過去コマンドを絞り込んでタップで挿入。読み取り専用で、
 *    シェルの履歴ファイル (`~/.bash_history` / `~/.zsh_history`) をそのまま見る。
 *  - 接続先: SSH / SFTP と、その SSH に追加した FTP / SMB / WebDAV / VNC を開く。
 *  - サーバー: 常駐サーバーの起動/停止・ON/OFF・編集 (設定シートと同じ [ServersBody])。
 *    毎回設定画面を開かずここから管理できる。
 *
 * [showSshTab] は呼び出し元が接続先機能を提供できない特殊画面だけ false にできる。
 * サーバータブは [serverSession] が渡されたときだけ出す。
 *
 * 永続化はそれぞれ [SnippetStore] / SshProfileStore / AppSettings (DataStore)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsSheet(
    onDismiss: () -> Unit,
    onRun: (String) -> Unit,
    onConnect: (SshProfile) -> Unit = {},
    onSftp: (SshProfile) -> Unit = {},
    onService: (SshProfile, RemoteService) -> Unit = { _, _ -> },
    showSshTab: Boolean = true,
    serverSession: TerminalSession? = null
) {
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
    var tab by remember { mutableStateOf(ToolsTab.SNIPPETS) }
    // タブ切替時はスクロールを先頭へ戻す (前のタブの位置を引き継がない)。
    LaunchedEffect(tab) { scrollState.scrollTo(0) }

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
                // ⚠ 中身の量に関わらず**常に全高**にする。付けないとシートが内容の高さに縮み、
                // 項目の少ないタブでは背が低くなる。すると**タブバーの位置がタブごとに動き**、
                // 切り替えた先で前のタブのタブバーがあった場所を押してしまう (誤タップ)。
                // 高さが変わらなければ、どのタブでもタブバーは同じ場所にある。
                // `fillMaxHeight` ではなく `weight` なのは、上のドラッグハンドルの分を
                // 差し引いた残り全部を取るため (fillMaxHeight だとハンドルの分だけはみ出す)。
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 履歴タブは常に出るので、タブバーは必ず表示する。
            run {
                ToolsTabBar(
                    selected = tab,
                    showSsh = showSshTab,
                    showServers = serverSession != null,
                    onSelect = { tab = it }
                )
            }
            when (tab) {
                ToolsTab.SNIPPETS -> SnippetsBody(onRun = onRun, onDismiss = onDismiss)
                ToolsTab.HISTORY -> HistoryBody(onRun = { cmd -> onRun(cmd); onDismiss() })
                ToolsTab.SSH -> SshProfilesBody(
                    onConnect = { p -> onConnect(p); onDismiss() },
                    onSftp = { p -> onSftp(p); onDismiss() },
                    onService = { p, service -> onService(p, service); onDismiss() }
                )
                ToolsTab.SERVERS -> serverSession?.let { ServersBody(session = it) }
                ToolsTab.WHEN -> WhenRulesBody()
            }
        }
    }
}

/** スニペット / 履歴 / SSH・SFTP / サーバー / 自動化 を切替えるセグメントタブ。出せないタブは省く。 */
@Composable
private fun ToolsTabBar(
    selected: ToolsTab,
    showSsh: Boolean,
    showServers: Boolean,
    onSelect: (ToolsTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabChip(
            label = stringResource(R.string.tools_tab_snippets),
            selected = selected == ToolsTab.SNIPPETS,
            modifier = Modifier.weight(1f),
            onSelect = { onSelect(ToolsTab.SNIPPETS) }
        )
        TabChip(
            label = stringResource(R.string.tools_tab_history),
            selected = selected == ToolsTab.HISTORY,
            modifier = Modifier.weight(1f),
            onSelect = { onSelect(ToolsTab.HISTORY) }
        )
        if (showSsh) {
            TabChip(
                label = stringResource(R.string.tools_tab_ssh),
                selected = selected == ToolsTab.SSH,
                modifier = Modifier.weight(1f),
                onSelect = { onSelect(ToolsTab.SSH) }
            )
        }
        if (showServers) {
            TabChip(
                label = stringResource(R.string.tools_tab_servers),
                selected = selected == ToolsTab.SERVERS,
                modifier = Modifier.weight(1f),
                onSelect = { onSelect(ToolsTab.SERVERS) }
            )
        }
        // 自動化 (z2-when) はセッションに依存しないので、GUI タブからでも常に出す。
        TabChip(
            label = stringResource(R.string.tools_tab_when),
            selected = selected == ToolsTab.WHEN,
            modifier = Modifier.weight(1f),
            onSelect = { onSelect(ToolsTab.WHEN) }
        )
    }
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val bg = if (selected) ZtsGreen.copy(alpha = 0.18f) else ZtsBgCard
    val border = if (selected) ZtsGreen else ZtsBorder
    val fg = if (selected) ZtsGreen else ZtsTextPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            // タブが 3 つ並ぶと幅が窮屈なので 12sp・1 行固定 (溢れは「…」)。
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * スニペット一覧本体 (タブのコンテンツ)。スクロールは呼び出し側 ([SnippetsSheet]) が持つ。
 *
 * - リスト表示: 各行をタップすると [onRun] で command 文字列をターミナルへ挿入する
 *   (Enter は付けない、ユーザーが必要なら手動で確定)。
 * - 「編集」: 編集モードに切替えて name / command を編集、削除も可能。
 * - 「+ 新規」: 空エントリで編集モードに入る。
 */
@Composable
private fun SnippetsBody(
    onRun: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SnippetStore(context.applicationContext) }
    val snippetsFlow = remember(store) {
        store.snippets.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }
    val snippets by snippetsFlow.collectAsState()
    var editing by remember { mutableStateOf<Snippet?>(null) }
    // グループ (0.8.387)。"" = 「すべて」= 絞らない。
    val groupsFlow = remember(store) {
        store.groups.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }
    val groups by groupsFlow.collectAsState()
    var selectedGroup by remember { mutableStateOf("") }
    var groupEditing by remember { mutableStateOf<SnippetGroup?>(null) }
    // 開いていたグループが消えたら「すべて」へ戻す (**空の棚を開いたまま固まらせない**)。
    LaunchedEffect(groups) {
        if (selectedGroup.isNotEmpty() && groups.none { it.id == selectedGroup }) selectedGroup = ""
    }
    // いま出す行。「すべて」なら全部、グループを開いていればその中だけ。
    val visible = remember(snippets, selectedGroup) {
        if (selectedGroup.isEmpty()) snippets else snippets.filter { it.groupId == selectedGroup }
    }

    // 初回だけサンプル (ls -la --color=auto) を投入。
    LaunchedEffect(Unit) { store.ensureSeeded() }

    // ドラッグ並べ替え: 表示順はローカル [order] を真実とし、ドラッグ中は flow 更新で上書きしない。
    // ⚠ 考え方は共通部品の ReorderState と同じ (自動化タブ・常駐サーバータブが使っている方)。
    // こちらは行が固定高なので独自のままだが、直すときは両方を見ること。
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableStateOf(0f) }
    var order by remember { mutableStateOf<List<Snippet>>(emptyList()) }
    // 指を離したあと、半端に残ったズレを 0 まで滑らせている最中の行 (0.8.272)。
    // ここを即 0 にすると、残りぶんだけ行が 1 フレームで飛び「離した瞬間に入れ替わった」ように見える。
    var settlingId by remember { mutableStateOf<String?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // 保存した並び。保存 (DataStore への書き込み → flow で戻ってくる) が反映されるまでは
    // flow 側の古い並びで上書きしない — 取り込むと並びが一度戻ってからまた入れ替わる。
    var pendingIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(visible, draggingId) {
        if (draggingId != null) return@LaunchedEffect
        val p = pendingIds
        if (p != null) {
            val ids = visible.map { it.id }
            when {
                // 顔ぶれが変わった (追加・削除) なら flow 側が正しい。
                ids.size != p.size || ids.toSet() != p.toSet() -> pendingIds = null
                // 並びが追いついた。以後はふつうに取り込む。
                ids == p -> pendingIds = null
                // 同じ顔ぶれで並びだけ違う = まだ保存が反映されていない。自分の並びを保つ。
                else -> return@LaunchedEffect
            }
        }
        order = visible
    }
    // 1 行ぶんのピッチ (行高 + Column の spacedBy 10dp)。これを超えて動かしたら隣と入れ替える。
    val rowPitchPx = with(LocalDensity.current) { (SNIPPET_ROW_HEIGHT + 10.dp).toPx() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val currentEdit = editing
        val currentGroupEdit = groupEditing
        if (currentEdit == null && currentGroupEdit == null) {
            // 新規は**開いているグループへ入れる**。開いてから「+ 新規」を押した人にとっては
            // それが自然で、未分類へ落ちると毎回移し替える羽目になる。
            ListHeader(onNew = { editing = newSnippet(selectedGroup) })
            GroupBar(
                groups = groups,
                selected = selectedGroup,
                onSelect = { selectedGroup = it },
                onEditGroup = { groupEditing = it },
                onAddGroup = { groupEditing = newGroup() }
            )
            if (order.isEmpty()) {
                EmptyState(inGroup = selectedGroup.isNotEmpty())
            } else {
                // key(s.id) でノード identity を固定 → 並べ替え中も掴んだ行に
                // ポインタ(ドラッグ)が追従する (Column でも item が移動できる)。
                order.forEach { s ->
                    key(s.id) {
                        // 着地し終わるまでは掴んでいるときと同じ扱い (前面・指追従) にする。
                        val dragging = s.id == draggingId || s.id == settlingId
                        SnippetRow(
                            snippet = s,
                            dragging = dragging,
                            dragOffsetY = if (dragging) dragDy else 0f,
                            onRun = {
                                onRun(s.command)
                                onDismiss()
                            },
                            onEdit = { editing = s },
                            onDelete = { scope.launch { store.delete(s.id) } },
                            onDragStart = {
                                // 前の行がまだ着地中なら止めて、そこから掴み直す。
                                settleJob?.cancel()
                                settleJob = null
                                settlingId = null
                                draggingId = s.id
                                dragDy = 0f
                            },
                            onDrag = { dy ->
                                dragDy += dy
                                val cur = order.indexOfFirst { it.id == draggingId }
                                if (cur >= 0) {
                                    if (dragDy > rowPitchPx && cur < order.lastIndex) {
                                        order = order.toMutableList()
                                            .also { it.add(cur + 1, it.removeAt(cur)) }
                                        dragDy -= rowPitchPx
                                    } else if (dragDy < -rowPitchPx && cur > 0) {
                                        order = order.toMutableList()
                                            .also { it.add(cur - 1, it.removeAt(cur)) }
                                        dragDy += rowPitchPx
                                    }
                                }
                            },
                            onDragEnd = {
                                val finalOrder = order
                                val id = draggingId
                                draggingId = null
                                // 保存は待たずに投げる (離してすぐ閉じても並びが残るように)。
                                pendingIds = finalOrder.map { it.id }
                                // ⚠ 絞り込み中は replaceAll に渡さない (出ていないグループが
                                // まるごと消える)。見えている行の位置だけ入れ替える。
                                scope.launch { store.replaceVisible(finalOrder) }
                                if (id != null && dragDy != 0f) {
                                    // 半端に残ったズレを 0 まで滑らせる (即 0 にすると行が飛ぶ)。
                                    settlingId = id
                                    settleJob = scope.launch {
                                        animate(
                                            initialValue = dragDy,
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = REORDER_SETTLE_MS)
                                        ) { value, _ -> dragDy = value }
                                        dragDy = 0f
                                        settlingId = null
                                        settleJob = null
                                    }
                                } else {
                                    dragDy = 0f
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            HintBlock()
        } else if (currentEdit != null) {
            EditForm(
                initial = currentEdit,
                groups = groups,
                onSave = { saved ->
                    scope.launch {
                        store.upsert(saved)
                        // 別のグループへ移したなら、移した先を開いて**行方を見せる**。
                        // 保存した途端に一覧から消えると、消えたのか移ったのか分からない。
                        if (selectedGroup.isNotEmpty() && saved.groupId != selectedGroup) {
                            selectedGroup = saved.groupId
                        }
                        editing = null
                    }
                },
                onCancel = { editing = null }
            )
        } else if (currentGroupEdit != null) {
            GroupEditForm(
                initial = currentGroupEdit,
                isNew = groups.none { it.id == currentGroupEdit.id },
                onSave = { saved ->
                    scope.launch {
                        store.upsertGroup(saved)
                        selectedGroup = saved.id
                        groupEditing = null
                    }
                },
                onDelete = {
                    scope.launch {
                        store.deleteGroup(currentGroupEdit.id)
                        selectedGroup = ""
                        groupEditing = null
                    }
                },
                onCancel = { groupEditing = null }
            )
        }
    }
}

private fun newSnippet(groupId: String = "") = Snippet(
    id = UUID.randomUUID().toString(),
    label = "",
    command = "",
    groupId = groupId
)

private fun newGroup() = SnippetGroup(id = UUID.randomUUID().toString(), name = "")

/** スニペット 1 行の固定高さ。ドラッグ並べ替えのピッチ計算に使うため固定にする。 */

/**
 * 履歴タブ (B2)。端末で実行した過去コマンドを絞り込んでタップで挿入する。
 *
 * **読み取り専用**で、入力・描画の経路には一切触らない。中身はシェルの履歴ファイルそのもの
 * ([ShellHistory] が `~/.bash_history` と `~/.zsh_history` をマージする) なので、
 * このアプリ独自の履歴を別に持つことはしない (二重管理を作らない)。
 *
 * タップで入るのは**入力行まで**。スニペットと同じく実行はしない (B1 と揃えた安全側の作法)。
 */
@Composable
private fun HistoryBody(onRun: (String) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    // ファイル読み込みなので画面を止めない。タブを開くたびに読み直す (履歴は増え続けるため)。
    val entries by produceState(initialValue = emptyList<ShellHistory.Entry>()) {
        value = withContext(Dispatchers.IO) { ShellHistory.load(context.applicationContext) }
    }
    val shown = remember(entries, query) { ShellHistory.filter(entries, query) }
    val stamp = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.history_desc),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_search_hint),
                    color = ZtsTextSecondary,
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

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                color = ZtsTextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        } else if (shown.isEmpty()) {
            Text(
                text = stringResource(R.string.history_no_match),
                color = ZtsTextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // ⚠ ここはシート全体の verticalScroll の中なので LazyColumn を入れ子にできない
        // (同じ向きのスクロールを重ねられない)。300 件を一度に組み立てると開くのが重くなるので、
        // **描くのは先頭 [HISTORY_RENDER_LIMIT] 件まで**にして、残りは絞り込みで辿ってもらう。
        shown.take(HISTORY_RENDER_LIMIT).forEach { e ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ZtsBgCard)
                    .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
                    .clickable { onRun(e.command) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = e.command,
                    color = ZtsTextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // zsh 側から来たものだけ時刻を持つ (bash は記録しない)。
                if (e.at > 0) {
                    Text(
                        text = stamp.format(Date(e.at * 1000)),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (shown.size > HISTORY_RENDER_LIMIT) {
            Text(
                text = stringResource(R.string.history_more, shown.size - HISTORY_RENDER_LIMIT),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** 履歴タブで一度に描く行数の上限 (残りは絞り込んで辿る)。 */
private const val HISTORY_RENDER_LIMIT = 50

private val SNIPPET_ROW_HEIGHT = 52.dp

@Composable
private fun ListHeader(onNew: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.snippets_title),
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
                text = stringResource(R.string.snippets_new),
                color = ZtsGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * 1 件も出ていないときの箱。⚠ **「まだ 1 つも無い」と「このグループが空」を書き分ける** —
 * 同じ文面だと、絞り込んだせいで消えたのか元から無いのかが読めない。
 */
@Composable
private fun EmptyState(inGroup: Boolean) {
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
            text = stringResource(
                if (inGroup) R.string.snippets_group_empty else R.string.snippets_empty
            ),
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
            text = stringResource(R.string.snippets_hint) + "\n" +
                stringResource(R.string.snippets_group_hint),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * グループの帯 (0.8.387)。`[すべて] [日常] [git] [+ グループ]` を横に並べる。
 *
 * **なぜページではなく棚か**: 増えた順に区切るページだと、**どこに何があるかが増減のたびに
 * 変わる**。自分で名前を付けた棚なら、中身が増えても場所は動かない
 * (利用者: 「日常系のスニペットとかgit管理系とか」)。
 *
 * ⚠ **開いているグループのチップにだけ ✎ を出す**。名前の変更と削除の入口はここしかないので、
 * 長押しのような隠し操作にはしない (見えないものは無いのと同じ)。
 */
@Composable
private fun GroupBar(
    groups: List<SnippetGroup>,
    selected: String,
    onSelect: (String) -> Unit,
    onEditGroup: (SnippetGroup) -> Unit,
    onAddGroup: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        GroupChip(
            label = stringResource(R.string.snippets_group_all),
            selected = selected.isEmpty(),
            onClick = { onSelect("") }
        )
        groups.forEach { g ->
            val open = g.id == selected
            GroupChip(
                label = if (open) g.name + " ✎" else g.name,
                selected = open,
                // 開いているものをもう一度押す = そのグループの編集。閉じているものは開くだけ。
                onClick = { if (open) onEditGroup(g) else onSelect(g.id) }
            )
        }
        GroupChip(
            label = stringResource(R.string.snippets_group_add),
            selected = false,
            onClick = onAddGroup
        )
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) ZtsGreen else ZtsBorder
    val bg = if (selected) ZtsGreen.copy(alpha = 0.18f) else ZtsBgCard
    val fg = if (selected) ZtsGreen else ZtsTextPrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label.ifBlank { stringResource(R.string.snippets_group_none) },
            color = fg,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * グループを作る / 名前を直す / 消す (0.8.387)。
 *
 * ⚠ **消すのは棚だけで、中身は消えない**ことをその場に書く。書いていないと、削除を押すのが
 * 怖くて棚が増え続ける (そして怖いまま押した人は中身を失ったと思う)。
 */
@Composable
private fun GroupEditForm(
    initial: SnippetGroup,
    isNew: Boolean,
    onSave: (SnippetGroup) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }

    Text(
        text = stringResource(
            if (isNew) R.string.snippets_group_new_title else R.string.snippets_group_edit_title
        ),
        color = ZtsGreen,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace
    )
    Field(
        label = stringResource(R.string.snippets_group_name_field),
        value = name,
        onChange = { name = it },
        placeholder = stringResource(R.string.snippets_group_name_placeholder)
    )
    if (!isNew) {
        Text(
            text = stringResource(R.string.snippets_group_delete_note),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallButton(label = stringResource(R.string.action_cancel), onClick = onCancel)
        if (!isNew) {
            SmallButton(
                label = stringResource(R.string.snippets_group_delete),
                danger = true,
                onClick = onDelete
            )
        }
        Box(modifier = Modifier.weight(1f))
        SmallButton(
            label = stringResource(R.string.action_save),
            accent = true,
            onClick = { if (name.isNotBlank()) onSave(initial.copy(name = name.trim())) }
        )
    }
}

/**
 * スニペット 1 行 (固定高 [SNIPPET_ROW_HEIGHT])。
 *  - 左側のテキスト領域をタップ → 挿入 (onRun)。コマンドを主表示、ラベルは上に薄く添える。
 *  - 右側に ≡ (ドラッグして並べ替え) / ✎ (編集) / ✕ (削除)。
 *  - ドラッグ中は緑枠 + 前面 (zIndex) + 指追従 (translationY) で表示。
 */
@Composable
private fun SnippetRow(
    snippet: Snippet,
    dragging: Boolean,
    dragOffsetY: Float,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SNIPPET_ROW_HEIGHT)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, if (dragging) ZtsGreen else ZtsBorder, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // テキスト領域 = 挿入ボタン (行タップで挿入)。コマンドを主表示。
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onRun)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
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
                text = snippet.command.ifBlank { stringResource(R.string.snippets_empty_command) },
                color = ZtsTextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
        // ドラッグハンドル (≡)。ここを掴んで上下に動かすと並べ替え。
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .pointerInput(snippet.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        }
                    )
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "≡",
                color = if (dragging) ZtsGreen else ZtsTextSecondary,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace
            )
        }
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
    groups: List<SnippetGroup>,
    onSave: (Snippet) -> Unit,
    onCancel: () -> Unit
) {
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var command by remember(initial.id) { mutableStateOf(initial.command) }
    var groupId by remember(initial.id) { mutableStateOf(initial.groupId) }

    Text(
        text = if (initial.label.isEmpty() && initial.command.isEmpty())
            stringResource(R.string.snippets_new_entry_title)
        else
            stringResource(R.string.snippets_edit_entry_title),
        color = ZtsGreen,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace
    )

    Field(
        label = stringResource(R.string.snippets_label_field),
        value = label,
        onChange = { label = it },
        placeholder = stringResource(R.string.snippets_label_placeholder)
    )
    Field(
        label = stringResource(R.string.snippets_command_field),
        value = command,
        onChange = { command = it },
        placeholder = "ls -la --color=auto",
        multiline = true
    )
    // どのグループに置くか。⚠ **グループを 1 つも作っていない人には出さない** — 選べる先が
    // 「未分類」しかない欄は、置き場所を選べるように見えて何も決められない。
    if (groups.isNotEmpty()) {
        Text(
            text = stringResource(R.string.snippets_group_field),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GroupChip(
                label = stringResource(R.string.snippets_group_none),
                selected = groupId.isEmpty(),
                onClick = { groupId = "" }
            )
            groups.forEach { g ->
                GroupChip(label = g.name, selected = g.id == groupId, onClick = { groupId = g.id })
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallButton(label = stringResource(R.string.action_cancel), onClick = onCancel)
        Box(modifier = Modifier.weight(1f))
        SmallButton(
            label = stringResource(R.string.action_save),
            accent = true,
            onClick = {
                if (command.isNotBlank()) {
                    onSave(initial.copy(label = label.trim(), command = command, groupId = groupId))
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
