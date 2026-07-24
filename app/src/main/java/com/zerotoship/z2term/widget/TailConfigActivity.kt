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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.ui.theme.Z2TermTheme
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ライブ tail ウィジェット (D2) の設定画面。**どのファイルを何行出すか**だけを選ばせる。
 *
 * 候補は `~/` 配下のログらしいファイル ([TailStore.candidates])。ここでファイルを作らせることは
 * しない (ログはマクロ・`z2-when`・セッション記録が書くもので、正本はファイル側にある)。
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

        val home = TailStore.home(this)
        val candidates = TailStore.candidates(this)
        // 「いつの・どれくらいの大きさのファイルか」が分かると選びやすい。
        val stamp = SimpleDateFormat("MM/dd HH:mm", Locale.US)
        val subtitles = candidates.associateWith { rel ->
            val f = File(home, rel)
            getString(R.string.tail_file_meta, stamp.format(Date(f.lastModified())), f.length() / 1024)
        }

        setContent {
            Z2TermTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = ZtsBgPrimary) {
                    TailConfigScreen(
                        candidates = candidates,
                        subtitles = subtitles,
                        initialPath = TailStore.path(this, appWidgetId),
                        initialLines = TailStore.lines(this, appWidgetId),
                        onSave = { path, lines -> save(path, lines) },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun save(path: String, lines: Int) {
        TailStore.set(this, appWidgetId, path, lines)
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
    candidates: List<String>,
    subtitles: Map<String, String>,
    initialPath: String?,
    initialLines: Int,
    onSave: (String, Int) -> Unit,
    onCancel: () -> Unit,
) {
    var path by remember { mutableStateOf(initialPath ?: candidates.firstOrNull()) }
    var lines by remember { mutableIntStateOf(initialLines) }

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

        if (candidates.isEmpty()) {
            Text(
                text = stringResource(R.string.tail_config_empty),
                color = ZtsTextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                candidates.forEach { rel ->
                    ConfigSelectRow(
                        title = rel,
                        subtitle = subtitles[rel].orEmpty(),
                        checked = rel == path,
                        // ファイルは 1 つだけ選ぶ (ラジオ相当)。
                        onToggle = { path = rel }
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.tail_lines_label),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TailStore.LINE_CHOICES.forEach { n ->
                ConfigButton(
                    label = n.toString(),
                    accent = n == lines,
                    onClick = { lines = n }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfigButton(
                label = stringResource(R.string.widget_config_save),
                accent = true,
                onClick = { path?.let { onSave(it, lines) } }
            )
            ConfigButton(
                label = stringResource(R.string.widget_config_cancel),
                onClick = onCancel
            )
        }
    }
}
