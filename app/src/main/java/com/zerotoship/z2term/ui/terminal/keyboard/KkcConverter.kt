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

    /**
     * 「読みを単純にカタカナへ写しただけの表層」(surface == カタカナ(reading)) に課す生起コスト
     * ペナルティ (Phase 3, G4 カタカナ化抑止)。
     *
     * IPADIC は擬音語/記号的名詞にカタカナ表層を低コストで持つため (例 ほん→ホン 3692 < 本 5947)、
     * そのままだと一般のかな漢字変換で過剰にカタカナ化する。対応する漢字/かな語があればそちらに
     * 負けるだけのペナルティを足す。
     *
     * ただし読みに長音符「ー」を含むものは正当な外来語 (コーヒー/データ/メール) なので除外する。
     * ネイティブ語の読みに「ー」は出ないため、これで外来語のカタカナ表記を壊さずに済む。
     */
    private const val KATAKANA_DUP_PENALTY = 4000

    /** Phase 4 共起 (コロケーション) 集合。`kkc_colloc.bloom` から読込。未配置なら null。 */
    @Volatile var collocationFilter: ExistenceFilter? = null
        private set

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        withContext(Dispatchers.IO) {
            if (loaded) return@withContext
            context.assets.open("kkc_matrix.bin").use { ms ->
                context.assets.open("kkc_lex.tsv").bufferedReader(Charsets.UTF_8).use { lr ->
                    loadFromStreams(ms, lr)
                }
            }
            runCatching {
                context.assets.open("kkc_colloc.bloom").use { collocationFilter = ExistenceFilter.load(it) }
            }
            // 学習リランカー (HistoryReranker) がまだ載っていなければ共起リランカーを既定で入れる。
            // ImeHistoryStore.ensureLoaded は CompositeReranker で上書きするので順序非依存。
            if (reranker === IdentityReranker && collocationFilter != null) {
                reranker = CollocationReranker({ collocationFilter })
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
                // 読みの単純カタカナ写し表層はペナルティを足して過剰なカタカナ化を抑える。
                // 長音符を含む読み (=外来語) は除外し、正当なカタカナ表記を守る。
                val adjCost = if ('ー' !in reading && surface == hiraganaToKatakana(reading)) {
                    cost + KATAKANA_DUP_PENALTY
                } else {
                    cost
                }
                map.getOrPut(reading) { ArrayList(2) }.add(Entry(surface, lc, rc, adjCost))
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

    /**
     * 学習ブロック参照 (動的ブロック分割)。完全一致 reading をユーザーが確定したことがあれば
     * `(優先表層, コスト下げ幅)` を返し、未学習なら null。[ImeHistoryStore] が配線する。
     *
     * これにより「こまんど → コマンド」のように一度まとめて確定した読みは、次回以降ラティスで
     * その読み全体のノードコストが下がり、自動分割で 1 ブロックに繋ぎ止められる (頻度で強化)。
     * 辞書に無い読みでも学習表層で合成ノードを足すので、任意の「良く使う読みの塊」を覚えられる。
     */
    @Volatile var learnedBlock: ((String) -> Pair<String, Int>?)? = null

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
        val blockFn = learnedBlock
        for (i in 0 until n) {
            for (j in i + 1..n) {
                val r = reading.substring(i, j)
                // 学習ブロック (2 文字以上): 該当読みのノードコストを下げて 1 ブロックに繋ぎ止める。
                val learned = if (j - i >= 2) blockFn?.invoke(r) else null
                val bonus = learned?.second ?: 0
                val entries = lex[r]
                if (entries != null) {
                    for (e in entries) {
                        // 学習ボーナスは「ユーザーが実際に確定した表層」だけに効かせる。
                        // 同じ読みの全表層へ一律に掛けると、塊 (ブロック) は維持できても辞書最小
                        // コストの別表層が勝ち、ユーザーが選んだ漢字が反映されない (例: きく→聴く を
                        // 学習しても 聞く が出続ける)。学習表層へ集中させることで「打ち慣れた変換」が
                        // 文中でも勝つようにする。
                        val applies = bonus > 0 && e.surface == learned?.first
                        val c = if (applies) (e.cost - bonus).coerceAtLeast(1) else e.cost
                        val nd = Node(i, j, e.surface, r, e.lc, e.rc, c)
                        startsAt[i].add(nd); endsAt[j].add(nd)
                    }
                }
                // 辞書に無い学習ブロックは学習表層で合成ノードを足す (lc=rc=0 で BOS/EOS 文脈近似)。
                if (learned != null && (entries == null || entries.none { it.surface == learned.first })) {
                    val c = (UNK_COST * (j - i) - bonus).coerceAtLeast(1)
                    val nd = Node(i, j, learned.first, r, 0, 0, c)
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
        // 自動ブロック分割は **正確なラティス最短経路** ([nbest] の 1 位) の分割を使う。
        // 位置 DP の [segments] は単一右文脈しか持たない近似で、接続コスト次第で経路が
        // ずれる (例: おねがいします → 尾根が/医師ます と誤分割し「おねが」でブロック固定、
        // 正解の「お願いします」が候補に出ない)。k=1 の nbest は reranker を通しても
        // 候補 1 件なので順不同、純粋なコスト最小経路 = 自動分割の基準として正しい。
        val segs = nbest(reading, 1, KkcContext.EMPTY).firstOrNull()?.segments
            ?: segments(reading)   // nbest が空 (未ロード等) のときだけ近似へフォールバック
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
