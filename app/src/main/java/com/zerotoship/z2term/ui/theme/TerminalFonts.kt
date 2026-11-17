package com.zerotoship.z2term.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ターミナル用フォントの選択肢。
 *
 * - monospace : Android 標準 FontFamily.Monospace
 * - assets/fonts/ 配下の同梱 TTF ([assetFile])
 * - 利用者が取り込んだフォント ([userFile]。`filesDir/fonts/` に置く。[UserFonts])
 *
 * 同梱フォントは assets に実体があるか、取り込んだフォントはファイルがあるかを確かめ、
 * ある場合のみ実体を返す。なければ Monospace にフォールバック。
 */
data class TerminalFontOption(
    val id: String,
    val displayName: String,
    val assetFile: String? = null,
    val userFile: String? = null,
)

object TerminalFontOptions {
    val MONOSPACE = TerminalFontOption("monospace", "System Monospace", null)
    val IBM_PLEX_MONO = TerminalFontOption("ibm-plex-mono", "IBM Plex Mono", "IBMPlexMono-Regular.ttf")
    val JETBRAINS_MONO = TerminalFontOption("jetbrains-mono", "JetBrains Mono", "JetBrainsMono-Regular.ttf")
    val FIRA_CODE = TerminalFontOption("fira-code", "Fira Code", "FiraCode-Regular.ttf")
    val Z2_MONO = TerminalFontOption("z2-mono", "Z2 Mono", "Z2Mono-Regular.ttf")

    /** 同梱の選択肢。取り込んだフォントは [UserFonts.options] に別に並ぶ。 */
    val ALL = listOf(MONOSPACE, IBM_PLEX_MONO, JETBRAINS_MONO, FIRA_CODE, Z2_MONO)

    fun byId(id: String): TerminalFontOption =
        ALL.firstOrNull { it.id == id } ?: UserFonts.optionFor(id) ?: MONOSPACE

    /** 実体ファイルがあるか確認 */
    fun isAvailable(context: Context, option: TerminalFontOption): Boolean {
        option.userFile?.let { return UserFonts.file(context, it).isFile }
        val file = option.assetFile ?: return true
        return try {
            context.assets.list("fonts")?.contains(file) == true
        } catch (e: Exception) { false }
    }

    /** 選択肢の書体。読めなければ null (呼び出し側で monospace へ落とす)。 */
    fun loadTypeface(context: Context, option: TerminalFontOption): Typeface? {
        option.userFile?.let { return UserFonts.load(UserFonts.file(context, it)) }
        val asset = option.assetFile ?: return null
        return try {
            Typeface.createFromAsset(context.assets, "fonts/$asset")
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * 利用者が取り込んだフォント。
 *
 * **置き場**: `filesDir/fonts/<ファイル名>`。設定画面で選んだファイルを**コピーして持つ**
 * (元ファイルを参照し続けない — SAF の権限はプロセスを跨いで保証されないうえ、元を消されたら
 * 表示が変わる。[com.zerotoship.z2term.ui.terminal.keyboard.UserDictStore] と同じ作法)。
 *
 * - 形式は TrueType / OpenType / コレクション (`.ttf` `.otf` `.ttc`。コレクションは先頭の 1 書体)。
 *   拡張子ではなく**中身を Android に読ませて**判定する (読めないものは置かない)。
 * - 設定の fontId は `user:<ファイル名>`。ファイルが消えていれば monospace へ落ちる。
 * - ⚠ **等幅かどうかは確かめない**。端末は「M」の幅を 1 マスにするので、等幅でない書体だと
 *   字の間が空いたり詰まったりする。選ぶのは利用者 (画面の説明で伝える)。
 */
// 保持するのは applicationContext のみ。
@Suppress("StaticFieldLeak")
object UserFonts {
    private const val TAG = "UserFonts"
    private const val DIR_NAME = "fonts"
    private const val ID_PREFIX = "user:"

    /** 1 ファイルの上限。日本語を含むフォント (20MB 前後) も入る大きさにする。 */
    private const val MAX_FILE_BYTES = 64L * 1024 * 1024

    private val _options = MutableStateFlow<List<TerminalFontOption>>(emptyList())
    /** 取り込み済みフォントの選択肢 (設定画面が購読する)。 */
    val options: StateFlow<List<TerminalFontOption>> = _options.asStateFlow()

    private val _revision = MutableStateFlow(0)
    /**
     * フォントのファイルが変わるたびに増える番号。書体を覚えておく側 (端末の描画・設定の見本) は
     * fontId と**この番号の組**で覚え直す。⚠ fontId はファイル名なので、バックアップから同じ名前の
     * フォントを戻すと fontId は変わらず、番号が無いと古い書体のまま描き続ける。
     */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** 前回読んだときのファイルの顔ぶれ (名前・大きさ・更新日時)。変わったときだけ [revision] を進める。 */
    @Volatile private var lastSignature: List<Triple<String, Long, Long>>? = null

    sealed interface ImportResult {
        data class Success(val option: TerminalFontOption) : ImportResult
        data object TooLarge : ImportResult
        /** フォントとして読めなかった。 */
        data object NotAFont : ImportResult
        data class Failed(val message: String) : ImportResult
    }

    fun dir(context: Context): File = File(context.applicationContext.filesDir, DIR_NAME)

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun optionFor(id: String): TerminalFontOption? {
        if (!id.startsWith(ID_PREFIX)) return null
        val name = id.removePrefix(ID_PREFIX)
        if (name.isBlank() || name.contains('/')) return null
        return option(name)
    }

    private fun option(name: String) = TerminalFontOption(
        id = ID_PREFIX + name,
        displayName = name.substringBeforeLast('.', name),
        userFile = name,
    )

    /** フォントファイルを読む。読めなければ null。 */
    fun load(file: File): Typeface? {
        if (!file.isFile) return null
        return runCatching { Typeface.Builder(file).build() }.getOrNull()
    }

    /** 一覧を読み直す (設定画面を開いたとき・足した/消した後)。 */
    suspend fun reload(context: Context) {
        val app = context.applicationContext
        val files = withContext(Dispatchers.IO) {
            dir(app).listFiles()
                ?.filter { it.isFile && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }
        val signature = files.map { Triple(it.name, it.length(), it.lastModified()) }
        // 設定画面を開くたびの読み直しでは書体を作り直さない (中身が同じなら番号を据え置く)。
        // ⚠ 初回 (lastSignature が null) も進める — その起動で最初の読み込みがバックアップからの
        // 復元だったとき、据え置くと古い書体のまま残る (作り直しは 1 度だけで軽い)。
        if (signature != lastSignature) _revision.value++
        lastSignature = signature
        _options.value = files.map { option(it.name) }
    }

    /**
     * [target] を [data] で置き換える (バックアップから戻すとき)。
     *
     * ⚠ **上書きせず、別ファイルに書いてから名前を付け替える**。読み込んだ書体はファイルを
     * メモリに対応付けて (mmap) 使っているので、使用中のファイルをその場で切り詰めて書き直すと、
     * 描画中に読みに行った瞬間にアプリごと落ちうる。付け替えなら古い中身は書体を手放すまで残る。
     */
    fun replaceAtomically(target: File, data: ByteArray) {
        val temp = File(target.parentFile, ".restore-${System.nanoTime()}")
        try {
            temp.writeBytes(data)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) throw java.io.IOException("cannot replace ${target.name}")
            }
        } finally {
            temp.delete()
        }
    }

    suspend fun import(context: Context, uri: Uri): ImportResult {
        val app = context.applicationContext
        val result = withContext(Dispatchers.IO) {
            val dir = dir(app).apply { mkdirs() }
            // いったん隠しファイルへ書き、フォントとして読めたら本来の名前にする
            // (読めないファイルが一覧に一瞬でも並ばないように)。
            val temp = File(dir, ".import-${System.nanoTime()}")
            runCatching {
                val size = copyLimited(app, uri, temp)
                    ?: return@runCatching ImportResult.TooLarge.also { temp.delete() }
                if (size == 0L || load(temp) == null) {
                    temp.delete()
                    return@runCatching ImportResult.NotAFont
                }
                val target = uniqueTarget(dir, displayName(app, uri))
                if (!temp.renameTo(target)) {
                    temp.copyTo(target)
                    temp.delete()
                }
                ImportResult.Success(option(target.name))
            }.getOrElse { e ->
                temp.delete()
                Log.w(TAG, "import failed: ${e.message}")
                ImportResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
        if (result is ImportResult.Success) reload(app)
        return result
    }

    /** 取り込んだフォント [option] を消す。 */
    suspend fun remove(context: Context, option: TerminalFontOption) {
        val name = option.userFile ?: return
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            runCatching { file(app, name).delete() }
                .onFailure { Log.w(TAG, "remove failed: ${it.message}") }
        }
        reload(app)
    }

    /** [MAX_FILE_BYTES] を超えたら null (途中まで書いたものは呼び出し側で消す)。 */
    private fun copyLimited(context: Context, uri: Uri, out: File): Long? {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("cannot open")
        input.use { ins ->
            out.outputStream().use { os ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_FILE_BYTES) return null
                    os.write(buf, 0, n)
                }
                return total
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "font.ttf"
    }

    /** 名前が衝突したら `-2` `-3`… を付けて別物として残す (選択中のフォントを黙って差し替えない)。 */
    private fun uniqueTarget(dir: File, rawName: String): File {
        val safe = rawName.replace(Regex("""[/\\:*?"<>|]"""), "_").trim()
            .trimStart('.')
            .ifBlank { "font.ttf" }
        val base = safe.substringBeforeLast('.', safe)
        val ext = safe.substringAfterLast('.', "")
        var candidate = File(dir, safe)
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "$base-$n" else "$base-$n.$ext")
            n++
        }
        return candidate
    }
}

/** 指定オプションに対応する Compose FontFamily を返す。不在なら Monospace */
@Composable
fun rememberTerminalFontFamily(option: TerminalFontOption): FontFamily {
    val context = LocalContext.current
    val revision by UserFonts.revision.collectAsState()
    return remember(option.id, revision) {
        TerminalFontOptions.loadTypeface(context, option)?.let { FontFamily(it) }
            ?: FontFamily.Monospace
    }
}
