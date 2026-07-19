package com.zerotoship.z2term.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 画面ロック解除の**失敗/成功**を検知して `~/.z2term/events.jsonl` へ流す端末管理者レシーバ。
 *
 * Android は通常アプリにロック解除失敗のコールバックを渡さないが、**端末管理者 (Device Admin)**
 * として登録し `watch-login` ポリシーを宣言したアプリには [onPasswordFailed] / [onPasswordSucceeded]
 * が届く (盗難対策アプリが使う定番の仕組み)。ここではその 2 つを [EventEmitter] に橋渡しするだけで、
 * **撮影・送信・警報などのアクションは一切ハードコードしない** (ユーザーがマクロで組む)。
 *
 * 用いるのは失敗回数取得 (`watch-login`) のみ。**遠隔ロック / ワイプ / パスワード強制などの
 * 破壊的ポリシーは宣言も行使もしない** (`device_admin.xml` にも載せない)。
 *
 * 設定「ロック解除の失敗監視」が OFF のときは、管理者が有効なままでも events.jsonl へ書かない
 * (トグルを検知の主スイッチにする)。管理者の有効/無効そのものは OS の端末管理者画面で行う。
 */
class PasswordWatchAdmin : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent) {
        if (!enabled(context)) return
        // 直近の連続失敗回数 (成功でリセットされる)。マクロが「N 回目から作動」を書けるよう {level} に載せる。
        val attempts = runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.currentFailedPasswordAttempts
        }.getOrNull()
        EventEmitter.emit(context, "unlock_failed", level = attempts)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        if (!enabled(context)) return
        EventEmitter.emit(context, "unlock_succeeded")
    }

    private fun enabled(context: Context): Boolean =
        runCatching { runBlocking { AppSettings(context).flow.first() }.unlockWatchEnabled }
            .onFailure { Log.w(TAG, "settings read failed", it) }
            .getOrDefault(false)

    companion object {
        private const val TAG = "PasswordWatchAdmin"

        /** この管理者コンポーネント。有効化ダイアログの `EXTRA_DEVICE_ADMIN` に渡す。 */
        fun component(context: Context): ComponentName =
            ComponentName(context, PasswordWatchAdmin::class.java)

        /** 端末管理者として有効化済みか。 */
        fun isActive(context: Context): Boolean = runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isAdminActive(component(context))
        }.getOrDefault(false)
    }
}
