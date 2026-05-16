package com.zerotoship.z2term

import android.app.Application
import android.util.Log

/**
 * Z2Term アプリケーション本体。
 *
 * M1 段階では、ネイティブライブラリのロードのみ実施。
 * M3 以降で Service の事前初期化、設定管理の初期化などを追加予定。
 */
class Z2TermApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Z2Term application starting (version=${BuildConfig.VERSION_NAME})")
    }

    companion object {
        const val TAG = "Z2Term"
    }
}
