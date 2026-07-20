package com.zerotoship.z2term.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.settingsGroupDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "z2term_settings_groups")

/**
 * 設定ページのグループ (アコーディオン) 開閉状態の永続化 + プロセス共有ホルダ。
 *
 * グループごとに `settings_group_open_<id>` という**固定キー 1 本**で持つ。1 グループ =
 * 1 キーなので、後からグループを増減しても既存の開閉状態が壊れない (未知のキーは無視され、
 * 保存の無いグループは [SettingsGroup.defaultOpen] にフォールバックする)。
 *
 * [ensureLoaded] を設定ページ表示時に呼ぶと DataStore から読み込み [openState] に反映する。
 */
object SettingsGroupStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun key(id: String) = booleanPreferencesKey("settings_group_open_$id")

    /** グループ id -> 開いているか。保存が無い id は入らない (既定値で解決する)。 */
    private val _openState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val openState: StateFlow<Map<String, Boolean>> = _openState.asStateFlow()

    @Volatile private var appContext: Context? = null
    @Volatile private var loaded = false

    /** DataStore から 1 度だけ読み込む (多重呼び出しは無視)。 */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val ctx = context.applicationContext
        appContext = ctx
        scope.launch {
            val prefs = ctx.settingsGroupDataStore.data.first()
            _openState.value = SettingsGroup.ALL.mapNotNull { g ->
                prefs[key(g.id)]?.let { g.id to it }
            }.toMap()
        }
    }

    /** 保存が無ければグループごとの既定値を返す。 */
    fun isOpen(group: SettingsGroup): Boolean = _openState.value[group.id] ?: group.defaultOpen

    /** 開閉を切り替えて永続化する ([openState] へは即反映)。 */
    fun setOpen(group: SettingsGroup, open: Boolean) {
        _openState.value = _openState.value + (group.id to open)
        val ctx = appContext ?: return
        scope.launch { ctx.settingsGroupDataStore.edit { it[key(group.id)] = open } }
    }
}
