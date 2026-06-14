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
         * GUI (Xvnc) アプリの音を Android で鳴らすか。ON のときだけ proot 内に PulseAudio を導入・起動し
         * その出力を TCP で受けて AudioTrack で再生する (オプトイン)。OFF (既定) では依存ゼロ・一切起動しない。
         */
        val guiAudioEnabled: Boolean = DEFAULT_GUI_AUDIO,
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
        val landscapeKeyboardHeightDp: Float = DEFAULT_LANDSCAPE_KEYBOARD_HEIGHT_DP,
        /**
         * 縦画面でのキーボード総高さ (dp)。横画面の [landscapeKeyboardHeightDp] とは別に保持し、
         * 画面の向きが変わると自動でそれぞれの値が適用される (毎回スライダーを直す手間をなくす)。
         * 既定 320dp / 範囲 200-460dp。
         */
        val portraitKeyboardHeightDp: Float = DEFAULT_PORTRAIT_KEYBOARD_HEIGHT_DP,
        /**
         * 裏機能「エンジン選択」の解放フラグ。設定のバージョンを7回タップで true になる
         * (Android 開発者モードと同作法)。false の間はエンジン選択 UI を出さない。
         * これ自体は root 不要 (非 root で proot⇄z2root を切替えるため)。chroot を選べるかは
         * 別途 [rootChrootUnlocked] (root セルフテスト成功) が要る。
         */
        val engineSelectorUnlocked: Boolean = false,
        /**
         * 裏機能「root で chroot 実行」の解放フラグ。7タップ時の root セルフテスト成功で true。
         * false の間は chroot エンジンを選択肢に出さない。
         */
        val rootChrootUnlocked: Boolean = false,
        /**
         * 端末セッションの実行エンジン。"proot"(既定・非root) / "z2root"(非root・自前 ptrace) /
         * "chroot"(root)。chroot は [rootChrootUnlocked] が true のときだけ有効 (それ以外は proot 扱い)。
         */
        val executionEngine: String = ENGINE_PROOT,
        /**
         * 外部 SD カード (`/storage/XXXX-XXXX`) を proot 内へ認識させるか。
         * ON のとき [com.zerotoship.z2term.storage.ExternalStorageDetector] が検出した
         * 物理ボリュームを `/sdcard_ext` (および同一の `/storage/XXXX-XXXX`) として
         * bind mount する。OFF (既定) では一切マウントしない (従来挙動と同じ)。
         */
        val externalStorageEnabled: Boolean = DEFAULT_EXTERNAL_STORAGE,
        /**
         * Android ホストの `/system` `/apex` を proot / chroot 内に bind するか (実験的)。
         * ON のとき proot に `-b /system -b /apex` を追加 (chroot 経路では `mount --bind` 相当) し、
         * PRoot 内から Android のリンカ (`/system/bin/linker64`) と ART ライブラリが見える状態になる。
         * これにより `lzhiyong/termux-ndk` の build-tools (aapt2/zipalign/aidl) のような
         * `INTERP=/system/bin/linker64` を要求する ARM aarch64 ELF が proot 内で動かせる
         * (= 端末内で Android アプリをビルドできる)。OFF (既定) では一切 bind せず従来挙動と同じ。
         * セキュリティ上の影響を理解した上で有効化すること。
         */
        val androidHostBindEnabled: Boolean = DEFAULT_ANDROID_HOST_BIND,
        /**
         * ツールバー (端末上部バー) のアイコン並び順。アクション id をカンマ区切りで保持する。
         * 空文字 = 既定順 (ReorderableToolbar 側で既定を補完)。長押しドラッグの並べ替えで更新。
         * 未知/欠落 id は表示側で既定順とマージするので、ボタン追加・削除があっても壊れない。
         */
        val toolbarOrder: String = ""
    )

    suspend fun setToolbarOrder(csv: String) {
        context.dataStore.edit { it[KEY_TOOLBAR_ORDER] = csv }
    }

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
            guiAudioEnabled = p[KEY_GUI_AUDIO] ?: DEFAULT_GUI_AUDIO,
            guiMagnification = p[KEY_GUI_MAGNIFICATION] ?: DEFAULT_GUI_MAGNIFICATION,
            cleanInstallGuiArmed = p[KEY_CLEAN_INSTALL_GUI] ?: false,
            landscapeKeyboardPosition = p[KEY_LANDSCAPE_KB_POS] ?: DEFAULT_LANDSCAPE_KEYBOARD_POSITION,
            landscapeKeyboardWidthDp = p[KEY_LANDSCAPE_KB_WIDTH] ?: DEFAULT_LANDSCAPE_KEYBOARD_WIDTH_DP,
            landscapeKeyboardHeightDp = p[KEY_LANDSCAPE_KB_HEIGHT] ?: DEFAULT_LANDSCAPE_KEYBOARD_HEIGHT_DP,
            portraitKeyboardHeightDp = p[KEY_PORTRAIT_KB_HEIGHT] ?: DEFAULT_PORTRAIT_KEYBOARD_HEIGHT_DP,
            engineSelectorUnlocked = p[KEY_ENGINE_UNLOCKED] ?: false,
            rootChrootUnlocked = p[KEY_ROOT_UNLOCKED] ?: false,
            executionEngine = p[KEY_ENGINE] ?: ENGINE_PROOT,
            externalStorageEnabled = p[KEY_EXTERNAL_STORAGE] ?: DEFAULT_EXTERNAL_STORAGE,
            androidHostBindEnabled = p[KEY_ANDROID_HOST_BIND] ?: DEFAULT_ANDROID_HOST_BIND,
            toolbarOrder = p[KEY_TOOLBAR_ORDER] ?: ""
        )
    }

    suspend fun setEngineSelectorUnlocked(value: Boolean) {
        context.dataStore.edit { it[KEY_ENGINE_UNLOCKED] = value }
    }

    suspend fun setRootChrootUnlocked(value: Boolean) {
        context.dataStore.edit { it[KEY_ROOT_UNLOCKED] = value }
    }

    suspend fun setExecutionEngine(value: String) {
        val normalized = when (value) {
            ENGINE_CHROOT -> ENGINE_CHROOT
            ENGINE_Z2ROOT -> ENGINE_Z2ROOT
            else -> ENGINE_PROOT
        }
        context.dataStore.edit { it[KEY_ENGINE] = normalized }
    }

    suspend fun setExternalStorageEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_EXTERNAL_STORAGE] = value }
    }

    suspend fun setAndroidHostBindEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_ANDROID_HOST_BIND] = value }
    }

    suspend fun setLandscapeKeyboardHeightDp(value: Float) {
        context.dataStore.edit {
            it[KEY_LANDSCAPE_KB_HEIGHT] = value.coerceIn(MIN_LANDSCAPE_KB_HEIGHT_DP, MAX_LANDSCAPE_KB_HEIGHT_DP)
        }
    }

    suspend fun setPortraitKeyboardHeightDp(value: Float) {
        context.dataStore.edit {
            it[KEY_PORTRAIT_KB_HEIGHT] = value.coerceIn(MIN_PORTRAIT_KB_HEIGHT_DP, MAX_PORTRAIT_KB_HEIGHT_DP)
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

    suspend fun setConfirmBeforeDownload(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CONFIRM_DOWNLOAD] = enabled }
    }

    suspend fun setGuiAudioEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GUI_AUDIO] = enabled }
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
        const val DEFAULT_KEYBOARD_STYLE = "spacious"
        const val DEFAULT_KEYBOARD_MODE = "custom"
        const val DEFAULT_KEEP_ALIVE = true

        /** 実行エンジン: 非 root の PRoot (既定) */
        const val ENGINE_PROOT = "proot"
        /** 実行エンジン: 非 root の自前 ptrace エンジン z2root (裏機能。foss の既定エンジン) */
        const val ENGINE_Z2ROOT = "z2root"
        /** 実行エンジン: root で実 chroot (裏機能・要解放) */
        const val ENGINE_CHROOT = "chroot"
        /** 実行エンジン: proot/z2root 起動失敗時の Android /system/bin/sh フォールバック (選択不可・表示専用)。 */
        const val ENGINE_ANDROID_SH = "android-sh"
        /** ダウンロード前確認は既定 ON (勝手に通信しない方針)。 */
        const val DEFAULT_CONFIRM_DOWNLOAD = true
        /** GUI 音声は既定 OFF (オプトイン。ON にして初めて PulseAudio を導入・起動する)。 */
        const val DEFAULT_GUI_AUDIO = false
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
        private val KEY_GUI_AUDIO = booleanPreferencesKey("gui_audio_enabled")
        private val KEY_GUI_MAGNIFICATION = floatPreferencesKey("gui_magnification")
        private val KEY_CLEAN_INSTALL_GUI = booleanPreferencesKey("clean_install_gui_armed")
        private val KEY_LANDSCAPE_KB_POS = stringPreferencesKey("landscape_kb_position")
        private val KEY_LANDSCAPE_KB_WIDTH = floatPreferencesKey("landscape_kb_width_dp")
        private val KEY_LANDSCAPE_KB_HEIGHT = floatPreferencesKey("landscape_kb_height_dp")
        private val KEY_PORTRAIT_KB_HEIGHT = floatPreferencesKey("portrait_kb_height_dp")
        private val KEY_ENGINE_UNLOCKED = booleanPreferencesKey("engine_selector_unlocked")
        private val KEY_ROOT_UNLOCKED = booleanPreferencesKey("root_chroot_unlocked")
        private val KEY_ENGINE = stringPreferencesKey("execution_engine")
        private val KEY_EXTERNAL_STORAGE = booleanPreferencesKey("external_storage_enabled")
        private val KEY_ANDROID_HOST_BIND = booleanPreferencesKey("android_host_bind_enabled")
        private val KEY_TOOLBAR_ORDER = stringPreferencesKey("toolbar_order")

        /** 外部 SD 認識は既定 OFF (オプトイン)。OFF の間は検出処理も走らない。 */
        const val DEFAULT_EXTERNAL_STORAGE = false
        /** Android ホスト bind は既定 OFF (オプトイン)。OFF では proot / chroot に何も追加しない。 */
        const val DEFAULT_ANDROID_HOST_BIND = false

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

        /** 縦画面でのキーボード総高さ (dp)。横画面とは独立して保持し、向きで自動切替。 */
        const val DEFAULT_PORTRAIT_KEYBOARD_HEIGHT_DP = 320f
        const val MIN_PORTRAIT_KB_HEIGHT_DP = 200f
        const val MAX_PORTRAIT_KB_HEIGHT_DP = 460f
    }
}
