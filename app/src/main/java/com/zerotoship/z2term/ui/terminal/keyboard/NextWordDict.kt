package com.zerotoship.z2term.ui.terminal.keyboard

import java.io.BufferedReader

/**
 * 確定した語の次に続きやすい語の表 (`kkc_next.tsv`・1.0.166)。
 *
 * 学習 ([ImeHistoryStore.nextWords]) は**自分が続けて打ったことのある語**しか出せないので、
 * 初めて打った語の後には何も出ない。ここは世の中の文章で続きやすい語を持ち、学習の後ろに足す。
 *
 * 形式: `語\t続き1\t続き2…`。続きは多い順。日本語版ウィキペディアの文章で語の並びを数えた結果
 * (作り方は `scripts/build-kkc-freq.sh`)。読み順の配列を二分探索するだけなので、読み込みは速い。
 */
object NextWordDict {

    private class Table(val keys: Array<String>, val values: Array<String>)

    @Volatile private var table: Table = Table(emptyArray(), emptyArray())

    /** [reader] = `kkc_next.tsv` (UTF-8)。close は呼び出し側で行う。JVM テストからも同じ経路で読める。 */
    fun loadFrom(reader: BufferedReader) {
        val rows = ArrayList<Pair<String, String>>(32_000)
        reader.forEachLine { line ->
            if (line.isEmpty() || line[0] == '#') return@forEachLine
            val tab = line.indexOf('\t')
            if (tab > 0 && tab < line.length - 1) rows.add(line.substring(0, tab) to line.substring(tab + 1))
        }
        // 二分探索は String の順序に合わせる (ファイルの並びとは絵文字などの扱いが違うことがある)。
        rows.sortBy { it.first }
        table = Table(Array(rows.size) { rows[it].first }, Array(rows.size) { rows[it].second })
    }

    /** テスト用: 表を空に戻す。 */
    internal fun clear() {
        table = Table(emptyArray(), emptyArray())
    }

    /**
     * [prev] に続きやすい語を、多い順に最大 [limit] 件返す。
     *
     * [prev] そのものが表に無いときは、後ろの部分で引く (今日はいい天気 → 天気)。確定は文節や文の
     * まとまりで来るため。⚠ 1 文字の部分では引かない (語の切れ目でない所に当たりやすい)。
     */
    fun next(prev: String, limit: Int = 8): List<String> {
        val t = table
        if (limit <= 0 || t.keys.isEmpty()) return emptyList()
        var start = 0
        while (start == 0 || prev.length - start >= MIN_SUFFIX) {
            if (start >= prev.length) break
            val at = t.keys.binarySearch(prev.substring(start))
            if (at >= 0) return t.values[at].split('\t').take(limit)
            start++
        }
        return emptyList()
    }

    private const val MIN_SUFFIX = 2
}
