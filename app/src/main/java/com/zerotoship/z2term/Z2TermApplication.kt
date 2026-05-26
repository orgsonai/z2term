package com.zerotoship.z2term

import android.app.Application
import android.util.Log
import com.zerotoship.z2term.gui.GuiEventWatcher

/**
 * Z2Term アプリケーション本体。
 *
 * M1 段階では、ネイティブライブラリのロードのみ実施。
 * M3 以降で Service の事前初期化、設定管理の初期化などを追加予定。
 *
 * P3 (CUI⇄GUI 連動): プロセス常駐の [GuiEventWatcher] をここで起動する。端末タブ内の
 * `z2run` から飛んでくる `OPEN N` 通知を受け取り、対応する GUI タブを開く / 前面化する。
 */
class Z2TermApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Z2Term application starting (version=${BuildConfig.VERSION_NAME})")
        // proot 内 `/storage/app/z2gui.events` (= 外部 files dir の同名ファイル) を監視開始。
        // 二重 start しても idempotent。Activity/Service ライフサイクルから独立して常駐する。
        GuiEventWatcher.start(this)
    }

    companion object {
        const val TAG = "Z2Term"
    }
}
