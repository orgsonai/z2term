package com.zerotoship.z2term

import android.app.Application
import android.content.Context
import android.util.Log
import com.zerotoship.z2term.clipboard.ClipboardHistoryStore
import com.zerotoship.z2term.gui.GuiEventWatcher
import com.zerotoship.z2term.service.ScreenTimeout
import com.zerotoship.z2term.service.SystemEventService
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.service.Z2ApiBridge
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Z2Term アプリケーション本体。
 *
 * M1 段階では、ネイティブライブラリのロードのみ実施。
 * M3 以降で Service の事前初期化、設定管理の初期化などを追加予定。
 *
 * P3 (CUI⇄GUI 連動): プロセス常駐の [GuiEventWatcher] をここで起動する。端末タブ内の
 * `z2run` から飛んでくる `OPEN N` 通知を受け取り、対応する GUI タブを開く / 前面化する。
 */
class Z2TermApplication : Application() {

    /**
     * アプリ全体の Context にも [LocaleHelper] を反映 (Toast / Service など Activity 外で
     * 取得する文字列の Locale を揃えるため)。Activity 側は [MainActivity.attachBaseContext]
     * でさらに同じ扱いをするので二重適用になるが、結果は同じ Locale なので無害。
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Z2Term application starting (version=${BuildConfig.VERSION_NAME})")
        // proot 内 `/storage/app/z2gui.events` (= 外部 files dir の同名ファイル) を監視開始。
        // 二重 start しても idempotent。Activity/Service ライフサイクルから独立して常駐する。
        GuiEventWatcher.start(this)
        // Android API ブリッジ (`z2-notify` 等) のリクエスト監視を開始 (Termux:API 相当)。
        Z2ApiBridge.start(this)
        // クリップボード履歴: ディスク読込 + システムクリップボード変化の監視を開始。
        ClipboardHistoryStore.init(this)
        // z2-when (A6) の時刻トリガーを貼り直す (AlarmManager 予約は再起動で消えるため。
        // BootReceiver でも貼るが、アプリを普通に開いた場合の取りこぼしをここで埋める)。idempotent。
        appScope.launch { runCatching { WhenManager.reload(this@Z2TermApplication) } }
        // z2-screen keepon も同様に、掛かったままなら予約を貼り直す (期限切れならその場で書き戻す)。
        // 消灯しない状態を取りこぼすと電池が静かに減り続けるので、入口を 2 つ持つ。
        appScope.launch {
            runCatching { ScreenTimeout.restoreOrReschedule(this@Z2TermApplication) }
                .onFailure { Log.w(TAG, "screen timeout restore skipped: ${it.message}") }
        }
        // システムイベント検知が ON なら常駐 FG サービスを起動 (アプリ前面起動時に再アサート)。
        // background から起動した場合は FG サービス起動が禁止されうるので握りつぶす (BootReceiver 側で別途起動)。
        appScope.launch {
            runCatching {
                if (AppSettings(this@Z2TermApplication).flow.first().systemEventCaptureEnabled) {
                    SystemEventService.start(this@Z2TermApplication)
                }
            }.onFailure { Log.w(TAG, "system event service autostart skipped: ${it.message}") }
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val TAG = "Z2Term"
    }
}
