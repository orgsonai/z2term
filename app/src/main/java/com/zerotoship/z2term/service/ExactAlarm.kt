package com.zerotoship.z2term.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import android.util.Log

/**
 * Doze を貫通する 1 回きりのアラームを、**置ける限り正確に**置く (0.8.332)。
 *
 * **なぜ 1 か所にまとめたか**: 時刻で動くものが 3 系統ある — `z2-alarm` ([AlarmScheduler])、
 * `z2-when time:*` ([WhenManager])、`z2-screen keepon` の期限 ([ScreenTimeout])。3 つとも
 * `setAndAllowWhileIdle` (不正確) を各自で呼んでいたため、**片方だけ直すと挙動が食い違う**。
 * 「Doze を貫通する 1 回きりのアラーム」という同じ用件なので、置き方も 1 つにする。
 *
 * **なぜ manifest に権限を足さないか**: `setExactAndAllowWhileIdle` は API31+ で「正確なアラーム」
 * が要るが、⚠ **電池の最適化を除外しているアプリは、権限を宣言しなくても OS 側が免除する**
 * (`AlarmManagerService` の allow-list 免除。実機の `dumpsys alarm` に
 * `exactAllowReason=allow-listed` として現れる)。z2term は常駐サーバーのために最適化除外を
 * お願いしているので、実際にはほぼ常に正確側で動く。除外されていない端末では静かに
 * 不正確側へ落ちるだけで、**予定そのものは落とさない**。
 *
 * 不正確側の実害: Doze 中は発火の機会が概ね 9〜15 分に 1 回しか回ってこないため、画面を消して
 * 放置していると**数分〜15 分ほど遅れる**。いまどちら側かは `z2-alarm list` の `exact` で見える。
 */
internal object ExactAlarm {

    private const val TAG = "ExactAlarm"

    /**
     * いま「正確なアラーム」を置けるか。API30 以下は常に置ける。
     *
     * ⚠ **調べた結果を覚えない**。許可は設定からいつでも変わるので、置くたびに聞く。
     */
    fun canBeExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)

    /**
     * [at] (epoch ミリ秒) に RTC_WAKEUP で 1 発置く。置けるなら正確に、駄目なら不正確に。
     *
     * [tag] はログ用の呼び元識別 (どの系統が失敗したのか分からないと追えない)。
     */
    // canScheduleExactAlarms() で可否を見たうえで呼び、駄目なら下の recoverCatching で
    // 不正確側へ落とす。manifest に SCHEDULE_EXACT_ALARM は足さない方針なので lint は抑止する。
    @SuppressLint("MissingPermission")
    fun setWakeup(am: AlarmManager, at: Long, pi: PendingIntent, tag: String) {
        runCatching {
            if (canBeExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }.recoverCatching {
            // ⚠ 可否を聞いた直後に許可が剥がれると SecurityException になる。**予定を落とさない**
            // ことが最優先なので、不正確側で置き直す (置けないまま黙って消えるのが最悪)。
            Log.w(TAG, "exact refused ($tag); inexact instead", it)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }.onFailure { Log.w(TAG, "schedule failed ($tag)", it) }
    }
}
