package com.zerotoship.z2term.ui.snippets

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * シェルの履歴ファイルを読んで「履歴パレット」(B2) に出す形にする。
 *
 * **履歴ファイルは 2 本ある**（引き継ぎ書 §8 の調査結果）:
 *  - `~/.bash_history` … `PROMPT_COMMAND='history -a'` で**コマンド終了後**に 1 行 1 コマンドで載る。
 *  - `~/.zsh_history`  … `INC_APPEND_HISTORY` で**実行前**に載る。形式は
 *    `: <epoch>:<duration>;<cmd>` の拡張形式（複数行コマンドは行末 `\` で継続）。
 *
 * zsh は `SHARE_HISTORY` で全タブが 1 本を共有するので、**タブ別・ディストロ別の出し分けは
 * 実体を持たない**。フラットに 1 本へマージして新しい順に出すのが正しい。
 *
 * ファイルは青天井に育つので**末尾から [MAX_TAIL_BYTES] だけ**読む（[TailReader] と同じ考え方）。
 */
object ShellHistory {

    /** 履歴ファイルの末尾から読むバイト数。数百件ぶんあれば足りる。 */
    private const val MAX_TAIL_BYTES = 256L * 1024

    /** パレットに出す最大件数。これ以上は古い方から捨てる。 */
    const val MAX_ENTRIES = 300

    /** 履歴 1 件。[at] は epoch 秒（bash 側など時刻が無いものは 0）。 */
    data class Entry(val command: String, val at: Long)

    /** 端末から見た `~`（= `filesDir/shared_home`）。 */
    private fun home(context: Context): File =
        File(context.applicationContext.filesDir, "shared_home")

    /**
     * bash と zsh の履歴をマージして**新しい順**に返す。
     *
     * 同じコマンドが両方に載ることがある（シェルを行き来した場合）ので、[merge] で重複を畳む。
     */
    fun load(context: Context): List<Entry> {
        val h = home(context)
        val bash = parseBash(readTail(File(h, ".bash_history"), unmetafy = false))
        val zsh = parseZsh(readTail(File(h, ".zsh_history"), unmetafy = true))
        return merge(bash, zsh)
    }

    /**
     * ファイル末尾を読む（無ければ空文字）。
     *
     * [unmetafy] は zsh 用。**zsh は履歴ファイルで非 ASCII を "metafy" して書く**ので、
     * そのまま UTF-8 として読むと日本語が化ける ([unmetafy] 参照)。
     */
    private fun readTail(file: File, unmetafy: Boolean): String {
        if (!file.isFile) return ""
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len == 0L) return ""
                val from = (len - MAX_TAIL_BYTES).coerceAtLeast(0L)
                raf.seek(from)
                val buf = ByteArray((len - from).toInt())
                raf.readFully(buf)
                val bytes = if (unmetafy) unmetafy(buf) else buf
                val text = String(bytes, Charsets.UTF_8)
                // 途中から読んだときの半端な 1 行目は捨てる。
                if (from > 0) text.substringAfter('\n', "") else text
            }
        }.getOrDefault("")
    }

    /**
     * zsh の履歴ファイルの "metafy" を元に戻す。
     *
     * zsh は履歴に書くとき、**0x80 以上のバイトを `0x83` + `(元のバイト xor 0x20)` の 2 バイトに
     * 置き換える**（内部表現をそのままファイルへ出しているため）。だから UTF-8 の日本語は
     * `0x83` 混じりの並びになり、**そのまま読むと必ず化ける**（実機の `.zsh_history` で確認:
     * 生のままでは UTF-8 として不正、この変換を通すと全体が正しく UTF-8 になる。2026-07-25）。
     *
     * 末尾が `0x83` で切れている場合（末尾だけ読んでいるので起こり得る）はそのまま落とす。
     */
    internal fun unmetafy(src: ByteArray): ByteArray {
        // 目印が無いなら触らない（無駄なコピーを避ける）。
        if (src.none { it == META }) return src
        val out = ByteArray(src.size)
        var w = 0
        var i = 0
        while (i < src.size) {
            val b = src[i]
            if (b == META) {
                if (i + 1 >= src.size) break  // 末尾で切れている
                out[w++] = (src[i + 1].toInt() xor 0x20).toByte()
                i += 2
            } else {
                out[w++] = b
                i++
            }
        }
        return out.copyOf(w)
    }

    /** zsh が metafy に使う目印バイト (0x83)。 */
    private const val META: Byte = 0x83.toByte()

    /** `.bash_history`（1 行 1 コマンド）。時刻は持たないので 0。 */
    internal fun parseBash(text: String): List<Entry> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { Entry(it, 0L) }
            .toList()

    /**
     * `.zsh_history`。拡張形式 `: <epoch>:<duration>;<cmd>` と素の 1 行の両方を受ける。
     *
     * 行末が `\` の行は次の行へ続く（複数行コマンド）。壊れた行は捨てるのではなく
     * **そのまま 1 コマンドとして拾う**（履歴は見せるだけで実行はしないので、無理に落とさない）。
     */
    internal fun parseZsh(text: String): List<Entry> {
        val out = ArrayList<Entry>()
        var pendingCmd: StringBuilder? = null
        var pendingAt = 0L

        fun flush() {
            val sb = pendingCmd ?: return
            val cmd = sb.toString().trim()
            if (cmd.isNotEmpty()) out.add(Entry(cmd, pendingAt))
            pendingCmd = null
            pendingAt = 0L
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r')
            val continued = pendingCmd != null
            if (continued) {
                // 継続行はそのまま足す（改行は空白に畳む）。
                val sb = pendingCmd!!
                sb.append('\n').append(line.removeSuffix("\\"))
                if (!line.endsWith("\\")) flush()
                return@forEach
            }
            if (line.isBlank()) return@forEach
            var at = 0L
            var body = line
            if (line.startsWith(": ")) {
                val semi = line.indexOf(';')
                val colon = line.indexOf(':', startIndex = 2)
                if (semi > 0 && colon in 2 until semi) {
                    at = line.substring(2, colon).trim().toLongOrNull() ?: 0L
                    body = line.substring(semi + 1)
                }
            }
            pendingCmd = StringBuilder(body.removeSuffix("\\"))
            pendingAt = at
            if (!body.endsWith("\\")) flush()
        }
        flush()
        return out
    }

    /**
     * 2 本をマージして**新しい順**にする。
     *
     * bash 側は時刻を持たないので、**ファイル内の並び（古い→新しい）を保ったまま zsh の後ろに置く**
     * のではなく、両方を「新しいものが先」に直してから交互ではなく単純連結し、**同じコマンドは
     * 最初に出た方（＝新しい方）だけ残す**。時刻を持つ zsh を先に見るので、時刻付きが優先される。
     */
    internal fun merge(bash: List<Entry>, zsh: List<Entry>): List<Entry> {
        val ordered = zsh.asReversed() + bash.asReversed()
        val seen = HashSet<String>()
        val out = ArrayList<Entry>()
        for (e in ordered) {
            if (!seen.add(e.command)) continue
            out.add(e)
            if (out.size >= MAX_ENTRIES) break
        }
        return out
    }

    /**
     * [query] で絞り込む。空なら全部。
     *
     * 大小文字を無視し、**空白で区切った語をすべて含む**ものを残す（`git log` と打てば
     * `git --no-pager log` も引っかかる）。
     */
    fun filter(entries: List<Entry>, query: String): List<Entry> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return entries
        return entries.filter { e ->
            val c = e.command.lowercase()
            terms.all { c.contains(it) }
        }
    }
}
