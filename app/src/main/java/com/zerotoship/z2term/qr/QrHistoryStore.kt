package com.zerotoship.z2term.qr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.qrHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "z2term_qr_history")

/**
 * QR ツールの履歴の保存先 (0.8.603)。[com.zerotoship.z2term.snippets.SnippetStore] と同じく
 * JSON 配列を 1 つの String キーに置く。端末の中だけに残る (`allowBackup="false"`)。
 */
internal class QrHistoryStore(private val context: Context) {
    val entries: Flow<List<QrHistoryEntry>> = context.qrHistoryDataStore.data.map { decode(it[KEY]) }

    suspend fun add(text: String) = update { QrHistory.add(it, text, System.currentTimeMillis()) }

    suspend fun setPinned(text: String, pinned: Boolean) = update { QrHistory.setPinned(it, text, pinned) }

    suspend fun remove(text: String) = update { QrHistory.remove(it, text) }

    suspend fun clearUnpinned() = update { QrHistory.clearUnpinned(it) }

    private suspend fun update(change: (List<QrHistoryEntry>) -> List<QrHistoryEntry>) {
        context.qrHistoryDataStore.edit { p -> p[KEY] = encode(change(decode(p[KEY]))) }
    }

    private companion object {
        val KEY = stringPreferencesKey("entries")

        fun decode(raw: String?): List<QrHistoryEntry> {
            if (raw == null) return emptyList()
            val array = try { JSONArray(raw) } catch (e: Exception) { return emptyList() }
            return (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                val text = o.optString("text")
                if (text.isEmpty()) null else QrHistoryEntry(text, o.optLong("time"), o.optBoolean("pinned"))
            }.distinctBy { it.text }
        }

        fun encode(entries: List<QrHistoryEntry>): String = JSONArray().apply {
            entries.forEach { put(JSONObject().put("text", it.text).put("time", it.time).put("pinned", it.pinned)) }
        }.toString()
    }
}
