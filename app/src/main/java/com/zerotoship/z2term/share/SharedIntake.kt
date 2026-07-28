package com.zerotoship.z2term.share

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

/**
 * 他アプリの共有シート (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) から受け取ったものを、
 * **端末に入れる 1 本の文字列**に変換する (B1)。
 *
 * 方針は「**入れるだけ・実行しない**」。改行を付けないので、受け取った内容は入力行に置かれ、
 * 実行するかどうかは必ずユーザーが決める (共有されたものが勝手に走る事故を作らない)。
 *
 * - **テキスト**を共有された → その文字列をそのまま入れる。
 * - **ファイル**を共有された → [INBOX_DIR] にコピーし、**端末から見たパス**を入れる。
 *   共有 URI は他アプリが握っている一時的な参照で端末 (シェル) からは触れないため、
 *   ホーム配下に実体を置いて初めて `less` や `python` に渡せる状態になる。
 *
 * 変換は I/O を含むのでワーカースレッドから呼ぶこと。
 */
object SharedIntake {

    /** 受け取ったファイルの置き場 (ホームからの相対)。端末からは `~/z2term-inbox/`。 */
    const val INBOX_DIR = "z2term-inbox"

    private const val TAG = "SharedIntake"
    /** 受け取るファイルの上限。これを超えるものは黙ってコピーせず、失敗として扱う。 */
    private const val MAX_BYTES = 512L * 1024 * 1024

    /** 共有が**テキスト**だったことを表す [Intake.kind] の値。 */
    const val KIND_TEXT = "text"

    /** 共有が**ファイル**だったことを表す [Intake.kind] の値。 */
    const val KIND_FILE = "file"

    /**
     * 受け取ったものを 1 つにまとめた結果 (0.8.266)。
     *
     * [text] は**端末に入れる文字列**そのもの (従来からの唯一の出力)。[kind] と [fileNames] は
     * `z2-when` の `share:` トリガーが「テキストか / ファイルか」「拡張子は何か」で絞るために要る。
     * 挿入する文字列からファイル名を読み戻すのは、クォートの有無で形が変わるぶん壊れやすいので、
     * **取り込んだ時点の事実をそのまま持ち回る**。
     *
     * @param fileNames 取り込んだファイル名 (拡張子付き・ディレクトリを含まない)。テキスト共有では空。
     */
    data class Intake(val kind: String, val text: String, val fileNames: List<String>)

    /**
     * [intent] が共有なら、受け取った内容を返す。共有でない / 中身が無いときは null。
     *
     * ファイルが複数のときは、それぞれのパスを**空白区切り**で並べる (そのままコマンドの
     * 引数として使えるように、必要ならクォートする)。
     */
    fun intakeFrom(context: Context, intent: Intent): Intake? {
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        // テキスト共有が最優先。ファイルマネージャ以外の多くはこちらで来る。
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotEmpty() }
            ?.let { return Intake(KIND_TEXT, it, emptyList()) }

        val uris: List<Uri> = when (action) {
            Intent.ACTION_SEND -> listOfNotNull(getStream(intent))
            else -> getStreams(intent)
        }
        if (uris.isEmpty()) return null

        val dir = File(File(context.filesDir, "shared_home"), INBOX_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "cannot create $dir")
            return null
        }
        val names = uris.mapNotNull { copyIn(context, it, dir) }
        if (names.isEmpty()) return null
        return Intake(
            kind = KIND_FILE,
            text = names.joinToString(" ") { homePath("$INBOX_DIR/$it") },
            fileNames = names,
        )
    }

    @Suppress("DEPRECATION")  // getParcelableExtra(String, Class) は API 33+。minSdk 29 のため旧 API を使う。
    private fun getStream(intent: Intent): Uri? =
        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    @Suppress("DEPRECATION")
    private fun getStreams(intent: Intent): List<Uri> =
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty().filterNotNull()

    /** [uri] の中身を [dir] にコピーし、置いたファイル名を返す。失敗したら null。 */
    private fun copyIn(context: Context, uri: Uri, dir: File): String? = runCatching {
        val name = displayName(context.contentResolver, uri)
        val target = uniqueFile(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            var total = 0L
            target.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) {
                        // 途中で打ち切った中途半端なファイルは残さない。
                        output.close()
                        target.delete()
                        Log.w(TAG, "too large: $uri")
                        return null
                    }
                    output.write(buf, 0, n)
                }
            }
        } ?: return null
        target.name
    }.getOrElse {
        Log.w(TAG, "copy failed: ${it.message}")
        null
    }

    /**
     * 共有元が名乗るファイル名を取り出す。取れないときは URI の末尾、それも無ければ既定名。
     * **パス区切りなど危険な文字は必ず落とす** (`../` で置き場の外に書かせない)。
     */
    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        val fromProvider = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        val raw = fromProvider ?: uri.lastPathSegment ?: "shared"
        val safe = raw.substringAfterLast('/').replace(UNSAFE, "_").trim('.', ' ')
        return safe.ifBlank { "shared" }.take(96)
    }

    /** 同名があれば `-2` `-3` … を足して、受け取ったものを取りこぼさない (上書きしない)。 */
    private fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        for (n in 2..99) {
            val f = File(dir, "$stem-$n$ext")
            if (!f.exists()) return f
        }
        return File(dir, "$stem-${System.currentTimeMillis()}$ext")
    }

    /**
     * ホームからの相対パス [rel] を、シェルにそのまま貼れる 1 引数にする。
     *
     * 素直な名前なら `~/z2term-inbox/foo.txt` のまま (読みやすい)。スペースや記号を含むときは
     * **`"$HOME/..."` 形式**にする — `"~/..."` とクォートすると `~` が展開されず
     * 「そんなファイルは無い」になってしまうため。
     * ダブルクォート内で意味を持つ文字 (`"` `\` `$` `` ` `` `!`) は [UNSAFE] 側で既に落としてある。
     */
    private fun homePath(rel: String): String =
        if (rel.none { it.isWhitespace() || it in "'*?[]()&;|<>#~" }) "~/$rel"
        else "\"\$HOME/$rel\""

    /**
     * ファイル名から落とす文字。パス区切り (`../` で置き場の外へ書かせない) に加えて、
     * **ダブルクォートで囲んでも意味を持ってしまう文字** (`"` `\` `$` 逆クォート `!`) と
     * 制御文字も落とす。これで [homePath] のクォートだけで安全に渡せる。
     * スペースやハイフンは普通の名前に出るので残す。
     */
    private val UNSAFE = Regex("[/\\\\:*?\"<>|\\$`!\\x00-\\x1F\\x7F]")
}
