package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * `z2-notify -b <ラベル>` で付けたボタンの受け口。押されたら events.jsonl へ
 * `notify_action` イベント (`{name}` = 通知に付けた名前、`{action}` = 押したラベル) を書き、
 * その通知を閉じる。
 *
 * これで「マクロが問いかけ → ユーザーがボタンで答える → マクロが続きを実行する」という
 * **対話型マクロ**が組める。従来 `z2-*` は一方通行 (通知を出すだけ) で、ユーザーの返事を
 * 受け取る手段が無かった。
 */
class NotifyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAP) return
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val appCtx = context.applicationContext
        // 押した通知は残しておく意味が無いので閉じる (押下＝返事が済んだ状態)。
        if (notifId >= 0) {
            runCatching { NotificationManagerCompat.from(appCtx).cancel(notifId) }
        }
        // 書き込みは一瞬だが、ファイル I/O なので goAsync でプロセスの生存を確保する。
        val pending = goAsync()
        Thread {
            try {
                EventEmitter.emit(appCtx, event = "notify_action", name = name, action = label)
            } finally {
                pending.finish()
            }
        }.apply { isDaemon = true }.start()
    }

    companion object {
        const val ACTION_TAP = "com.zerotoship.z2term.NOTIFY_ACTION"
        const val EXTRA_NAME = "name"
        const val EXTRA_LABEL = "label"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
