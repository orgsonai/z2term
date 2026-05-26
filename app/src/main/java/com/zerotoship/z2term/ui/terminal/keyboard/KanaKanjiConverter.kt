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
 * - [segment]         : 文節分割の合成 (複数語フレーズ)
 * - [convertFlexible] : 上記をまとめた「柔軟な変換」(キーボードはこれを使う)
 *
 * 厳密な形態素解析ではなく辞書ベースの best-effort。送り仮名/文節は候補を補助的に増やすだけで、
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
            lines = result
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
     * 文節分割による合成変換。読みを左から「最長一致の見出し」で食べ進め、各文節の第1候補を
     * 連結する (例: きょうのてんき → 今日の天気 のような複数語フレーズ)。最後の文節は送り仮名
     * 活用も試す。辞書ヒットが 2 文節以上のときだけ結果を返す (単語+かなのノイズを避ける)。
     */
    fun segment(reading: String, maxSeg: Int = 12): String? {
        if (reading.length < 2 || lines.isEmpty()) return null
        val sb = StringBuilder()
        var pos = 0
        var seg = 0
        var dictSegs = 0
        while (pos < reading.length && seg < maxSeg) {
            var matched = false
            var end = reading.length
            while (end > pos) {
                val sub = reading.substring(pos, end)
                val cands = convert(sub)
                if (cands.isNotEmpty()) {
                    sb.append(cands[0]); pos = end; dictSegs++; matched = true; break
                }
                end--
            }
            if (!matched) {
                val ok = okuriForms(reading.substring(pos), 1)
                if (ok.isNotEmpty()) { sb.append(ok[0]); pos = reading.length; dictSegs++ }
                else { sb.append(reading[pos]); pos++ }  // 変換不能な1文字はかなのまま
            }
            seg++
        }
        if (pos < reading.length) sb.append(reading.substring(pos))
        val res = sb.toString()
        return if (dictSegs >= 2 && res != reading) res else null
    }

    /**
     * 柔軟な変換候補。優先順:
     *  1. 学習履歴: 完全一致 reading の確定済み単語 ([ImeHistoryStore.historyFor]) ← 最上位
     *  2. 完全一致 ([convert])
     *  3. 送り仮名活用 ([okuriForms]) — 単語+活用/助動詞
     *  4. 文節分割の合成 ([segment]) — 複数語フレーズ
     *  5. 学習履歴: 前方一致 ([ImeHistoryStore.predictHistory]) — 「打ち慣れた語」予測
     *  6. 前方一致の予測 ([predict]) で補完
     */
    fun convertFlexible(reading: String, limit: Int = 16): List<String> {
        if (reading.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        // 1. 学習履歴 (完全一致) は最優先で上位表示。loaded 前は空。
        for (h in ImeHistoryStore.historyFor(reading, limit = 4)) {
            out.add(h)
            if (out.size >= limit) return out.toList()
        }
        if (lines.isEmpty()) {
            // 辞書未ロードでも履歴と前方一致履歴は出す。
            for (h in ImeHistoryStore.predictHistory(reading, limit = limit)) {
                out.add(h); if (out.size >= limit) break
            }
            return out.toList()
        }
        out.addAll(convert(reading))
        out.addAll(okuriForms(reading))
        segment(reading)?.let { out.add(it) }
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
}

/** ひらがな文字列をカタカナへ。 */
fun hiraganaToKatakana(s: String): String =
    buildString(s.length) {
        for (ch in s) append(if (ch in 'ぁ'..'ゖ') ch + 0x60 else ch)
    }

/**
 * 変換の入力状態 (composing) を保持するホルダ。
 *
 * キーボード(かな入力)が [append] で積み、候補バーが [candidates] を表示し、
 * 確定で [onCommit] により PTY へ送出する。Compose state なので
 * 変化すると関係する Composable が再描画される。
 */
class ComposingState(
    private val onCommit: (String) -> Unit
) {
    var text by mutableStateOf("")
        private set
    var candidates by mutableStateOf<List<String>>(emptyList())
        private set

    val isActive: Boolean get() = text.isNotEmpty()

    /** かな 1 文字を積む。予測候補を更新。 */
    fun append(ch: Char) {
        text += ch
        refreshPredict()
    }

    /** 直前の文字を [s] (濁点等) に置換。 */
    fun replaceLast(s: Char) {
        if (text.isEmpty()) return
        text = text.dropLast(1) + s
        refreshPredict()
    }

    /** composing 末尾を 1 文字削除。消費したら true。 */
    fun backspace(): Boolean {
        if (text.isEmpty()) return false
        text = text.dropLast(1)
        if (text.isEmpty()) candidates = emptyList() else refreshPredict()
        return true
    }

    /** 変換キー: 完全一致 + 送り仮名活用 + 文節分割を含む柔軟な候補を出す。 */
    fun convert() {
        if (text.isEmpty()) return
        candidates = buildList(KanaKanjiConverter.convertFlexible(text))
    }

    /** 候補を確定して PTY へ。学習履歴 ([ImeHistoryStore]) にも記録する。 */
    fun commit(candidate: String) {
        ImeHistoryStore.record(text, candidate)
        onCommit(candidate)
        reset()
    }

    /** 生のひらがなを確定。空なら false。生かなも履歴に残し「次は予測上位」化する。 */
    fun commitRaw(): Boolean {
        if (text.isEmpty()) return false
        ImeHistoryStore.record(text, text)
        onCommit(text)
        reset()
        return true
    }

    fun reset() {
        text = ""
        candidates = emptyList()
    }

    private fun refreshPredict() {
        candidates = buildList(KanaKanjiConverter.convertFlexible(text))
    }

    /** 辞書候補にカタカナを加えた表示用リスト (生ひらがなはバー左のラベルで確定する)。 */
    private fun buildList(dict: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        out.addAll(dict)
        val kata = hiraganaToKatakana(text)
        if (kata != text) out.add(kata)
        out.remove(text)
        return out.toList()
    }
}
