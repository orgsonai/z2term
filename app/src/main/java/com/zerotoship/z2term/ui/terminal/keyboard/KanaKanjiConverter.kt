package com.zerotoship.z2term.ui.terminal.keyboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SKK 辞書ベースの簡易かな漢字変換エンジン。
 *
 * 辞書はアセット `z2dict.txt` (UTF-8)。1 行 = "よみ /候補1/候補2/.../"。
 * 行は見出し(よみ)の Unicode 順にソート済みなので二分探索で引ける。
 *
 * - [convert]         : よみに完全一致する候補 (= 変換)
 * - [predict]         : よみで前方一致する見出しの候補を集める (= 予測変換)
 * - [okuriForms]      : 送り仮名活用 (つくって→作って など。連用形見出しから語幹を流用)
 * - [convertFlexible] : 上記 + [KkcConverter] の文まるごと最尤変換をまとめた候補 (キーボードはこれを使う)
 *
 * 厳密な形態素解析ではなく辞書ベースの best-effort。単語の別表記候補や前方一致予測を担い、
 * 「文まるごと」の最尤変換 (文節分割) は [KkcConverter] (Viterbi/IPADIC) に委ねる。
 * 生のかな・カタカナは常に確定候補として残す。
 */
object KanaKanjiConverter {
    @Volatile private var lines: List<String> = emptyList()
    @Volatile var loaded: Boolean = false
        private set

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        withContext(Dispatchers.IO) {
            if (loaded) return@withContext
            val result = ArrayList<String>(160_000)
            context.assets.open("z2dict.txt").bufferedReader(Charsets.UTF_8).use { r ->
                r.forEachLine { if (it.isNotEmpty() && it[0] != ';') result.add(it) }
            }
            // 元辞書 (SKK 送り仮名なし) は常用動詞・形容詞の終止形/活用をほぼ持たず、押す/入る/
            // 出る/食べる… が変換できない。内蔵の常用語テーブルから活用形を生成し、見出し順に
            // マージして「直接 convert で引ける」状態にする (okuri 推測のノイズに頼らない)。
            lines = mergeDict(result, buildSupplement())
            loaded = true
        }
    }

    /** 見出しの行 index を二分探索。完全一致なら index、無ければ -(挿入位置)-1。 */
    private fun searchIndex(reading: String): Int {
        val src = lines
        var lo = 0
        var hi = src.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = headOf(src[mid]).compareTo(reading)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -lo - 1
    }

    private fun headOf(line: String): String {
        val sp = line.indexOf(' ')
        return if (sp >= 0) line.substring(0, sp) else line
    }

    private fun candidatesOf(line: String): List<String> {
        val sp = line.indexOf(' ')
        if (sp < 0) return emptyList()
        return line.substring(sp + 1).split('/').filter { it.isNotEmpty() }
    }

    /** よみに完全一致する候補。 */
    fun convert(reading: String): List<String> {
        if (reading.isEmpty() || lines.isEmpty()) return emptyList()
        val idx = searchIndex(reading)
        if (idx < 0) return emptyList()
        return candidatesOf(lines[idx])
    }

    /** よみで前方一致する見出しの候補を最大 [limit] 件集める (予測変換)。 */
    fun predict(reading: String, limit: Int = 16): List<String> {
        if (reading.isEmpty() || lines.isEmpty()) return emptyList()
        val src = lines
        var idx = searchIndex(reading)
        if (idx < 0) idx = -idx - 1
        val out = LinkedHashSet<String>()
        var i = idx
        while (i < src.size && out.size < limit) {
            val line = src[i]
            if (!headOf(line).startsWith(reading)) break
            for (c in candidatesOf(line)) {
                out.add(c)
                if (out.size >= limit) break
            }
            i++
        }
        return out.toList()
    }

    // ---- ここから「柔軟な変換」(送り仮名活用 + 文節分割) ----------------------------
    // 元の辞書は SKK の送り仮名なし形式 (読み→候補) で、`つくる /作る/` のような動詞終止形や
    // 活用エントリを持たない。そこで「連用形の見出し (例 つくり /作り/) から漢字語幹を取り出し、
    // ユーザーが打った送り仮名を付け直す」ことで、つくって→作って のような活用変換を補う。
    // 厳密な形態素解析ではないため候補は補助的に追加するだけで、生のかな/カタカナは常に残す。

    private fun isHira(c: Char): Boolean = c in 'ぁ'..'ゖ' || c == 'ー' || c == 'ゔ'
    private fun isKanjiChar(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c == '々' || c == '〆'

    /**
     * 送り仮名活用による候補。読みを「語幹 + 送り仮名」に分け、辞書の連用形見出し
     * (語幹 + ひらがな1文字 → 漢字 + 同じひらがな) から漢字語幹を得て、打った送り仮名を付け直す。
     * 例: 読み「つくって」→ 語幹「つく」+送り「って」、辞書「つくり /作り/造り/」→ 作って/造って。
     * 長い語幹を優先し、最初にヒットした語幹長で確定する (短い語幹由来のノイズを抑える)。
     */
    fun okuriForms(reading: String, limit: Int = 6): List<String> {
        if (reading.length < 2 || lines.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        for (stemLen in reading.length - 1 downTo 1) {
            val stem = reading.substring(0, stemLen)
            val okuri = reading.substring(stemLen)
            var idx = searchIndex(stem); if (idx < 0) idx = -idx - 1
            var i = idx
            var foundAtThisLen = false
            while (i < lines.size) {
                val head = headOf(lines[i])
                if (!head.startsWith(stem)) break
                // 見出し = 語幹 + ひらがな1文字 (= 辞書の送り仮名) のみ対象。
                if (head.length == stemLen + 1 && isHira(head[stemLen])) {
                    val v = head[stemLen]
                    for (c in candidatesOf(lines[i])) {
                        // 候補が「漢字語幹 + 同じ送り仮名 v」で終わるものだけ採用。
                        if (c.length >= 2 && c.last() == v) {
                            val kStem = c.dropLast(1)
                            if (kStem.isNotEmpty() && kStem.any { isKanjiChar(it) }) {
                                out.add(kStem + okuri)
                                foundAtThisLen = true
                                if (out.size >= limit) return out.toList()
                            }
                        }
                    }
                }
                i++
            }
            if (foundAtThisLen) break
        }
        return out.toList()
    }

    /**
     * 柔軟な変換候補。優先順:
     *  1. 学習履歴: 完全一致 reading の確定済み単語 ([ImeHistoryStore.historyFor]) ← 最上位
     *  2. 文まるごと最尤変換 ([KkcConverter.convert]) — 読み全体を Viterbi で一発変換
     *  3. 完全一致 ([convert]) / 送り仮名活用 ([okuriForms]) — 単語の別表記候補
     *  4. 学習履歴: 前方一致 ([ImeHistoryStore.predictHistory]) — 「打ち慣れた語」予測
     *  5. 前方一致の予測 ([predict]) で補完
     */
    fun convertFlexible(reading: String, limit: Int = 16, prevSurface: String? = null): List<String> {
        if (reading.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        // 1. 学習履歴 (完全一致) は最優先で上位表示。loaded 前は空。
        for (h in ImeHistoryStore.historyFor(reading, limit = 4)) {
            out.add(h)
            if (out.size >= limit) return out.toList()
        }
        // 2. 文まるごとの最尤変換 (Viterbi/IPADIC)。読み全体を最尤の単語列へ一発変換する。
        //    N-best を取り、直前確定語 [prevSurface] による履歴 bigram リランク (Phase 2) を
        //    通した 1 位を使う (学習が無ければ Viterbi 1-best と一致)。
        val ctx = KkcConverter.KkcContext(prevSurface)
        // Viterbi/N-best の上位を複数候補に出す。1 位が稀語 (例 うって→討手) でも、正解
        // (打って 等) が 2 位以降に必ず候補へ並ぶようにする。一度選べば学習で次回 1 位に上がる。
        val nb = KkcConverter.nbest(reading, 8, ctx)
        if (nb.isNotEmpty()) {
            for (cand in nb.take(6)) {
                out.add(cand.surface)
                if (out.size >= limit) return out.toList()
            }
        } else {
            KkcConverter.convert(reading)?.let {
                out.add(it); if (out.size >= limit) return out.toList()
            }
        }
        if (lines.isEmpty()) {
            // z2dict 未ロードでも履歴と前方一致履歴は出す。
            for (h in ImeHistoryStore.predictHistory(reading, limit = limit)) {
                out.add(h); if (out.size >= limit) break
            }
            return out.toList()
        }
        out.addAll(convert(reading))
        out.addAll(okuriForms(reading))
        // 5. 学習履歴 (前方一致) を辞書の前方一致予測より先に。
        for (h in ImeHistoryStore.predictHistory(reading, limit = 6)) {
            out.add(h); if (out.size >= limit) break
        }
        for (c in predict(reading, limit)) {
            out.add(c)
            if (out.size >= limit) break
        }
        return out.toList().take(limit)
    }

    /**
     * スプリットモードで「最初の文節」の自動分割長を返す。
     *
     * 文節境界は [KkcConverter] (Viterbi) の文節分割に委ねる (例: きょうのてんき → 先頭 きょうの)。
     * Kkc 未ロード時のみ、最長辞書一致 + 後続ひらがな取り込みのフォールバックを使う。
     */
    fun autoSplitHeadLen(reading: String): Int {
        if (reading.isEmpty()) return 0
        val h = KkcConverter.headBunsetsuLen(reading)
        if (h in 1..reading.length) return h
        // フォールバック (Kkc 未ロード): 先頭の最長辞書一致 + 後続ひらがな。
        var headLen = -1
        for (end in reading.length downTo 2) {
            if (convert(reading.substring(0, end)).isNotEmpty()) { headLen = end; break }
        }
        if (headLen < 0) return reading.length
        var end = headLen
        while (end < reading.length && isHira(reading[end])) {
            var nextContent = false
            for (e in reading.length downTo end + 2) {
                if (convert(reading.substring(end, e)).isNotEmpty()) { nextContent = true; break }
            }
            if (nextContent) break
            end++
        }
        return end
    }

    // ========================================================================
    // 常用語の活用補完 (動詞 / 形容詞 / サ変 / カ変)
    //
    // 元の z2dict (SKK の送り仮名なし形式) は動詞・形容詞の終止形や活用形をほとんど持たない
    // (例: たべる/はしる/でる/いれる が無い、はいる は「配流」のみで「入る」が無い)。そのため
    // 「押して」「入る」「出ません」のような日常語が変換候補に出ず実用にならなかった。
    //
    // ここでは主要な常用語を「終止形 + 主要活用形 (て/た/ます/ません/ない/ば/意志/連用…)」へ
    // 展開して見出し行を作り、起動時に元辞書へマージする ([mergeDict])。これにより
    // convert("おして") が直接「押して」を返せる (okuriForms の推測に頼らず確実)。
    // ========================================================================

    /** 活用種別。GODAN_IKU は「行く」型 (て/た形が促音便: 行って/行った の例外)。 */
    private enum class Vk { ICHIDAN, GODAN, GODAN_IKU, SURU, KURU, ADJ }

    // 五段活用の語尾母音変換 (い段 / あ段 / え段 / お段)。未知の文字はそのまま返す。
    private fun iRow(c: Char): Char = when (c) {
        'う' -> 'い'; 'く' -> 'き'; 'ぐ' -> 'ぎ'; 'す' -> 'し'; 'つ' -> 'ち'
        'ぬ' -> 'に'; 'ぶ' -> 'び'; 'む' -> 'み'; 'る' -> 'り'; else -> c
    }
    private fun aRow(c: Char): Char = when (c) {
        'う' -> 'わ'; 'く' -> 'か'; 'ぐ' -> 'が'; 'す' -> 'さ'; 'つ' -> 'た'
        'ぬ' -> 'な'; 'ぶ' -> 'ば'; 'む' -> 'ま'; 'る' -> 'ら'; else -> c
    }
    private fun eRow(c: Char): Char = when (c) {
        'う' -> 'え'; 'く' -> 'け'; 'ぐ' -> 'げ'; 'す' -> 'せ'; 'つ' -> 'て'
        'ぬ' -> 'ね'; 'ぶ' -> 'べ'; 'む' -> 'め'; 'る' -> 'れ'; else -> c
    }
    private fun oRow(c: Char): Char = when (c) {
        'う' -> 'お'; 'く' -> 'こ'; 'ぐ' -> 'ご'; 'す' -> 'そ'; 'つ' -> 'と'
        'ぬ' -> 'の'; 'ぶ' -> 'ぼ'; 'む' -> 'も'; 'る' -> 'ろ'; else -> c
    }
    /** 五段の て形語尾 (音便)。う/つ/る→って、ぬ/ぶ/む→んで、く→いて、ぐ→いで、す→して。 */
    private fun godanTe(c: Char): String = when (c) {
        'う', 'つ', 'る' -> "って"; 'ぬ', 'ぶ', 'む' -> "んで"
        'く' -> "いて"; 'ぐ' -> "いで"; 'す' -> "して"; else -> "て"
    }
    /** て形 → た形 (て→た / で→だ)。 */
    private fun teToTa(te: String): String = when {
        te.endsWith("で") -> te.dropLast(1) + "だ"
        te.endsWith("て") -> te.dropLast(1) + "た"
        else -> te
    }

    /** 1 語の主要活用形を [add](よみ, 漢字) で吐き出す。 */
    private fun generateForms(r: String, k: String, vk: Vk, add: (String, String) -> Unit) {
        when (vk) {
            Vk.GODAN -> {
                if (r.length < 2 || k.isEmpty()) { add(r, k); return }
                val rc = r.last()
                val rs = r.dropLast(1)              // よみ語幹
                val ks = k.dropLast(1)              // 漢字語幹 (送り仮名を落とす)
                val i = iRow(rc); val a = aRow(rc); val e = eRow(rc); val o = oRow(rc)
                val te = godanTe(rc); val ta = teToTa(te)
                add(r, k)                                          // 終止/連体  押す
                add(rs + te, ks + te)                              // て形       押して
                add(rs + ta, ks + ta)                              // た形       押した
                add(rs + i, ks + i)                                // 連用       押し
                add(rs + i + "ます", ks + i + "ます")
                add(rs + i + "ません", ks + i + "ません")
                add(rs + i + "ました", ks + i + "ました")
                add(rs + i + "たい", ks + i + "たい")
                add(rs + a + "ない", ks + a + "ない")
                add(rs + a + "なかった", ks + a + "なかった")
                add(rs + e + "ば", ks + e + "ば")
                add(rs + o + "う", ks + o + "う")                  // 意志       押そう
            }
            Vk.GODAN_IKU -> {                                      // 行く型 (て/た形のみ促音便)
                if (r.length < 2 || k.isEmpty()) { add(r, k); return }
                val rs = r.dropLast(1); val ks = k.dropLast(1)     // いく→い / 行く→行
                add(r, k)                                          // 行く
                add(rs + "って", ks + "って")                      // 行って (例外)
                add(rs + "った", ks + "った")                      // 行った (例外)
                add(rs + "き", ks + "き")                          // 連用 行き
                add(rs + "きます", ks + "きます")
                add(rs + "きません", ks + "きません")
                add(rs + "きました", ks + "きました")
                add(rs + "きたい", ks + "きたい")
                add(rs + "かない", ks + "かない")
                add(rs + "かなかった", ks + "かなかった")
                add(rs + "けば", ks + "けば")
                add(rs + "こう", ks + "こう")
            }
            Vk.ICHIDAN -> {
                if (!r.endsWith("る") || k.isEmpty()) { add(r, k); return }
                val rs = r.dropLast(1); val ks = k.dropLast(1)
                add(r, k)                                          // 食べる
                add(rs + "て", ks + "て")
                add(rs + "た", ks + "た")
                add(rs, ks)                                        // 連用       食べ
                add(rs + "ます", ks + "ます")
                add(rs + "ません", ks + "ません")
                add(rs + "ました", ks + "ました")
                add(rs + "たい", ks + "たい")
                add(rs + "ない", ks + "ない")
                add(rs + "なかった", ks + "なかった")
                add(rs + "れば", ks + "れば")
                add(rs + "よう", ks + "よう")
            }
            Vk.SURU -> {
                if (!r.endsWith("する") || k.isEmpty()) { add(r, k); return }
                val rs = r.dropLast(2); val ks = k.dropLast(2)
                add(rs + "する", ks + "する")
                add(rs + "して", ks + "して")
                add(rs + "した", ks + "した")
                add(rs + "します", ks + "します")
                add(rs + "しません", ks + "しません")
                add(rs + "しました", ks + "しました")
                add(rs + "したい", ks + "したい")
                add(rs + "しない", ks + "しない")
                add(rs + "すれば", ks + "すれば")
                add(rs + "しよう", ks + "しよう")
            }
            Vk.KURU -> {                                            // 来る (固定: 漢字は常に「来」)
                add("くる", "来る"); add("きて", "来て"); add("きた", "来た")
                add("きます", "来ます"); add("きません", "来ません"); add("きました", "来ました")
                add("こない", "来ない"); add("こなかった", "来なかった")
                add("くれば", "来れば"); add("こよう", "来よう"); add("きたい", "来たい")
            }
            Vk.ADJ -> {
                if (!r.endsWith("い") || k.isEmpty()) { add(r, k); return }
                val rs = r.dropLast(1); val ks = k.dropLast(1)
                add(r, k)                                          // 高い
                add(rs + "くて", ks + "くて")
                add(rs + "く", ks + "く")
                add(rs + "くない", ks + "くない")
                add(rs + "かった", ks + "かった")
                add(rs + "くなかった", ks + "くなかった")
                add(rs + "ければ", ks + "ければ")
            }
        }
    }

    /** 常用語テーブルを活用展開し、見出し順にソートした辞書行 ("よみ /漢字/…") を返す。 */
    private fun buildSupplement(): List<String> {
        val map = LinkedHashMap<String, LinkedHashSet<String>>()
        val add: (String, String) -> Unit = { reading, kanji ->
            if (reading.isNotEmpty() && kanji.isNotEmpty()) {
                map.getOrPut(reading) { LinkedHashSet() }.add(kanji)
            }
        }
        for ((r, k, vk) in SUPPLEMENT_WORDS) generateForms(r, k, vk, add)
        return map.entries
            .sortedBy { it.key }
            .map { (r, ks) -> "$r /" + ks.joinToString("/") + "/" }
    }

    /**
     * 見出し順ソート済みの [base] (元辞書) と [extra] (補完辞書) を、見出しでマージする。
     * 同じ見出しは候補を結合し、補完候補を先頭に置く (補完語を優先表示)。結果も見出し順を保つので
     * 二分探索 ([searchIndex]) がそのまま使える。
     */
    private fun mergeDict(base: List<String>, extra: List<String>): List<String> {
        if (extra.isEmpty()) return base
        val out = ArrayList<String>(base.size + extra.size)
        var i = 0
        var j = 0
        while (i < base.size && j < extra.size) {
            val hb = headOf(base[i])
            val he = headOf(extra[j])
            val c = hb.compareTo(he)
            when {
                c < 0 -> { out.add(base[i]); i++ }
                c > 0 -> { out.add(extra[j]); j++ }
                else -> {
                    val combined = LinkedHashSet<String>()
                    combined.addAll(candidatesOf(extra[j]))   // 補完候補を優先
                    combined.addAll(candidatesOf(base[i]))
                    out.add("$he /" + combined.joinToString("/") + "/")
                    i++; j++
                }
            }
        }
        while (i < base.size) { out.add(base[i]); i++ }
        while (j < extra.size) { out.add(extra[j]); j++ }
        return out
    }

    /**
     * 内蔵の常用語テーブル (よみ終止形, 漢字終止形, 活用種別)。
     * 日常でよく使う動詞・形容詞・サ変・カ変を中心に厳選。ここに無い語は従来どおり
     * okuriForms / segment の推測でカバーする。読みが重複するもの (きる=切る/着る 等) は
     * [buildSupplement] が候補を結合する。
     */
    private val SUPPLEMENT_WORDS: List<Triple<String, String, Vk>> = listOf(
        // ---- 五段動詞 ----
        Triple("おす", "押す", Vk.GODAN),
        // --- 追加常用語 (打つ 等の抜け補完 + 端末でよく使うサ変) ---
        Triple("うつ", "打つ", Vk.GODAN),
        Triple("あらう", "洗う", Vk.GODAN),
        Triple("わらう", "笑う", Vk.GODAN),
        Triple("はらう", "払う", Vk.GODAN),
        Triple("うたう", "歌う", Vk.GODAN),
        Triple("ひろう", "拾う", Vk.GODAN),
        Triple("とおる", "通る", Vk.GODAN),
        Triple("わたる", "渡る", Vk.GODAN),
        Triple("ふむ", "踏む", Vk.GODAN),
        Triple("つつむ", "包む", Vk.GODAN),
        Triple("さわる", "触る", Vk.GODAN),
        Triple("まがる", "曲がる", Vk.GODAN),
        Triple("こまる", "困る", Vk.GODAN),
        Triple("おこる", "怒る", Vk.GODAN),
        Triple("よろこぶ", "喜ぶ", Vk.GODAN),
        Triple("かざる", "飾る", Vk.GODAN),
        Triple("いのる", "祈る", Vk.GODAN),
        Triple("しらべる", "調べる", Vk.ICHIDAN),
        Triple("くらべる", "比べる", Vk.ICHIDAN),
        Triple("そだてる", "育てる", Vk.ICHIDAN),
        Triple("すてる", "捨てる", Vk.ICHIDAN),
        Triple("まける", "負ける", Vk.ICHIDAN),
        Triple("みせる", "見せる", Vk.ICHIDAN),
        Triple("いきる", "生きる", Vk.ICHIDAN),
        Triple("たてる", "立てる", Vk.ICHIDAN),
        Triple("あびる", "浴びる", Vk.ICHIDAN),
        Triple("おもしろい", "面白い", Vk.ADJ),
        Triple("すごい", "凄い", Vk.ADJ),
        Triple("きたない", "汚い", Vk.ADJ),
        Triple("ほぞんする", "保存する", Vk.SURU),
        Triple("さくじょする", "削除する", Vk.SURU),
        Triple("じっこうする", "実行する", Vk.SURU),
        Triple("きどうする", "起動する", Vk.SURU),
        Triple("へんこうする", "変更する", Vk.SURU),
        Triple("ついかする", "追加する", Vk.SURU),
        Triple("はいる", "入る", Vk.GODAN),
        Triple("とる", "取る", Vk.GODAN),
        Triple("わかる", "分かる", Vk.GODAN),
        Triple("つくる", "作る", Vk.GODAN),
        Triple("のる", "乗る", Vk.GODAN),
        Triple("うる", "売る", Vk.GODAN),
        Triple("きる", "切る", Vk.GODAN),
        Triple("しる", "知る", Vk.GODAN),
        Triple("いる", "要る", Vk.GODAN),
        Triple("まつ", "待つ", Vk.GODAN),
        Triple("もつ", "持つ", Vk.GODAN),
        Triple("かつ", "勝つ", Vk.GODAN),
        Triple("たつ", "立つ", Vk.GODAN),
        Triple("かう", "買う", Vk.GODAN),
        Triple("いう", "言う", Vk.GODAN),
        Triple("あう", "会う", Vk.GODAN),
        Triple("すう", "吸う", Vk.GODAN),
        Triple("つかう", "使う", Vk.GODAN),
        Triple("おもう", "思う", Vk.GODAN),
        Triple("ならう", "習う", Vk.GODAN),
        Triple("てつだう", "手伝う", Vk.GODAN),
        Triple("かく", "書く", Vk.GODAN),
        Triple("きく", "聞く", Vk.GODAN),
        Triple("なく", "泣く", Vk.GODAN),
        Triple("あるく", "歩く", Vk.GODAN),
        Triple("うごく", "動く", Vk.GODAN),
        Triple("はたらく", "働く", Vk.GODAN),
        Triple("つく", "付く", Vk.GODAN),
        Triple("ひく", "引く", Vk.GODAN),
        Triple("おく", "置く", Vk.GODAN),
        Triple("およぐ", "泳ぐ", Vk.GODAN),
        Triple("いそぐ", "急ぐ", Vk.GODAN),
        Triple("ぬぐ", "脱ぐ", Vk.GODAN),
        Triple("はなす", "話す", Vk.GODAN),
        Triple("だす", "出す", Vk.GODAN),
        Triple("かえす", "返す", Vk.GODAN),
        Triple("けす", "消す", Vk.GODAN),
        Triple("わたす", "渡す", Vk.GODAN),
        Triple("さがす", "探す", Vk.GODAN),
        Triple("ためす", "試す", Vk.GODAN),
        Triple("なおす", "直す", Vk.GODAN),
        Triple("しぬ", "死ぬ", Vk.GODAN),
        Triple("あそぶ", "遊ぶ", Vk.GODAN),
        Triple("よぶ", "呼ぶ", Vk.GODAN),
        Triple("とぶ", "飛ぶ", Vk.GODAN),
        Triple("えらぶ", "選ぶ", Vk.GODAN),
        Triple("はこぶ", "運ぶ", Vk.GODAN),
        Triple("のむ", "飲む", Vk.GODAN),
        Triple("よむ", "読む", Vk.GODAN),
        Triple("やすむ", "休む", Vk.GODAN),
        Triple("すむ", "住む", Vk.GODAN),
        Triple("すすむ", "進む", Vk.GODAN),
        Triple("たのむ", "頼む", Vk.GODAN),
        Triple("こむ", "込む", Vk.GODAN),
        Triple("いく", "行く", Vk.GODAN_IKU),
        Triple("なる", "成る", Vk.GODAN),
        Triple("わる", "割る", Vk.GODAN),
        Triple("ふる", "降る", Vk.GODAN),
        Triple("かえる", "帰る", Vk.GODAN),
        Triple("はしる", "走る", Vk.GODAN),
        Triple("しまる", "閉まる", Vk.GODAN),
        Triple("はじまる", "始まる", Vk.GODAN),
        Triple("おわる", "終わる", Vk.GODAN),
        Triple("かわる", "変わる", Vk.GODAN),
        Triple("まわる", "回る", Vk.GODAN),
        Triple("のこる", "残る", Vk.GODAN),
        Triple("すわる", "座る", Vk.GODAN),
        Triple("おくる", "送る", Vk.GODAN),
        Triple("まもる", "守る", Vk.GODAN),
        Triple("とまる", "止まる", Vk.GODAN),
        Triple("きまる", "決まる", Vk.GODAN),
        Triple("あつまる", "集まる", Vk.GODAN),
        Triple("みつかる", "見つかる", Vk.GODAN),
        Triple("もどる", "戻る", Vk.GODAN),
        Triple("のぼる", "登る", Vk.GODAN),
        Triple("くばる", "配る", Vk.GODAN),
        Triple("がんばる", "頑張る", Vk.GODAN),
        Triple("へる", "減る", Vk.GODAN),
        // ---- 一段動詞 ----
        Triple("たべる", "食べる", Vk.ICHIDAN),
        Triple("みる", "見る", Vk.ICHIDAN),
        Triple("でる", "出る", Vk.ICHIDAN),
        Triple("いれる", "入れる", Vk.ICHIDAN),
        Triple("あける", "開ける", Vk.ICHIDAN),
        Triple("しめる", "閉める", Vk.ICHIDAN),
        Triple("つける", "付ける", Vk.ICHIDAN),
        Triple("きめる", "決める", Vk.ICHIDAN),
        Triple("はじめる", "始める", Vk.ICHIDAN),
        Triple("とめる", "止める", Vk.ICHIDAN),
        Triple("あつめる", "集める", Vk.ICHIDAN),
        Triple("みつける", "見つける", Vk.ICHIDAN),
        Triple("うける", "受ける", Vk.ICHIDAN),
        Triple("かける", "掛ける", Vk.ICHIDAN),
        Triple("わける", "分ける", Vk.ICHIDAN),
        Triple("あげる", "上げる", Vk.ICHIDAN),
        Triple("さげる", "下げる", Vk.ICHIDAN),
        Triple("なげる", "投げる", Vk.ICHIDAN),
        Triple("にげる", "逃げる", Vk.ICHIDAN),
        Triple("たすける", "助ける", Vk.ICHIDAN),
        Triple("かんがえる", "考える", Vk.ICHIDAN),
        Triple("おしえる", "教える", Vk.ICHIDAN),
        Triple("おぼえる", "覚える", Vk.ICHIDAN),
        Triple("こたえる", "答える", Vk.ICHIDAN),
        Triple("かえる", "変える", Vk.ICHIDAN),
        Triple("ふえる", "増える", Vk.ICHIDAN),
        Triple("きえる", "消える", Vk.ICHIDAN),
        Triple("みえる", "見える", Vk.ICHIDAN),
        Triple("ねる", "寝る", Vk.ICHIDAN),
        Triple("でかける", "出かける", Vk.ICHIDAN),
        Triple("わすれる", "忘れる", Vk.ICHIDAN),
        Triple("なれる", "慣れる", Vk.ICHIDAN),
        Triple("うまれる", "生まれる", Vk.ICHIDAN),
        Triple("つかれる", "疲れる", Vk.ICHIDAN),
        Triple("きる", "着る", Vk.ICHIDAN),
        Triple("かりる", "借りる", Vk.ICHIDAN),
        Triple("おりる", "降りる", Vk.ICHIDAN),
        Triple("すぎる", "過ぎる", Vk.ICHIDAN),
        Triple("とじる", "閉じる", Vk.ICHIDAN),
        Triple("かんじる", "感じる", Vk.ICHIDAN),
        Triple("しんじる", "信じる", Vk.ICHIDAN),
        Triple("できる", "出来る", Vk.ICHIDAN),
        // ---- カ変 (来る) ----
        Triple("くる", "来る", Vk.KURU),
        // ---- サ変 (名詞 + する) ----
        Triple("べんきょうする", "勉強する", Vk.SURU),
        Triple("りょうりする", "料理する", Vk.SURU),
        Triple("そうじする", "掃除する", Vk.SURU),
        Triple("せんたくする", "洗濯する", Vk.SURU),
        Triple("うんどうする", "運動する", Vk.SURU),
        Triple("でんわする", "電話する", Vk.SURU),
        Triple("よやくする", "予約する", Vk.SURU),
        Triple("せつめいする", "説明する", Vk.SURU),
        Triple("じゅんびする", "準備する", Vk.SURU),
        Triple("かくにんする", "確認する", Vk.SURU),
        Triple("れんしゅうする", "練習する", Vk.SURU),
        Triple("しょくじする", "食事する", Vk.SURU),
        Triple("さんぽする", "散歩する", Vk.SURU),
        Triple("けんきゅうする", "研究する", Vk.SURU),
        Triple("りようする", "利用する", Vk.SURU),
        Triple("せいりする", "整理する", Vk.SURU),
        Triple("へんじする", "返事する", Vk.SURU),
        Triple("せっていする", "設定する", Vk.SURU),
        // ---- い形容詞 ----
        Triple("たかい", "高い", Vk.ADJ),
        Triple("やすい", "安い", Vk.ADJ),
        Triple("ひくい", "低い", Vk.ADJ),
        Triple("おおきい", "大きい", Vk.ADJ),
        Triple("ちいさい", "小さい", Vk.ADJ),
        Triple("あたらしい", "新しい", Vk.ADJ),
        Triple("ふるい", "古い", Vk.ADJ),
        Triple("よい", "良い", Vk.ADJ),
        Triple("たのしい", "楽しい", Vk.ADJ),
        Triple("うれしい", "嬉しい", Vk.ADJ),
        Triple("かなしい", "悲しい", Vk.ADJ),
        Triple("さびしい", "寂しい", Vk.ADJ),
        Triple("いそがしい", "忙しい", Vk.ADJ),
        Triple("やさしい", "優しい", Vk.ADJ),
        Triple("むずかしい", "難しい", Vk.ADJ),
        Triple("あつい", "暑い", Vk.ADJ),
        Triple("さむい", "寒い", Vk.ADJ),
        Triple("すずしい", "涼しい", Vk.ADJ),
        Triple("あたたかい", "暖かい", Vk.ADJ),
        Triple("つめたい", "冷たい", Vk.ADJ),
        Triple("とおい", "遠い", Vk.ADJ),
        Triple("ちかい", "近い", Vk.ADJ),
        Triple("はやい", "早い", Vk.ADJ),
        Triple("おそい", "遅い", Vk.ADJ),
        Triple("ながい", "長い", Vk.ADJ),
        Triple("みじかい", "短い", Vk.ADJ),
        Triple("ひろい", "広い", Vk.ADJ),
        Triple("せまい", "狭い", Vk.ADJ),
        Triple("おもい", "重い", Vk.ADJ),
        Triple("かるい", "軽い", Vk.ADJ),
        Triple("つよい", "強い", Vk.ADJ),
        Triple("よわい", "弱い", Vk.ADJ),
        Triple("あかるい", "明るい", Vk.ADJ),
        Triple("くらい", "暗い", Vk.ADJ),
        Triple("うつくしい", "美しい", Vk.ADJ),
        Triple("あぶない", "危ない", Vk.ADJ),
        Triple("いたい", "痛い", Vk.ADJ),
        Triple("ほしい", "欲しい", Vk.ADJ),
        Triple("おいしい", "美味しい", Vk.ADJ),
        Triple("わかい", "若い", Vk.ADJ),
        Triple("あまい", "甘い", Vk.ADJ),
        Triple("からい", "辛い", Vk.ADJ),
        Triple("おおい", "多い", Vk.ADJ),
        Triple("すくない", "少ない", Vk.ADJ),
        Triple("ただしい", "正しい", Vk.ADJ),
    )
}

/** ひらがな文字列をカタカナへ。 */
fun hiraganaToKatakana(s: String): String =
    buildString(s.length) {
        for (ch in s) append(if (ch in 'ぁ'..'ゖ') ch + 0x60 else ch)
    }

// ----------------------------------------------------------------------------
// 濁点/半濁点/小書き循環テーブル (JapaneseFlickKeyboard の `小゛゜` キーと
// ComposingState の連打サイクルの両方が参照)。
// 順番は base → 小書き → 濁点 → 半濁点。
// 例: つ → っ → づ → つ、う → ぅ → ゔ → う、は → ば → ぱ → は。
// ----------------------------------------------------------------------------
internal val CYCLE_GROUPS: List<List<Char>> = listOf(
    listOf('あ', 'ぁ'), listOf('い', 'ぃ'), listOf('う', 'ぅ', 'ゔ'), listOf('え', 'ぇ'), listOf('お', 'ぉ'),
    listOf('か', 'が'), listOf('き', 'ぎ'), listOf('く', 'ぐ'), listOf('け', 'げ'), listOf('こ', 'ご'),
    listOf('さ', 'ざ'), listOf('し', 'じ'), listOf('す', 'ず'), listOf('せ', 'ぜ'), listOf('そ', 'ぞ'),
    listOf('た', 'だ'), listOf('ち', 'ぢ'), listOf('つ', 'っ', 'づ'), listOf('て', 'で'), listOf('と', 'ど'),
    listOf('は', 'ば', 'ぱ'), listOf('ひ', 'び', 'ぴ'), listOf('ふ', 'ぶ', 'ぷ'), listOf('へ', 'べ', 'ぺ'), listOf('ほ', 'ぼ', 'ぽ'),
    listOf('や', 'ゃ'), listOf('ゆ', 'ゅ'), listOf('よ', 'ょ'), listOf('わ', 'ゎ')
)

/** char → (グループ, そのグループ内 index)。`小゛゜` 循環と連打サイクルで共有。 */
internal val CYCLE_INDEX: Map<Char, Pair<List<Char>, Int>> = buildMap {
    for (group in CYCLE_GROUPS) {
        for ((i, c) in group.withIndex()) put(c, group to i)
    }
}

/**
 * 変換の入力状態 (composing) を保持するホルダ。
 *
 * キーボード(かな入力)が [emitKana] / [append] で積み、候補バーが [candidates] を表示し、
 * 確定で [onCommit] により PTY へ送出する。Compose state なので変化すると関係する
 * Composable が再描画される。
 *
 * **スプリット変換モード**:
 *   [convert] を呼ぶと [splitHeadLen] が >0 になり、`text` の先頭 [splitHeadLen] 文字が
 *   「現在変換中のセグメント」となる。`◀ ▶` で範囲を [shrinkSplitHead] / [extendSplitHead]
 *   で調整できる。セグメントを確定 ([commit] / [commitRaw]) すると、その分を PTY へ送出し、
 *   残りに対して自動でつぎの分割長を決め、フォーカスを次のブロックへ移す。残り 0 文字で抜ける。
 *   候補 ([candidates]) はスプリット中はセグメント分のみ参照する。
 *
 * **候補サイクル**:
 *   スプリット中に [convert] を続けて押すと [selectedCandidateIndex] が -1 → 0 → 1 → ... →
 *   末尾 → -1 と循環する。-1 = 生のかな (頭セグメント) を確定対象とする状態。⏎ ([commitRaw])
 *   は 「現在選択中の対象」を確定する (生かな or 選択中の候補)。
 *
 * **小書き/濁点**:
 *   [CYCLE_INDEX] の循環 (例: は→ば→ぱ→は、つ→っ→づ→つ) は `小゛゜` キー
 *   ([JapaneseFlickKeyboard] の `cycleDakuten`) だけが使う。かなの連打は循環させず素直に
 *   重ねる (「つつ」が「っ」にならないように)。
 *
 * **再変換**:
 *   composing が空の状態で [restoreLastCommit] を呼ぶと、直前 [commit] / [commitRaw] した
 *   読みを composing に戻し、確定文字数 (= 端末から削除すべきコードポイント数) を返す。
 *   呼び出し側はその数だけ 0x7F (DEL) を送って端末の確定済みテキストを消し、続けて
 *   [convert] で再度変換を始める想定。
 */
class ComposingState(
    private val onCommit: (String) -> Unit
) {
    var text by mutableStateOf("")
        private set
    var candidates by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * 長文の「一括予測」候補。スプリット中で後続 (tail) が残っているとき、読み全体の最尤変換
     * ([KkcConverter.convert] = Viterbi)。null なら無し。
     * 候補バーで専用ピルとして出し、タップ ([commitFull]) で全文を一括確定する。
     */
    var fullPrediction by mutableStateOf<String?>(null)
        private set

    /**
     * [fullPrediction] を構成する文節ブロックの (読み, 表層) 内訳。一括確定 ([commitFull]) 時に
     * ブロック単位で学習させるため保持する。文全体を 1 キーで学習すると、その読みが丸ごと再来した
     * ときしか効かず、頻用ブロック (今日の / 天気は …) の再利用が効かない。
     */
    private var fullPredictionBlocks: List<Pair<String, String>> = emptyList()

    /** スプリットモード: 0 なら未起動。1..text.length なら先頭 splitHeadLen 文字がフォーカス。 */
    var splitHeadLen by mutableStateOf(0)
        private set

    /** 候補サイクル: -1 = 生かな (頭セグメント) 選択中、0..candidates.size-1 = 候補選択中。 */
    var selectedCandidateIndex by mutableStateOf(-1)
        private set

    /** 直前 commit の読み (再変換用)。次の commit や restore で更新/クリア。 */
    var lastCommittedReading by mutableStateOf<String?>(null)
        private set
    /** 直前 commit で PTY へ送った文字列 (端末から消すべき長さの算出用)。 */
    var lastCommittedOutput by mutableStateOf<String?>(null)
        private set

    /** 連打サイクル: 直前 emit したかな文字とそのタイムスタンプ。 */
    private var lastEmitChar: Char? = null
    private var lastEmitTimeMs: Long = 0L

    /**
     * 直前に確定した語/文節の表層 (bigram 学習・リランクの前語コンテキスト)。
     * セグメント/全文の確定で更新し、composing を明示リセット ([reset]) したらクリアする。
     */
    private var prevCommitSurface: String? = null

    /**
     * 現在のスプリットが「長文の自動分割」由来か (true) / 「変換キーによる手動分割」由来か (false)。
     * 自動分割中は ⌫ で素直に 1 文字消す (手動分割中は ⌫ でまず分割取消) ように分岐するために持つ。
     */
    private var autoSplit: Boolean = false

    /**
     * 同一スプリット run 中に連続確定したブロックの (読み, 表層)。run が尽きたとき (または
     * 一括確定時) に「結合読み → 結合表層」を [ImeHistoryStore] へ記録し、頻用の塊
     * (びる+ド → ビルド 等) を次回以降 1 ブロックへ繋ぎ止める材料にする ([KkcConverter.learnedBlock])。
     * これが無いと、自動分割で割れた語は常に各ブロック単体でしか学習されず、何度使っても
     * 「びるど」全体が履歴に入らないため分割が解消しなかった。
     */
    private val committedRun = ArrayList<Pair<String, String>>()

    val isActive: Boolean get() = text.isNotEmpty()
    val isSplitMode: Boolean get() = splitHeadLen > 0
    /** スプリット中のフォーカス文字列 (先頭セグメント)。非スプリット中は text 全体を返す。 */
    val splitHead: String
        get() = if (isSplitMode) text.substring(0, splitHeadLen.coerceAtMost(text.length)) else text
    /** スプリット中のフォーカス外 (尾側)。非スプリット中は空文字列。 */
    val splitTail: String
        get() = if (isSplitMode && splitHeadLen < text.length) text.substring(splitHeadLen) else ""
    /** 再変換可能か (composing が空 ∧ 直前 commit が残っている)。 */
    val canReconvert: Boolean
        get() = text.isEmpty() && !lastCommittedReading.isNullOrEmpty()

    /**
     * かな 1 文字をタップ入力する。常に通常 append する。
     *
     * 以前は「同じかなを短時間に連打すると末尾を濁点/半濁点/小書きへ
     * 循環」していたが、これだと「つつ」が打てず「っ」になってしまう。小書き・濁点は専用の
     * `小゛゜` キー ([JapaneseFlickKeyboard] の `cycleDakuten`) があるので、連打は循環させず
     * 素直に同じ文字を重ねる (ユーザー要望)。
     */
    fun emitKana(ch: Char) {
        splitHeadLen = 0
        autoSplit = false
        text += ch
        lastEmitChar = ch
        lastEmitTimeMs = System.currentTimeMillis()
        selectedCandidateIndex = -1
        reevaluateAutoSplit()
    }

    /**
     * 任意の 1 文字を composing に積む (記号 ／ プログラム経由)。連打サイクルの履歴は
     * リセットされる (次に同じかなが来ても循環しない)。
     */
    fun append(ch: Char) {
        splitHeadLen = 0
        autoSplit = false
        text += ch
        lastEmitChar = null
        lastEmitTimeMs = 0
        selectedCandidateIndex = -1
        reevaluateAutoSplit()
    }

    /** 直前の文字を [s] (濁点等) に置換。スプリット中なら抜けてから置換する。 */
    fun replaceLast(s: Char) {
        if (text.isEmpty()) return
        splitHeadLen = 0
        autoSplit = false
        text = text.dropLast(1) + s
        lastEmitChar = null
        lastEmitTimeMs = 0
        selectedCandidateIndex = -1
        reevaluateAutoSplit()
    }

    /**
     * ⌫。スプリット中はまずスプリットを抜ける (取消) だけで文字は消さない。
     * 非スプリット時のみ末尾 1 文字を削除する。消費したら true。
     */
    fun backspace(): Boolean {
        if (text.isEmpty()) return false
        // 手動スプリット (変換キーで入った) 中は、まずスプリット取消だけで文字は消さない。
        if (isSplitMode && !autoSplit) {
            splitHeadLen = 0
            selectedCandidateIndex = -1
            refreshPredict()
            return true
        }
        // 自動スプリット中 or 非スプリット: 末尾 1 文字を削除して長文判定をやり直す。
        splitHeadLen = 0
        autoSplit = false
        text = text.dropLast(1)
        lastEmitChar = null
        lastEmitTimeMs = 0
        selectedCandidateIndex = -1
        if (text.isEmpty()) candidates = emptyList() else reevaluateAutoSplit()
        return true
    }

    /**
     * 変換キー。未スプリットなら [KanaKanjiConverter.autoSplitHeadLen] で先頭セグメント長を
     * 自動決定してスプリットモードへ入る。すでにスプリット中なら**候補サイクル**:
     * `-1 (生かな) → 0 → 1 → ... → 末尾 → -1` を巡る。
     */
    fun convert() {
        if (text.isEmpty()) return
        if (!isSplitMode) {
            splitHeadLen = KanaKanjiConverter.autoSplitHeadLen(text)
                .coerceAtLeast(1).coerceAtMost(text.length)
            autoSplit = false   // 変換キーによる手動分割
            selectedCandidateIndex = -1
            refreshPredict()
        } else {
            val n = candidates.size
            if (n == 0) {
                selectedCandidateIndex = -1
                return
            }
            selectedCandidateIndex =
                if (selectedCandidateIndex >= n - 1) -1 else selectedCandidateIndex + 1
        }
    }

    /** スプリット中: フォーカスを 1 文字広げる (末尾までで止まる)。候補選択はリセット。 */
    fun extendSplitHead() {
        if (!isSplitMode) return
        if (splitHeadLen < text.length) {
            splitHeadLen++
            selectedCandidateIndex = -1
            refreshPredict()
        }
    }

    /** スプリット中: フォーカスを 1 文字縮める (最小 1 文字)。候補選択はリセット。 */
    fun shrinkSplitHead() {
        if (!isSplitMode) return
        if (splitHeadLen > 1) {
            splitHeadLen--
            selectedCandidateIndex = -1
            refreshPredict()
        }
    }

    /**
     * 候補を確定。スプリット中は現在セグメントだけを送り、残りに対して次のセグメントを自動分割。
     * 残り 0 ならスプリットを終了する。学習履歴 ([ImeHistoryStore]) にも記録し、
     * [lastCommittedReading] / [lastCommittedOutput] を更新する (再変換用)。
     */
    fun commit(candidate: String) {
        if (text.isEmpty()) return
        val key = if (isSplitMode) splitHead else text
        ImeHistoryStore.record(key, candidate)
        prevCommitSurface?.let { ImeHistoryStore.recordBigram(it, candidate) }
        prevCommitSurface = candidate
        if (isSplitMode) committedRun.add(key to candidate)
        onCommit(candidate)
        lastCommittedReading = key
        lastCommittedOutput = candidate
        advanceSegmentOrReset()
    }

    /**
     * 長文の一括予測 ([fullPrediction]) を確定する。現在の composing 全体 (head + tail) を
     * まとめて送り、composing をリセットする。[fullPrediction] が無ければ何もせず false。
     */
    fun commitFull(): Boolean {
        val full = fullPrediction ?: return false
        if (text.isEmpty()) return false
        // ブロック単位で学習する: 文全体を 1 キーで覚えると同じ読みが丸ごと再来したときしか効かず、
        // 頻用ブロック (今日の / 天気は …) が別の文で再利用されない。各ブロックの (読み→表層) と
        // ブロック間の bigram を記録し、次回以降は文中でも打ち慣れたブロックが優先されるようにする。
        val blocks = fullPredictionBlocks
        if (blocks.size >= 2 && blocks.joinToString("") { it.second } == full) {
            var prev = prevCommitSurface
            for ((r, s) in blocks) {
                ImeHistoryStore.record(r, s)
                prev?.let { ImeHistoryStore.recordBigram(it, s) }
                prev = s
            }
            // ブロック単体に加え、短い結合読みは「結合ブロック」としても学習する。これで
            // 一括確定 (ビルド 等) でも全体読みが履歴に入り、次回 1 ブロックへ繋ぎ止まる。
            if (text.length in 2..MERGE_MAX_READING_LEN) ImeHistoryStore.record(text, full)
        } else {
            // 内訳が取れない/不整合なら従来どおり文全体を 1 エントリで学習。
            ImeHistoryStore.record(text, full)
            prevCommitSurface?.let { ImeHistoryStore.recordBigram(it, full) }
        }
        prevCommitSurface = full
        committedRun.clear()
        onCommit(full)
        lastCommittedReading = text
        lastCommittedOutput = full
        text = ""
        candidates = emptyList()
        fullPrediction = null
        fullPredictionBlocks = emptyList()
        splitHeadLen = 0
        autoSplit = false
        selectedCandidateIndex = -1
        lastEmitChar = null
        lastEmitTimeMs = 0
        return true
    }

    /**
     * ⏎ などの「現在選択中を確定」。候補サイクル中なら選択候補を、そうでなければ生かな
     * (スプリット中はセグメントのみ) を確定する。空なら false。
     */
    fun commitRaw(): Boolean {
        if (text.isEmpty()) return false
        // 候補サイクル中なら、その候補を確定 (commit 経由で学習履歴と再変換情報も更新される)
        if (selectedCandidateIndex in candidates.indices) {
            commit(candidates[selectedCandidateIndex])
            return true
        }
        val toCommit = if (isSplitMode) splitHead else text
        ImeHistoryStore.record(toCommit, toCommit)
        prevCommitSurface?.let { ImeHistoryStore.recordBigram(it, toCommit) }
        prevCommitSurface = toCommit
        if (isSplitMode) committedRun.add(toCommit to toCommit)
        onCommit(toCommit)
        lastCommittedReading = toCommit
        lastCommittedOutput = toCommit
        advanceSegmentOrReset()
        return true
    }

    fun reset() {
        text = ""
        candidates = emptyList()
        fullPrediction = null
        fullPredictionBlocks = emptyList()
        splitHeadLen = 0
        autoSplit = false
        selectedCandidateIndex = -1
        lastEmitChar = null
        lastEmitTimeMs = 0
        committedRun.clear()
        // composing を明示的に閉じたら bigram の前語コンテキストもクリア (文の流れが切れる)。
        prevCommitSurface = null
        // 注意: lastCommittedReading / lastCommittedOutput は意図的に残す
        //   (再変換は composing が空でも使える機能)。
    }

    /**
     * 再変換: 直前 commit した読みを composing に復元する。返り値は端末側で消すべき
     * **コードポイント数** (= 直前 PTY 出力の長さ)。0 なら復元不可。
     * 呼び出し側はその数だけ 0x7F (DEL) を送って端末を整える想定。
     */
    fun restoreLastCommit(): Int {
        if (text.isNotEmpty()) return 0
        val r = lastCommittedReading ?: return 0
        val o = lastCommittedOutput ?: return 0
        text = r
        splitHeadLen = 0
        autoSplit = false
        selectedCandidateIndex = -1
        lastEmitChar = null
        lastEmitTimeMs = 0
        val cps = o.codePointCount(0, o.length)
        lastCommittedReading = null
        lastCommittedOutput = null
        reevaluateAutoSplit()
        return cps
    }

    /**
     * 連続確定した run ([committedRun]) を「結合ブロック」として学習させる。読みが短い
     * (≤ [MERGE_MAX_READING_LEN]) 2 ブロック以上のときだけ、結合読み → 結合表層を記録する。
     * 長文 (今日の天気は…) まで丸ごと 1 ブロック化しないよう長さで絞る。記録後 run はクリア。
     */
    private fun learnMergedRun() {
        if (committedRun.size >= 2) {
            val mergedReading = committedRun.joinToString("") { it.first }
            val mergedSurface = committedRun.joinToString("") { it.second }
            if (mergedReading.length in 2..MERGE_MAX_READING_LEN && mergedSurface.isNotEmpty()) {
                ImeHistoryStore.record(mergedReading, mergedSurface)
            }
        }
        committedRun.clear()
    }

    /** スプリット中: 確定後の処理。残りに対して次の分割を行うか、無ければ全リセット。 */
    private fun advanceSegmentOrReset() {
        if (isSplitMode) {
            val remaining = splitTail
            if (remaining.isEmpty()) {
                learnMergedRun()
                text = ""
                candidates = emptyList()
                fullPrediction = null
                splitHeadLen = 0
                autoSplit = false
                selectedCandidateIndex = -1
                lastEmitChar = null
                lastEmitTimeMs = 0
            } else {
                text = remaining
                splitHeadLen = KanaKanjiConverter.autoSplitHeadLen(remaining)
                    .coerceAtLeast(1).coerceAtMost(remaining.length)
                selectedCandidateIndex = -1
                refreshPredict()
            }
        } else {
            text = ""
            candidates = emptyList()
            fullPrediction = null
            splitHeadLen = 0
            autoSplit = false
            selectedCandidateIndex = -1
            lastEmitChar = null
            lastEmitTimeMs = 0
        }
    }

    /**
     * text を変更したあとに呼ぶ。文が 2 文節以上に分かれるとき ([KkcConverter.bunsetsu]) は、変換キーを
     * 押さなくても自動で先頭文節にスプリットし、ブロックごとの予測候補を出す (= 長文を「明日の /
     * 天気は / …」のように区切って予測)。単語 1 個程度 (文節 1 つ) のときは全体予測 (スプリットなし)。
     */
    private fun reevaluateAutoSplit() {
        // text が編集された = 確定 run の連続性が切れたので結合学習用の蓄積を捨てる
        // (segment 確定は advanceSegmentOrReset→refreshPredict 経由で、ここは通らない)。
        committedRun.clear()
        val b = if (text.length >= 2) KkcConverter.bunsetsu(text) else emptyList()
        if (b.size >= 2) {
            autoSplit = true
            splitHeadLen = b[0].first.length.coerceAtLeast(1).coerceAtMost(text.length)
        } else {
            autoSplit = false
            splitHeadLen = 0
        }
        selectedCandidateIndex = -1
        refreshPredict()
    }

    private fun refreshPredict() {
        val key = if (isSplitMode) splitHead else text
        if (key.isEmpty()) {
            candidates = emptyList()
            selectedCandidateIndex = -1
            fullPrediction = null
            return
        }
        candidates = buildList(KanaKanjiConverter.convertFlexible(key, prevSurface = prevCommitSurface), key)
        if (selectedCandidateIndex >= candidates.size) selectedCandidateIndex = -1
        // 長文の一括予測: スプリット中で後続 (tail) が残っているとき、
        //   「先頭ブロックの最尤候補 + 残りかなの最尤」を組み合わせた「文まるごと」候補を出す。
        // 以前は読み全体 (composing.text) の Viterbi 1-best を使っていたため、◀▶ で
        // splitHeadLen を動かしても一括予測ピル (薄緑) が変わらなかった (ユーザー要望)。
        // 先頭は candidates 先頭 (= 履歴/Viterbi/辞書を統合した最尤) を使い、残りは
        // 先頭表層を文脈にして tail を Viterbi で 1-best 変換する。
        fullPrediction = if (isSplitMode && splitTail.isNotEmpty()) {
            val headSurface = candidates.firstOrNull() ?: splitHead
            val tailSurface = KkcConverter.nbest(splitTail, 1, KkcConverter.KkcContext(headSurface))
                .firstOrNull()?.surface
                ?: KkcConverter.convert(splitTail)
                ?: splitTail
            // 一括確定時にブロック単位で学習できるよう内訳を控える (先頭ブロック + tail の各文節)。
            // 文全体を 1 キーで覚えると頻用ブロックの再利用が効かないため。
            fullPredictionBlocks = buildList {
                add(splitHead to headSurface)
                addAll(KkcConverter.bunsetsu(splitTail))
            }
            (headSurface + tailSurface).takeIf { it != text }
        } else {
            fullPredictionBlocks = emptyList()
            null
        }
    }

    /** 辞書候補にカタカナを加えた表示用リスト (生ひらがなはバー左のラベルで確定する)。 */
    private fun buildList(dict: List<String>, reading: String): List<String> {
        val out = LinkedHashSet<String>()
        out.addAll(dict)
        val kata = hiraganaToKatakana(reading)
        if (kata != reading) out.add(kata)
        out.remove(reading)
        return out.toList()
    }

    private companion object {
        /** 結合ブロック学習で許す結合読みの最大長 (これより長い文は丸ごと 1 ブロック化しない)。 */
        const val MERGE_MAX_READING_LEN = 6
    }
}
