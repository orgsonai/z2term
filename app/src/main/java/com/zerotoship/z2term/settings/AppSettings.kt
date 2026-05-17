package com.zerotoship.z2term.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * アプリ設定 (テーマ・フォント・スクロールバック行数) の DataStore ラッパー。
 *
 * シングルトンとして `Context.appSettings` でアクセス。
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "z2term_settings")

class AppSettings(private val context: Context) {

    data class Snapshot(
        val themeName: String = DEFAULT_THEME,
        val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
        val scrollbackLines: Int = DEFAULT_SCROLLBACK_LINES,
        val distroId: String = DEFAULT_DISTRO
    )

    val flow: Flow<Snapshot> = context.dataStore.data.map { p ->
        Snapshot(
            themeName = p[KEY_THEME_NAME] ?: DEFAULT_THEME,
            fontSizeSp = p[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE_SP,
            scrollbackLines = p[KEY_SCROLLBACK] ?: DEFAULT_SCROLLBACK_LINES,
            distroId = p[KEY_DISTRO_ID] ?: DEFAULT_DISTRO
        )
    }

    suspend fun setDistro(id: String) {
        context.dataStore.edit { it[KEY_DISTRO_ID] = id }
    }

    suspend fun setTheme(name: String) {
        context.dataStore.edit { it[KEY_THEME_NAME] = name }
    }

    suspend fun setFontSize(sp: Float) {
        context.dataStore.edit { it[KEY_FONT_SIZE] = sp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP) }
    }

    suspend fun setScrollbackLines(lines: Int) {
        context.dataStore.edit {
            it[KEY_SCROLLBACK] = lines.coerceIn(MIN_SCROLLBACK_LINES, MAX_SCROLLBACK_LINES)
        }
    }

    companion object {
        const val DEFAULT_THEME = "ZTS Theme"
        const val DEFAULT_FONT_SIZE_SP = 13f
        const val DEFAULT_SCROLLBACK_LINES = 5000
        const val DEFAULT_DISTRO = "alpine"

        const val MIN_FONT_SIZE_SP = 8f
        const val MAX_FONT_SIZE_SP = 32f
        const val MIN_SCROLLBACK_LINES = 500
        const val MAX_SCROLLBACK_LINES = 50000

        private val KEY_THEME_NAME = stringPreferencesKey("theme_name")
        private val KEY_FONT_SIZE = floatPreferencesKey("font_size_sp")
        private val KEY_SCROLLBACK = intPreferencesKey("scrollback_lines")
        private val KEY_DISTRO_ID = stringPreferencesKey("distro_id")
    }
}
