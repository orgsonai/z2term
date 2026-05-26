package com.zerotoship.z2term.ui.terminal.keyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 */
object ImeHistoryStore {
    private const val TAG = "ImeHistoryStore"
    private const val FILE_NAME = "ime_history.json"
    private const val VERSION = 1
    private const val MAX_ENTRIES = 4000
    private const val MIN_WORD_LEN = 2     // 1 文字確定 (単打ひらがな等) は学習しない
    private const val SAVE_DEBOUNCE_MS = 1000L
    private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000  // 直近 7 日
    private const val MAX_RECENCY_BOOST = 5.0

    private data class Entry(val word: String, var count: Int, var lastUsedAt: Long)

    /** reading → 候補単語リスト。同一 reading で複数候補を別 Entry として保持。 */
    private val byReading: HashMap<String, MutableList<Entry>> = HashMap()
    private val mutex = Mutex()
    @Volatile private var loaded = false
    private var saveJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var contextRef: Context? = null

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
            scheduleSave()
        }
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
        if (obj.optInt("version", 0) != VERSION) return
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
        val obj = JSONObject().apply {
            put("version", VERSION)
            put("entries", arr)
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
