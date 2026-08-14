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
import kotlinx.coroutines.flow.first
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
        /**
         * OS の入力メソッド (`Z2ImeService`) として開いたときの面 (`KeyboardFace.id`)。
         * 切替キーで移るたびに保存し、次にキーボードが開くときその面で出す。
         *
         * ⚠ **端末画面の内蔵キーボードはこの値を読まない** — 端末は常に英字面から始める。
         * 端末では英字で打ち始めることが多く、他アプリでは日本語で打ち始めることが多いため、
         * 面を覚えるのは入力メソッド側だけにしている。
         *
         * ⚠ 0.8.305 で真偽値 (`ime_japanese_mode`) から面の id へ移した。既存ユーザーの値は
         * 読み出し時に読み替える (true → かな面 / false → 英字面)。
         */
        val imeFace: String = DEFAULT_IME_FACE,
        /**
         * 数字だけの面 (`KeyboardFace.NUMBER`) を面の巡回に入れるか (0.8.305)。
         * OFF なら巡回は「あ → A → あ」の従来どおりで、キーの見た目も 0.8.304 と変わらない。
         */
        val keyboardNumberFace: Boolean = DEFAULT_KEYBOARD_NUMBER_FACE,
        /**
         * 面の巡回順のプリセット (`KeyboardFace.ORDER_*_ID`)。
         *
         * ⚠ **3 面の巡回順は回転を除いて 2 通りしかない**ので、選べるのはこの 2 つで全部
         * (`A → 12 → あ` は `あ → A → 12` を回しただけの同じ順)。
         */
        val keyboardFaceOrder: String = DEFAULT_KEYBOARD_FACE_ORDER,
        /** フォアグラウンド常駐サービスを使うか (Activity 破棄後もセッション維持) */
        val keepAliveService: Boolean = DEFAULT_KEEP_ALIVE,
        /** 画面消灯ロック (ディスプレイを自動で消さない) の状態。次回起動時に復元 */
        val keepScreenOn: Boolean = DEFAULT_KEEP_SCREEN_ON,
        /**
         * このアプリの画面だけの明るさ (🔅 のダブルタップで出る帯)。**null = OS に任せる** (既定)。
         *
         * 保存しない一時的な調整として始めた (0.8.234) が、暗い部屋で使う人は毎回同じ値へ
         * 合わせ直すことになっていたため永続化した (0.8.242)。「戻す」でキーごと消えて
         * null に戻るので、保存していても**モードは増えない** (触らなければ OS 任せのまま)。
         */
        val screenBrightness: Float? = null,
        /** キーボード表示/非表示トグルバーをキーボードの上に出すか (OFF なら ⌨ ボタンのダブルタップで切替) */
        val keyboardToggleBar: Boolean = DEFAULT_KEYBOARD_TOGGLE_BAR,
        /**
         * OS のキーボードを使っているときに補助キーバー (ESC/TAB/CTRL/矢印…) を出すか。
         * 内蔵キーボードのときは元々出ないので影響しない。
         */
        val specialKeyBar: Boolean = DEFAULT_SPECIAL_KEY_BAR,
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
         * これ自体は root 不要。chroot を選べるかは
         * 別途 [rootChrootUnlocked] (root セルフテスト成功) が要る。
         */
        val engineSelectorUnlocked: Boolean = false,
        /**
         * 裏機能「root で chroot 実行」の解放フラグ。7タップ時の root セルフテスト成功で true。
         * false の間は chroot エンジンを選択肢に出さない。
         */
        val rootChrootUnlocked: Boolean = false,
        /**
         * 端末セッションの実行エンジン。"z2root"(非root・自前 ptrace) / "chroot"(root)。
         * chroot は [rootChrootUnlocked] が true のときだけ有効。それ以外は常に z2root。
         */
        val executionEngine: String = ENGINE_Z2ROOT,
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
        val toolbarOrder: String = "",
        /**
         * ツールバーで**隠している**ボタンのアクション id をカンマ区切りで保持する。
         * 空文字 = 全部出す (既定)。設定シートの「ツールバー」セクションから切り替える。
         * ボタンが増えても各自で減らせるようにするための指定で、⚙ 設定だけは隠せない
         * (隠すと設定画面へ戻れなくなるため。[com.zerotoship.z2term.ui.terminal.ToolbarButtons] 参照)。
         */
        val toolbarHidden: String = "",
        /**
         * 端末ログ (ツールバー ⚪) の保存先。**ホーム (`~`) からの相対パス**で持つ。
         * 端末からもファイラーからもすぐ触れるよう、既定は `~/z2term-log/`。
         * 記録の ON/OFF 自体はタブごとの状態なので永続化しない (アプリ再起動で必ず OFF)。
         */
        val sessionLogDir: String = DEFAULT_SESSION_LOG_DIR,
        /**
         * 端末ログのファイル名テンプレート。`{date}` = [sessionLogTimeFormat] で整形した日時、
         * `{tab}` = タブ名 (ファイル名に使えない文字は `_` に置換)。
         */
        val sessionLogNameTemplate: String = DEFAULT_SESSION_LOG_NAME,
        /** 端末ログのファイル名に埋める日時の書式 (`SimpleDateFormat` パターン)。 */
        val sessionLogTimeFormat: String = DEFAULT_SESSION_LOG_TIME,
        /**
         * 記録を始めるとき、**それまで画面に出ていた分 (スクロールバック含む) も先頭に書く**か。
         * 既定 OFF (押した時点から先だけ)。ON にすると「あ、記録し忘れた」を後から拾える。
         */
        val sessionLogIncludeScrollback: Boolean = DEFAULT_SESSION_LOG_SCROLLBACK,
        /**
         * 同名のファイルがあったとき追記するか。既定 OFF = **毎回新しいファイル**
         * (同名なら `-2` `-3` を足す)。追記だと停止→再開の境目が分からなくなるため。
         */
        val sessionLogAppend: Boolean = DEFAULT_SESSION_LOG_APPEND,
        /**
         * 色や画面制御の指示 (エスケープシーケンス) を**そのまま残す**か。既定 OFF =
         * 人が読めるプレーンテキストに直して書く。ON は不具合報告用の生ログ。
         */
        val sessionLogRaw: Boolean = DEFAULT_SESSION_LOG_RAW,
        /**
         * 全画面表示 (alt screen) の間も書くか。既定 OFF。全画面 TUI は画面を組み立て直しながら
         * 描くので、平坦なテキストにしても意味のある内容にならず、ファイルだけが膨れるため。
         */
        val sessionLogAltScreen: Boolean = DEFAULT_SESSION_LOG_ALT_SCREEN,
        /**
         * 新しいタブが繋がったら、⚪ を押さなくても記録を始めるか (既定 OFF)。
         *
         * 「あとから見返そうとしたら録っていなかった」を無くすための設定。記録の ON/OFF 自体は
         * タブごとの状態で永続化しないが、**この設定は永続化する** — 自動開始は「毎回そうしたい」
         * という意思であって、そのタブだけの一時的な状態ではないため。
         */
        val sessionLogAutoStart: Boolean = DEFAULT_SESSION_LOG_AUTO_START,
        /**
         * 端末ログに書く前に、鍵・トークン・パスワードらしき部分を伏せ字にするか (既定 ON)。
         *
         * ⚠ **完全ではない** ([com.zerotoship.z2term.core.SecretMasker] を参照)。誤爆しない形が
         * はっきりしているものだけを対象にしており、独自形式の秘密は素通りする。それでも既定 ON
         * なのは、ログを人に見せる場面 (不具合報告・作業記録の共有) で最も多い漏れ方が
         * `TOKEN=...` と貼り付けた秘密鍵の 2 つで、そこは高い精度で潰せるため。
         */
        val sessionLogMaskSecrets: Boolean = DEFAULT_SESSION_LOG_MASK,
        /**
         * ログの各行の先頭に日時を付けるか (既定 OFF)。書式は固定長の
         * `[yyyy-MM-dd HH:mm:ss] `。⚠ **生ログ (raw) には効かない** — バイト列が
         * そのまま残ることが生ログの存在意義なので、1 バイトも足さない。
         */
        val sessionLogTimestamp: Boolean = DEFAULT_SESSION_LOG_TIMESTAMP,
        /**
         * z2root エンジンの syscall トレースログを出すか (開発者用・既定 OFF)。
         * ON のとき shared_home/z2root_trace.log に全 syscall を記録する。ログは膨大で
         * 容量を圧迫するため一般ユーザーは使わない。エンジン選択 (7タップ解放) と同じ場所に
         * トグルを置き、解放済みのときだけ表示する。
         */
        val traceLogEnabled: Boolean = DEFAULT_TRACE_LOG,
        /**
         * Kitty graphics protocol の **file/temp/shm 転送** (`t=f`/`t=t`/`t=s`) を許可するか。
         * 既定 OFF (= TUI からの任意ファイル読取を遮断)。 ON にするとセッション側で
         * ホスト/ゲストパス変換 + 実ファイル読込 + temp の自動 unlink を行う実体を
         * エミュレータに注入する。 OFF の間は parser が file/temp/shm をすべて破棄し
         * `a=q` も ENOTSUPPORTED を返す。
         */
        val kittyExternalFileEnabled: Boolean = DEFAULT_KITTY_EXTERNAL_FILE,
        /**
         * 画面タップ / 1 指ドラッグ / 1 指長押しを **SGR mouse protocol** (`?1006`) として
         * PTY へ送るか。 既定 OFF。 ON にすると TUI 側が `?1000`/`?1002`/`?1003`/`?1006` で
         * mouse capture を有効化した状態のとき以下を SGR (`\x1b[<n;col;row>M/m`) で送出する:
         *  - 1 指タップ → button 0 (左クリック相当) の press + release
         *  - 1 指長押し → button 2 (右クリック相当) の press + release
         *  - 1 指ドラッグ → button 32 (motion 修飾) を連発
         * OFF (既定) のときはタップ/長押し/ドラッグはすべて Z2Term 自身の操作 (フォーカス /
         * テキスト選択 / スクロール) に使う。 二本指スワイプ→ホイール (button 64/65) は
         * opt-in に関係なく従来通り mouse capture 中なら送出する。
         */
        val sgrMouseInputEnabled: Boolean = DEFAULT_SGR_MOUSE_INPUT,
        /**
         * 常駐サーバー定義 (JSON 配列)。[com.zerotoship.z2term.settings.ServerEntry.decode] で
         * `List<ServerEntry>` に復元する。空文字 = 未設定。
         */
        val serverEntries: String = "",
        /**
         * 端末起動時 (BOOT_COMPLETED) に常駐サーバーを自動起動するか。ON かつ enabled な
         * サーバーがあれば、アプリを開かずに [com.zerotoship.z2term.service.ServerDaemonService] が
         * supervisor を立ち上げる。既定 OFF。
         */
        val serversAutostartOnBoot: Boolean = DEFAULT_SERVERS_AUTOSTART,
        /**
         * 常駐サーバーの **省電力モード**。ON のとき [com.zerotoship.z2term.service.ServerDaemonService] が
         * WakeLock / WifiLock を握らず、端末の Doze (深いスリープ) を許す。電池は減りにくくなるが、
         * 画面消灯中は外部からの着信が遅延・取りこぼすことがある (到達性より電池優先)。既定 OFF。
         */
        val serversLowPower: Boolean = DEFAULT_SERVERS_LOW_POWER,
        /**
         * 通知検知の有効化フラグ。ON かつ OS の「通知アクセス」許可があるとき、
         * [com.zerotoship.z2term.service.NotificationLogService] が受け取った通知を生のまま
         * `~/.z2term/notifications.jsonl` へ追記する。フィルタ・保存方針・配信は一切ハードコードせず、
         * 加工はユーザーがターミナル側で自由に行う **汎用の検知入口** (z2-notify の逆向き)。
         * 既定 OFF・完全ローカル・外部送信なし。
         */
        val notificationCaptureEnabled: Boolean = DEFAULT_NOTIFICATION_CAPTURE,
        /**
         * 通知ログを **ファイルに保存する**か。OFF にすると検知 (常駐) は続けたまま
         * `~/.z2term/notifications.jsonl` へは一切書かない (検知だけ使いたい場合・
         * 保存容量やプライバシーを気にする場合)。既定 ON = 従来どおり保存する。
         */
        val notificationLogEnabled: Boolean = DEFAULT_NOTIFICATION_LOG,
        /**
         * 通知ログの出力フォーマット **テンプレート**。ユーザーが自由に編集する。プレースホルダ
         * `{time}` `{ts}` `{pkg}` `{app}` `{title}` `{text}` `{category}` `{key}` と、改行 `\n`・
         * タブ `\t`・1 行化 `{text1}` `{title1}` (改行→空白) が使える。**空文字なら JSONL** (機械可読・既定)。
         * 置換は [com.zerotoship.z2term.service.NotificationLogService.render] が行う。
         */
        val notificationLogFormat: String = DEFAULT_NOTIFICATION_LOG_FORMAT,
        /**
         * 通知ログを **先頭追記** (新着が上) にするか。false で従来どおり末尾追記 (新着が下)。
         * true のとき [com.zerotoship.z2term.service.LogWriter] が既存内容を読んで書き直す。
         */
        val notificationLogPrepend: Boolean = DEFAULT_LOG_PREPEND,
        /**
         * システムイベント検知の有効化フラグ。ON のとき [com.zerotoship.z2term.service.SystemEventService]
         * (opt-in の FG サービス) が常駐し、画面 ON/OFF・ロック解除・充電・電池残量・Wi‑Fi 接続などを
         * `~/.z2term/events.jsonl` へ追記する。通知検知 (z2-notify の逆向き) の姉妹機能で、加工はユーザーが
         * ターミナル側で自由に行う **汎用の検知入口**。既定 OFF・完全ローカル・外部送信なし。
         */
        val systemEventCaptureEnabled: Boolean = DEFAULT_SYSTEM_EVENT_CAPTURE,
        /**
         * 初回ガイド (最初の 3 枚) を出し終えたか。1 度きりの案内なので、**触ったら二度と出さない**。
         * 既定 false = まだ出していない。設定から false に戻せば、もう一度出せる。
         */
        val introDone: Boolean = false,
        /**
         * つまずきの言い換え (端末に既知のエラーが出たら 1 行だけ次の一手を出す)。
         * 既定 ON。うっとうしいと感じたらすぐ切れるよう、設定に出しておく。
         */
        val terminalHintsEnabled: Boolean = true,
        /**
         * システムイベントログの出力フォーマット **テンプレート**。プレースホルダ `{time}` `{ts}`
         * `{event}` `{level}` `{ssid}` と、改行 `\n`・タブ `\t` が使える。**空文字なら JSONL** (既定)。
         * 置換は [com.zerotoship.z2term.service.SystemEventService.render] が行う。
         */
        val systemEventLogFormat: String = DEFAULT_SYSTEM_EVENT_LOG_FORMAT,
        /**
         * システムイベントログを **先頭追記** (新着が上) にするか。false で末尾追記 (新着が下)。
         * true のとき [com.zerotoship.z2term.service.LogWriter] が既存内容を読んで書き直す。
         */
        val systemEventLogPrepend: Boolean = DEFAULT_LOG_PREPEND,
        /**
         * 画面ロック解除の**失敗監視**フラグ。ON かつ端末管理者 (Device Admin) が有効なとき、
         * [com.zerotoship.z2term.service.PasswordWatchAdmin] がロック解除の失敗/成功を
         * `~/.z2term/events.jsonl` へ `unlock_failed` / `unlock_succeeded` として追記する。
         * 盗難・不正利用対策マクロ (失敗回数で通知・位置記録・警報など) の**検知入口**で、
         * 撮影や送信などのアクションはアプリ側でハードコードせずユーザーがマクロで組む。
         * 端末管理者権限は失敗回数の取得 (`watch-login`) にのみ使い、遠隔ロック/ワイプはしない。
         * 既定 OFF・完全ローカル・外部送信なし。
         */
        val unlockWatchEnabled: Boolean = DEFAULT_UNLOCK_WATCH,
        /**
         * SMS 受信検知の有効化フラグ。ON かつ OS の `RECEIVE_SMS` 許可があるとき、
         * [com.zerotoship.z2term.service.SmsLogReceiver] が受信 SMS を `~/.z2term/sms.jsonl` へ追記する。
         * 通知と違い SMS 本文は OS の機微通知伏せ字 (Android 15+) やロック状態の影響を受けないため、
         * ワンタイムパスワードを確実に取れる。通知検知の姉妹機能で加工はユーザーがターミナル側で行う。
         * 既定 OFF・完全ローカル・外部送信なし。
         */
        val smsCaptureEnabled: Boolean = DEFAULT_SMS_CAPTURE,
        /**
         * SMS ログの出力フォーマット **テンプレート**。プレースホルダ `{time}` `{ts}` `{from}` `{body}` と、
         * 改行 `\n`・タブ `\t`・1 行化 `{body1}` (改行→空白) が使える。**空文字なら JSONL** (機械可読・既定)。
         * 置換は [com.zerotoship.z2term.service.SmsLogReceiver.render] が行う。
         */
        val smsLogFormat: String = DEFAULT_SMS_LOG_FORMAT,
        /**
         * SMS ログを **先頭追記** (新着が上) にするか。false で末尾追記 (新着が下)。
         */
        val smsLogPrepend: Boolean = DEFAULT_LOG_PREPEND
    )

    suspend fun setToolbarOrder(csv: String) {
        context.dataStore.edit { it[KEY_TOOLBAR_ORDER] = csv }
    }

    suspend fun setToolbarHidden(csv: String) {
        context.dataStore.edit { it[KEY_TOOLBAR_HIDDEN] = csv }
    }

    suspend fun setSessionLogDir(value: String) {
        context.dataStore.edit { it[KEY_SESSION_LOG_DIR] = value }
    }

    suspend fun setSessionLogNameTemplate(value: String) {
        context.dataStore.edit { it[KEY_SESSION_LOG_NAME] = value }
    }

    suspend fun setSessionLogTimeFormat(value: String) {
        context.dataStore.edit { it[KEY_SESSION_LOG_TIME] = value }
    }

    suspend fun setSessionLogIncludeScrollback(value: Boolean) {
        context.dataStore.edit { it[KEY_SESSION_LOG_SCROLLBACK] = value }
    }

    suspend fun setSessionLogAppend(value: Boolean) {
        context.dataStore.edit { it[KEY_SESSION_LOG_APPEND] = value }
    }

    suspend fun setSessionLogRaw(value: Boolean) {
        context.dataStore.edit { it[KEY_SESSION_LOG_RAW] = value }
    }

    suspend fun setSessionLogAltScreen(value: Boolean) {
        context.dataStore.edit { it[KEY_SESSION_LOG_ALT_SCREEN] = value }
    }

    suspend fun setSessionLogAutoStart(value: Boolean) {
        context.dataStore.edit { it[KEY_SESSION_LOG_AUTO_START] = value }
    }

    suspend fun setSessionLogMaskSecrets(value: Boolean) {
        context.dataStore.edit { it[KEY_SESSION_LOG_MASK] = value }
    }

    suspend fun setSessionLogTimestamp(value: Boolean) {
        context.dataStore.edit { it[KEY_SESSION_LOG_TIMESTAMP] = value }
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
            // ⚠ 旧 `ime_japanese_mode` からの読み替え。新しいキーが無いユーザーは
            // 真偽値の方を見て、かな面 / 英字面に対応付ける (面の設定を失わせない)。
            imeFace = p[KEY_IME_FACE]
                ?: if (p[KEY_IME_JAPANESE_MODE] == true) FACE_ID_KANA else DEFAULT_IME_FACE,
            keyboardNumberFace = p[KEY_KEYBOARD_NUMBER_FACE] ?: DEFAULT_KEYBOARD_NUMBER_FACE,
            keyboardFaceOrder = p[KEY_KEYBOARD_FACE_ORDER] ?: DEFAULT_KEYBOARD_FACE_ORDER,
            keepAliveService = p[KEY_KEEP_ALIVE] ?: DEFAULT_KEEP_ALIVE,
            keepScreenOn = p[KEY_KEEP_SCREEN_ON] ?: DEFAULT_KEEP_SCREEN_ON,
            // キーが無い = 一度も触っていない or 「戻す」を押した = OS に任せる。
            screenBrightness = p[KEY_SCREEN_BRIGHTNESS],
            keyboardToggleBar = p[KEY_KEYBOARD_TOGGLE_BAR] ?: DEFAULT_KEYBOARD_TOGGLE_BAR,
            specialKeyBar = p[KEY_SPECIAL_KEY_BAR] ?: DEFAULT_SPECIAL_KEY_BAR,
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
            executionEngine = p[KEY_ENGINE] ?: ENGINE_Z2ROOT,
            externalStorageEnabled = p[KEY_EXTERNAL_STORAGE] ?: DEFAULT_EXTERNAL_STORAGE,
            androidHostBindEnabled = p[KEY_ANDROID_HOST_BIND] ?: DEFAULT_ANDROID_HOST_BIND,
            toolbarOrder = p[KEY_TOOLBAR_ORDER] ?: "",
            toolbarHidden = p[KEY_TOOLBAR_HIDDEN] ?: "",
            sessionLogDir = p[KEY_SESSION_LOG_DIR] ?: DEFAULT_SESSION_LOG_DIR,
            sessionLogNameTemplate = p[KEY_SESSION_LOG_NAME] ?: DEFAULT_SESSION_LOG_NAME,
            sessionLogTimeFormat = p[KEY_SESSION_LOG_TIME] ?: DEFAULT_SESSION_LOG_TIME,
            sessionLogIncludeScrollback = p[KEY_SESSION_LOG_SCROLLBACK] ?: DEFAULT_SESSION_LOG_SCROLLBACK,
            sessionLogAppend = p[KEY_SESSION_LOG_APPEND] ?: DEFAULT_SESSION_LOG_APPEND,
            sessionLogRaw = p[KEY_SESSION_LOG_RAW] ?: DEFAULT_SESSION_LOG_RAW,
            sessionLogAltScreen = p[KEY_SESSION_LOG_ALT_SCREEN] ?: DEFAULT_SESSION_LOG_ALT_SCREEN,
            sessionLogAutoStart = p[KEY_SESSION_LOG_AUTO_START] ?: DEFAULT_SESSION_LOG_AUTO_START,
            sessionLogMaskSecrets = p[KEY_SESSION_LOG_MASK] ?: DEFAULT_SESSION_LOG_MASK,
            sessionLogTimestamp = p[KEY_SESSION_LOG_TIMESTAMP] ?: DEFAULT_SESSION_LOG_TIMESTAMP,
            traceLogEnabled = p[KEY_TRACE_LOG] ?: DEFAULT_TRACE_LOG,
            kittyExternalFileEnabled = p[KEY_KITTY_EXTERNAL_FILE] ?: DEFAULT_KITTY_EXTERNAL_FILE,
            sgrMouseInputEnabled = p[KEY_SGR_MOUSE_INPUT] ?: DEFAULT_SGR_MOUSE_INPUT,
            serverEntries = p[KEY_SERVER_ENTRIES] ?: "",
            serversAutostartOnBoot = p[KEY_SERVERS_AUTOSTART] ?: DEFAULT_SERVERS_AUTOSTART,
            serversLowPower = p[KEY_SERVERS_LOW_POWER] ?: DEFAULT_SERVERS_LOW_POWER,
            notificationCaptureEnabled = p[KEY_NOTIFICATION_CAPTURE] ?: DEFAULT_NOTIFICATION_CAPTURE,
            notificationLogEnabled = p[KEY_NOTIFICATION_LOG] ?: DEFAULT_NOTIFICATION_LOG,
            notificationLogFormat = p[KEY_NOTIFICATION_LOG_FORMAT] ?: DEFAULT_NOTIFICATION_LOG_FORMAT,
            notificationLogPrepend = p[KEY_NOTIFICATION_LOG_PREPEND] ?: DEFAULT_LOG_PREPEND,
            systemEventCaptureEnabled = p[KEY_SYSTEM_EVENT_CAPTURE] ?: DEFAULT_SYSTEM_EVENT_CAPTURE,
            introDone = p[KEY_INTRO_DONE] ?: false,
            terminalHintsEnabled = p[KEY_TERMINAL_HINTS] ?: true,
            systemEventLogFormat = p[KEY_SYSTEM_EVENT_LOG_FORMAT] ?: DEFAULT_SYSTEM_EVENT_LOG_FORMAT,
            systemEventLogPrepend = p[KEY_SYSTEM_EVENT_LOG_PREPEND] ?: DEFAULT_LOG_PREPEND,
            unlockWatchEnabled = p[KEY_UNLOCK_WATCH] ?: DEFAULT_UNLOCK_WATCH,
            smsCaptureEnabled = p[KEY_SMS_CAPTURE] ?: DEFAULT_SMS_CAPTURE,
            smsLogFormat = p[KEY_SMS_LOG_FORMAT] ?: DEFAULT_SMS_LOG_FORMAT,
            smsLogPrepend = p[KEY_SMS_LOG_PREPEND] ?: DEFAULT_LOG_PREPEND
        )
    }

    suspend fun setSmsCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SMS_CAPTURE] = enabled }
    }

    suspend fun setSmsLogFormat(template: String) {
        context.dataStore.edit { it[KEY_SMS_LOG_FORMAT] = template }
    }

    suspend fun setSmsLogPrepend(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SMS_LOG_PREPEND] = enabled }
    }

    suspend fun setNotificationCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATION_CAPTURE] = enabled }
    }

    suspend fun setNotificationLogEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATION_LOG] = enabled }
    }

    suspend fun setNotificationLogFormat(template: String) {
        context.dataStore.edit { it[KEY_NOTIFICATION_LOG_FORMAT] = template }
    }

    suspend fun setNotificationLogPrepend(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATION_LOG_PREPEND] = enabled }
    }

    suspend fun setSystemEventCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SYSTEM_EVENT_CAPTURE] = enabled }
    }

    /** 初回ガイドを出し終えた (または「もう出さない」を選んだ) ことを覚える。 */
    suspend fun setIntroDone(done: Boolean) {
        context.dataStore.edit { it[KEY_INTRO_DONE] = done }
    }

    suspend fun setTerminalHintsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TERMINAL_HINTS] = enabled }
    }

    /** 設定をまるごと JSON にする (持ち出し用・0.8.239)。 */
    suspend fun exportRaw(): String = PrefsPortable.toJson(context.dataStore.data.first())

    /** 持ち出した設定を書き戻す (既存は消さず、あるものだけ更新する)。 */
    suspend fun importRaw(json: String) {
        context.dataStore.edit { PrefsPortable.applyTo(it, json) }
    }

    suspend fun setSystemEventLogFormat(template: String) {
        context.dataStore.edit { it[KEY_SYSTEM_EVENT_LOG_FORMAT] = template }
    }

    suspend fun setSystemEventLogPrepend(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SYSTEM_EVENT_LOG_PREPEND] = enabled }
    }

    suspend fun setUnlockWatchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_UNLOCK_WATCH] = enabled }
    }

    suspend fun setServerEntries(json: String) {
        context.dataStore.edit { it[KEY_SERVER_ENTRIES] = json }
    }

    suspend fun setServersAutostartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SERVERS_AUTOSTART] = enabled }
    }

    suspend fun setServersLowPower(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SERVERS_LOW_POWER] = enabled }
    }

    suspend fun setKittyExternalFileEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_KITTY_EXTERNAL_FILE] = value }
    }

    suspend fun setSgrMouseInputEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_SGR_MOUSE_INPUT] = value }
    }

    suspend fun setTraceLogEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_TRACE_LOG] = value }
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
            else -> ENGINE_Z2ROOT
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

    /** 入力メソッドで最後に使っていた面を覚える (`KeyboardFace.id`)。 */
    suspend fun setImeFace(faceId: String) {
        context.dataStore.edit { it[KEY_IME_FACE] = faceId }
    }

    suspend fun setKeyboardNumberFace(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEYBOARD_NUMBER_FACE] = enabled }
    }

    suspend fun setKeyboardFaceOrder(orderId: String) {
        context.dataStore.edit { it[KEY_KEYBOARD_FACE_ORDER] = orderId }
    }

    suspend fun setKeepAliveService(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_ALIVE] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }

    /**
     * この画面だけの明るさを保存する。[level] が null なら**キーごと消す** (= OS に任せるへ戻す)。
     * 0 を書いて「明るさ 0 で保存済み」にしてしまわないよう、必ず remove で戻すこと。
     */
    suspend fun setScreenBrightness(level: Float?) {
        context.dataStore.edit {
            if (level == null) it.remove(KEY_SCREEN_BRIGHTNESS) else it[KEY_SCREEN_BRIGHTNESS] = level
        }
    }

    suspend fun setKeyboardToggleBar(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEYBOARD_TOGGLE_BAR] = enabled }
    }

    suspend fun setSpecialKeyBar(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SPECIAL_KEY_BAR] = enabled }
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

    /**
     * すべての設定を初期値へ戻す (「初期化」= デフォルト設定に戻る)。DataStore の全キーを消すと
     * [flow] が各 DEFAULT_* / 既定値へフォールバックする (実行エンジンは [ENGINE_Z2ROOT] が既定)。
     * 常駐サーバー定義・裏機能の解放フラグ (エンジン選択 / root chroot)・ツールバー並び順・
     * 各種ログ設定も含めてまっさらな初期状態に戻る。OS 本体 (rootfs) や作業ファイルには触れない。
     */
    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
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
        /** `KeyboardFace.KANA.id` / `KeyboardFace.ASCII.id` (設定層から UI を参照しないため文字列で持つ)。 */
        const val FACE_ID_KANA = "kana"
        const val FACE_ID_ASCII = "ascii"
        /** 入力メソッドの初回は英字面から。以後は最後に使った面を覚える。 */
        const val DEFAULT_IME_FACE = FACE_ID_ASCII
        /** 数字面は既定 ON (この面を足すこと自体が 0.8.305 の要望)。 */
        const val DEFAULT_KEYBOARD_NUMBER_FACE = false
        /** 巡回順の既定は「あ → A → 12」(`KeyboardFace.ORDER_ASCII_FIRST_ID`)。 */
        const val DEFAULT_KEYBOARD_FACE_ORDER = "kana_ascii_number"
        const val DEFAULT_KEEP_ALIVE = true
        /** 画面消灯ロックは既定 OFF (放置でのバッテリ消費を避ける)。トグル状態は永続化して復元。 */
        const val DEFAULT_KEEP_SCREEN_ON = false
        /** キーボードトグルバーは既定 ON (従来どおりキーボードの上に表示)。OFF で ⌨ ダブルタップ切替。 */
        const val DEFAULT_KEYBOARD_TOGGLE_BAR = true
        /** 補助キーバーは既定 ON (従来どおり OS キーボードの上に表示)。 */
        const val DEFAULT_SPECIAL_KEY_BAR = true

        /** 実行エンジン: 非 root の自前 ptrace エンジン z2root (既定エンジン。foss/full 共通で常用) */
        const val ENGINE_Z2ROOT = "z2root"
        /** 実行エンジン: root で実 chroot (裏機能・要解放) */
        const val ENGINE_CHROOT = "chroot"
        /** 実行エンジン: z2root 起動失敗時の Android /system/bin/sh フォールバック (選択不可・表示専用)。 */
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

        const val MIN_FONT_SIZE_SP = 4f
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
        // ⚠ 0.8.304 以前の面の設定。読み替えのためだけに残してある (書き込みはもうしない)。
        private val KEY_IME_JAPANESE_MODE = booleanPreferencesKey("ime_japanese_mode")
        private val KEY_IME_FACE = stringPreferencesKey("ime_face")
        private val KEY_KEYBOARD_NUMBER_FACE = booleanPreferencesKey("keyboard_number_face")
        private val KEY_KEYBOARD_FACE_ORDER = stringPreferencesKey("keyboard_face_order")
        private val KEY_KEEP_ALIVE = booleanPreferencesKey("keep_alive_service")
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val KEY_SCREEN_BRIGHTNESS = floatPreferencesKey("screen_brightness")
        private val KEY_KEYBOARD_TOGGLE_BAR = booleanPreferencesKey("keyboard_toggle_bar")
        private val KEY_SPECIAL_KEY_BAR = booleanPreferencesKey("special_key_bar")
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
        private val KEY_TOOLBAR_HIDDEN = stringPreferencesKey("toolbar_hidden")
        private val KEY_SESSION_LOG_DIR = stringPreferencesKey("session_log_dir")
        private val KEY_SESSION_LOG_NAME = stringPreferencesKey("session_log_name")
        private val KEY_SESSION_LOG_TIME = stringPreferencesKey("session_log_time_format")
        private val KEY_SESSION_LOG_SCROLLBACK = booleanPreferencesKey("session_log_scrollback")
        private val KEY_SESSION_LOG_APPEND = booleanPreferencesKey("session_log_append")
        private val KEY_SESSION_LOG_RAW = booleanPreferencesKey("session_log_raw")
        private val KEY_SESSION_LOG_ALT_SCREEN = booleanPreferencesKey("session_log_alt_screen")

        /** 端末ログの既定の保存先 (ホームからの相対)。 */
        const val DEFAULT_SESSION_LOG_DIR = "z2term-log"
        /** 端末ログの既定のファイル名テンプレート。 */
        const val DEFAULT_SESSION_LOG_NAME = "{date}-{tab}.txt"
        /** 端末ログの既定の日時書式。 */
        const val DEFAULT_SESSION_LOG_TIME = "yyyy-MM-dd_HHmm"
        /** 記録開始時に過去分を書き出すか (既定 OFF = 押した時点から先だけ)。 */
        const val DEFAULT_SESSION_LOG_SCROLLBACK = false
        /** 同名ファイルへの追記 (既定 OFF = 毎回新しいファイル)。 */
        const val DEFAULT_SESSION_LOG_APPEND = false
        /** エスケープをそのまま残すか (既定 OFF = プレーンテキスト)。 */
        const val DEFAULT_SESSION_LOG_RAW = false
        /** 全画面表示 (alt screen) 中も書くか (既定 OFF)。 */
        const val DEFAULT_SESSION_LOG_ALT_SCREEN = false
        /** 新しいタブが繋がったら自動で記録を始めるか (既定 OFF)。 */
        const val DEFAULT_SESSION_LOG_AUTO_START = false
        /** 鍵・トークンらしき部分を伏せ字にするか (既定 ON)。 */
        const val DEFAULT_SESSION_LOG_MASK = true
        private val KEY_SESSION_LOG_AUTO_START = booleanPreferencesKey("session_log_auto_start")
        private val KEY_SESSION_LOG_MASK = booleanPreferencesKey("session_log_mask_secrets")
        private val KEY_SESSION_LOG_TIMESTAMP = booleanPreferencesKey("session_log_timestamp")
        const val DEFAULT_SESSION_LOG_TIMESTAMP = false
        private val KEY_TRACE_LOG = booleanPreferencesKey("trace_log_enabled")
        private val KEY_KITTY_EXTERNAL_FILE = booleanPreferencesKey("kitty_external_file_enabled")
        private val KEY_SGR_MOUSE_INPUT = booleanPreferencesKey("sgr_mouse_input_enabled")
        private val KEY_SERVER_ENTRIES = stringPreferencesKey("server_entries")
        private val KEY_SERVERS_AUTOSTART = booleanPreferencesKey("servers_autostart_on_boot")
        private val KEY_SERVERS_LOW_POWER = booleanPreferencesKey("servers_low_power")
        private val KEY_NOTIFICATION_CAPTURE = booleanPreferencesKey("notification_capture_enabled")
        private val KEY_NOTIFICATION_LOG = booleanPreferencesKey("notification_log_enabled")
        private val KEY_NOTIFICATION_LOG_FORMAT = stringPreferencesKey("notification_log_format")
        private val KEY_SYSTEM_EVENT_CAPTURE = booleanPreferencesKey("system_event_capture_enabled")
        private val KEY_INTRO_DONE = booleanPreferencesKey("intro_done")
        private val KEY_TERMINAL_HINTS = booleanPreferencesKey("terminal_hints")
        private val KEY_SYSTEM_EVENT_LOG_FORMAT = stringPreferencesKey("system_event_log_format")
        private val KEY_NOTIFICATION_LOG_PREPEND = booleanPreferencesKey("notification_log_prepend")
        private val KEY_SYSTEM_EVENT_LOG_PREPEND = booleanPreferencesKey("system_event_log_prepend")
        private val KEY_UNLOCK_WATCH = booleanPreferencesKey("unlock_watch_enabled")
        private val KEY_SMS_CAPTURE = booleanPreferencesKey("sms_capture_enabled")
        private val KEY_SMS_LOG_FORMAT = stringPreferencesKey("sms_log_format")
        private val KEY_SMS_LOG_PREPEND = booleanPreferencesKey("sms_log_prepend")

        /** 通知検知は既定 OFF (明示 opt-in + OS の通知アクセス許可が要る)。 */
        const val DEFAULT_NOTIFICATION_CAPTURE = false
        /** 通知ログのファイル保存は既定 ON (検知が ON のときだけ効く。OFF で検知のみ)。 */
        const val DEFAULT_NOTIFICATION_LOG = true
        /** 通知ログのフォーマットテンプレート。空文字 = JSONL (機械可読・既定)。 */
        const val DEFAULT_NOTIFICATION_LOG_FORMAT = ""

        /** システムイベント検知は既定 OFF (明示 opt-in で FG サービスを常駐させる)。 */
        const val DEFAULT_SYSTEM_EVENT_CAPTURE = false
        /** システムイベントログのフォーマットテンプレート。空文字 = JSONL (機械可読・既定)。 */
        const val DEFAULT_SYSTEM_EVENT_LOG_FORMAT = ""

        /** ログ書き込みは既定で末尾追記 (新着が下)。ON で先頭追記 (新着が上)。 */
        const val DEFAULT_LOG_PREPEND = false

        /** ロック解除の失敗監視は既定 OFF (明示 opt-in + 端末管理者の有効化が要る)。 */
        const val DEFAULT_UNLOCK_WATCH = false

        /** SMS 受信検知は既定 OFF (明示 opt-in + OS の RECEIVE_SMS 許可が要る)。 */
        const val DEFAULT_SMS_CAPTURE = false
        /** SMS ログのフォーマットテンプレート。空文字 = JSONL (機械可読・既定)。 */
        const val DEFAULT_SMS_LOG_FORMAT = ""

        /** 常駐サーバーの起動時自動起動は既定 OFF (明示 opt-in)。 */
        const val DEFAULT_SERVERS_AUTOSTART = false
        /** 常駐サーバーの省電力モードは既定 OFF (既定は到達性優先で WakeLock/WifiLock を握る)。 */
        const val DEFAULT_SERVERS_LOW_POWER = false

        /** z2root syscall トレースログは既定 OFF (開発者用。ログが膨大で容量を圧迫する)。 */
        const val DEFAULT_TRACE_LOG = false
        /**
         * Kitty graphics `t=f`/`t=t`/`t=s` 経由の外部ファイル読込は **既定 OFF**。
         * TUI から任意ファイル読取を許可する経路なので、 明示 opt-in したセッションだけで
         * ホスト/ゲスト変換 + 実 I/O を行う。
         */
        const val DEFAULT_KITTY_EXTERNAL_FILE = false
        /**
         * 画面タップ→SGR mouse 送出 (button 0/2/32) は **既定 OFF**。 ON にすると 1 指 tap/
         * 長押し/ドラッグが mouse capture 中の TUI に届くようになり、 Z2Term 自身のテキスト
         * 選択や long-press メニューは封じられる。 二本指スワイプ→wheel は opt-in に関係なく
         * 従来通り送出する。
         */
        const val DEFAULT_SGR_MOUSE_INPUT = false

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
