package com.zerotoship.z2term.distro

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 公式 URL からディストロ rootfs (tar.gz) をダウンロードしてキャッシュへ保存する。
 *
 * FOSS フレーバー (assets に rootfs を含めないビルド) で利用。ダウンロード後は
 * [DistroInstaller.install] にこのファイルパスを assets 経由ではなく直接渡せる
 * ように [resolveLocalArchive] で取り出せる。
 *
 * セキュリティ:
 * - HTTPS のみ
 * - SHA-256 を比較できる場合は [expectedSha256] で検証
 */
class DistroDownloader(private val context: Context) {

    sealed class Progress {
        data class Started(val total: Long) : Progress()
        data class Downloading(val received: Long, val total: Long) : Progress()
        object Verifying : Progress()
        data class Completed(val file: File) : Progress()
        data class Failed(val error: Throwable) : Progress()
    }

    fun download(
        spec: DistroSpec,
        abi: String,
        expectedSha256: String? = null,
        /**
         * HTTP read timeout (ms)。設定「インストールのタイムアウトを無効化」ON のとき呼び出し側で
         * 長めの値を渡す。0 だと完全無期限で詰まりやすいので、無効化時も上限は付けて広げる方針。
         */
        readTimeoutMs: Int = 30_000
    ): Flow<Progress> = flow {
        val outFile = File(cacheDir().apply { mkdirs() }, "${spec.id}-$abi.tgz")
        try {
            val url = resolveDownloadUrl(spec, abi)
                ?: throw IllegalStateException("No download URL for ${spec.id} / $abi")
            if (outFile.exists()) outFile.delete()

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "z2term/${spec.id}")
            }

            val total = conn.contentLengthLong
            emit(Progress.Started(total))

            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        received += read
                        if (received % (256 * 1024) < 64 * 1024) {
                            emit(Progress.Downloading(received, total))
                        }
                    }
                    emit(Progress.Downloading(received, total))
                }
            }

            if (expectedSha256 != null) {
                emit(Progress.Verifying)
                val actual = sha256(outFile)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    outFile.delete()
                    throw SecurityException("SHA-256 mismatch: expected=$expectedSha256 actual=$actual")
                }
            }

            emit(Progress.Completed(outFile))
            Log.i(TAG, "Downloaded ${spec.id} / $abi -> ${outFile.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "Download failed", e)
            // 途中まで書いた壊れたファイルを消す。残すと次回 resolveLocalArchive が
            // これを「取得済み」とみなし、展開失敗を繰り返す原因になる。
            runCatching { if (outFile.exists()) outFile.delete() }
            emit(Progress.Failed(e))
        }
    }.flowOn(Dispatchers.IO)

    fun resolveLocalArchive(spec: DistroSpec, abi: String): File? {
        // 旧拡張子 (.tar.gz) と新拡張子 (.tgz) の双方を確認
        val tgz = File(cacheDir(), "${spec.id}-$abi.tgz")
        if (tgz.exists()) return tgz
        val legacy = File(cacheDir(), "${spec.id}-$abi.tar.gz")
        return if (legacy.exists()) legacy else null
    }

    /** キャッシュ済みアーカイブ (.tgz / 旧 .tar.gz) を削除。クリーン再インストール用。 */
    fun deleteCachedArchive(distroId: String, abi: String) {
        runCatching { File(cacheDir(), "$distroId-$abi.tgz").delete() }
        runCatching { File(cacheDir(), "$distroId-$abi.tar.gz").delete() }
    }

    private fun cacheDir(): File = File(context.cacheDir, "distros")

    private fun sha256(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = ins.read(buf); if (r < 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * spec から実際のダウンロード URL を決定する。
     *  1. 直接 URL (downloadUrlArm64) があればそれ
     *  2. index URL (indexUrlArm64) があれば、ディレクトリを取得して
     *     最新のタイムスタンプ付きサブディレクトリ + indexFileName を組み立てる
     *     (linuxcontainers の Arch arm64 など)
     *  3. 同梱 distro (Alpine) を FOSS で DL する場合の公式 URL
     */
    private fun resolveDownloadUrl(spec: DistroSpec, abi: String): String? {
        spec.downloadUrl(abi)?.let { return it }
        spec.indexUrl(abi)?.let { return resolveFromIndex(it, spec.indexFileName) }
        return when (spec.id) {
            "alpine" -> when (abi) {
                "arm64-v8a" -> "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz"
                else -> null
            }
            else -> null
        }
    }

    /**
     * ディレクトリ index (HTML) を取得し、`YYYYMMDD_HH%3AMM/` 形式の最新サブ
     * ディレクトリを選んで `<index><latest><fileName>` を返す。
     * linuxcontainers のイメージツリー (有効 HTTPS) を想定。
     */
    private fun resolveFromIndex(indexUrl: String, fileName: String): String? {
        val conn = (URL(indexUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "z2term")
        }
        val html = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        // href="20260520_04%3A18/" のようなエントリを全て拾い、辞書順最大 = 最新
        val dirs = Regex("""href="(\d{8}_\d{2}%3A\d{2}/)"""")
            .findAll(html).map { it.groupValues[1] }.toList()
        val latest = dirs.maxOrNull()
            ?: throw IllegalStateException("index に最新ディレクトリが見つかりません: $indexUrl")
        val base = if (indexUrl.endsWith("/")) indexUrl else "$indexUrl/"
        return "$base$latest$fileName"
    }

    companion object {
        private const val TAG = "DistroDownloader"
    }
}
