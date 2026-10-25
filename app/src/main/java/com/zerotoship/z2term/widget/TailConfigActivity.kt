package com.zerotoship.z2term.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.settings.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ライブ tail ウィジェット (D2) の設定画面。**見るファイルを決めるだけ。**
 *
 * 0.8.219 までは `~` 配下を機械的に走査した候補を 60 件並べていたが、**数が多すぎて選べない**
 * と実機で指摘された (2026-07-25)。いまは
 *  - 上のパス欄に**自分で打つ**（`~/.z2term/events.jsonl` のように）
 *  - 下の一覧で**フォルダを辿って選ぶ**（1 階層ずつ）
 * の 2 通り。行数は保存しない（ウィジェットの高さから自動で決まる）。
 */
class TailConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // D1 と同じ。これが無いとステータスバー / ナビゲーションバーの下に潜り込む。
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val initial = TailStore.path(this, appWidgetId)
        val initialMode = TailStore.mode(this, appWidgetId)

        setContent {
            WidgetConfigTheme {
                TailConfigScreen(
                    initialPath = initial,
                    initialMode = initialMode,
                    initialTextSp = TailStore.textSp(this, appWidgetId),
                    list = { dir -> TailStore.list(this, dir) },
                    isFile = { p -> TailStore.resolve(this, p)?.isFile == true },
                    onSave = { path, mode, sp -> save(path, mode, sp) },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun save(path: String, mode: TailStore.Mode, textSp: Int) {
        TailStore.set(this, appWidgetId, path)
        TailStore.setMode(this, appWidgetId, mode)
        TailStore.setTextSp(this, appWidgetId, textSp)
        val id = appWidgetId
        // 置かれた直後は OS の更新が来ないので、自分で 1 回描く。
        Thread { runCatching { TailWidgetProvider.renderAll(applicationContext) } }
            .apply { isDaemon = true; start() }
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}

@Composable
private fun TailConfigScreen(
    initialPath: String?,
    initialMode: TailStore.Mode,
    initialTextSp: Int,
    list: (String) -> List<TailStore.Entry>,
    isFile: (String) -> Boolean,
    onSave: (String, TailStore.Mode, Int) -> Unit,
    onCancel: () -> Unit,
) {
    var path by remember { mutableStateOf(initialPath.orEmpty()) }
    var mode by remember { mutableStateOf(initialMode) }
    var textSp by remember { mutableStateOf(initialTextSp) }
    // いま開いているフォルダ (`~` からの相対。空なら `~` 自身)。
    var dir by remember {
        mutableStateOf(initialPath?.let { TailStore.parentOf(it) }.orEmpty())
    }
    val entries = remember(dir) { list(dir) }
    val stamp = SimpleDateFormat("MM/dd HH:mm", Locale.US)
    val canSave = path.isNotBlank() && isFile(path)
    val modes = listOf(TailStore.Mode.TAIL, TailStore.Mode.HEAD)

    ConfigScaffold(
        title = stringResource(R.string.tail_config_title),
        onClose = onCancel,
        closeDescription = stringResource(R.string.widget_config_cancel),
        bottomBar = {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.widget_config_cancel)) }
            // 保存できない理由は入力欄の下に出す (押しても何も起きない、を作らない)。
            Button(onClick = { onSave(path.trim(), mode, textSp) }, enabled = canSave) {
                Text(stringResource(R.string.widget_config_save))
            }
        },
    ) {
        item { ConfigNote(stringResource(R.string.tail_config_desc)) }

        // 自分で打つ口。フォルダを辿って選んだときもここに入るので、いつでも直せる。
        item {
            val invalid = path.isNotBlank() && !canSave
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                singleLine = true,
                isError = invalid,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                supportingText = {
                    Text(
                        stringResource(if (invalid) R.string.tail_not_a_file else R.string.tail_path_hint)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ファイルのどちら側を出すか。増え続けるログなら末尾、書き終わったファイルなら先頭。
        item { ConfigSection(stringResource(R.string.tail_mode_label)) }
        item {
            ConfigSegmented(
                labels = listOf(
                    stringResource(R.string.tail_mode_tail),
                    stringResource(R.string.tail_mode_head),
                ),
                selected = modes.indexOf(mode),
                onSelect = { mode = modes[it] },
            )
            ConfigNote(
                stringResource(
                    if (mode == TailStore.Mode.TAIL) R.string.tail_mode_tail_desc else R.string.tail_mode_head_desc
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // 文字の大きさ (0.8.255)。段を並べる — スライダーだと 1sp 刻みで迷わせるだけで、
        // ウィジェットの文字は数段階あれば足りる。
        item { ConfigSection(stringResource(R.string.tail_text_size_label)) }
        item {
            ConfigTextSize(
                options = listOf(8, 10, 12, 14, 17, 20),
                selected = textSp,
                defaultSp = TailStore.DEFAULT_TEXT_SP,
                defaultLabel = stringResource(R.string.tail_text_size_default),
                onSelect = { textSp = it },
            )
            ConfigNote(stringResource(R.string.tail_text_size_desc))
        }

        // いま開いているフォルダと、その中身。
        item { ConfigSection("~/$dir") }
        TailStore.parentOf(dir)?.let { up ->
            item(key = "..") {
                ListItem(
                    headlineContent = { Text("../") },
                    leadingContent = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                    modifier = Modifier.clickable { dir = up },
                )
            }
        }
        if (entries.isEmpty()) {
            item { ConfigNote(stringResource(R.string.tail_dir_empty)) }
        }
        items(entries, key = { it.relPath }) { e ->
            val picked = !e.isDir && e.relPath == path
            ListItem(
                headlineContent = { Text(if (e.isDir) e.name + "/" else e.name) },
                supportingContent = if (e.isDir) null else {
                    {
                        Text(
                            stringResource(
                                R.string.tail_file_meta, stamp.format(Date(e.modified)), e.size / 1024
                            )
                        )
                    }
                },
                leadingContent = {
                    Icon(
                        if (e.isDir) Icons.Outlined.Folder else Icons.Outlined.Description,
                        contentDescription = null,
                    )
                },
                trailingContent = if (picked) {
                    { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else null,
                colors = if (picked) {
                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else ListItemDefaults.colors(),
                modifier = Modifier.clickable { if (e.isDir) dir = e.relPath else path = e.relPath },
            )
        }
    }
}
