package com.zerotoship.z2term.gui.rdp

import android.util.Log
import com.zerotoship.z2term.gui.ClipboardFiles
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * [MS-RDPECLIP]。テキスト (CF_UNICODETEXT) と**ファイル** (FileGroupDescriptorW) を共有する。
 * HTML や画像の形式は宣言しない (受け取れないものを宣言すると相手が送ってくる)。
 *
 * ファイルは **`CB_FILECLIP_NO_FILE_PATHS` のストリーム転送**として扱う。相手のパスを受け取って
 * 開きに行くのではなく、**中身を要求して受け取る**形なので、こちらにファイルシステムを見せずに済む。
 *
 * ⛔⛔ **取り寄せの途中で待たない。** 受信ループから呼ばれるので、「要求 → 応答を待つ」と書くと
 * そこで RDP 全体が止まる。⇒ **応答が来たら次を要求する**状態機械にしてある ([Incoming])。
 */
internal class RdpCliprdr(
    private val sendMessage: (ByteArray) -> Unit,
    private val onRemoteText: (String) -> Unit,
    /** 相手が渡してきたファイルの置き場。null ならファイルの形式を宣言しない。 */
    private val fileSink: ClipboardFiles.Sink? = null,
    /**
     * 相手がコピーしたファイルの一覧が変わったとき (空 = 何も無くなった)。
     *
     * ⛔ **ここで中身を取り寄せない。** コピーは Windows の中だけで完結することも多く、
     * そのたびに端末へ落としていたら通信も置き場も浪費する。⇒ **中身は [receiveOfferedFiles]
     * を呼ばれたときだけ**取りに行く。⭐ RDP は元々この作法 (こちらのファイルを渡すときも、
     * 相手は貼り付けた瞬間に初めて中身を要求してくる)。
     */
    private val onFilesOffered: (List<ClipboardFiles.Entry>) -> Unit = {},
    /** 取り寄せが終わった (成功・失敗によらず)。 */
    private val onFilesReceived: () -> Unit = {},
) {
    private var serverReady = false
    private var localText: String? = null
    private var localFiles: ClipboardFiles.Source? = null
    /** 相手の Format List にあった FileGroupDescriptorW の id。⚠ 登録形式なので**相手ごとに違う**。 */
    private var remoteFileFormatId: Int? = null
    /** 直前に投げた Format Data Request。応答がテキストかファイルかを見分けるために覚える。 */
    private var pendingFormat: Int? = null
    /** 相手が今コピーしているファイルの一覧。⚠ **中身はまだ 1 バイトも取り寄せていない。** */
    private var offered: List<ClipboardFiles.Entry> = emptyList()
    private var incoming: Incoming? = null
    private var nextStreamId = 1

    private val reassembler = RdpChannelReassembler("CLIPRDR", MAX_MESSAGE_BYTES) { handle(it) }

    /** 取り寄せ中の一覧と、どこまで受け取ったか。 */
    private class Incoming(val entries: List<ClipboardFiles.Entry>) {
        var index = -1
        var position = 0L
        var streamId = 0
        var receiving = false
    }

    @Synchronized
    fun start() {
        if (serverReady) return
        serverReady = true
        send(capabilities())
        send(formatList())
    }

    @Synchronized
    fun announceLocalText(text: String) {
        localText = text.take(MAX_TEXT_CHARS)
        localFiles?.let { runCatching { it.close() } }
        localFiles = null
        if (serverReady) send(formatList())
    }

    /** Android 側でコピーされたファイルを相手へ差し出す。null で取り下げる。 */
    @Synchronized
    fun announceLocalFiles(source: ClipboardFiles.Source?) {
        localFiles?.let { runCatching { it.close() } }
        localFiles = source?.takeIf { it.entries.isNotEmpty() }
        if (localFiles != null) localText = null
        if (serverReady) send(formatList())
    }

    @Synchronized
    fun acceptChannelChunk(payload: ByteArray) = reassembler.accept(payload)

    /**
     * CLIPRDR は画面・入力とは独立した任意チャネル。相手の clipboard PDU が壊れていても
     * **RDP セッション全体を切らない**。分割途中を捨て、進行中のファイルだけ失敗として畳む。
     *
     * @return true if the chunk was accepted; false if only CLIPRDR was reset.
     */
    @Synchronized
    fun acceptChannelChunkSafely(payload: ByteArray): Boolean = try {
        reassembler.accept(payload)
        true
    } catch (e: Exception) {
        Log.w(TAG, "CLIPRDR: discarded an invalid channel message; desktop stays connected", e)
        reassembler.reset()
        abortIncoming()
        false
    }

    /**
     * [onFilesOffered] で知らせた一覧の**中身を取りに行く**。利用者が受け取ると決めたときだけ呼ぶ。
     *
     * 取り寄せ中の呼び出しは無視する (二重に走らせない)。
     */
    @Synchronized
    fun receiveOfferedFiles() {
        val sink = fileSink ?: return
        if (offered.isEmpty() || incoming != null) return
        Log.i(TAG, "CLIPRDR: receiving ${offered.size} file(s)")
        incoming = Incoming(offered)
        advanceIncoming(sink)
    }

    @Synchronized
    fun close() {
        abortIncoming()
        localFiles?.let { runCatching { it.close() } }
        localFiles = null
    }

    private fun handle(message: ByteArray) {
        if (message.size < CLIP_HEADER_SIZE) throw IOException("truncated CLIPRDR message")
        val type = le16(message, 0)
        val flags = le16(message, 2)
        val length = le32(message, 4)
        if (length < 0 || length != message.size - CLIP_HEADER_SIZE) {
            throw IOException("invalid CLIPRDR message length")
        }
        val body = message.copyOfRange(CLIP_HEADER_SIZE, message.size)
        when (type) {
            CB_MONITOR_READY -> {
                serverReady = true
                send(capabilities())
                send(formatList())
            }
            CB_CLIP_CAPS -> Unit
            CB_FORMAT_LIST -> formatListArrived(body)
            CB_FORMAT_DATA_REQUEST -> formatDataRequested(body)
            CB_FORMAT_DATA_RESPONSE -> formatDataArrived(flags, body)
            CB_FILECONTENTS_REQUEST -> fileContentsRequested(body)
            CB_FILECONTENTS_RESPONSE -> fileContentsArrived(flags, body)
        }
    }

    private fun formatListArrived(body: ByteArray) {
        send(message(CB_FORMAT_LIST_RESPONSE, CB_RESPONSE_OK, byteArrayOf()))
        // 相手のコピー内容が入れ替わった。取り寄せかけていたものを畳み、出していた一覧も取り下げる。
        abortIncoming()
        if (offered.isNotEmpty()) {
            offered = emptyList()
            onFilesOffered(emptyList())
        }
        val formats = formats(body)
        val fileFormat = formats.firstOrNull { it.second.equals(FILE_FORMAT_NAME, ignoreCase = true) }?.first
        remoteFileFormatId = fileFormat
        // ⚠ **ファイルを優先する。** Windows はファイルをコピーすると、その置き場を指す文字列も
        //    一緒に announce することがある。テキストを先に取ると、ファイルの代わりにパスが届く。
        val wanted = when {
            fileSink != null && fileFormat != null -> fileFormat
            formats.any { it.first == CF_UNICODETEXT } -> CF_UNICODETEXT
            else -> null
        } ?: return
        pendingFormat = wanted
        send(message(CB_FORMAT_DATA_REQUEST, 0, le32Bytes(wanted)))
    }

    private fun formatDataRequested(body: ByteArray) {
        val requested = body.takeIf { it.size >= 4 }?.let { le32(it, 0) }
        val text = localText
        val files = localFiles
        when {
            requested == CF_UNICODETEXT && text != null -> {
                val encoded = (text + "\u0000").toByteArray(Charsets.UTF_16LE)
                send(message(CB_FORMAT_DATA_RESPONSE, CB_RESPONSE_OK, encoded))
            }
            requested == LOCAL_FILE_FORMAT_ID && files != null -> {
                send(message(CB_FORMAT_DATA_RESPONSE, CB_RESPONSE_OK, fileDescriptors(files.entries)))
            }
            else -> send(message(CB_FORMAT_DATA_RESPONSE, CB_RESPONSE_FAIL, byteArrayOf()))
        }
    }

    private fun formatDataArrived(flags: Int, body: ByteArray) {
        val requested = pendingFormat
        pendingFormat = null
        if (flags and CB_RESPONSE_OK == 0 || body.isEmpty()) return
        if (requested != null && requested == remoteFileFormatId) {
            offerIncoming(body)
            return
        }
        val evenLength = body.size - (body.size % 2)
        val text = body.copyOf(evenLength).toString(Charsets.UTF_16LE).trimEnd('\u0000')
        if (text.isNotEmpty()) onRemoteText(text.take(MAX_TEXT_CHARS))
    }

    /**
     * 相手の一覧が届いた。⚠ フォルダは飛ばす (中身の一覧は別に来ない)。
     *
     * ⛔ **ここで中身を取り寄せない。** 一覧 (名前と大きさ) は 1 件 592 バイトで軽く、
     * 「何が来ているか」を見せるのに要る。中身は [receiveOfferedFiles] まで待つ。
     */
    private fun offerIncoming(body: ByteArray) {
        if (fileSink == null) return
        if (body.size < 4) return
        val count = le32(body, 0)
        if (count <= 0 || count > MAX_FILES) return
        val entries = mutableListOf<ClipboardFiles.Entry>()
        var offset = 4
        repeat(count) {
            if (offset + FILEDESCRIPTOR_SIZE > body.size) return@repeat
            val attributes = le32(body, offset + 36)
            val sizeHigh = le32(body, offset + 64).toLong() and 0xFFFFFFFFL
            val sizeLow = le32(body, offset + 68).toLong() and 0xFFFFFFFFL
            val name = body.copyOfRange(offset + 72, offset + FILEDESCRIPTOR_SIZE)
                .toString(Charsets.UTF_16LE).trimEnd('\u0000')
            offset += FILEDESCRIPTOR_SIZE
            if (attributes and FILE_ATTRIBUTE_DIRECTORY != 0) return@repeat
            // ⚠ 相手の区切りのままにしない。フォルダごとコピーされると `a\b.txt` で届く。
            val leaf = name.substringAfterLast('\\').substringAfterLast('/')
            if (leaf.isNotEmpty()) entries += ClipboardFiles.Entry(leaf, (sizeHigh shl 32) or sizeLow)
        }
        if (entries.isEmpty()) return
        Log.i(TAG, "CLIPRDR: the peer is offering ${entries.size} file(s)")
        offered = entries
        onFilesOffered(entries)
    }

    /** 次のファイルへ進む。⚠ 受信スレッドから呼ばれるので、ここで待たない。 */
    private fun advanceIncoming(sink: ClipboardFiles.Sink) {
        val state = incoming ?: return
        if (state.receiving) {
            state.receiving = false
            sink.finish(true)
        }
        while (true) {
            state.index++
            if (state.index >= state.entries.size) {
                incoming = null
                onFilesReceived()
                return
            }
            val entry = state.entries[state.index]
            state.position = 0
            if (entry.size <= 0) {
                // 中身の無いファイルも「届いた」ことにする (0 バイトで作られる)。
                if (sink.begin(entry)) sink.finish(true)
                continue
            }
            if (!sink.begin(entry)) continue
            state.receiving = true
            requestChunk(state, entry)
            return
        }
    }

    private fun requestChunk(state: Incoming, entry: ClipboardFiles.Entry) {
        val remaining = entry.size - state.position
        val want = minOf(remaining, CHUNK_BYTES.toLong()).toInt()
        state.streamId = nextStreamId++
        val body = ByteArrayOutputStream().apply {
            write(le32Bytes(state.streamId))
            write(le32Bytes(state.index))
            write(le32Bytes(FILECONTENTS_RANGE))
            write(le32Bytes(state.position.toInt()))
            write(le32Bytes((state.position ushr 32).toInt()))
            write(le32Bytes(want))
        }.toByteArray()
        send(message(CB_FILECONTENTS_REQUEST, 0, body))
    }

    private fun fileContentsArrived(flags: Int, body: ByteArray) {
        val sink = fileSink ?: return
        val state = incoming ?: return
        if (body.size < 4 || le32(body, 0) != state.streamId) return
        if (flags and CB_RESPONSE_OK == 0) {
            Log.w(TAG, "CLIPRDR: the peer refused to send file ${state.index}")
            state.receiving = false
            sink.finish(false)
            advanceIncoming(sink)
            return
        }
        val data = body.copyOfRange(4, body.size)
        if (data.isNotEmpty()) {
            sink.write(data)
            state.position += data.size
        }
        val entry = state.entries[state.index]
        when {
            // ⚠ 空の応答は「もう出せない」の意味。⭐ **足りていなければ未完了として畳む**
            //    (足りているのに完了扱いにしないのと同じくらい、逆も困る)。
            data.isEmpty() -> {
                state.receiving = false
                sink.finish(state.position >= entry.size)
                advanceIncoming(sink)
            }
            state.position >= entry.size -> advanceIncoming(sink)
            else -> requestChunk(state, entry)
        }
    }

    private fun fileContentsRequested(body: ByteArray) {
        if (body.size < FILECONTENTS_REQUEST_BYTES) {
            Log.w(TAG, "CLIPRDR: ignored truncated file request (${body.size} bytes)")
            return
        }
        val streamId = le32(body, 0)
        val index = le32(body, 4)
        val requestFlags = le32(body, 8)
        val positionLow = le32(body, 12).toLong() and 0xFFFFFFFFL
        val positionHigh = le32(body, 16).toLong() and 0xFFFFFFFFL
        val requested = le32(body, 20)
        val source = localFiles
        val entry = source?.entries?.getOrNull(index)
        if (source == null || entry == null) {
            send(message(CB_FILECONTENTS_RESPONSE, CB_RESPONSE_FAIL, le32Bytes(streamId)))
            return
        }
        if (requestFlags == FILECONTENTS_SIZE) {
            val reply = le32Bytes(streamId) + le32Bytes(entry.size.toInt()) +
                le32Bytes((entry.size ushr 32).toInt())
            send(message(CB_FILECONTENTS_RESPONSE, CB_RESPONSE_OK, reply))
            return
        }
        if (requestFlags != FILECONTENTS_RANGE || requested < 0 || requested > MAX_OUTGOING_CHUNK_BYTES) {
            Log.w(TAG, "CLIPRDR: refused invalid file request flags=$requestFlags bytes=$requested")
            send(message(CB_FILECONTENTS_RESPONSE, CB_RESPONSE_FAIL, le32Bytes(streamId)))
            return
        }
        val position = (positionHigh shl 32) or positionLow
        if (position < 0 || position > entry.size || requested.toLong() > entry.size - position) {
            Log.w(TAG, "CLIPRDR: refused out-of-range file request index=$index position=$position bytes=$requested")
            send(message(CB_FILECONTENTS_RESPONSE, CB_RESPONSE_FAIL, le32Bytes(streamId)))
            return
        }
        // ⛔ requested を受信側の 64 KiB chunk に丸めない。Windows は通常これより大きい範囲を
        // 1 回で要求し、短い成功応答を EOF と扱うため、途中でファイルが切れて貼り付けに失敗する。
        val data = runCatching { source.read(index, position, requested) }.getOrNull()
        if (data == null) {
            send(message(CB_FILECONTENTS_RESPONSE, CB_RESPONSE_FAIL, le32Bytes(streamId)))
            return
        }
        send(message(CB_FILECONTENTS_RESPONSE, CB_RESPONSE_OK, le32Bytes(streamId) + data))
    }

    private fun abortIncoming() {
        val state = incoming ?: return
        incoming = null
        if (state.receiving) fileSink?.finish(false)
        onFilesReceived()
    }

    private fun capabilities(): ByteArray {
        // ⚠ ファイルを扱う宣言は sink があるときだけ。受け取れないものを宣言すると相手が送ってくる。
        val general = if (fileSink != null) {
            CB_USE_LONG_FORMAT_NAMES or CB_STREAM_FILECLIP_ENABLED or CB_FILECLIP_NO_FILE_PATHS
        } else {
            CB_USE_LONG_FORMAT_NAMES
        }
        val body = ByteArrayOutputStream().apply {
            write(le16Bytes(1))
            write(le16Bytes(0))
            write(le16Bytes(CB_CAPSTYPE_GENERAL))
            write(le16Bytes(12))
            write(le32Bytes(CB_CAPS_VERSION_2))
            write(le32Bytes(general))
        }.toByteArray()
        return message(CB_CLIP_CAPS, 0, body)
    }

    private fun formatList(): ByteArray {
        val body = ByteArrayOutputStream().apply {
            if (localFiles != null) {
                write(le32Bytes(LOCAL_FILE_FORMAT_ID))
                write((FILE_FORMAT_NAME + "\u0000").toByteArray(Charsets.UTF_16LE))
            }
            if (localText != null) {
                write(le32Bytes(CF_UNICODETEXT))
                write(byteArrayOf(0, 0))
            }
        }.toByteArray()
        return message(CB_FORMAT_LIST, 0, body)
    }

    /** 相手へ渡す一覧。⚠ 名前は 260 文字ぶんの固定枠に収める。 */
    private fun fileDescriptors(entries: List<ClipboardFiles.Entry>): ByteArray =
        ByteArrayOutputStream().apply {
            write(le32Bytes(entries.size))
            entries.forEach { entry ->
                write(le32Bytes(FD_ATTRIBUTES or FD_FILESIZE or FD_SHOWPROGRESSUI))
                write(ByteArray(32)) // reserved1
                write(le32Bytes(FILE_ATTRIBUTE_NORMAL))
                write(ByteArray(16)) // reserved2
                write(ByteArray(8)) // lastWriteTime: 知らせない (FD_WRITESTIME を立てていない)
                write(le32Bytes((entry.size ushr 32).toInt()))
                write(le32Bytes(entry.size.toInt()))
                val name = ByteArray(FILENAME_BYTES)
                val encoded = entry.name.take(FILENAME_CHARS - 1).toByteArray(Charsets.UTF_16LE)
                encoded.copyInto(name, 0, 0, minOf(encoded.size, FILENAME_BYTES - 2))
                write(name)
            }
        }.toByteArray()

    private fun send(message: ByteArray) = sendMessage(message)

    private fun message(type: Int, flags: Int, body: ByteArray): ByteArray =
        le16Bytes(type) + le16Bytes(flags) + le32Bytes(body.size) + body

    /** Format List を「id と名前」で読む。short (36 バイト固定) と long (可変) の両方が来る。 */
    private fun formats(body: ByteArray): List<Pair<Int, String>> {
        if (body.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<Int, String>>()
        if (body.size % SHORT_FORMAT_SIZE == 0 && looksShort(body)) {
            var offset = 0
            while (offset + SHORT_FORMAT_SIZE <= body.size) {
                val name = body.copyOfRange(offset + 4, offset + SHORT_FORMAT_SIZE)
                    .toString(Charsets.UTF_16LE).trimEnd('\u0000')
                result += le32(body, offset) to name
                offset += SHORT_FORMAT_SIZE
            }
            return result
        }
        var offset = 0
        while (offset + 4 <= body.size) {
            val id = le32(body, offset)
            offset += 4
            val start = offset
            while (offset + 1 < body.size &&
                !(body[offset] == 0.toByte() && body[offset + 1] == 0.toByte())
            ) {
                offset += 2
            }
            val name = body.copyOfRange(start, offset).toString(Charsets.UTF_16LE)
            offset = minOf(offset + 2, body.size)
            result += id to name
        }
        return result
    }

    /**
     * 36 バイトの倍数でも long format names のことがある。
     *
     * ⚠ 見分けは**名前の枠が本当に名前として埋まっているか**で行う: short 形式なら 5 バイト目
     * 以降は UTF-16 の名前か 0 詰めで、途中に単独の 0 バイトが並ぶ。
     */
    private fun looksShort(body: ByteArray): Boolean {
        var offset = 0
        while (offset + SHORT_FORMAT_SIZE <= body.size) {
            val tail = body.copyOfRange(offset + 4, offset + SHORT_FORMAT_SIZE)
            if (tail.last() != 0.toByte() || tail[tail.size - 2] != 0.toByte()) return false
            offset += SHORT_FORMAT_SIZE
        }
        return true
    }

    companion object {
        private const val TAG = "RdpCliprdr"

        private const val CLIP_HEADER_SIZE = 8

        private const val CB_MONITOR_READY = 0x0001
        private const val CB_FORMAT_LIST = 0x0002
        private const val CB_FORMAT_LIST_RESPONSE = 0x0003
        private const val CB_FORMAT_DATA_REQUEST = 0x0004
        private const val CB_FORMAT_DATA_RESPONSE = 0x0005
        private const val CB_CLIP_CAPS = 0x0007
        private const val CB_FILECONTENTS_REQUEST = 0x0008
        private const val CB_FILECONTENTS_RESPONSE = 0x0009
        private const val CB_RESPONSE_OK = 0x0001
        private const val CB_RESPONSE_FAIL = 0x0002

        private const val CB_CAPSTYPE_GENERAL = 0x0001
        private const val CB_CAPS_VERSION_2 = 0x00000002
        private const val CB_USE_LONG_FORMAT_NAMES = 0x00000002
        private const val CB_STREAM_FILECLIP_ENABLED = 0x00000004
        /** ⭐ パスではなく**中身**をやり取りする宣言。こちらのファイルシステムを見せずに済む。 */
        private const val CB_FILECLIP_NO_FILE_PATHS = 0x00000008

        private const val CF_UNICODETEXT = 13
        /** こちらが announce するファイル形式の id。⚠ 相手は**名前**で見分けるので値は任意。 */
        private const val LOCAL_FILE_FORMAT_ID = 0xC0DE
        private const val FILE_FORMAT_NAME = "FileGroupDescriptorW"

        private const val FILECONTENTS_SIZE = 0x0001
        private const val FILECONTENTS_RANGE = 0x0002
        private const val FILECONTENTS_REQUEST_BYTES = 24

        private const val FILEDESCRIPTOR_SIZE = 592
        private const val FILENAME_BYTES = 520
        private const val FILENAME_CHARS = 260
        private const val FD_ATTRIBUTES = 0x0004
        private const val FD_FILESIZE = 0x0040
        private const val FD_SHOWPROGRESSUI = 0x4000
        private const val FILE_ATTRIBUTE_DIRECTORY = 0x0010
        private const val FILE_ATTRIBUTE_NORMAL = 0x0080

        private const val SHORT_FORMAT_SIZE = 36
        private const val MAX_MESSAGE_BYTES = 2 * 1024 * 1024
        private const val MAX_TEXT_CHARS = 256 * 1024
        private const val MAX_FILES = 512
        /** 1 回に取り寄せる大きさ。⚠ CLIPRDR の 1 通の上限より十分小さくしておく。 */
        private const val CHUNK_BYTES = 64 * 1024
        /** Windows からの要求は 64 KiB を超える。要求より短い成功応答を返さないための安全上限。 */
        private const val MAX_OUTGOING_CHUNK_BYTES = 4 * 1024 * 1024

        private fun le16(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

        private fun le32(data: ByteArray, offset: Int): Int =
            le16(data, offset) or (le16(data, offset + 2) shl 16)

        private fun le16Bytes(value: Int) =
            byteArrayOf(value.toByte(), (value ushr 8).toByte())

        private fun le32Bytes(value: Int) =
            byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())
    }
}
