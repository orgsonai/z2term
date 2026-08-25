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
 *
 * ## ⚠ 表は「差し替え」で更新する (書き換えない)
 *
 * 記録 ([record] / [recordBigram]) は IO スレッドのコルーチンで走り、参照 ([historyFor] /
 * [predictHistoryWithReading] / [bigramBonus] / [learnedBlock]) は**変換のたびに UI スレッド**から
 * 呼ばれる。可変の `HashMap` を共有すると、記録が新しい読みを put した瞬間に UI スレッド側の
 * 走査が `ConcurrentModificationException` を投げてアプリごと落ちる (実際に落ちた: 変換候補を
 * 押す → 確定で [record] → 続けて残りかなの候補を出すために履歴を走査、の並びで踏む)。
 *
 * そこで表は**不変オブジェクト**として持ち、更新するときは新しい表を組んで参照先を差し替える
 * (copy-on-write)。読み手が触るのは常に「完成した表」なので、走査中に中身が変わることが
 * **構造的に起こらない**。⚠ 例外を握りつぶす形 (try/catch) で塞がないこと — 握りつぶしても
 * 壊れた読み出し (取りこぼし・無限ループ) の可能性は残る。
 *
 * 表を差し替える側 ([record] などの書き手) は [mutex] で直列化する。読み手は錠を取らない。
 */
// 保持するのは applicationContext のみ (Application はプロセス生存期間そのものなので
// シングルトンから参照し続けても leak しない)。lint は参照先が application か判別できず
// 一律に警告するため、その旨を明記して抑制する。Activity/View の Context は保持しないこと。
@Suppress("StaticFieldLeak")
object ImeHistoryStore {
    private const val TAG = "ImeHistoryStore"
    private const val FILE_NAME = "ime_history.json"
    private const val VERSION = 2           // v2: bigram テーブルを追加 (v1 = unigram のみ、読込互換)
    private const val MAX_ENTRIES = 4000
    private const val MAX_BIGRAMS = 8000
    private const val MIN_WORD_LEN = 2     // これ未満は [isLearnableWord] で文字種を見て決める
    private const val SAVE_DEBOUNCE_MS = 1000L
    private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000  // 直近 7 日
    private const val MAX_RECENCY_BOOST = 5.0

    // bigram (直前確定 → 当語) のコストボーナス。Viterbi コスト(数百〜数千)に対し十分強く効かせる。
    private const val BIGRAM_BASE_BONUS = 1500   // count=1 の基本ボーナス
    private const val BIGRAM_COUNT_STEP = 250    // count が増えるごとの加算 (上限 +750)
    private const val BIGRAM_RECENT_BONUS = 500  // 直近 7 日内の追加 (線形減衰)

    // 学習ブロック (動的ブロック分割) のコスト下げ幅。カタカナ化ペナルティ(4000)+接続コストを
    // 1〜2 回の確定で上回れる強さにし、頻用読みを自動で 1 ブロックへまとめる。
    private const val BLOCK_BASE_BONUS = 3000    // count=1 の基本下げ幅
    private const val BLOCK_COUNT_STEP = 1500    // count が増えるごとの加算 (count 上限 4 で +4500)
    private const val BLOCK_RECENT_BONUS = 1000  // 直近 7 日内の追加 (線形減衰)

    // unigram (読み → その表層) のコスト下げ幅 (0.8.398)。**文中のノード 1 つずつ**に効かせる。
    //
    // 値は実測で決めた (`UnigramLearningTest`)。同梱辞書に対して、頻度が上がるほど強い
    // 「壁」を順に越えていく段階になっている:
    //   count=1 (1200)  … 「きょう→今日」(文中で 教 に負けていた) が 1 回の確定で勝つ
    //   count=3 (2800)  … 「はなし→話」(噺 に負けていた) / 「とき→時」が勝つ
    //   count=8 (6800)  … 「もの→物」= **平仮名優先ペナルティ (KANA_PREFERRED_PENALTY=4000)** を越える
    // ⚠ 平仮名で書くのが普通な語 (もの / こと / ある …) の既定を **1 回の確定では壊さない**のが
    // 狙い。何度も自分で選んだ語だけが既定を上書きできる。
    // ⚠ 学習ブロック (BLOCK_* 最大 8500) は越えさせない。塊の繋ぎ止めより語 1 つの頻度が
    // 強くなると、覚えたはずの分割が崩れる。
    private const val UNIGRAM_BASE_BONUS = 1200   // count=1 の基本下げ幅
    private const val UNIGRAM_COUNT_STEP = 800    // count が増えるごとの加算 (上限 count 8 で +5600)
    private const val UNIGRAM_MAX_COUNT = 8       // これ以上使い込んでも下げ幅は増やさない
    private const val UNIGRAM_RECENT_BONUS = 500  // 直近 7 日内の追加 (線形減衰)

    /** UI へ公開する 1 エントリの値オブジェクト (内部 Entry とは別: 不変)。 */
    data class HistoryItem(
        val reading: String,
        val word: String,
        val count: Int,
        val lastUsedAt: Long
    )

    // ⚠ Entry / BiEntry は**不変**。count を書き換えたいときは copy() で新しい値に差し替える
    //   (可変フィールドにすると、表を差し替えても中身だけが別スレッドから書き換わってしまう)。
    private data class Entry(val word: String, val count: Int, val lastUsedAt: Long)
    private data class BiEntry(val next: String, val count: Int, val lastUsedAt: Long)

    /**
     * reading → 候補単語リスト。同一 reading で複数候補を別 Entry として保持。
     * ⚠ 中身を書き換えず、更新のたびに新しい Map へ差し替える (クラス冒頭の注記)。
     */
    @Volatile private var byReading: Map<String, List<Entry>> = emptyMap()

    /** 前確定語の表層 → 続いて確定された語の表層リスト (UserHistory bigram 相当)。同上。 */
    @Volatile private var bigram: Map<String, List<BiEntry>> = emptyMap()

    /** 表を差し替える側 (書き手) の直列化用。読み手は取らない。 */
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
        // 動的ブロック分割: ラティスから学習ブロックを参照させる (頻用読みを 1 ブロックへ)。
        KkcConverter.learnedBlock = ::learnedBlock
        // 頻度優先: ラティスのノード 1 つずつに「その表層を選んだ実績」を効かせる (0.8.398)。
        KkcConverter.unigramBonus = ::unigramBonus
        _versionFlow.update { it + 1 }
    }

    /** 持ち出し用: 学習履歴のファイル (`filesDir/ime_history.json`)。 */
    fun historyFile(context: Context): File = fileOf(context)

    /**
     * 持ち出しから戻した履歴を読み直す。
     *
     * ⚠ **これを呼ばないと、戻した学習は次に起動するまで効かない** — 読み込みは 1 度きりで、
     * [ensureLoaded] は 2 回目から何もしないため (ファイルだけ新しくなって、机の上は古いまま)。
     */
    suspend fun reload(context: Context) {
        val app = context.applicationContext
        mutex.withLock {
            contextRef = app
            withContext(Dispatchers.IO) {
                runCatching { loadFromDisk(app) }
                    .onFailure { Log.w(TAG, "reload failed: ${it.message}") }
            }
            loaded = true
        }
        _versionFlow.update { it + 1 }
    }

    /**
     * この確定を学習するか (0.8.398)。
     *
     * 2 文字以上は無条件。1 文字は**変換した結果だけ**覚える (漢字・カタカナ)。
     * 単打のひらがな・記号・英数字は覚えない — 「か」「の」のような助詞が履歴に溜まると
     * 候補の先頭を埋めるだけで、選び直す手間がかえって増える。
     *
     * ⭐ **1 文字の漢字を覚えないと、よく使う漢字ほど順位が上がらない**。長文を打つほど
     * 効いてくるのは 1 文字の漢字 (時 / 事 / 物 / 方 …) なので、ここを一律に切っていたのが
     * 「何度使っても前に来ない」の主因だった (利用者指摘)。
     */
    internal fun isLearnableWord(word: String): Boolean {
        if (word.isEmpty()) return false
        if (word.length >= MIN_WORD_LEN) return true
        val c = word[0]
        return isKanji(c) || isKatakana(c)
    }

    /** CJK 統合漢字 (拡張 A 含む) と、漢字と同じ扱いで使う繰り返し記号。 */
    private fun isKanji(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c == '々' || c == '〆'

    /** 片仮名 (ヴ・ヵ・ヶ を含む)。長音符は単体で確定しないので入れない。 */
    private fun isKatakana(c: Char): Boolean = c in 'ァ'..'ヶ'

    /**
     * 確定された (読み, 単語) を記録する。学習するかは [isLearnableWord] が決める。
     * 同一 reading に同一 word が既にあれば count++ / 時刻更新、無ければ追加。
     * 書き込みは debounce で実 I/O を抑える。
     */
    fun record(reading: String, word: String) {
        if (!loaded) return
        if (reading.isEmpty() || word.isEmpty()) return
        if (!isLearnableWord(word)) return
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val next = HashMap(byReading)
                val list = ArrayList(next[reading] ?: emptyList())
                val at = list.indexOfFirst { it.word == word }
                if (at >= 0) {
                    list[at] = list[at].copy(count = list[at].count + 1, lastUsedAt = now)
                } else {
                    list.add(Entry(word, 1, now))
                }
                next[reading] = list
                // 件数上限を超えたら最下位スコアから切り詰めてから差し替える。
                byReading = trimEntries(next)
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
                val table = HashMap(bigram)
                val list = ArrayList(table[prev] ?: emptyList())
                val at = list.indexOfFirst { it.next == next }
                if (at >= 0) {
                    list[at] = list[at].copy(count = list[at].count + 1, lastUsedAt = now)
                } else {
                    list.add(BiEntry(next, 1, now))
                }
                table[prev] = list
                bigram = trimBigrams(table)
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

    /**
     * 動的ブロック分割用: 完全一致 reading をユーザーが確定したことがあれば
     * `(最頻表層, コスト下げ幅)` を返す。未学習 / 1 文字 reading は null。
     * [KkcConverter.learnedBlock] から変換のホットパスで参照される。錠は取らないが、
     * 見えるのは差し替え済みの不変な表なので走査中に中身が変わることはない。
     */
    fun learnedBlock(reading: String): Pair<String, Int>? {
        if (!loaded || reading.length < 2) return null
        val list = byReading[reading] ?: return null
        var best: Entry? = null
        for (e in list) if (best == null || e.count > best.count) best = e
        val e = best ?: return null
        if (e.count <= 0) return null
        val now = System.currentTimeMillis()
        val ageMs = (now - e.lastUsedAt).coerceAtLeast(0)
        val recency = if (ageMs >= RECENT_WINDOW_MS) 0
                      else (BLOCK_RECENT_BONUS * (1.0 - ageMs.toDouble() / RECENT_WINDOW_MS)).toInt()
        val freq = BLOCK_BASE_BONUS + BLOCK_COUNT_STEP * (e.count.coerceAtMost(4) - 1)
        return e.word to (freq + recency)
    }

    /**
     * 頻度優先 (0.8.398): 読み [reading] をその表層 [surface] で確定した実績があれば、
     * ラティスのノードコストから引く値を返す。実績が無ければ 0。
     * [KkcConverter.unigramBonus] から変換のホットパスで参照される。
     *
     * ⚠ [learnedBlock] とは効き方が違う。learnedBlock は「読み完全一致の**最頻表層 1 つ**」を
     * 塊として繋ぎ止めるためのもので、**2 文字以上の読みにしか効かない**。こちらは長さも
     * 順位も問わず**どのノードにも**効くので、**長文の途中に出てくる頻用語**が上がる。
     * ⭐ 「長文変換でも何度も使う漢字が前に来ない」(利用者) はここが無かったのが原因。
     * 文まるごとの候補 ([KkcConverter.nbest]) は経路の総コストで並ぶので、語 1 つの頻度は
     * ラティスへ入れない限り 1 ミリも反映されない。
     *
     * 錠は取らないが、見えるのは差し替え済みの不変な表なので走査中に中身は変わらない。
     */
    fun unigramBonus(reading: String, surface: String): Int {
        if (!loaded || reading.isEmpty() || surface.isEmpty()) return 0
        val list = byReading[reading] ?: return 0
        var hit: Entry? = null
        for (e in list) if (e.word == surface) { hit = e; break }
        val e = hit ?: return 0
        if (e.count <= 0) return 0
        val now = System.currentTimeMillis()
        val ageMs = (now - e.lastUsedAt).coerceAtLeast(0)
        val recency = if (ageMs >= RECENT_WINDOW_MS) 0
                      else (UNIGRAM_RECENT_BONUS * (1.0 - ageMs.toDouble() / RECENT_WINDOW_MS)).toInt()
        val freq = UNIGRAM_BASE_BONUS + UNIGRAM_COUNT_STEP * (e.count.coerceAtMost(UNIGRAM_MAX_COUNT) - 1)
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
     * 前方一致 prefix の reading を持つ履歴を score 降順で返す (予測変換)。同一 word は重複排除。
     * 「打った読みで始まる学習済みの語句」を出す本来の予測変換 (例: 「お」→「お願いします」)。
     */
    fun predictHistory(prefix: String, limit: Int = 8): List<String> =
        predictHistoryWithReading(prefix, limit).map { it.second }

    /**
     * [predictHistory] と同じだが、各候補の「実際の読み (履歴上の見出し)」を伴って返す。
     * 確定時に「打った接頭辞」ではなく実際の読みで学習させるために使う
     * ([ComposingState.commit])。同一 word は最上位 score の読みだけを残す。
     */
    fun predictHistoryWithReading(prefix: String, limit: Int = 8): List<Pair<String, String>> {
        if (!loaded || prefix.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        // 全走査になるが、エントリ数は MAX_ENTRIES=4000 と小さくラグなし。
        // 走査するのは差し替え済みの不変な表 (記録側が書き換えることはない)。
        val flat = ArrayList<Triple<String, Entry, Double>>()
        for ((r, entries) in byReading) {
            if (!r.startsWith(prefix)) continue
            for (e in entries) flat.add(Triple(r, e, score(e, now)))
        }
        flat.sortByDescending { it.third }
        val seen = HashSet<String>()
        val out = ArrayList<Pair<String, String>>()
        for ((r, e, _) in flat) {
            if (seen.add(e.word)) out.add(r to e.word)
            if (out.size >= limit) break
        }
        return out
    }

    /**
     * UI 用: 学習済み全エントリのスナップショットを score 降順で返す。
     * 走査するのは不変の表なのでそのまま安全に読める。
     */
    fun snapshot(): List<HistoryItem> {
        if (!loaded) return emptyList()
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
        return out
    }

    /** UI 用: 学習済みエントリの件数。 */
    fun approximateCount(): Int {
        if (!loaded) return 0
        var n = 0
        for ((_, l) in byReading) n += l.size
        return n
    }

    /** UI 用: 1 件削除 (reading + word の完全一致)。永続化は debounce save に乗せる。 */
    suspend fun deleteEntry(reading: String, word: String) {
        if (!loaded) return
        mutex.withLock {
            val list = byReading[reading] ?: return@withLock
            val kept = list.filterNot { it.word == word }
            if (kept.size == list.size) return@withLock
            val next = HashMap(byReading)
            if (kept.isEmpty()) next.remove(reading) else next[reading] = kept
            byReading = next
        }
        _versionFlow.update { it + 1 }
        scheduleSave()
    }

    /** UI 用: 学習履歴を全削除。ファイルも空で上書きされる (debounce save 経由)。 */
    suspend fun clearAll() {
        if (!loaded) return
        mutex.withLock { byReading = emptyMap(); bigram = emptyMap() }
        _versionFlow.update { it + 1 }
        scheduleSave()
    }

    private fun score(e: Entry, now: Long): Double {
        val ageMs = (now - e.lastUsedAt).coerceAtLeast(0)
        val recency = if (ageMs >= RECENT_WINDOW_MS) 0.0
                      else MAX_RECENCY_BOOST * (1.0 - ageMs.toDouble() / RECENT_WINDOW_MS)
        return e.count.toDouble() + recency
    }

    /**
     * 件数上限を超えていたら最下位スコアから切り詰めた**新しい表**を返す (収まっていれば
     * 渡された表をそのまま返す)。⚠ 渡された表は差し替え前の作業用コピーなので中身を書き換えて
     * よいが、公開済みの表を渡さないこと。
     */
    private fun trimEntries(table: Map<String, List<Entry>>): Map<String, List<Entry>> {
        var total = 0
        for ((_, entries) in table) total += entries.size
        if (total <= MAX_ENTRIES) return table
        // 全エントリを score 昇順にして 10% 切り捨て (毎回ではなく余裕を持って削る)。
        val now = System.currentTimeMillis()
        data class Ref(val reading: String, val entry: Entry, val s: Double)
        val all = ArrayList<Ref>(total)
        for ((r, entries) in table) for (e in entries) all.add(Ref(r, e, score(e, now)))
        all.sortBy { it.s }
        val drop = (total - MAX_ENTRIES) + total / 10
        val dropped = HashMap<String, MutableSet<Entry>>()
        for (i in 0 until drop.coerceAtMost(all.size)) {
            val ref = all[i]
            dropped.getOrPut(ref.reading) { HashSet() }.add(ref.entry)
        }
        val out = HashMap<String, List<Entry>>(table.size)
        for ((r, entries) in table) {
            val del = dropped[r]
            val kept = if (del == null) entries else entries.filterNot { it in del }
            if (kept.isNotEmpty()) out[r] = kept
        }
        return out
    }

    /** [trimEntries] の bigram 版。 */
    private fun trimBigrams(table: Map<String, List<BiEntry>>): Map<String, List<BiEntry>> {
        var total = 0
        for ((_, l) in table) total += l.size
        if (total <= MAX_BIGRAMS) return table
        val now = System.currentTimeMillis()
        data class Ref(val prev: String, val entry: BiEntry, val s: Double)
        val all = ArrayList<Ref>(total)
        for ((p, entries) in table) for (e in entries) {
            val ageMs = (now - e.lastUsedAt).coerceAtLeast(0)
            val rec = if (ageMs >= RECENT_WINDOW_MS) 0.0
                      else MAX_RECENCY_BOOST * (1.0 - ageMs.toDouble() / RECENT_WINDOW_MS)
            all.add(Ref(p, e, e.count + rec))
        }
        all.sortBy { it.s }
        val drop = (total - MAX_BIGRAMS) + total / 10
        val dropped = HashMap<String, MutableSet<BiEntry>>()
        for (i in 0 until drop.coerceAtMost(all.size)) {
            val ref = all[i]
            dropped.getOrPut(ref.prev) { HashSet() }.add(ref.entry)
        }
        val out = HashMap<String, List<BiEntry>>(table.size)
        for ((p, entries) in table) {
            val del = dropped[p]
            val kept = if (del == null) entries else entries.filterNot { it in del }
            if (kept.isNotEmpty()) out[p] = kept
        }
        return out
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
        // 組み上がってから 1 度だけ差し替える (途中の表を読み手へ見せない)。
        val loadedEntries = HashMap<String, MutableList<Entry>>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val r = e.optString("r")
            val w = e.optString("w")
            if (r.isEmpty() || w.isEmpty()) continue
            val c = e.optInt("c", 1).coerceAtLeast(1)
            val t = e.optLong("t", 0L)
            loadedEntries.getOrPut(r) { mutableListOf() }.add(Entry(w, c, t))
        }
        byReading = loadedEntries
        val barr = obj.optJSONArray("bigrams") ?: return  // v1 には無い
        val loadedBigrams = HashMap<String, MutableList<BiEntry>>()
        for (i in 0 until barr.length()) {
            val e = barr.optJSONObject(i) ?: continue
            val p = e.optString("p")
            val nx = e.optString("n")
            if (p.isEmpty() || nx.isEmpty()) continue
            val c = e.optInt("c", 1).coerceAtLeast(1)
            val t = e.optLong("t", 0L)
            loadedBigrams.getOrPut(p) { mutableListOf() }.add(BiEntry(nx, c, t))
        }
        bigram = loadedBigrams
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
