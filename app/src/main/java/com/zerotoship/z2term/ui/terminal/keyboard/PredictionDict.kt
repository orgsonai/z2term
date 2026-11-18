package com.zerotoship.z2term.ui.terminal.keyboard

import java.io.BufferedReader

/**
 * 打ちかけの読みを補完するための「よく使われる語」の表 (`kkc_predict.tsv`・1.0.137)。
 *
 * 学習履歴 ([ImeHistoryStore]) の前方一致は**自分が確定したことのある語**しか出せないので、
 * 使い始めや初めて打つ語では補完が出ない。同梱辞書の前方一致 ([KanaKanjiConverter.predict]) は
 * 辞書の並び順で、よく使う語が前に来ない (しごと → 仕事相手 / 仕事唄 …)。ここは世の中でよく
 * 使われる語を回数つきで持ち、打った読みで始まる語を**多い順**に返す。
 *
 * 形式: `読み\t表層\t回数` を読み順に並べたもの。日本語版ウィキペディアの文章を単語に分けて
 * 数えた結果 (作り方は `scripts/build-kkc-freq.sh`)。⚠ 書き言葉の頻度なので、会話の決まり文句は
 * 弱い。そこは学習と定型文の側で補う。
 *
 * 読み順に並んだ配列を二分探索するだけなので、読み込みは速く、メモリも表の大きさぶんで済む。
 */
object PredictionDict {

    private class Table(val readings: Array<String>, val surfaces: Array<String>, val counts: IntArray)

    @Volatile private var table: Table = Table(emptyArray(), emptyArray(), IntArray(0))

    val loaded: Boolean get() = table.readings.isNotEmpty()

    /** [reader] = `kkc_predict.tsv` (UTF-8)。close は呼び出し側で行う。JVM テストからも同じ経路で読める。 */
    fun loadFrom(reader: BufferedReader) {
        val rows = ArrayList<Triple<String, String, Int>>(40_000)
        reader.forEachLine { line ->
            if (line.isEmpty() || line[0] == '#') return@forEachLine
            val p = line.split('\t')
            if (p.size < 3) return@forEachLine
            val n = p[2].toIntOrNull() ?: return@forEachLine
            rows.add(Triple(p[0], p[1], n))
        }
        // ファイルは読み順だが、二分探索の前提なのでここでも並べる (並びが崩れた資産でも壊れない)。
        rows.sortWith(compareBy({ it.first }, { -it.third }))
        table = Table(
            Array(rows.size) { rows[it].first },
            Array(rows.size) { rows[it].second },
            IntArray(rows.size) { rows[it].third },
        )
    }

    /** テスト用: 表を空に戻す。 */
    internal fun clear() {
        table = Table(emptyArray(), emptyArray(), IntArray(0))
    }

    /**
     * [prefix] で始まり、[prefix] より長い読みを持つ語を、回数の多い順に最大 [limit] 件返す。
     * 戻り値は `(読み, 表層)`。確定時に「打った接頭辞」でなく実際の読みで学習させるため、読みを伴う。
     *
     * 1 文字の接頭辞にも答える (1.0.166)。出す数は [KanaKanjiConverter.completionSlots] が絞る。
     */
    fun predictWithReading(prefix: String, limit: Int = 8): List<Pair<String, String>> {
        val t = table
        if (prefix.length < MIN_PREFIX || limit <= 0 || t.readings.isEmpty()) return emptyList()
        // 下限: prefix 以上の最初の位置。
        var lo = 0
        var hi = t.readings.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (t.readings[mid] < prefix) lo = mid + 1 else hi = mid
        }
        // 上位 limit 件を回数で選ぶ。範囲は読み順なので、いったん全部見て小さな配列に保つ。
        val best = ArrayList<Int>(limit + 1)
        var i = lo
        while (i < t.readings.size && t.readings[i].startsWith(prefix)) {
            if (t.readings[i].length > prefix.length) {
                var at = best.size
                while (at > 0 && t.counts[best[at - 1]] < t.counts[i]) at--
                if (at < limit) {
                    best.add(at, i)
                    if (best.size > limit) best.removeAt(best.size - 1)
                }
            }
            i++
        }
        val seen = HashSet<String>()
        val out = ArrayList<Pair<String, String>>(best.size)
        for (idx in best) if (seen.add(t.surfaces[idx])) out.add(t.readings[idx] to t.surfaces[idx])
        return out
    }

    fun predict(prefix: String, limit: Int = 8): List<String> = predictWithReading(prefix, limit).map { it.second }

    /** これ未満の長さの接頭辞には補完を返さない。 */
    const val MIN_PREFIX = 1
}
