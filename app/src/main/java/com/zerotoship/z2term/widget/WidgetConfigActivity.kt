package com.zerotoship.z2term.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.settings.LocaleHelper

/**
 * マクロウィジェット (マス目型 [StatusWidgetProvider] / 一覧型 [MacroListWidgetProvider]) の
 * 設定画面。ウィジェットを置いたときにランチャーから呼ばれ、**どのマクロを並べるか**と
 * 文字の大きさを選ばせる。どちらの種類かは appWidgetId から判別し、選べる数だけが変わる
 * (マス目は 4 個、一覧は [WidgetStore.MAX_LIST_MACROS] 個)。
 *
 * 候補は `~/.z2term/macros/` 配下の `.sh` の実ファイル。ここで新しいマクロを作らせることはしない
 * (マクロはターミナルで書く/`z2-macro install` で入れるもので、正本はファイル側にある)。
 *
 * API 31+ では `configuration_optional` を付けてあるので、この画面を出さずに置いても
 * ウィジェットは動く (その場合はマクロディレクトリの先頭から上限まで並ぶ)。
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    /** このウィジェットに並べられる数 (マス目 4 / 一覧 [WidgetStore.MAX_LIST_MACROS])。 */
    private var maxMacros = WidgetStore.MAX_MACROS

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MainActivity / GuiActivity と同じく edge-to-edge にする。どの API でも同じ前提になるので、
        // 中身の `windowInsetsPadding(systemBars)` が二重padding にならない。
        enableEdgeToEdge()
        // 途中でやめた (戻る) ときはウィジェットを置かない、が Android の作法。
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val provider = runCatching {
            AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className
        }.getOrNull()
        maxMacros = if (provider == MacroListWidgetProvider::class.java.name) {
            WidgetStore.MAX_LIST_MACROS
        } else {
            WidgetStore.MAX_MACROS
        }

        val available = WidgetStore.availableMacros(this)
        val initial = WidgetStore.macros(this, appWidgetId, maxMacros)
        // ファイル名だけでは何のマクロか分からないので、スクリプト先頭のコメントを説明として出す。
        val descriptions = available.associateWith { WidgetStore.description(this, it) }

        setContent {
            WidgetConfigTheme {
                ConfigScreen(
                    available = available,
                    descriptions = descriptions,
                    initial = initial,
                    maxMacros = maxMacros,
                    initialTextSp = WidgetStore.textSp(this, appWidgetId),
                    onSave = { selected, sp -> save(selected, sp) },
                    onCancel = { finish() },
                    onResetHistory = { resetHistory() },
                )
            }
        }
    }

    /**
     * ボタンの `✓` と時刻をいま消す。保存 (「保存」ボタン) とは独立に**その場で効く**
     * — 見えている印を消すのが目的なので、保存を待たせると何も起きていないように見える。
     */
    private fun resetHistory() {
        WidgetStore.clearRunHistory(this)
        Thread { MacroWidgets.renderEverything(applicationContext) }
            .apply { isDaemon = true; start() }
        Toast.makeText(this, R.string.widget_config_reset_done, Toast.LENGTH_SHORT).show()
    }

    private fun save(selected: List<String>, textSp: Int) {
        WidgetStore.setMacros(this, appWidgetId, selected, maxMacros)
        WidgetStore.setTextSp(this, appWidgetId, textSp)
        // 置かれた直後は OS の更新が来ないので、自分で 1 回描く。
        val id = appWidgetId
        Thread { MacroWidgets.renderEverything(applicationContext) }
            .apply { isDaemon = true; start() }
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        )
        finish()
    }
}

@Composable
private fun ConfigScreen(
    available: List<String>,
    descriptions: Map<String, String>,
    initial: List<String>,
    maxMacros: Int,
    initialTextSp: Int,
    onSave: (List<String>, Int) -> Unit,
    onCancel: () -> Unit,
    onResetHistory: () -> Unit,
) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var textSp by remember { mutableStateOf(initialTextSp) }

    ConfigScaffold(
        title = stringResource(R.string.widget_config_title),
        onClose = onCancel,
        closeDescription = stringResource(R.string.widget_config_cancel),
        bottomBar = {
            // いくつ選んだかを保存の横に出す (上限に達すると未選択の行が押せなくなる理由が分かる)。
            Text(
                text = "${selected.size} / $maxMacros",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.widget_config_cancel)) }
            Button(onClick = { onSave(selected.toList(), textSp) }) {
                Text(stringResource(R.string.widget_config_save))
            }
        },
    ) {
        item { ConfigNote(stringResource(R.string.widget_config_desc, maxMacros)) }

        if (available.isEmpty()) {
            item {
                ConfigNote(
                    stringResource(R.string.widget_config_empty),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            item { Spacer(Modifier.padding(top = 4.dp)) }
            items(available, key = { it }) { name ->
                val checked = name in selected
                // 上限に達していたら未選択の行は押せない (先に外してもらう)。
                val enabled = checked || selected.size < maxMacros
                val desc = descriptions[name].orEmpty()
                ListItem(
                    headlineContent = { Text(WidgetStore.label(name)) },
                    supportingContent = {
                        Text(desc.ifBlank { stringResource(R.string.widget_config_no_desc) })
                    },
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
                    modifier = Modifier
                        .alpha(if (enabled) 1f else 0.5f)
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { if (checked) selected.remove(name) else selected.add(name) },
                        ),
                )
            }
        }

        // 「一覧に無いマクロをどう足すのか分からない」という実機フィードバックへの対応。
        // 一覧が空のときだけでなく**常に**出す (足し方はここでしか分からない)。
        item { ConfigNote(stringResource(R.string.widget_config_add_hint)) }

        // 文字の大きさ (0.8.255)。ここで選んだ差分が、見出し・状態・マス・フッターに**まとめて**効く。
        item { ConfigSection(stringResource(R.string.tail_text_size_label)) }
        item {
            ConfigTextSize(
                options = listOf(9, 11, 13, 15, 17, 20),
                selected = textSp,
                defaultSp = WidgetStore.DEFAULT_TEXT_SP,
                defaultLabel = stringResource(R.string.tail_text_size_default),
                onSelect = { textSp = it },
            )
        }

        // 実行履歴 (マクロの結果と時刻) のリセット。日付が変われば自動で消えるが、
        // 「いま消したい」に応える出口をここに置く — ウィジェットを置き直しても消えず、
        // アプリのデータ削除しか手が無かった (実機フィードバック 2026-07-25)。
        item { ConfigSection(stringResource(R.string.widget_config_reset)) }
        item { ConfigNote(stringResource(R.string.widget_config_reset_desc)) }
        item {
            OutlinedButton(
                onClick = onResetHistory,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.widget_config_reset)) }
        }
    }
}
