package com.zerotoship.z2term.widget

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * ウィジェットの設定画面で共通に使う部品 ([WidgetConfigActivity] = マクロ / [TailConfigActivity] = ライブ tail)。
 *
 * 1.0.56: ホーム画面のウィジェット本体と同じく **Android 標準の見た目**にした。アプリ本体の ZTS
 * ダーク・等幅ではなく、端末のライト / ダークと配色 (API 31+ は壁紙から作られる色) に合わせた
 * Material 3 の部品 (上のバー・チェックボックスの一覧・段切り替えのボタン・下のバー) で組む。
 * 1.0.55 までは `[x]` の文字と枠線のボタンを等幅で並べていた。
 */

/** API 29〜30 で使う色 (ウィジェットの `res/values{,-night}/widget_colors.xml` と同じ緑系)。 */
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF1B6D3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0C8),
    onPrimaryContainer = Color(0xFF00391B),
    secondaryContainer = Color(0xFFE3EAE0),
    onSecondaryContainer = Color(0xFF191C19),
    background = Color(0xFFF6F8F3),
    surface = Color(0xFFF6F8F3),
    onSurface = Color(0xFF191C19),
    onSurfaceVariant = Color(0xFF414941),
    outlineVariant = Color(0xFFC1C9BE),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFF86D99E),
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF0F5229),
    onPrimaryContainer = Color(0xFFB9F0C8),
    secondaryContainer = Color(0xFF2C332D),
    onSecondaryContainer = Color(0xFFE1E3DE),
    background = Color(0xFF1A1C1A),
    surface = Color(0xFF1A1C1A),
    onSurface = Color(0xFFE1E3DE),
    onSurfaceVariant = Color(0xFFC0C9BD),
    outlineVariant = Color(0xFF414941),
)

/** 端末のライト / ダークと配色に合わせたテーマ。アプリの選択中テーマには追従しない (ウィジェット側と同じ)。 */
@Composable
internal fun WidgetConfigTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) FallbackDark else FallbackLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * 設定画面の枠: 上のバー (題名と ✕) ・スクロールする中身・下のバー (保存 / キャンセル)。
 *
 * `Scaffold` がステータスバーとナビゲーションバーの分を空けるので、Android 15 の
 * edge-to-edge 強制でも上下が隠れない (下のバーは自分で `navigationBarsPadding`)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigScaffold(
    title: String,
    onClose: () -> Unit,
    closeDescription: String,
    bottomBar: @Composable RowScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = closeDescription)
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = bottomBar,
                )
            }
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + 8.dp,
            ),
            content = content,
        )
    }
}

/** 段落の説明文 (画面の頭や、選択肢の下の補足)。 */
@Composable
internal fun ConfigNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** 区切りの見出し。上に区切り線を引いて、段のまとまりを分ける。 */
@Composable
internal fun ConfigSection(text: String, divider: Boolean = true) {
    if (divider) HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * 1 つだけ選ぶ段切り替え (文字の大きさ・末尾 / 先頭)。[labels] の順に並べ、[selected] 番目を選択中にする。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigSegmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

/** 文字の大きさの段切り替え。既定の値には「既定」を添える (戻したくなったとき迷わない)。 */
@Composable
internal fun ConfigTextSize(
    options: List<Int>,
    selected: Int,
    defaultSp: Int,
    defaultLabel: String,
    onSelect: (Int) -> Unit,
) {
    ConfigSegmented(
        labels = options.map { "$it" },
        selected = options.indexOf(selected),
        onSelect = { onSelect(options[it]) },
    )
    ConfigNote("$defaultLabel: $defaultSp", modifier = Modifier.padding(top = 4.dp))
}
