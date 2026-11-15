package com.zerotoship.z2term.service

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.zerotoship.z2term.R
import com.zerotoship.z2term.viewer.ViewerPage
import com.zerotoship.z2term.viewer.ViewerStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 読み上げた返事 (`z2-say`) の履歴 (1.0.126)。「直前の読み上げ」の通知を押すと、ビューアでこの一覧を開く。
 *
 * 音量を出していないと返事が聞こえないので、1.0.125 で最新の 1 件を通知に残した。それだけでは、
 * 続けて話しかけたときに前の返事を見返せない。⇒ **新しいものを上にして [LIMIT] 件まで**持つ。
 *
 * ⚠ **アプリの中だけに置く** (`filesDir`)。読み上げには認証コードや AI の答えのような、人に見られたくない
 * 内容も混ざりうる。端末の共有フォルダにも、z2term のバックアップにも入れない (Android の自動バックアップは
 * マニフェストで切ってある)。消すのはビューアの「履歴を消す」([clear])。
 */
internal object SpeechHistory {
    private const val TAG = "SpeechHistory"
    const val LIMIT = 50
    private const val FILE = "speech-history.json"

    /** ビューアのページ名。履歴が変わるたびに書き直す。 */
    const val PAGE = "speech-history"

    private data class Entry(val at: Long, val text: String)

    private fun file(context: Context) = AtomicFile(File(context.filesDir, FILE))

    private fun load(context: Context): List<Entry> = try {
        val array = JSONArray(String(file(context).readFully(), Charsets.UTF_8))
        (0 until array.length()).map { array.getJSONObject(it).let { o -> Entry(o.getLong("t"), o.getString("s")) } }
    } catch (_: java.io.FileNotFoundException) {
        emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "history unreadable; starting over", e); emptyList()
    }

    private fun save(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("t", it.at).put("s", it.text)) }
        val atomic = file(context)
        val stream = atomic.startWrite()
        try { stream.write(array.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }

    /** 1 件足して (新しいものが先頭)、[LIMIT] を超えたぶんを古い方から落とし、ページを書き直す。 */
    @Synchronized fun add(context: Context, text: String) {
        val entries = (listOf(Entry(System.currentTimeMillis(), text)) + load(context)).take(LIMIT)
        save(context, entries)
        render(context, entries)
    }

    /** 履歴とページを両方消す。 */
    @Synchronized fun clear(context: Context) {
        file(context).delete()
        ViewerStore.remove(context, PAGE)
    }

    private fun render(context: Context, entries: List<Entry>) {
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        val body = entries.joinToString("\n\n") { "${format.format(Instant.ofEpochMilli(it.at))}\n${it.text}" }
        ViewerStore.put(context, PAGE,
            ViewerPage(ViewerPage.fromText(body), context.getString(R.string.speech_history_title)))
    }
}
