package com.zerotoship.z2term.gui.rdp

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * 端末の 1 フォルダを、RDP の相手から `\\tsclient\<共有名>` として読み書きさせる。
 * [MS-RDPEFS] のドライブ ([RdpDeviceRedirection] が運ぶ IRP) を、この 1 か所で処理する。
 *
 * ## 何をしていて、何をしていないか
 *
 * 相手 (Windows) は**普通のファイルシステムだと思って**話しかけてくる。開く・読む・書く・
 * 一覧する・名前を変える・消す — の要求 (IRP) が 1 つずつ届き、こちらが 1 つずつ答える。
 * ⇒ ここは「小さなファイルサーバー」であって、まとめて転送する仕組みではない。
 *
 * ⛔ **[root] の外へは 1 バイトも出さない。** 相手が `..` を混ぜた道や、外を指す
 * シンボリックリンクを渡してくることを前提に、**実体のパスが [root] の下にあることを毎回
 * 確かめる** ([resolve])。ここが共有フォルダと「端末まるごと公開」の唯一の境目になる。
 *
 * ⛔ **[process] を RDP の受信ループから直接呼ばない。** ファイル I/O は待つ処理なので、
 * 受信ループで動かすと画面も入力も音も止まる。呼び出し側 ([RdpDeviceRedirection]) が
 * 専用のスレッドへ回す。
 *
 * ⚠ **ハンドルは相手が閉じるまで残る。** 相手がタブを閉じずに切断することもあるので、
 * セッションの終わりに [close] で全部畳む。
 */
internal class RdpDrive(
    /** 端末側の共有フォルダ。無ければ作る (作れなければ共有そのものを諦める)。 */
    private val root: File,
    /** 相手の一覧に出る名前。 */
    val shareName: String,
) : AutoCloseable {

    /** 相手から届いた 1 件の要求。ヘッダは [RdpDeviceRedirection] が剥がしている。 */
    class Request(val fileId: Int, val majorFunction: Int, val minorFunction: Int, val body: ByteArray)

    /** 1 件の答え。[payload] は DR_DEVICE_IOCOMPLETION のヘッダより後ろだけ。 */
    class Response(val status: Int, val payload: ByteArray)

    /** 開いているファイル / フォルダ 1 つ。 */
    private class Handle(
        val file: File,
        val directory: Boolean,
        var access: RandomAccessFile?,
        var deleteOnClose: Boolean = false,
        /** 一覧の途中経過。1 回の要求につき 1 件ずつ返す決まりなので、位置を覚えておく。 */
        var listing: List<Pair<String, File>>? = null,
        var listIndex: Int = 0,
    )

    private val handles = HashMap<Int, Handle>()
    private var nextFileId = 1

    /** 相手が受け取れる最大。1 通が大きすぎるとチャネルの再組み立てが溢れる。 */
    private val maxIoBytes = 8 * 1024 * 1024

    @Synchronized
    fun process(request: Request): Response? = try {
        when (request.majorFunction) {
            IRP_MJ_CREATE -> create(request.body)
            IRP_MJ_CLOSE -> closeHandle(request.fileId)
            IRP_MJ_READ -> read(request.fileId, request.body)
            IRP_MJ_WRITE -> write(request.fileId, request.body)
            IRP_MJ_QUERY_INFORMATION -> queryInformation(request.fileId, request.body)
            IRP_MJ_SET_INFORMATION -> setInformation(request.fileId, request.body)
            IRP_MJ_QUERY_VOLUME_INFORMATION -> queryVolumeInformation(request.body)
            IRP_MJ_DIRECTORY_CONTROL -> directoryControl(request)
            IRP_MJ_LOCK_CONTROL -> Response(STATUS_SUCCESS, ByteArray(5))
            // フラッシュは「書き終わったか」の確認。こちらは都度書いているので成功で返す。
            IRP_MJ_FLUSH_BUFFERS -> Response(STATUS_SUCCESS, RdpLe.bytes32(0))
            else -> {
                Log.i(TAG, "unhandled IRP major=0x${request.majorFunction.toString(16)}")
                Response(STATUS_NOT_SUPPORTED, RdpLe.bytes32(0))
            }
        }
    } catch (e: Exception) {
        // ⚠ **1 件の失敗でセッションを落とさない。** 相手はエラーを受け取れる。
        Log.w(TAG, "drive request failed (major=0x${request.majorFunction.toString(16)})", e)
        Response(STATUS_UNSUCCESSFUL, RdpLe.bytes32(0))
    }

    @Synchronized
    override fun close() {
        handles.values.forEach { handle ->
            runCatching { handle.access?.close() }
            if (handle.deleteOnClose) runCatching { handle.file.delete() }
        }
        handles.clear()
    }

    // --- 開く / 閉じる ---

    private fun create(body: ByteArray): Response {
        if (body.size < CREATE_HEADER) return Response(STATUS_UNSUCCESSFUL, createReply(0, 0))
        val desiredAccess = RdpLe.u32(body, 0)
        val createDisposition = RdpLe.u32(body, 20)
        val createOptions = RdpLe.u32(body, 24)
        val pathLength = RdpLe.u32(body, 28)
        val path = RdpLe.readUtf16(body, CREATE_HEADER, pathLength)

        val target = resolve(path)
            ?: return Response(STATUS_ACCESS_DENIED, createReply(0, 0))

        val wantsDirectory = (createOptions and FILE_DIRECTORY_FILE) != 0
        val existed = target.exists()

        // ⚠ 消す許可だけを求めて開いてくる (Explorer の削除)。中身を開かずにハンドルだけ返す。
        val information: Int
        when (createDisposition) {
            FILE_SUPERSEDE -> {
                if (existed && !target.isDirectory) target.delete()
                information = FILE_SUPERSEDED
            }
            FILE_OPEN -> {
                if (!existed) return Response(STATUS_NO_SUCH_FILE, createReply(0, 0))
                information = FILE_OPENED
            }
            FILE_CREATE -> {
                if (existed) return Response(STATUS_OBJECT_NAME_COLLISION, createReply(0, 0))
                information = FILE_CREATED
            }
            FILE_OPEN_IF -> information = if (existed) FILE_OPENED else FILE_CREATED
            FILE_OVERWRITE -> {
                if (!existed) return Response(STATUS_NO_SUCH_FILE, createReply(0, 0))
                information = FILE_OVERWRITTEN
            }
            FILE_OVERWRITE_IF -> information = if (existed) FILE_OVERWRITTEN else FILE_CREATED
            else -> return Response(STATUS_NOT_SUPPORTED, createReply(0, 0))
        }

        val isDirectory = if (existed) target.isDirectory else wantsDirectory
        if (!existed) {
            val created = if (isDirectory) target.mkdirs() else {
                target.parentFile?.mkdirs()
                runCatching { target.createNewFile() }.getOrDefault(false)
            }
            if (!created) return Response(STATUS_ACCESS_DENIED, createReply(0, 0))
        }

        var access: RandomAccessFile? = null
        if (!isDirectory) {
            // ⚠ 書ける相手なら書ける形で開く。読み取りしか許されない場所 (端末の設定次第) は
            //   読み取りへ落として繋ぎ続ける — 開けないより見えた方がいい。
            access = runCatching { RandomAccessFile(target, "rw") }.getOrNull()
                ?: runCatching { RandomAccessFile(target, "r") }.getOrNull()
                ?: return Response(STATUS_ACCESS_DENIED, createReply(0, 0))
            if (information == FILE_OVERWRITTEN || information == FILE_SUPERSEDED) {
                runCatching { access.setLength(0) }
            }
        }

        val fileId = nextFileId++
        handles[fileId] = Handle(
            file = target,
            directory = isDirectory,
            access = access,
            deleteOnClose = (createOptions and FILE_DELETE_ON_CLOSE) != 0,
        )
        Log.i(
            TAG,
            "open '${path.ifBlank { "\\" }}' " +
                "(${if (isDirectory) "dir" else "file"}, access=0x${desiredAccess.toString(16)})",
        )
        return Response(STATUS_SUCCESS, createReply(fileId, information))
    }

    private fun createReply(fileId: Int, information: Int): ByteArray =
        RdpLe.build {
            u32(fileId)
            u8(information)
        }

    private fun closeHandle(fileId: Int): Response {
        val handle = handles.remove(fileId)
        runCatching { handle?.access?.close() }
        if (handle != null && handle.deleteOnClose) {
            val gone = if (handle.directory) handle.file.deleteRecursively() else handle.file.delete()
            if (!gone) Log.w(TAG, "could not delete ${handle.file.name}")
        }
        return Response(STATUS_SUCCESS, ByteArray(5))
    }

    // --- 読み書き ---

    private fun read(fileId: Int, body: ByteArray): Response {
        val handle = handles[fileId] ?: return Response(STATUS_UNSUCCESSFUL, RdpLe.bytes32(0))
        val access = handle.access ?: return Response(STATUS_ACCESS_DENIED, RdpLe.bytes32(0))
        if (body.size < 12) return Response(STATUS_UNSUCCESSFUL, RdpLe.bytes32(0))
        val length = RdpLe.u32Long(body, 0).coerceAtMost(maxIoBytes.toLong()).toInt()
        val offset = RdpLe.i64(body, 4)
        if (length <= 0 || offset < 0) return Response(STATUS_SUCCESS, RdpLe.bytes32(0))

        access.seek(offset)
        val buffer = ByteArray(length)
        var filled = 0
        while (filled < length) {
            val n = access.read(buffer, filled, length - filled)
            if (n <= 0) break
            filled += n
        }
        return Response(
            STATUS_SUCCESS,
            RdpLe.build {
                u32(filled)
                write(buffer, 0, filled)
            },
        )
    }

    private fun write(fileId: Int, body: ByteArray): Response {
        val handle = handles[fileId] ?: return Response(STATUS_UNSUCCESSFUL, writeReply(0))
        val access = handle.access ?: return Response(STATUS_ACCESS_DENIED, writeReply(0))
        if (body.size < WRITE_HEADER) return Response(STATUS_UNSUCCESSFUL, writeReply(0))
        val declared = RdpLe.u32Long(body, 0).toInt()
        val offset = RdpLe.i64(body, 4)
        val available = body.size - WRITE_HEADER
        val length = declared.coerceIn(0, available)
        if (offset < 0) return Response(STATUS_UNSUCCESSFUL, writeReply(0))

        access.seek(offset)
        access.write(body, WRITE_HEADER, length)
        return Response(STATUS_SUCCESS, writeReply(length))
    }

    private fun writeReply(length: Int): ByteArray = RdpLe.build {
        u32(length)
        u8(0) // Padding
    }

    // --- ファイルの情報 ---

    private fun queryInformation(fileId: Int, body: ByteArray): Response {
        val handle = handles[fileId] ?: return Response(STATUS_UNSUCCESSFUL, RdpLe.bytes32(0))
        if (body.size < 8) return Response(STATUS_UNSUCCESSFUL, RdpLe.bytes32(0))
        val infoClass = RdpLe.u32(body, 0)
        val file = handle.file
        val buffer = when (infoClass) {
            FILE_BASIC_INFORMATION -> RdpLe.build {
                val time = toFileTime(file.lastModified())
                u64(time) // CreationTime: 端末は作成時刻を持たないので更新時刻で代用
                u64(time) // LastAccessTime
                u64(time) // LastWriteTime
                u64(time) // ChangeTime
                u32(attributesOf(file))
            }
            FILE_STANDARD_INFORMATION -> RdpLe.build {
                val size = if (file.isDirectory) 0L else file.length()
                u64(size) // AllocationSize
                u64(size) // EndOfFile
                u32(1) // NumberOfLinks
                u8(if (handle.deleteOnClose) 1 else 0)
                u8(if (file.isDirectory) 1 else 0)
            }
            FILE_ATTRIBUTE_TAG_INFORMATION -> RdpLe.build {
                u32(attributesOf(file))
                u32(0) // ReparseTag: 再解析ポイントは扱わない
            }
            else -> return Response(STATUS_NOT_SUPPORTED, RdpLe.bytes32(0))
        }
        return Response(
            STATUS_SUCCESS,
            RdpLe.build {
                u32(buffer.size)
                write(buffer)
            },
        )
    }

    private fun setInformation(fileId: Int, body: ByteArray): Response {
        val handle = handles[fileId] ?: return Response(STATUS_UNSUCCESSFUL, RdpLe.bytes32(0))
        if (body.size < SET_INFORMATION_HEADER) return Response(STATUS_UNSUCCESSFUL, RdpLe.bytes32(0))
        val infoClass = RdpLe.u32(body, 0)
        val length = RdpLe.u32(body, 4)
        val buffer = SET_INFORMATION_HEADER

        val status = when (infoClass) {
            // 時刻と属性。⚠ **黙って成功にする。** Android のファイルには Windows の属性が
            //   無く、ここを失敗にすると Explorer のコピーが最後の 1 手で必ず失敗する。
            FILE_BASIC_INFORMATION -> {
                if (body.size >= buffer + 24) {
                    val lastWrite = RdpLe.i64(body, buffer + 16)
                    if (lastWrite > 0) runCatching { handle.file.setLastModified(toEpochMillis(lastWrite)) }
                }
                STATUS_SUCCESS
            }
            FILE_END_OF_FILE_INFORMATION, FILE_ALLOCATION_INFORMATION -> {
                if (body.size < buffer + 8) STATUS_UNSUCCESSFUL else {
                    val size = RdpLe.i64(body, buffer)
                    val access = handle.access
                    if (access == null || size < 0) STATUS_UNSUCCESSFUL
                    else {
                        access.setLength(size)
                        STATUS_SUCCESS
                    }
                }
            }
            FILE_DISPOSITION_INFORMATION -> {
                // 本体が無ければ「消す」。1 バイト来ていればその値。
                handle.deleteOnClose = if (length > 0 && body.size > buffer) {
                    RdpLe.u8(body, buffer) != 0
                } else true
                STATUS_SUCCESS
            }
            FILE_RENAME_INFORMATION -> rename(handle, body, buffer)
            else -> STATUS_NOT_SUPPORTED
        }
        return Response(
            status,
            RdpLe.build {
                u32(length)
                u8(0) // Padding
            },
        )
    }

    /** DR_DRIVE_SET_INFORMATION_REQ の FileRenameInformation ぶん。 */
    private fun rename(handle: Handle, body: ByteArray, offset: Int): Int {
        if (body.size < offset + 6) return STATUS_UNSUCCESSFUL
        val replaceIfExists = RdpLe.u8(body, offset) != 0
        val nameLength = RdpLe.u32(body, offset + 2)
        val name = RdpLe.readUtf16(body, offset + 6, nameLength)
        val target = resolve(name) ?: return STATUS_ACCESS_DENIED
        if (target.exists()) {
            if (!replaceIfExists) return STATUS_OBJECT_NAME_COLLISION
            if (!target.deleteRecursively()) return STATUS_ACCESS_DENIED
        }
        // ⚠ 開いたままだと Android でも rename が失敗しうる。先に閉じる。
        runCatching { handle.access?.close() }
        handle.access = null
        target.parentFile?.mkdirs()
        if (!handle.file.renameTo(target)) return STATUS_ACCESS_DENIED
        return STATUS_SUCCESS
    }

    // --- ボリュームの情報 ---

    private fun queryVolumeInformation(body: ByteArray): Response {
        if (body.size < 8) return Response(STATUS_UNSUCCESSFUL, RdpLe.bytes32(0))
        val label = RdpLe.utf16(shareName)
        val fileSystem = RdpLe.utf16(FILE_SYSTEM_NAME)
        // ⚠ 空き容量は「端末の空き」をそのまま出す。Explorer は残量を見てコピーを止める。
        val blockSize = 4096L
        val total = (root.totalSpace.takeIf { it > 0 } ?: DEFAULT_VOLUME_BYTES) / blockSize
        val free = (root.usableSpace.takeIf { it > 0 } ?: 0L) / blockSize

        val buffer = when (RdpLe.u32(body, 0)) {
            FILE_FS_VOLUME_INFORMATION -> RdpLe.build {
                u64(toFileTime(root.lastModified()))
                u32(VOLUME_SERIAL)
                u32(label.size)
                u8(0) // SupportsObjects
                // ⚠ Reserved は付けない (付けると相手が長さを読み違える)。
                write(label)
            }
            FILE_FS_SIZE_INFORMATION -> RdpLe.build {
                u64(total)
                u64(free)
                u32(1) // SectorsPerAllocationUnit
                u32(blockSize.toInt()) // BytesPerSector
            }
            FILE_FS_FULL_SIZE_INFORMATION -> RdpLe.build {
                u64(total)
                u64(free) // CallerAvailableAllocationUnits
                u64(free) // ActualAvailableAllocationUnits
                u32(1)
                u32(blockSize.toInt())
            }
            FILE_FS_ATTRIBUTE_INFORMATION -> RdpLe.build {
                u32(FILE_CASE_SENSITIVE_SEARCH or FILE_CASE_PRESERVED_NAMES or FILE_UNICODE_ON_DISK)
                u32(255) // MaximumComponentNameLength
                u32(fileSystem.size)
                write(fileSystem)
            }
            FILE_FS_DEVICE_INFORMATION -> RdpLe.build {
                u32(FILE_DEVICE_DISK)
                u32(0) // Characteristics
            }
            else -> return Response(STATUS_NOT_SUPPORTED, RdpLe.bytes32(0))
        }
        return Response(
            STATUS_SUCCESS,
            RdpLe.build {
                u32(buffer.size)
                write(buffer)
            },
        )
    }

    // --- 一覧 ---

    private fun directoryControl(request: Request): Response? = when (request.minorFunction) {
        IRP_MN_QUERY_DIRECTORY -> queryDirectory(request.fileId, request.body)
        // ⚠ **変更通知には答えない。** 本物のファイルシステムは「何か変わるまで」返事を
        //   保留する。ここで失敗を返すと Explorer がフォルダを開いた直後にエラーを出す。
        //   ⇒ 黙って握る (相手はセッションが切れるまで待ち続けるだけで、実害が無い)。
        IRP_MN_NOTIFY_CHANGE_DIRECTORY -> null
        else -> Response(STATUS_NOT_SUPPORTED, RdpLe.bytes32(0))
    }

    private fun queryDirectory(fileId: Int, body: ByteArray): Response {
        val handle = handles[fileId] ?: return Response(STATUS_UNSUCCESSFUL, emptyListReply())
        if (!handle.directory) return Response(STATUS_UNSUCCESSFUL, emptyListReply())
        if (body.size < QUERY_DIRECTORY_HEADER) return Response(STATUS_UNSUCCESSFUL, emptyListReply())
        val infoClass = RdpLe.u32(body, 0)
        val initialQuery = RdpLe.u8(body, 4) != 0
        val pathLength = RdpLe.u32(body, 5)
        val path = RdpLe.readUtf16(body, QUERY_DIRECTORY_HEADER, pathLength)

        if (initialQuery) {
            val pattern = path.substringAfterLast('\\').ifBlank { "*" }
            handle.listing = listOf(
                "." to handle.file,
                ".." to (handle.file.parentFile ?: handle.file),
            ).filter { matches(it.first, pattern) } +
                (handle.file.listFiles() ?: emptyArray())
                    .filter { matches(it.name, pattern) }
                    .sortedBy { it.name.lowercase() }
                    .map { it.name to it }
            handle.listIndex = 0
        }

        val listing = handle.listing ?: return Response(STATUS_NO_MORE_FILES, emptyListReply())
        if (handle.listIndex >= listing.size) {
            // 初回で 1 件も無い = そもそも見つからない。2 回目以降は「もう無い」。
            val status = if (initialQuery) STATUS_NO_SUCH_FILE else STATUS_NO_MORE_FILES
            return Response(status, emptyListReply())
        }
        val (name, file) = listing[handle.listIndex++]
        val entry = directoryEntry(infoClass, name, file)
            ?: return Response(STATUS_NOT_SUPPORTED, emptyListReply())
        return Response(
            STATUS_SUCCESS,
            RdpLe.build {
                u32(entry.size)
                write(entry)
            },
        )
    }

    private fun emptyListReply(): ByteArray = RdpLe.build {
        u32(0)
        u8(0) // Padding
    }

    /** [MS-FSCC] の一覧 1 件。⚠ 1 通に 1 件だけ (NextEntryOffset は必ず 0)。 */
    private fun directoryEntry(infoClass: Int, name: String, file: File): ByteArray? {
        val encoded = RdpLe.utf16(name)
        val time = toFileTime(file.lastModified())
        val size = if (file.isDirectory) 0L else file.length()
        return when (infoClass) {
            FILE_NAMES_INFORMATION -> RdpLe.build {
                u32(0) // NextEntryOffset
                u32(0) // FileIndex
                u32(encoded.size)
                write(encoded)
            }
            FILE_DIRECTORY_INFORMATION, FILE_FULL_DIRECTORY_INFORMATION, FILE_BOTH_DIRECTORY_INFORMATION -> {
                RdpLe.build {
                    u32(0) // NextEntryOffset
                    u32(0) // FileIndex
                    u64(time) // CreationTime
                    u64(time) // LastAccessTime
                    u64(time) // LastWriteTime
                    u64(time) // ChangeTime
                    u64(size) // EndOfFile
                    u64(size) // AllocationSize
                    u32(attributesOf(file))
                    u32(encoded.size)
                    if (infoClass != FILE_DIRECTORY_INFORMATION) u32(0) // EaSize
                    if (infoClass == FILE_BOTH_DIRECTORY_INFORMATION) {
                        u8(0) // ShortNameLength: 8.3 名は出さない
                        // ⚠⚠ **Reserved の 1 バイトは足さない。** [MS-FSCC] の表には載っているが、
                        //    実際の RDP では入れると相手が名前の位置を 1 バイト読み違える
                        //    (FreeRDP も同じ判断をしていて、実機での相互接続はこちらが正しい)。
                        //    FileBasicInformation を 36 バイト、FileStandardInformation を
                        //    22 バイトで返しているのも同じ理由。
                        zeros(24) // ShortName
                    }
                    write(encoded)
                }
            }
            else -> null
        }
    }

    // --- 道の解決と小物 ---

    /**
     * 相手のパス (`\sub\file.txt`) を端末のファイルにする。
     *
     * ⛔ **[root] の外を指すものは null**。`..` を弾くだけでは足りず、**実体を解決してから**
     * [root] の下かどうかを見る (共有フォルダの中に外を指すリンクが置かれている場合がある)。
     */
    private fun resolve(path: String): File? {
        val cleaned = path.replace('\\', '/').trim('/')
        if (cleaned.isEmpty()) return root
        val parts = cleaned.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == "." || it == ".." }) return null
        val candidate = File(root, parts.joinToString(File.separator))
        return runCatching {
            val base = root.canonicalFile
            val resolved = candidate.canonicalFile
            if (resolved == base || resolved.path.startsWith(base.path + File.separator)) {
                candidate
            } else null
        }.getOrNull()
    }

    /** Windows の検索パターン (`*` と `?` だけ) の照合。 */
    private fun matches(name: String, pattern: String): Boolean {
        if (pattern.isBlank() || pattern == "*" || pattern == "*.*") return true
        val regex = buildString {
            append("(?i)")
            pattern.forEach { ch ->
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(ch.toString()))
                }
            }
        }
        return runCatching { Regex(regex).matches(name) }.getOrDefault(true)
    }

    private fun attributesOf(file: File): Int {
        var attributes = if (file.isDirectory) FILE_ATTRIBUTE_DIRECTORY else FILE_ATTRIBUTE_NORMAL
        if (file.name.startsWith(".")) attributes = attributes or FILE_ATTRIBUTE_HIDDEN
        if (file.exists() && !file.canWrite()) attributes = attributes or FILE_ATTRIBUTE_READONLY
        return attributes
    }

    companion object {
        private const val TAG = "RdpDrive"

        /** 相手に名乗るファイルシステム名。Windows 側の表示に出る。 */
        private const val FILE_SYSTEM_NAME = "z2termfs"
        private const val VOLUME_SERIAL = 0x5A327465
        private const val DEFAULT_VOLUME_BYTES = 4L * 1024 * 1024 * 1024

        /**
         * Windows の時刻は **1601-01-01 UTC から 100 ナノ秒単位**。Unix epoch との差が
         * この定数 (秒)。⚠ ここを間違えると Explorer が全部のファイルを 1601 年扱いにする。
         */
        private const val EPOCH_DIFF_SECONDS = 11_644_473_600L

        fun toFileTime(epochMillis: Long): Long =
            if (epochMillis <= 0) 0L else (epochMillis + EPOCH_DIFF_SECONDS * 1000L) * 10_000L

        fun toEpochMillis(fileTime: Long): Long =
            if (fileTime <= 0) 0L else fileTime / 10_000L - EPOCH_DIFF_SECONDS * 1000L

        // DR_CREATE_REQ の固定部 (DesiredAccess..PathLength)
        private const val CREATE_HEADER = 32
        // DR_WRITE_REQ の固定部 (Length + Offset + Padding)
        private const val WRITE_HEADER = 32
        // DR_DRIVE_SET_INFORMATION_REQ / QUERY_INFORMATION の固定部
        private const val SET_INFORMATION_HEADER = 32
        // DR_DRIVE_QUERY_DIRECTORY_REQ の固定部
        private const val QUERY_DIRECTORY_HEADER = 32

        // [MS-RDPEFS] 2.2.1.4 MajorFunction
        const val IRP_MJ_CREATE = 0x00000000
        const val IRP_MJ_CLOSE = 0x00000002
        const val IRP_MJ_READ = 0x00000003
        const val IRP_MJ_WRITE = 0x00000004
        const val IRP_MJ_QUERY_INFORMATION = 0x00000005
        const val IRP_MJ_SET_INFORMATION = 0x00000006
        const val IRP_MJ_QUERY_VOLUME_INFORMATION = 0x0000000A
        const val IRP_MJ_DIRECTORY_CONTROL = 0x0000000C
        const val IRP_MJ_FLUSH_BUFFERS = 0x00000009
        const val IRP_MJ_LOCK_CONTROL = 0x00000011

        const val IRP_MN_QUERY_DIRECTORY = 0x00000001
        const val IRP_MN_NOTIFY_CHANGE_DIRECTORY = 0x00000002

        // NTSTATUS。⚠ 0x8/0xC 始まりは Int に収まらない Long リテラルなので const にできない。
        const val STATUS_SUCCESS = 0x00000000
        val STATUS_NO_MORE_FILES = 0x80000006.toInt()
        val STATUS_UNSUCCESSFUL = 0xC0000001.toInt()
        val STATUS_NO_SUCH_FILE = 0xC000000F.toInt()
        val STATUS_ACCESS_DENIED = 0xC0000022.toInt()
        val STATUS_OBJECT_NAME_COLLISION = 0xC0000035.toInt()
        val STATUS_NOT_SUPPORTED = 0xC00000BB.toInt()

        // CreateDisposition
        private const val FILE_SUPERSEDE = 0x00000000
        private const val FILE_OPEN = 0x00000001
        private const val FILE_CREATE = 0x00000002
        private const val FILE_OPEN_IF = 0x00000003
        private const val FILE_OVERWRITE = 0x00000004
        private const val FILE_OVERWRITE_IF = 0x00000005

        // Create の Information (相手に「何をしたか」を返す)
        private const val FILE_SUPERSEDED = 0x00000000
        private const val FILE_OPENED = 0x00000001
        private const val FILE_CREATED = 0x00000002
        private const val FILE_OVERWRITTEN = 0x00000003

        // CreateOptions
        private const val FILE_DIRECTORY_FILE = 0x00000001
        private const val FILE_DELETE_ON_CLOSE = 0x00001000

        // [MS-FSCC] FileInformationClass
        private const val FILE_DIRECTORY_INFORMATION = 1
        private const val FILE_FULL_DIRECTORY_INFORMATION = 2
        private const val FILE_BOTH_DIRECTORY_INFORMATION = 3
        private const val FILE_BASIC_INFORMATION = 4
        private const val FILE_STANDARD_INFORMATION = 5
        private const val FILE_RENAME_INFORMATION = 10
        private const val FILE_NAMES_INFORMATION = 12
        private const val FILE_DISPOSITION_INFORMATION = 13
        private const val FILE_ALLOCATION_INFORMATION = 19
        private const val FILE_END_OF_FILE_INFORMATION = 20
        private const val FILE_ATTRIBUTE_TAG_INFORMATION = 35

        // [MS-FSCC] FsInformationClass
        private const val FILE_FS_VOLUME_INFORMATION = 1
        private const val FILE_FS_SIZE_INFORMATION = 3
        private const val FILE_FS_DEVICE_INFORMATION = 4
        private const val FILE_FS_ATTRIBUTE_INFORMATION = 5
        private const val FILE_FS_FULL_SIZE_INFORMATION = 7

        // ファイル属性
        private const val FILE_ATTRIBUTE_READONLY = 0x00000001
        private const val FILE_ATTRIBUTE_HIDDEN = 0x00000002
        private const val FILE_ATTRIBUTE_DIRECTORY = 0x00000010
        private const val FILE_ATTRIBUTE_NORMAL = 0x00000080

        private const val FILE_CASE_SENSITIVE_SEARCH = 0x00000001
        private const val FILE_CASE_PRESERVED_NAMES = 0x00000002
        private const val FILE_UNICODE_ON_DISK = 0x00000004
        private const val FILE_DEVICE_DISK = 0x00000007
    }
}
