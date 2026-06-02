package com.zerotoship.z2term.ui.terminal.keyboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
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
            context.assets.open("kkc_matrix.bin").use { ms ->
                context.assets.open("kkc_lex.tsv").bufferedReader(Charsets.UTF_8).use { lr ->
                    loadFromStreams(ms, lr)
                }
            }
        }
    }

    /**
     * Android Context を介さずに直接ロードする (JVM ユニットテスト用)。
     * [matrixStream] = kkc_matrix.bin、[lexReader] = kkc_lex.tsv (UTF-8)。
     * 呼び出し側はストリームの close を行うこと。
     */
    fun loadFromStreams(matrixStream: InputStream, lexReader: BufferedReader) {
        val bytes = matrixStream.readBytes()
        val bb = java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        lsize = bb.short.toInt() and 0xFFFF
        val rsize = bb.short.toInt() and 0xFFFF
        val arr = ShortArray(lsize * rsize)
        bb.asShortBuffer().get(arr)
        matrix = arr

        val map = HashMap<String, ArrayList<Entry>>(120_000)
        lexReader.useLines { lines ->
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

    // ---------------------------------------------------------------------
    // Phase 1: N-best ラティス + リランカー土台
    //
    // segments()/convert() の 1-best Viterbi は据え置き (ベースライン保持)。
    // ここで「ラティスを陽に組んで後ろ向き A* で N-best を取り出す」段を足す。
    // mozc の NBestGenerator と同じ構造 (forward Viterbi → backward A*)。
    // Phase 2 以降の学習/共起 rerank は [reranker] 差し替えで載せる。
    // ---------------------------------------------------------------------

    /** N-best 変換候補。[segments]=(読み,表層) の列、[cost]=経路総コスト(小さいほど良い)。 */
    class Candidate(
        val surface: String,
        val segments: List<Pair<String, String>>,
        val cost: Int,
    )

    /**
     * 変換の文脈。Phase 2 以降のリランカーが参照する。
     * [prevSurface] = 直前に確定した語/文節の表層 (bigram の前語)。無ければ null。
     */
    class KkcContext(val prevSurface: String?) {
        companion object { val EMPTY = KkcContext(null) }
    }

    /** N-best 候補列の並べ替え器。Phase 2 以降 (履歴 bigram/共起) を差し込む拡張点。 */
    fun interface KkcReranker {
        fun rerank(reading: String, candidates: List<Candidate>, context: KkcContext): List<Candidate>
    }

    /** 既定: 何もしない (Viterbi コスト順のまま返す)。 */
    val IdentityReranker = KkcReranker { _, c, _ -> c }

    @Volatile var reranker: KkcReranker = IdentityReranker

    /** ラティスのノード。BOS/EOS は surface/reading 空・cost 0 で表す。 */
    private class Node(
        val begin: Int, val end: Int,
        val surface: String, val reading: String,
        val lc: Int, val rc: Int, val cost: Int,
    )

    /**
     * ラティス + 後ろ向き A* で N-best 候補を返す。表層ユニークで最大 [k] 件、コスト昇順。
     * 1 位は [segments] と同じコストモデル (BOS 接続あり / EOS 接続コスト 0) なので
     * 辞書語のみで構成されるケースでは convert() と一致する。
     * 未ロード/変換不能なら空。最後に [reranker] を通す。
     */
    fun nbest(reading: String, k: Int, context: KkcContext = KkcContext.EMPTY): List<Candidate> {
        if (reading.isEmpty() || !loaded || k <= 0) return emptyList()
        val n = reading.length
        val INF = Int.MAX_VALUE / 4

        // --- ラティス構築 ---
        val startsAt = Array(n) { ArrayList<Node>() }
        val endsAt = Array(n + 1) { ArrayList<Node>() }
        val bos = Node(0, 0, "", "", 0, 0, 0)
        val eos = Node(n, n, "", "", 0, 0, 0)
        endsAt[0].add(bos)
        for (i in 0 until n) {
            for (j in i + 1..n) {
                val r = reading.substring(i, j)
                val entries = lex[r] ?: continue
                for (e in entries) {
                    val nd = Node(i, j, e.surface, r, e.lc, e.rc, e.cost)
                    startsAt[i].add(nd); endsAt[j].add(nd)
                }
            }
            // 未知かな 1 文字 (lc=rc=0 = BOS/EOS 文脈で近似。本体は UNK_COST が支配的)。
            val r1 = reading.substring(i, i + 1)
            val unk = Node(i, i + 1, r1, r1, 0, 0, UNK_COST)
            startsAt[i].add(unk); endsAt[i + 1].add(unk)
        }

        // --- 前向き最小コスト forward[node] (BOS から node を含めた最小)。EOS 接続は 0。 ---
        val forward = HashMap<Node, Int>()
        forward[bos] = 0
        for (i in 0 until n) {
            for (nd in startsAt[i]) {
                var bestPrev = INF
                for (prev in endsAt[i]) {
                    val f = forward[prev] ?: continue
                    if (f >= INF) continue
                    val c = f + conn(prev.rc, nd.lc)
                    if (c < bestPrev) bestPrev = c
                }
                forward[nd] = if (bestPrev >= INF) INF else bestPrev + nd.cost
            }
        }
        var fEos = INF
        for (prev in endsAt[n]) {
            val f = forward[prev] ?: continue
            if (f < fEos) fEos = f   // EOS 接続コスト 0
        }
        if (fEos >= INF) return emptyList()
        forward[eos] = fEos

        // --- 後ろ向き A*。優先度 f = g(部分,最左=node) + forward[node] - node.cost (exact 下界)。 ---
        class PP(val node: Node, val g: Int, val tail: PP?)
        val pq = java.util.PriorityQueue<PP>(compareBy { it.g + (forward[it.node] ?: INF) - it.node.cost })
        pq.add(PP(eos, 0, null))

        val out = ArrayList<Candidate>()
        val seen = HashSet<String>()
        var pops = 0
        val popLimit = 50_000
        while (pq.isNotEmpty() && out.size < k && pops < popLimit) {
            val p = pq.poll() ?: break; pops++
            val cur = p.node
            if (cur === bos) {
                val segs = ArrayList<Pair<String, String>>()
                var t = p.tail
                while (t != null && t.node !== eos) {
                    segs.add(t.node.reading to t.node.surface)
                    t = t.tail
                }
                val surf = segs.joinToString("") { it.second }
                if (surf.isNotEmpty() && seen.add(surf)) {
                    out.add(Candidate(surf, segs, p.g))
                }
                continue
            }
            for (left in endsAt[cur.begin]) {
                val fLeft = forward[left] ?: continue
                if (fLeft >= INF) continue
                val connCost = if (cur === eos) 0 else conn(left.rc, cur.lc)
                pq.add(PP(left, p.g + connCost + left.cost, p))
            }
        }
        return reranker.rerank(reading, out, context)
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
