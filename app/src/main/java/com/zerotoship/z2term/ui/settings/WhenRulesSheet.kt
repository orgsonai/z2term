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
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.settings.WhenRule
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                fired.forEach { f ->
                    Text(
                        // 日付は落として時刻だけ (幅が限られるので、直近を読むのに要るのは時刻)。
                        text = "${shortTime(f.time)}  ${f.trigger}  ${f.status}",
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
                Text(
                    text = rule.trigger,
                    color = if (rule.enabled) ZtsTextPrimary else ZtsTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
