package com.zerotoship.z2term.clipboard

import android.content.ClipboardManager
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * クリップボード履歴ストア。OS のシステムクリップボードは「現在の 1 件」しか保持しないため、
 * 変化を監視して履歴として貯める ([record])。端末からのコピー / 他アプリでのコピーを取り込み、
 * 貼り付けボタンのダブルタップで開く履歴シートと、キーボードの 📋 パッドから過去のコピーを
 * 選べるようにする。
 *
 * ⚠ **履歴の実体はこの object ただ 1 つ**。以前はキーボードのパッド用に同名の別 object が
 * あり、**同じ `filesDir/clipboard_history.json` を別のキー (`entries` / `items`) で丸ごと
 * 上書きし合っていた** — 片方が保存すると、もう片方からは「中身の無いファイル」に見えるため、
 * パッドの履歴は起動のたびに空から始まっていた (0.8.313 で 1 本化)。**入口を増やすときも
 * ストアは増やさない**こと。
 *
 * 同期方針 (Android のクリップボード制約に合わせた現実解):
 *  - [registerSystemSync] で `OnPrimaryClipChangedListener` を張り、アプリ前面中のシステム
 *    クリップボード変化 (= 端末コピー含む) をその場で取り込む。
 *  - 前面復帰時に [captureCurrent] を呼ぶ事で、裏で他アプリがコピーした内容も拾う
 *    (Android 10+ はフォーカス中のみクリップボード読取が許可されるため)。
 *  - 端末コピー経路からは [record] を直接呼んでも良い (重複は先頭一致で弾く)。
 *
 * ## 機微なクリップ (0.8.314)
 *
 * パスワードマネージャ等が付ける [EXTRA_IS_SENSITIVE] 印のクリップは、**以前は丸ごと捨てて
 * いた** — その結果、いちばん貼り付けたいもの (パスワード) だけが 📋 履歴に出ず、
 * 「コピーしたのに入っていない」状態になっていた (利用者の指摘)。
 *
 * 取り込むように改めたうえで、残り続けないように次の 3 つで縛る:
 *
 *  - **[SENSITIVE_TTL_MS] (30 秒) で履歴から自動的に消える。** 貼るには足りて、放置はされない。
 *  - **同じ値が OS のクリップボードにまだ載っていれば、そこからも消す。** 履歴だけ消しても
 *    他アプリから貼れてしまうため。⚠ **値が変わっていたら触らない** — 別のアプリが後から
 *    コピーしたものを奪うことになる (同梱サンプル `otp-clip.sh` と同じ作法)。
 *  - **ディスクに書かない** ([saveToDisk] が落とす)。30 秒で消えるものを永続化しても、
 *    アプリを殺した瞬間だけ残るという最悪の形になる。
 *
 * 保存場所: `filesDir/clipboard_history.json`
 */
// 保持するのは applicationContext のみ (Application はプロセス生存期間そのものなので
// シングルトンから参照し続けても leak しない)。lint は参照先が application か判別できず
// 一律に警告するため、その旨を明記して抑制する。Activity/View の Context は保持しないこと。
@Suppress("StaticFieldLeak")
object ClipboardHistoryStore {
    private const val TAG = "ClipHistory"
    private const val FILE_NAME = "clipboard_history.json"
    private const val VERSION = 1
    private const val MAX_ENTRIES = 50
    private const val MAX_TEXT_LEN = 20_000   // 巨大貼り付けの暴走を防ぐ上限
    private const val SAVE_DEBOUNCE_MS = 800L

    /** 機微なクリップを履歴に置いておく時間。貼るには足りて、放置はされない長さ。 */
    private const val SENSITIVE_TTL_MS = 30_000L

    /**
     * 「この内容は機微」だとクリップに付く印 (`ClipDescription.EXTRA_IS_SENSITIVE`)。
     * 定数自体は API 33 で公開されたが、値は文字列キーなのでそれ以前の OS でも
     * 付いていれば読める。⚠ 定数を直接参照すると minSdk では解決できないので値で書く。
     */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /**
     * 履歴 1 件。[text] はコピー本文、[copiedAt] は記録時刻 (epoch ms)。
     *
     * [sensitive] は「機微」印が付いていたクリップ (パスワード等)。この印が付いた行だけは
     * [SENSITIVE_TTL_MS] で消え、ディスクにも書かれない。
     */
    data class ClipEntry(val text: String, val copiedAt: Long, val sensitive: Boolean = false)

    private val _history = MutableStateFlow<List<ClipEntry>>(emptyList())
    val history: StateFlow<List<ClipEntry>> = _history.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private var purgeJob: Job? = null
    private val loadMutex = Mutex()
    @Volatile private var loaded = false
    private var contextRef: Context? = null
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    /** ディスク読込 + システムクリップボード監視の開始。Application から 1 度だけ呼ぶ。 */
    fun init(context: Context) {
        val app = context.applicationContext
        contextRef = app
        scope.launch { ensureLoaded(app) }
        registerSystemSync(app)
    }

    /**
     * ディスクからの読込を 1 度だけ行う。[init] が済んでいれば何もしないので、履歴を見せる
     * 入口 (シート / キーボードのパッド) を開く直前に呼んで読込完了を待ってよい。
     */
    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        val app = context.applicationContext
        contextRef = app
        loadMutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                runCatching { loadFromDisk(app) }
                    .onFailure { Log.w(TAG, "load failed: ${it.message}") }
            }
            loaded = true
        }
    }

    /** 現在のシステムクリップボードを 1 件取り込む (前面復帰時・パッドを開いた時などに呼ぶ)。 */
    fun captureCurrent(context: Context) {
        contextRef = context.applicationContext
        val clip = runCatching { readClip(context) }
            .onFailure { Log.w(TAG, "clip read failed: ${it.message}") }
            .getOrNull()
        if (clip != null && clip.text.isNotEmpty()) record(clip.text, clip.sensitive)
    }

    /** 読み取ったクリップ 1 件。[sensitive] は「機微」印が付いていたか。 */
    private data class Clip(val text: String, val sensitive: Boolean)

    /** クリップボードの中身をテキストとして読む。空 / 読めないときは null。 */
    private fun readClip(context: Context): Clip? {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0)?.coerceToText(context)?.toString() ?: return null
        val sensitive = clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true
        return Clip(text, sensitive)
    }

    /**
     * 履歴へ 1 件追加する。直前 (先頭) と同一本文なら時刻だけ更新して重複を作らない。
     * 既出 (途中) の本文は削除して先頭へ繰り上げる (LRU)。上限超過分は末尾から切り捨て。
     *
     * @param sensitive 「機微」印が付いていたクリップなら true。30 秒で自動的に消える
     *   ([SENSITIVE_TTL_MS]) 一時的な行として積む。
     */
    fun record(text: String, sensitive: Boolean = false) {
        val body = text.take(MAX_TEXT_LEN)
        if (body.isEmpty() || body.isBlank()) return
        val now = System.currentTimeMillis()
        val cur = _history.value
        val rest = cur.filterNot { it.text == body }
        val head = cur.firstOrNull()
        if (rest.size == cur.size && head?.text == body && head.sensitive == sensitive) {
            // 既に先頭が同一: 何もしない (時刻だけ更新するほどの価値はない)。
            // ⚠ 機微かどうかが変わったときだけは積み直す (消える行かどうかが変わるため)。
            return
        }
        _history.value = (listOf(ClipEntry(body, now, sensitive)) + rest).take(MAX_ENTRIES)
        if (sensitive) schedulePurge()
        scheduleSave()
    }

    /**
     * 機微な行の期限切れを見張る 1 本のジョブ。次に切れる行まで待って消し、
     * まだ機微な行が残っていれば繰り返す。残っていなければ止まる (常駐しない)。
     */
    private fun schedulePurge() {
        if (purgeJob?.isActive == true) return
        purgeJob = scope.launch {
            while (true) {
                val next = _history.value
                    .filter { it.sensitive }
                    .minOfOrNull { it.copiedAt + SENSITIVE_TTL_MS } ?: return@launch
                val wait = next - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                purgeExpired()
            }
        }
    }

    /** 期限の切れた機微な行を履歴から外し、OS のクリップボードにも残っていれば消す。 */
    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        val expired = _history.value.filter { it.sensitive && now - it.copiedAt >= SENSITIVE_TTL_MS }
        if (expired.isEmpty()) return
        val gone = expired.toSet()
        _history.value = _history.value.filterNot { it in gone }
        contextRef?.let { ctx -> expired.forEach { clearSystemClipIfUnchanged(ctx, it.text) } }
        scheduleSave()
    }

    /**
     * OS のクリップボードが**まだ同じ値**なら空にする。
     *
     * ⚠ 値が変わっていたら何もしない — 後から別のアプリがコピーしたものを奪ってしまうため。
     * Android 10+ は前面 (フォーカスあり) でないとクリップボードを読めないので、読めなかった
     * ときも「変わっているかもしれない」側に倒して触らない。
     */
    private fun clearSystemClipIfUnchanged(context: Context, text: String) {
        runCatching {
            val cm = context.getSystemService(ClipboardManager::class.java) ?: return
            if (readClip(context)?.text != text) return
            cm.clearPrimaryClip()
        }.onFailure { Log.w(TAG, "clip clear failed: ${it.message}") }
    }

    /** 1 件削除。 */
    fun delete(entry: ClipEntry) {
        _history.value = _history.value.filterNot { it.text == entry.text && it.copiedAt == entry.copiedAt }
        scheduleSave()
    }

    /** 全削除。 */
    fun clearAll() {
        _history.value = emptyList()
        scheduleSave()
    }

    private fun registerSystemSync(context: Context) {
        if (listener != null) return
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            runCatching {
                val clip = readClip(context)
                if (clip != null && clip.text.isNotEmpty()) record(clip.text, clip.sensitive)
            }.onFailure { Log.w(TAG, "clip read failed: ${it.message}") }
        }
        runCatching { cm.addPrimaryClipChangedListener(l); listener = l }
            .onFailure { Log.w(TAG, "addPrimaryClipChangedListener failed: ${it.message}") }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val ctx = contextRef ?: return@launch
            runCatching { saveToDisk(ctx) }
                .onFailure { Log.w(TAG, "save failed: ${it.message}") }
        }
    }

    private fun fileOf(context: Context) = File(context.filesDir, FILE_NAME)

    private fun loadFromDisk(context: Context) {
        val f = fileOf(context)
        if (!f.exists()) return
        val obj = JSONObject(f.readText(Charsets.UTF_8))
        if (obj.optInt("version", 0) != VERSION) return
        // `items` は 0.8.312 以前のキーボード側ストアが書いていたキー。同じファイルを別キーで
        // 奪い合っていたので、1 本化 (0.8.313) の際に残っていた分もここで拾って引き継ぐ。
        val arr = obj.optJSONArray("entries") ?: obj.optJSONArray("items") ?: return
        val out = ArrayList<ClipEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val t = e.optString("t")
            if (t.isEmpty()) continue
            out.add(ClipEntry(t, e.optLong("at", 0L)))
        }
        _history.value = out.take(MAX_ENTRIES)
    }

    private fun saveToDisk(context: Context) {
        val arr = JSONArray()
        // ⚠ 機微な行は書かない。30 秒で消えるものを永続化すると、アプリを殺した瞬間だけ
        // ディスクに残るという最悪の形になる。
        for (e in _history.value.filterNot { it.sensitive }) {
            arr.put(JSONObject().apply {
                put("t", e.text)
                put("at", e.copiedAt)
            })
        }
        val obj = JSONObject().apply {
            put("version", VERSION)
            put("entries", arr)
        }
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(obj.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(fileOf(context))) {
            fileOf(context).writeText(obj.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }
}
