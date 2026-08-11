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
 * ⚠ **機微なクリップは残さない。** パスワードマネージャ等が付ける [EXTRA_IS_SENSITIVE] 印の
 * クリップは取り込まない。
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

    /**
     * 「この内容は機微」だとクリップに付く印 (`ClipDescription.EXTRA_IS_SENSITIVE`)。
     * 定数自体は API 33 で公開されたが、値は文字列キーなのでそれ以前の OS でも
     * 付いていれば読める。⚠ 定数を直接参照すると minSdk では解決できないので値で書く。
     */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /** 履歴 1 件。[text] はコピー本文、[copiedAt] は記録時刻 (epoch ms)。 */
    data class ClipEntry(val text: String, val copiedAt: Long)

    private val _history = MutableStateFlow<List<ClipEntry>>(emptyList())
    val history: StateFlow<List<ClipEntry>> = _history.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
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
        val text = runCatching { readClip(context) }
            .onFailure { Log.w(TAG, "clip read failed: ${it.message}") }
            .getOrNull()
        if (!text.isNullOrEmpty()) record(text)
    }

    /** クリップボードの中身をテキストとして読む。機微印が付いていれば null。 */
    private fun readClip(context: Context): String? {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true) return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0)?.coerceToText(context)?.toString()
    }

    /**
     * 履歴へ 1 件追加する。直前 (先頭) と同一本文なら時刻だけ更新して重複を作らない。
     * 既出 (途中) の本文は削除して先頭へ繰り上げる (LRU)。上限超過分は末尾から切り捨て。
     */
    fun record(text: String) {
        val body = text.take(MAX_TEXT_LEN)
        if (body.isEmpty() || body.isBlank()) return
        val now = System.currentTimeMillis()
        val cur = _history.value
        val rest = cur.filterNot { it.text == body }
        if (rest.size == cur.size && cur.firstOrNull()?.text == body) {
            // 既に先頭が同一: 何もしない (時刻だけ更新するほどの価値はない)。
            return
        }
        _history.value = (listOf(ClipEntry(body, now)) + rest).take(MAX_ENTRIES)
        scheduleSave()
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
                val text = readClip(context)
                if (!text.isNullOrEmpty()) record(text)
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
        for (e in _history.value) {
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
