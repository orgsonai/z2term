package com.zerotoship.z2term.ui.gui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.gui.GuiApp
import com.zerotoship.z2term.gui.GuiSession
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.launch

/**
 * ☰ アプリ一覧シート（0.8.499）。
 *
 * distro に**実際に入っている** GUI アプリを並べ、タップで起こす。一覧の中身は
 * `z2menu list` が決める（[com.zerotoship.z2term.gui.GuiAppCatalog]）ので、ここは
 * 「並べて、選ばせて、[GuiSession.launchApp] に渡す」だけを持つ。
 *
 * ⭐ **なぜ GUI の中のパネルではなくここに置くか**: デスクトップ内にパネル（lxpanel 等）を
 * 立てると、PC 用の寸法のボタンがスマホの画面に出て**指より小さくなる**。加えて distro ごとの
 * パッケージと設定を z2term が抱えることになる。ネイティブのシートなら、追加パッケージ無しで
 * どの distro でも同じように出て、指で押せる大きさになる。
 *
 * ⚠ 窓の切り替えはここには無い。X の窓一覧を取るには `wmctrl` / `xdotool` が要り、
 * **どの distro でも導入対象に入っていない**ため。デスクトップ長押し →「窓」
 * （openbox 内蔵の `client-list-menu`）が担当する。下の案内はそのための一文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuiAppsSheet(
    session: GuiSession,
    onDismiss: () -> Unit,
) {
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

    val apps by session.apps.collectAsState()
    val loading by session.appsLoading.collectAsState()
    var query by remember { mutableStateOf("") }

    // ⚠ 開くたびに取り直す。パッケージを入れた直後に出ないと「入れたのに一覧に無い」で詰まる。
    LaunchedEffect(Unit) { session.refreshApps() }

    val shown = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter {
            it.name.lowercase().contains(q) ||
                it.comment.lowercase().contains(q) ||
                it.exec.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
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
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.gui_apps_title),
                    color = ZtsGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = if (loading) stringResource(R.string.gui_apps_loading)
                           else stringResource(R.string.gui_apps_count, apps.size),
                    color = ZtsTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // 検索。⚠ 入っているアプリが数百になる distro があるので、一覧だけでは辿れない。
            if (apps.size > 8) {
                SearchField(query = query, onChange = { query = it })
            }

            if (apps.isEmpty()) {
                EmptyState(loading = loading)
            } else {
                shown.forEach { app ->
                    key(app.name, app.exec) {
                        AppRow(app = app, onSelect = { session.launchApp(app); closeSheet() })
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.gui_apps_no_match, query),
                        color = ZtsTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.gui_apps_window_hint),
                color = ZtsTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🔍", fontSize = 14.sp)
        Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.gui_apps_search_hint),
                    color = ZtsTextSecondary,
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = ZtsTextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(ZtsGreen),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AppRow(app: GuiApp, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                color = ZtsTextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 説明が無い .desktop も多いので、そのときはコマンドを出す (何が起きるかは示す)。
            Text(
                text = app.comment.ifBlank { app.exec },
                color = ZtsTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (app.terminal) {
            // 端末の中で開くアプリ (vim 等)。押した先が「窓ではなく端末」だと分かるようにする。
            Text(
                text = stringResource(R.string.gui_apps_terminal_badge),
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ZtsBgSecondary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(loading: Boolean) {
    Text(
        text = stringResource(
            if (loading) R.string.gui_apps_searching else R.string.gui_apps_empty
        ),
        color = ZtsTextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}
