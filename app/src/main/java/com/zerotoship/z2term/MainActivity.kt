package com.zerotoship.z2term

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.zerotoship.z2term.clipboard.ClipboardHistoryStore
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.service.TerminalService
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.share.SharedIntake
import com.zerotoship.z2term.ui.terminal.TerminalScreen
import com.zerotoship.z2term.ui.theme.Z2TermTheme
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * エントリ Activity。
 *
 * - `SessionManager.ensureFirst` で最初のセッションを確保し、画面に表示する。
 * - `TerminalService` を起動して、Activity 破棄後も PTY を維持。
 * - Android 13+ では `POST_NOTIFICATIONS` をリクエスト (失敗しても起動継続)。
 * - 他アプリの共有シートから来た `ACTION_SEND` を受け取り、端末タブへ入れる (B1)。
 *   タブは常に 1 画面で持つものなので `launchMode="singleTask"`。共有が来るたびに
 *   Activity が積み上がって「戻る」で古い画面が出る、という状態を作らない。
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted/denied どちらでもアプリは継続。永続通知が出ないだけ。 */ }

    /**
     * アプリ内言語スイッチ ([LocaleHelper]) を反映する。OS Locale ではなく
     * `z2term_locale` SharedPreferences の値で `Configuration.setLocale` を上書き。
     * Activity の `recreate()` で再呼出され、英語/日本語切替が即時反映される。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        // 常駐サービスの起動/停止は設定 (keepAliveService) に従い TerminalScreen 側で制御する。

        // ユーザー独自テーマを DataStore から読み込む (テーマ解決前に反映)
        CustomThemeStore.ensureLoaded(applicationContext)

        // 最初のセッションを必ず確保 (TerminalScreen は SessionManager を直接観測)
        SessionManager.ensureFirst(applicationContext)

        setContent {
            Z2TermTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ZtsBgPrimary
                ) {
                    TerminalScreen()
                }
            }
        }

        // 共有シートから起動されたとき (アプリが動いていなかった場合はこちらに来る)。
        handleShareIntent(intent)
    }

    /** 既にアプリが動いている状態で共有された場合 (`singleTask` なのでここに届く)。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * 他アプリから共有されたテキスト / ファイルを端末タブへ入れる (B1)。
     *
     * **入れるだけで実行はしない。** ファイルは `~/z2term-inbox/` に取り込んでからパスを入れる
     * (共有 URI は端末側から触れないため)。取り込みは I/O を含むのでワーカースレッドで行う。
     *
     * 同じ Intent で二度処理しないよう、印を Intent 自身に付ける (画面回転などで `onCreate` が
     * 走り直したときに、同じ内容がもう一度入るのを防ぐ)。
     */
    private fun handleShareIntent(intent: Intent?) {
        val i = intent ?: return
        if (i.action != Intent.ACTION_SEND && i.action != Intent.ACTION_SEND_MULTIPLE) return
        if (i.getBooleanExtra(EXTRA_SHARE_HANDLED, false)) return
        i.putExtra(EXTRA_SHARE_HANDLED, true)
        lifecycleScope.launch {
            val intake = withContext(Dispatchers.IO) {
                runCatching { SharedIntake.intakeFrom(applicationContext, i) }.getOrNull()
            }
            val text = intake?.text
            if (text.isNullOrEmpty()) {
                Toast.makeText(this@MainActivity, R.string.toast_share_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            // z2-when の `share:*` トリガー (0.8.266)。挿入は今までどおり行う (ルールは足し算)。
            // ルール読み込みとエンジン起動を含むので画面のスレッドから外す。
            withContext(Dispatchers.IO) {
                runCatching {
                    WhenManager.onShare(applicationContext, intake.kind, text, intake.fileNames)
                }.onFailure { Log.w("MainActivity", "share rule failed: ${it.message}") }
            }
            val ok = SessionManager.insertText(text)
            Toast.makeText(
                this@MainActivity,
                if (ok) R.string.toast_share_inserted else R.string.toast_share_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // 前面復帰時に現在のシステムクリップボードを履歴へ取り込む。Android 10+ は
        // フォーカス中のみ読取が許可されるため、裏で他アプリがコピーした内容はここで拾う。
        ClipboardHistoryStore.captureCurrent(this)
        // 繋ぎっぱなしの受付 (z2-session attach) が落ちていたら張り直す。張れていれば何もしない。
        // 入口が Application.onCreate だけだと、一度落ちた受付はアプリを開き直しても戻らない。
        com.zerotoship.z2term.service.AttachServer.start(this)
    }

    /**
     * ウィンドウフォーカス獲得時にもクリップボードを取り込む。
     *
     * Android 10+ の「フォーカスのあるアプリだけがクリップボードを読める」制限は
     * *ウィンドウフォーカス* が基準で、[onResume] の時点ではまだフォーカスが確定して
     * おらず primaryClip が空で返る端末がある。それだと「他アプリでコピー → 戻る」が
     * 取りこぼされるので、フォーカス獲得後にもう一度読む。
     * (通知シェード/ダイアログを閉じた時など、前面のままの復帰もここで拾える)
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ClipboardHistoryStore.captureCurrent(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        /** 共有 Intent を処理済みにする印 (同じ Intent での二重挿入を防ぐ)。 */
        const val EXTRA_SHARE_HANDLED = "com.zerotoship.z2term.SHARE_HANDLED"
    }
}
