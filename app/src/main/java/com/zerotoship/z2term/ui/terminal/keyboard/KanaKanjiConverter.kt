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

    /**
     * スプリットモードで「最初の文節」の自動分割長を返す。辞書に完全一致するもっとも長い
     * プレフィックス長 (>=2) を返し、見つからなければ文字列全体の長さを返す。
     */
    fun autoSplitHeadLen(reading: String): Int {
        if (reading.isEmpty()) return 0
        for (end in reading.length downTo 2) {
            if (convert(reading.substring(0, end)).isNotEmpty()) return end
        }
        return reading.length
    }
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

/** 連打サイクルの「同一キー」判定の時間窓 (ms)。これを超えたら別タップ扱い。 */
private const val CYCLE_REPEAT_WINDOW_MS = 1100L

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
 * **連打サイクル**:
 *   [emitKana] で同じかなが [CYCLE_REPEAT_WINDOW_MS] 以内に再度入ると、composing 末尾を
 *   [CYCLE_INDEX] の次の形へ循環させる (例: は→ば→ぱ→は、つ→っ→づ→つ)。`小゛゜` を別途
 *   押す手間を省く。
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
     * かな 1 文字をタップ入力する。
     *   - 直前と同じかな かつ [CYCLE_REPEAT_WINDOW_MS] 以内 かつ末尾が循環グループ内
     *     → composing 末尾を **次の形に循環** ([CYCLE_INDEX] に基づき濁点/半濁点/小書き)。
     *   - それ以外は通常 append。
     * フリック方向の文字 (い段〜お段) は通常 append (循環対象外、ただし同じ文字を連続
     * フリックすると循環は発動し得る)。
     */
    fun emitKana(ch: Char) {
        val now = System.currentTimeMillis()
        val within = (now - lastEmitTimeMs) < CYCLE_REPEAT_WINDOW_MS
        val sameKey = lastEmitChar == ch
        val last = text.lastOrNull()
        val entry = if (within && sameKey && last != null) CYCLE_INDEX[last] else null
        if (entry != null && entry.first.contains(ch)) {
            // 連打サイクル: 末尾を次の形に置換 (lastEmitChar はそのまま保持して続けてサイクル)
            val (forms, idx) = entry
            val nextCh = forms[(idx + 1) % forms.size]
            if (isSplitMode) splitHeadLen = 0
            text = text.dropLast(1) + nextCh
            selectedCandidateIndex = -1
            lastEmitTimeMs = now
            refreshPredict()
            return
        }
        // 通常 append
        if (isSplitMode) splitHeadLen = 0
        text += ch
        lastEmitChar = ch
        lastEmitTimeMs = now
        selectedCandidateIndex = -1
        refreshPredict()
    }

    /**
     * 任意の 1 文字を composing に積む (記号 ／ プログラム経由)。連打サイクルの履歴は
     * リセットされる (次に同じかなが来ても循環しない)。
     */
    fun append(ch: Char) {
        if (isSplitMode) splitHeadLen = 0
        text += ch
        lastEmitChar = null
        lastEmitTimeMs = 0
        selectedCandidateIndex = -1
        refreshPredict()
    }

    /** 直前の文字を [s] (濁点等) に置換。スプリット中なら抜けてから置換する。 */
    fun replaceLast(s: Char) {
        if (text.isEmpty()) return
        if (isSplitMode) splitHeadLen = 0
        text = text.dropLast(1) + s
        lastEmitChar = null
        lastEmitTimeMs = 0
        selectedCandidateIndex = -1
        refreshPredict()
    }

    /**
     * ⌫。スプリット中はまずスプリットを抜ける (取消) だけで文字は消さない。
     * 非スプリット時のみ末尾 1 文字を削除する。消費したら true。
     */
    fun backspace(): Boolean {
        if (text.isEmpty()) return false
        if (isSplitMode) {
            splitHeadLen = 0
            selectedCandidateIndex = -1
            refreshPredict()
            return true
        }
        text = text.dropLast(1)
        lastEmitChar = null
        lastEmitTimeMs = 0
        selectedCandidateIndex = -1
        if (text.isEmpty()) candidates = emptyList() else refreshPredict()
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
        onCommit(candidate)
        lastCommittedReading = key
        lastCommittedOutput = candidate
        advanceSegmentOrReset()
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
        onCommit(toCommit)
        lastCommittedReading = toCommit
        lastCommittedOutput = toCommit
        advanceSegmentOrReset()
        return true
    }

    fun reset() {
        text = ""
        candidates = emptyList()
        splitHeadLen = 0
        selectedCandidateIndex = -1
        lastEmitChar = null
        lastEmitTimeMs = 0
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
        selectedCandidateIndex = -1
        lastEmitChar = null
        lastEmitTimeMs = 0
        val cps = o.codePointCount(0, o.length)
        lastCommittedReading = null
        lastCommittedOutput = null
        refreshPredict()
        return cps
    }

    /** スプリット中: 確定後の処理。残りに対して次の分割を行うか、無ければ全リセット。 */
    private fun advanceSegmentOrReset() {
        if (isSplitMode) {
            val remaining = splitTail
            if (remaining.isEmpty()) {
                text = ""
                candidates = emptyList()
                splitHeadLen = 0
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
            splitHeadLen = 0
            selectedCandidateIndex = -1
            lastEmitChar = null
            lastEmitTimeMs = 0
        }
    }

    private fun refreshPredict() {
        val key = if (isSplitMode) splitHead else text
        if (key.isEmpty()) {
            candidates = emptyList()
            selectedCandidateIndex = -1
            return
        }
        candidates = buildList(KanaKanjiConverter.convertFlexible(key), key)
        if (selectedCandidateIndex >= candidates.size) selectedCandidateIndex = -1
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
}
