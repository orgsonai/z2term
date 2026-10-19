package com.zerotoship.z2term.service

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * 通知・SMS の記録を「何日分だけ残す」ための控え (1.0.50)。
 *
 * **なぜ控えが要るか**: 記録の書式は利用者が自由に決める (複数行の読みやすい形など) ので、
 * 行を読んで日時を取り出すことはできない。そこで記録 1 件を書くたびに、書いた時刻とバイト数を
 * 隠しファイル `.<名前>.idx` へ 1 行足し、古い分は**その合計バイト数だけファイルから切り落とす**。
 * 末尾追記なら古い分は先頭に、先頭追記なら末尾にある。
 *
 * ⚠ **控えと中身が食い違ったら何も消さない**: 利用者が手で消した・書き換えた・途中で先頭追記を
 * 切り替えた、などで合計が合わなくなったら、いまの中身をまとめて「いま書いた 1 件」として
 * 数え直す (消すのは選んだ日数が過ぎてから)。消しすぎるより残しすぎる側へ倒す。
 * この版より前からある記録も同じ扱いで、控えを作った日から数える。
 *
 * ファイルは 1 本のまま (分けない・退避しない)。古い分を切り落とした新しい中身を一時ファイルに
 * 書いて置き換える。
 */
internal object LogRetention {
    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    /** 末尾追記 / 先頭追記 / 控えを付ける前からある分。 */
    internal const val APPEND = 'a'
    internal const val PREPEND = 'p'
    internal const val UNKNOWN = 'u'

    internal data class Entry(val ts: Long, val bytes: Long, val mode: Char)

    /** 最後に見た時刻と日数 (ファイルごと)。消す処理は 1 時間に 1 回、日数を変えたらすぐ。 */
    private val checked = HashMap<String, Pair<Long, Int>>()

    fun indexFile(log: File): File = File(log.parentFile, "." + log.name + ".idx")

    /**
     * [log] へ [bytes] バイトの記録 1 件を書いた直後に呼ぶ。控えに足し、必要なら古い分を消す。
     * [keepDays] が 0 なら消さない (控えだけ付ける。あとから日数を選んだときに効くように)。
     */
    fun recorded(log: File, bytes: Long, prepend: Boolean, keepDays: Int, now: Long) {
        val idx = indexFile(log)
        val sb = StringBuilder()
        if (!idx.exists()) {
            val before = log.length() - bytes
            if (before > 0) sb.append(format(Entry(now, before, UNKNOWN)))
        }
        sb.append(format(Entry(now, bytes, if (prepend) PREPEND else APPEND)))
        idx.appendText(sb.toString())
        val due = synchronized(checked) {
            val last = checked[log.path]
            val go = last == null || last.second != keepDays || now - last.first >= HOUR || now < last.first
            if (go) checked[log.path] = now to keepDays
            go
        }
        if (due) prune(log, keepDays, now)
    }

    /** [keepDays] 日より古い記録を消し、控えを 1 時間ごとにまとめ直す。 */
    fun prune(log: File, keepDays: Int, now: Long) {
        val idx = indexFile(log)
        val length = log.length()
        val entries = read(idx)?.takeIf { consistent(it, length) }
            ?: listOf(Entry(now, length, UNKNOWN)).filter { it.bytes > 0 }
        val cutoff = if (keepDays > 0) now - keepDays * DAY else Long.MIN_VALUE
        val expired = entries.takeWhile { it.ts < cutoff }
        val cut = expired.sumOf { it.bytes }
        if (cut > 0) trim(log, cut, fromEnd = entries.any { it.mode == PREPEND })
        write(idx, compact(entries.drop(expired.size)))
    }

    /** 合計がファイルの大きさと合い、末尾追記と先頭追記が混ざっていないか。前からある分は先頭だけ。 */
    internal fun consistent(entries: List<Entry>, length: Long): Boolean {
        if (entries.sumOf { it.bytes } != length) return false
        if (entries.drop(1).any { it.mode == UNKNOWN }) return false
        return !(entries.any { it.mode == APPEND } && entries.any { it.mode == PREPEND })
    }

    /** 同じ書き方で同じ 1 時間に入る記録を 1 行にまとめる。時刻は新しい方を残す (消すのが遅れる側)。 */
    internal fun compact(entries: List<Entry>): List<Entry> {
        val out = ArrayList<Entry>(entries.size)
        for (e in entries) {
            val last = out.lastOrNull()
            if (last != null && last.mode == e.mode && last.ts / HOUR == e.ts / HOUR)
                out[out.size - 1] = Entry(maxOf(last.ts, e.ts), last.bytes + e.bytes, e.mode)
            else out += e
        }
        return out
    }

    internal fun read(idx: File): List<Entry>? {
        if (!idx.exists()) return null
        return runCatching {
            idx.readLines().filter { it.isNotEmpty() }.map { line ->
                val f = line.split('\t')
                require(f.size == 3 && f[2].length == 1 && f[2][0] in setOf(APPEND, PREPEND, UNKNOWN))
                Entry(f[0].toLong(), f[1].toLong().also { require(it >= 0) }, f[2][0])
            }
        }.getOrNull()
    }

    private fun format(e: Entry) = "${e.ts}\t${e.bytes}\t${e.mode}\n"

    private fun write(idx: File, entries: List<Entry>) {
        val tmp = File(idx.parentFile, idx.name + ".tmp")
        tmp.writeText(entries.joinToString("") { format(it) })
        if (!tmp.renameTo(idx)) { tmp.delete(); error("index rename failed") }
    }

    /** [log] から [cut] バイトを切り落とす。[fromEnd] なら末尾 (先頭追記の古い分)、でなければ先頭。 */
    private fun trim(log: File, cut: Long, fromEnd: Boolean) {
        val tmp = File(log.parentFile, "." + log.name + ".tmp")
        RandomAccessFile(log, "r").channel.use { src ->
            FileOutputStream(tmp).channel.use { dst ->
                val count = (src.size() - cut).coerceAtLeast(0)
                val start = if (fromEnd) 0L else cut
                var done = 0L
                while (done < count) {
                    val n = src.transferTo(start + done, count - done, dst)
                    if (n <= 0) break
                    done += n
                }
                check(done == count) { "short copy" }
            }
        }
        if (!tmp.renameTo(log)) { tmp.delete(); error("log rename failed") }
    }
}
