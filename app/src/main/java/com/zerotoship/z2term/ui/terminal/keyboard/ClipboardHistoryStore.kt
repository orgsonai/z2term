package com.zerotoship.z2term.ui.terminal.keyboard

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
 * キーボードの貼り付けパッド ([KeyboardPad]) が見せるクリップボード履歴。
 *
 * 保存場所: `filesDir/clipboard_history.json`
 *
 * ⚠ **常時監視はできない。** Android 10 以降、クリップボードを読めるのは
 * **フォアグラウンドのアプリか、現在選ばれている入力メソッド**だけで、裏で見張ることは
 * できない (できたとしてもすべきでない)。そのため取り込みは [capture] を**パッドを開いた
 * ときに 1 回**呼ぶ形にしてある = 利用者から見ると「**コピーしてからキーボードを開くと
 * 履歴に入る**」。この順序は説明が要るので、空のときのパッドにその一文を出している。
 *
 * ⚠ **機微なクリップは残さない。** パスワードマネージャ等が付ける
 * [EXTRA_IS_SENSITIVE] 印のクリップは取り込まない。取り込んだものも [MAX_ENTRIES] 件で
 * 打ち切り、1 件ずつ / まとめて消せる ([remove] / [clearAll])。
 */
// 保持するのは applicationContext のみ ([ImeHistoryStore] と同じ方針)。
@Suppress("StaticFieldLeak")
object ClipboardHistoryStore {
    private const val TAG = "ClipboardHistory"
    private const val FILE_NAME = "clipboard_history.json"
    private const val VERSION = 1
    private const val MAX_ENTRIES = 50
    /** これより長いクリップは覚えない (端末のログを丸ごとコピーした直後などに履歴を潰さない)。 */
    private const val MAX_TEXT_LEN = 20_000
    private const val SAVE_DEBOUNCE_MS = 500L

    /**
     * 「この内容は機微」だとクリップに付く印 (`ClipDescription.EXTRA_IS_SENSITIVE`)。
     * 定数自体は API 33 で公開されたが、値は文字列キーなのでそれ以前の OS でも
     * 付いていれば読める。⚠ 定数を直接参照すると minSdk では解決できないので値で書く。
     */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /** 履歴 1 件。[copiedAt] は取り込んだ時刻 (新しい順に並べるためだけに使う)。 */
    data class Item(val text: String, val copiedAt: Long)

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var loaded = false
    private var saveJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var contextRef: Context? = null

    /** パッドを開いたときに呼ぶ。読み込みは IO で 1 度だけ。 */
    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            contextRef = context.applicationContext
            withContext(Dispatchers.IO) {
                runCatching { _items.value = loadFromDisk(context.applicationContext) }
                    .onFailure { Log.w(TAG, "load failed: ${it.message}") }
            }
            loaded = true
        }
    }

    /**
     * 今クリップボードに入っているテキストを 1 件取り込む (既に先頭と同じなら何もしない)。
     *
     * ⚠ **フォアグラウンド/入力メソッドとして動いている間しか読めない**ので、パッドを開いた
     * ときに呼ぶこと。読めなかった場合 (権限が無い・テキストでない) は静かに諦める。
     */
    fun capture(context: Context) {
        contextRef = context.applicationContext
        val text = runCatching { readClip(context) }
            .onFailure { Log.w(TAG, "read failed: ${it.message}") }
            .getOrNull() ?: return
        add(text)
    }

    /** 履歴へ 1 件積む (同じ内容が既にあれば先頭へ引き上げるだけ)。 */
    fun add(text: String) {
        if (text.isEmpty() || text.length > MAX_TEXT_LEN) return
        val now = System.currentTimeMillis()
        val current = _items.value
        if (current.firstOrNull()?.text == text) return
        _items.value = (listOf(Item(text, now)) + current.filterNot { it.text == text })
            .take(MAX_ENTRIES)
        scheduleSave()
    }

    fun remove(text: String) {
        val next = _items.value.filterNot { it.text == text }
        if (next.size == _items.value.size) return
        _items.value = next
        scheduleSave()
    }

    fun clearAll() {
        if (_items.value.isEmpty()) return
        _items.value = emptyList()
        scheduleSave()
    }

    /** クリップボードの中身をテキストとして読む。機微印が付いていれば null。 */
    private fun readClip(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true) return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val ctx = contextRef ?: return@launch
            mutex.withLock {
                runCatching { saveToDisk(ctx, _items.value) }
                    .onFailure { Log.w(TAG, "save failed: ${it.message}") }
            }
        }
    }

    private fun fileOf(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun loadFromDisk(context: Context): List<Item> {
        val f = fileOf(context)
        if (!f.exists()) return emptyList()
        val obj = JSONObject(f.readText(Charsets.UTF_8))
        if (obj.optInt("version", 0) != VERSION) return emptyList()
        val arr = obj.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val t = e.optString("t")
            if (t.isEmpty()) continue
            out.add(Item(t, e.optLong("at", 0L)))
        }
        return out.take(MAX_ENTRIES)
    }

    private fun saveToDisk(context: Context, items: List<Item>) {
        val arr = JSONArray()
        for (it in items) {
            arr.put(JSONObject().apply {
                put("t", it.text)
                put("at", it.copiedAt)
            })
        }
        val obj = JSONObject().apply {
            put("version", VERSION)
            put("items", arr)
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
