package com.zerotoship.z2term.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * [AutoBackup] が仕掛けた時刻アラームの受け口 (0.8.386)。
 *
 * 書き出しは zip 1 本ぶんのファイル I/O で、Doze 中の起床直後は特に遅い。`goAsync()` で
 * 「まだ処理中」と OS に伝えてから別スレッドで走らせる — これが無いと `onReceive` を抜けた
 * 瞬間にプロセスごと止められ、**中途半端な zip だけが残る**。
 */
class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutoBackup.ACTION_FIRE) return
        val app = context.applicationContext
        val pending = goAsync()
        Thread({
            try {
                AutoBackup.onFired(app)
            } catch (t: Throwable) {
                Log.w("AutoBackupReceiver", "auto backup failed", t)
            } finally {
                pending.finish()
            }
        }, "auto-backup").apply { isDaemon = true }.start()
    }
}
