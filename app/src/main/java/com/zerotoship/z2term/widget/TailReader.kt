package com.zerotoship.z2term.widget

import java.io.File
import java.io.RandomAccessFile

/**
 * ファイルの末尾 N 行を読む (ウィジェット D2「ライブ tail」用)。
 *
 * ログは青天井に育つ ([com.zerotoship.z2term.core.SessionLogger] も
 * [com.zerotoship.z2term.service.LogWriter] もローテーションしない方針) ので、
 * **全部読んではいけない**。末尾から [MAX_TAIL_BYTES] だけ切り出して、その中で行に割る。
 *
 * 判断部分 ([lastLines]) は Android 非依存にして `TailReaderTest` で押さえる。
 */
object TailReader {

    /** 末尾から読むバイト数の上限。数十行ぶんあれば足りるので小さく抑える。 */
    const val MAX_TAIL_BYTES = 16L * 1024

    /**
     * [file] の末尾 [lines] 行。読めなければ空。
     *
     * 途中のバイトから読み始めるので**先頭行がマルチバイト文字の途中で切れる**ことがある。
     * その 1 行は捨てる ([lastLines] が担当) ので、化けた行が出ることはない。
     */
    fun read(file: File, lines: Int): List<String> {
        if (lines <= 0 || !file.isFile) return emptyList()
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len == 0L) return emptyList()
                val from = (len - MAX_TAIL_BYTES).coerceAtLeast(0L)
                raf.seek(from)
                val buf = ByteArray((len - from).toInt())
                raf.readFully(buf)
                lastLines(String(buf, Charsets.UTF_8), lines, truncatedHead = from > 0)
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
        if (n <= 0 || text.isEmpty()) return emptyList()
        // 末尾の改行は「最後の行が終わった」印であって空行ではない。
        val body = text.removeSuffix("\n").removeSuffix("\r")
        if (body.isEmpty()) return emptyList()
        val all = body.split("\n").map { it.removeSuffix("\r") }
        val usable = if (truncatedHead && all.size > 1) all.drop(1) else all
        return usable.takeLast(n)
    }
}
