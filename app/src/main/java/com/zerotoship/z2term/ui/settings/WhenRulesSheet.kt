package com.zerotoship.z2term.ui.settings

import android.widget.Toast
import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.zerotoship.z2term.edge.AndroidActions
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.service.NotificationLogService
import com.zerotoship.z2term.service.SystemEventService
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.LocaleHelper
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.zerotoship.z2term.settings.WhenCommandLine
import com.zerotoship.z2term.settings.WhenCondition
import com.zerotoship.z2term.settings.WhenConditionSpec
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
import com.zerotoship.z2term.service.Z2ApiBridge
import com.zerotoship.z2term.service.WhenGuard
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.service.WhenSecrets
import com.zerotoship.z2term.settings.PromptAnswer
import com.zerotoship.z2term.settings.WhenRule
import com.zerotoship.z2term.settings.WhenTriggerCatalog
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.components.ConfirmDialog
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import com.zerotoship.z2term.ui.theme.ZtsWarning
import com.zerotoship.z2term.ui.components.ReorderHandle
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.components.rememberReorderState
import com.zerotoship.z2term.ui.components.reorderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 📜 ツールシートの「自動化」タブ (0.8.227)。`z2-when` のルールを**見る・止める・試す**画面。
 *
 * **なぜ要るか**: `z2-when` はこのアプリの主要機能なのに、登録したルールが画面のどこにも出ず、
 * 端末で `z2-when list` を打つかウィジェットの「有効 N 件」の数字しか手がかりが無かった。
 * さらに `event:` トリガー (0.8.226) で**裏で走る回数が一気に増えた**のに、暴走したときに
 * 全部止める 1 操作も、「さっき何が走ったか」を見る場所も無かった。
 *
 * **作らないもの**: ルールの**新規作成と編集は載せない**。ルールの正本は
 * `~/.z2term/when/<id>.rule` のテキストで、ロジックはシェル側に置くという設計 (DESIGN-SPEC の
 * 「接続点はアプリ・ロジックはシェル」) を崩さないため。ここは一覧・ON/OFF・ログ・▶試す・
 * 一時停止だけに留め、作るのは端末の `z2-when` に任せる。
 *
 * 常駐サーバーのタブ ([ServersBody]) と同じ部品・同じ並びに揃えてある (`ToggleRow` /
 * `HintBox` / `IconCell` / `PillButton` を共有)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenRulesSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var forceClose by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden) forceClose || scrollState.value == 0 else true
        }
    )
    val closeSheet: () -> Unit = {
        forceClose = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
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
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            WhenRulesBody()
        }
    }
}

/**
 * 自動化タブの本体 (シートの中身)。スクロールは呼び出し側が持つ。
 *
 * コマンド一覧 → 自動化 → 自動化ルールで管理する。スクロールは呼び出し側が持つ。
 */
@Composable
fun WhenRulesBody() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 画面を開いている間だけ読み直す。ルールも発火記録もファイルなので、端末側で
    // `z2-when` を叩いた結果もそのまま出る (2 つの入口で同じものを見る)。
    var rules by remember { mutableStateOf(emptyList<WhenRule>()) }
    var paused by remember { mutableStateOf(false) }
    var fired by remember { mutableStateOf(emptyList<WhenManager.Fired>()) }
    var lastFired by remember { mutableStateOf(emptyMap<String, String>()) }
    var reloadTick by remember { mutableStateOf(0) }

    // ドラッグ並べ替え (スニペットタブと同じ操作)。並びは各ルールファイルの `order=` に書く
    // ので、端末から `z2-when` を叩いても消えない。表示順だけで、実行には影響しない。
    val reorder = rememberReorderState(spacing = 10.dp) { ids ->
        scope.launch(Dispatchers.IO) {
            WhenManager.reorderRules(context, ids)
            reloadTick++
        }
    }
    LaunchedEffect(reloadTick) {
        while (true) {
            rules = runCatching { WhenManager.loadRules(context) }.getOrDefault(emptyList())
            paused = WhenManager.isPaused(context)
            fired = runCatching { WhenManager.recentFiredEntries(context, FIRED_SHOWN) }
                .getOrDefault(emptyList())
            lastFired = runCatching { WhenManager.lastFiredByRule(context) }.getOrDefault(emptyMap())
            delay(1500)
        }
    }

    // 編集中はフォームだけを出す (常駐サーバータブと同じ作り)。
    var editing by remember { mutableStateOf<WhenRule?>(null) }
    var isNew by remember { mutableStateOf(false) }
    // ✕ で消す前に一度止める (0.8.452)。一覧の ✕ は編集ボタンの隣にあり、ミスタップが
    // そのままルールの消失になっていた。⚠ **`z2-when` からの削除には確認を挟まない** —
    // コマンドは打った時点で明示的で、対話の余地がない。
    var pendingDelete by remember { mutableStateOf<WhenRule?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val currentEdit = editing
        if (currentEdit != null) {
            WhenRuleEditForm(
                initial = currentEdit,
                isNew = isNew,
                onSave = { saved, secrets ->
                    scope.launch(Dispatchers.IO) {
                        // 新規は id を持たせずに開くので、保存する瞬間に採番する
                        // (作りかけで閉じたときに空のルールファイルを残さないため)。
                        val rule = if (saved.id.isEmpty()) {
                            saved.copy(id = WhenManager.newRuleId(context))
                        } else {
                            saved
                        }
                        // 隠す返事の中身はルールファイルとは別に置く。実行より先に揃えておく。
                        WhenSecrets.save(
                            context, rule.id,
                            keep = rule.answers.filter { it.secret }.map { it.prompt },
                            updates = secrets,
                        )
                        WhenManager.saveRule(context, rule)
                        reloadTick++
                    }
                    editing = null
                },
                onCancel = { editing = null }
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.when_title),
                color = ZtsGreen,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.when_count, rules.count { it.enabled }, rules.size),
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 並び替えは押している間だけ (≡ を常に出すと、スクロールの指で順番が変わる)。
            if (rules.size >= 2 || reorder.arranging) {
                PillButton(label = stringResource(if (reorder.arranging) R.string.list_reorder_done else R.string.list_reorder),
                    accent = reorder.arranging) { reorder.arranging = !reorder.arranging }
                Spacer(modifier = Modifier.width(8.dp))
            }
            PillButton(label = stringResource(R.string.when_new), accent = true) {
                isNew = true
                editing = WhenRule(id = "", trigger = "", run = "")
            }
        }

        // キルスイッチ。止まっている間は一覧側にも出すので、開いた瞬間に「なぜ動かないか」が分かる。
        ToggleRow(
            title = stringResource(R.string.when_pause),
            desc = stringResource(R.string.when_pause_desc),
            checked = paused,
            onChange = { want ->
                paused = want
                scope.launch(Dispatchers.IO) { WhenManager.setPaused(context, want) }
            }
        )
        if (paused) {
            Text(
                text = stringResource(R.string.when_paused_banner),
                color = ZtsError,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (rules.isEmpty()) {
            HintBox(stringResource(R.string.when_empty))
        } else {
            reorder.sync(rules.map { it.id })
            val byId = rules.associateBy { it.id }
            reorder.order.mapNotNull { byId[it] }.forEach { rule ->
                // key(id) でノード identity を固定 → 並べ替え中も掴んだ行に指が追従する。
                key(rule.id) {
                    WhenRuleRow(
                        rule = rule,
                        lastFiredAt = lastFired[rule.id],
                        modifier = Modifier.reorderItem(reorder, rule.id),
                        arranging = reorder.arranging,
                        handle = { ReorderHandle(reorder, rule.id) },
                        onToggle = { checked ->
                            scope.launch(Dispatchers.IO) {
                                WhenManager.setRuleEnabled(context, rule.id, checked)
                                reloadTick++
                            }
                        },
                        onRunNow = { scope.launch(Dispatchers.IO) { WhenManager.runNow(context, rule) } },
                        onEdit = { isNew = false; editing = rule },
                        onDelete = { pendingDelete = rule }
                    )
                }
            }
        }

        // 直近の発火。「いま何が走っているか」ではなく「さっき何が走ったか」を出す欄。
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.when_fired_title),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        if (fired.isEmpty()) {
            HintBox(stringResource(R.string.when_fired_empty))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(ZtsBgPrimary)
                    .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 発火の記録に残るのはトリガーだが、一覧と同じ見出し (名前) で読めた方が
                // 「どのルールが走ったか」が分かる。消したルールの記録はトリガーのまま出る。
                val namedRules = rules.filter { it.name.isNotEmpty() }.associate { it.id to it.name }
                fired.forEach { f ->
                    Text(
                        // 日付は落として時刻だけ (幅が限られるので、直近を読むのに要るのは時刻)。
                        text = "${shortTime(f.time)}  ${namedRules[f.ruleId] ?: f.trigger}  ${f.status}",
                        color = when {
                            f.status == "paused" -> ZtsError
                            f.status == "manual" -> ZtsTextSecondary
                            // 絞り込みで見送ったもの (skip:if / skip:cooldown …)。止まったのが
                            // 意図どおりか一目で分かるよう、実行とも一時停止とも違う色にする。
                            f.status.startsWith("skip:") -> ZtsWarning
                            else -> ZtsTextPrimary
                        },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        HintBox(stringResource(R.string.when_hint))
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_when_rule_title),
            // 名前は任意なので、無ければトリガーで指す (一覧の見出しと同じ出し方)。
            message = stringResource(
                R.string.confirm_delete_item_msg,
                target.name.ifBlank { target.trigger },
            ),
            confirmLabel = stringResource(R.string.ssh_action_delete),
            confirmColor = ZtsError,
            onConfirm = {
                pendingDelete = null
                scope.launch(Dispatchers.IO) {
                    WhenManager.removeRule(context, target.id)
                    reloadTick++
                }
            },
            onCancel = { pendingDelete = null },
        )
    }
}

/** 一覧に出す発火の件数。多すぎるとタブが縦に伸びるだけで読めない。 */
private const val FIRED_SHOWN = 12

/** `2026-07-25T21:40:12` → `21:40:12`。形式が違えばそのまま返す。 */
private fun shortTime(iso: String): String =
    iso.substringAfter('T', iso)

/**
 * ルールに付いている絞り込みを 1 行にまとめる (`if=wifi cooldown=30m`)。
 * **ルールファイルに書いてある表記のまま**出す — 端末で直すときにそのまま打てるように。
 */
private fun filterSummary(rule: WhenRule): String = buildList {
    if (rule.condition.isNotEmpty()) add("if=${rule.condition}")
    if (rule.cooldown.isNotEmpty()) add("cooldown=${rule.cooldown}")
    if (rule.between.isNotEmpty()) add("between=${rule.between}")
    if (rule.days.isNotEmpty()) add("days=${rule.days}")
}.joinToString("  ")

@Composable
private fun WhenRuleRow(
    rule: WhenRule,
    lastFiredAt: String?,
    modifier: Modifier = Modifier,
    arranging: Boolean = false,
    handle: @Composable () -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    // ログは開いている間だけ読む (常時読むと一覧のポーリングが重くなる。ServerRow と同じ作法)。
    var logOpen by remember(rule.id) { mutableStateOf(false) }
    var logText by remember(rule.id) { mutableStateOf("") }
    LaunchedEffect(logOpen, rule.id) {
        while (logOpen) {
            logText = WhenManager.readRuleLog(context, rule.id)
            delay(1500)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                // OFF 側も必ず指定する (指定しないと暗い背景でスイッチが見えなくなる)。
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ZtsGreen,
                    checkedTrackColor = ZtsGreen.copy(alpha = 0.3f),
                    uncheckedThumbColor = ZtsTextSecondary,
                    uncheckedTrackColor = ZtsBgCard,
                    uncheckedBorderColor = ZtsBorder
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 見出しは名前 (未記入ならトリガー・0.8.303)。トリガーだけが並ぶと
                // 「いつ動くか」しか分からず、同じきっかけのルールを見分けられない。
                Text(
                    text = rule.label,
                    color = if (rule.enabled) ZtsTextPrimary else ZtsTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 名前を付けたときだけ、きっかけを 1 行足す (名前で消してしまわない)。
                if (rule.name.isNotEmpty()) {
                    Text(
                        text = rule.trigger,
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = rule.run,
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 絞り込み (if / cooldown / between / days) は付いているときだけ 1 行出す。
                // 付いていないルールの見え方を変えないため (0.8.263)。
                if (rule.hasFilters) {
                    Text(
                        text = filterSummary(rule),
                        color = ZtsWarning,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (lastFiredAt != null) {
                        stringResource(R.string.when_last_fired, shortTime(lastFiredAt))
                    } else {
                        stringResource(R.string.when_never_fired)
                    },
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            // ▶ = トリガーを待たずに 1 回試す。「充電したら…」を確かめるのに充電を
            // 抜き差しさせないための出口 (一時停止中でも動く)。
            // 並び替え中は ≡ だけにする (隣の ✕ を動かす指で押さないように)。
            if (arranging) handle() else {
                IconCell(label = "▶", onClick = onRunNow)
                IconCell(label = "▤", onClick = { logOpen = !logOpen })
                IconCell(label = "✎", onClick = onEdit)
                IconCell(label = "✕", danger = true, onClick = onDelete)
            }
        }

        if (logOpen) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.when_log_title),
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                PillButton(label = stringResource(R.string.servers_log_clear)) {
                    WhenManager.clearRuleLog(context, rule.id)
                    logText = ""
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ZtsBgPrimary)
                    .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Text(
                    text = logText.ifBlank { stringResource(R.string.servers_log_empty) },
                    color = if (logText.isBlank()) ZtsTextSecondary else ZtsTextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * ルールの編集フォーム（0.8.272）。新規作成もこれ 1 つで済ませる。
 *
 * **なぜ画面から作れるようにしたか**: 0.8.271 まではここを「見る・止める・試す」だけに留めて、
 * 作る・直すは端末の `z2-when` に任せていた。しかし `run` の**全文が画面のどこにも出ない**ため、
 * 「このルールが何をするのか」を確かめるのに端末を開くしかなかった。さらに**折り返して貼り付けた
 * コマンドが途中で切れ、黙って構文エラーになり続ける事故**が起きた（ルールファイルは 1 行 1 項目
 * なので、`run` に改行が入ると 2 行目以降が捨てられる）。画面から直せて、改行をその場で潰せて、
 * トリガーを候補から選べれば、この 3 つとも起きない。
 *
 * 正本がテキストファイルであることは変えていない（[WhenManager.saveRule] が同じ書式で書く）ので、
 * 端末で作ったものを画面で直す・その逆、どちらもできる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenRuleEditForm(
    initial: WhenRule,
    isNew: Boolean,
    /** 2 つ目は**今回入れ直した**隠す返事 (聞かれる文言 → 中身)。触っていないものは入らない。 */
    onSave: (WhenRule, Map<String, String>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    // 聞かれたときの返事 (1.0.12)。隠す返事の中身は画面へ読み戻さない (保存済みかどうかだけ見る)。
    var answerRows by remember(initial.id) { mutableStateOf(initial.answers) }
    val storedSecrets = remember(initial.id) { WhenSecrets.storedPrompts(context, initial.id) }
    var trigger by remember(initial.id) { mutableStateOf(initial.trigger) }
    var run by remember(initial.id) { mutableStateOf(initial.run) }
    var condition by remember(initial.id) { mutableStateOf(initial.condition) }
    // 条件ビルダー (0.8.373)。⚠ 端末で書いた式が**組み立て直せないときはそのまま文字で見せる** —
    // 画面が勝手に解釈して書き換えると、開いて閉じただけで動きの変わるルールができる。
    val builder = remember(initial.id) {
        WhenConditionSpec.builderOf(initial.condition, initial.conditionAny)
    }
    var conditionAny by remember(initial.id) { mutableStateOf(initial.conditionAny) }
    var condMode by remember(initial.id) { mutableStateOf(builder.mode) }
    var conds by remember(initial.id) { mutableStateOf(builder.conditions) }
    val advanced = remember(initial.id) { builder.advanced }
    var elseCmd by remember(initial.id) { mutableStateOf(initial.otherwise) }
    // 条件行に出す「今の値」(0.8.374)。⚠ **開いた時点で 1 回だけ**読む — 条件を組むのに要るのは
    // 「いくつを境にすればいいか」の目安で、秒単位の追従ではない (常駐も通信も増やさない)。
    // 元は `volume>77` のような**あり得ない閾値**が書けてしまい (実際は 0〜15 のステップ数)、
    // 条件に合っていないつもりのルールが動いた (実機で指摘)。単位もスケールも画面から知りようが無かった。
    val nowState = remember(initial.id) { Z2ApiBridge.stateSnapshot(context) }
    var cooldown by remember(initial.id) { mutableStateOf(initial.cooldown) }
    var between by remember(initial.id) { mutableStateOf(initial.between) }
    var days by remember(initial.id) { mutableStateOf(initial.days) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    // 貼り付けた文字列から改行を落としたら、黙って直さずに一言出す（勝手に変えられたと思わせない）。
    var stripped by remember(initial.id) { mutableStateOf(false) }

    // run が 1 本のスクリプトを指しているなら中身を読む（読み取り専用の確認窓）。
    var script by remember(initial.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(run) {
        script = withContext(Dispatchers.IO) { WhenManager.readRunScript(context, run) }
    }

    // 保存ボタンの中（Composable ではない）から使うので、文言は先に取っておく。
    val msgTriggerEmpty = stringResource(R.string.when_form_trigger_empty)
    val msgTriggerUnknown = stringResource(R.string.when_form_trigger_unknown)
    val msgTriggerBadSpec = stringResource(R.string.when_form_trigger_bad_spec)
    val msgRunEmpty = stringResource(R.string.when_form_run_empty)
    val msgRunMultiline = stringResource(R.string.when_form_run_multiline)
    val msgIfUnknown = stringResource(R.string.when_form_if_unknown)
    val msgCondIncomplete = stringResource(R.string.when_cond_incomplete)
    val msgAnswerSecretMissing = stringResource(R.string.when_answer_secret_missing)

    Text(
        text = if (isNew) stringResource(R.string.when_new_rule_title)
        else stringResource(R.string.when_edit_rule_title),
        color = ZtsGreen,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace
    )

    // 名前は表示だけの項目 (0.8.303)。空欄で保存すれば今までどおりトリガーが見出しになるので、
    // 必須にはしない — 1 行足すために保存が止まる方が嫌われる。
    Field(
        label = stringResource(R.string.when_name_field),
        value = name,
        // ルールファイルは 1 行 1 項目。名前でも改行が入れば後ろが捨てられるので同じく潰す。
        onChange = { name = it.replace('\n', ' ').replace('\r', ' ') },
        placeholder = stringResource(R.string.when_name_placeholder)
    )

    // きっかけ (1.0.54 で作り直し)。選ぶ部品は [TriggerPicker]、正本は今までどおり 1 行の文字列。
    TriggerPicker(
        ruleKey = initial.id,
        trigger = trigger,
        startOpen = isNew || initial.trigger.isBlank(),
        onTrigger = {
            // 貼り付けで改行が混ざっても 1 行に保つ（トリガーは 1 行しか読まれない）。
            trigger = it.replace('\n', ' ').replace('\r', ' ')
            error = null
        },
    )

    Field(
        label = stringResource(R.string.when_run_field),
        value = run,
        onChange = { value ->
            // ⚠ ここが肝。ルールファイルは 1 行 1 項目なので、改行が入ると 2 行目以降が
            // 捨てられて**途中で切れたコマンド**になる（0.8.272 の事故）。入った瞬間に潰す。
            val single = value.replace('\n', ' ').replace('\r', ' ')
            if (single != value) stripped = true
            run = single
        },
        placeholder = "~/.z2term/macros/backup.sh",
        multiline = true
    )
    if (stripped) {
        Text(
            text = stringResource(R.string.when_form_newline_stripped),
            color = ZtsWarning,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    // run がスクリプト 1 本を指しているなら、その中身を見せる（直すのは端末側）。
    script?.let { text ->
        Text(
            text = stringResource(R.string.when_script_title),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgPrimary)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(
                text = text,
                color = ZtsTextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = stringResource(R.string.when_script_readonly),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    Text(
        text = stringResource(R.string.when_filters_label),
        color = ZtsTextSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
    )
    // --- 条件 (0.8.373) -------------------------------------------------------
    Text(
        text = stringResource(R.string.when_cond_title),
        color = ZtsTextSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
    )
    if (advanced) {
        // 端末で書いたもの。触らずにそのまま出す。
        Text(
            text = stringResource(R.string.when_cond_advanced),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Field(
            label = stringResource(R.string.when_if_field),
            value = condition,
            onChange = { condition = it.trim() },
            placeholder = "wifi,!screen"
        )
        Field(
            label = stringResource(R.string.when_if_any_field),
            value = conditionAny,
            onChange = { conditionAny = it.trim() },
            placeholder = "wifi,ssid=Home"
        )
    } else {
        // 「すべて満たす」/「どれか満たす」= if= か if_any= か。
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeChip(
                label = stringResource(R.string.when_cond_mode_all),
                selected = condMode == WhenConditionSpec.Mode.ALL,
            ) { condMode = WhenConditionSpec.Mode.ALL }
            ModeChip(
                label = stringResource(R.string.when_cond_mode_any),
                selected = condMode == WhenConditionSpec.Mode.ANY,
            ) { condMode = WhenConditionSpec.Mode.ANY }
        }
        if (conds.isEmpty()) {
            Text(
                text = stringResource(R.string.when_cond_none),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        conds.forEachIndexed { index, cond ->
            key(index) {
                ConditionRow(
                    cond = cond,
                    now = nowState,
                    onChange = { updated -> conds = conds.toMutableList().also { it[index] = updated } },
                    onRemove = { conds = conds.toMutableList().also { it.removeAt(index) } },
                )
            }
        }
        PillButton(label = stringResource(R.string.when_cond_add)) {
            conds = conds + WhenCondition("wifi", WhenCondition.Op.TRUTHY)
        }
    }

    // --- 条件に合わないとき (0.8.373) ----------------------------------------
    Text(
        text = stringResource(R.string.when_else_title),
        color = ZtsTextSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
    )
    Field(
        label = stringResource(R.string.when_else_field),
        value = elseCmd,
        onChange = { elseCmd = it },
        placeholder = stringResource(R.string.when_else_hint)
    )
    Text(
        text = stringResource(R.string.when_else_note),
        color = ZtsTextSecondary,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace
    )
    Field(
        label = stringResource(R.string.when_cooldown_field),
        value = cooldown,
        onChange = { cooldown = it.trim() },
        placeholder = "30m"
    )
    Field(
        label = stringResource(R.string.when_between_field),
        value = between,
        onChange = { between = it.trim() },
        placeholder = "22:00-07:00"
    )
    Field(
        label = stringResource(R.string.when_days_field),
        value = days,
        onChange = { days = it.trim() },
        placeholder = "mon-fri"
    )

    // --- 聞かれたときの返事 (1.0.12) -----------------------------------------
    Text(
        text = stringResource(R.string.when_answer_title),
        color = ZtsTextSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
    )
    answerRows.forEachIndexed { index, row ->
        fun update(next: PromptAnswer) {
            answerRows = answerRows.toMutableList().also { it[index] = next }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Field(
                label = stringResource(R.string.when_answer_prompt),
                value = row.prompt,
                onChange = { update(row.copy(prompt = it.replace("\n", " ").replace("\r", ""))) },
                placeholder = "Password:"
            )
            Field(
                label = stringResource(R.string.when_answer_text),
                value = row.text,
                onChange = { update(row.copy(text = it.replace("\n", "").replace("\r", ""))) },
                placeholder = stringResource(
                    if (row.secret && row.prompt.trim() in storedSecrets) R.string.when_answer_stored_hint
                    else R.string.when_answer_text_hint
                ),
                secret = row.secret
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeChip(
                    label = stringResource(R.string.when_answer_secret),
                    selected = row.secret,
                    onClick = { update(row.copy(secret = !row.secret)) }
                )
                Box(modifier = Modifier.weight(1f))
                PillButton(label = stringResource(R.string.when_answer_remove)) {
                    answerRows = answerRows.toMutableList().also { it.removeAt(index) }
                }
            }
        }
    }
    PillButton(label = stringResource(R.string.when_answer_add)) {
        answerRows = answerRows + PromptAnswer("")
    }
    Text(
        text = stringResource(R.string.when_answer_note),
        color = ZtsTextSecondary,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace
    )

    // --- 端末で言うとこう (0.8.375) ------------------------------------------
    // ⚠ **保存する中身とプレビューは 1 か所で組む**。別々に組むと「画面に出ているコマンド」と
    // 「保存されるルール」がズレて、コピーして貼った 1 行が別のルールになる。
    val builtSpec = if (advanced) null else WhenConditionSpec.build(conds)
    val edited = initial.copy(
        name = name.trim(),
        trigger = trigger.trim(),
        run = run.trim(),
        condition = when {
            advanced -> condition.trim()
            condMode == WhenConditionSpec.Mode.ALL -> builtSpec.orEmpty()
            else -> ""
        },
        conditionAny = when {
            advanced -> conditionAny.trim()
            condMode == WhenConditionSpec.Mode.ANY -> builtSpec.orEmpty()
            else -> ""
        },
        otherwise = elseCmd.trim(),
        cooldown = cooldown.trim(),
        between = between.trim(),
        days = days.trim(),
        // 文言が空の行は捨てる。隠す返事の中身はルールへ入れない (別に保存する)。
        answers = answerRows.filter { it.prompt.isNotBlank() }
            .map { PromptAnswer(it.prompt.trim(), if (it.secret) "" else it.text, it.secret) },
    )
    Text(
        text = stringResource(R.string.when_cmd_title),
        color = ZtsTextSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
    )
    if (edited.trigger.isEmpty() || edited.run.isEmpty()) {
        // 中途半端な行を出さない (貼っても動かないコマンドを見せる方が混乱する)。
        Text(
            text = stringResource(R.string.when_cmd_hint),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    } else {
        val commandLine = WhenCommandLine.of(edited)
        val copied = stringResource(R.string.settings_cmd_copied)
        Text(
            text = commandLine,
            color = ZtsGreen,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .clickable {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(
                        android.content.ClipData.newPlainText("z2term", commandLine)
                    )
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Text(
            text = stringResource(R.string.when_cmd_note),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    error?.let { message ->
        Text(
            text = message,
            color = ZtsError,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PillButton(label = stringResource(R.string.action_cancel), onClick = onCancel)
        Box(modifier = Modifier.weight(1f))
        PillButton(label = stringResource(R.string.action_save), accent = true) {
            val t = edited.trigger
            val r = edited.run
            // 綴りが 1 文字違うだけで「一覧に並ぶのに一生動かないルール」になるので、
            // 保存の前に必ず見る（CLI が登録時に検査しているのと同じ理由・同じ判定）。
            val problem = when (WhenTriggerCatalog.triggerProblem(t)) {
                WhenTriggerCatalog.Problem.EMPTY -> msgTriggerEmpty
                WhenTriggerCatalog.Problem.UNKNOWN_KIND -> msgTriggerUnknown.format(t.substringBefore(':'))
                WhenTriggerCatalog.Problem.BAD_SPEC -> msgTriggerBadSpec.format(t)
                null -> when (WhenTriggerCatalog.runProblem(r)) {
                    WhenTriggerCatalog.RunProblem.EMPTY -> msgRunEmpty
                    WhenTriggerCatalog.RunProblem.MULTILINE -> msgRunMultiline
                    null -> if (advanced) {
                        (unknownConditionKey(condition) ?: unknownConditionKey(conditionAny))
                            ?.let { msgIfUnknown.format(it) }
                    } else null
                }
            }
            // ビルダーで組んだ条件は**往復で検証する** (組み立てた文字列を読み直せなければ、
            // 値の入れ忘れなどで画面の見た目と実際の式がズレている)。
            val incomplete = builtSpec != null && WhenConditionSpec.parse(builtSpec) == null
            // 隠す返事は、中身が保存済みか今回入れたものでないと送るものが無い。
            val secretRows = answerRows.filter { it.secret && it.prompt.isNotBlank() }
            val secretMissing = secretRows.any { it.text.isEmpty() && it.prompt.trim() !in storedSecrets }
            error = problem ?: if (incomplete) msgCondIncomplete
                else if (secretMissing) msgAnswerSecretMissing else null
            // 画面に出ているコマンドと同じもの ([edited]) をそのまま保存する。
            if (error == null) {
                onSave(edited, secretRows.filter { it.text.isNotEmpty() }.associate { it.prompt.trim() to it.text })
            }
        }
    }
}

/** 「すべて満たす」/「どれか満たす」の切り替え。選ばれている方だけ枠と字を強調する。 */
@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) ZtsGreen else ZtsTextSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (selected) ZtsGreen else ZtsBorder, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/**
 * プルダウンの入口 (0.8.522)。
 *
 * ⛔ **文字と ▾ を並べるだけにしない。** そうしていた間、利用者には「ただの白文字」で
 * 押せる場所に見えず、▾ も本文と同じ大きさなので文の一部に埋もれていた。
 * ⭐ **押せるものの意匠は 1 つに揃える** — 枠 1dp + 背景 + 角丸 6dp は [PillButton] と同じで、
 * 「この見た目は押せる」が画面をまたいで一貫する。⚠ ボタンと違うのは右端の ▾ だけで、
 * それが「押すと開く」の印になる。
 *
 * @param fill 幅いっぱいに広げる (行の中で他と場所を取り合わないとき)。
 * @param accent 値そのものではなく**選び方**を表す欄 (演算子) を緑にする。
 */
@Composable
private fun DropdownAnchor(
    label: String,
    fill: Boolean = false,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, if (accent) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (accent) ZtsGreen else ZtsTextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f, fill = false)
        )
        // ⚠ 本文より大きくする。同じ大きさだと文の一部に見えて、印にならない。
        Text(
            text = "▾",
            color = ZtsGreen,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 条件 1 行 (0.8.373)。**選ぶだけで組める**ようにするのが目的なので、キーも演算子も
 * プルダウンにし、値の欄はキーの型のときだけ出す。
 *
 * ⚠ **キーを変えたら演算子と値を既定へ戻す**。`level<30` のまま `wifi` へ変えると
 * 「真偽キーに数値比較」という、端末では書けても画面では表せない組み合わせになる。
 */
@Composable
private fun ConditionRow(
    cond: WhenCondition,
    now: Map<String, String>,
    onChange: (WhenCondition) -> Unit,
    onRemove: () -> Unit,
) {
    val kind = WhenConditionSpec.kindOf(cond.key)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                var open by remember { mutableStateOf(false) }
                DropdownAnchor(
                    label = conditionKeyLabel(cond.key),
                    fill = true,
                    onClick = { open = true }
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    WhenConditionSpec.KEYS.forEach { k ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    conditionKeyLabel(k),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                )
                            },
                            onClick = { open = false; onChange(defaultCondition(k)) }
                        )
                    }
                }
            }
            Text(
                text = "✕",
                color = ZtsTextSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onRemove() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                var open by remember { mutableStateOf(false) }
                DropdownAnchor(
                    label = conditionOpLabel(cond),
                    accent = true,
                    onClick = { open = true }
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    conditionOpChoices(cond).forEach { choice ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    conditionOpLabel(choice),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                )
                            },
                            onClick = { open = false; onChange(choice.copy(value = cond.value)) }
                        )
                    }
                }
            }
            // 真偽のときは値の欄が要らないぶん、ここに「今の値」を出す。
            if (kind == WhenConditionSpec.Kind.BOOL) {
                conditionNowLabel(cond.key, now)?.let { nowText ->
                    Text(
                        text = nowText,
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            // 値が要るのは「一致」と「数値比較」だけ。真偽のときは欄そのものを出さない。
            if (kind != WhenConditionSpec.Kind.BOOL) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ZtsBgCard)
                        .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (cond.value.isEmpty()) {
                        Text(
                            text = if (kind == WhenConditionSpec.Kind.NUMBER) "30" else "Home",
                            color = ZtsTextSecondary.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    BasicTextField(
                        value = cond.value,
                        onValueChange = { onChange(cond.copy(value = it.trim())) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = ZtsTextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(ZtsGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                conditionNowLabel(cond.key, now)?.let { nowText ->
                    Text(
                        text = nowText,
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

/**
 * 条件行に添える「今の値」(0.8.374)。取れなければ null (何も出さない)。
 *
 * ⚠ **`volume` は上限も一緒に出す** — 0〜15 のような**端末ごとに違うステップ数**なので、
 * % だと思って `volume>77` と書くと**永久に成立しない条件**ができる (実機で踏んだ)。
 * ⚠ 真偽の読み方は [WhenGuard.truthy] を通す — 表示と判定を別々に書くと、「今: いいえ」と
 * 出ているのに条件は成立する、という一番たちの悪いズレになる。
 */
@Composable
private fun conditionNowLabel(key: String, now: Map<String, String>): String? {
    val raw = now[key]?.trim().orEmpty()
    if (raw.isEmpty()) {
        // つないでいない Wi-Fi の名前など、「今は無い」ことに意味がある項目だけ出す。
        return if (key == "ssid") stringResource(R.string.when_now_empty) else null
    }
    val shown = when {
        key == "volume" -> now["volume_max"]?.let { "$raw / $it" } ?: raw
        WhenConditionSpec.kindOf(key) == WhenConditionSpec.Kind.BOOL ->
            if (WhenGuard.truthy(raw)) stringResource(R.string.when_op_yes)
            else stringResource(R.string.when_op_no)
        else -> raw
    }
    return stringResource(R.string.when_now_value, shown)
}

/** キーを選び直したときの既定。型に合う演算子から始める。 */
private fun defaultCondition(key: String): WhenCondition = when (WhenConditionSpec.kindOf(key)) {
    WhenConditionSpec.Kind.BOOL -> WhenCondition(key, WhenCondition.Op.TRUTHY)
    WhenConditionSpec.Kind.TEXT -> WhenCondition(key, WhenCondition.Op.EQ)
    WhenConditionSpec.Kind.NUMBER -> WhenCondition(key, WhenCondition.Op.LT)
}

/** その条件で選べる演算子 (値は呼び出し側が引き継ぐ)。 */
private fun conditionOpChoices(cond: WhenCondition): List<WhenCondition> =
    when (WhenConditionSpec.kindOf(cond.key)) {
        WhenConditionSpec.Kind.BOOL -> listOf(
            cond.copy(op = WhenCondition.Op.TRUTHY, negate = false),
            cond.copy(op = WhenCondition.Op.TRUTHY, negate = true),
        )
        WhenConditionSpec.Kind.TEXT -> listOf(
            cond.copy(op = WhenCondition.Op.EQ, negate = false),
            cond.copy(op = WhenCondition.Op.EQ, negate = true),
        )
        WhenConditionSpec.Kind.NUMBER -> listOf(
            cond.copy(op = WhenCondition.Op.GT, negate = false),
            cond.copy(op = WhenCondition.Op.LT, negate = false),
        )
    }

@Composable
private fun conditionOpLabel(cond: WhenCondition): String =
    when (WhenConditionSpec.kindOf(cond.key)) {
        WhenConditionSpec.Kind.BOOL ->
            if (cond.negate) stringResource(R.string.when_op_no) else stringResource(R.string.when_op_yes)
        WhenConditionSpec.Kind.TEXT ->
            if (cond.negate) stringResource(R.string.when_op_diff) else stringResource(R.string.when_op_same)
        WhenConditionSpec.Kind.NUMBER ->
            if (cond.op == WhenCondition.Op.LT) stringResource(R.string.when_op_less)
            else stringResource(R.string.when_op_more)
    }

/** 条件キーの日本語 / 英語ラベル。⚠ 一覧は [WhenConditionSpec.KEYS] と揃えること。 */
@Composable
private fun conditionKeyLabel(key: String): String = when (key) {
    "wifi" -> stringResource(R.string.when_key_wifi)
    "charging" -> stringResource(R.string.when_key_charging)
    "screen" -> stringResource(R.string.when_key_screen)
    "locked" -> stringResource(R.string.when_key_locked)
    "idle" -> stringResource(R.string.when_key_idle)
    "headset" -> stringResource(R.string.when_key_headset)
    "bt_audio" -> stringResource(R.string.when_key_bt_audio)
    "airplane" -> stringResource(R.string.when_key_airplane)
    "ssid" -> stringResource(R.string.when_key_ssid)
    "plug" -> stringResource(R.string.when_key_plug)
    "ringer" -> stringResource(R.string.when_key_ringer)
    "level" -> stringResource(R.string.when_key_level)
    "temp" -> stringResource(R.string.when_key_temp)
    "volume" -> stringResource(R.string.when_key_volume)
    else -> key
}

/**
 * `if=` に知らないキーが混ざっていれば、その最初の 1 つ（無ければ null）。
 * 判定は [WhenGuard] が持っているものをそのまま使う（一覧を 2 か所に置かない）。
 */
private fun unknownConditionKey(spec: String): String? =
    spec.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { term -> term.removePrefix("!").takeWhile { it != '=' && it != '<' && it != '>' }.trim() }
        .firstOrNull { it.isNotEmpty() && !WhenGuard.isKnownCondition(it) }

/** トリガー種別の短いラベル。文字列は `strings.xml` に置く（英語版も要るため）。 */
@Composable
private fun kindLabel(key: String): String = when (key) {
    "charge" -> stringResource(R.string.when_kind_charge)
    "battery" -> stringResource(R.string.when_kind_battery)
    "time" -> stringResource(R.string.when_kind_time)
    "wifi" -> stringResource(R.string.when_kind_wifi)
    "net" -> stringResource(R.string.when_kind_net)
    "sensor" -> stringResource(R.string.when_kind_sensor)
    "sms" -> stringResource(R.string.when_kind_sms)
    "notify" -> stringResource(R.string.when_kind_notify)
    "file" -> stringResource(R.string.when_kind_file)
    "share" -> stringResource(R.string.when_kind_share)
    "event" -> stringResource(R.string.when_kind_event)
    "boot" -> stringResource(R.string.when_kind_boot)
    "bt" -> stringResource(R.string.when_kind_bt)
    "app" -> stringResource(R.string.when_kind_app)
    "steps" -> stringResource(R.string.when_kind_steps)
    else -> key
}

/**
 * きっかけを選ぶ部品 (1.0.54)。
 *
 * 種別が 15、候補が 50 を超え、書式 (`battery:below=20`) のボタンを 1 面に並べる作りでは
 * **読んで選べなくなった**。そこで次の 3 つに分けた。
 *  - 種別を「何について起きたか」の段 ([WhenTriggerCatalog.groups]) に分けて並べる
 *  - 候補は「いつ動くか」の文で 1 行ずつ出し、書式はその下に小さく添える
 *  - 選び終えたら畳み、選んだものの文と書式・値の欄・足りない許可だけを残す
 *
 * ⚠ 正本は**今までどおり 1 行の文字列** ([trigger])。端末で書いた候補に無い書き方も
 * 「一覧にない書き方」として壊さずに出し、下の書式欄でそのまま直せる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerPicker(
    ruleKey: String,
    trigger: String,
    startOpen: Boolean,
    onTrigger: (String) -> Unit,
) {
    val context = LocalContext.current
    val events = remember { eventChoices(context) }
    val kindOf = trigger.substringBefore(':').trim()
    var open by remember(ruleKey) { mutableStateOf(startOpen) }
    var openKind by remember(ruleKey) { mutableStateOf(kindOf) }
    val option = WhenTriggerCatalog.optionFor(trigger)
    val eventName = if (kindOf == "event") trigger.substringAfter(':', "").trim() else ""

    Text(
        text = stringResource(R.string.when_trigger_field),
        color = ZtsTextSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
    )
    // 選んでいるもの。押すと選び直す一覧が開く (閉じた状態でも何を選んだかは必ず読める)。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, if (open) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
            .clickable { open = !open; if (open && openKind.isEmpty()) openKind = kindOf }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val summary = when {
                trigger.isBlank() -> stringResource(R.string.when_trigger_none)
                kindOf == "event" -> events.firstOrNull { it.first == eventName }?.second
                    ?: stringResource(R.string.when_trigger_custom)
                option != null -> optionLabel(option)
                else -> stringResource(R.string.when_trigger_custom)
            }
            Text(
                text = summary,
                color = if (trigger.isBlank()) ZtsTextSecondary else ZtsTextPrimary,
                fontSize = 13.sp
            )
            if (trigger.isNotBlank()) {
                Text(text = trigger, color = ZtsTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Text(
            text = stringResource(if (open) R.string.when_trigger_close else R.string.when_trigger_change),
            color = ZtsGreen,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    if (open) {
        Text(
            text = stringResource(R.string.when_trigger_pick),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        // 段ごとに 1 行。左に段の名前、右に種別。段の名前で当たりを付けてから種別を選ぶ。
        WhenTriggerCatalog.groups.forEach { group ->
            val kinds = WhenTriggerCatalog.kinds.filter { it.group == group }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = groupLabel(group),
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.width(84.dp).padding(top = 9.dp)
                )
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    kinds.forEach { k ->
                        PillButton(label = kindLabel(k.labelKey), accent = k.id == openKind) {
                            openKind = if (openKind == k.id) "" else k.id
                        }
                    }
                }
            }
        }
        // 選んだ種別の候補。1 行 = 1 候補で、文を主に・書式を従に出す。
        val kind = WhenTriggerCatalog.kinds.firstOrNull { it.id == openKind }
        if (kind != null) {
            val rows: List<Triple<String, String, String>> = if (kind.id == "event") {
                events.map { (name, desc) -> Triple("event:$name", desc, "event:$name") }
            } else {
                kind.options.map { Triple(it.example, optionLabel(it), it.example) }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            ) {
                rows.forEachIndexed { index, (value, label, shown) ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(ZtsBorder))
                    val selected = value == trigger ||
                        (kind.id != "event" && WhenTriggerCatalog.optionFor(trigger)?.example == value)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onTrigger(value)
                                open = false
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(text = label, color = if (selected) ZtsGreen else ZtsTextPrimary, fontSize = 12.sp)
                        Text(text = shown, color = ZtsTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }

    // 値の要る候補なら、値だけの欄を出す (書式の中の数字を探して直さなくてよいように)。
    if (option != null && option.needsValue) {
        Field(
            label = stringResource(R.string.when_trigger_value),
            value = trigger.trim().removePrefix(option.template),
            onChange = { onTrigger(option.template + it) },
            placeholder = option.hint
        )
    }

    TriggerRequirement(trigger)

    // 書式そのもの。端末の `z2-when` と同じ 1 行で、候補に無い書き方もここで直せる。
    Field(
        label = stringResource(R.string.when_trigger_format),
        value = trigger,
        onChange = onTrigger,
        placeholder = "charge:start"
    )
}

/**
 * 選んだきっかけが**いまの設定では動かない**ときだけ、理由と直す入口を出す (1.0.54)。
 *
 * 検知 OFF・許可が無い、のどれも「登録はできるのに一度も動かない」になり、画面からは
 * 原因が見えない。満たしているときは何も出さない (余計な注意書きで画面を埋めない)。
 */
@Composable
private fun TriggerRequirement(trigger: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resume = rememberPermissionRefresh()
    var asked by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked++ }
    val detection by remember { AppSettings(context).flow.map { it.systemEventCaptureEnabled } }
        .collectAsState(initial = true)
    val kind = trigger.substringBefore(':').trim()
    val spec = trigger.substringAfter(':', "").trim()
    fun allowed(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // 1 つずつ見て、最初に足りないものだけを出す (並べても、直す順は上からになる)。
    val need: Triple<Int, String, () -> Unit>? = remember(trigger, resume, asked, detection) {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        val needsDetection = when (kind) {
            "charge", "battery", "wifi", "net", "bt", "sensor", "steps", "file" -> true
            "event" -> spec !in setOf("alarm", "notify_action", "unlock_failed", "unlock_succeeded", "boot")
            else -> false
        }
        when {
            needsDetection && !detection -> Triple(R.string.when_need_detection, context.getString(R.string.when_need_detection_on)) {
                scope.launch {
                    AppSettings(context).setSystemEventCaptureEnabled(true)
                    SystemEventService.sync(context, true)
                }
                Unit
            }
            kind == "bt" && Build.VERSION.SDK_INT >= 31 && !allowed(Manifest.permission.BLUETOOTH_CONNECT) ->
                Triple(R.string.when_need_bt, context.getString(R.string.permissions_request)) {
                    runCatching { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }.onFailure { open(details) }
                    Unit
                }
            kind == "steps" && !allowed(Manifest.permission.ACTIVITY_RECOGNITION) ->
                Triple(R.string.when_need_activity, context.getString(R.string.permissions_request)) {
                    runCatching { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }.onFailure { open(details) }
                    Unit
                }
            kind == "sms" && !allowed(Manifest.permission.RECEIVE_SMS) ->
                Triple(R.string.when_need_sms, context.getString(R.string.permissions_request)) {
                    runCatching { launcher.launch(Manifest.permission.RECEIVE_SMS) }.onFailure { open(details) }
                    Unit
                }
            kind == "notify" && runCatching {
                !context.getSystemService(NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(ComponentName(context, NotificationLogService::class.java))
            }.getOrDefault(false) ->
                Triple(R.string.when_need_listener, context.getString(R.string.permissions_manage)) {
                    open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    Unit
                }
            kind == "app" && !AndroidActions.enabled(context) ->
                Triple(R.string.when_need_accessibility, context.getString(R.string.permissions_manage)) {
                    open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    Unit
                }
            else -> null
        }
    }
    need?.let { (message, button, action) ->
        Text(text = stringResource(message), color = ZtsWarning, fontSize = 11.sp)
        PillButton(label = button, onClick = action)
    }
    // 歩数は「今日いくつか」が分かると、届かせたい数を決めやすい (条件の「今の値」と同じ考え方)。
    if (kind == "steps") {
        val today = remember(trigger, resume) { WhenManager.stepsToday(context) }
        if (today != null) {
            Text(
                text = stringResource(R.string.when_steps_today, today),
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** `z2-when events` の一覧 (名前 → 説明)。CLI と同じ文言を使い、言い回しを 2 か所に持たない。 */
private fun eventChoices(context: Context): List<Pair<String, String>> =
    runCatching {
        Z2ApiMsg(lang = LocaleHelper.language(context), d = "$").whenEventList.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
            val name = t.substringBefore(' ')
            val desc = t.substringAfter(' ', "").substringBeforeLast('[').trim()
            name to desc.ifEmpty { name }
        }
    }.getOrDefault(emptyList())

/** 種別をまとめる段の名前。 */
@Composable
private fun groupLabel(group: String): String = when (group) {
    "time" -> stringResource(R.string.when_group_time)
    "power" -> stringResource(R.string.when_group_power)
    "link" -> stringResource(R.string.when_group_link)
    "apps" -> stringResource(R.string.when_group_apps)
    "device" -> stringResource(R.string.when_group_device)
    else -> group
}

/** 候補の「いつ動くか」の文。キーは [WhenTriggerCatalog.Option.labelKey]。 */
@Composable
private fun optionLabel(option: WhenTriggerCatalog.Option): String = when (option.labelKey) {
    "time_daily" -> stringResource(R.string.when_opt_time_daily)
    "time_at" -> stringResource(R.string.when_opt_time_at)
    "time_every" -> stringResource(R.string.when_opt_time_every)
    "time_cron" -> stringResource(R.string.when_opt_time_cron)
    "charge_start" -> stringResource(R.string.when_opt_charge_start)
    "charge_stop" -> stringResource(R.string.when_opt_charge_stop)
    "battery_below" -> stringResource(R.string.when_opt_battery_below)
    "battery_above" -> stringResource(R.string.when_opt_battery_above)
    "battery_temp_above" -> stringResource(R.string.when_opt_battery_temp_above)
    "battery_temp_below" -> stringResource(R.string.when_opt_battery_temp_below)
    "wifi_connect" -> stringResource(R.string.when_opt_wifi_connect)
    "wifi_disconnect" -> stringResource(R.string.when_opt_wifi_disconnect)
    "wifi_ssid" -> stringResource(R.string.when_opt_wifi_ssid)
    "net_online" -> stringResource(R.string.when_opt_net_online)
    "net_offline" -> stringResource(R.string.when_opt_net_offline)
    "net_wifi" -> stringResource(R.string.when_opt_net_wifi)
    "net_mobile" -> stringResource(R.string.when_opt_net_mobile)
    "net_ethernet" -> stringResource(R.string.when_opt_net_ethernet)
    "bt_connect" -> stringResource(R.string.when_opt_bt_connect)
    "bt_disconnect" -> stringResource(R.string.when_opt_bt_disconnect)
    "bt_connect_name" -> stringResource(R.string.when_opt_bt_connect_name)
    "bt_disconnect_name" -> stringResource(R.string.when_opt_bt_disconnect_name)
    "app_open" -> stringResource(R.string.when_opt_app_open)
    "app_close" -> stringResource(R.string.when_opt_app_close)
    "notify_any" -> stringResource(R.string.when_opt_notify_any)
    "notify_otp" -> stringResource(R.string.when_opt_notify_otp)
    "notify_pkg" -> stringResource(R.string.when_opt_notify_pkg)
    "notify_title" -> stringResource(R.string.when_opt_notify_title)
    "notify_contains" -> stringResource(R.string.when_opt_notify_contains)
    "notify_call" -> stringResource(R.string.when_opt_notify_call)
    "notify_missed_call" -> stringResource(R.string.when_opt_notify_missed_call)
    "sms_any" -> stringResource(R.string.when_opt_sms_any)
    "sms_otp" -> stringResource(R.string.when_opt_sms_otp)
    "sms_from" -> stringResource(R.string.when_opt_sms_from)
    "sms_contains" -> stringResource(R.string.when_opt_sms_contains)
    "share_any" -> stringResource(R.string.when_opt_share_any)
    "share_text" -> stringResource(R.string.when_opt_share_text)
    "share_file" -> stringResource(R.string.when_opt_share_file)
    "share_contains" -> stringResource(R.string.when_opt_share_contains)
    "share_ext" -> stringResource(R.string.when_opt_share_ext)
    "event" -> stringResource(R.string.when_opt_event)
    "sensor_shake" -> stringResource(R.string.when_opt_sensor_shake)
    "sensor_light_above" -> stringResource(R.string.when_opt_sensor_light_above)
    "sensor_light_below" -> stringResource(R.string.when_opt_sensor_light_below)
    "sensor_near" -> stringResource(R.string.when_opt_sensor_near)
    "sensor_far" -> stringResource(R.string.when_opt_sensor_far)
    "steps_reach" -> stringResource(R.string.when_opt_steps_reach)
    "file_new" -> stringResource(R.string.when_opt_file_new)
    "file_changed" -> stringResource(R.string.when_opt_file_changed)
    "file_deleted" -> stringResource(R.string.when_opt_file_deleted)
    "boot" -> stringResource(R.string.when_opt_boot)
    else -> option.example
}
