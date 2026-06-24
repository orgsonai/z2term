package com.zerotoship.z2term.emulator

import java.io.File
import java.io.RandomAccessFile

/**
 * Kitty graphics protocol の **file/temp/shm 転送** (`t=f`/`t=t`/`t=s`) を、 ホスト
 * (Android 側) のファイル I/O に変換する [KittyGraphicsParser.ExternalTransferSource] 実装。
 *
 * TUI 側 (z2root/proot 内) が出すパスは **ゲスト rootfs 内の絶対パス** (例: `/tmp/img.png`)。
 * z2term 自体は Android 側で動くので、 [rootfsRoot] の下を見にいく形でホスト側 File に
 * 変換して読む。
 *
 *  - `TransferKind.File`: ゲスト絶対パスをそのまま rootfs 配下にマップ。
 *  - `TransferKind.TempFile`: 同上で読了後 `delete()` する (Kitty 仕様)。
 *  - `TransferKind.SharedMemory`: POSIX shm 名 (`/<name>`) を `<rootfsRoot>/dev/shm/<name>`
 *    にマップ。 z2root/proot 内の shm 実装次第なのでベストエフォート。
 *
 * セキュリティ:
 *  - `..` を含むパスは拒否 (rootfs 外へ出ない)。
 *  - 読込上限 [MAX_BYTES] を超えるサイズ指定はそこで打ち切り (zip-bomb / DoS 対策)。
 *  - 既定 OFF。 セッション側で AppSettings の opt-in が ON のときだけ注入される。
 */
class KittyHostTransferSource(
    private val rootfsRoot: File
) : KittyGraphicsParser.ExternalTransferSource {

    override fun read(
        kind: KittyGraphicsParser.TransferKind,
        name: String,
        offset: Long,
        size: Long
    ): ByteArray? {
        val host = resolveHostFile(kind, name) ?: return null
        return try {
            readSlice(host, offset, size)
        } catch (_: Throwable) {
            null
        } finally {
            if (kind == KittyGraphicsParser.TransferKind.TempFile) {
                runCatching { host.delete() }
            }
        }
    }

    private fun resolveHostFile(kind: KittyGraphicsParser.TransferKind, name: String): File? {
        if (name.isEmpty()) return null
        if (name.contains("/../") || name.endsWith("/..") || name.startsWith("../")) return null
        val guestPath = when (kind) {
            KittyGraphicsParser.TransferKind.File,
            KittyGraphicsParser.TransferKind.TempFile -> {
                if (!name.startsWith("/")) return null
                name
            }
            KittyGraphicsParser.TransferKind.SharedMemory -> {
                // POSIX shm: `/<name>` 形式。 先頭スラッシュを除いた名前を /dev/shm 下にマップ。
                val shmName = name.trimStart('/').ifEmpty { return null }
                if (shmName.contains('/')) return null
                "/dev/shm/$shmName"
            }
        }
        // rootfsRoot を起点に rebase。 ただし最終的なパスが rootfsRoot 配下に収まることを再確認。
        val candidate = File(rootfsRoot, guestPath.trimStart('/')).canonicalFile
        val base = rootfsRoot.canonicalFile
        if (!candidate.path.startsWith(base.path + File.separator) && candidate.path != base.path) {
            return null
        }
        return candidate.takeIf { it.isFile }
    }

    private fun readSlice(host: File, offset: Long, size: Long): ByteArray? {
        val length = host.length()
        if (offset < 0 || offset > length) return null
        // 上限超過要求自体を弾く (ファイル長で縮められても、 攻撃検出を兼ねて拒否)。
        if (size > MAX_BYTES) return null
        val want = if (size < 0) length - offset else size.coerceAtMost(length - offset)
        if (want <= 0L) return ByteArray(0)
        if (want > MAX_BYTES) return null
        val buf = ByteArray(want.toInt())
        RandomAccessFile(host, "r").use { raf ->
            if (offset > 0) raf.seek(offset)
            var read = 0
            while (read < buf.size) {
                val n = raf.read(buf, read, buf.size - read)
                if (n <= 0) break
                read += n
            }
            if (read < buf.size) return null
        }
        return buf
    }

    companion object {
        /** 1 回の `t=f`/`t=t`/`t=s` で読める最大サイズ (zip-bomb / DoS 対策)。 */
        const val MAX_BYTES: Long = 16L * 1024 * 1024
    }
}
