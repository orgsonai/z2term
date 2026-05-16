package com.zerotoship.z2term.distro

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

/**
 * ディストロ rootfs の展開を担当。
 *
 * M1 段階では Alpine Linux のみ。assets 配下の tar.gz を解凍する。
 *
 * 注意:
 * - org.apache.commons.compress に依存しないように、tar 部分は手書きで実装する。
 *   Android アプリで依存ライブラリを増やしたくないため。
 * - シンボリックリンクや特殊ファイル（device files）は適切にスキップ/作成する。
 */
class DistroInstaller(private val context: Context) {

    /** 進捗イベント */
    sealed class Progress {
        object Started : Progress()
        data class Extracting(val current: Int, val total: Int, val path: String) : Progress()
        object Configuring : Progress()
        object Completed : Progress()
        data class Failed(val error: Throwable) : Progress()
    }

    /**
     * Alpine Linux を assets から展開。
     *
     * 想定ファイル: app/src/main/assets/alpine-minirootfs-aarch64.tar.gz
     *               app/src/main/assets/alpine-minirootfs-armv7.tar.gz
     */
    fun installAlpine(): Flow<Progress> = flow {
        emit(Progress.Started)
        try {
            val rootfsDir = File(context.filesDir, "distros/alpine")
            if (rootfsDir.exists()) {
                Log.i(TAG, "Alpine already installed, removing old version")
                rootfsDir.deleteRecursively()
            }
            rootfsDir.mkdirs()

            val abi = detectAbi()
            val assetName = when (abi) {
                "arm64-v8a" -> ALPINE_ASSET_ARM64
                "armeabi-v7a" -> ALPINE_ASSET_ARM
                else -> throw UnsupportedOperationException("Unsupported ABI: $abi")
            }
            Log.i(TAG, "Installing Alpine from asset: $assetName")

            context.assets.open(assetName).use { input ->
                GZIPInputStream(input).use { gz ->
                    extractTar(gz, rootfsDir) { current, path ->
                        // Flow を suspend なくしたいので emit はしない（M1 シンプル化）
                    }
                }
            }

            emit(Progress.Configuring)
            postInstallSetup(rootfsDir)

            emit(Progress.Completed)
            Log.i(TAG, "Alpine installation completed: ${rootfsDir.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "Alpine installation failed", e)
            emit(Progress.Failed(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * tar アーカイブを展開（POSIX ustar / GNU tar 互換）。
     * シンボリックリンクは Files.createSymbolicLink で作成。
     */
    private fun extractTar(
        input: java.io.InputStream,
        outputDir: File,
        onProgress: (Int, String) -> Unit
    ) {
        var count = 0
        val buffer = ByteArray(512)
        val dataBuffer = ByteArray(8192)

        while (true) {
            val header = readFully(input, buffer, 0, 512) ?: break

            // ファイル名（最大100文字）
            val name = parseString(buffer, 0, 100)
            if (name.isEmpty()) {
                // 末尾の空ブロックに到達
                break
            }

            val mode = parseOctal(buffer, 100, 8)
            val size = parseOctal(buffer, 124, 12).toLong()
            val typeflag = buffer[156].toInt().toChar()
            val linkname = parseString(buffer, 157, 100)
            val prefix = parseString(buffer, 345, 155)

            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name
            val outFile = File(outputDir, fullName)

            count++
            if (count % 100 == 0) {
                onProgress(count, fullName)
            }

            when (typeflag) {
                '0', '\u0000' -> {
                    // 通常ファイル
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        var remaining = size
                        while (remaining > 0) {
                            val toRead = minOf(remaining.toInt(), dataBuffer.size)
                            val read = input.read(dataBuffer, 0, toRead)
                            if (read < 0) break
                            out.write(dataBuffer, 0, read)
                            remaining -= read
                        }
                    }
                    // パディングスキップ
                    val padding = ((size + 511) / 512 * 512 - size).toInt()
                    if (padding > 0) input.skip(padding.toLong())
                    // パーミッション設定
                    setUnixMode(outFile, mode)
                }
                '5' -> {
                    // ディレクトリ
                    outFile.mkdirs()
                    setUnixMode(outFile, mode)
                }
                '2' -> {
                    // シンボリックリンク
                    outFile.parentFile?.mkdirs()
                    try {
                        if (outFile.exists()) outFile.delete()
                        java.nio.file.Files.createSymbolicLink(
                            outFile.toPath(),
                            java.io.File(linkname).toPath()
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to create symlink: $fullName -> $linkname", e)
                    }
                }
                '1' -> {
                    // ハードリンク（簡易対応: コピー）
                    val target = File(outputDir, linkname)
                    if (target.exists()) {
                        target.copyTo(outFile, overwrite = true)
                    }
                }
                '3', '4', '6' -> {
                    // キャラクタデバイス、ブロックデバイス、FIFO（スキップ）
                    Log.d(TAG, "Skipping special file: $fullName (type=$typeflag)")
                }
                else -> {
                    Log.d(TAG, "Skipping unknown type '$typeflag': $fullName")
                }
            }
        }
        Log.i(TAG, "Tar extraction complete: $count entries")
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray, offset: Int, len: Int): ByteArray? {
        var total = 0
        while (total < len) {
            val read = input.read(buf, offset + total, len - total)
            if (read < 0) return if (total == 0) null else buf
            total += read
        }
        // 全部ゼロなら null を返す（tar の終端マーク）
        if (buf.all { it == 0.toByte() }) return null
        return buf
    }

    private fun parseString(buf: ByteArray, offset: Int, maxLen: Int): String {
        var end = offset
        val limit = offset + maxLen
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset, Charsets.US_ASCII)
    }

    private fun parseOctal(buf: ByteArray, offset: Int, maxLen: Int): Int {
        var result = 0
        for (i in offset until offset + maxLen) {
            val c = buf[i].toInt() and 0xFF
            if (c == 0 || c == ' '.code) break
            if (c < '0'.code || c > '7'.code) break
            result = (result shl 3) or (c - '0'.code)
        }
        return result
    }

    private fun setUnixMode(file: File, mode: Int) {
        // 実行ビット有無のみ反映（API 制約上、完全な chmod は不可）
        if ((mode and 0b001_001_001) != 0) {
            file.setExecutable(true, false)
        }
        if ((mode and 0b010_010_010) != 0) {
            file.setWritable(true, false)
        }
        file.setReadable(true, false)
    }

    /**
     * 展開後の最小限の設定。
     * M1 段階では DNS 設定のみ。zsh インストール等は M4 で対応。
     */
    private fun postInstallSetup(rootfsDir: File) {
        // /etc/resolv.conf を Cloudflare DNS で生成
        val resolvConf = File(rootfsDir, "etc/resolv.conf")
        resolvConf.parentFile?.mkdirs()
        resolvConf.writeText("""
            nameserver 1.1.1.1
            nameserver 1.0.0.1
            nameserver 8.8.8.8
        """.trimIndent())

        // /etc/hosts
        val hosts = File(rootfsDir, "etc/hosts")
        hosts.parentFile?.mkdirs()
        if (!hosts.exists()) {
            hosts.writeText("""
                127.0.0.1   localhost
                ::1         localhost
            """.trimIndent())
        }

        // /tmp 作成
        File(rootfsDir, "tmp").mkdirs()

        Log.i(TAG, "Post-install setup completed")
    }

    private fun detectAbi(): String {
        // Build.SUPPORTED_ABIS の最初を採用
        return android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    }

    companion object {
        private const val TAG = "DistroInstaller"
        private const val ALPINE_ASSET_ARM64 = "alpine-minirootfs-aarch64.tar.gz"
        private const val ALPINE_ASSET_ARM = "alpine-minirootfs-armv7.tar.gz"
    }
}
