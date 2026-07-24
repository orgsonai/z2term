package com.zerotoship.z2term.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/**
 * ホーム画面ウィジェット (D1) の設定画面。ウィジェットを置いたときにランチャーから呼ばれ、
 * **どのマクロをボタンに並べるか**だけを選ばせる。
 *
 * 候補は `~/.z2term/macros/` 配下の `.sh` の実ファイル。ここで新しいマクロを作らせることはしない
 * (マクロはターミナルで書く/`z2-macro install` で入れるもので、正本はファイル側にある)。
 *
 * API 31+ では `configuration_optional` を付けてあるので、この画面を出さずに置いても
 * ウィジェットは動く (その場合はマクロディレクトリの先頭 4 件が並ぶ)。
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 途中でやめた (戻る) ときはウィジェットを置かない、が Android の作法。
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        CustomThemeStore.ensureLoaded(applicationContext)

        val available = WidgetStore.availableMacros(this)
        val initial = WidgetStore.macros(this, appWidgetId)

        setContent {
            Z2TermTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = ZtsBgPrimary) {
                    ConfigScreen(
                        available = available,
                        initial = initial,
                        onSave = { selected -> save(selected) },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun save(selected: List<String>) {
        WidgetStore.setMacros(this, appWidgetId, selected)
        // 置かれた直後は OS の更新が来ないので、自分で 1 回描く。
        val id = appWidgetId
        Thread { runCatching { StatusWidgetProvider.renderAll(applicationContext) } }
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
    initial: List<String>,
    onSave: (List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.widget_config_title),
            color = ZtsTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = stringResource(R.string.widget_config_desc, WidgetStore.MAX_MACROS),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        if (available.isEmpty()) {
            Text(
                text = stringResource(R.string.widget_config_empty),
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
                available.forEach { name ->
                    val checked = name in selected
                    MacroRow(
                        name = name,
                        checked = checked,
                        // 上限に達していたら未選択の行は押せない (先に外してもらう)。
                        enabled = checked || selected.size < WidgetStore.MAX_MACROS,
                        onToggle = {
                            if (checked) selected.remove(name) else selected.add(name)
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfigButton(
                label = stringResource(R.string.widget_config_save),
                accent = true,
                onClick = { onSave(selected.toList()) }
            )
            ConfigButton(
                label = stringResource(R.string.widget_config_cancel),
                onClick = onCancel
            )
        }
    }
}

@Composable
private fun MacroRow(
    name: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val fg = when {
        checked -> ZtsGreen
        enabled -> ZtsTextPrimary
        else -> ZtsTextSecondary.copy(alpha = 0.5f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, if (checked) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (checked) "[x]" else "[ ]",
            color = fg,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = name,
            color = fg,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ConfigButton(
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, if (accent) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = if (accent) ZtsGreen else ZtsTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
