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
            //
            // さらに、SKK 辞書はカタカナ外来語の読みを英単語綴りへ落とすエントリ (こみっと→commit
            // など) を持たないため、プログラミング/シェルでよく使う ~200 語を内蔵 [buildLoanwords]
            // で追加する (英語小文字のみ。すべてカタカナ語の hiragana 読み)。
            lines = mergeDict(mergeDict(result, buildSupplement()), buildLoanwords())
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

    /** [predict] と同じ前方一致だが、各候補の見出し (読み) を伴って返す。予測の読み逆引き用。 */
    fun predictWithReading(reading: String, limit: Int = 16): List<Pair<String, String>> {
        if (reading.isEmpty() || lines.isEmpty()) return emptyList()
        var idx = searchIndex(reading); if (idx < 0) idx = -idx - 1
        val out = ArrayList<Pair<String, String>>()
        var i = idx
        while (i < lines.size && out.size < limit) {
            val line = lines[i]
            val head = headOf(line)
            if (!head.startsWith(reading)) break
            for (c in candidatesOf(line)) {
                out.add(head to c)
                if (out.size >= limit) break
            }
            i++
        }
        return out
    }

    /**
     * 前方一致の予測候補について「表層 → 実際の読み」を返す (接頭辞より長い読みのものだけ)。
     * 確定時に、打った接頭辞ではなく実際の読みで学習させるために [ComposingState.commit] が参照する。
     * 学習履歴を優先し、辞書見出しで補う。同一表層は最初の読みを採る。
     */
    fun predictionReadingMap(prefix: String): Map<String, String> {
        if (prefix.isEmpty()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for ((r, w) in ImeHistoryStore.predictHistoryWithReading(prefix, limit = 8)) {
            if (r != prefix) map.putIfAbsent(w, r)
        }
        for ((r, w) in predictWithReading(prefix)) {
            if (r != prefix) map.putIfAbsent(w, r)
        }
        return map
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
     *  2. 学習履歴: 前方一致 ([ImeHistoryStore.predictHistory]) — 打った読みで始まる学習済み語句の予測変換
     *  3. 文まるごと最尤変換 ([KkcConverter.convert]) — 読み全体を Viterbi で一発変換
     *  4. 完全一致 ([convert]) / 送り仮名活用 ([okuriForms]) — 単語の別表記候補
     *  5. 前方一致の予測 ([predict]) で補完
     */
    fun convertFlexible(
        reading: String,
        limit: Int = 16,
        prevSurface: String? = null,
        allowPrediction: Boolean = true,
    ): List<String> {
        if (reading.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        // 1. 学習履歴 (完全一致) は最優先で上位表示。loaded 前は空。
        for (h in ImeHistoryStore.historyFor(reading, limit = 4)) {
            out.add(h)
            if (out.size >= limit) return out.toList()
        }
        // 2. 学習履歴 (前方一致) = 本来の予測変換。打った読みで始まる学習済みの語句を変換より先に出す。
        if (allowPrediction) {
            for (h in ImeHistoryStore.predictHistory(reading, limit = 6)) {
                out.add(h)
                if (out.size >= limit) return out.toList()
            }
        }
        // 3. 文まるごとの最尤変換 (Viterbi/IPADIC)。読み全体を最尤の単語列へ一発変換する。
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
            // z2dict 未ロード時は前方一致履歴を上限まで埋める (ステップ2は6件まで)。
            if (allowPrediction) {
                for (h in ImeHistoryStore.predictHistory(reading, limit = limit)) {
                    out.add(h); if (out.size >= limit) break
                }
            }
            return out.toList()
        }
        out.addAll(convert(reading))
        out.addAll(okuriForms(reading))
        // 5. 辞書の前方一致予測 (読みより長い補完) で補う。後続ブロックがある分割の先頭ブロックでは
        //   抑止する: 補完が tail と重なって「して下さい + 下さい」のような被り長文予測を生むため。
        if (allowPrediction) {
            for (c in predict(reading, limit)) {
                out.add(c)
                if (out.size >= limit) break
            }
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

    /**
     * カタカナ外来語の hiragana 読み → 英語小文字綴りを返す内蔵テーブル。
     *
     * SKK 辞書は「こみっと」「ぷっしゅ」のような hiragana 入力に対し英語表記の候補を持たない
     * (持っても /OK/ 等少数で網羅性が無い)。ここではプログラミング/シェル/Git/HTTP まわりで
     * よく使う ~200 語を内蔵し、辞書見出しと同列に merge することで `convert("こみっと")` が
     * 直接 `commit` を返せるようにする。
     *
     * 方針:
     *  - 候補はすべて英語小文字 (commit, push, file, …)。CamelCase/UPPER は学習履歴で個別に
     *    覚えれば足りる。複数候補がある場合 (folder ↔ folder 等の音引き違い) は別エントリ。
     *  - hiragana が日本語固有語と衝突するもの (ぼたん→button vs 牡丹、たぶ→tab vs 他部) は
     *    UX 退行を避けるため除外。長めの確実なカタカナ語のみ採用。
     *  - 単一字 (え, あ 等) や促音/長音だけのキーも除外。
     */
    private val LOANWORD_ENTRIES: List<Pair<String, List<String>>> = listOf(
        // ---- 一般 / OK 系 ----
        "おーけー" to listOf("ok"),
        "おーけい" to listOf("ok"),
        "いえす" to listOf("yes"),
        "のー" to listOf("no"),
        "はろー" to listOf("hello"),
        "わーるど" to listOf("world"),
        "ふー" to listOf("foo"),
        "ばー" to listOf("bar"),
        "ばず" to listOf("baz"),
        "えんたー" to listOf("enter"),
        "えすけーぷ" to listOf("escape"),

        // ---- Git ----
        "こみっと" to listOf("commit"),
        "ぷっしゅ" to listOf("push"),
        "ぷる" to listOf("pull"),
        "ぷるりくえすと" to listOf("pullrequest"),
        "ぷるりく" to listOf("pr"),
        "まーじ" to listOf("merge"),
        "ぶらんち" to listOf("branch"),
        "りべーす" to listOf("rebase"),
        "すたっしゅ" to listOf("stash"),
        "ちぇっくあうと" to listOf("checkout"),
        "ふぉーく" to listOf("fork"),
        "くろーん" to listOf("clone"),
        "りぽじとり" to listOf("repository", "repo"),
        "りぽ" to listOf("repo"),
        "いしゅー" to listOf("issue"),
        "ふぇっち" to listOf("fetch"),
        "ぶれーむ" to listOf("blame"),
        "でぃふ" to listOf("diff"),
        "すてーたす" to listOf("status"),
        "りもーと" to listOf("remote"),
        "おりじん" to listOf("origin"),
        "へっど" to listOf("head"),
        "ますたー" to listOf("master"),
        "めいん" to listOf("main"),
        "こんふりくと" to listOf("conflict"),
        "ろぐ" to listOf("log"),
        "ぎっと" to listOf("git"),
        "ぎっとはぶ" to listOf("github"),
        "ぎっとらぶ" to listOf("gitlab"),

        // ---- Shell / Linux ----
        "ふぁいる" to listOf("file"),
        "でぃれくとり" to listOf("directory"),
        "ふぉるだ" to listOf("folder"),
        "ふぉるだー" to listOf("folder"),
        "ぱす" to listOf("path"),
        "りんく" to listOf("link"),
        "しんぼりっく" to listOf("symbolic"),
        "たーみなる" to listOf("terminal"),
        "しぇる" to listOf("shell"),
        "ばっしゅ" to listOf("bash"),
        "ぜっとしぇる" to listOf("zsh"),
        "こまんど" to listOf("command"),
        "すくりぷと" to listOf("script"),
        "えいりあす" to listOf("alias"),
        "えんびろんめんと" to listOf("environment"),
        "ぷろせす" to listOf("process"),
        "しぐなる" to listOf("signal"),
        "すりーぷ" to listOf("sleep"),
        "うえいと" to listOf("wait"),
        "ぱいぷ" to listOf("pipe"),
        "りだいれくと" to listOf("redirect"),
        "ぱーみっしょん" to listOf("permission"),
        "おーなー" to listOf("owner"),
        "ぐるーぷ" to listOf("group"),
        "まうんと" to listOf("mount"),
        "かーねる" to listOf("kernel"),
        "ろぐいん" to listOf("login"),
        "ろぐあうと" to listOf("logout"),
        "しすてむ" to listOf("system"),
        "でぃすく" to listOf("disk"),
        "めもり" to listOf("memory"),
        "しーぴーゆー" to listOf("cpu"),
        "すれっど" to listOf("thread"),
        "たすく" to listOf("task"),
        "りぶーと" to listOf("reboot"),
        "りせっと" to listOf("reset"),

        // ---- Build / Code ----
        "びるど" to listOf("build"),
        "こんぱいる" to listOf("compile"),
        "ぱっけーじ" to listOf("package"),
        "いんすとーる" to listOf("install"),
        "でぷろい" to listOf("deploy"),
        "りりーす" to listOf("release"),
        "てすと" to listOf("test"),
        "でばっぐ" to listOf("debug"),
        "りんと" to listOf("lint"),
        "ふぉーまっと" to listOf("format"),
        "えらー" to listOf("error"),
        "わーにんぐ" to listOf("warning"),
        "えくせぷしょん" to listOf("exception"),
        "すたっく" to listOf("stack"),
        "とれーす" to listOf("trace"),
        "あさーと" to listOf("assert"),
        "ばーじょん" to listOf("version"),
        "あっぷぐれーど" to listOf("upgrade"),
        "あっぷでーと" to listOf("update"),
        "ふぁんくしょん" to listOf("function"),
        "くらす" to listOf("class"),
        "めそっど" to listOf("method"),
        "ぱらめーた" to listOf("parameter"),
        "ぱらめーたー" to listOf("parameter"),
        "ぼいど" to listOf("void"),
        "ぬる" to listOf("null"),
        "とぅるー" to listOf("true"),
        "ふぉるす" to listOf("false"),
        "りすと" to listOf("list"),
        "あれい" to listOf("array"),
        "まっぷ" to listOf("map"),
        "でぃくしょなり" to listOf("dictionary"),
        "でぃくと" to listOf("dict"),
        "せっと" to listOf("set"),
        "きゅー" to listOf("queue"),
        "つりー" to listOf("tree"),
        "のーど" to listOf("node"),
        "えっじ" to listOf("edge"),
        "ぐらふ" to listOf("graph"),
        "すとりんぐ" to listOf("string"),
        "ばいと" to listOf("byte"),
        "びっと" to listOf("bit"),
        "ぶーる" to listOf("bool"),
        "いんと" to listOf("int"),
        "ふろーと" to listOf("float"),
        "だぶる" to listOf("double"),
        "おぶじぇくと" to listOf("object"),
        "いんすたんす" to listOf("instance"),
        "もじゅーる" to listOf("module"),
        "らいぶらり" to listOf("library"),
        "ふれーむわーく" to listOf("framework"),
        "いんぽーと" to listOf("import"),
        "えくすぽーと" to listOf("export"),
        "いんでっくす" to listOf("index"),
        "いんぷっと" to listOf("input"),
        "あうとぷっと" to listOf("output"),
        "いんさーと" to listOf("insert"),
        "せれくと" to listOf("select"),
        "おぷしょん" to listOf("option"),

        // ---- Network ----
        "さーば" to listOf("server"),
        "さーばー" to listOf("server"),
        "くらいあんと" to listOf("client"),
        "ほすと" to listOf("host"),
        "ぽーと" to listOf("port"),
        "ゆーあーるえる" to listOf("url"),
        "ゆーあーるあい" to listOf("uri"),
        "あどれす" to listOf("address"),
        "あいぴー" to listOf("ip"),
        "どめいん" to listOf("domain"),
        "でぃーえぬえす" to listOf("dns"),
        "えいちてぃーてぃーぴー" to listOf("http"),
        "えいちてぃーてぃーぴーえす" to listOf("https"),
        "てぃーしーぴー" to listOf("tcp"),
        "ゆーでぃーぴー" to listOf("udp"),
        "えすえすえいち" to listOf("ssh"),
        "えすえすえる" to listOf("ssl"),
        "てぃーえるえす" to listOf("tls"),
        "ぷろきし" to listOf("proxy"),
        "げーとうぇい" to listOf("gateway"),
        "りくえすと" to listOf("request"),
        "れすぽんす" to listOf("response"),
        "へっだー" to listOf("header"),
        "ぼでぃ" to listOf("body"),
        "じぇいそん" to listOf("json"),
        "えっくすえむえる" to listOf("xml"),
        "やむる" to listOf("yaml"),
        "ぱけっと" to listOf("packet"),
        "そけっと" to listOf("socket"),
        "うぇぶ" to listOf("web"),
        "えーぴーあい" to listOf("api"),

        // ---- Data ----
        "でーた" to listOf("data"),
        "でーたべーす" to listOf("database"),
        "でーびー" to listOf("db"),
        "きゃっしゅ" to listOf("cache"),
        "きー" to listOf("key"),
        "ばりゅー" to listOf("value"),
        "はっしゅ" to listOf("hash"),
        "べーす" to listOf("base"),
        "せっしょん" to listOf("session"),
        "とーくん" to listOf("token"),

        // ---- Operations ----
        "ろーど" to listOf("load"),
        "せーぶ" to listOf("save"),
        "こぴー" to listOf("copy"),
        "ぺーすと" to listOf("paste"),
        "かっと" to listOf("cut"),
        "でりーと" to listOf("delete"),
        "りむーぶ" to listOf("remove"),
        "くりえいと" to listOf("create"),
        "えでぃっと" to listOf("edit"),
        "びゅー" to listOf("view"),
        "おーぷん" to listOf("open"),
        "くろーず" to listOf("close"),
        "すたーと" to listOf("start"),
        "すとっぷ" to listOf("stop"),
        "らん" to listOf("run"),
        "いぐじっと" to listOf("exit"),
        "ふぁいんど" to listOf("find"),
        "さーち" to listOf("search"),
        "りぷれーす" to listOf("replace"),
        "そーと" to listOf("sort"),
        "ふぃるたー" to listOf("filter"),

        // ---- UI / app ----
        "うぃんどう" to listOf("window"),
        "すくりーん" to listOf("screen"),
        "ぺーじ" to listOf("page"),
        "だいあろぐ" to listOf("dialog"),
        "いめーじ" to listOf("image"),
        "あいこん" to listOf("icon"),
        "あぷり" to listOf("app"),
        "あぷりけーしょん" to listOf("application"),
        "せってぃんぐ" to listOf("setting"),
        "せってぃんぐす" to listOf("settings"),
        "こんふぃぐ" to listOf("config"),
        "めにゅー" to listOf("menu"),
        "ゆーざ" to listOf("user"),
        "ゆーざー" to listOf("user"),
        "ぱすわーど" to listOf("password"),
        "あかうんと" to listOf("account"),
        "ぷろふぁいる" to listOf("profile"),
        "のーと" to listOf("note"),
        "めも" to listOf("memo"),
        "りどみー" to listOf("readme"),
        "どっく" to listOf("doc"),
        "どきゅめんと" to listOf("document"),
        "めっせーじ" to listOf("message"),

        // ---- 言語 ----
        "ぱいそん" to listOf("python"),
        "ぱいそにすた" to listOf("pythonista"),
        "るびー" to listOf("ruby"),
        "じゃば" to listOf("java"),
        "じゃばすくりぷと" to listOf("javascript"),
        "じぇーえす" to listOf("js"),
        "たいぷすくりぷと" to listOf("typescript"),
        "てぃーえす" to listOf("ts"),
        "ことりん" to listOf("kotlin"),
        "ごー" to listOf("go"),
        "ごーらん" to listOf("golang"),
        "らすと" to listOf("rust"),
        "すいふと" to listOf("swift"),
        "ぴーえいちぴー" to listOf("php"),
        "ぱーる" to listOf("perl"),
        "すから" to listOf("scala"),
        "だーと" to listOf("dart"),
        "るあ" to listOf("lua"),
        "はすける" to listOf("haskell"),
        "くろーじゃ" to listOf("clojure"),
        "えりくさ" to listOf("elixir"),
        "しーしゃーぷ" to listOf("csharp"),
        "しーぷらぷら" to listOf("cpp"),

        // ---- ツール / パッケージマネージャ ----
        "のーどじぇーえす" to listOf("nodejs"),
        "えぬぴーえむ" to listOf("npm"),
        "やーん" to listOf("yarn"),
        "ぴーえぬぴーえむ" to listOf("pnpm"),
        "ぴっぷ" to listOf("pip"),
        "じぇむ" to listOf("gem"),
        "かーご" to listOf("cargo"),
        "ぐれーどる" to listOf("gradle"),
        "めいぶん" to listOf("maven"),
        "ばぜる" to listOf("bazel"),
        "めいく" to listOf("make"),
        "しーめいく" to listOf("cmake"),
        "どっかー" to listOf("docker"),
        "くーばねてぃす" to listOf("kubernetes"),
        "けーはちえす" to listOf("k8s"),
        "くーぶ" to listOf("kube"),
        "てらふぉーむ" to listOf("terraform"),
        "あんしぶる" to listOf("ansible"),

        // ---- エディタ ----
        "びむ" to listOf("vim"),
        "にーびむ" to listOf("neovim"),
        "いーまっくす" to listOf("emacs"),
        "ぶいえすこーど" to listOf("vscode"),
        "ないーえすこーど" to listOf("vscode"),

        // ---- OS / ディストロ ----
        "りなっくす" to listOf("linux"),
        "うぶんつ" to listOf("ubuntu"),
        "あるぱいん" to listOf("alpine"),
        "かり" to listOf("kali"),
        "あーち" to listOf("arch"),
        "でびあん" to listOf("debian"),
        "ふぃどら" to listOf("fedora"),
        "うぃんどうず" to listOf("windows"),
        "まっく" to listOf("mac"),
        "あんどろいど" to listOf("android"),
        "あいおーえす" to listOf("ios"),

        // ---- コード/構文 ----
        "ぷりんと" to listOf("print"),
        "りたーん" to listOf("return"),
        "えるす" to listOf("else"),
        "ぶれーく" to listOf("break"),
        "こんてぃにゅー" to listOf("continue"),
        "とらい" to listOf("try"),
        "きゃっち" to listOf("catch"),
        "すろー" to listOf("throw"),
        "ふぁいなり" to listOf("finally"),
        "ねーむすぺーす" to listOf("namespace"),
        "ぱぶりっく" to listOf("public"),
        "ぷらいべーと" to listOf("private"),
        "ぷろてくと" to listOf("protected"),
        "すたてぃっく" to listOf("static"),
        "あぶすとらくと" to listOf("abstract"),
        "いんたーふぇーす" to listOf("interface"),
        "いんへりっと" to listOf("inherit"),
        "おーばーらいど" to listOf("override"),
        "あのてーしょん" to listOf("annotation"),

        // ---- 補足 ----
        "けーす" to listOf("case"),
        "てんぷれーと" to listOf("template"),
        "ぷろぐらむ" to listOf("program"),
        "ぷろじぇくと" to listOf("project"),
        "ぷろぱてぃ" to listOf("property"),
        "こんてな" to listOf("container"),
        "こんすたんと" to listOf("constant"),
        "こんすとらくた" to listOf("constructor"),
        "らんなー" to listOf("runner"),
        "どらいばー" to listOf("driver"),
        "ばっくあっぷ" to listOf("backup"),
        "ばいなり" to listOf("binary"),
        "ばっち" to listOf("batch"),
        "ぶろっく" to listOf("block"),
        "ぽいんた" to listOf("pointer"),
        "べりあぶる" to listOf("variable"),
    )

    /** [LOANWORD_ENTRIES] を辞書行 ("よみ /英語1/英語2/") へ変換し、見出し順にソートして返す。 */
    private fun buildLoanwords(): List<String> {
        // 同じ読みが複数回出る (念のため) 場合は LinkedHashSet で順序を保ちつつ重複排除。
        val map = LinkedHashMap<String, LinkedHashSet<String>>()
        for ((r, words) in LOANWORD_ENTRIES) {
            if (r.isEmpty() || words.isEmpty()) continue
            val s = map.getOrPut(r) { LinkedHashSet() }
            for (w in words) if (w.isNotEmpty()) s.add(w)
        }
        return map.entries
            .sortedBy { it.key }
            .map { (r, ws) -> "$r /" + ws.joinToString("/") + "/" }
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
 * **カーソルと先頭ブロック**:
 *   [cursor] (0..text.length) が挿入カーソルであり、同時に「先頭ブロックの境目」でもある。
 *   `◀ ▶` ([moveCursorLeft] / [moveCursorRight]) でカーソルを動かすと、`text` の先頭
 *   [cursor] 文字 ([splitHead]) が変換対象になり、[candidates] がそれに追従する。かな入力は
 *   カーソル位置へ挿入 ([emitKana]/[append])、⌫ ([backspace]) はカーソル直前を削除する
 *   (= 途中修正できる)。行頭 (cursor=0) まで移動可。確定 ([commit] / [commitRaw]) すると
 *   先頭ブロックを PTY へ送出し、残り ([splitTail]) を composing に据えて末尾カーソルで続行、
 *   残り 0 文字で抜ける。
 *
 * **候補サイクル**:
 *   [convert] (変換キー) を押すと現在の先頭ブロックの候補について [selectedCandidateIndex] が
 *   -1 → 0 → 1 → ... → 末尾 → -1 と循環する。-1 = 生のかな (先頭ブロック) を確定対象とする状態。
 *   ⏎ ([commitRaw]) は「現在選択中の対象」を確定する (生かな or 選択中の候補)。
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

    /**
     * 挿入カーソル (text 内 0..length)。かな/記号はここへ挿入、⌫ はここの直前を削除する。
     * 同時に「先頭ブロックの境目」でもあり、候補は text[0..cursor] を変換対象にする。
     * ◀▶ で 0..length を自由に動かせる (行頭 0 まで到達可)。打鍵/確定/リセットで末尾へ戻る。
     */
    var cursor by mutableStateOf(0)
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
     * 同一スプリット run 中に連続確定したブロックの (読み, 表層)。run が尽きたとき (または
     * 一括確定時) に「結合読み → 結合表層」を [ImeHistoryStore] へ記録し、頻用の塊
     * (びる+ド → ビルド 等) を次回以降 1 ブロックへ繋ぎ止める材料にする ([KkcConverter.learnedBlock])。
     * これが無いと、自動分割で割れた語は常に各ブロック単体でしか学習されず、何度使っても
     * 「びるど」全体が履歴に入らないため分割が解消しなかった。
     */
    private val committedRun = ArrayList<Pair<String, String>>()

    /**
     * 現在の候補のうち「前方一致予測 (打った読みより長い読みを持つ語)」の 表層 → 実際の読み。
     * 確定時に、打った接頭辞ではなく実際の読みで学習させるために参照する。
     * 例: 「お」と打って予測「お願いします」を選ぶと、履歴は「お→お願いします」ではなく
     *     「おねがいします→お願いします」として加算される。[refreshPredict] で都度更新。
     */
    private var predictionReadings: Map<String, String> = emptyMap()

    val isActive: Boolean get() = text.isNotEmpty()
    /** 先頭ブロック = カーソルより前 (= 変換対象)。カーソル 0 では空。 */
    val splitHead: String
        get() = text.substring(0, cursor.coerceIn(0, text.length))
    /** カーソルより後ろの残りかな。 */
    val splitTail: String
        get() = text.substring(cursor.coerceIn(0, text.length))
    /** 後続 (tail) が残る「途中に境目がある」状態か (0 < cursor < length)。一括予測・確定分岐に使う。 */
    val hasTail: Boolean get() = cursor in 1 until text.length
    /** 表示互換: composing 中は常に先頭ピルに caret 付きで全体を出す (旧 isSplitMode の呼び出し用)。 */
    val isSplitMode: Boolean get() = isActive
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
        insertAtCursor(ch)
        lastEmitChar = ch
        lastEmitTimeMs = System.currentTimeMillis()
    }

    /**
     * 任意の 1 文字を composing に積む (記号 ／ プログラム経由)。連打サイクルの履歴は
     * リセットされる (次に同じかなが来ても循環しない)。
     */
    fun append(ch: Char) {
        insertAtCursor(ch)
        lastEmitChar = null
        lastEmitTimeMs = 0
    }

    /** [ch] を [cursor] 位置へ挿入し、カーソルを 1 つ進める。候補・一括予測を更新。 */
    private fun insertAtCursor(ch: Char) {
        val at = cursor.coerceIn(0, text.length)
        text = text.substring(0, at) + ch + text.substring(at)
        cursor = at + 1
        selectedCandidateIndex = -1
        refreshPredict()
    }

    /** カーソル直前の文字を [s] (濁点等) に置換 (濁点循環)。カーソルが行頭なら何もしない。 */
    fun replaceLast(s: Char) {
        if (cursor == 0) return
        text = text.substring(0, cursor - 1) + s + text.substring(cursor)
        lastEmitChar = null
        lastEmitTimeMs = 0
        selectedCandidateIndex = -1
        refreshPredict()
    }

    /**
     * ⌫。カーソル直前の 1 文字を削除する (途中でも末尾でも一様)。カーソルが行頭のときは
     * composing 中なら端末へ DEL を送らず消費だけする。何か消費したら true。
     */
    fun backspace(): Boolean {
        if (cursor == 0) return text.isNotEmpty()  // 行頭: composing 中は消費のみ / 空なら false で端末 DEL
        text = text.substring(0, cursor - 1) + text.substring(cursor)
        cursor -= 1
        lastEmitChar = null
        lastEmitTimeMs = 0
        selectedCandidateIndex = -1
        if (text.isEmpty()) { candidates = emptyList(); fullPrediction = null } else refreshPredict()
        return true
    }

    /**
     * 変換キー。現在の先頭ブロック (text[0..cursor]) の候補を
     * `-1 (生かな) → 0 → 1 → ... → 末尾 → -1` でサイクルする。
     * 先頭ブロックを狭めたい/広げたいときは ◀▶ でカーソルを動かす。
     */
    fun convert() {
        if (text.isEmpty()) return
        val n = candidates.size
        if (n == 0) {
            selectedCandidateIndex = -1
            return
        }
        selectedCandidateIndex =
            if (selectedCandidateIndex >= n - 1) -1 else selectedCandidateIndex + 1
    }

    /** ▶: カーソルを 1 つ右へ (末尾まで)。先頭ブロックが広がり候補が追従する。 */
    fun moveCursorRight() {
        if (cursor < text.length) {
            cursor++
            selectedCandidateIndex = -1
            refreshPredict()
        }
    }

    /** ◀: カーソルを 1 つ左へ (行頭 0 まで)。先頭ブロックが縮み候補が追従する。 */
    fun moveCursorLeft() {
        if (cursor > 0) {
            cursor--
            selectedCandidateIndex = -1
            refreshPredict()
        }
    }

    // 旧名の別名 (キーボード側の ◀▶ 呼び出し互換)。◀▶ = カーソル移動に統一した。
    fun extendSplitHead() = moveCursorRight()
    fun shrinkSplitHead() = moveCursorLeft()

    /** 濁点循環などが対象にする「カーソル直前の文字」。無ければ null。 */
    fun charBeforeCaret(): Char? = if (cursor in 1..text.length) text[cursor - 1] else null

    /**
     * 候補を確定。スプリット中は現在セグメントだけを送り、残りに対して次のセグメントを自動分割。
     * 残り 0 ならスプリットを終了する。学習履歴 ([ImeHistoryStore]) にも記録し、
     * [lastCommittedReading] / [lastCommittedOutput] を更新する (再変換用)。
     */
    fun commit(candidate: String) {
        if (text.isEmpty()) return
        // 変換対象は先頭ブロック (cursor より前)。カーソルが行頭なら全体を対象にする。
        val typedKey = if (cursor > 0) splitHead else text
        // 前方一致予測を選んだ場合は、打った接頭辞でなく実際の読みで学習する
        // (「お」→「お願いします」を「おねがいします→お願いします」として加算)。
        val recordKey = predictionReadings[candidate]?.takeIf { it != typedKey } ?: typedKey
        val isPrefixPrediction = recordKey != typedKey
        ImeHistoryStore.record(recordKey, candidate)
        prevCommitSurface?.let { ImeHistoryStore.recordBigram(it, candidate) }
        prevCommitSurface = candidate
        // 予測は打った読み 1 文節に収まらない (表層が接頭辞より長い) ので結合学習には積まない。
        if (hasTail && !isPrefixPrediction) committedRun.add(typedKey to candidate)
        onCommit(candidate)
        lastCommittedReading = recordKey
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
        cursor = 0
        candidates = emptyList()
        fullPrediction = null
        fullPredictionBlocks = emptyList()
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
        // 先頭ブロック (cursor より前) を生確定。カーソルが行頭なら全体を確定する。
        val toCommit = if (cursor > 0) splitHead else text
        ImeHistoryStore.record(toCommit, toCommit)
        prevCommitSurface?.let { ImeHistoryStore.recordBigram(it, toCommit) }
        prevCommitSurface = toCommit
        if (hasTail) committedRun.add(toCommit to toCommit)
        onCommit(toCommit)
        lastCommittedReading = toCommit
        lastCommittedOutput = toCommit
        advanceSegmentOrReset()
        return true
    }

    fun reset() {
        text = ""
        cursor = 0
        candidates = emptyList()
        fullPrediction = null
        fullPredictionBlocks = emptyList()
        selectedCandidateIndex = -1
        lastEmitChar = null
        lastEmitTimeMs = 0
        committedRun.clear()
        predictionReadings = emptyMap()
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
        cursor = r.length
        selectedCandidateIndex = -1
        lastEmitChar = null
        lastEmitTimeMs = 0
        val cps = o.codePointCount(0, o.length)
        lastCommittedReading = null
        lastCommittedOutput = null
        refreshPredict()
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

    /**
     * 確定後の処理。カーソルより後ろ (splitTail) が残るなら残りを composing に据えて続行、
     * 無ければ全リセット。残りはカーソルを末尾に置く (次のブロックは ◀ で選ぶ)。
     */
    private fun advanceSegmentOrReset() {
        val remaining = if (hasTail) splitTail else ""
        if (remaining.isEmpty()) {
            learnMergedRun()
            text = ""
            cursor = 0
            candidates = emptyList()
            fullPrediction = null
            fullPredictionBlocks = emptyList()
            selectedCandidateIndex = -1
            lastEmitChar = null
            lastEmitTimeMs = 0
        } else {
            text = remaining
            cursor = remaining.length
            selectedCandidateIndex = -1
            refreshPredict()
        }
    }

    private fun refreshPredict() {
        // 変換対象 = 先頭ブロック (cursor より前)。カーソルが行頭なら全体。
        val key = if (cursor > 0) splitHead else text
        if (key.isEmpty()) {
            candidates = emptyList()
            selectedCandidateIndex = -1
            fullPrediction = null
            predictionReadings = emptyMap()
            return
        }
        // 後続 (tail) が残る先頭ブロック (0 < cursor < length) では前方一致予測 (読みより長い補完) を
        // 抑止する。補完が tail と重なると「して下さい」+「下さい」のような被り長文予測になるため、
        // 各ブロックは自分の読みぴったりの変換だけを出す。行頭/末尾 (block=全体) では予測を許す。
        candidates = buildList(
            KanaKanjiConverter.convertFlexible(key, prevSurface = prevCommitSurface, allowPrediction = !hasTail),
            key,
        )
        // 予測候補の実際の読みを控える (確定時に接頭辞でなく読みで学習するため)。予測抑止時は空。
        predictionReadings = if (hasTail) emptyMap() else KanaKanjiConverter.predictionReadingMap(key)
        if (selectedCandidateIndex >= candidates.size) selectedCandidateIndex = -1
        // 長文の一括予測: スプリット中で後続 (tail) が残っているとき、
        //   「先頭ブロックの最尤候補 + 残りかなの最尤」を組み合わせた「文まるごと」候補を出す。
        // 以前は読み全体 (composing.text) の Viterbi 1-best を使っていたため、◀▶ で
        // splitHeadLen を動かしても一括予測ピル (薄緑) が変わらなかった (ユーザー要望)。
        // 先頭は candidates 先頭 (= 履歴/Viterbi/辞書を統合した最尤) を使い、残りは
        // 先頭表層を文脈にして tail を Viterbi で 1-best 変換する。
        fullPrediction = if (hasTail) {
            // 先頭ブロックの表層は「学習履歴 (完全一致) の頻度 1 位」を最優先する。これは平仮名表層
            // (例 して→して) も含む。candidates は buildList が「読みと同一＝平仮名」を除外するため、
            // candidates.first() を使うと学習済みの平仮名が拾えず常に漢字 (仕手 等) になっていた
            // (= 何度平仮名で確定しても長文予測の先頭が漢字のまま)。履歴を直接見て平仮名も土俵に乗せる。
            val headSurface = ImeHistoryStore.historyFor(splitHead, 1).firstOrNull()
                ?: candidates.firstOrNull() ?: splitHead
            // 残りかなも同様に、全体一致の学習履歴 (平仮名含む) があればそれを最優先する。
            // 無ければ従来どおり Viterbi (KANA_PREFERRED 等のコストモデル込み) の 1-best。
            val learnedTail = ImeHistoryStore.historyFor(splitTail, 1).firstOrNull()
            val tailSurface = learnedTail
                ?: KkcConverter.nbest(splitTail, 1, KkcConverter.KkcContext(headSurface))
                    .firstOrNull()?.surface
                ?: KkcConverter.convert(splitTail)
                ?: splitTail
            // 被り除去: 過去に短いブロックへ長い表層が学習されている等で headSurface が
            // 既に tailSurface を末尾に含む場合 (例 headSurface=「して下さい」, tailSurface=「下さい」)、
            // そのまま連結すると「して下さい下さい」と二重になる。重なる分を落として 1 つにする。
            val full = if (tailSurface.isNotEmpty() && headSurface.endsWith(tailSurface)) {
                headSurface
            } else {
                headSurface + tailSurface
            }
            // 一括確定時にブロック単位で学習できるよう内訳を控える (先頭ブロック + tail の各文節)。
            // 文全体を 1 キーで覚えると頻用ブロックの再利用が効かないため。
            // ★内訳は「表示している表層」と一致させる: 表示が平仮名なのに裏で bunsetsu の漢字を
            //   再学習すると、commitFull のたびに漢字 count が増え平仮名が永遠に勝てなくなるため。
            fullPredictionBlocks = buildList {
                add(splitHead to headSurface)
                if (learnedTail != null) add(splitTail to learnedTail)
                else addAll(KkcConverter.bunsetsu(splitTail))
            }
            full.takeIf { it != text }
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
