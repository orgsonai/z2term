package com.zerotoship.z2term.ui.terminal.keyboard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

/**
 * ユーザー辞書ストア。利用者が持ち込んだ **SKK 形式のテキスト** (`よみ /候補1/候補2/`) を
 * 変換候補に混ぜる。
 *
 * **なぜ要るか**: 同梱辞書 ([KanaKanjiConverter] の `z2dict.txt` + [KkcConverter] の IPADIC) は
 * 一般語しか持たない。人名・社名・プロジェクト名・自分だけの略語は、⚠ **何度打っても学習
 * ([ImeHistoryStore]) が育つまで出てこない**。手元の語をファイルで渡せれば最初から出せる。
 *
 * **置き場**: `filesDir/user_dict/<ファイル名>`。設定画面から選んだファイルを**コピーして持つ**
 * (元ファイルを参照し続けない — SAF の権限はプロセスを跨いで保証されないうえ、元を消されたら
 * 辞書が消える)。⚠ ファイル単位で足す/消すので、どの語がどこから来たのかを利用者が追える。
 *
 * **形式** (SKK 辞書と同じ):
 * ```
 * ;; 行頭の ; はコメント
 * ずーたーむ /Z2Term/z2term/
 * おるぐそん /orgson/
 * ```
 * - ⚠ **読みがひらがなの行だけ**を採る。SKK の送り仮名あり見出し (`おくr /送/` のように
 *   末尾がアルファベット) は活用処理が要るので採らない (中途半端に採ると「おくr」という
 *   読みが候補に出てしまう)。
 * - 候補の `;注釈` は落とす (SKK の注釈記法)。
 * - 文字コードは **UTF-8 / EUC-JP の両対応**。⚠ 配布されている SKK 辞書は EUC-JP が多く、
 *   UTF-8 決め打ちだと丸ごと文字化けした辞書が静かに入る。
 *
 * **候補への効かせ方** (3 経路):
 *  1. 完全一致 → [lookup]。[KanaKanjiConverter.convertFlexible] が学習履歴の次に置く
 *     (自分で登録した語なので同梱辞書・Viterbi より前)。
 *  2. 前方一致 (予測変換) → [predictWithReading]。
 *  3. 文まるごと変換 → [block] を [KkcConverter.userDictBlock] へ配線し、ラティスに
 *     合成ノードとして足す。⚠ これが無いと「単語では出るのに文の中では出ない」辞書になる。
 */
// 保持するのは applicationContext のみ ([ImeHistoryStore] と同じ理由)。
@Suppress("StaticFieldLeak")
object UserDictStore {
    private const val TAG = "UserDictStore"
    private const val DIR_NAME = "user_dict"

    /** 1 ファイルの上限。手元の語を足す用途には十分で、巨大辞書でヒープを潰さない。 */
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024

    /** 全ファイル合計の語数上限。超えるぶんは読み捨てる (端末のメモリを守る)。 */
    private const val MAX_TOTAL_WORDS = 300_000

    /** 読みの最大長。これより長い見出しは壊れた行とみなす。 */
    private const val MAX_READING_LEN = 32

    /**
     * 表形式 (よみ→表記→品詞→注釈) の列区切り。**タブ、または 2 個以上の空白**。
     * ⚠ 1 個のスペースを区切りにしない — SKK 形式 (`よみ /候補/`) と見分けが付かなくなる。
     */
    private val COLUMN_SEP = Regex("""\t+|　+| {2,}""")

    /**
     * 文まるごと変換 ([KkcConverter.nbest]) でユーザー辞書の語に与えるコスト下げ幅。
     * ⚠ カタカナ化ペナルティ (4000) を越えないと、登録した表層が「カタカナのまま」に負ける。
     * 学習ブロック ([ImeHistoryStore.learnedBlock]) が在ればそちらが優先されるので、
     * ここは「一度も確定していない登録語」の初期値にあたる。
     */
    private const val BLOCK_BONUS = 4000

    /** 取り込んだ辞書ファイル 1 つぶん (UI 表示用)。 */
    data class DictFile(val name: String, val words: Int)

    /** 取り込みの結果。UI 側で文言にする (ここでは文字列リソースを持たない)。 */
    sealed interface ImportResult {
        data class Success(val name: String, val words: Int) : ImportResult
        /** ファイルが [MAX_FILE_BYTES] より大きい。 */
        data object TooLarge : ImportResult
        /** 1 語も取れなかった (形式違い / 空)。 */
        data object NoEntries : ImportResult
        data class Failed(val message: String) : ImportResult
    }

    /**
     * 見出しソート済みの表。完全一致は二分探索、前方一致は挿入位置から前進で引く
     * ([KanaKanjiConverter] と同じ作法)。
     *
     * ⚠ **読みと候補は 1 つの物として差し替える**。別々の変数に持つと、読み替え ([reload]) の
     * 最中に「新しい readings で引いた添字を古い candidates に当てる」瞬間ができ、辞書を
     * 入れ替えた直後に添字はみ出しで落ちうる。対で持てばその瞬間自体が無くなる。
     */
    private class Table(val readings: Array<String>, val candidates: Array<List<String>>)

    @Volatile private var table = Table(emptyArray(), emptyArray())

    private val _files = MutableStateFlow<List<DictFile>>(emptyList())
    /** 取り込み済みファイルの一覧 (設定画面が購読する)。 */
    val files: StateFlow<List<DictFile>> = _files.asStateFlow()

    private val _wordCount = MutableStateFlow(0)
    /** 登録語数の合計 (重複読みの候補も 1 語と数える)。 */
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var loaded = false

    /** 起動時 (端末画面 / IME サービス) から呼ぶ。読み込みは IO で 1 度だけ。 */
    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            loadAll(context.applicationContext)
            loaded = true
        }
        KkcConverter.userDictBlock = ::block
    }

    /** ファイルを足した/消した後に読み直す。 */
    suspend fun reload(context: Context) {
        mutex.withLock { loadAll(context.applicationContext) }
        KkcConverter.userDictBlock = ::block
    }

    /**
     * 選んだファイル [uri] を取り込む。中身を読んで**語が取れたときだけ**保存するので、
     * 形式違いのファイルが辞書一覧に並ぶことはない。
     */
    suspend fun import(context: Context, uri: Uri): ImportResult {
        val app = context.applicationContext
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = readLimited(app, uri) ?: return@runCatching ImportResult.TooLarge
                val text = decode(bytes)
                val parsed = parse(text)
                if (parsed.isEmpty()) return@runCatching ImportResult.NoEntries
                val words = parsed.values.sumOf { it.size }
                val file = saveCopy(app, displayName(app, uri), bytes)
                ImportResult.Success(file.name, words)
            }.getOrElse { e ->
                Log.w(TAG, "import failed: ${e.message}")
                ImportResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
        if (result is ImportResult.Success) reload(app)
        return result
    }

    /** 取り込んだ辞書 [name] を消す。 */
    suspend fun remove(context: Context, name: String) {
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            runCatching { File(dir(app), name).delete() }
                .onFailure { Log.w(TAG, "remove failed: ${it.message}") }
        }
        reload(app)
    }

    /** 読みに完全一致する候補 (登録順)。 */
    fun lookup(reading: String): List<String> {
        if (reading.isEmpty()) return emptyList()
        val t = table
        val idx = searchIndex(t, reading)
        return if (idx >= 0) t.candidates[idx] else emptyList()
    }

    /** 読みで前方一致する候補を「実際の読み → 候補」で最大 [limit] 件返す (予測変換)。 */
    fun predictWithReading(prefix: String, limit: Int = 8): List<Pair<String, String>> {
        val t = table
        if (prefix.isEmpty() || t.readings.isEmpty()) return emptyList()
        var i = searchIndex(t, prefix)
        if (i < 0) i = -i - 1
        val out = ArrayList<Pair<String, String>>()
        while (i < t.readings.size && out.size < limit) {
            val r = t.readings[i]
            if (!r.startsWith(prefix)) break
            for (c in t.candidates[i]) {
                out.add(r to c)
                if (out.size >= limit) break
            }
            i++
        }
        return out
    }

    /** [predictWithReading] の候補だけ。 */
    fun predict(prefix: String, limit: Int = 8): List<String> =
        predictWithReading(prefix, limit).map { it.second }

    /**
     * 文まるごと変換用: 読みが登録されていれば `(第 1 候補, コスト下げ幅)`。
     * ⚠ [KkcConverter.nbest] のホットパスから呼ばれるので確保も同期も取らない。
     */
    fun block(reading: String): Pair<String, Int>? {
        if (reading.length < 2) return null
        val t = table
        val idx = searchIndex(t, reading)
        if (idx < 0) return null
        val first = t.candidates[idx].firstOrNull() ?: return null
        return first to BLOCK_BONUS
    }

    // ---- 内部 ----------------------------------------------------------------

    /** 持ち出し用: 辞書ファイルの置き場 (`filesDir/user_dict`)。 */
    fun dictDir(context: Context): File = dir(context)

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun searchIndex(t: Table, reading: String): Int {
        val src = t.readings
        var lo = 0
        var hi = src.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = src[mid].compareTo(reading)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -lo - 1
    }

    /** `user_dict/` の全ファイルを読み、1 本の見出しソート済み表にまとめる。 */
    private suspend fun loadAll(context: Context) = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, MutableList<String>>()
        val fileList = ArrayList<DictFile>()
        var total = 0
        val listed = dir(context).listFiles()?.sortedBy { it.name } ?: emptyList()
        for (f in listed) {
            if (!f.isFile) continue
            val parsed = runCatching { parse(decode(f.readBytes())) }
                .onFailure { Log.w(TAG, "load ${f.name}: ${it.message}") }
                .getOrNull() ?: continue
            var words = 0
            for ((reading, cands) in parsed) {
                if (total >= MAX_TOTAL_WORDS) break
                val list = merged.getOrPut(reading) { ArrayList() }
                for (c in cands) {
                    if (total >= MAX_TOTAL_WORDS) break
                    // 同じ読みに同じ候補が別ファイルから来たら 1 つにまとめる。
                    if (list.contains(c)) continue
                    list.add(c)
                    words++
                    total++
                }
            }
            fileList.add(DictFile(f.name, words))
        }
        val sorted = merged.keys.sorted()
        // 読みと候補を組み上げてから 1 度で差し替える (対で入れ替わるので添字がズレない)。
        table = Table(
            sorted.toTypedArray(),
            Array(sorted.size) { merged[sorted[it]] ?: emptyList() },
        )
        _files.value = fileList
        _wordCount.value = total
    }

    /**
     * 辞書テキストを「読み → 候補」に開く。**2 つの書き方**を受ける (どちらも 1 行 1 語):
     *
     *  - **SKK 形式**: `よみ /候補1/候補2/` (区切りはスペースでもタブでもよい)
     *  - **表形式**: `よみ→表記→品詞→注釈` (タブ / 全角スペース / 2 個以上の半角スペース区切り)。
     *    かな漢字変換の辞書ツールが書き出す形。⚠ **3 列目以降は使わない** — 品詞を活かすには
     *    IPADIC の文脈 ID へ対応付ける必要があり、雑に混ぜると接続コストが壊れる。
     *
     * ⚠ 読みがひらがな以外の行 (送り仮名あり見出し・英字見出し・壊れた行) は捨てる。
     */
    internal fun parse(text: String): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line[0] == ';' || line[0] == '#') return@forEach
            val cols = line.split(COLUMN_SEP).filter { it.isNotEmpty() }
            // 表形式か SKK 形式かは **2 列目が `/` で始まるか**で見分ける。
            // タブ区切りで書かれた SKK 形式 (よみ→/候補/) も取りこぼさない。
            if (cols.size >= 2 && !cols[1].startsWith("/")) {
                val reading = cols[0]
                if (!isPlainKanaReading(reading)) return@forEach
                addCandidate(out, reading, cols[1])
                return@forEach
            }
            // SKK 形式: "よみ /候補1/候補2/"。区切りはスペースでもタブでもよい。
            val sp = line.indexOfFirst { it == ' ' || it == '\t' }
            if (sp <= 0) return@forEach
            val reading = line.substring(0, sp)
            if (!isPlainKanaReading(reading)) return@forEach
            val body = line.substring(sp + 1).trim()
            if (!body.startsWith("/")) return@forEach
            for (part in body.split('/')) {
                if (part.isEmpty()) continue
                // SKK の注釈 (候補;注釈) は表示に混ぜない。
                addCandidate(out, reading, part.substringBefore(';'))
            }
        }
        return out
    }

    private fun addCandidate(
        out: MutableMap<String, MutableList<String>>,
        reading: String,
        rawCandidate: String
    ) {
        val cand = rawCandidate.trim()
        if (cand.isEmpty() || cand == reading) return
        val list = out.getOrPut(reading) { ArrayList() }
        if (!list.contains(cand)) list.add(cand)
    }

    private fun isPlainKanaReading(s: String): Boolean {
        if (s.isEmpty() || s.length > MAX_READING_LEN) return false
        return s.all { it in 'ぁ'..'ゖ' || it == 'ー' || it == 'ゔ' }
    }

    /**
     * UTF-8 として厳密にデコードし、壊れていれば EUC-JP として読み直す。
     * ⚠ 置換文字 (U+FFFD) を許すデコードだと、EUC-JP の辞書が「文字化けした語の山」として
     * 静かに取り込まれる。厳密デコードで弾いてから EUC-JP を試す。
     */
    private fun decode(bytes: ByteArray): String {
        runCatching {
            val dec = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return dec.decode(ByteBuffer.wrap(bytes)).toString()
        }
        val euc = runCatching { Charset.forName("EUC-JP") }.getOrNull()
            ?: return String(bytes, Charsets.UTF_8)
        return runCatching { String(bytes, euc) }.getOrElse { String(bytes, Charsets.UTF_8) }
    }

    /** 上限を超えたら null。読み切る前に打ち切るので、巨大ファイルでもメモリを食わない。 */
    private fun readLimited(context: Context, uri: Uri): ByteArray? {
        context.contentResolver.openInputStream(uri).use { input ->
            input ?: return null
            val buf = ByteArray(64 * 1024)
            val out = java.io.ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) return null
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "dict.txt"
    }

    /** `user_dict/` へコピーする。名前が衝突したら `-2` `-3`… を付けて別物として残す。 */
    private fun saveCopy(context: Context, rawName: String, bytes: ByteArray): File {
        val safe = rawName.replace(Regex("""[/\\:*?"<>|]"""), "_").ifBlank { "dict.txt" }
        val base = safe.substringBeforeLast('.', safe)
        val ext = safe.substringAfterLast('.', "")
        var candidate = File(dir(context), safe)
        var n = 2
        while (candidate.exists()) {
            val name = if (ext.isEmpty()) "$base-$n" else "$base-$n.$ext"
            candidate = File(dir(context), name)
            n++
        }
        candidate.writeBytes(bytes)
        return candidate
    }
}
