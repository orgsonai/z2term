package com.zerotoship.z2term.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.ui.theme.Z2TermTheme
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
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

        CustomThemeStore.ensureLoaded(applicationContext)
        val initial = TailStore.path(this, appWidgetId)
        val initialMode = TailStore.mode(this, appWidgetId)

        setContent {
            Z2TermTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = ZtsBgPrimary) {
                    TailConfigScreen(
                        initialPath = initial,
                        initialMode = initialMode,
                        list = { dir -> TailStore.list(this, dir) },
                        isFile = { p -> TailStore.resolve(this, p)?.isFile == true },
                        onSave = { path, mode -> save(path, mode) },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun save(path: String, mode: TailStore.Mode) {
        TailStore.set(this, appWidgetId, path)
        TailStore.setMode(this, appWidgetId, mode)
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
    list: (String) -> List<TailStore.Entry>,
    isFile: (String) -> Boolean,
    onSave: (String, TailStore.Mode) -> Unit,
    onCancel: () -> Unit,
) {
    var path by remember { mutableStateOf(initialPath.orEmpty()) }
    var mode by remember { mutableStateOf(initialMode) }
    // いま開いているフォルダ (`~` からの相対。空なら `~` 自身)。
    var dir by remember {
        mutableStateOf(initialPath?.let { TailStore.parentOf(it) }.orEmpty())
    }
    val entries = remember(dir) { list(dir) }
    val stamp = SimpleDateFormat("MM/dd HH:mm", Locale.US)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.tail_config_title),
            color = ZtsTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = stringResource(R.string.tail_config_desc),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        // 自分で打つ口。フォルダを辿って選んだときもここに入るので、いつでも直せる。
        BasicTextField(
            value = path,
            onValueChange = { path = it },
            singleLine = true,
            textStyle = TextStyle(
                color = ZtsTextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(ZtsGreen),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        )
        Text(
            text = stringResource(R.string.tail_path_hint),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        // ファイルのどちら側を出すか。増え続けるログなら末尾、書き終わったファイルなら先頭。
        Text(
            text = stringResource(R.string.tail_mode_label),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfigSelectRow(
                title = stringResource(R.string.tail_mode_tail),
                subtitle = stringResource(R.string.tail_mode_tail_desc),
                checked = mode == TailStore.Mode.TAIL,
                modifier = Modifier.weight(1f),
                onToggle = { mode = TailStore.Mode.TAIL }
            )
            ConfigSelectRow(
                title = stringResource(R.string.tail_mode_head),
                subtitle = stringResource(R.string.tail_mode_head_desc),
                checked = mode == TailStore.Mode.HEAD,
                modifier = Modifier.weight(1f),
                onToggle = { mode = TailStore.Mode.HEAD }
            )
        }

        // いま開いているフォルダ。
        Text(
            text = "~/" + dir,
            color = ZtsGreen,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TailStore.parentOf(dir)?.let { up ->
                ConfigSelectRow(
                    title = "../",
                    subtitle = "",
                    checked = false,
                    onToggle = { dir = up }
                )
            }
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.tail_dir_empty),
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            entries.forEach { e ->
                ConfigSelectRow(
                    title = if (e.isDir) e.name + "/" else e.name,
                    subtitle = if (e.isDir) "" else
                        stringResource(R.string.tail_file_meta, stamp.format(Date(e.modified)), e.size / 1024),
                    checked = !e.isDir && e.relPath == path,
                    onToggle = { if (e.isDir) dir = e.relPath else path = e.relPath }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfigButton(
                label = stringResource(R.string.widget_config_save),
                accent = true,
                onClick = { if (path.isNotBlank() && isFile(path)) onSave(path.trim(), mode) }
            )
            ConfigButton(
                label = stringResource(R.string.widget_config_cancel),
                onClick = onCancel
            )
        }
        // 保存できない理由をその場で出す (押しても何も起きない、を作らない)。
        if (path.isNotBlank() && !isFile(path)) {
            Text(
                text = stringResource(R.string.tail_not_a_file),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
