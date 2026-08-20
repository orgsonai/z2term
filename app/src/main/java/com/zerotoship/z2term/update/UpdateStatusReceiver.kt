package com.zerotoship.z2term.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.io.File

/**
 * `PackageInstaller` からの結果を受ける (0.8.371)。
 *
 * ⚠ **`STATUS_PENDING_USER_ACTION` を捨てない**。OS は「入れていいか」の確認画面を
 * **この結果に添えた Intent** としてよこすだけで、自分では出さない。受け取って
 * `startActivity` するまで、利用者からは**押したのに何も起きない**ように見える。
 *
 * ⚠ 成功のときに**自分が生きている保証は無い** (入れ替えで落とされる)。ここでの後片付けは
 * 「受け取れたら早めに済ませる」程度の意味で、確実な掃除は次の起動時
 * ([UpdateInstaller.cleanupDownloads]) が受け持つ。
 */
class UpdateStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        val keep = intent.getBooleanExtra(EXTRA_KEEP, false)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntentOf(intent)
                if (confirm == null) {
                    Log.w(TAG, "pending user action without an intent")
                    return
                }
                // ⚠ 端末に出す以上 NEW_TASK が要る (アプリが前面にいるとは限らない。
                //    `z2-update` は SSH の向こうから叩かれることもある)。
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { Log.w(TAG, "cannot show the install prompt", it) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "update installed")
                if (!keep) deleteApk(apkPath)
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                Log.w(TAG, "update failed: $msg")
                // ⚠ 断られたときに黙らない。理由が出ないと「押したのに入らない」で終わる。
                runCatching { Toast.makeText(context, "z2-update: $msg", Toast.LENGTH_LONG).show() }
                if (!keep) deleteApk(apkPath)
            }
        }
    }

    private fun confirmIntentOf(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        }

    private fun deleteApk(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    companion object {
        private const val TAG = "UpdateStatus"

        /** 入れ替えに使った APK の場所 (片付ける対象)。 */
        const val EXTRA_APK_PATH = "com.zerotoship.z2term.extra.APK_PATH"

        /** 入れ終わっても APK を残すか (設定 / `z2-update --keep`)。 */
        const val EXTRA_KEEP = "com.zerotoship.z2term.extra.KEEP_APK"
    }
}
