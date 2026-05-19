package com.zerotoship.z2term.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        val distroId: String = DEFAULT_DISTRO,
        val fontId: String = DEFAULT_FONT,
        val ambiguousAsWide: Boolean = DEFAULT_AMBIGUOUS_AS_WIDE,
        val initCommand: String = "",
        val keyboardStyleId: String = DEFAULT_KEYBOARD_STYLE,
        val loginShell: String = DEFAULT_LOGIN_SHELL
    )

    val flow: Flow<Snapshot> = context.dataStore.data.map { p ->
        Snapshot(
            themeName = p[KEY_THEME_NAME] ?: DEFAULT_THEME,
            fontSizeSp = p[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE_SP,
            scrollbackLines = p[KEY_SCROLLBACK] ?: DEFAULT_SCROLLBACK_LINES,
            distroId = p[KEY_DISTRO_ID] ?: DEFAULT_DISTRO,
            fontId = p[KEY_FONT_ID] ?: DEFAULT_FONT,
            ambiguousAsWide = p[KEY_AMBIGUOUS_WIDE] ?: DEFAULT_AMBIGUOUS_AS_WIDE,
            initCommand = p[KEY_INIT_COMMAND] ?: "",
            keyboardStyleId = p[KEY_KEYBOARD_STYLE] ?: DEFAULT_KEYBOARD_STYLE,
            loginShell = p[KEY_LOGIN_SHELL] ?: DEFAULT_LOGIN_SHELL
        )
    }

    suspend fun setKeyboardStyleId(id: String) {
        context.dataStore.edit { it[KEY_KEYBOARD_STYLE] = id }
    }

    suspend fun setLoginShell(shell: String) {
        context.dataStore.edit { it[KEY_LOGIN_SHELL] = shell }
    }

    suspend fun setInitCommand(value: String) {
        context.dataStore.edit { it[KEY_INIT_COMMAND] = value }
    }

    suspend fun setAmbiguousAsWide(value: Boolean) {
        context.dataStore.edit { it[KEY_AMBIGUOUS_WIDE] = value }
    }

    suspend fun setDistro(id: String) {
        context.dataStore.edit { it[KEY_DISTRO_ID] = id }
    }

    suspend fun setFontId(id: String) {
        context.dataStore.edit { it[KEY_FONT_ID] = id }
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
        const val DEFAULT_FONT = "monospace"
        const val DEFAULT_AMBIGUOUS_AS_WIDE = false
        const val DEFAULT_KEYBOARD_STYLE = "compact"
        /** Alpine 同梱で zsh が利用可能なので既定 zsh。`-l` でログインシェル動作。 */
        const val DEFAULT_LOGIN_SHELL = "/bin/zsh"
        val AVAILABLE_SHELLS = listOf("/bin/zsh", "/bin/bash", "/bin/sh")

        const val MIN_FONT_SIZE_SP = 8f
        const val MAX_FONT_SIZE_SP = 32f
        const val MIN_SCROLLBACK_LINES = 500
        const val MAX_SCROLLBACK_LINES = 50000

        private val KEY_THEME_NAME = stringPreferencesKey("theme_name")
        private val KEY_FONT_SIZE = floatPreferencesKey("font_size_sp")
        private val KEY_SCROLLBACK = intPreferencesKey("scrollback_lines")
        private val KEY_DISTRO_ID = stringPreferencesKey("distro_id")
        private val KEY_FONT_ID = stringPreferencesKey("font_id")
        private val KEY_AMBIGUOUS_WIDE = booleanPreferencesKey("ambiguous_as_wide")
        private val KEY_INIT_COMMAND = stringPreferencesKey("init_command")
        private val KEY_KEYBOARD_STYLE = stringPreferencesKey("keyboard_style")
        private val KEY_LOGIN_SHELL = stringPreferencesKey("login_shell")
    }
}
