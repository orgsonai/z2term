package com.zerotoship.z2term.ui.settings

import androidx.activity.compose.BackHandler
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
import com.zerotoship.z2term.service.WhenGuard
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.settings.WhenRule
import com.zerotoship.z2term.settings.WhenTriggerCatalog
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBorder
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
 * 設定シートの「ルールを管理」([WhenRulesSheet]) と、ツールシート (📜) の「自動化」タブの
 * 両方から同じ UI を使うために切り出してある ([ServersBody] と同じ作り)。
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val currentEdit = editing
        if (currentEdit != null) {
            WhenRuleEditForm(
                initial = currentEdit,
                isNew = isNew,
                onSave = { saved ->
                    scope.launch(Dispatchers.IO) {
                        // 新規は id を持たせずに開くので、保存する瞬間に採番する
                        // (作りかけで閉じたときに空のルールファイルを残さないため)。
                        val rule = if (saved.id.isEmpty()) {
                            saved.copy(id = WhenManager.newRuleId(context))
                        } else {
                            saved
                        }
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
                        handle = { ReorderHandle(reorder, rule.id) },
                        onToggle = { checked ->
                            scope.launch(Dispatchers.IO) {
                                WhenManager.setRuleEnabled(context, rule.id, checked)
                                reloadTick++
                            }
                        },
                        onRunNow = { scope.launch(Dispatchers.IO) { WhenManager.runNow(context, rule) } },
                        onEdit = { isNew = false; editing = rule },
                        onDelete = {
                            scope.launch(Dispatchers.IO) {
                                WhenManager.removeRule(context, rule.id)
                                reloadTick++
                            }
                        }
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
            handle()
            IconCell(label = "▶", onClick = onRunNow)
            IconCell(label = "▤", onClick = { logOpen = !logOpen })
            IconCell(label = "✎", onClick = onEdit)
            IconCell(label = "✕", danger = true, onClick = onDelete)
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
    onSave: (WhenRule) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
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
    var cooldown by remember(initial.id) { mutableStateOf(initial.cooldown) }
    var between by remember(initial.id) { mutableStateOf(initial.between) }
    var days by remember(initial.id) { mutableStateOf(initial.days) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    // 貼り付けた文字列から改行を落としたら、黙って直さずに一言出す（勝手に変えられたと思わせない）。
    var stripped by remember(initial.id) { mutableStateOf(false) }
    // 候補を開いている種別。既存ルールを開いたときはその種別を開いておく。
    var openKind by remember(initial.id) {
        mutableStateOf(if (isNew) "" else initial.trigger.substringBefore(':').trim())
    }

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

    Field(
        label = stringResource(R.string.when_trigger_field),
        value = trigger,
        // 貼り付けで改行が混ざっても 1 行に保つ（トリガーは 1 行しか読まれない）。
        onChange = { trigger = it.replace('\n', ' ').replace('\r', ' ') },
        placeholder = "charge:start"
    )

    // きっかけの候補。**そのまま入れて動く完成形**を並べるので、選んだ直後から正しい。
    Text(
        text = stringResource(R.string.when_trigger_pick),
        color = ZtsTextSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WhenTriggerCatalog.kinds.forEach { k ->
            PillButton(label = kindLabel(k.labelKey), accent = k.id == openKind) {
                openKind = if (openKind == k.id) "" else k.id
            }
        }
    }
    WhenTriggerCatalog.kinds.firstOrNull { it.id == openKind }?.let { kind ->
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            kind.options.forEach { option ->
                PillButton(label = option.example) {
                    trigger = option.example
                    error = null
                }
            }
        }
    }

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
            val t = trigger.trim()
            val r = run.trim()
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
            val builtSpec = if (advanced) null else WhenConditionSpec.build(conds)
            val incomplete = builtSpec != null && WhenConditionSpec.parse(builtSpec) == null
            error = problem ?: if (incomplete) msgCondIncomplete else null
            if (error == null) {
                val cond = when {
                    advanced -> condition.trim()
                    condMode == WhenConditionSpec.Mode.ALL -> builtSpec.orEmpty()
                    else -> ""
                }
                val condAny = when {
                    advanced -> conditionAny.trim()
                    condMode == WhenConditionSpec.Mode.ANY -> builtSpec.orEmpty()
                    else -> ""
                }
                onSave(
                    initial.copy(
                        name = name.trim(),
                        trigger = t,
                        run = r,
                        condition = cond,
                        conditionAny = condAny,
                        otherwise = elseCmd.trim(),
                        cooldown = cooldown.trim(),
                        between = between.trim(),
                        days = days.trim(),
                    )
                )
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
 * 条件 1 行 (0.8.373)。**選ぶだけで組める**ようにするのが目的なので、キーも演算子も
 * プルダウンにし、値の欄はキーの型のときだけ出す。
 *
 * ⚠ **キーを変えたら演算子と値を既定へ戻す**。`level<30` のまま `wifi` へ変えると
 * 「真偽キーに数値比較」という、端末では書けても画面では表せない組み合わせになる。
 */
@Composable
private fun ConditionRow(
    cond: WhenCondition,
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
                Text(
                    text = conditionKeyLabel(cond.key) + " ▾",
                    color = ZtsTextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { open = true }
                        .padding(vertical = 2.dp)
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
                Text(
                    text = conditionOpLabel(cond) + " ▾",
                    color = ZtsGreen,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { open = true }
                        .padding(vertical = 2.dp)
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
            }
        }
    }
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
    else -> key
}
