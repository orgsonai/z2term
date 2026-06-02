package com.zerotoship.z2term.ui.terminal.keyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * IME 学習履歴ストア。確定済み「読み → 単語」のペアを永続化し、予測/変換結果のランキングに
 * 利用する (Gboard 等で「打ち慣れた語が上位に来る」体験を最小実装で再現する)。
 *
 * 保存場所: `filesDir/ime_history.json`
 *
 * 設計方針:
 *  - 完全な形態素解析ではなく辞書(SKK)ベース変換の補助。ユーザーが [ComposingState.commit] /
 *    [ComposingState.commitRaw] で確定したものをそのまま 1 エントリとして覚える。
 *  - 過剰な学習を避けるため、語長 1 文字 (ノイズになりやすい単打ひらがな等) は保存しない。
 *  - 件数上限 [MAX_ENTRIES] を超えたら使用頻度×最近性で下位を切り捨てる (LRU + freq)。
 *  - 書き込みは debounce (1.0 秒) で coalesce する (連打入力時の I/O 抑制)。
 *
 * スコア式: `count * 1.0 + recencyBoost`。recencyBoost は最近 7 日内なら最大 +5、それ以前は 0。
 * これにより「最近使った語」と「沢山使う語」の双方が候補上位に来る。
 *
 * 履歴管理 UI (設定 → 学習履歴) からは [snapshot] / [deleteEntry] / [clearAll] を使う。
 * UI 側は変化通知 [versionFlow] を購読しておけば、別経路で学習が増えた場合も自動で再描画できる。
 */
object ImeHistoryStore {
    private const val TAG = "ImeHistoryStore"
    private const val FILE_NAME = "ime_history.json"
    private const val VERSION = 2           // v2: bigram テーブルを追加 (v1 = unigram のみ、読込互換)
    private const val MAX_ENTRIES = 4000
    private const val MAX_BIGRAMS = 8000
    private const val MIN_WORD_LEN = 2     // 1 文字確定 (単打ひらがな等) は学習しない
    private const val SAVE_DEBOUNCE_MS = 1000L
    private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000  // 直近 7 日
    private const val MAX_RECENCY_BOOST = 5.0

    // bigram (直前確定 → 当語) のコストボーナス。Viterbi コスト(数百〜数千)に対し十分強く効かせる。
    private const val BIGRAM_BASE_BONUS = 1500   // count=1 の基本ボーナス
    private const val BIGRAM_COUNT_STEP = 250    // count が増えるごとの加算 (上限 +750)
    private const val BIGRAM_RECENT_BONUS = 500  // 直近 7 日内の追加 (線形減衰)

    /** UI へ公開する 1 エントリの値オブジェクト (内部 Entry とは別: 不変)。 */
    data class HistoryItem(
        val reading: String,
        val word: String,
        val count: Int,
        val lastUsedAt: Long
    )

    private data class Entry(val word: String, var count: Int, var lastUsedAt: Long)
    private data class BiEntry(val next: String, var count: Int, var lastUsedAt: Long)

    /** reading → 候補単語リスト。同一 reading で複数候補を別 Entry として保持。 */
    private val byReading: HashMap<String, MutableList<Entry>> = HashMap()

    /** 前確定語の表層 → 続いて確定された語の表層リスト (UserHistory bigram 相当)。 */
    private val bigram: HashMap<String, MutableList<BiEntry>> = HashMap()
    private val mutex = Mutex()
    @Volatile private var loaded = false
    private var saveJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var contextRef: Context? = null

    /** 履歴の世代カウンタ。record / delete / clearAll で increment。UI はこれで再フェッチ判断。 */
    private val _versionFlow = MutableStateFlow(0)
    val versionFlow: StateFlow<Int> = _versionFlow.asStateFlow()

    /** 起動時 (TerminalScreen) から呼ぶ。読み込みは IO で 1 度だけ。 */
    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            contextRef = context.applicationContext
            withContext(Dispatchers.IO) {
                runCatching { loadFromDisk(context.applicationContext) }
                    .onFailure { Log.w(TAG, "load failed: ${it.message}") }
            }
            loaded = true
        }
        // N-best リランク段を構成する: 共起 (Phase 4) → ユーザ確定 bigram (Phase 2) の順。
        // 共起で同音語を整え、ユーザの明示的選択 (履歴) を最終的に優先させる。
        KkcConverter.reranker = CompositeReranker(
            listOf(
                CollocationReranker({ KkcConverter.collocationFilter }),
                HistoryReranker(::bigramBonus),
            ),
        )
        _versionFlow.update { it + 1 }
    }

    /**
     * 確定された (読み, 単語) を記録する。1 文字単語はスキップ。同一 reading に同一 word が
     * 既にあれば count++ / 時刻更新、無ければ追加。書き込みは debounce で実 I/O を抑える。
     */
    fun record(reading: String, word: String) {
        if (!loaded) return
        if (reading.isEmpty() || word.isEmpty()) return
        if (word.length < MIN_WORD_LEN) return
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val list = byReading.getOrPut(reading) { mutableListOf() }
                val existing = list.firstOrNull { it.word == word }
                if (existing != null) {
                    existing.count += 1
                    existing.lastUsedAt = now
                } else {
                    list.add(Entry(word, 1, now))
                }
                // 件数上限を超えたら最下位スコアから切り詰める (mutex 内で実行)。
                ensureCapacityLocked()
            }
            _versionFlow.update { it + 1 }
            scheduleSave()
        }
    }

    /**
     * 「直前確定語 [prev] → 当語 [next]」の連接を学習する (UserHistoryPredictor の bigram 相当)。
     * 連続して確定された 2 語のペアを覚え、次回以降の変換リランクで当語を昇格させる。
     * prev/next が空・同一はスキップ。書き込みは debounce save に乗せる。
     */
    fun recordBigram(prev: String, next: String) {
        if (!loaded) return
        if (prev.isEmpty() || next.isEmpty() || prev == next) return
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val list = bigram.getOrPut(prev) { mutableListOf() }
                val existing = list.firstOrNull { it.next == next }
                if (existing != null) {
                    existing.count += 1
                    existing.lastUsedAt = now
                } else {
                    list.add(BiEntry(next, 1, now))
                }
                ensureBigramCapacityLocked()
            }
            scheduleSave()
        }
    }

    /**
     * 「[prev] → [next]」が学習済みなら正のコストボーナスを返す (大きいほど強く昇格)。
     * 未学習なら 0。頻度 (count) と直近性 (recency) でスケールする。リランカーから参照。
     */
    fun bigramBonus(prev: String, next: String): Int {
        if (!loaded || prev.isEmpty() || next.isEmpty()) return 0
        val list = bigram[prev] ?: return 0
        val e = list.firstOrNull { it.next == next } ?: return 0
        val now = System.currentTimeMillis()
        val ageMs = (now - e.lastUsedAt).coerceAtLeast(0)
        val recency = if (ageMs >= RECENT_WINDOW_MS) 0
                      else (BIGRAM_RECENT_BONUS * (1.0 - ageMs.toDouble() / RECENT_WINDOW_MS)).toInt()
        val freq = BIGRAM_BASE_BONUS + BIGRAM_COUNT_STEP * (e.count.coerceAtMost(4) - 1)
        return freq + recency
    }

    /** 完全一致 reading の候補を score 降順で返す (履歴ヒットのみ。辞書とは別経路)。 */
    fun historyFor(reading: String, limit: Int = 4): List<String> {
        if (!loaded || reading.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val list = byReading[reading] ?: return emptyList()
        return list.sortedByDescending { score(it, now) }
            .take(limit)
            .map { it.word }
    }

    /**
     * 前方一致 prefix の reading を持つ履歴を score 降順で返す (予測補強)。
     * 同一 word は重複排除。
     */
    fun predictHistory(prefix: String, limit: Int = 8): List<String> {
        if (!loaded || prefix.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val out = LinkedHashSet<String>()
        // HashMap なので走査になるが、エントリ数は MAX_ENTRIES=4000 と小さくラグなし。
        val flat = ArrayList<Pair<Entry, Long>>()
        for ((r, entries) in byReading) {
            if (!r.startsWith(prefix)) continue
            for (e in entries) flat.add(e to score(e, now).toLong())
        }
        flat.sortByDescending { it.second }
        for ((e, _) in flat) {
            if (out.add(e.word) && out.size >= limit) break
        }
        return out.toList()
    }

    /**
     * UI 用: 学習済み全エントリのスナップショットを score 降順で返す。
     * mutex で排他してから ArrayList へコピーするのでスレッド安全。
     */
    suspend fun snapshot(): List<HistoryItem> = mutex.withLock {
        if (!loaded) return@withLock emptyList()
        val out = ArrayList<HistoryItem>()
        val now = System.currentTimeMillis()
        for ((r, entries) in byReading) {
            for (e in entries) out.add(HistoryItem(r, e.word, e.count, e.lastUsedAt))
        }
        out.sortByDescending {
            val ageMs = (now - it.lastUsedAt).coerceAtLeast(0)
            val recency = if (ageMs >= RECENT_WINDOW_MS) 0.0
                          else MAX_RECENCY_BOOST * (1.0 - ageMs.toDouble() / RECENT_WINDOW_MS)
            it.count.toDouble() + recency
        }
        out
    }

    /** UI 用: 概算件数 (mutex 取らずに best-effort で読む。表示更新用なので OK)。 */
    fun approximateCount(): Int {
        if (!loaded) return 0
        var n = 0
        try {
            for ((_, l) in byReading) n += l.size
        } catch (_: ConcurrentModificationException) {
            // 同時記録中の場合は再試行せず古い値を返す (表示なので不正確で OK)。
        }
        return n
    }

    /** UI 用: 1 件削除 (reading + word の完全一致)。永続化は debounce save に乗せる。 */
    suspend fun deleteEntry(reading: String, word: String) {
        if (!loaded) return
        mutex.withLock {
            val list = byReading[reading] ?: return@withLock
            list.removeAll { it.word == word }
            if (list.isEmpty()) byReading.remove(reading)
        }
        _versionFlow.update { it + 1 }
        scheduleSave()
    }

    /** UI 用: 学習履歴を全削除。ファイルも空で上書きされる (debounce save 経由)。 */
    suspend fun clearAll() {
        if (!loaded) return
        mutex.withLock { byReading.clear(); bigram.clear() }
        _versionFlow.update { it + 1 }
        scheduleSave()
    }

    private fun score(e: Entry, now: Long): Double {
        val ageMs = (now - e.lastUsedAt).coerceAtLeast(0)
        val recency = if (ageMs >= RECENT_WINDOW_MS) 0.0
                      else MAX_RECENCY_BOOST * (1.0 - ageMs.toDouble() / RECENT_WINDOW_MS)
        return e.count.toDouble() + recency
    }

    private fun ensureCapacityLocked() {
        var total = 0
        for ((_, entries) in byReading) total += entries.size
        if (total <= MAX_ENTRIES) return
        // 全エントリを score 昇順にして 10% 切り捨て (毎回ではなく余裕を持って削る)。
        val now = System.currentTimeMillis()
        data class Ref(val reading: String, val entry: Entry, val s: Double)
        val all = ArrayList<Ref>(total)
        for ((r, entries) in byReading) for (e in entries) all.add(Ref(r, e, score(e, now)))
        all.sortBy { it.s }
        val drop = (total - MAX_ENTRIES) + total / 10
        for (i in 0 until drop.coerceAtMost(all.size)) {
            val ref = all[i]
            val list = byReading[ref.reading] ?: continue
            list.remove(ref.entry)
            if (list.isEmpty()) byReading.remove(ref.reading)
        }
    }

    private fun ensureBigramCapacityLocked() {
        var total = 0
        for ((_, l) in bigram) total += l.size
        if (total <= MAX_BIGRAMS) return
        val now = System.currentTimeMillis()
        data class Ref(val prev: String, val entry: BiEntry, val s: Double)
        val all = ArrayList<Ref>(total)
        for ((p, entries) in bigram) for (e in entries) {
            val ageMs = (now - e.lastUsedAt).coerceAtLeast(0)
            val rec = if (ageMs >= RECENT_WINDOW_MS) 0.0
                      else MAX_RECENCY_BOOST * (1.0 - ageMs.toDouble() / RECENT_WINDOW_MS)
            all.add(Ref(p, e, e.count + rec))
        }
        all.sortBy { it.s }
        val drop = (total - MAX_BIGRAMS) + total / 10
        for (i in 0 until drop.coerceAtMost(all.size)) {
            val ref = all[i]
            val list = bigram[ref.prev] ?: continue
            list.remove(ref.entry)
            if (list.isEmpty()) bigram.remove(ref.prev)
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val ctx = contextRef ?: return@launch
            mutex.withLock {
                runCatching { saveToDisk(ctx) }
                    .onFailure { Log.w(TAG, "save failed: ${it.message}") }
            }
        }
    }

    private fun fileOf(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun loadFromDisk(context: Context) {
        val f = fileOf(context)
        if (!f.exists()) return
        val obj = JSONObject(f.readText(Charsets.UTF_8))
        val ver = obj.optInt("version", 0)
        if (ver < 1 || ver > VERSION) return   // v1(unigram のみ)/v2(+bigram) を読込互換
        val arr = obj.optJSONArray("entries") ?: return
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val r = e.optString("r")
            val w = e.optString("w")
            if (r.isEmpty() || w.isEmpty()) continue
            val c = e.optInt("c", 1).coerceAtLeast(1)
            val t = e.optLong("t", 0L)
            val list = byReading.getOrPut(r) { mutableListOf() }
            list.add(Entry(w, c, t))
        }
        val barr = obj.optJSONArray("bigrams") ?: return  // v1 には無い
        for (i in 0 until barr.length()) {
            val e = barr.optJSONObject(i) ?: continue
            val p = e.optString("p")
            val nx = e.optString("n")
            if (p.isEmpty() || nx.isEmpty()) continue
            val c = e.optInt("c", 1).coerceAtLeast(1)
            val t = e.optLong("t", 0L)
            bigram.getOrPut(p) { mutableListOf() }.add(BiEntry(nx, c, t))
        }
    }

    private fun saveToDisk(context: Context) {
        val arr = JSONArray()
        for ((r, entries) in byReading) {
            for (e in entries) {
                val o = JSONObject()
                o.put("r", r)
                o.put("w", e.word)
                o.put("c", e.count)
                o.put("t", e.lastUsedAt)
                arr.put(o)
            }
        }
        val barr = JSONArray()
        for ((p, entries) in bigram) {
            for (e in entries) {
                val o = JSONObject()
                o.put("p", p)
                o.put("n", e.next)
                o.put("c", e.count)
                o.put("t", e.lastUsedAt)
                barr.put(o)
            }
        }
        val obj = JSONObject().apply {
            put("version", VERSION)
            put("entries", arr)
            put("bigrams", barr)
        }
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(obj.toString(), Charsets.UTF_8)
        // 原子的置換 (中断時に壊れた JSON が残らないように)
        if (!tmp.renameTo(fileOf(context))) {
            fileOf(context).writeText(obj.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }
}
