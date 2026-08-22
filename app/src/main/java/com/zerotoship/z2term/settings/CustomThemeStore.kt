package com.zerotoship.z2term.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zerotoship.z2term.emulator.TerminalTheme
import com.zerotoship.z2term.emulator.terminalThemeFromJson
import com.zerotoship.z2term.emulator.toJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

private val Context.customThemeDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "z2term_custom_theme")

/**
 * ユーザー独自テーマ ([TerminalTheme]) の永続化 + プロセス共有ホルダ。
 *
 * 1 件のみ保持する (null = 未作成)。`TerminalSession` (エミュレータ配色)、UI の
 * `AppColors`、設定のテーマ一覧がすべてこの [theme] StateFlow を購読して一致させる。
 *
 * [ensureLoaded] を起動時に呼ぶと DataStore から読み込み [theme] に反映する。
 */
object CustomThemeStore {
    private val KEY = stringPreferencesKey("custom_theme")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _theme = MutableStateFlow<TerminalTheme?>(null)
    val theme: StateFlow<TerminalTheme?> = _theme.asStateFlow()

    @Volatile private var appContext: Context? = null
    @Volatile private var loaded = false

    /** DataStore から 1 度だけ読み込む (多重呼び出しは無視)。 */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val ctx = context.applicationContext
        appContext = ctx
        scope.launch {
            val raw = ctx.customThemeDataStore.data.first()[KEY]
            _theme.value = raw?.let {
                runCatching { terminalThemeFromJson(JSONObject(it)) }.getOrNull()
            }
        }
    }

    /**
     * 持ち出し用: 保存してあるテーマの JSON (未作成なら空文字)。
     *
     * ⚠ [AppSettings] とは**別の DataStore** なので、設定の持ち出しには乗ってこない。
     * 手で作った配色は入れ直しでは戻らないものの筆頭なので、別のファイルとして運ぶ。
     */
    suspend fun exportRaw(context: Context): String =
        context.applicationContext.customThemeDataStore.data.first()[KEY].orEmpty()

    /**
     * 持ち出しから戻す。⚠ **読めない / 空のときは何もしない** — バックアップにテーマが
     * 無いというだけで、いま使っているテーマを消してしまわないため。
     */
    suspend fun importRaw(context: Context, raw: String) {
        val parsed = runCatching { terminalThemeFromJson(JSONObject(raw)) }.getOrNull() ?: return
        val ctx = context.applicationContext
        appContext = ctx
        ctx.customThemeDataStore.edit { it[KEY] = raw }
        // 画面はこの StateFlow を見ているので、ここで入れれば戻した瞬間に配色が変わる。
        _theme.value = parsed
    }

    /** 保存 (null で削除)。永続化後に [theme] へ即反映。 */
    suspend fun save(theme: TerminalTheme?) {
        val ctx = appContext ?: return
        ctx.customThemeDataStore.edit { p ->
            if (theme == null) p.remove(KEY) else p[KEY] = theme.toJson().toString()
        }
        _theme.value = theme
    }
}
