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

    fun download(spec: DistroSpec, abi: String, expectedSha256: String? = null): Flow<Progress> = flow {
        try {
            val url = officialUrlFor(spec, abi)
                ?: throw IllegalStateException("No official URL for ${spec.id} / $abi")
            val outFile = File(cacheDir().apply { mkdirs() }, "${spec.id}-$abi.tgz")
            if (outFile.exists()) outFile.delete()

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
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

    private fun officialUrlFor(spec: DistroSpec, abi: String): String? = when (spec.id) {
        "alpine" -> when (abi) {
            "arm64-v8a" -> "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz"
            "armeabi-v7a" -> "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armv7/alpine-minirootfs-3.21.0-armv7.tar.gz"
            else -> null
        }
        // Ubuntu の公式は xz 圧縮で配布なので、当面は手動配置をドキュメントで案内
        else -> null
    }

    companion object {
        private const val TAG = "DistroDownloader"
    }
}
