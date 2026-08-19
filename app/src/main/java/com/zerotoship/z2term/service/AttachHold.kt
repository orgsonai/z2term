package com.zerotoship.z2term.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * **繋いでいる間だけ常駐枠に入れる** (`z2-session attach`・ユーザー判断 2026-08-20)。
 *
 * PC から繋いだまま作業していて、途中でアプリが落とされて切れるのが一番困る。繋いでいる間は
 * [TerminalService]（🔒 が起こしているのと同じサービス。役目がまさに「セッションのプロセスを
 * 生かすこと」）に入れておき、最後の 1 人が抜けたら下ろす。**新しいサービスは作らない。**
 *
 * ⚠⚠ **設定値 [AppSettings.keepAliveService] は書き換えない。** 🔒 の表示は設定値を見ている
 * だけなので、attach のために設定を触ると **抜けたあともユーザーの設定が変わったまま**になる。
 * 常駐サーバー側が既に同じことをしていて、設定シートは
 * `checked = settings.keepAliveService || serversRunning` と **表示だけ足し込む**形にしてある。
 * 同じ流儀に揃える。
 *
 * ⚠ **下ろしてよいのは「自分が起こした場合」だけ**。🔒 が ON か、常駐サーバーが 1 本でも
 * 動いているなら、そちらの都合で常駐しているので触らない（attach から抜けたついでに他人の
 * 常駐を切ってはいけない）。
 */
object AttachHold {

    private const val TAG = "AttachHold"

    private val lock = Any()
    private var count = 0

    /** 今いくつ繋がっているか（通知の文言に使う）。 */
    @Volatile
    var attached: Int = 0
        private set

    fun acquire(context: Context) {
        val appCtx = context.applicationContext
        synchronized(lock) {
            count++
            attached = count
        }
        // 起こし直すと onStartCommand が走って通知が組み直される（文言が「接続中」になる）。
        runCatching { TerminalService.start(appCtx) }
            .onFailure { Log.w(TAG, "cannot hold: ${it.message}") }
    }

    fun release(context: Context) {
        val appCtx = context.applicationContext
        val last: Boolean
        synchronized(lock) {
            if (count > 0) count--
            attached = count
            last = count == 0
        }
        if (!last) {
            runCatching { TerminalService.start(appCtx) }
            return
        }
        if (otherReasonToStayResident(appCtx)) {
            // 🔒 か常駐サーバーの都合で常駐している。文言だけ戻す。
            runCatching { TerminalService.start(appCtx) }
            return
        }
        // 自分が起こしたぶんだけ下ろす。セッションは生かしたまま（ACTION_DETACH）。
        runCatching {
            appCtx.startService(
                Intent(appCtx, TerminalService::class.java).setAction(TerminalService.ACTION_DETACH)
            )
        }.onFailure { Log.w(TAG, "cannot release: ${it.message}") }
    }

    private fun otherReasonToStayResident(context: Context): Boolean {
        val keepAlive = runCatching {
            runBlocking { AppSettings(context).flow.first().keepAliveService }
        }.getOrDefault(false)
        return keepAlive || ServerDaemonManager.isRunning
    }
}
