package com.zerotoship.z2term.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * **精度と権限のトレードオフ**: `setExactAndAllowWhileIdle` は API31+ で `SCHEDULE_EXACT_ALARM`
 * (ユーザー許可) が要る。マクロ用途で数分のズレは許容できるので、**権限が要らない**
 * `setAndAllowWhileIdle` (Doze 貫通・不正確) を使う。実際の発火は指定時刻から**数分遅れることがある**。
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

    /** 登録済みアラームを JSON 配列で返す (発火予定の早い順)。 */
    fun listJson(context: Context): String {
        val arr = JSONArray()
        load(context).sortedBy { it.at }.forEach { arr.put(it.toJson()) }
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
        emitEvent(context, entry.name)
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

    /**
     * `alarm` イベントを events.jsonl へ書く。
     *
     * **設定「システムイベント検知」の ON/OFF に関係なく書く**: アラームはユーザーが明示的に
     * 仕掛けたものなので、検知トグル (受動的なイベントの取捨) とは独立に扱うほうが直感的。
     * 出力フォーマット (`{name}` を含むテンプレート) と「新しいものを先頭に」は設定に従う。
     */
    private fun emitEvent(context: Context, name: String) {
        val settings = runCatching { runBlocking { AppSettings(context).flow.first() } }.getOrNull()
        val now = System.currentTimeMillis()
        val line = SystemEventService.render(
            settings?.systemEventLogFormat.orEmpty(),
            ts = now,
            time = ISO.format(Date(now)),
            event = "alarm",
            level = null,
            ssid = "",
            name = name
        )
        runCatching {
            LogWriter.write(
                SystemEventService.logFile(context),
                line,
                settings?.systemEventLogPrepend ?: false
            )
        }.onFailure { Log.w(TAG, "alarm event write failed: ${it.message}") }
    }

    private fun schedule(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // setAndAllowWhileIdle: Doze を貫通しつつ SCHEDULE_EXACT_ALARM 権限が要らない
        // (代わりに発火が数分ずれることがある。マクロ用途では許容)。
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.at, pendingIntent(context, entry.id))
        }.onFailure { Log.w(TAG, "schedule failed for ${entry.id}", it) }
    }

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
