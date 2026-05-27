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
        val loginShell: String = DEFAULT_LOGIN_SHELL,
        /** 直近のキーボードモード ("custom" / "system")。次回起動時に復元 */
        val keyboardMode: String = DEFAULT_KEYBOARD_MODE,
        /** フォアグラウンド常駐サービスを使うか (Activity 破棄後もセッション維持) */
        val keepAliveService: Boolean = DEFAULT_KEEP_ALIVE,
        /** GUI セッションで起動するターミナル ([com.zerotoship.z2term.proot.GuiTerminal] の id) */
        val guiTerminalId: String = DEFAULT_GUI_TERMINAL,
        /** 通信を伴うダウンロード (distro / GUI パッケージ) の前に確認ダイアログを出すか */
        val confirmBeforeDownload: Boolean = DEFAULT_CONFIRM_DOWNLOAD,
        /**
         * GUI の表示倍率。1.0 = 端末画素そのまま (最も精細)、大きいほど低解像度＝表示が大きい。
         * Xvnc の仮想画面解像度 = 表示領域px / 倍率 で決まる (次回 GUI 起動から反映)。
         */
        val guiMagnification: Float = DEFAULT_GUI_MAGNIFICATION,
        /**
         * 次に開く GUI タブでクリーンインストール (GUI パッケージをキャッシュごと入れ直す) を行うか。
         * 起動時に消化して false に戻す (チェックは確実に外れる)。distro 側はシート内ローカル状態で扱う。
         */
        val cleanInstallGuiArmed: Boolean = false,
        /**
         * インストール (GUI 一式の apk/apt/pacman・distro rootfs ダウンロード) のタイムアウトを
         * 無効化するか。ON のとき GUI 起動は VNC へ接続できるまで無期限に待ち、distro DL の
         * HTTP read timeout は長め (5 分) になる。遅い回線や Arch 等の大物導入を最後まで
         * 待ちたいときに使う。停止は GUI タブの「✕」(stop) で手動キャンセル可能。
         */
        val noInstallTimeout: Boolean = DEFAULT_NO_INSTALL_TIMEOUT,
        /**
         * 横画面時のキーボード配置 ("left" / "bottom" / "right")。
         * 縦画面のときはこの値に関わらず常に下に出る。
         */
        val landscapeKeyboardPosition: String = DEFAULT_LANDSCAPE_KEYBOARD_POSITION,
        /**
         * 横画面で左/右配置にしたときのキーボード列の幅 (dp)。大きいほどキーが押しやすく、
         * その分端末/GUI 領域が狭くなる。下/縦画面では使われない。
         */
        val landscapeKeyboardWidthDp: Float = DEFAULT_LANDSCAPE_KEYBOARD_WIDTH_DP,
        /**
         * 横画面でのキーボード総高さ (dp)。左/右/下のどの配置でも適用される (横画面の時のみ)。
         * 大きいほどキーが押しやすいが、その分端末/GUI 領域が狭くなる。
         * 既定 320dp / 範囲 200-500dp。
         */
        val landscapeKeyboardHeightDp: Float = DEFAULT_LANDSCAPE_KEYBOARD_HEIGHT_DP
    )

    suspend fun setGuiMagnification(value: Float) {
        context.dataStore.edit {
            it[KEY_GUI_MAGNIFICATION] = value.coerceIn(MIN_GUI_MAGNIFICATION, MAX_GUI_MAGNIFICATION)
        }
    }

    suspend fun setCleanInstallGuiArmed(armed: Boolean) {
        context.dataStore.edit { it[KEY_CLEAN_INSTALL_GUI] = armed }
    }

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
            loginShell = p[KEY_LOGIN_SHELL] ?: DEFAULT_LOGIN_SHELL,
            keyboardMode = p[KEY_KEYBOARD_MODE] ?: DEFAULT_KEYBOARD_MODE,
            keepAliveService = p[KEY_KEEP_ALIVE] ?: DEFAULT_KEEP_ALIVE,
            guiTerminalId = p[KEY_GUI_TERMINAL] ?: DEFAULT_GUI_TERMINAL,
            confirmBeforeDownload = p[KEY_CONFIRM_DOWNLOAD] ?: DEFAULT_CONFIRM_DOWNLOAD,
            guiMagnification = p[KEY_GUI_MAGNIFICATION] ?: DEFAULT_GUI_MAGNIFICATION,
            cleanInstallGuiArmed = p[KEY_CLEAN_INSTALL_GUI] ?: false,
            noInstallTimeout = p[KEY_NO_INSTALL_TIMEOUT] ?: DEFAULT_NO_INSTALL_TIMEOUT,
            landscapeKeyboardPosition = p[KEY_LANDSCAPE_KB_POS] ?: DEFAULT_LANDSCAPE_KEYBOARD_POSITION,
            landscapeKeyboardWidthDp = p[KEY_LANDSCAPE_KB_WIDTH] ?: DEFAULT_LANDSCAPE_KEYBOARD_WIDTH_DP,
            landscapeKeyboardHeightDp = p[KEY_LANDSCAPE_KB_HEIGHT] ?: DEFAULT_LANDSCAPE_KEYBOARD_HEIGHT_DP
        )
    }

    suspend fun setLandscapeKeyboardHeightDp(value: Float) {
        context.dataStore.edit {
            it[KEY_LANDSCAPE_KB_HEIGHT] = value.coerceIn(MIN_LANDSCAPE_KB_HEIGHT_DP, MAX_LANDSCAPE_KB_HEIGHT_DP)
        }
    }

    suspend fun setLandscapeKeyboardPosition(value: String) {
        val normalized = when (value) {
            LANDSCAPE_KB_LEFT, LANDSCAPE_KB_BOTTOM, LANDSCAPE_KB_RIGHT -> value
            else -> DEFAULT_LANDSCAPE_KEYBOARD_POSITION
        }
        context.dataStore.edit { it[KEY_LANDSCAPE_KB_POS] = normalized }
    }

    suspend fun setLandscapeKeyboardWidthDp(value: Float) {
        context.dataStore.edit {
            it[KEY_LANDSCAPE_KB_WIDTH] = value.coerceIn(MIN_LANDSCAPE_KB_WIDTH_DP, MAX_LANDSCAPE_KB_WIDTH_DP)
        }
    }

    suspend fun setNoInstallTimeout(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NO_INSTALL_TIMEOUT] = enabled }
    }

    suspend fun setConfirmBeforeDownload(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CONFIRM_DOWNLOAD] = enabled }
    }

    suspend fun setGuiTerminal(id: String) {
        context.dataStore.edit { it[KEY_GUI_TERMINAL] = id }
    }

    suspend fun setKeyboardMode(mode: String) {
        context.dataStore.edit { it[KEY_KEYBOARD_MODE] = mode }
    }

    suspend fun setKeepAliveService(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_ALIVE] = enabled }
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
        const val DEFAULT_KEYBOARD_MODE = "custom"
        const val DEFAULT_KEEP_ALIVE = true
        /** ダウンロード前確認は既定 ON (勝手に通信しない方針)。 */
        const val DEFAULT_CONFIRM_DOWNLOAD = true
        /** タイムアウト無効化は既定 OFF (従来通り 5 分・30s で打ち切り、明示的に解除させる)。 */
        const val DEFAULT_NO_INSTALL_TIMEOUT = false
        /** GUI 接続待ちの既定タイムアウト (ms)。Arch の pacman も込みで 5 分。 */
        const val DEFAULT_GUI_CONNECT_TIMEOUT_MS = 300_000L
        /** distro DL の既定 read timeout (ms)。HTTP 単一 read の上限。 */
        const val DEFAULT_DOWNLOAD_READ_TIMEOUT_MS = 30_000
        /** タイムアウト無効化 ON のときに使う長め read timeout (ms)。完全 0 にすると無期限で詰まりやすいので 5 分。 */
        const val EXTENDED_DOWNLOAD_READ_TIMEOUT_MS = 300_000
        /** GUI ターミナルの既定 ([com.zerotoship.z2term.proot.GuiTerminal.XTERM] の id) */
        const val DEFAULT_GUI_TERMINAL = "xterm"
        /** GUI 表示倍率の既定。1.5 = 解像度を 2/3 にして表示を一回り大きく (細かすぎ対策)。 */
        const val DEFAULT_GUI_MAGNIFICATION = 1.5f
        /** 0.5 = 仮想画面を 2 倍解像度にして縮小表示 (より細かく・広く)。1.0 が等倍。 */
        const val MIN_GUI_MAGNIFICATION = 0.5f
        const val MAX_GUI_MAGNIFICATION = 3.0f
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
        private val KEY_KEYBOARD_MODE = stringPreferencesKey("keyboard_mode")
        private val KEY_KEEP_ALIVE = booleanPreferencesKey("keep_alive_service")
        private val KEY_GUI_TERMINAL = stringPreferencesKey("gui_terminal")
        private val KEY_CONFIRM_DOWNLOAD = booleanPreferencesKey("confirm_before_download")
        private val KEY_GUI_MAGNIFICATION = floatPreferencesKey("gui_magnification")
        private val KEY_CLEAN_INSTALL_GUI = booleanPreferencesKey("clean_install_gui_armed")
        private val KEY_NO_INSTALL_TIMEOUT = booleanPreferencesKey("no_install_timeout")
        private val KEY_LANDSCAPE_KB_POS = stringPreferencesKey("landscape_kb_position")
        private val KEY_LANDSCAPE_KB_WIDTH = floatPreferencesKey("landscape_kb_width_dp")
        private val KEY_LANDSCAPE_KB_HEIGHT = floatPreferencesKey("landscape_kb_height_dp")

        /** 横画面時のキーボード配置の選択肢 */
        const val LANDSCAPE_KB_LEFT = "left"
        const val LANDSCAPE_KB_BOTTOM = "bottom"
        const val LANDSCAPE_KB_RIGHT = "right"
        /** 既定: 横画面でも下 (従来挙動と同じ) */
        const val DEFAULT_LANDSCAPE_KEYBOARD_POSITION = LANDSCAPE_KB_BOTTOM

        /** 横画面サイド配置のキーボード列の幅 (dp)。10 キー幅で 1 キー = 幅/10 dp。 */
        const val DEFAULT_LANDSCAPE_KEYBOARD_WIDTH_DP = 420f
        /** 最小幅: 1 キー = 28dp (タップしづらいが許容) */
        const val MIN_LANDSCAPE_KB_WIDTH_DP = 280f
        /** 最大幅: 端末/GUI を残したいので 700dp で打ち止め */
        const val MAX_LANDSCAPE_KB_WIDTH_DP = 700f

        /** 横画面でのキーボード総高さ (dp)。下/左/右どの配置でも横画面の時に適用。 */
        const val DEFAULT_LANDSCAPE_KEYBOARD_HEIGHT_DP = 320f
        const val MIN_LANDSCAPE_KB_HEIGHT_DP = 200f
        const val MAX_LANDSCAPE_KB_HEIGHT_DP = 500f
    }
}
