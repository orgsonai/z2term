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
 * - [convert]  : よみに完全一致する候補 (= 変換)
 * - [predict]  : よみで前方一致する見出しの候補を集める (= 予測変換)
 *
 * 文節分割や送り仮名活用は行わない。単語・連語単位の変換のみ。
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
}

/** ひらがな文字列をカタカナへ。 */
fun hiraganaToKatakana(s: String): String =
    buildString(s.length) {
        for (ch in s) append(if (ch in 'ぁ'..'ゖ') ch + 0x60 else ch)
    }

/**
 * 変換の入力状態 (composing) を保持するホルダ。
 *
 * キーボード(かな入力)が [append] で積み、候補バーが [candidates] を表示し、
 * 確定で [onCommit] により PTY へ送出する。Compose state なので
 * 変化すると関係する Composable が再描画される。
 */
class ComposingState(
    private val onCommit: (String) -> Unit
) {
    var text by mutableStateOf("")
        private set
    var candidates by mutableStateOf<List<String>>(emptyList())
        private set

    val isActive: Boolean get() = text.isNotEmpty()

    /** かな 1 文字を積む。予測候補を更新。 */
    fun append(ch: Char) {
        text += ch
        refreshPredict()
    }

    /** 直前の文字を [s] (濁点等) に置換。 */
    fun replaceLast(s: Char) {
        if (text.isEmpty()) return
        text = text.dropLast(1) + s
        refreshPredict()
    }

    /** composing 末尾を 1 文字削除。消費したら true。 */
    fun backspace(): Boolean {
        if (text.isEmpty()) return false
        text = text.dropLast(1)
        if (text.isEmpty()) candidates = emptyList() else refreshPredict()
        return true
    }

    /** 変換キー: 完全一致の候補を前に出す。 */
    fun convert() {
        if (text.isEmpty()) return
        candidates = buildList(KanaKanjiConverter.convert(text))
    }

    /** 候補を確定して PTY へ。 */
    fun commit(candidate: String) {
        onCommit(candidate)
        reset()
    }

    /** 生のひらがなを確定。空なら false。 */
    fun commitRaw(): Boolean {
        if (text.isEmpty()) return false
        onCommit(text)
        reset()
        return true
    }

    fun reset() {
        text = ""
        candidates = emptyList()
    }

    private fun refreshPredict() {
        candidates = buildList(KanaKanjiConverter.predict(text))
    }

    /** 辞書候補にカタカナを加えた表示用リスト (生ひらがなはバー左のラベルで確定する)。 */
    private fun buildList(dict: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        out.addAll(dict)
        val kata = hiraganaToKatakana(text)
        if (kata != text) out.add(kata)
        out.remove(text)
        return out.toList()
    }
}
