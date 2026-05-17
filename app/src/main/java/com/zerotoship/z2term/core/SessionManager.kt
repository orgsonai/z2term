package com.zerotoship.z2term.core

import android.content.Context

/**
 * プロセス全体で 1 つの [TerminalSession] を共有するためのシングルトン。
 *
 * UI ViewModel もフォアグラウンドサービスもこの object を経由してセッションに
 * アクセスする。サービスが強参照を保持する間、Activity 破棄でもセッションは
 * 維持される。
 */
object SessionManager {

    @Volatile
    private var instance: TerminalSession? = null

    fun get(context: Context): TerminalSession {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val s = TerminalSession(context.applicationContext)
            instance = s
            return s
        }
    }

    /** セッションを破棄してインスタンスを開放 */
    fun shutdown() {
        synchronized(this) {
            instance?.shutdown()
            instance = null
        }
    }
}
