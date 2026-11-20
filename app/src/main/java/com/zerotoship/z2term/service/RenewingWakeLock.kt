package com.zerotoship.z2term.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * 期限付きで握り、**期限が来る前に取り直し続ける** `PARTIAL_WAKE_LOCK` (1.0.180)。
 *
 * 1.0.179 までの [TerminalService] / [ServerDaemonService] は WakeLock を 8 時間の期限付きで
 * 1 回だけ握り、取り直すのは `onStartCommand` のときだけだった。アプリを起動して 8 時間たつと
 * **サービスは動いているのに WakeLock が 1 本も無い**状態になり、画面を消すと CPU が眠って
 * SSH タブの keepalive も常駐サーバーも止まる (実機: 起動 8 時間後に画面を消すと 15 分ほどで
 * SSH タブが切れた)。
 *
 * ここでは [TIMEOUT_MS] の期限で握り、[RENEW_MS] ごとに取り直す。期限は残したまま
 * なので、取り直しが何かの理由で止まっても、最長 [TIMEOUT_MS] で手放される (握りっぱなしにならない)。
 * 握っている間は CPU が起きているので、取り直しの `postDelayed` は遅れずに回る。
 *
 * ⚠ `setReferenceCounted(false)` の WakeLock は、`acquire(timeout)` を呼び直すと前の期限を
 * 取り消して新しい期限を付け直す (Android の `WakeLock.acquireLocked` が解放の予約を外してから
 * 入れ直す)。取り直しはこの性質に頼っている。
 *
 * どのスレッドから呼んでもよい (取り直しはメインスレッドで回る)。
 */
internal class RenewingWakeLock(context: Context, private val tag: String) {

    private val pm = context.applicationContext.getSystemService(PowerManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var lock: PowerManager.WakeLock? = null
    private var closed = false

    private val renew = object : Runnable {
        override fun run() {
            synchronized(this@RenewingWakeLock) {
                val wl = lock ?: return
                wl.acquire(TIMEOUT_MS)
                handler.postDelayed(this, RENEW_MS)
            }
        }
    }

    val isHeld: Boolean
        @Synchronized get() = lock?.isHeld == true

    /** 握る (握っていれば期限を付け直す)。何度呼んでもよい。[close] の後は何もしない。 */
    @Synchronized
    fun acquire() {
        // ⚠ サービスの起動処理は別スレッドで握ることがあり、その前に onDestroy が走りうる。
        // 閉じた後に握ると、取り直しが回り続けて誰も手放さなくなるので断る。
        if (closed) return
        val wl = lock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
            setReferenceCounted(false)
        }.also { lock = it }
        wl.acquire(TIMEOUT_MS)
        handler.removeCallbacks(renew)
        handler.postDelayed(renew, RENEW_MS)
    }

    /** 手放して取り直しも止める。握っていなくても呼んでよい。 */
    @Synchronized
    fun release() {
        handler.removeCallbacks(renew)
        lock?.let { if (it.isHeld) it.release() }
        lock = null
    }

    /** 手放し、以後 [acquire] されても握らない。サービスの `onDestroy` で呼ぶ。 */
    @Synchronized
    fun close() {
        closed = true
        release()
    }

    companion object {
        /** 1 回に付ける期限。取り直しが止まったときに手放されるまでの最長時間でもある。 */
        const val TIMEOUT_MS = 60L * 60 * 1000
        /** 取り直す間隔。期限より十分短くして、間に合わないことが無いようにする。 */
        const val RENEW_MS = 30L * 60 * 1000
    }
}
