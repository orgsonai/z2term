package com.zerotoship.z2term.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import org.json.JSONObject
import java.io.File

/**
 * OS の**自動画面消灯までの時間** (`Settings.System.SCREEN_OFF_TIMEOUT`) を、
 * **期限つきで**引き延ばす (`z2-screen`)。
 *
 * **⚠ ツールバーの 🔅 とは別物**。🔅 は `FLAG_KEEP_SCREEN_ON` で「**このアプリを開いている間**
 * 消えない」だけ。ここで扱うのは **OS 全体の設定**なので、アプリを畳んでもホームに戻っても効く。
 * 「1 時間だけ自動消灯を止めて長いビルドを眺める」のような用途はこちらでしか満たせない。
 *
 * **なぜアプリ側に要るか**: マクロだけでは不可能。`/system/bin/settings` は rootfs から見えるが、
 * アプリ UID から叩くと `SecurityException` になる (この binder シェルコマンドは `shell` / `root`
 * 専用)。Android の作法どおり `WRITE_SETTINGS` を宣言し、利用者が「システム設定の変更」画面で
 * 明示的に許可したときだけ書き換える。
 *
 * **必ず元に戻す**のがこの機能の肝。「消灯しない」を掛けっぱなしにすると電池が静かに溶ける。
 * - 掛けるときに**元の値**を `filesDir/screen_timeout.json` へ保存する。
 * - 期限は [AlarmManager] で予約する ([ScreenTimeoutReceiver])。アプリが落ちても OS が起こす。
 * - 予約は端末再起動で消えるので [BootReceiver] から [restoreOrReschedule] を呼び直す。
 *   再起動中に期限を過ぎていたら**その場で書き戻す** (画面設定を巻き戻し忘れない)。
 *
 * 保存ファイルが残っているかどうかが「掛かっているか」の正本。SCREEN_OFF_TIMEOUT の現在値では
 * 判定しない (利用者が設定アプリ側で触っても矛盾しないように)。
 */
object ScreenTimeout {

    private const val TAG = "ScreenTimeout"
    private const val FILE_NAME = "screen_timeout.json"

    /** AlarmManager の requestCode。1 本しか使わないので固定でよい。 */
    private const val REQUEST_CODE = 0x2E5C

    /**
     * 「消えない」ときに書き込む値 (ミリ秒)。`Int.MAX_VALUE` は約 24.8 日で、OS 標準の設定アプリが
     * 「なし」に使うのと同じ考え方。無限を表す専用値は Android に無い。
     */
    private const val NEVER_MS = Int.MAX_VALUE

    /** 掛けられる時間の上限 (24 時間)。打ち間違いで何日も点きっぱなしになるのを防ぐ。 */
    private const val MAX_SECONDS = 24L * 60 * 60

    private val lock = Any()

    // --- 公開 API (Z2ApiBridge から呼ぶ) ---

    /** 「システム設定の変更」が許可されているか。 */
    fun canWrite(context: Context): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    /** 「システム設定の変更」の許可画面を開く Intent (設定画面とブリッジの両方から使う)。 */
    fun manageIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * [seconds] の間、自動消灯を止める。既に掛かっていれば**期限だけ延ばす**
     * (元の値は最初に掛けたときのものを保ち続ける — 上書きすると「なし」が元の値になってしまう)。
     * @return 状態 JSON ([statusJson] と同じ形)
     */
    fun keepOn(context: Context, seconds: Long): String {
        if (!canWrite(context)) throw IllegalStateException(NOT_ALLOWED)
        if (seconds <= 0) throw IllegalArgumentException("z2-screen: 時間は 1 秒以上で指定してください")
        if (seconds > MAX_SECONDS) throw IllegalArgumentException("z2-screen: 一度に掛けられるのは 24h までです")

        val until = System.currentTimeMillis() + seconds * 1000
        synchronized(lock) {
            val saved = load(context)
            // 元の値は「掛かっていないとき」だけ読む。掛かっている最中に読むと NEVER_MS を
            // 元の値として保存してしまい、期限が来ても消灯が戻らなくなる。
            val original = saved?.original ?: currentTimeoutMs(context)
            // 元の値が読めなかった (-1) なら**掛けない**。戻せない状態を作らないことを優先する。
            if (original <= 0) throw IllegalStateException("z2-screen: 元の消灯時間を読めませんでした")
            // ⚠ **記録が先、書き換えが後**。逆にすると、記録に失敗したときに「消えない」だけが
            // 残って誰も元へ戻せなくなる。この順なら、書き換えに失敗しても残るのは
            // 「元の値＝今の値」という無害な記録だけで、次の書き戻しが空振りするだけで済む。
            save(context, Saved(original = original, until = until))
            write(context, NEVER_MS)
        }
        schedule(context, until)
        return statusJson(context)
    }

    /**
     * 期限を待たずに元へ戻す (`z2-screen keepon off`)。掛かっていなければ何もしない。
     * @return 状態 JSON
     */
    fun cancel(context: Context): String {
        // 書き戻せたときだけ予約を外す。失敗したまま外すと、あとで戻す機会が無くなる。
        if (restoreNow(context)) unschedule(context)
        return statusJson(context)
    }

    /**
     * 期限が来たときに [ScreenTimeoutReceiver] から呼ぶ。書き戻して保存を消す。
     */
    fun onExpired(context: Context) {
        restoreNow(context)
    }

    /**
     * 端末起動後・アプリ起動時に呼ぶ。掛かっていなければ何もしない。
     * 期限を過ぎていればその場で書き戻し、まだなら予約を貼り直す。
     */
    fun restoreOrReschedule(context: Context) {
        val saved = synchronized(lock) { load(context) } ?: return
        if (saved.until <= System.currentTimeMillis()) restoreNow(context) else schedule(context, saved.until)
    }

    /**
     * 今の状態を**フラットな JSON** で返す (`z2-state` と同じ理由 — jq 無しでも拾えるように)。
     * `keepon` が掛かっているか / いつまで / 残り秒 / 現在の消灯時間 / 元の消灯時間 / 許可の有無。
     */
    fun statusJson(context: Context): String {
        val saved = synchronized(lock) { load(context) }
        val now = System.currentTimeMillis()
        return JSONObject().apply {
            put("allowed", canWrite(context))
            put("keepon", saved != null)
            put("timeout_ms", currentTimeoutMs(context))
            if (saved != null) {
                put("until", saved.until)
                put("remaining_sec", ((saved.until - now) / 1000).coerceAtLeast(0))
                put("original_ms", saved.original)
            }
        }.toString()
    }

    /** 許可が無いときに CLI へ返す文言。設定画面への行き方まで書く (端末側では開けないため)。 */
    const val NOT_ALLOWED: String =
        "z2-screen: システム設定の変更が許可されていません " +
            "(設定 › 画面の自動消灯 › 「システム設定の変更を許可」から許可してください)"

    // --- 内部 ---

    private data class Saved(val original: Int, val until: Long)

    /**
     * 書き戻して保存を消す。保存が無ければ何もしない (二重に呼ばれても安全)。
     *
     * ⚠ **消すのは書き戻せたときだけ**。失敗したまま消すと元の値が永久に失われ、画面が消えない
     * ままになる。残しておけば次のアプリ起動 ([restoreOrReschedule]) がもう一度試せる。
     *
     * @return 書き戻した (または元から掛かっていなかった) なら true
     */
    private fun restoreNow(context: Context): Boolean = synchronized(lock) {
        val saved = load(context) ?: return true
        val ok = runCatching { write(context, saved.original) }
            .onFailure { Log.w(TAG, "restore failed (${saved.original}ms) — keeping the record", it) }
            .isSuccess
        if (ok) clear(context)
        ok
    }

    private fun currentTimeoutMs(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
    }.getOrDefault(-1)

    private fun write(context: Context, ms: Int) {
        if (!Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms)) {
            throw IllegalStateException("z2-screen: 画面消灯の設定を書き換えられませんでした")
        }
    }

    private fun schedule(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // setExactAndAllowWhileIdle は API31+ で SCHEDULE_EXACT_ALARM が要る。数分のズレは
        // 「消灯しない時間が少し延びる」だけで実害が小さいので、権限の要らない不正確版を使う
        // (AlarmScheduler と同じ判断)。
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
        }.onFailure { Log.w(TAG, "schedule failed", it) }
    }

    private fun unschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScreenTimeoutReceiver::class.java)
            .setAction(ScreenTimeoutReceiver.ACTION_EXPIRE)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun load(context: Context): Saved? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            Saved(original = o.getInt("original"), until = o.getLong("until"))
        }.getOrElse {
            // 壊れていたら「掛かっていない」として扱う。ここで例外を投げると書き戻しの経路ごと
            // 止まってしまい、消灯しないまま復帰できなくなる。
            Log.w(TAG, "$FILE_NAME broken — treating as not held", it)
            runCatching { f.delete() }
            null
        }
    }

    /** ⚠ 失敗を握り潰さない。書けなかったのに掛けてしまうと、戻し方の無い状態が生まれる。 */
    private fun save(context: Context, s: Saved) {
        file(context).writeText(
            JSONObject().put("original", s.original).put("until", s.until).toString()
        )
    }

    private fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
