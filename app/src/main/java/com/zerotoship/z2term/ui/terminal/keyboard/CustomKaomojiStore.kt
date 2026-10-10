package com.zerotoship.z2term.ui.terminal.keyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 自分で足した顔文字・AA (顔文字・AA パッドの「✎」タブ)。保存場所: `filesDir/kaomoji_custom.json`。
 *
 * 足す・消すのは設定 (キーボード・入力 › 顔文字・AA の追加) から。パッドは並べて打つだけにする
 * (キーの並ぶ狭い場所に編集を持ち込まない)。バックアップにはこのファイルごと入る
 * ([com.zerotoship.z2term.backup.BackupManager])。
 *
 * ⚠ 並びは**足した順 (新しいものが先頭)**。使った順は [RecentKaomojiStore] が別に持つ。
 */
internal object CustomKaomojiStore {
    private const val TAG = "CustomKaomoji"
    const val FILE_NAME = "kaomoji_custom.json"
    const val MAX_ENTRIES = 200
    const val MAX_CHARS = 2000
    const val MAX_LINES = 40

    sealed interface AddResult {
        data object Added : AddResult
        data object Empty : AddResult
        data object Duplicate : AddResult
        data object TooLarge : AddResult
        data object Full : AddResult
    }

    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    @Volatile private var loaded = false
    private val lock = Mutex()

    fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        lock.withLock { if (!loaded) { _items.value = read(context); loaded = true } }
    }

    /** ファイルを読み直す (バックアップから戻した直後用)。 */
    suspend fun reload(context: Context) {
        lock.withLock { _items.value = read(context); loaded = true }
    }

    suspend fun add(context: Context, raw: String): AddResult {
        val text = normalize(raw)
        if (text.isEmpty()) return AddResult.Empty
        if (text.length > MAX_CHARS || text.count { it == '\n' } >= MAX_LINES) return AddResult.TooLarge
        ensureLoaded(context)
        return lock.withLock {
            val cur = _items.value
            when {
                text in cur -> AddResult.Duplicate
                cur.size >= MAX_ENTRIES -> AddResult.Full
                else -> {
                    val next = listOf(text) + cur
                    write(context, next)
                    _items.value = next
                    AddResult.Added
                }
            }
        }
    }

    suspend fun remove(context: Context, text: String) {
        ensureLoaded(context)
        lock.withLock {
            val next = _items.value.filterNot { it == text }
            if (next.size == _items.value.size) return@withLock
            write(context, next)
            _items.value = next
        }
    }

    /**
     * 入力欄の文字を、並べて打てる形に整える。
     *
     * - 改行は `\n` にそろえる (貼り付けた文字は `\r\n` のことがある)。
     * - 行末の空白と、前後の空行は落とす。**行頭の空白は絵の一部なので残す。**
     * - タブは端末の桁位置で幅が変わり絵が崩れるので、空白 4 つに置き換える。
     */
    fun normalize(raw: String): String {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
            .split('\n').map { it.trimEnd() }
        val first = lines.indexOfFirst { it.isNotEmpty() }
        if (first < 0) return ""
        val last = lines.indexOfLast { it.isNotEmpty() }
        return lines.subList(first, last + 1).joinToString("\n")
    }

    private suspend fun read(context: Context): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(context)
            if (!f.exists()) return@runCatching emptyList()
            val arr = JSONObject(f.readText(Charsets.UTF_8)).optJSONArray("items") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
                .distinct().take(MAX_ENTRIES)
        }.onFailure { Log.w(TAG, "load failed: ${it.message}") }.getOrDefault(emptyList())
    }

    private suspend fun write(context: Context, items: List<String>) = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray()
            items.forEach { arr.put(it) }
            val json = JSONObject().put("items", arr).toString()
            val dst = file(context)
            val tmp = File(dst.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json, Charsets.UTF_8)
            if (!tmp.renameTo(dst)) { dst.writeText(json, Charsets.UTF_8); tmp.delete() }
        }.onFailure { Log.w(TAG, "save failed: ${it.message}") }
    }
}
