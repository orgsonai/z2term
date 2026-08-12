package com.zerotoship.z2term.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * 時刻トリガー (`z2-alarm`)。指定した時刻になったら [SystemEventService.logFile]
 * (`~/.z2term/events.jsonl`) へ `alarm` イベントを 1 行追記する。
 *
 * **なぜアプリ側に要るか**: 「毎朝 7 時に」のような時刻マクロは従来 distro の cron に頼っていたが、
 * (1) cron の導入が distro ごとに要る (2) **Android の Doze で端末が眠ると cron 自体が動かない**
 * ため、実質的に画面を点けている間しか効かなかった。AlarmManager 経由なら Doze 中でも OS が
 * アプリを起こすので、時刻マクロが初めて実用になる。
 *
 * **精度と権限のトレードオフ**: `setExactAndAllowWhileIdle` は API31+ で「正確なアラーム」が要る。
 * **manifest には権限を足さない** — 代わりに [canBeExact] で*その場で*可否を聞き、駄目なら
 * `setAndAllowWhileIdle` (Doze 貫通・不正確) へ落ちる (0.8.332)。
 *
 * ⚠ **電池の最適化を除外しているアプリは、権限を宣言しなくても正確なアラームが許される**
 * (AlarmManagerService の allow-list 免除。実機の `dumpsys alarm` に
 * `exactAllowReason=allow-listed` として出る)。z2term は常駐サーバーのために最適化除外を
 * お願いしているので、**実際にはほぼ常に正確側で動く**。除外を外されたときは自動的に
 * 不正確側へ戻るだけで、落ちない。
 *
 * 不正確側に落ちたときの実害: Doze 中は発火の機会が概ね 9〜15 分に 1 回しか回ってこないため、
 * **画面を消して放置していると数分〜15 分ほど遅れる**。可否は `z2-alarm list` の `exact` で見える。
 *
 * 永続化は `filesDir/alarms.json`。端末再起動で AlarmManager の予約は消えるため、
 * [BootReceiver] と [rescheduleAll] で貼り直す。
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val FILE_NAME = "alarms.json"

    /** 1 件のアラーム。[kind] が `daily` なら発火後に翌日へ再セット、`once` なら削除する。 */
    data class AlarmEntry(
        val id: Int,
        val name: String,
        val kind: String,
        /** 次に発火するエポックミリ秒。 */
        val at: Long,
        /** `daily` のときの時刻 (再セット用)。`once` では -1。 */
        val hour: Int = -1,
        val minute: Int = -1
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("kind", kind)
            put("at", at)
            put("time", ISO.format(Date(at)))
            if (kind == KIND_DAILY) {
                put("hour", hour)
                put("minute", minute)
            }
        }

        companion object {
            fun fromJson(o: JSONObject) = AlarmEntry(
                id = o.optInt("id"),
                name = o.optString("name"),
                kind = o.optString("kind", KIND_ONCE),
                at = o.optLong("at"),
                hour = o.optInt("hour", -1),
                minute = o.optInt("minute", -1)
            )
        }
    }

    const val KIND_ONCE = "once"
    const val KIND_DAILY = "daily"

    private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    // 複数プロセス (アプリ / レシーバ) から触るので、読み書きはこのオブジェクトで直列化する。
    private val lock = Any()

    // --- 公開 API (Z2ApiBridge から呼ぶ) ---

    /**
     * アラームを 1 件登録する。[hour]/[minute] 指定なら「今日のその時刻、既に過ぎていれば明日」。
     * 戻り値は登録内容の JSON (CLI がそのまま表示する)。
     */
    fun add(context: Context, name: String, kind: String, at: Long, hour: Int, minute: Int): String {
        val entry = AlarmEntry(
            id = Random.nextInt(1, Int.MAX_VALUE),
            name = name.ifBlank { "alarm" },
            kind = kind,
            at = at,
            hour = hour,
            minute = minute
        )
        synchronized(lock) {
            val list = load(context).toMutableList()
            list.add(entry)
            save(context, list)
        }
        schedule(context, entry)
        return entry.toJson().toString()
    }

    /**
     * 登録済みアラームを JSON 配列で返す (発火予定の早い順)。
     *
     * 各件に `exact` を足してある (0.8.332)。⚠ **配列のままにする** — `z2-alarm list | jq '.[0].at'`
     * のような手元の書き方を壊さないため、器を変えずに項目を 1 つ増やす形にした。
     * `false` の日は「画面を消していると数分〜15 分遅れる」の説明になる ([ExactAlarm])。
     */
    fun listJson(context: Context): String {
        val exact = isExact(context)
        val arr = JSONArray()
        load(context).sortedBy { it.at }.forEach { arr.put(it.toJson().put("exact", exact)) }
        return arr.toString()
    }

    /**
     * [key] に一致するアラームを取り消す。`all` なら全件、数字なら id、それ以外は name 一致。
     * @return 取り消した件数
     */
    fun cancel(context: Context, key: String): Int {
        val removed: List<AlarmEntry>
        synchronized(lock) {
            val list = load(context)
            val target = when {
                key.equals("all", ignoreCase = true) -> list
                key.toIntOrNull() != null -> list.filter { it.id == key.toInt() }
                else -> list.filter { it.name == key }
            }
            if (target.isEmpty()) return 0
            removed = target
            save(context, list - target.toSet())
        }
        removed.forEach { unschedule(context, it) }
        return removed.size
    }

    /** 端末起動後・アプリ起動時に、保存済みアラームの予約を貼り直す (過ぎた once は捨てる)。 */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        val kept = ArrayList<AlarmEntry>()
        synchronized(lock) {
            for (e in load(context)) {
                when {
                    // 再起動中に時刻を過ぎた daily は次の該当時刻へ送る (取りこぼしても鳴り続けさせない)。
                    e.kind == KIND_DAILY -> kept.add(e.copy(at = nextDailyAt(e.hour, e.minute)))
                    e.at > now -> kept.add(e)
                    // 過ぎてしまった once は捨てる (後追いで発火させると意図しない動作になる)。
                    else -> Log.i(TAG, "dropping stale alarm ${e.id} (${e.name})")
                }
            }
            save(context, kept)
        }
        kept.forEach { schedule(context, it) }
    }

    /**
     * レシーバから呼ぶ発火処理。events.jsonl へ 1 行書き、`daily` なら翌日へ再セット、
     * `once` なら保存から削除する。
     */
    fun onFired(context: Context, id: Int) {
        val entry = load(context).firstOrNull { it.id == id }
        if (entry == null) {
            Log.w(TAG, "fired unknown alarm id=$id")
            return
        }
        EventEmitter.emit(context, event = "alarm", name = entry.name)
        if (entry.kind == KIND_DAILY) {
            val next = entry.copy(at = nextDailyAt(entry.hour, entry.minute))
            synchronized(lock) {
                save(context, load(context).map { if (it.id == id) next else it })
            }
            schedule(context, next)
        } else {
            synchronized(lock) {
                save(context, load(context).filterNot { it.id == id })
            }
        }
    }

    /** `HH:MM` の次の発火時刻 (今日のその時刻を既に過ぎていれば明日)。 */
    fun nextDailyAt(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    // --- 内部 ---

    // events.jsonl への書き込みは EventEmitter に集約 (通知ボタンの応答と同じ経路)。
    private fun schedule(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // 置き方は [ExactAlarm] に 1 本化 (z2-when time:* / z2-screen keepon と同じ用件のため)。
        ExactAlarm.setWakeup(am, entry.at, pendingIntent(context, entry.id), "alarm ${entry.id}")
    }

    /** `z2-alarm list` に出す「いま正確に置けるか」。遅れの原因を端末から確かめられるようにする。 */
    fun isExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java)?.let { ExactAlarm.canBeExact(it) } ?: false

    private fun unschedule(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pendingIntent(context, entry.id)) }
    }

    private fun pendingIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun load(context: Context): List<AlarmEntry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            List(arr.length()) { AlarmEntry.fromJson(arr.getJSONObject(it)) }
        }.getOrElse {
            Log.w(TAG, "alarms.json broken — starting empty", it)
            emptyList()
        }
    }

    private fun save(context: Context, list: List<AlarmEntry>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            file(context).writeText(arr.toString())
        }.onFailure { Log.w(TAG, "alarms.json write failed", it) }
    }
}
