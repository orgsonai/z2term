package com.zerotoship.z2term.service

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.zerotoship.z2term.R

/**
 * `z2-notify --copy <文字列>` で付けた「コピー」ボタンの受け口 (0.8.335)。
 *
 * ## なぜ画面 (Activity) なのか
 *
 * **Android 10 以降、前面にいないアプリはクリップボードに書けない。** 裏で走ったマクロが
 * `z2-clip set` を呼んでも OS が黙って捨てるだけで、端末側には成功したように見える:
 *
 * ```
 * E ClipboardService: Denying clipboard access to com.zerotoship.z2term,
 *   application is not in focus nor is it a system service for user 0
 * ```
 *
 * 着信・SMS・通知をきっかけに走るマクロは、性質上いつも裏にいる。**書けるのは
 * 「フォーカスを持っている間」か「そのアプリが選択中の入力方法 (IME) のとき」だけ**なので、
 * 自動コピーはこの端末の設定次第で成否が変わる。当てにできる形にするには、**人がボタンを
 * 押した瞬間にフォーカスを取る**しかない。
 *
 * 通知のボタンからこの画面を起こすとシェードが閉じてフォーカスがこちらへ来るので、そこで
 * 書いて即座に閉じる。中身を描かない透明な画面なので、利用者には「ボタンを押したらコピー
 * された」だけが見える。
 *
 * ⚠ **フォーカスが来てから書く** ([onWindowFocusChanged])。[onCreate] や `onResume` の時点では
 * まだウィンドウがフォーカスを取っておらず、同じ拒否に遭う。
 */
class ClipCopyActivity : Activity() {

    /** 二重コピー防止 (フォーカスは出入りのたびに来る)。 */
    private var copied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 押した通知は用済みなので閉じる (z2-notify -b のボタンと同じ扱い)。
        val notifId = intent?.getIntExtra(EXTRA_NOTIF_ID, -1) ?: -1
        if (notifId >= 0) {
            runCatching { NotificationManagerCompat.from(this).cancel(notifId) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || copied) return
        copied = true
        copyAndFinish(intent?.getStringExtra(EXTRA_TEXT).orEmpty())
    }

    /**
     * フォーカスを取れないまま裏へ回されたら、居座らずに閉じる。
     * (ロック画面から押した等でフォーカスが来ない場合の逃げ道。)
     */
    override fun onPause() {
        super.onPause()
        if (!isFinishing) finish()
    }

    private fun copyAndFinish(text: String) {
        if (text.isEmpty()) { finish(); return }
        val ok = runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("z2term", text))
            // 書けたかを読み返して確かめる。拒否されても例外は飛ばず、黙って元のままになる。
            cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() == text
        }.getOrElse { Log.w(TAG, "clip copy failed", it); false }

        // Android 13 以降は OS がコピーの確認 UI を出すので、重ねてトーストを出さない。
        if (!ok) {
            Toast.makeText(this, R.string.clip_copy_failed, Toast.LENGTH_SHORT).show()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.clip_copied, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        private const val TAG = "ClipCopyActivity"
        const val ACTION_COPY = "com.zerotoship.z2term.CLIP_COPY"
        const val EXTRA_TEXT = "text"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
