package com.zerotoship.z2term.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBorder

/**
 * 編集画面の「やめる / 保存」を、スクロールの外 (シートの一番下) に固定するための受け皿 (1.0.79)。
 *
 * 接続先・サーバー・自動化ルールの編集は、スクロールする一覧の中に入力欄を並べている。
 * ボタンが入力欄の後ろにあると、項目の多い接続先などでは毎回いちばん下までスクロールしないと
 * 押せなかった。シートが [StickyFooterHost] を用意し、スクロール領域の下に [StickyFooterSlot]
 * を置く。中身の側は [StickyFooter] で包むだけで、そこへ移る。
 *
 * 受け皿が無い場所で [StickyFooter] を使ったときは、その場にそのまま出す (従来どおり)。
 */
class StickyFooterHost {
    internal var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

val LocalStickyFooterHost = staticCompositionLocalOf<StickyFooterHost?> { null }

@Composable
fun rememberStickyFooterHost(): StickyFooterHost = remember { StickyFooterHost() }

/** [host] を子に渡す。スクロール領域とその下の [StickyFooterSlot] を包む。 */
@Composable
fun ProvideStickyFooter(host: StickyFooterHost, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStickyFooterHost provides host, content = content)
}

/** 受け皿があればシート下端へ、無ければその場に [content] を出す。 */
@Composable
fun StickyFooter(content: @Composable () -> Unit) {
    val host = LocalStickyFooterHost.current
    if (host == null) {
        content()
        return
    }
    val latest by rememberUpdatedState(content)
    DisposableEffect(host) {
        val slot: @Composable () -> Unit = { latest() }
        host.content = slot
        onDispose { if (host.content === slot) host.content = null }
    }
}

/** シート下端の固定欄。登録された中身が無いとき (一覧表示中) は何も出さない。 */
@Composable
fun StickyFooterSlot(host: StickyFooterHost, modifier: Modifier = Modifier) {
    val content = host.content ?: return
    Column(modifier.fillMaxWidth().background(ZtsBgPrimary)) {
        HorizontalDivider(color = ZtsBorder, thickness = 1.dp)
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            content()
        }
    }
}
