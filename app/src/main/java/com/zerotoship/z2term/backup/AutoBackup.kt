package com.zerotoship.z2term.backup

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zerotoship.z2term.R
import com.zerotoship.z2term.icon.setZ2SmallIcon
import com.zerotoship.z2term.service.ExactAlarm
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

/**
 * 定期バックアップ (0.8.386)。決まった日時に [BackupManager] を回して、選んだフォルダへ
 * 1 本ずつ積み、**世代数を超えたぶんを古いものから消す**。
 *
 * **なぜ要るか**: 持ち出しの仕組み ([BackupManager]) は 0.8.239 からあるが、**手で押した
 * ときにしか残らない**。機種変や初期化は押し忘れた側で起きるので、「作ってあるはず」が
 * 一番危ない。日時を決めて自動で積めば、最後に押した日ではなく**昨日の状態**が常にある。
 *
 * ## 決めたこと
 *
 * 1. ⚠ **秘密 (SSH のパスワード・秘密鍵) は含めない**。含めるには合言葉が要り、自動で
 *    書き出すには合言葉を端末へ置くことになる。「合言葉なしで秘密を出す経路は作らない」
 *    という手動書き出しの約束を、自動化のために崩さない。秘密ごと運ぶときは手で 1 本作る。
 * 2. **自動で作ったものだけを世代整理の対象にする**。ファイル名の頭を [AUTO_PREFIX]
 *    (`z2term-auto-`) にして、手で作った `z2term-backup-*.zip` とは名前で分ける。
 *    同じフォルダを選んでも**手で作った 1 本が世代整理で消えることはない**。
 * 3. **失敗したときだけ通知する**。毎日成功の通知が出ると読まなくなり、失敗したその日も
 *    読み飛ばす。うまくいった日は設定画面の「最後の書き出し」にだけ残す。
 *
 * ## 眠っている端末で動かす
 *
 * 予約は [ExactAlarm] (置けるなら正確・駄目なら Doze 貫通の不正確) に寄せる。再起動で
 * AlarmManager の予約は消えるので [com.zerotoship.z2term.service.BootReceiver] と
 * アプリ起動時に [schedule] を呼んで貼り直す。**予約は 1 本だけ**で、発火のたびに次を置く。
 */
object AutoBackup {

    private const val TAG = "AutoBackup"

    /** 自動で作ったバックアップの名前の頭。手で作ったものと分けるための唯一の目印。 */
    const val AUTO_PREFIX = "z2term-auto-"

    const val INTERVAL_DAILY = "daily"
    const val INTERVAL_WEEKLY = "weekly"
    const val INTERVAL_MONTHLY = "monthly"

    /** 残せる世代数の範囲。0 は「消し続ける」になってしまうので下限は 1。 */
    const val KEEP_MIN = 1
    const val KEEP_MAX = 30

    private const val CHANNEL_ID = "z2term_auto_backup"
    private const val NOTIFY_ID = 0x2BAC

    const val ACTION_FIRE = "com.zerotoship.z2term.AUTO_BACKUP_FIRE"
    private const val REQUEST_CODE = 0x2BAC

    /** 走った結果。[detail] は成功ならファイル名、失敗なら理由の符丁 ([Result.text] が文言にする)。 */
    data class Result(val ok: Boolean, val detail: String) {
        /** 設定へ残す形 (`ok:<名前>` / `err:<符丁>`)。 */
        fun encode(): String = (if (ok) "ok:" else "err:") + detail
    }

    /** 失敗の符丁。**文言は画面側** ([R.string] を引く) で出す。 */
    const val ERR_NO_FOLDER = "nofolder"
    const val ERR_NO_ACCESS = "noaccess"
    const val ERR_WRITE = "write"

    // --- 予約 ---

    /**
     * 設定を読んで予約を貼り直す。OFF / フォルダ未選択なら**予約を消すだけ**。
     *
     * アプリ起動時・設定を変えた直後・端末起動後・発火直後に呼ぶ (何度呼んでも同じ状態になる)。
     */
    suspend fun schedule(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val s = runCatching { AppSettings(app).flow.first() }.getOrNull() ?: return@withContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return@withContext
        if (!s.autoBackupEnabled || s.autoBackupFolder.isEmpty()) {
            runCatching { am.cancel(pending(app)) }
            return@withContext
        }
        val at = nextAt(
            interval = s.autoBackupInterval,
            dayOfWeek = s.autoBackupDayOfWeek,
            dayOfMonth = s.autoBackupDayOfMonth,
            hour = s.autoBackupHour,
            minute = s.autoBackupMinute,
            from = System.currentTimeMillis()
        )
        ExactAlarm.setWakeup(am, at, pending(app), TAG)
        Log.i(TAG, "next auto backup at $at")
    }

    /** 発火。書き出して結果を残し、**次を置き直す**。 */
    fun onFired(context: Context) {
        val app = context.applicationContext
        runBlocking {
            val result = runAndRecord(app)
            if (!result.ok) notifyFailure(app, result.detail)
            // ⚠ 失敗しても**次は置く**。1 回書けなかったことと、以後ずっと回らないことは別。
            schedule(app)
        }
    }

    /**
     * 1 本書き出して**結果を設定へ残す**。画面の「今すぐ書き出す」もここを通る —
     * 手で押したぶんが「最後の書き出し」に出ないと、動いているのかどうかが分からない。
     */
    suspend fun runAndRecord(context: Context): Result {
        val app = context.applicationContext
        val result = runOnce(app)
        runCatching {
            AppSettings(app).setAutoBackupResult(System.currentTimeMillis(), result.encode())
        }
        return result
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, AutoBackupReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * [from] より後の、最初の書き出し時刻 (epoch ミリ秒)。
     *
     * **純関数**にしてある — 「毎週日曜の 3:00」が本当に次の日曜になるかは、端末を待たずに
     * 確かめられないと直せない ([AutoBackupScheduleTest])。
     */
    fun nextAt(
        interval: String,
        dayOfWeek: Int,
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
        from: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (interval) {
            INTERVAL_WEEKLY -> {
                val want = dayOfWeek.coerceIn(Calendar.SUNDAY, Calendar.SATURDAY)
                // 今日がその曜日で、時刻がまだ来ていないならそれが答え。でなければ日を進める。
                var guard = 0
                while ((cal.timeInMillis <= from || cal.get(Calendar.DAY_OF_WEEK) != want) && guard < 8) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    guard++
                }
            }
            INTERVAL_MONTHLY -> {
                // ⚠ 日は 1-28 に丸める。29-31 を許すと**その日が無い月だけ飛ぶ**。
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, 28))
                if (cal.timeInMillis <= from) cal.add(Calendar.MONTH, 1)
            }
            else -> if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    // --- 書き出し ---

    /**
     * いま 1 本書き出す (画面の「今すぐ書き出す」からも呼ぶ)。
     *
     * 秘密は含めない ([BackupManager.Options] の既定のまま)。書き終えてから世代整理をする —
     * 逆にすると、書き出しに失敗した日に古いものだけ消えて**手元が減る**。
     */
    suspend fun runOnce(context: Context): Result {
        val app = context.applicationContext
        val s = AppSettings(app).flow.first()
        if (s.autoBackupFolder.isEmpty()) return Result(false, ERR_NO_FOLDER)
        val tree = runCatching { Uri.parse(s.autoBackupFolder) }.getOrNull()
            ?: return Result(false, ERR_NO_FOLDER)
        if (!hasWriteAccess(app, tree)) return Result(false, ERR_NO_ACCESS)

        val name = AUTO_PREFIX + BackupManager.stamp() + ".zip"
        val cr = app.contentResolver
        val written = runCatching {
            val dir = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val target = DocumentsContract.createDocument(cr, dir, "application/zip", name)
                ?: error("createDocument returned null")
            cr.openOutputStream(target)?.use { out ->
                BackupManager.export(app, out, BackupManager.Options())
            } ?: error("cannot open output")
            true
        }.onFailure { Log.w(TAG, "auto backup failed", it) }.getOrDefault(false)
        if (!written) return Result(false, ERR_WRITE)

        runCatching { prune(app, tree, s.autoBackupKeep) }
            .onFailure { Log.w(TAG, "prune failed", it) }
        return Result(true, name)
    }

    /** 選んだフォルダへ**いまも書けるか**。ユーザーが権限を外した / フォルダを消したときに false。 */
    fun hasWriteAccess(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.isWritePermission && it.uri == tree
        }

    /**
     * 世代整理。**自動で作ったものだけ**を新しい順に [keep] 本残し、残りを消す。
     *
     * @return 消したファイル名。
     */
    private fun prune(context: Context, tree: Uri, keep: Int): List<String> {
        val cr = context.contentResolver
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val found = LinkedHashMap<String, String>()   // 表示名 → ドキュメント ID
        cr.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) found[c.getString(1)] = c.getString(0)
        }
        val doomed = stale(found.keys.toList(), keep)
        doomed.forEach { name ->
            val id = found[name] ?: return@forEach
            runCatching {
                DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }.onFailure { Log.w(TAG, "delete failed: $name", it) }
        }
        return doomed
    }

    /**
     * [names] のうち消すべきもの。**純関数** ([AutoBackupScheduleTest] で守る)。
     *
     * 名前に `YYYYMMDD-HHMM` が入っているので**辞書順 = 時系列順**。並べ替えに更新日時を
     * 使わないのは、保存先によっては日時が取れない / 揃わないことがあるため。
     */
    fun stale(names: List<String>, keep: Int): List<String> =
        names.filter { it.startsWith(AUTO_PREFIX) && it.endsWith(".zip") }
            .sortedDescending()
            .drop(keep.coerceIn(KEEP_MIN, KEEP_MAX))

    // --- 失敗の知らせ ---

    // POST_NOTIFICATIONS 未許可は下の runCatching で握って Log に流すので、lint の権限チェックは
    // 抑止する (Z2ApiBridge の通知と同じ扱い)。⚠ 通知が出せなくても書き出しの成否は
    // 設定画面の「最後の書き出し」に残るので、知る手立てが消えるわけではない。
    @SuppressLint("MissingPermission")
    private fun notifyFailure(context: Context, detail: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.auto_backup_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.auto_backup_channel_desc) }
            )
        }
        val reason = context.getString(reasonRes(detail))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setZ2SmallIcon(context)
            .setContentTitle(context.getString(R.string.auto_backup_failed_title))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setAutoCancel(true)
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_ID, builder.build()) }
            .onFailure { Log.w(TAG, "notify failed", it) }
    }

    /** 失敗の符丁 → 文言。`err:` が付いたままでも外れても引けるようにしておく。 */
    fun reasonRes(detail: String): Int = when (detail.removePrefix("err:")) {
        ERR_NO_FOLDER -> R.string.auto_backup_err_nofolder
        ERR_NO_ACCESS -> R.string.auto_backup_err_noaccess
        else -> R.string.auto_backup_err_write
    }
}
