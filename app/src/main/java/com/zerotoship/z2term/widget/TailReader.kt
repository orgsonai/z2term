package com.zerotoship.z2term.widget

import java.io.File
import java.io.RandomAccessFile

/**
 * ファイルの末尾 N 行 / 先頭 N 行を読む (ウィジェット D2「ライブ tail」用)。
 *
 * ログは青天井に育つ ([com.zerotoship.z2term.core.SessionLogger] も
 * [com.zerotoship.z2term.service.LogWriter] もローテーションしない方針) ので、
 * **全部読んではいけない**。片側から [MAX_TAIL_BYTES] だけ切り出して、その中で行に割る。
 *
 * 判断部分 ([lastLines] / [firstLines]) は Android 非依存にして `TailReaderTest` で押さえる。
 */
object TailReader {

    /** 片側から読むバイト数の上限。数十行ぶんあれば足りるので小さく抑える。 */
    const val MAX_TAIL_BYTES = 16L * 1024

    /**
     * [file] の [mode] 側 [lines] 行。読めなければ空。
     *
     * 切り出した窓の**外側に続きがある側の 1 行**はマルチバイト文字の途中で切れている
     * ことがあるので捨てる ([lastLines] / [firstLines] が担当)。化けた行は出ない。
     */
    fun read(file: File, lines: Int, mode: TailStore.Mode = TailStore.Mode.TAIL): List<String> {
        if (lines <= 0 || !file.isFile) return emptyList()
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len == 0L) return emptyList()
                val from = if (mode == TailStore.Mode.HEAD) 0L else (len - MAX_TAIL_BYTES).coerceAtLeast(0L)
                val to = if (mode == TailStore.Mode.HEAD) minOf(len, MAX_TAIL_BYTES) else len
                raf.seek(from)
                val buf = ByteArray((to - from).toInt())
                raf.readFully(buf)
                val text = String(buf, Charsets.UTF_8)
                if (mode == TailStore.Mode.HEAD) {
                    firstLines(text, lines, truncatedTail = to < len)
                } else {
                    lastLines(text, lines, truncatedHead = from > 0)
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * [text] の末尾 [n] 行 (空行は残す。末尾の改行だけは行として数えない)。
     *
     * [truncatedHead] が true なら**先頭行は途中から始まっている**可能性があるので捨てる
     * (文字化けや半端な行を出さないため)。
     */
    internal fun lastLines(text: String, n: Int, truncatedHead: Boolean = false): List<String> {
        val all = splitLines(text)
        if (n <= 0 || all.isEmpty()) return emptyList()
        val usable = if (truncatedHead && all.size > 1) all.drop(1) else all
        return usable.takeLast(n)
    }

    /**
     * [text] の先頭 [n] 行 (空行は残す。末尾の改行だけは行として数えない)。
     *
     * [truncatedTail] が true なら**最終行は途中で終わっている**可能性があるので捨てる
     * ([lastLines] と対称。切れている側が逆になるだけ)。
     */
    internal fun firstLines(text: String, n: Int, truncatedTail: Boolean = false): List<String> {
        val all = splitLines(text)
        if (n <= 0 || all.isEmpty()) return emptyList()
        val usable = if (truncatedTail && all.size > 1) all.dropLast(1) else all
        return usable.take(n)
    }

    private fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        // 末尾の改行は「最後の行が終わった」印であって空行ではない。
        val body = text.removeSuffix("\n").removeSuffix("\r")
        if (body.isEmpty()) return emptyList()
        return body.split("\n").map { it.removeSuffix("\r") }
    }
}
