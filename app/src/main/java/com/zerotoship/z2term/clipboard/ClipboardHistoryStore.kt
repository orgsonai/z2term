package com.zerotoship.z2term.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
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
import java.security.MessageDigest
import java.util.UUID

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
 * ## 画像・ファイルのクリップ (1.0.122)
 *
 * 画像やファイルのコピーは、クリップボードに**中身ではなく `content://` の場所**が載る。
 * 以前は文字として読んでいたので、履歴には `content://…` という文字列が並ぶだけで、
 * 貼っても画像にもファイルにもならなかった (利用者の指摘)。
 *
 *  - **見つけたその場で中身を写し取る** (`filesDir/clipboard-files/<id>/…`)。⚠ 場所だけ
 *    覚えても後から開けない — 読む許可は「いまクリップボードに載っている間」しか無い。
 *  - 写し取りは裏のスレッドで行う。同じクリップを二度写さないよう、クリップの時刻と
 *    場所を [lastFileStamp] に覚えておく (前面に戻るたびに取り込みが呼ばれるため)。
 *  - 中身の SHA-256 を行の同一性 ([ClipEntry.text]) に使う。同じ名前で別の画像を
 *    載せるアプリがあるので、名前や場所では見分けない。
 *  - 行が消えたら写しも消す ([sweepFiles])。機微な行は文字と同じく 30 秒で消える。
 *
 * ## ピン留め (1.0.122)
 *
 * 住所やメールアドレスのように何度も貼るものは、行を長押しして**ピン留め**できる
 * ([togglePin])。ピン留めした行は**いつも上に並び**、50 件の上限でも「クリア」でも消えない。
 * ⚠ 並びは「ピン留め → それ以外 (新しい順)」で、この順を [_history] 自体が保つ。
 * 見せる側で並べ替えない (履歴シートとパッドで順が食い違う)。
 * ⚠ 機微な行 (30 秒で消える) はピン留めできない — 残さないための仕組みと両立しない。
 *
 * 保存場所: `filesDir/clipboard_history.json` (画像・ファイルの写しは `filesDir/clipboard-files/`)
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

    /** 画像・ファイルの写しの置き場 (`filesDir` からの相対)。FileProvider の公開範囲と揃える。 */
    private const val FILES_DIR = "clipboard-files"
    /** 写している途中のフォルダの頭に付ける印。履歴に載るまでは掃除の対象にしない。 */
    private const val TMP_PREFIX = ".tmp-"
    /** 画像・ファイルの行の [ClipEntry.text] の頭。後ろに中身の SHA-256 が続く。 */
    private const val FILE_KEY_PREFIX = "clip-files:"
    private const val MAX_FILES_PER_CLIP = 16
    /** 1 回のコピーで写し取る上限。これを超えるコピーは履歴に入れない。 */
    private const val MAX_CLIP_FILE_BYTES = 64L * 1024 * 1024
    /** 履歴全体で持つ写しの上限。超えたら古い画像・ファイルの行から落とす。 */
    private const val MAX_TOTAL_FILE_BYTES = 256L * 1024 * 1024

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
    data class ClipEntry(
        val text: String,
        val copiedAt: Long,
        val sensitive: Boolean = false,
        /**
         * 画像・ファイルのコピーなら、その写し。空なら文字のコピー。
         * ⚠ 空でない行の [text] は本文ではなく同一性の印 (中身の SHA-256)。**画面に出さない**。
         */
        val files: List<ClipFile> = emptyList(),
        /** ピン留めした行。いつも上に並び、上限でも「クリア」でも消えない。 */
        val pinned: Boolean = false,
    )

    /**
     * 写し取った画像・ファイル 1 つ。[path] は置き場 (`filesDir/clipboard-files/`) からの相対。
     */
    data class ClipFile(val name: String, val mime: String, val path: String, val size: Long) {
        val isImage: Boolean get() = mime.startsWith("image/")
    }

    private val _history = MutableStateFlow<List<ClipEntry>>(emptyList())
    val history: StateFlow<List<ClipEntry>> = _history.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private var purgeJob: Job? = null
    private val loadMutex = Mutex()
    @Volatile private var loaded = false
    private var contextRef: Context? = null
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    /** 履歴の書き換えと「写しのフォルダを履歴に載せる」を 1 つずつ通すための鍵。 */
    private val lock = Any()
    private val fileMutex = Mutex()
    /** 最後に写し取りを始めたクリップ (時刻 + 場所)。同じクリップを二度写さないため。 */
    @Volatile private var lastFileStamp: String? = null
    /** [lastFileStamp] のクリップが入った行の [ClipEntry.text]。写し終わるまでは null。 */
    @Volatile private var lastFileKey: String? = null

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
        // 保存していない行 (機微なもの) や、途中で落ちたときの写しがここで片付く。
        withContext(Dispatchers.IO) { runCatching { sweepFiles(app, includeUnfinished = true) } }
    }

    /** 現在のシステムクリップボードを 1 件取り込む (前面復帰時・パッドを開いた時などに呼ぶ)。 */
    fun captureCurrent(context: Context) {
        contextRef = context.applicationContext
        runCatching { capture(context) }
            .onFailure { Log.w(TAG, "clip read failed: ${it.message}") }
    }

    /** いまのクリップを履歴へ。画像・ファイルなら中身を写し取り、文字ならそのまま積む。 */
    private fun capture(context: Context) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val primary = cm.primaryClip ?: return
        val uris = fileUris(primary)
        if (uris.isNotEmpty()) {
            captureFiles(context.applicationContext, primary, uris)
            return
        }
        val clip = readClip(context) ?: return
        if (clip.text.isNotEmpty()) record(clip.text, clip.sensitive)
    }

    /**
     * クリップに載っている画像・ファイルの場所。文字のコピーなら空。
     *
     * ⚠ 文字も一緒に載っている項目は文字として扱う (これまでどおり)。`http://` などの
     * `content://` 以外の場所も文字として扱う — 開いて写せるのは `content://` だけ。
     */
    private fun fileUris(clip: ClipData): List<Uri> =
        (0 until clip.itemCount).mapNotNull { index ->
            val item = clip.getItemAt(index) ?: return@mapNotNull null
            item.uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT && item.text.isNullOrEmpty() }
        }.distinct().take(MAX_FILES_PER_CLIP)

    private fun stampOf(clip: ClipData, uris: List<Uri>): String =
        "${clip.description?.timestamp}|${uris.joinToString("|")}"

    private fun captureFiles(app: Context, clip: ClipData, uris: List<Uri>) {
        val stamp = stampOf(clip, uris)
        if (stamp == lastFileStamp) return
        lastFileStamp = stamp
        lastFileKey = null
        val sensitive = clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true
        // 場所から種類が分からないときの控え。`text/uri-list` のような入れ物の種類は使わない。
        val declared = clip.description?.let { d ->
            (0 until d.mimeTypeCount).map { d.getMimeType(it) }
                .firstOrNull { !it.startsWith("text/") && '*' !in it }
        }
        scope.launch {
            fileMutex.withLock {
                ensureLoaded(app)
                runCatching { importFiles(app, uris, declared, sensitive) }
                    .onSuccess { key -> if (stamp == lastFileStamp) lastFileKey = key }
                    .onFailure { Log.w(TAG, "clip file capture failed: ${it.message}") }
            }
        }
    }

    /**
     * [uris] の中身を置き場へ写し、履歴の先頭に積む。積んだ行の [ClipEntry.text] を返す。
     *
     * ⚠ 写している間は [TMP_PREFIX] 付きのフォルダに置き、**履歴に載せるのと同時に**
     * 本来の名前へ変える。こうしないと、写し終わった直後で履歴に載る前のフォルダを
     * [sweepFiles] が「どの行のものでもない」と見て消してしまう。
     */
    private fun importFiles(app: Context, uris: List<Uri>, declaredMime: String?, sensitive: Boolean): String {
        val root = filesRoot(app).apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val tmp = File(root, TMP_PREFIX + id)
        check(tmp.mkdirs()) { "cannot create $FILES_DIR" }
        try {
            val resolver = app.contentResolver
            val digest = MessageDigest.getInstance("SHA-256")
            val files = ArrayList<ClipFile>(uris.size)
            var total = 0L
            uris.forEachIndexed { index, uri ->
                val mime = resolver.getType(uri) ?: declaredMime ?: "application/octet-stream"
                val name = fileName(resolver, uri, mime)
                // 同じ名前のファイルが並んでも上書きしないよう、1 つずつフォルダを分ける。
                val dir = File(tmp, index.toString()).apply { check(mkdir()) { "cannot create folder" } }
                var size = 0L
                (resolver.openInputStream(uri) ?: error("cannot open $uri")).use { input ->
                    File(dir, name).outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            size += n
                            check(total <= MAX_CLIP_FILE_BYTES) { "larger than $MAX_CLIP_FILE_BYTES bytes" }
                            digest.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                        }
                    }
                }
                digest.update(0)
                files += ClipFile(name, mime, "$id/$index/$name", size)
            }
            check(total > 0) { "empty" }
            val key = FILE_KEY_PREFIX + digest.digest().joinToString("") { "%02x".format(it) }
            synchronized(lock) {
                val (pinned, rest) = _history.value.partition { it.pinned }
                val head = rest.firstOrNull()
                if (pinned.any { it.text == key } || (head?.text == key && head.sensitive == sensitive)) {
                    // ピン留め済み、または先頭と同じ中身: 写し直したぶんを捨てるだけ。
                    tmp.deleteRecursively()
                    return key
                }
                check(tmp.renameTo(File(root, id))) { "cannot keep the copy" }
                val entry = ClipEntry(key, System.currentTimeMillis(), sensitive, files)
                _history.value = trimmed(pinned + entry + rest.filterNot { it.text == key })
            }
            if (sensitive) schedulePurge()
            scheduleSave()
            return key
        } catch (e: Exception) {
            tmp.deleteRecursively()
            throw e
        }
    }

    /**
     * コピー元が名乗る名前。拡張子が無ければ種類から足す (端末のコマンドは拡張子で
     * 種類を見分けるものが多い)。⚠ パス区切りなどは必ず落とす (置き場の外へ書かせない)。
     */
    private fun fileName(resolver: ContentResolver, uri: Uri, mime: String): String {
        val declared = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        val safe = (declared ?: uri.lastPathSegment ?: "")
            .substringAfterLast('/')
            .replace(UNSAFE_NAME, "_")
            .trim('.', ' ')
            .take(120)
            .ifBlank { "clip" }
        if (safe.substringAfterLast('.', "").let { it.isNotEmpty() && it.length <= 5 }) return safe
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: return safe
        return "$safe.$extension"
    }

    private val UNSAFE_NAME = Regex("[/\\\\:*?\"<>|\\$`!'\\x00-\\x1F\\x7F]")

    private fun filesRoot(context: Context) = File(context.filesDir, FILES_DIR)

    /** 写しの実ファイル。 */
    fun fileOf(context: Context, file: ClipFile): File = File(filesRoot(context), file.path)

    /** 写しをほかのアプリへ渡すときの場所 (読む許可は渡す側が付ける)。 */
    fun uriOf(context: Context, file: ClipFile): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.sharedfiles", fileOf(context, file))

    /** いまクリップボードに載っているのが画像・ファイルか。 */
    fun clipboardHoldsFiles(context: Context): Boolean = runCatching {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
        clip != null && fileUris(clip).isNotEmpty()
    }.getOrDefault(false)

    /**
     * いまクリップボードに載っている画像・ファイルの行。文字のコピーのとき、まだ写し
     * 終わっていないとき、大きすぎて入らなかったときは null。
     */
    fun currentFileEntry(context: Context): ClipEntry? = runCatching {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
        val uris = fileUris(clip)
        if (uris.isEmpty() || stampOf(clip, uris) != lastFileStamp) return null
        val key = lastFileKey ?: return null
        _history.value.firstOrNull { it.text == key }
    }.getOrNull()

    /**
     * 件数と、写しの合計の上限に収める。[entries] は「ピン留め → それ以外 (新しい順)」の並び。
     *
     * ピン留めした行は数にも入れず、落としもしない (写しの大きさだけは合計から引く)。
     * それ以外のいちばん新しい行 (いま積んだ行) も落とさない。
     */
    private fun trimmed(entries: List<ClipEntry>): List<ClipEntry> {
        var budget = MAX_TOTAL_FILE_BYTES
        var count = 0
        return entries.filter { entry ->
            val size = entry.files.sumOf { it.size }
            if (entry.pinned) {
                budget -= size
                return@filter true
            }
            count++
            if (count > MAX_ENTRIES) return@filter false
            if (size == 0L) return@filter true
            budget -= size
            count == 1 || budget >= 0
        }
    }

    /**
     * [entry] のピン留めを付ける / 外す。外した行はふつうの履歴の先頭へ戻る。
     * 機微な行 (30 秒で消える) には付けられない。
     */
    fun togglePin(entry: ClipEntry) {
        if (entry.sensitive) return
        synchronized(lock) {
            val cur = _history.value
            val found = cur.firstOrNull { it.text == entry.text && it.copiedAt == entry.copiedAt } ?: return
            val (pinned, rest) = (cur - found).partition { it.pinned }
            _history.value = if (found.pinned) trimmed(pinned + found.copy(pinned = false) + rest)
                else pinned + found.copy(pinned = true) + rest
        }
        scheduleSave()
    }

    /**
     * どの行のものでもない写しを消す。行が消えた (削除・全消去・押し出し・30 秒の期限)
     * あとは必ずここを通る ([scheduleSave] の後ろ) ので、消す側で個別に片付けなくてよい。
     *
     * ⚠ フォルダの一覧を**先に**取ってから履歴を見る。順番が逆だと、その間に載った
     * ばかりの行の写しを消してしまう。
     *
     * @param includeUnfinished 写しかけ ([TMP_PREFIX]) も消す。起動直後の 1 回だけ true。
     */
    private fun sweepFiles(context: Context, includeUnfinished: Boolean = false) {
        if (!loaded) return
        val folders = filesRoot(context).listFiles() ?: return
        val keep = synchronized(lock) {
            _history.value.flatMap { it.files }.mapTo(HashSet()) { it.path.substringBefore('/') }
        }
        for (folder in folders) {
            val unfinished = folder.name.startsWith(TMP_PREFIX)
            if (if (unfinished) includeUnfinished else folder.name !in keep) folder.deleteRecursively()
        }
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
        synchronized(lock) {
            val (pinned, cur) = _history.value.partition { it.pinned }
            // ピン留めしてある本文は、コピーし直しても動かさない (同じ行が 2 つ並ぶだけになる)。
            if (pinned.any { it.text == body }) return
            val rest = cur.filterNot { it.text == body }
            val head = cur.firstOrNull()
            if (rest.size == cur.size && head?.text == body && head.sensitive == sensitive) {
                // 既に先頭が同一: 何もしない (時刻だけ更新するほどの価値はない)。
                // ⚠ 機微かどうかが変わったときだけは積み直す (消える行かどうかが変わるため)。
                return
            }
            _history.value = trimmed(pinned + ClipEntry(body, now, sensitive) + rest)
        }
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
        val expired = synchronized(lock) {
            val gone = _history.value.filter { it.sensitive && now - it.copiedAt >= SENSITIVE_TTL_MS }
            if (gone.isNotEmpty()) _history.value = _history.value - gone.toSet()
            gone
        }
        if (expired.isEmpty()) return
        // 画像・ファイルの行は OS のクリップボードと見比べる本文が無いので、履歴からだけ消す。
        contextRef?.let { ctx ->
            expired.filter { it.files.isEmpty() }.forEach { clearSystemClipIfUnchanged(ctx, it.text) }
        }
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
        synchronized(lock) {
            _history.value = _history.value.filterNot { it.text == entry.text && it.copiedAt == entry.copiedAt }
        }
        scheduleSave()
    }

    /** 全削除。⚠ ピン留めした行は残す (残したいから留めたもの。消すときは外してから)。 */
    fun clearAll() {
        synchronized(lock) { _history.value = _history.value.filter { it.pinned } }
        scheduleSave()
    }

    private fun registerSystemSync(context: Context) {
        if (listener != null) return
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            runCatching { capture(context) }
                .onFailure { Log.w(TAG, "clip read failed: ${it.message}") }
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
            runCatching { sweepFiles(ctx) }
                .onFailure { Log.w(TAG, "clip file cleanup failed: ${it.message}") }
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
            val files = readFiles(context, e.optJSONArray("f"))
            // 画像・ファイルの行なのに写しが欠けていたら、貼れない行なので捨てる。
            if (t.startsWith(FILE_KEY_PREFIX) && files.isEmpty()) continue
            out.add(ClipEntry(t, e.optLong("at", 0L), files = files, pinned = e.optBoolean("pin", false)))
        }
        // 並びは「ピン留め → それ以外」。保存した順のままだが、手で書き換えられていても揃える。
        synchronized(lock) { _history.value = trimmed(out.sortedByDescending { it.pinned }) }
    }

    /** 保存してあった写しの一覧。1 つでも実ファイルが無ければ空 (その行ごと捨てさせる)。 */
    private fun readFiles(context: Context, arr: JSONArray?): List<ClipFile> {
        if (arr == null) return emptyList()
        val root = filesRoot(context).canonicalFile
        val out = ArrayList<ClipFile>(arr.length())
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: return emptyList()
            val file = ClipFile(f.optString("n"), f.optString("m"), f.optString("p"), f.optLong("s"))
            val real = File(root, file.path).canonicalFile
            if (!real.isFile || !real.startsWith(root)) return emptyList()
            out.add(file)
        }
        return out
    }

    private fun saveToDisk(context: Context) {
        val arr = JSONArray()
        // ⚠ 機微な行は書かない。30 秒で消えるものを永続化すると、アプリを殺した瞬間だけ
        // ディスクに残るという最悪の形になる。
        for (e in _history.value.filterNot { it.sensitive }) {
            arr.put(JSONObject().apply {
                put("t", e.text)
                put("at", e.copiedAt)
                if (e.pinned) put("pin", true)
                if (e.files.isNotEmpty()) {
                    put("f", JSONArray().apply {
                        for (file in e.files) {
                            put(JSONObject().apply {
                                put("n", file.name)
                                put("m", file.mime)
                                put("p", file.path)
                                put("s", file.size)
                            })
                        }
                    })
                }
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
