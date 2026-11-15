package com.zerotoship.z2term.clipboard

import android.content.Context
import com.zerotoship.z2term.share.SharedIntake
import com.zerotoship.z2term.share.SharedPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 履歴の画像・ファイルを**端末へ貼る**ときの出口。
 *
 * 端末に画像そのものは入らないので、**ホームの `~/z2term-inbox/` に置いて、そのパスを貼る**。
 * 共有で受け取ったファイルと同じ置き場・同じ書き方 (`"$HOME"/'…'`) にしてある — 置き場が
 * 2 つあると探すことになるし、書き方が同じなら Linux でも Android のシェルでもそのまま通る。
 *
 * ⚠ **履歴の写しそのもののパスは貼らない。** 写しは行が消えると一緒に消える
 * ([ClipboardHistoryStore])。貼ったパスが後から開けなくなると、貼った側からは壊れて見える。
 */
object ClipboardFilePaste {
    /** 行ごとの、すでに置いたファイル (ホームからの相対)。同じ行を何度貼っても置き直さない。 */
    private val placed = ConcurrentHashMap<String, List<String>>()

    /** [entry] の画像・ファイルを置き場へ出し、端末に貼る文字列を返す。出せなければ null。 */
    suspend fun terminalText(context: Context, entry: ClipboardHistoryStore.ClipEntry): String? =
        withContext(Dispatchers.IO) {
            if (entry.files.isEmpty()) return@withContext null
            val app = context.applicationContext
            val home = File(app.filesDir, "shared_home")
            val known = placed[entry.text]?.takeIf { paths -> paths.all { File(home, it).isFile } }
            val paths = known ?: runCatching {
                entry.files.map { SharedIntake.importDocument(app, ClipboardHistoryStore.uriOf(app, it)) }
            }.getOrNull()?.also { placed[entry.text] = it }
                ?: return@withContext null
            SharedPayload.insertion("", paths)
        }
}
