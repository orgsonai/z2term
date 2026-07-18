package com.zerotoship.z2term.service

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * `~/.z2term/events.jsonl` へ 1 行書く共通処理。
 *
 * [SystemEventService] が拾う受動的なイベントとは別に、**ユーザーが明示的に仕掛けたもの**
 * (時刻トリガー [AlarmScheduler]、通知ボタンの応答 [NotifyActionReceiver]) もここから同じ
 * ファイルへ書く。マクロ側は書き手を区別せず `event` だけを見ればよい。
 *
 * これらは設定「システムイベント検知」の ON/OFF に**依存しない**。あのトグルは
 * 「受動的に流れてくるイベントを集めるか」の設定で、ユーザーが自分で仕掛けた合図とは別物のため。
 * 出力フォーマットと「新しいものを先頭に」は設定に従う。
 */
internal object EventEmitter {

    private const val TAG = "EventEmitter"
    private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    /** [event] を 1 行書く。[name] は仕掛けたときの識別名、[action] は押されたボタン等。 */
    fun emit(context: Context, event: String, name: String = "", action: String = "") {
        val settings = runCatching { runBlocking { AppSettings(context).flow.first() } }.getOrNull()
        val now = System.currentTimeMillis()
        val line = SystemEventService.render(
            settings?.systemEventLogFormat.orEmpty(),
            ts = now,
            time = ISO.format(Date(now)),
            event = event,
            level = null,
            ssid = "",
            name = name,
            action = action
        )
        runCatching {
            LogWriter.write(
                SystemEventService.logFile(context),
                line,
                settings?.systemEventLogPrepend ?: false
            )
        }.onFailure { Log.w(TAG, "event write failed ($event): ${it.message}") }
    }
}
