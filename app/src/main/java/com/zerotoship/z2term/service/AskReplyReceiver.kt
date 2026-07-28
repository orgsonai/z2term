package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

/**
 * `z2-ask` の返信欄で書かれた答えの受け口 (0.8.267)。
 *
 * `z2-notify -b <ラベル>` の [NotifyActionReceiver] が「用意した選択肢のどれを押したか」を
 * 返すのに対し、こちらは**自由入力**を返す。答えは events.jsonl ではなく、
 * **待っている端末側のプロセスへ直接**返す ([Z2ApiBridge.completeAsk] が `resp` を書き、
 * `z2-ask` がそれを標準出力に流す) — 問いかけた本人がそのまま受け取るのが自然なので。
 *
 * ⚠ **答えずに通知を消した場合も必ずここへ来る** ([ACTION_CANCEL] = `setDeleteIntent`)。
 * 何も返さないと端末側が待ち時間いっぱい黙って固まるので、キャンセルは ERR として返す。
 */
class AskReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_REPLY && action != ACTION_CANCEL) return
        val reqId = intent.getStringExtra(EXTRA_REQ_ID).orEmpty()
        if (reqId.isEmpty()) return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val appCtx = context.applicationContext

        // 返信欄の中身。取れなければ (キャンセル時など) null = 「答えなかった」。
        val answer = if (action == ACTION_REPLY) {
            RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(Z2ApiBridge.REMOTE_INPUT_KEY)?.toString()
        } else {
            null
        }

        // 答えたら通知は用済み。⚠ setAutoCancel は返信では効かないので明示的に閉じる
        // (残ると「まだ聞かれている」ように見え、二度目の返信もできてしまう)。
        if (notifId >= 0) {
            runCatching { NotificationManagerCompat.from(appCtx).cancel(notifId) }
        }

        // ファイル I/O なので goAsync でプロセスの生存を確保する (NotifyActionReceiver と同じ)。
        val pending = goAsync()
        Thread {
            try {
                Z2ApiBridge.completeAsk(appCtx, reqId, answer)
            } finally {
                pending.finish()
            }
        }.apply { isDaemon = true }.start()
    }

    companion object {
        const val ACTION_REPLY = "com.zerotoship.z2term.ASK_REPLY"
        const val ACTION_CANCEL = "com.zerotoship.z2term.ASK_CANCEL"
        const val EXTRA_REQ_ID = "req_id"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
