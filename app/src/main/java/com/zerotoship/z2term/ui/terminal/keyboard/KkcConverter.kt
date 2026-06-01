package com.zerotoship.z2term.ui.terminal.keyboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

/**
 * かな漢字「文」変換エンジン (コスト最小経路 = Viterbi)。
 *
 * 旧来の「SKK 辞書を最長一致で食べる」方式は、辞書に稀語・姓 (きょうの→京野 等) が在ると
 * それを丸ごと拾ってゴミ変換になっていた。本エンジンは **IPADIC (MeCab) の単語コスト + 品詞
 * 接続コスト行列**で、読み全体を「最尤の単語列」に一発変換する (実 IME と同じ原理)。
 *
 * アセット:
 *  - `kkc_lex.tsv`    : `読み(ひらがな)\t表層\t左文脈ID\t右文脈ID\t単語コスト` を読み順ソート。
 *                       IPADIC 2.7.0 由来 (固有名詞/地名/組織は除いて軽量化)。
 *  - `kkc_matrix.bin` : 接続コスト行列。先頭に uint16 lsize, rsize (LE)、続けて int16 × lsize*rsize。
 *                       接続コスト(左ノードの右ID lr, 右ノードの左ID rl) = matrix[lr + rl*lsize]。
 *
 * 文節 ([bunsetsu]) は「内容語 + 後続のひらがな(助詞/助動詞)」でまとめ、ブロックごと変換 UI に使う。
 */
object KkcConverter {

    /** 1 語彙エントリ。surface=表層, lc/rc=左右文脈ID, cost=単語生起コスト。 */
    private class Entry(val surface: String, val lc: Int, val rc: Int, val cost: Int)

    @Volatile private var lex: Map<String, List<Entry>> = emptyMap()
    private var matrix: ShortArray = ShortArray(0)
    private var lsize: Int = 0

    @Volatile var loaded: Boolean = false
        private set

    /** 未知かな (辞書に無い読み) 1 文字を残すときのコスト。実語より十分高くしておく。 */
    private const val UNK_COST = 17000

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        withContext(Dispatchers.IO) {
            if (loaded) return@withContext
            // 接続行列
            context.assets.open("kkc_matrix.bin").use { ins ->
                val bytes = ins.readBytes()
                val bb = java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                lsize = bb.short.toInt() and 0xFFFF
                val rsize = bb.short.toInt() and 0xFFFF
                val arr = ShortArray(lsize * rsize)
                bb.asShortBuffer().get(arr)
                matrix = arr
            }
            // 語彙 (読み順ソート済み TSV)
            val map = HashMap<String, ArrayList<Entry>>(120_000)
            context.assets.open("kkc_lex.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val t1 = line.indexOf('\t'); if (t1 < 0) continue
                    val t2 = line.indexOf('\t', t1 + 1); if (t2 < 0) continue
                    val t3 = line.indexOf('\t', t2 + 1); if (t3 < 0) continue
                    val t4 = line.indexOf('\t', t3 + 1); if (t4 < 0) continue
                    val reading = line.substring(0, t1)
                    val surface = line.substring(t1 + 1, t2)
                    val lc = line.substring(t2 + 1, t3).toIntOrNull() ?: continue
                    val rc = line.substring(t3 + 1, t4).toIntOrNull() ?: continue
                    val cost = line.substring(t4 + 1).toIntOrNull() ?: continue
                    map.getOrPut(reading) { ArrayList(2) }.add(Entry(surface, lc, rc, cost))
                }
            }
            lex = map
            loaded = true
        }
    }

    /** 接続コスト。左ノードの右文脈 [leftRc] → 右ノードの左文脈 [rightLc]。 */
    private fun conn(leftRc: Int, rightLc: Int): Int {
        if (matrix.isEmpty()) return 0
        val idx = leftRc + rightLc * lsize
        return if (idx in matrix.indices) matrix[idx].toInt() else 0
    }

    private fun isHira(c: Char): Boolean = c in 'ぁ'..'ゖ' || c == 'ー' || c == 'ゔ'
    private fun isKanjiOrKata(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c in 'ァ'..'ヶ' || c == '々'

    /**
     * Viterbi で最尤の (読み, 表層) セグメント列を返す。未ロード/変換不能なら空。
     * セグメントは IPADIC の単語単位 (今日 / の / 天気 / は / …)。
     */
    fun segments(reading: String): List<Pair<String, String>> {
        if (reading.isEmpty() || !loaded) return emptyList()
        val n = reading.length
        val INF = Int.MAX_VALUE / 4
        val best = IntArray(n + 1) { INF }; best[0] = 0
        val bpPos = IntArray(n + 1) { -1 }
        val bpSurf = arrayOfNulls<String>(n + 1)
        val bpRead = arrayOfNulls<String>(n + 1)
        val rcat = IntArray(n + 1)   // ベスト経路でその位置に至るノードの右文脈 ID
        for (i in 0 until n) {
            if (best[i] == INF) continue
            for (j in i + 1..n) {
                val r = reading.substring(i, j)
                val entries = lex[r] ?: continue
                for (e in entries) {
                    val c = best[i] + conn(rcat[i], e.lc) + e.cost
                    if (c < best[j]) {
                        best[j] = c; bpPos[j] = i; bpSurf[j] = e.surface; bpRead[j] = r; rcat[j] = e.rc
                    }
                }
            }
            // 未知かな 1 文字フォールバック (接続コストは素通り、右文脈は前を引き継ぐ)。
            val j = i + 1
            val c = best[i] + UNK_COST
            if (c < best[j]) {
                best[j] = c; bpPos[j] = i; bpSurf[j] = reading.substring(i, j); bpRead[j] = reading.substring(i, j); rcat[j] = rcat[i]
            }
        }
        if (best[n] >= INF) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        var j = n
        while (j > 0) {
            val i = bpPos[j]
            out.add(bpRead[j]!! to bpSurf[j]!!)
            j = i
        }
        out.reverse()
        return out
    }

    /** 読み全体の最尤変換 (文まるごと)。変換不能なら null。 */
    fun convert(reading: String): String? {
        val segs = segments(reading)
        if (segs.isEmpty()) return null
        val res = segs.joinToString("") { it.second }
        return if (res.isNotEmpty()) res else null
    }

    /**
     * 文節 (内容語 + 後続のひらがな助詞/助動詞) 単位に区切った (読み, 表層) を返す。
     * 例: きょうのてんきは → [(きょうの,今日の),(てんきは,天気は)]。
     * Viterbi の単語セグメントを「漢字/カタカナで始まる語 → 続くひらがな語を吸収」でまとめる。
     */
    fun bunsetsu(reading: String): List<Pair<String, String>> {
        val segs = segments(reading)
        if (segs.isEmpty()) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        var curR = StringBuilder(); var curS = StringBuilder()
        var started = false
        for ((r, s) in segs) {
            val headKanji = s.isNotEmpty() && isKanjiOrKata(s[0])
            if (headKanji && started) {
                out.add(curR.toString() to curS.toString())
                curR = StringBuilder(); curS = StringBuilder()
            }
            curR.append(r); curS.append(s); started = true
        }
        if (curR.isNotEmpty()) out.add(curR.toString() to curS.toString())
        return out
    }

    /** 先頭文節の読みの長さ (自動ブロック分割の頭ブロック長)。文節が取れなければ 0。 */
    fun headBunsetsuLen(reading: String): Int {
        val b = bunsetsu(reading)
        if (b.isEmpty()) return 0
        return b[0].first.length
    }
}
