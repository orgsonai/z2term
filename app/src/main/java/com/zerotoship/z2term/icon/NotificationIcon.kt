package com.zerotoship.z2term.icon

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zerotoship.z2term.R

/**
 * 通知アイコンを [IconStore] の差し替えに追従させるための入口。
 *
 * ⚠ **[IconStore] と同じファイルに置かない**。`object` とトップレベル関数を 1 ファイルへ混ぜると、
 * Android lint (K2 UAST) がそのファイルの解析中に `ClassCastException` で落ち、**lint そのものが
 * 中断する**（0.8.294 で実際に踏んだ。落ちるのは lint だけで、コンパイルも実行も通るため
 * 気付きにくい）。置き場を分けておけば避けられる。
 */

/**
 * 通知の小アイコンを付ける。差し替えたドット絵があればそれ、無ければ既定の `ic_notification`。
 *
 * ⚠ **通知を作るところは必ずこれを通すこと**。素の `setSmallIcon(R.drawable.ic_notification)` が
 * 1 か所でも残ると、そこだけ差し替えが効かない通知になる (どれが漏れているか外から分からない)。
 */
fun NotificationCompat.Builder.setZ2SmallIcon(context: Context): NotificationCompat.Builder {
    val custom = IconStore.notificationIcon(context)
    return if (custom != null) setSmallIcon(custom) else setSmallIcon(R.drawable.ic_notification)
}

/**
 * **いま出ている通知**のアイコンを差し替える (`z2-icon set notify` の直後に呼ぶ)。
 *
 * これが無いと、常駐通知は次に作り直されるまで古いアイコンのままになる。常駐は普段
 * 作り直されない (それが常駐の意味) ので、差し替えたのに何も起きないように見える。
 *
 * `Notification.Builder.recoverBuilder` は**出ている通知から組み立て直す**ための API。
 * アイコンだけ差し替えて同じ ID で出し直すので、本文もボタンも常駐の扱いもそのまま残る。
 *
 * ⚠ `setOnlyAlertOnce` を付ける — 付けないと、出し直した通知が**もう一度音を出す**。
 */
fun refreshActiveNotifications(context: Context) {
    val app = context.applicationContext
    val nm = app.getSystemService(NotificationManager::class.java) ?: return
    val custom = IconStore.bitmap(app, IconStore.TARGET_NOTIFY)?.let { Icon.createWithBitmap(it) }
    val active = runCatching { nm.activeNotifications }.getOrNull() ?: return
    active.forEach { sbn ->
        runCatching {
            val builder = Notification.Builder.recoverBuilder(app, sbn.notification)
            if (custom != null) builder.setSmallIcon(custom)
            else builder.setSmallIcon(R.drawable.ic_notification)
            builder.setOnlyAlertOnce(true)
            nm.notify(sbn.tag, sbn.id, builder.build())
        }.onFailure { Log.w("IconStore", "refresh notification ${sbn.id} failed", it) }
    }
}
